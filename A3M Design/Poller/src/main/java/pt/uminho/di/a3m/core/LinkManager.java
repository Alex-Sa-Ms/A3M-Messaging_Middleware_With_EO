package pt.uminho.di.a3m.core;

import com.google.protobuf.InvalidProtocolBufferException;
import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.core.flowcontrol.FlowCreditsPayload;
import pt.uminho.di.a3m.core.messaging.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;
import java.util.logging.Logger;

import pt.uminho.di.a3m.core.Link.LinkState;
import pt.uminho.di.a3m.core.Link.ParkStateWithEvents;
import pt.uminho.di.a3m.core.messaging.payloads.ErrorPayload;
import pt.uminho.di.a3m.core.messaging.payloads.SerializableMap;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.poller.PollTable;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;

public class LinkManager implements Link.LinkObserver {
    private final Socket socket;
    final Map<SocketIdentifier, Link> links = new HashMap<>();
    private int clock = 0;
    public LinkManager(Socket socket) {
        this.socket = socket;
    }

    /** @return (socket) lock */
    private Lock lock(){
        return socket.lock;
    }

    /** @return (socket) state */
    private AtomicReference<SocketState> state(){
        return socket.state;
    }

    private Link getLink(SocketIdentifier peerId) {
        try{
            lock().lock();
            return links.get(peerId);
        } finally {
            lock().unlock();
        }
    }

    /**
     * Create message to the mentioned peer.
     * @param dest identifier of the destination
     * @param type type of the message
     * @param payload content of the message
     * @return created message
     */
    private SocketMsg createMsg(SocketIdentifier dest, byte type, byte[] payload){
        assert dest != null;
        return new SocketMsg(socket.getId(), dest, type, payload);
    }

    /**
     * Create and dispatch message to the peer
     * @param dest identifier of the destination
     * @param type type of the message
     * @param payload content of the message
     */
    private void dispatch(SocketIdentifier dest, byte type, byte[] payload){
        SocketMsg msg = createMsg(dest, type, payload);
        socket.dispatch(msg);
    }

    /**
     * Create and dispatch message to the peer
     * @param dest identifier of the destination
     * @param payload content of the message
     */
    private void dispatch(SocketIdentifier dest, Payload payload){
        assert payload != null;
        SocketMsg msg = createMsg(dest, payload.getType(), payload.getPayload());
        socket.dispatch(msg);
    }

    /**
     * Dispatch message
     * @param msg socket message to be dispatched
     */
    private void dispatch(SocketMsg msg){
        socket.dispatch(msg);
    }

    /**
     * Schedule dispatch of a message to the peer
     * @param dest identifier of the destination
     * @param type type of the message
     * @param payload content of the message
     * @param dispatchTime time at which the dispatch should be executed. 
     *                     Must be obtained using System.currentTimeMillis()               
     */
    private AtomicReference<SocketMsg> scheduleDispatch(SocketIdentifier dest, byte type, byte[] payload, long dispatchTime){
        SocketMsg msg = createMsg(dest, type, payload);
        return socket.scheduleDispatch(msg, dispatchTime);
    }

    /**
     * Schedule dispatch of a message to the peer
     * @param dest identifier of the destination
     * @param payload content of the message
     * @param dispatchTime time at which the dispatch should be executed.
     *                     Must be obtained using System.currentTimeMillis()
     */
    private AtomicReference<SocketMsg> scheduleDispatch(SocketIdentifier dest, Payload payload, long dispatchTime){
        assert payload != null;
        SocketMsg msg = createMsg(dest, payload.getType(), payload.getPayload());
        return socket.scheduleDispatch(msg, dispatchTime);
    }

    /**
     * Schedule dispatch of a message to the peer
     * @param msg socket message to be dispatched
     * @param dispatchTime time at which the dispatch should be executed. 
     *                     Must be obtained using System.currentTimeMillis() 
     */
    private AtomicReference<SocketMsg> scheduleDispatch(SocketMsg msg, long dispatchTime){
        return socket.scheduleDispatch(msg, dispatchTime);
    }

    private void dispatchNotLinkedErrorMsg(SocketIdentifier peerId, SocketMsg msg){
        byte[] errorPayload = new ErrorPayload(ErrorType.SOCK_NLINKED).getPayload();
        dispatch(peerId, MsgType.ERROR, errorPayload);
        Logger.getLogger(socket.getId().toString()).warning("Not linked peer (" + peerId + ") sent: " + msg.toString());
    }

    // ********* Linking Reply Codes ********* //
    // Zero for success.
    // Non-fatal refusal codes are positive.
    // Fatal refusal codes are negative.

    private static final int AC_SUCCESS = 0;

    // **** Non-fatal refusal codes **** //

    // The link may not be established, but exists.
    // Used to make the peer schedule a retry of the
    // linking process, therefore allowing the ongoing
    // unlinking process to finish.
    private static final int AC_LINK_EXISTS = 1;
    // Temporarily unavailable
    private static final int AC_TMP_NAVAIL = 2;

    // **** Fatal refusal codes **** //

    // Incompatible protocols
    private static final int AC_INCOMPATIBLE = -1;
    // Incoming link requests not allowed.
    private static final int AC_INCOMING_NOT_ALLOWED = -2;
    // Canceled linking process
    public static final int AC_CANCELED = -3;
    // Socket is closed
    public static final int AC_CLOSED = -4;

    private static boolean isPositiveLinkingCode(int replycode){
        return replycode == AC_SUCCESS;
    }

    private static boolean isFatalLinkingCode(int replycode){
        return replycode < 0;
    }

    private static boolean isNonFatalLinkingCode(int replycode){
        return replycode > 0;
    }

    // ********* Creation & Parsing of Link-related messages ********* //

    /**
     * Creates link request payload.
     * @param clockId link's clock identifier
     * @return serializable map which corresponds to the link request payload.
     */
    private SerializableMap createLinkRequestMsg(int clockId){
        SerializableMap map = new SerializableMap();
        int protocolId = socket.getProtocol().id();
        int credits = socket.getOption("capacity", Integer.class);
        map.putInt("protocolId", protocolId);
        map.putInt("credits", credits);
        map.putInt("clockId", clockId);
        return map;
    }

    private void logParseErrorOnLinkReqOrReplyMsg(SocketMsg msg){
        Logger.getLogger(socket.getId().toString())
                .warning("Invalid " + (msg.getType() == MsgType.LINK ? "link request" : "link reply")
                        + " from " + msg.getSrcId() + " : " + Arrays.toString(msg.getPayload()));
    }

    /**
     * Converts message's payload (byte array) to serializable map if possible.
     * Then, checks if the mandatory fields of a link request / link reply
     * message are present.
     * @param msg message to have its payload converted to serializable map
     * @return serializable map or null if payload is invalid
     */
    private SerializableMap parseLinkRequestMsg(SocketMsg msg){
        try {
            SerializableMap map = SerializableMap.deserialize(msg.getPayload());
            if(!map.hasInt("protocolId") || !map.hasInt("clockId") || !map.hasInt("credits"));
            else return map;
        } catch (InvalidProtocolBufferException ignored) {}
        // if invalid payload, log it, and return null
        logParseErrorOnLinkReqOrReplyMsg(msg);
        return null;
    }

    /**
     * Creates link reply payload. Identical to the link request
     * payload but enables a reply code to be provided.
     * @param clockId link's clock identifier
     * @param replyCode reply code
     * @return serializable map which corresponds to the link request payload.
     */
    private SerializableMap createLinkReplyMsgWithMetadata(int clockId, int replyCode) {
        SerializableMap map = createLinkRequestMsg(clockId);
        map.putInt("replyCode", replyCode);
        return map;
    }

    private SerializableMap createLinkReplyMsgWithoutMetadata(int clockId, int replyCode) {
        SerializableMap map = new SerializableMap();
        map.putInt("clockId", clockId);
        map.putInt("replyCode", replyCode);
        return map;
    }

    private SerializableMap parseLinkReplyMsg(SocketMsg msg) {
        try {
            SerializableMap map = SerializableMap.deserialize(msg.getPayload());
            if(!map.hasInt("clockId") || !map.hasInt("replyCode"));
            else return map;
        } catch (InvalidProtocolBufferException ignored) {}
        // if invalid payload, log it, and return null
        logParseErrorOnLinkReqOrReplyMsg(msg);
        return null;
    }

    /**
     * @param clockId link's clock identifier
     * @return payload of unlink message
     */
    private byte[] createUnlinkMsg(int clockId) {
        return ByteBuffer.allocate(4).putInt(clockId).array();
    }

    /**
     * Extracts the clock identifier of the peer from the unlink message.
     * @param msg unlink message
     * @return the clock identifier if the message's payload is valid. Otherwise, returns null.
     */
    private static Integer parseUnlinkMsg(SocketMsg msg){
        if(msg.getPayload() != null && msg.getPayload().length == 4)
            return ByteBuffer.wrap(msg.getPayload()).getInt();
        else
            return null;
    }

    public static String linkRelatedMsgToString(SocketMsg msg) throws InvalidProtocolBufferException {
        StringBuilder sb = new StringBuilder();
        String type = "<not link-related>", payload = "";
        if(msg == null) return null;

        switch (msg.getType()) {
            case MsgType.LINK -> {
                type = "LINK";
                payload = SerializableMap.deserialize(msg.getPayload()).toString();
            }
            case MsgType.LINKREPLY -> {
                type = "LINKREPLY";
                payload = SerializableMap.deserialize(msg.getPayload()).toString();
            }
            case MsgType.UNLINK -> {
                type = "UNLINK";
                payload = String.valueOf(parseUnlinkMsg(msg));
            }
        }

        return sb.append("msg{src=").append(msg.getSrcId())
                 .append(", dest=").append(msg.getDestId())
                 .append(", type=").append(type)
                 .append(", payload=").append(payload)
                 .append("}").toString();
    }

    // ********* Linking/Unlinking logic ********* //

    // TODO 0 - make restrictions on the clock identifiers where they are required

    // TODO 2 - see where notifying waiters and creating socket events is required
    //  - set credits to zero when unlinking to prevent sending of data messages?

    // TODO 3 - pass methods in the Link class to this class

    // TODO 4 - since there are "wait for link" methods which wait for a link
    //  to be established, then there should be a "wait for link closure" also.

    /**
     * Determines if a peer is compatible
     * @param peerProtocolId peer's protocol identifier
     * @return true if peer is compatible. Otherwise, returns false.
     */
    private boolean isPeerCompatible(int peerProtocolId){
        return socket.isCompatibleProtocol(peerProtocolId);
    }

    /**
     * Method invoked when the link exists.
     * Checks if peer is compatible. If not compatible, rejects the link
     * by sending an imcompatible refusal code in a LINKREPLY, then closes the link.
     * If compatible, sends success code in a LINKREPLY and establishes the link.
     * @param link link to be checked
     * @param establish flag should be set when, in addition to sending the LINKREPLY message,
     *                 establishing the link is desirable. If not set, then the LINKREPLY message
     *                  is sent, but the link is not established, meaning it remains in the same
     *                  state (which should be LINKING) until the peer's LINKREPLY message is received
     *                  and the decision about the establishment can be made.
     * @return true if compatible. false, otherwise.
     */
    private boolean checkCompatibilityThenAcceptOrReject(Link link, boolean establish) {
        boolean compatible;
        byte[] payload;
        // if a link request is scheduled, cancel it immediately
        SocketMsg scheduled = link.cancelScheduledMessage();

        if(isPeerCompatible(link.getPeerProtocolId())){
            // if a link request was not scheduled, the socket sends a LINKREPLY with a success code
            // but without metadata since a LINK msg was already sent to the peer
            // containing the metadata. The socket also establishes the link.
            if (scheduled == null) {
                payload = createLinkReplyMsgWithoutMetadata(link.getClockId(),AC_SUCCESS).serialize();
                if (establish) establishLink(link);
            }
            // Else, if there was a scheduled link request, we remain in
            // a LINKING state, send a LINKREPLY message with metadata
            // and a success code, and wait for the peer to confirm the establishment.
            else payload = createLinkReplyMsgWithMetadata(link.getClockId(),AC_SUCCESS).serialize();
            compatible = true;
        }else{
            // if incompatible, create LINKREPLY message with incompatible (fatal) refusal code,
            // and add metadata depending on whether a LINK msg is considered in "flight" or not,
            // as done above when the peer is compatible.
            if(scheduled == null)
                // not scheduled, so considered "in flight"
                payload = createLinkReplyMsgWithoutMetadata(link.getClockId(),AC_INCOMPATIBLE).serialize();
            else
                payload = createLinkReplyMsgWithMetadata(link.getClockId(),AC_INCOMPATIBLE).serialize();
            closeLink(link);
            compatible = false;
        }
        dispatch(link.getDestId(), MsgType.LINKREPLY, payload);
        return compatible;
    }

    /**
     * Method invoked when the link has not yet been created.
     * Checks if linking condtions are met, such as the peer being compatible, etc.
     * Rejects with non-fatal or fatal refusal code depending on the failed linking condition.
     * If all linking conditions are met, sends success code in a LINKREPLY and establishes the link.
     * @return link state, if link was accepted. null, if rejected.
     */
    private Link checkLinkingConditionsThenAcceptOrReject(SocketIdentifier peerId, int peerProtocolId, int peerClockId, int outCredits) {
        Link link = null;
        byte[] payload = null;
        int replyCode = 0;
        // if socket is closing or is closed, send fatal refusal
        if(socket.getState() == SocketState.CLOSING
                || socket.getState() == SocketState.CLOSED)
            replyCode = AC_CLOSED;

        // if incoming link requests are not allowed, send fatal refusal
        boolean allowIncomingLinkRequests = socket.getOption("allowIncomingLinkRequests", Boolean.class);
        if(!allowIncomingLinkRequests)
            replyCode = AC_INCOMING_NOT_ALLOWED;

        // If the peer's protocol is not compatible with the socket's protocol,
        // send a fatal refusal reason informing the incompatibility
        if(!isPeerCompatible(peerProtocolId))
            replyCode = AC_INCOMPATIBLE;

        // If max links limit has been reached. The link cannot be established
        // at the moment, but may be established in the future.
        int maxLinks = socket.getOption("maxLinks", Integer.class);
        if(links.size() >= maxLinks)
            replyCode = AC_TMP_NAVAIL;
        
        if(replyCode != 0){
            payload = createLinkReplyMsgWithMetadata(0, replyCode).serialize();
        } else { 
            // All requirements have been passed, so create link and set it to "linking".
            // A LINKREPLY message is sent to inform willingness to establish the link,
            // and a LINKREPLY message is expected to be returned to confirm the establishment.
            link = createLinkingLink(peerId, peerProtocolId, peerClockId, outCredits);
            payload = createLinkReplyMsgWithMetadata(link.getClockId(), AC_SUCCESS).serialize();
        }

        // dispatch LINKREPLY message
        dispatch(peerId, MsgType.LINKREPLY, payload);
        return link;
    }

    private void scheduleLinkRequest(Link link){
        link.resetPeerMetadata(); // make sure peer information is reset
        byte[] linkReqPayload =
                createLinkRequestMsg(link.getClockId()).serialize();
        long retryInterval = socket.getOption("retryInterval", Long.class);
        long dispatchTime = retryInterval + System.currentTimeMillis();
        AtomicReference<SocketMsg> scheduled =
                scheduleDispatch(link.getDestId(), MsgType.LINK, linkReqPayload, dispatchTime);
        link.setScheduled(scheduled);
    }

    /**
     * Creates link with default incoming capacity.
     * <p>The link is inserted in the "links" collection.
     * @param peerId peer's socket identifier
     * @return link with 'null' state
     */
    private Link createLink(SocketIdentifier peerId){
        int capacity = socket.getOption("capacity", Integer.class);
        Link link = new Link(socket.getId(), peerId, clock, capacity, this);
        links.put(peerId, link);
        // The clock identifiers are provided in an increasing order,
        // as a form of causal consistency.
        clock++;
        return link;
    }

    /**
     * Create link and set it to LINKING state. To be used
     * when link() is invoked and the link does not exist.
     * <p>The link is inserted in the "links" collection.
     * @param peerId peer's socket identifier
     * @return link in LINKING state
     */
    private Link createLinkingLink(SocketIdentifier peerId){
        Link link = createLink(peerId);
        // set state to LINKING
        link.setState(LinkState.LINKING);
        return link;
    }

    /**
     * Create link, set provided attributes and set state to LINKING.
     * To be used when a LINK message is received and the link does not exist.
     * This method limits itself to setting the attributes. It does
     * not do any special procedures such as waking up waiters.
     * <p> The link is inserted in the "links" collection.
     * @param sid peer's socket identifier
     * @return link in LINKING state
     */
    private Link createLinkingLink(SocketIdentifier sid, int peerProtocolId, int peerClockId, int outCredits){
        Link link = createLink(sid);
        link.setState(LinkState.LINKING);
        link.setPeerMetadata(peerProtocolId, peerClockId, outCredits);
        return link;
    }

    /**
     * Closes link and removes it from the links collection
     * @param link link to be closed and removed
     */
    private void closeLink(Link link){
        link.close();
        links.remove(link.getDestId());
        // TODO - Inform link closure to the custom socket logic
        //  socket.customHandleEvent(new LinkClosedEvent(link));
        // TODO - if socket is closing and "links" is empty,
        //     then, invoke socket internal close. Use events instead (observer pattern).
        linkSockets.remove(link.getDestId()); // remove link socket in cache if there is one
    }

    /**
     * Establishes link.
     * @param link link to be established
     */
    private void establishLink(Link link){
        link.establish();
        // TODO - create link established event
        //  socket.customHandleEvent(new LinkEstablishedEvent(link));
    }

    private void handleLinkMsg(SocketMsg msg) {
        SocketIdentifier peerId = msg.getSrcId();
        SerializableMap payload = parseLinkRequestMsg(msg);
        if(payload == null) return; // if payload is invalid
        int peerProtocolId = payload.getInt("protocolId");
        int outCredits = payload.getInt("credits");
        int peerClockId = payload.getInt("clockId");
        try {
            lock().lock();
            Link link = links.get(peerId);
            // If link exists
            if(link != null){
                // Discard messages without an identifier that is not
                // at least as new as the current one
                if(peerClockId < link.getPeerClockId()) return;
                switch (link.getState()){
                    case LINKING -> {
                        // To be in a LINKING state:
                        //  1. (THIS SITUATION) The socket must have either
                        // invoked link() and is waiting the peer's metadata,
                        //  2. The socket has received a LINK msg with the peer's metadata
                        // and is only waiting for the confirmation (LINKREPLY) to establish the link.

                        // To establish a LINK, both the LINK and LINKREPLY messages need
                        // to be received. If the LINK message was the first to arrive,
                        // we cannot establish the link since we are missing the LINKREPLY msg.
                        boolean establish = false;

                        if(link.isLinkReplyMsgReceived() != null){
                            // ignore link messages that have a clock identifier
                            // that does not match the wanted clock identifier
                            if(peerClockId != link.getPeerClockId())
                                return;
                            // save and then reset the waiting variable
                            int peerReplyCode = link.isLinkReplyMsgReceived();
                            link.setLinkReplyMsgReceived(null);

                            // LINK and LINKREPLY have been received,
                            // so the socket can establish the link
                            // if compatible
                            if(isPositiveLinkingCode(peerReplyCode))
                                establish = true;
                            // if the received code is fatal, we need
                            // to set in progress an unlinking process
                            else if(isFatalLinkingCode(peerReplyCode)) {
                                closeLink(link);
                                return;
                            }
                            // if a non-fatal reply code was received and
                            // the peer is compatible, we can schedule a
                            // new link request. Otherwise, close the link.
                            else { // if(isNonFatalLinkingCode(peerReplyCode))
                                if (isPeerCompatible(peerProtocolId)) {
                                    scheduleLinkRequest(link);
                                } else {
                                    closeLink(link);
                                }
                                return;
                            }
                        }

                        if(link.isWaitingPeerMetadata()){
                            // Peer has invoked link() and is waiting for metadata (in a LINK or LINKREPLY msg).
                            link.setPeerMetadata(peerProtocolId, peerClockId, outCredits);
                            // Send LINKREPLY message and close link if incompatible.
                            // If compatible and a LINKREPLY message has not been received,
                            // then wait for it before reaching the final decision regarding
                            // the establishment of the link.
                            checkCompatibilityThenAcceptOrReject(link, establish);
                        }
                        else if(peerClockId > link.getPeerClockId()){
                            SerializableMap linkreplyPayload = createLinkReplyMsgWithMetadata(0, AC_LINK_EXISTS);
                            dispatch(peerId, MsgType.LINKREPLY, linkreplyPayload.serialize());
                        }
                    }
                    // Ignore message when ESTABLISHED. This cannot happen, as
                    // for a LINK/LINKREPLY message to be sent, the sender must not
                    // have the link, and the state of this link is that both peers
                    // agreed on having the link established.
                    // case ESTABLISHED -> {}
                    case UNLINKING -> {
                        // To be in a UNLINKING state, the link must have
                        // been established before. Now, the socket is waiting
                        // for the peer to confirm the closure of the link.

                        // Since a LINK message has been received, this means
                        // the peer has closed the link on its end, and then
                        // initiated a new linking process. So, the received
                        // message must have a higher clock id then the peer's
                        // clock id associated with the current link.
                        if(peerClockId > link.getPeerClockId()) {
                            // A LINKREPLY with non-fatal "ALREADY_LINKED" negative response
                            // is sent to reschedule the linking process, giving time to
                            // finish the current unlinking process.
                            // Closing the current link and creating a new one could also
                            // be an approach, however, we'll stick with this solution for now.
                            byte[] replyPayload = createLinkReplyMsgWithMetadata(link.getClockId(), AC_LINK_EXISTS).serialize();
                            dispatch(peerId, MsgType.LINKREPLY, replyPayload);
                        }
                    }
                    case CANCELLING -> {
                        // To be in a CANCELLING state, the socket wants to close the link
                        // but cannot since it has not yet received the peer's metadata,
                        // which may come in a LINK message.
                        // Since receiving a LINK message would initiate a linking process,
                        // we need to catch it before starting the unlinking process,
                        // to ensure the link is closed.
                        if(link.isWaitingPeerMetadata()) {
                            Integer peerReplyCode = link.isLinkReplyMsgReceived();
                            // If the peer's LINKREPLY message has not yet been received,
                            // or it has been received and has the peer's will to establish the
                            // link, then the socket must send a fatal LINKREPLY message to
                            // ensure the link is closed on the peer's side. If the peer
                            // has rejected (closed) the link before this LINKREPLY message arrives,
                            // the LINKREPLY message is simply discarded.
                            // After sending the LINKREPLY message, the link can be closed.
                            if (peerReplyCode == null || isPositiveLinkingCode(peerReplyCode)) {
                                byte[] replyPayload = createLinkReplyMsgWithoutMetadata(link.getClockId(), AC_CANCELED).serialize();
                                dispatch(peerId, MsgType.LINKREPLY, replyPayload);
                            }
                            // If the peer has sent a negative reply code, meaning it refused to link,
                            // the socket can simply close the link.
                            closeLink(link);
                        }
                        else{
                            // link request after peer closed link on its side
                            if(peerClockId > link.getPeerClockId()) {
                                byte[] replyPayload = createLinkReplyMsgWithMetadata(link.getClockId(), AC_LINK_EXISTS).serialize();
                                dispatch(peerId, MsgType.LINKREPLY, replyPayload);
                            }
                        }
                    }
                }
            }
            // If link does not exist, check linking conditions and sends the appropriate answer.
            // If linking conditions are met, creates the link in a LINKING state, and waits for
            // link establishment confirmation, either through a positive code in a LINKREPLY message,
            // or by receiving a data/control message.
            else checkLinkingConditionsThenAcceptOrReject(peerId, peerProtocolId, peerClockId, outCredits);
        } finally {
            lock().unlock();
        }
    }

    private void handleLinkReplyMsg(SocketMsg msg) {
        SocketIdentifier peerId = msg.getSrcId();
        SerializableMap payload = parseLinkReplyMsg(msg);
        if(payload == null) return; // if payload is invalid
        int replyCode = payload.getInt("replyCode");
        int peerClockId = payload.getInt("clockId");
        int outCredits = payload.getInt("credits");
        //int peerProtocolId = payload.getInt("protocolId");
        try {
            lock().lock();
            Link link = links.get(peerId);
            // If link exists
            if(link != null) {
                // discard messages with old clock identifiers
                if(peerClockId < link.getPeerClockId()) return;
                switch (link.getState()){
                    case LINKING -> {
                        // if the socket is waiting for the peer's metadata
                        // and a LINKREPLY message was received
                        if(link.isWaitingPeerMetadata()){
                            // if the LINKREPLY contains the peer's metadata,
                            // then, the peer is not a linking process initiator
                            if(payload.hasInt("protocolId")){
                                int peerProtocolId = payload.getInt("protocolId");
                                // success linking code
                                if(isPositiveLinkingCode(replyCode)) {
                                    // since the peer did not initiate a linking
                                    // process with the socket, we can check the
                                    // compatibility and act accordingly, i.e.,
                                    // establish the link, or send a rejecting
                                    // LINKREPLY and close the link
                                    link.setPeerMetadata(peerProtocolId, peerClockId, outCredits);
                                    checkCompatibilityThenAcceptOrReject(link, true);
                                } else if (isFatalLinkingCode(replyCode)) {
                                    // if the LINKREPLY message contains a fatal
                                    // refusal code, then remove the link
                                    closeLink(link);
                                } else{
                                    // if not fatal refusal code and
                                    // if peer is compatible, then
                                    // schedule a linking process retry
                                    if(isPeerCompatible(peerProtocolId)) {
                                        scheduleLinkRequest(link);
                                    }else{
                                        // remove link if not compatible
                                        closeLink(link);
                                    }
                                }
                            }
                            // else, the socket and the peer started a linking
                            // process simultaneously. Waiting for a LINK message
                            // from the peer is required.
                            else{
                                // set peer's clock identifier associated
                                // with the incoming LINK message
                                link.setPeerClockId(peerClockId);

                                // Since we need to wait for LINK msg, we set
                                // the reply code received to the variable that
                                // informs if the socket has received the peer's LINKREPLY
                                // message before the peer's LINK message had arrived,
                                // and let the socket remain in a LINKING state.
                                // When the peer's LINK message arrives,
                                // the socket performs the appropriate behavior,
                                // such as removing the link, scheduling a
                                // new link request or establishing the link,
                                // depending on the reply code present in the variable.
                                link.setLinkReplyMsgReceived(replyCode);
                            }
                        }
                        // Else, if the peer's metadata was already received,
                        // then the socket has received a LINK message with
                        // the peer's metadata, has sent a positive LINKREPLY answer 
                        // and is waiting for a LINKREPLY message, which is this one.
                        // This LINKREPLY message is required to determine if the link
                        // should be established or closed.
                        else{
                            // assert the clock identifiers match
                            if(peerClockId == link.getPeerClockId()){
                                // if the answer is positive, then establish the link
                                if(isPositiveLinkingCode(replyCode))
                                    establishLink(link);
                                // if answer is fatal, then close link.
                                // We can close the link, because it has already
                                // been close by the peer on its side.
                                else if(isFatalLinkingCode(replyCode))
                                    closeLink(link);
                                // else, if answer is (negative) non-fatal,
                                // then schedule a new link request
                                else scheduleLinkRequest(link);
                            }
                        }
                    }
                    // Ignore LINKREPLY messages when ESTABLISHED.
                    // case ESTABLISHED -> {}

                    // Receiving a LINKREPLY message when in UNLINKING state is not possible. To pass 
                    // to the UNLINKING state, one must have already received a LINKREPLY message that
                    // would result in the link being established.
                    // case UNLINKING -> {}

                    // TODO - Covered two new cases I wasn't thinking about.
                    //      1. Instead of testing it now. Write the algorithm in pseudo-code first to check if any
                    //          scenarios were missed. Attention:
                    //              - Clock identifiers must be valid.
                    //              - Remember scheduling. When to do it, when to cancel it.
                    //              - Establish only after receiving both positive answer and compatible metadata.
                    //              - Closing the link can happen as soon as the peer's metadata is received and an answer is sent back.
                    //      2. Optimize pseudo-code, but keep the original one. The original one can help explain the algorithm.
                    case CANCELLING -> {
                        // A link is set to the CANCELLING state when the establishment of the link is no
                        // longer desired but the peer's metadata and answer have not yet been received.
                        if(link.isWaitingPeerMetadata()){
                            // if waiting for peer metadata and LINKREPLY does not have
                            // metadata, then wait for LINK msg.
                            if(!payload.hasInt("protocolId")){
                                link.setPeerClockId(peerClockId);
                                link.setLinkReplyMsgReceived(replyCode);
                            }else{
                                // peer's metadata and answer are present in the LINKREPLY message,
                                // so the socket can answer with a "fatal" LINKREPLY msg if the
                                // peer is in a LINKING state, i.e., if its answer was positive.
                                if(isPositiveLinkingCode(replyCode)) {
                                    byte[] respPayload =
                                            createLinkReplyMsgWithoutMetadata(link.getClockId(), AC_CANCELED).serialize();
                                    dispatch(peerId, MsgType.LINKREPLY, respPayload);
                                }
                                closeLink(link);
                            }
                        }
                        // If the socket is not waiting for the peer's metadata, then the socket
                        // has already received a LINK msg and sent a positive LINKREPLY msg in response.
                        // So, if the peer's LINKREPLY msg is positive, the peer assumes the LINK has established,
                        // meaning, the socket needs to pass to an UNLINKING state. If the peer's response is negative,
                        // then, the link can be simply closed.
                        else {
                            // since a peer's message has been received, asserting the clock is the
                            // same across the peer's LINK and LINKREPLY messages is necessary.
                            if(peerClockId == link.getPeerClockId()){
                                if(isPositiveLinkingCode(replyCode)) {
                                    link.setState(LinkState.UNLINKING);
                                    dispatch(peerId, MsgType.UNLINK, createUnlinkMsg(link.getClockId()));
                                }else {
                                    closeLink(link);
                                }
                            }
                        }
                    }
                }
            }
            // Else: if link does not exist, ignore the message.
        } finally {
            lock().unlock();
        }
    }

    private void handleUnlinkMsg(SocketMsg msg) {
        SocketIdentifier peerId = msg.getSrcId();
        Integer peerClockId = parseUnlinkMsg(msg);
        if(peerClockId == null) return; // ignore if payload is invalid
        try {
            lock().lock();
            Link link = links.get(peerId);
            // if link exists
            if(link != null){
                // To accept an unlink request, the peer's clock identifier
                // must match the registered peer clock identifier.
                if(peerClockId < link.getPeerClockId()
                        || peerClockId > link.getPeerProtocolId())
                    return;
                boolean sendUnlinkMsg = false;
                switch (link.getState()){
                    // There is always a peer that reaches the ESTABLISHED state first.
                    // This means, that a socket may receive an UNLINK message,
                    // not only when in the ESTABLISHED state, but also while in a LINKING
                    // state waiting for the peer's answer, or in a CANCELLING state.
                    case LINKING, ESTABLISHED, CANCELLING -> {
                        // confirm that the peer's metadata has been
                        // received and an answer has been sent.
                        if(!link.isWaitingPeerMetadata())
                            sendUnlinkMsg = true;
                        else
                            return;
                    }
                }
                // If not in UNLINKING state, send an UNLINK message
                if(sendUnlinkMsg)
                    dispatch(peerId, MsgType.UNLINK, createUnlinkMsg(link.getClockId()));
                // Link closure occurs for all states as long as the peer's
                // UNLINK message matches the registered peer's clock identifier
                closeLink(link);
            }
        } finally {
            lock().unlock();
        }
    }

    // ********* Public Linking/Unlinking methods ********* //

    /**
     * Initiate a linking process for the provided socket identifier.
     * @param peerId peer's socket identifier
     * @throws IllegalArgumentException If socket identifier is null.
     * @throws IllegalStateException If the link is being closed or
     * if the limit of links has been reached.
     */
    public void link(SocketIdentifier peerId){
        if(peerId == null)
            throw new IllegalArgumentException("Socket identifier cannot be null.");
        try{
            lock().lock();
            Link link = links.get(peerId);
            if(link != null){
                // if link is established or attempting to link, there is nothing to do.
                // If link is in a state that unlinking will follow, then throw an exception
                // informing that the link cannot be established because it is already closing.
                LinkState state = link.getState();
                if(state == LinkState.UNLINKING || state == LinkState.CANCELLING)
                    throw new IllegalStateException("Link is currently being closed. Try again later.");
            }else{
                // Link does not exist, so create and register it if
                // the maximum amount of links has not been reached.
                int maxLinks = socket.getOption("maxLinks", Integer.class);
                if(links.size() < maxLinks) {
                    // Each link created is given a different clock identifier.
                    link = createLinkingLink(peerId);
                    // send a LINK message to the peer
                    SerializableMap map = createLinkRequestMsg(link.getClockId());
                    dispatch(peerId, MsgType.LINK, map.serialize());
                }else{
                    throw new IllegalStateException("Maximum amount of links has been reached.");
                }
            }
        }finally {
            lock().unlock();
        }
    }

    /**
     * Initiate an unlinking process for the provided socket identifier.
     * Can be used to cancel an ongoing linking process.
     * @param peerId peer's socket identifier
     */
    public void unlink(SocketIdentifier peerId){
        if(peerId == null)
            throw new IllegalArgumentException("Socket identifier cannot be null.");
        try {
            lock().lock();
            Link link = links.get(peerId);
            if(link != null){
                switch (link.getState()){
                    case LINKING -> {
                        // try cancelling scheduled link request
                        SocketMsg scheduled = link.cancelScheduledMessage();
                        // if message was canceled, remove link
                        if(scheduled != null)
                            closeLink(link);
                        else{
                            // Else, switch to a "cancelling" state
                            link.setState(LinkState.CANCELLING);
                        }
                    }
                    case ESTABLISHED -> {
                        // if link is established, changed it to UNLINKING
                        // and initiate the unlinking process by sending
                        // an unlink message.
                        link.setState(LinkState.UNLINKING);
                        dispatch(peerId, MsgType.UNLINK, createUnlinkMsg(link.getClockId()));
                    }
                    // If state is UNLINKING or CANCELLING, then
                    // the "unlinking" process is already in progress.
                    //case UNLINKING, CANCELLING -> {}
                }
            }
        } finally {
            lock().unlock();
        }
    }

    public boolean isLinkState(SocketIdentifier peerId, Predicate<LinkState> predicate){
        try{
            lock().lock();
            Link link = links.get(peerId);
            LinkState state = link != null ? link.getState() : null;
            return predicate.test(state);
        } finally {
            lock().unlock();
        }
    }

    public boolean isLinked(SocketIdentifier peerId){
        return isLinkState(peerId, linkState -> linkState == LinkState.ESTABLISHED);
    }

    public boolean isLinking(SocketIdentifier peerId){
        return isLinkState(peerId, linkState -> linkState == LinkState.LINKING);
    }

    public boolean isUnlinking(SocketIdentifier peerId){
        return isLinkState(peerId, linkState -> linkState == LinkState.UNLINKING
                                             || linkState == LinkState.CANCELLING);
    }

    public boolean isUnlinked(SocketIdentifier peerId){
        return isLinkState(peerId, linkState -> linkState == null
                                             || linkState == LinkState.CLOSED);
    }

    // ********* Handle Data & Control Messages ********* //

    // TODO - all socket messages should carry the clock identifier. Put it as part of the SocketMsg?
    //          If applied, don't forget to verify that messages that do not have a matching identifier
    //          must be discarded.
    private boolean handleDataOrControlMsg(SocketMsg msg) {
        boolean handled = true;
        SocketIdentifier peerId = msg.getSrcId();
        try {
            lock().lock();
            Link link = links.get(peerId);
            if (link != null) {
                switch (link.getState()){
                    case ESTABLISHED -> handled = false;
                    case LINKING -> {
                        // If in a LINKING state and if the peer's metadata has been received, the
                        // reception of a data message can be interpreted as a positive reply code.
                        if(!link.isWaitingPeerMetadata()) {
                            establishLink(link);
                            handled = false;
                        }else dispatchNotLinkedErrorMsg(peerId, msg);
                    }
                    // data/control messages received while in UNLINKING, CANCELLING
                    // or CLOSED state, will not be processed, so mark it as handled,
                    // and ignore the message.
                    // default -> {}
                }
            }else dispatchNotLinkedErrorMsg(peerId, msg);
            return handled;
        }finally {
            lock().unlock();
        }
    }

    // ********* Handle Flow-control Message  ********* //

    /**
     * Handles a credits flow control message.
     * @param msg not null flow control message
     * @apiNote Thread must hold socket lock when the method is called.
     */
    void handleFlowControlMsg(SocketMsg msg){
        assert msg != null;
        try{
            lock().lock();
            Link link = links.get(msg.getSrcId());
            if(link != null) {
                FlowCreditsPayload fcp = FlowCreditsPayload.convertFrom(msg.getPayload());
                assert fcp != null;
                link.adjustOutgoingCredits(fcp.getCredits());
            }
        } finally {
            lock().unlock();
        }
    }

    /**
     * Sends credits to the peer associated with the provided link.
     * @param batch credits to be sent. Can be negative if removing
     *              credits from the peer is desirable.
     */
    @Override
    public void onBatchReadyEvent(Link link, int batch) {
        FlowCreditsPayload payload = new FlowCreditsPayload(batch);
        dispatch(link.getDestId(), payload.getType(), payload.getPayload());
    }

    // ********* Main Handle Msg and Handle Error Msg Methods ********* //

    /**
     * Handles linking/unlinking logic messages.
     * @param msg socket message to be handled.
     * @implNote Any invalid messages provided are ignored.
     */
    boolean handleMsg(SocketMsg msg) {
        if (msg == null) return true;
        boolean ret = true;
        switch (msg.getType()) {
            case MsgType.DATA -> ret = handleDataOrControlMsg(msg);
            case MsgType.FLOW -> handleFlowControlMsg(msg);
            case MsgType.LINK -> handleLinkMsg(msg);
            case MsgType.LINKREPLY -> handleLinkReplyMsg(msg);
            case MsgType.UNLINK -> handleUnlinkMsg(msg);
            case MsgType.ERROR -> ret = handleErrorMsg(msg);
            // if it's not one of the types above, it is assumed to be a control message
            default -> ret = handleDataOrControlMsg(msg);
        }
        return ret;
    }

    private boolean handleErrorMsg(SocketMsg msg) {
        assert msg != null;
        // convert payload
        ErrorPayload payload = ErrorPayload.parseFrom(msg.getPayload());
        // log faulty payload
        if(payload == null){
            // Ignore faulty error message for now.
            Logger.getLogger(socket.getId().toString()).warning("Received faulty error msg payload: " +
                    "{\n\tBytes: " + Arrays.toString(msg.getPayload()) +
                    ",\n\tUTF8: " + StandardCharsets.UTF_8.decode(ByteBuffer.wrap(msg.getPayload())) + "}");
            return true;
        }
        boolean handled = false;
        // handle socket not found message since it may have
        // been triggered by a link request to a socket that
        // does exist (yet, hopefully)
        if (payload.getCode() == ErrorType.SOCK_NFOUND) {
            handleSocketNotFoundError(msg);
            handled = true;
        }
        return handled;
    }

    private void handleSocketNotFoundError(SocketMsg msg) {
        try {
            lock().lock();
            Link link = links.get(msg.getSrcId());
            // If link exists and the socket has not received
            // a LINK or LINKREPLY message from the peer,
            // then schedule a new request if in a LINKING state,
            // or close the LINK if in a CANCELLING state.
            // Otherwise, ignore the message.
            if(link != null){
                if(link.isWaitingPeerMetadata()
                        && link.isLinkReplyMsgReceived() == null){
                    switch (link.getState()){
                        case LINKING -> scheduleLinkRequest(link);
                        case CANCELLING -> closeLink(link);
                    }
                }
            }
        } finally {
            lock().unlock();
        }
    }

    // ********* Link Socket Methods ********* //
    Map<SocketIdentifier, LinkSocket> linkSockets = new HashMap<>();

    /**
     * @param peerId identifier of the peer's socket with which a link exists (even if not established)
     * @return link socket if a link exists, even if not established, with the peer. null, otherwise.
     */
    public LinkSocket getLinkSocket(SocketIdentifier peerId){
        try{
            lock().lock();
            LinkSocket linkSocket = linkSockets.get(peerId);
            if(linkSocket == null){
                Link link = links.get(peerId);
                if(link != null) {
                    linkSocket = new LinkSocket(link, this);
                    linkSockets.put(peerId, linkSocket);
                }
            }
            return linkSocket;
        }finally {
            lock().unlock();
        }
    }

    /**
     * Polls a link for I/O events.
     * @param link link instance
     * @param pt poll table
     * @return available I/O events
     */
    int pollLink(Link link, PollTable pt) {
        try{
            lock().lock();
            return link.poll(pt);
        } finally {
            lock().unlock();
        }
    }

    /**
     * Polls a link for I/O events.
     * @param peerId identifier of the peer's socket
     * @param pt poll table
     * @return available I/O events
     */
    public int pollLink(SocketIdentifier peerId, PollTable pt) {
        try{
            lock().lock();
            Link link = links.get(peerId);
            if(link == null)
                throw new IllegalArgumentException("Peer does not have an associated link.");
            return link.poll(pt);
        } finally {
            lock().unlock();
        }
    }

    /**
     * Waits until an incoming message, from the peer associated with this
     * link, is available or the deadline is reached or the thread is interrupted.
     * If the socket is in COOKED mode, the returned messages are data messages.
     * If in RAW mode, the returned messages may also be custom control messages.
     * @param link link from which the message should be received
     * @param deadline waiting limit to poll an incoming message
     * @return available incoming message or "null" if the operation timed out
     * without a message becoming available.
     * @apiNote Caller must not hold socket lock as it will result in a deadlock
     * when a blocking operation with a non-expired deadline is requested.
     */
    SocketMsg receive(Link link, Long deadline) throws InterruptedException {
        SocketMsg msg;
        WaitQueueEntry wait;
        ParkStateWithEvents ps;
        boolean timedOut = Timeout.hasTimedOut(deadline);

        try {
            lock().lock();
            // if link is closed and queue is empty, messages cannot be received.
            if (link.getState() == Link.LinkState.CLOSED && link.hasIncomingMessages())
                throw new LinkClosedException();
            // try retrieving a message immediately
            msg = link.tryPollingMessage();
            if (msg != null) return msg;
            // return if timed out
            if (timedOut) return null;
            // If a message could not be retrieved, make the caller a waiter
            wait = link.queueDirectEventWaiter(PollFlags.POLLIN, true);
            ps = (Link.ParkStateWithEvents) wait.getPriv();
        } finally {
            lock().unlock();
        }

        while (true) {
            if (timedOut || link.getState() == Link.LinkState.CLOSED)
                break;
            if (Thread.currentThread().isInterrupted()) {
                wait.delete();
                throw new InterruptedException();
            }
            // Wait until woken up
            WaitQueueEntry.defaultWaitFunction(wait, ps, deadline, true);
            try {
                lock().lock();
                // attempt to poll a message
                msg = link.tryPollingMessage();
                if (msg != null) break;
                // if a message was not available and the operation has not
                // timed out, sets parked state to true preemptively,
                // so that a notification is not missed
                timedOut = Timeout.hasTimedOut(deadline);
                if (!timedOut) ps.parked.set(true);
            } finally {
                lock().unlock();
            }
        }
        // delete entry since a hanging wait queue entry is not desired.
        wait.delete();
        return msg;
    }

    /**
     * Waits until an incoming message, from the peer associated with this
     * link, is available or the deadline is reached or the thread is interrupted.
     * If the socket is in COOKED mode, the returned messages are data messages.
     * If in RAW mode, the returned messages may also be custom control messages.
     * @param peerId identifier of the socket peer from which the message should be received
     * @param deadline waiting limit to poll an incoming message
     * @return available incoming message or "null" if the operation timed out
     * without a message becoming available.
     * @apiNote Caller must not hold socket lock as it will result in a deadlock
     * when a blocking operation with a non-expired deadline is requested.
     */
    SocketMsg receive(SocketIdentifier peerId, Long deadline) throws InterruptedException {
        Link link = getLink(peerId);
        return receive(link, deadline);
    }

    /**
     * Sends message to the peer associated with this link. This method
     * is not responsible for verifying if the control message follows
     * the custom semantics of the socket.
     * @param link link to which the message should be sent.
     * @param payload message content
     * @param deadline timestamp at which the method must return
     *                 indicating a timeout if sending was not possible
     *                 due to the lack of permission (i.e. credits).
     * @return "true" if message was sent. "false" if permission
     *          was not acquired during the specified timeout.
     * @throws IllegalArgumentException If the payload is null or if the payload type
     * corresponds to a reserved type other than DATA.
     * @throws InterruptedException If thread was interrupted.
     * @apiNote Caller must not hold socket lock as it will result in a deadlock
     * when a blocking operation with a non-expired deadline is requested.
     */
    boolean send(Link link, Payload payload, Long deadline) throws InterruptedException {
        boolean ret = false;
        WaitQueueEntry wait;
        ParkStateWithEvents ps;
        boolean timedOut = Timeout.hasTimedOut(deadline);

        if(payload == null)
            throw new IllegalArgumentException("Invalid payload.");

        // if payload is a reserved type different from DATA,
        // then the send operation is not allowed.
        if(MsgType.isReservedType(payload.getType())
                && payload.getType() != MsgType.DATA)
            throw new IllegalArgumentException("Invalid payload type.");

        try {
            lock().lock();
            // if link is closed, messages cannot be sent.
            if (link.getState() == Link.LinkState.CLOSED)
                throw new LinkClosedException();
            // If message is a control message, then it can be dispatched immediately.
            // In case of a data message, permission from the flow control is required.
            if(payload.getType() != MsgType.DATA || link.hasOutgoingCredits()) {
                dispatch(link.getDestId(), payload);
                return true;
            }
            // return if timed out
            if (timedOut) return false;
            // If the message could not be sent, make the caller a waiter
            wait = link.queueDirectEventWaiter(PollFlags.POLLOUT, true);
            ps = (ParkStateWithEvents) wait.getPriv();
        } finally {
            lock().unlock();
        }

        while (true) {
            if (timedOut || link.getState() == Link.LinkState.CLOSED)
                break;
            if (Thread.currentThread().isInterrupted()) {
                wait.delete();
                throw new InterruptedException();
            }
            // Wait until woken up
            WaitQueueEntry.defaultWaitFunction(wait, ps, deadline, true);
            try {
                lock().lock();
                // attempt to send the message again
                if(link.hasOutgoingCredits())  {
                    dispatch(link.getDestId(), payload);
                    ret = true;
                    break;
                }
                // if permission to send the message was not granted and
                // the operation has not timed out,sets parked state to
                // true preemptively, so that a notification is not missed
                timedOut = Timeout.hasTimedOut(deadline);
                if (!timedOut) ps.parked.set(true);
            } finally {
                lock().unlock();
            }
        }
        // delete entry since a hanging wait queue entry is not desired.
        wait.delete();
        return ret;
    }

    /**
     * Sends message to linked peer. This method is not responsible for
     * verifying if the control message follows the custom semantics of the socket.
     * @param peerId identifier of the peer's socket
     * @param payload message content
     * @param deadline timestamp at which the method must return
     *                 indicating a timeout if sending was not possible
     *                 due to the lack of permission (i.e. credits).
     * @return "true" if message was sent. "false" if permission
     *          was not acquired during the specified timeout.
     * @throws IllegalStateException If the peer is not linked.
     * @throws IllegalArgumentException If the payload is null or if the payload type
     * corresponds to a reserved type other than DATA.
     * @throws InterruptedException If thread was interrupted
     * @apiNote Caller must not hold socket lock as it will result in a deadlock
     * when a blocking operation with a non-expired deadline is requested.
     */
    boolean send(SocketIdentifier peerId, Payload payload, Long deadline) throws InterruptedException {
        Link link = getLink(peerId);
        return send(link, payload, deadline);
    }
}
