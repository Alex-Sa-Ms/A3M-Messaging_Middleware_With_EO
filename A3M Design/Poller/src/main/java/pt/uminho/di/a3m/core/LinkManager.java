package pt.uminho.di.a3m.core;

import com.google.protobuf.InvalidProtocolBufferException;
import pt.uminho.di.a3m.core.messaging.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.logging.Logger;

import pt.uminho.di.a3m.core.LinkNew.LinkState;
import pt.uminho.di.a3m.poller.PollFlags;

public class LinkManager {
    private final Socket socket;
    final Map<SocketIdentifier, LinkNew> links = new HashMap<>();
    private int clock = 0; // TODO - starting at 0 to simplify debugging, but then change to Integer.MIN_VALUE
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

    ///** @return message dispatcher */
    //private MessageDispatcher dispatcher(){
    //    return socket.getDispatcher();
    //}

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
        assert dest != null;
        SocketMsg msg = new SocketMsg(socket.getId(), dest, type, payload);
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
        assert dest != null;
        SocketMsg msg = new SocketMsg(socket.getId(), dest, type, payload);
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

    // ********* Linking Acknowledgment Codes ********* //
    // Zero for success.
    // Non-fatal refusal codes are positive.
    // Fatal refusal codes are negative.

    private static final int AC_SUCCESS = 0;

    // **** Non-fatal refusal codes **** //

    // Already linked. Used to reschedule linking process to
    // allow the ongoing unlinking process to finish.
    private static final int AC_ALREADY_LINKED = 1;
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

    private boolean isPositiveLinkingCode(int ackcode){
        return ackcode == AC_SUCCESS;
    }

    private boolean isFatalLinkingCode(int ackcode){
        return ackcode < 0;
    }

    private boolean isNonFatalLinkingCode(int ackcode){
        return ackcode > 0;
    }

    // ********* Creation & Parsing of Link-related messages ********* //

    // TODO - delete after debugging
    final static Lock printLock = new ReentrantLock();
    
    /**
     * Creates link request payload.
     * @param clockId link's clock identifier
     * @return serializable map which corresponds to the link request payload.
     */
    private SerializableMap createLinkRequestMsg(int clockId){
        SerializableMap map = new SerializableMap();
        int protocolId = socket.getProtocol().id();
        int credits = socket.getOption("peerCapacity", Integer.class);
        map.putInt("protocolId", protocolId);
        map.putInt("credits", credits);
        map.putInt("clockId", clockId);
        return map;
    }

    private void logParseErrorOnLinkReqOrAckMsg(SocketMsg msg){
        Logger.getLogger(socket.getId().toString())
                .warning("Invalid " + (msg.getType() == MsgType.LINK ? "link request" : "link acknowledgement")
                        + "from " + msg.getSrcId() + " : " + Arrays.toString(msg.getPayload()));
    }

    /**
     * Converts message's payload (byte array) to serializable map if possible.
     * Then, checks if the mandatory fields of a link request / link acknowledgement
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
        logParseErrorOnLinkReqOrAckMsg(msg);
        return null;
    }

    /**
     * Creates link acknowledgement payload. Identical to the link request
     * payload but enables an acknowledgement code to be provided.
     * @param clockId link's clock identifier
     * @param ackCode acknowledgement code
     * @return serializable map which corresponds to the link request payload.
     */
    private SerializableMap createLinkAckMsgWithMetadata(int clockId, int ackCode) {
        SerializableMap map = createLinkRequestMsg(clockId);
        map.putInt("ackCode", ackCode);
        return map;
    }

    private SerializableMap createLinkAckMsgWithoutMetadata(int clockId, int ackCode) {
        SerializableMap map = new SerializableMap();
        map.putInt("clockId", clockId);
        map.putInt("ackCode", ackCode);
        return map;
    }

    private SerializableMap parseLinkAckMsg(SocketMsg msg) {
        try {
            SerializableMap map = SerializableMap.deserialize(msg.getPayload());
            if(!map.hasInt("clockId") || !map.hasInt("ackCode"));
            else return map;
        } catch (InvalidProtocolBufferException ignored) {}
        // if invalid payload, log it, and return null
        logParseErrorOnLinkReqOrAckMsg(msg);
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
    private Integer parseUnlinkMsg(SocketMsg msg){
        if(msg.getPayload() != null && msg.getPayload().length == 4)
            return ByteBuffer.wrap(msg.getPayload()).getInt();
        else
            return null;
    }

    // ********* Linking/Unlinking logic ********* //

    // TODO 0 - check which methods may be moved to the new Link class

    // TODO 0 - make restrictions on the clock identifiers where they are required

    // TODO 0 - check where the waitingLinkMsg/waitingUnlinkMsg should be reset.
    //  These variables should be reset right after receiving the required message.

    /* TODO 1 - what happens when a DATA/CONTROL message is received before the link is established?
          Accumulate messages in the incoming queue and when the link is established, feed them
          again to the socket's handle custom msg?
          - Solution 1:
              1. Link queue should only be initialized using the socket's getInQueueSupplier when
              the link passes to established.
              2. When a message is received before the link is established, the inQueue is initialized
              to a LinkedList<> so that the messages can be stored until the link establishment.
              Problem: Introduces vulnerability. Messages can be bombarded before the arrival of the
              message that accepts the link. Since the exactly-once semantic means not discarding messages,
              the memory could be easily exhausted using this exploit. However, for other solution to
              not be an exploit, we need to have an "abuser detector" on the messages received using the discussed
              "acceptable range" when a change in the window for a lower value is performed.
              Problem 2: Even with such detector mechanism, the control messages could still be used for this purpose,
              as they are not influenced by flow control. To prevent control messages from being used for such purposes,
              these must not be queued and by being handled immediately, one could detect contabilize faulty messages
              and add the socket or link in question to a blacklist. The best solution would actually be adding the socket
              to a firewall to block its traffic.
          - Solution 2:
            1. Do SYN -> SYN/ACK -> ACK, i.e., LINK -> LINKACK -> LINKACK (the last one may carry the clockId and success code only).
                    - Currently, when a socket receives a LINK msg from a compatible socket, it creates a
                    link and immediately sets it to established. This means, that data and control messages
                    can be sent right after, potentially reaching the destination before the LINKACK message
                    that would make the requester establish the link on its side as well. If a data/control
                    message reaches the requester before the link is established, the requester does not know
                    what kind of socket it is talking to, so it can't possibly process the message. Queuing
                    the messages could be a solution until the establishment of the link, but taht would create
                    the exploit mentioned above, so, the most appropriate solution is in fact queuing the messages
                    on the sender (in a outgoing queue) and dispatch them only after receiving a LINKACK message
                    from the other peer.
    */


    // TODO 2 - see where notifying waiters and creating socket events is required
    //  - set credits to zero when unlinking to prevent sending of data messages?

    // TODO 3 - pass methods in the Link class to this class

    // TODO 4 - since there are "wait for link" methods which wait for a link
    //  to be established, then there should be a "wait for link closure" also.

    /** @return true if the peer metadata has not yet been received. */
    private boolean isWaitingPeerMetadata(LinkNew link){
        // If the peer protocol has not yet been received,
        // then we assume the metadata has not yet been received.
        return link.getPeerProtocolId() == null;
    }

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
     * by sending an imcompatible refusal code in a LINKACK, then closes the link.
     * If compatible, sends success code in a LINKACK and establishes the link.
     * @param link link to be checked
     * @param establish flag should be set when, in addition to sending the LINKACK message,
     *                 establishing the link is desirable. If not set, then the LINKACK message
     *                  is sent, but the link is not established, meaning it remains in the same
     *                  state (which should be LINKING) until the peer's LINKACK message is received
     *                  and the decision about the establishment can be made.
     * @return true if compatible. false, otherwise.
     */
    private boolean checkCompatibilityThenAcceptOrReject(LinkNew link, boolean establish) {
        boolean compatible;
        byte[] payload;
        // if a link request is scheduled, cancel it immediately
        SocketMsg scheduled = link.cancelScheduledMessage();

        if(isPeerCompatible(link.getPeerProtocolId())){
            // if a link request was not scheduled, the socket sends a LINKACK with a success code
            // but without metadata since a LINK msg was already sent to the peer
            // containing the metadata. The socket also establishes the link.
            if (scheduled == null) {
                payload = createLinkAckMsgWithoutMetadata(link.getClockId(),AC_SUCCESS).serialize();
                if (establish) establishLink(link);
            }
            // Else, if there was a scheduled link request, we remain in
            // a LINKING state, send a LINKACK message with metadata
            // and a success code, and wait for the peer to confirm the establishment.
            else payload = createLinkAckMsgWithMetadata(link.getClockId(),AC_SUCCESS).serialize();
            compatible = true;
        }else{
            // if incompatible, create LINKACK message with incompatible (fatal) refusal code,
            // and add metadata depending on whether a LINK msg is considered in "flight" or not,
            // as done above when the peer is compatible.
            if(scheduled == null)
                // not scheduled, so considered "in flight"
                payload = createLinkAckMsgWithoutMetadata(link.getClockId(),AC_INCOMPATIBLE).serialize();
            else
                payload = createLinkAckMsgWithMetadata(link.getClockId(),AC_INCOMPATIBLE).serialize();
            closeLink(link);
            compatible = false;
        }
        dispatch(link.id.destId(), MsgType.LINKACK, payload);
        return compatible;
    }

    /**
     * Method invoked when the link has not yet been created.
     * Checks if linking condtions are met, such as the peer being compatible, etc.
     * Rejects with non-fatal or fatal refusal code depending on the failed linking condition.
     * If all linking conditions are met, sends success code in a LINKACK and establishes the link.
     * @return link state, if link was accepted. null, if rejected.
     */
    private LinkNew checkLinkingConditionsThenAcceptOrReject(SocketIdentifier peerId, int peerProtocolId, int peerClockId, int outCredits) {
        LinkNew link = null;
        byte[] payload = null;

        // if socket is closing or is closed, send fatal refusal
        if(socket.getState() == SocketState.CLOSING
                || socket.getState() == SocketState.CLOSED)
            payload = createLinkAckMsgWithMetadata(0, AC_CLOSED).serialize();

        // if incoming link requests are not allowed, send fatal refusal
        boolean allowIncomingLinkRequests = socket.getOption("allowIncomingLinkRequests", Boolean.class);
        if(!allowIncomingLinkRequests)
            payload = createLinkAckMsgWithMetadata(0, AC_INCOMING_NOT_ALLOWED).serialize();

        // If max links limit has been reached. The link cannot be established
        // at the moment, but may be established in the future.
        int maxLinks = socket.getOption("maxLinks", Integer.class);
        if(links.size() >= maxLinks) {
            payload = createLinkAckMsgWithMetadata(0, AC_TMP_NAVAIL).serialize();
        }
        // If the peer's protocol is not compatible with the socket's protocol,
        // send a fatal refusal reason informing the incompatibility
        if(!isPeerCompatible(peerProtocolId))
            payload = createLinkAckMsgWithMetadata(0, AC_INCOMPATIBLE).serialize();

        // if all linking conditions are met
        if(payload == null) {
            // All requirements have been passed, so create link and set it to "linking".
            // A LINKACK message is sent to inform willingness to establish the link,
            // and a LINKACK message is expected to be returned to confirm the establishment.
            link = createLinkingLink(peerId, peerProtocolId, peerClockId, outCredits);
            payload = createLinkAckMsgWithMetadata(link.getClockId(), AC_SUCCESS).serialize();
        }

        // dispatch LINKACK message
        dispatch(peerId, MsgType.LINKACK, payload);
        return link;
    }

    private void scheduleLinkRequest(LinkNew link){
        // make sure peer information is reset
        link.setPeerProtocolId(null);
        link.setPeerClockId(Integer.MIN_VALUE);
        link.outFCS.applyCreditVariation(-link.outFCS.getCredits());

        byte[] linkReqPayload =
                createLinkRequestMsg(link.getClockId()).serialize();
        long retryInterval = socket.getOption("retryInterval", Long.class);
        long dispatchTime = retryInterval + System.currentTimeMillis();
        AtomicReference<SocketMsg> scheduled =
                scheduleDispatch(link.id.destId(), MsgType.LINK, linkReqPayload, dispatchTime);
        link.setScheduled(scheduled);
    }
    
    /**
     * Sets peer's information received on a LINK/LINKACK message.
     * This method limits itself to setting the values. It does not
     * do any special procedures, such as waking up waiters.
     * @param link link instance related to the peer
     * @param protocolId peer's protocol identifier
     * @param clockId peer's clock identifier
     * @param outCredits peer's provided credits
     */
    private void setPeerInformation(LinkNew link, int protocolId, int clockId, int outCredits){
        // set peer's protocol
        link.setPeerProtocolId(protocolId);
        // update peer's link clock identifier
        link.setPeerClockId(clockId);
        // update outgoing credits
        link.outFCS.applyCreditVariation(outCredits);
    }
    
    /**
     * Closes link and removes it from the links collection
     * @param link link to be closed and removed
     */
    private void closeLink(LinkNew link){
        link.close();
        links.remove(link.id.destId());
        // TODO - if socket is closing and "links" is empty,
        //     then, invoke socket internal close.
    }
    
    /**
     * Creates link with default incoming capacity.
     * <p>The link is inserted in the "links" collection.
     * @param sid peer's socket identifier
     * @return link with 'null' state
     */
    private LinkNew createLink(SocketIdentifier sid){
        int peerCapacity = socket.getOption("peerCapacity", Integer.class);
        LinkIdentifier linkId = new LinkIdentifier(socket.getId(), sid);
        LinkNew link = new LinkNew(linkId, clock, peerCapacity);
        links.put(sid, link);
        // The clock identifiers are provided in an increasing order,
        // as a form of causal consistency.
        clock++;
        return link;
    }

    /**
     * Create link and set it to LINKING state. To be used
     * when link() is invoked and the link does not exist.
     * <p>The link is inserted in the "links" collection.
     * @param sid peer's socket identifier
     * @return link in LINKING state
     */
    private LinkNew createLinkingLink(SocketIdentifier sid){
        LinkNew link = createLink(sid);
        // set state to LINKING
        link.state.set(LinkState.LINKING);
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
    private LinkNew createLinkingLink(SocketIdentifier sid, int peerProtocolId, int peerClockId, int outCredits){
        LinkNew link = createLink(sid);
        link.state.set(LinkState.LINKING);
        setPeerInformation(link, peerProtocolId, peerClockId, outCredits);
        return link;
    }

    private void establishLink(LinkNew link, int peerProtocolId, int peerClockId, int outCredits){
        setPeerInformation(link, peerProtocolId, peerClockId, outCredits);
        establishLink(link);
    }

    private void establishLink(LinkNew link){
        link.state.set(LinkState.ESTABLISHED);
        // wakes waiters if outgoing credits are positive
        int outCredits = link.outFCS.getCredits();
        if(outCredits > 0)
            link.waitQ.fairWakeUp(0,outCredits,0,PollFlags.POLLOUT);
        // TODO - create link established event
    }

    private void dispatchNonLinkedErrorMsg(SocketIdentifier peerId, SocketMsg msg){
        byte[] errorPayload = new ErrorPayload(ErrorType.SOCK_NLINKED).getPayload();
        dispatch(peerId, MsgType.ERROR, errorPayload);
        Logger.getLogger(socket.getId().toString()).warning("Not linked peer (" + peerId + ") sent: " + msg.toString());
    }

    /**
     * Handles linking/unlinking logic messages.
     * @param msg socket message to be handled.
     * @implNote Any invalid messages provided are ignored.
     */
    void handleMsg(SocketMsg msg) {
        if (msg == null)
            return;
        switch (msg.getType()) {
            case MsgType.DATA -> handleDataOrControlMsg(msg);
            //case MsgType.FLOW -> handleFlowControlMsg(msg);
            case MsgType.LINK -> handleLinkMsg(msg);
            case MsgType.LINKACK -> handleLinkAckMsg(msg);
            case MsgType.UNLINK -> handleUnlinkMsg(msg);
            //case MsgType.ERROR -> handleErrorMsg(msg);
            default -> handleDataOrControlMsg(msg); // if it's not one of the types above, it is assumed to be a control message
        }
    }

    // TODO - control and data messages should carry the clock identifier too.
    private void handleDataOrControlMsg(SocketMsg msg) {
        SocketIdentifier peerId = msg.getSrcId();
        // TODO - handleControlMsg()
        Payload payload;
        if(msg.getType() == MsgType.DATA){
            //payload = parseDataMsg(msg);
        }else{
            //payload = parseControlMsg(msg);
        }
        //if(payload == null) return; // if payload is invalid
        try {
            lock().lock();
            LinkNew link = links.get(peerId);
            // If link exists
            if (link != null) {
                switch (link.state.get()){
                    case ESTABLISHED -> {
                        if(msg.getType() == MsgType.DATA){
                            // TODO - handleDataMsg() in ESTABLISHED state
                        }else {
                            // TODO - handleControlMsg() in ESTABLISHED state
                        }
                        return;
                    }
                    case LINKING -> {
                        // If in a LINKING state and if the peer's
                        // metadata has been received, the reception
                        // of a data message can be interpreted as
                        // a positive ack code.
                        if(!isWaitingPeerMetadata(link)) {
                            establishLink(link);
                            if(msg.getType() == MsgType.DATA){
                                // TODO - handleDataMsg(), invoke same method here as invoked in the ESTABLISHED case
                            }else {
                                // TODO - handleControlMsg(), invoke same method here as invoked in the ESTABLISHED case
                            }
                            return;
                        }
                    }
                    // data/control messages received while in UNLINKING, CANCELLING
                    // or CLOSED state, will not be processed
                    default -> { return; }
                }
            }
            // If link does not exist, or if in LINKING state and not expecting a data/control message,
            // then send error "socket not linked" and discard the message.
            dispatchNonLinkedErrorMsg(peerId, msg);
        }finally {
            lock().unlock();
        }
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
            LinkNew link = links.get(peerId);
            // If link exists
            if(link != null){
                // Discard messages without an identifier that is not
                // at least as new as the current one
                if(peerClockId < link.getPeerClockId()) return;
                switch (link.state.get()){
                    case LINKING -> {
                        // To be in a LINKING state:
                        //  1. (THIS SITUATION) The socket must have either
                        // invoked link() and is waiting the peer's metadata,
                        //  2. The socket has received a LINK msg with the peer's metadata
                        // and is only waiting for the confirmation (LINKACK) to establish the link.

                        if(link.isLinkAckMsgReceived() != null){
                            // ignore link messages that have a clock identifier
                            // that does not match the wanted clock identifier
                            if(peerClockId != link.getPeerClockId())
                                return;
                            // save and then reset the waiting variable
                            int peerAckCode = link.isLinkAckMsgReceived();
                            link.setLinkAckMsgReceived(null);
                            // if the received code is fatal, we need
                            // to set in progress an unlinking process
                            if(isFatalLinkingCode(peerAckCode)) {
                                closeLink(link);
                                return;
                            }
                            // if a non-fatal ack code was received and
                            // the peer is compatible, we can schedule a
                            // new link request. Otherwise, close the link.
                            else if(isNonFatalLinkingCode(peerAckCode)) {
                                if (isPeerCompatible(peerProtocolId)) {
                                    scheduleLinkRequest(link);
                                } else {
                                    closeLink(link);
                                }
                                return;
                            }
                            // Else, if received a positive ack code,
                            // the socket must accept or reject the link request.
                            // else if (isPositiveLinkingCode(link.isWaitingLinkMsg()) == 0)
                        }

                        if(isWaitingPeerMetadata(link)){
                            // Peer has invoked link() and is waiting for metadata (in a LINK or LINKACK msg).
                            setPeerInformation(link, peerProtocolId, peerClockId, outCredits);
                            // Send LINKACK message and close link if incompatible.
                            // If compatible, wait for the peer's LINKACK message to decide
                            // if the establishment should be done or not.
                            checkCompatibilityThenAcceptOrReject(link, false);
                        }
                    }
                    // Ignore message when ESTABLISHED. This cannot happen, as
                    // for a LINK/LINKACK message to be sent, the sender must not
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
                            // A LINKACK with non-fatal "ALREADY_LINKED" negative response
                            // is sent to reschedule the linking process, giving time to
                            // finish the current unlinking process.
                            // Closing the current link and creating a new one could also
                            // be an approach, however, we'll stick with this solution for now.
                            byte[] ackPayload = createLinkAckMsgWithMetadata(link.getClockId(), AC_ALREADY_LINKED).serialize();
                            dispatch(peerId, MsgType.LINKACK, ackPayload);
                        }
                    }
                    case CANCELLING -> {
                        // To be in a CANCELLING state, the socket wants to close the link
                        // but cannot since it has not yet received the peer's metadata,
                        // which may come in a LINK message.
                        // Since receiving a LINK message would initiate a linking process,
                        // we need to catch it before starting the unlinking process,
                        // to ensure the link is closed.

                        Integer peerAckCode = link.isLinkAckMsgReceived();
                        // If the peer's LINKACK message has not yet been received,
                        // or it has been received and has the peer's will to establish the
                        // link, then the socket must send a fatal LINKACK message to
                        // ensure the link is closed on the peer's side. If the peer
                        // has rejected (closed) the link before this LINKACK message arrives,
                        // the LINKACK message is simply discarded.
                        // After sending the LINKACK message, the link can be closed.
                        if(peerAckCode == null || isPositiveLinkingCode(peerAckCode)){
                            byte[] ackPayload = createLinkAckMsgWithoutMetadata(link.getClockId(), AC_CANCELED).serialize();
                            dispatch(peerId, MsgType.LINKACK, ackPayload);
                        }
                        // If the peer has sent a negative ack code, meaning it refused to link,
                        // the socket can simply close the link.
                        closeLink(link);
                    }
                }
            }
            // If link does not exist, check linking conditions and sends the appropriate answer.
            // If linking conditions are met, creates the link in a LINKING state, and waits for
            // link establishment confirmation, either through a positive code in a LINKACK message,
            // or by receiving a data/control message.
            else checkLinkingConditionsThenAcceptOrReject(peerId, peerProtocolId, peerClockId, outCredits);
        } finally {
            lock().unlock();
        }
    }

    private void handleLinkAckMsg(SocketMsg msg) {
        SocketIdentifier peerId = msg.getSrcId();
        SerializableMap payload = parseLinkAckMsg(msg);
        if(payload == null) return; // if payload is invalid
        int ackCode = payload.getInt("ackCode");
        int peerClockId = payload.getInt("clockId");
        int outCredits = payload.getInt("credits");
        //int peerProtocolId = payload.getInt("protocolId");
        try {
            lock().lock();
            LinkNew link = links.get(peerId);
            // If link exists
            if(link != null) {
                // discard messages with old clock identifiers
                if(peerClockId < link.getPeerClockId()) return;
                switch (link.state.get()){
                    case LINKING -> {
                        // if the socket is waiting for the peer's metadata
                        // and a LINKACK message was received
                        if(isWaitingPeerMetadata(link)){
                            // if the LINKACK contains the peer's metadata,
                            // then, the peer is not a linking process initiator
                            if(payload.hasInt("protocolId")){
                                int peerProtocolId = payload.getInt("protocolId");
                                setPeerInformation(link, peerProtocolId, peerClockId, outCredits);
                                // success linking code
                                if(isPositiveLinkingCode(ackCode)) {
                                    // since the peer did not initiate a linking
                                    // process with the socket, we can check the
                                    // compatibility and act accordingly, i.e.,
                                    // establish the link, or send a rejecting
                                    // LINKACK and close the link
                                    checkCompatibilityThenAcceptOrReject(link, true);
                                } else if (isFatalLinkingCode(ackCode)) {
                                    // if the LINKACK message contains a fatal
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
                                // the ack code received to the variable that
                                // informs if the socket has received the peer's LINKACK
                                // message before the peer's LINK message had arrived,
                                // and let the socket remain in a LINKING state.
                                // When the peer's LINK message arrives,
                                // the socket performs the appropriate behavior,
                                // such as removing the link, scheduling a
                                // new link request or establishing the link,
                                // depending on the ack code present in the variable.
                                link.setLinkAckMsgReceived(ackCode);
                            }
                        }
                        // Else, if the peer's metadata was already received,
                        // then the socket has received a LINK message with
                        // the peer's metadata, has sent a positive LINKACK answer 
                        // and is waiting for a LINKACK message, which is this one.
                        // This LINKACK message is required to determine if the link
                        // should be established or closed.
                        else{
                            // assert the clock identifiers match
                            if(peerClockId == link.getPeerClockId()){
                                // if the answer is positive, then establish the link
                                if(isPositiveLinkingCode(ackCode))
                                    establishLink(link);
                                // if answer is fatal, then close link.
                                // We can close the link, because it has already
                                // been close by the peer on its side.
                                else if(isFatalLinkingCode(ackCode))
                                    closeLink(link);
                                // else, if answer is (negative) non-fatal,
                                // then schedule a new link request
                                else scheduleLinkRequest(link);
                            }
                        }
                    }
                    // Ignore LINKACK messages when ESTABLISHED.
                    // case ESTABLISHED -> {}

                    // Receiving a LINKACK message when in UNLINKING state is not possible. To pass 
                    // to the UNLINKING state, one must have already received a LINKACK message that
                    // would result in the link being established.
                    // case UNLINKING -> {}
                    
                    case CANCELLING -> {
                        // A link is set to the CANCELLING state when the establishment of the
                        // link is no longer desired but the peer's metadata has not yet been received.

                        // If the socket is waiting for the peer's metadata and
                        // the LINKACK message does not contain the peer's metadata,
                        // then the socket remains in this state and waits for the 
                        // arrival of the LINK message.
                        if(isWaitingPeerMetadata(link) && !payload.hasInt("protocolId")) {
                            link.setPeerClockId(peerClockId);
                            link.setLinkAckMsgReceived(ackCode);
                        }
                        else{
                            // Else, the LINKACK message is the last message that the
                            // peer will send regarding the linking process. So, the
                            // socket just needs to send a fatal answer if the peer
                            // wants to establish the link, or close the link if
                            // the peer refused the link establishment.
                            if(isPositiveLinkingCode(ackCode)) {
                                byte[] respPayload =
                                        createLinkAckMsgWithoutMetadata(link.getClockId(), AC_CANCELED).serialize();
                                dispatch(peerId, MsgType.LINKACK, respPayload);
                            }
                            closeLink(link);
                        }
                    }
                }
            }
            // Else: if link does not exist, ignore the message.
        } finally {
            lock().unlock();
        }
    }

    // TODO - check where WAITING_TO_UNLINK needs to be reformulated to CANCELLING;
    //      + change "waitingLink" to "linkAckReceived" (search comments too)

    private void handleUnlinkMsg(SocketMsg msg) {
        SocketIdentifier peerId = msg.getSrcId();
        Integer peerClockId = parseUnlinkMsg(msg);
        if(peerClockId == null) return; // ignore if payload is invalid
        try {
            lock().lock();
            LinkNew link = links.get(peerId);
            // if link exists
            if(link != null){
                // To accept an unlink request, the peer's clock identifier
                // must match the registered peer clock identifier.
                if(peerClockId < link.getPeerClockId()
                        || peerClockId > link.getPeerProtocolId())
                    return;
                boolean sendUnlinkMsg = false;
                switch (link.state.get()){
                    // There is always a peer that reaches the ESTABLISHED state first.
                    // This means, that a socket may receive an UNLINK message,
                    // not only when in the ESTABLISHED state, but also while in a LINKING
                    // state waiting for the peer's answer, or in a CANCELLING state.
                    case LINKING, ESTABLISHED, CANCELLING -> {
                        // confirm that the peer's metadata has been
                        // received and an answer has been sent.
                        if(!isWaitingPeerMetadata(link))
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
            LinkNew link = links.get(peerId);
            if(link != null){
                // if link is established or attempting to link, there is nothing to do.
                // If link is in a state that unlinking will follow, then throw an exception
                // informing that the link cannot be established because it is already closing.
                LinkState state = link.state.get();
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
            LinkNew link = links.get(peerId);
            if(link != null){
                switch (link.state.get()){
                    case LINKING -> {
                        // try cancelling scheduled link request
                        SocketMsg scheduled = link.cancelScheduledMessage();
                        // if message was canceled, remove link
                        if(scheduled != null)
                            closeLink(link);
                        else{
                            // Else, switch to a "cancelling" state
                            link.state.set(LinkState.CANCELLING);
                        }
                    }
                    case ESTABLISHED -> {
                        // if link is established, changed it to UNLINKING
                        // and initiate the unlinking process by sending
                        // an unlink message.
                        link.state.set(LinkState.UNLINKING);
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
            LinkNew link = links.get(peerId);
            LinkState state = link != null ? link.state.get() : null;
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

    // ********** Flow Control Methods ********** //

    private void adjustOutgoingCredits(LinkNew link, int credits){
        link.outFCS.applyCreditVariation(credits);
        // Waiters can only be notified when there are available
        // credits. Therefore, if current amount of credits is
        // equal or superior to the amount of positive credits
        // received, wake up waiters up to the received amount of credits.
        // Else, wake up only the amount of waiters that the available
        // credits allow.
        int wakeUps = Math.min(link.outFCS.getCredits(), credits);
        if(wakeUps > 0)
            link.waitQ.fairWakeUp(0, wakeUps, 0, PollFlags.POLLOUT);
    }
}
