package pt.uminho.di.a3m.core;

import com.google.protobuf.InvalidProtocolBufferException;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.SerializableMap;
import pt.uminho.di.a3m.core.messaging.SocketMsg;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.logging.Logger;

import pt.uminho.di.a3m.core.LinkNew.LinkState;
import pt.uminho.di.a3m.poller.PollFlags;

public class LinkManager {
    private final Socket socket;
    private final Map<SocketIdentifier, LinkNew> links = new HashMap<>();
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
     * Dispatch message to the peer
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

/*
When invoking link():
        1. If link exists:
            1-LINKING/ESTABLISHED:
                1. If link state is LINKING or ESTABLISHED, do nothing and return.
            1-UNLINKING/WAITING-TO-UNLINK:
                1. Throw exception, saying, link is closing (IllegalStateException)
        2. Create link with peer's identifier as -1.
        3. Set link to LINKING state.
        4. Send a LINK message to the peer.
        5. Wait for a message from the peer (rejects any message with an identifier smaller than the currently saved peer's identifier)
            5-LINK:
                1. If either a link or a positive link acknowledgement message is received, set link state
                    to "established", update the peer's identifier using the received value and return.
            5-REFUSAL:
                1. If a negative link acknowledgment message is received,
                   determine if the reason is fatal or non-fatal.
                    5-REFUSAL-FATAL:
                        1. If fatal, close and delete link. The closure must wake up
                           all waiters with a POLLHUP and POLLFREE notification.
                    5-REFUSAL-NON-FATAL:
                        1. If not-fatal, schedule a retry.
                            NOTE: Since there is a possibility of the scheduled retry undoing an unlink from the peer,
                                  when the peer sends a link and unlink messages after sending the non-fatal refusal,
                                  the scheduled dispatches should return an atomic reference that enables cancelling
                                  the dispatch. To cancel the dispatch, one should set the value to "null". The messaging
                                  system, will also set the value to "null" using getAndSet(null) enabling to verify if
                                  the message was dispatched.
            5-UNLINK:
                1. If an unlink message is received, send an UNLINK message and close the link.
 */

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

    private boolean isFatalRefusalCode(int rfcode){
        return rfcode < 0;
    }

    private boolean isNonFatalRefusalCode(int rfcode){
        return rfcode > 0;
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
        int credits = socket.getOption("peerCapacity", Integer.class);
        map.putInt("protocolId", protocolId);
        map.putInt("credits", credits);
        map.putInt("clockId", clockId);
        return map;
    }

    /**
     * Converts message's payload (byte array) to serializable map if possible.
     * Then, checks if the mandatory fields of a link request / link acknowledgement
     * message are present.
     * @param msg message to have its payload converted to serializable map
     * @return serializable map or null if payload is invalid
     */
    private SerializableMap parseLinkReqOrAckMsg(SocketMsg msg){
        try {
            SerializableMap map = SerializableMap.deserialize(msg.getPayload());
            if(!map.hasInt("protocolId") || !map.hasInt("clockId") || !map.hasInt("credits"));
            else return map;
        } catch (InvalidProtocolBufferException ignored) {}
        // if invalid payload, log it, and return null
        Logger.getLogger(socket.getId().toString())
                .warning("Invalid " + (msg.getType() == MsgType.LINK ? "link request" : "link acknowledgement")
                        + "from " + msg.getSrcId() + " : " + Arrays.toString(msg.getPayload()));
        return null;
    }

    private SerializableMap parseLinkRequestMsg(SocketMsg msg){
        return parseLinkReqOrAckMsg(msg);
    }

    /**
     * Creates link acknowledgement payload. Identical to the link request
     * payload but enables an acknowledgement code to be provided.
     * @param clockId link's clock identifier
     * @param ackCode acknowledgement code
     * @return serializable map which corresponds to the link request payload.
     */
    private SerializableMap createLinkAckMsg(int clockId, int ackCode) {
        SerializableMap map = createLinkRequestMsg(clockId);
        map.putInt("ackCode", ackCode);
        return map;
    }

    private SerializableMap parseLinkAckMsg(SocketMsg msg) {
        return parseLinkReqOrAckMsg(msg);
    }

    private byte[] createUnlinkMsg(int clockId) {
        return ByteBuffer.allocate(4).putInt(clockId).array();
    }

    /**
     * Extracts the clock identifier of the peer from the unlink message.
     * @param msg unlink message
     * @return the clock identifier if the message's payload is valid. Otherwise, returns null.
     */
    private Integer parseUnlinkMsg(SocketMsg msg){
        if(msg.getPayload() == null || msg.getPayload().length != 4)
            return null;
        else
            return ByteBuffer.wrap(msg.getPayload()).getInt();
    }

    // ********* Linking/Unlinking logic ********* //

    // TODO - see where notifying waiters and creating socket events is required

    /**
     * Determines if a peer is compatible
     * @param peerProtocolId peer's protocol identifier
     * @return true if peer is compatible. Otherwise, returns false.
     */
    private boolean isPeerCompatible(int peerProtocolId){
        return socket.isCompatibleProtocol(peerProtocolId);
    }

    /**
     * Confirms peer compatibility. If the peer is not compatible,
     * sets in motion an unlinking process.
     * @return true if peer is compatible. false, otherwise.
     */
    private boolean confirmPeerCompatibility(LinkNew link, int peerProtocolId){
        if(!isPeerCompatible(peerProtocolId)){
            // in the absence of malicious nodes, this does not happen.
            link.state.set(LinkState.UNLINKING);
            dispatch(link.id.destId(), MsgType.UNLINK, createUnlinkMsg(link.getClockId()));
            return false;
        }else
            return true;
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
    private void removeLink(LinkNew link){
        link.close();
        links.remove(link.id.destId());
    }
    
    /**
     * Creates link with default incoming capacity.
     * @param sid peer's socket identifier
     * @return link with 'null' state
     */
    private LinkNew createLink(SocketIdentifier sid){
        int peerCapacity = socket.getOption("peerCapacity", Integer.class);
        LinkIdentifier linkId = new LinkIdentifier(socket.getId(), sid);
        LinkNew link = new LinkNew(linkId, clock, peerCapacity);
        // The clock identifiers are provided in an increasing order,
        // as a form of causal consistency.
        clock++;
        return link;
    }

    /**
     * Create link and set it to LINKING state.
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
     * Create link, set provided attributes and set state to ESTABLISHED.
     * This method limits itself to setting the attributes. It does
     * not do any special procedures such as waking up waiters.
     * @param sid peer's socket identifier
     * @return link in LINKING state
     */
    private LinkNew createEstablishedLink(SocketIdentifier sid, int peerProtocolId, int peerClockId, int outCredits){
        LinkNew link = createLink(sid);
        link.state.set(LinkState.ESTABLISHED);
        setPeerInformation(link, peerProtocolId, peerClockId, outCredits);
        return link;
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
            case MsgType.LINK -> handleLinkMsg(msg);
            case MsgType.LINKACK -> handleLinkAckMsg(msg);
            case MsgType.UNLINK -> handleUnlinkMsg(msg);
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
                        // TODO - where is the verification of compatibility?
                        link.state.set(LinkState.ESTABLISHED);
                        // update peer's information
                        setPeerInformation(link, peerProtocolId, peerClockId, outCredits);
                        // if a link message is scheduled, cancel it,
                        // and send it immediatelly
                        SocketMsg scheduled = link.cancelScheduledMessage();
                        if(scheduled != null)
                            dispatch(scheduled);
                    }
                    // Ignore message when ESTABLISHED. This cannot happen, as
                    // for a LINK/LINKACK message to be sent, the sender must not
                    // have the link, and the state of this link is that both peers
                    // agreed on having the link established.
                    // case ESTABLISHED -> {}
                    case UNLINKING -> {
                        // The received message must have a higher clock id
                        // then the peer's clock id associated with the link.
                        // The peer has already closed the link and started
                        // a new linking process.
                        if(peerClockId > link.getPeerClockId()) {
                            // Send LINKACK with non-fatal "ALREADY_LINKED" negative response
                            // to reschedule the linking process, giving time to finish the current
                            // unlinking process.
                            byte[] ackPayload = createLinkAckMsg(link.getClockId(), AC_ALREADY_LINKED).serialize();
                            dispatch(peerId, MsgType.LINKACK, ackPayload);
                        }
                    }
                    case WAITING_TO_UNLINK -> {
                        // Update peer's identifier as it is the first time receiving
                        // a message from the peer since the creation of the link.
                        link.setPeerClockId(peerClockId);
                        // Change to UNLINKING as the required to establish the link
                        // was received
                        link.state.set(LinkState.UNLINKING);
                        // Send an UNLINK message to initiate the unlinking process
                        dispatch(peerId, MsgType.UNLINK, createUnlinkMsg(link.getClockId()));
                    }
                }
            }
            // If link does not exist
            else{
                byte[] ackPayload;

                // if incoming link requests are not allowed, send fatal refusal
                boolean allowIncomingLinkRequests = socket.getOption("allowIncomingLinkRequests", Boolean.class);
                if(!allowIncomingLinkRequests) {
                    ackPayload = createLinkAckMsg(0, AC_INCOMING_NOT_ALLOWED).serialize();
                    dispatch(peerId, MsgType.LINKACK, ackPayload);
                    return;
                }

                // If max links limit has been reached. The link cannot be established
                // at the moment, but may be established in the future.
                int maxLinks = socket.getOption("maxLinks", Integer.class);
                if(links.size() >= maxLinks){
                    ackPayload = createLinkAckMsg(0, AC_TMP_NAVAIL).serialize();
                    dispatch(peerId, MsgType.LINKACK, ackPayload);
                    return;
                }

                // If the peer's protocol is not compatible with the socket's protocol,
                // send a fatal refusal reason informing the incompatibility
                if(!isPeerCompatible(peerProtocolId)){
                    ackPayload = createLinkAckMsg(0, AC_INCOMPATIBLE).serialize();
                    dispatch(peerId, MsgType.LINKACK, ackPayload);
                    return;
                }

                // All requirements have been passed, so the link can be established.
                // A LINKACK message is sent to confirm the establishment
                LinkNew newLink = createEstablishedLink(peerId, peerProtocolId, peerClockId, outCredits);
                ackPayload = createLinkAckMsg(newLink.getClockId(), AC_SUCCESS).serialize();
                dispatch(peerId, MsgType.LINKACK, ackPayload);
                // wakes waiters if outgoing credits are positive
                if(outCredits > 0)
                    newLink.waitQ.fairWakeUp(0,outCredits,0,PollFlags.POLLOUT);
            }
        } finally {
            lock().unlock();
        }
    }

    private void handleLinkAckMsg(SocketMsg msg) {
        SocketIdentifier peerId = msg.getSrcId();
        SerializableMap payload = parseLinkAckMsg(msg);
        if(payload == null) return; // if payload is invalid
        int ackCode = payload.getInt("ackCode");
        int peerProtocolId = payload.getInt("protocolId");
        int outCredits = payload.getInt("credits");
        int peerClockId = payload.getInt("clockId");
        try {
            lock().lock();
            LinkNew link = links.get(peerId);
            // If link exists
            if(link != null) {
                // discard messages with old clock identifiers
                if(peerClockId < link.getPeerClockId()) return;
                switch (link.state.get()){
                    case LINKING -> {
                        // if the link was accepted by the peer
                        if(ackCode == 0){
                            // Confirm compatibility. Initiates 
                            // unlinking process if not compatible. 
                            if(confirmPeerCompatibility(link, peerProtocolId)){
                                link.state.set(LinkState.ESTABLISHED);
                                setPeerInformation(link, peerProtocolId, peerClockId, outCredits);
                            }
                        }
                        // if the acknowledgement message contains a fatal
                        // refusal code, then remove the link
                        else if(isFatalRefusalCode(ackCode))
                            removeLink(link);
                        else { 
                            // if not fatal refusal code, then 
                            // schedule a linking process retry
                            byte[] linkReqPayload =
                                    createLinkRequestMsg(link.getClockId()).serialize();
                            long dispatchTime =
                                    System.currentTimeMillis()
                                    + socket.getOption("retryInterval", Long.class);
                            AtomicReference<SocketMsg> scheduled =
                                    scheduleDispatch(peerId, MsgType.LINK, linkReqPayload, dispatchTime);
                            link.setScheduled(scheduled);
                        }
                    }
                    // Ignore message when ESTABLISHED. This cannot happen, as
                    // for a LINK/LINKACK message to be sent, the sender must not
                    // have the link, and the state of this link is that both peers
                    // agreed on having the link established.
                    // case ESTABLISHED -> {}

                    // Receiving a LINKACK message when in UNLINKING state is not possible.
                    // To pass to the UNLINKING state, one must have already received a LINK/LINKACK
                    // message.
                    // case UNLINKING -> {}
                    case WAITING_TO_UNLINK -> {
                        // A link is set to the WAITING_TO_UNLINK state when a LINK/LINKACK
                        // message has not yet been received to determine if the link should be
                        // established or not.

                        // Update peer's identifier as it is the first time receiving
                        // a message from the peer since the creation of the link.
                        link.setPeerClockId(peerClockId);
                        
                        // if the ack code is not successful, closing the link.
                        // can be done has the link establishment was rejected.
                        if(ackCode != 0) {
                            removeLink(link);
                            // A closing event does not need to be emitted as 
                            // a link established event was not emitted.
                            return;
                        }
                        
                        // Confirm compatibility. Initiates unlinking process
                        // if not compatible. 
                        if(!confirmPeerCompatibility(link, peerProtocolId))
                            return;
                        
                        // Send an UNLINK message
                        dispatch(peerId, MsgType.UNLINK, createUnlinkMsg(link.getClockId()));

                        // If an UNLINK msg as already been received while
                        // in the WAITING_TO_UNLINK state, then the link
                        // can be closed after sending the UNLINK message.
                        if(link.isUnlinkReceived()) removeLink(link);
                        // Else, the link can progress to the UNLINKING state
                        else link.state.set(LinkState.UNLINKING);
                    }
                }
            }
            // Else: if link does not exist. 
            // Ignore message, although a LINKACK message is not
            // supposed to be received when the link does not exist.
        } finally {
            lock().unlock();
        }

    }

    private void handleUnlinkMsg(SocketMsg msg) {
        try {
            lock().lock();
            SocketIdentifier peerId = msg.getSrcId();
            // TODO - handle unlink msg
        } finally {
            lock().unlock();
        }

    }

    /**
     * Initiate a linking process for the provided socket identifier.
     * @param sid socket identifier of the peer with which a link should be established
     * @throws IllegalArgumentException If socket identifier is null.
     * @throws IllegalStateException If the link is being closed or if the limit of links has been reached.
     */
    public void link(SocketIdentifier sid){
        if(sid == null)
            throw new IllegalArgumentException("Socket identifier cannot be null.");
        try{
            lock().lock();
            LinkNew link = links.get(sid);
            if(link != null){
                // if link is established or attempting to link, there is nothing to do.
                // If link is in a state that unlinking will follow, then throw an exception
                // informing that the link cannot be established because it is already closing.
                LinkState state = link.state.get();
                if(state == LinkState.UNLINKING || state == LinkState.WAITING_TO_UNLINK)
                    throw new IllegalStateException("Link is currently being closed. Try again later.");
            }else{
                // Link does not exist, so create and register it if
                // the maximum amount of links has not been reached.
                int maxLinks = socket.getOption("maxLinks", Integer.class);
                if(links.size() < maxLinks) {
                    // Each link created is given a different clock identifier.
                    link = createLinkingLink(sid);
                    links.put(sid, link);
                    // send a LINK message to the peer
                    SerializableMap map = createLinkRequestMsg(clock);
                    dispatch(sid, MsgType.LINK, map.serialize());
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
     * @param sid socket identifier of the peer with which the link should be closed.
     */
    public void unlink(SocketIdentifier sid){
        if(sid == null)
            throw new IllegalArgumentException("Socket identifier cannot be null.");
        try {
            lock().lock();
            // TODO - unlink()
        } finally {
            lock().unlock();
        }
    }

    /*
    TODO - Linking/Unlink algorithm using link identifier:

        *** Linking algorithm ***

        - A clock is required to determine the identifier of the link.
        - Each link is a combination of a clock identifier from each peer.
        - Every link-related message carries the integer identifier of the link (the identifier generated on its own side).

        Link states:
        LINKING
        UNLINKING
        WAITING-TO-UNLINK
        ESTABLISHED
        UNLINKED

        When invoking link():
        1. If link exists:
            1-LINKING/ESTABLISHED:
                1. If link state is LINKING or ESTABLISHED, do nothing and return.
            1-UNLINKING/WAITING-TO-UNLINK:
                1. Throw exception, saying, link is closing (IllegalStateException)
        2. Create link with peer's identifier as -1.
        3. Set link to LINKING state.
        4. Send a LINK message to the peer.
        5. Wait for a message from the peer (rejects any message with an identifier smaller than the currently saved peer's identifier)
            5-LINK:
                1. If either a link or a positive link acknowledgement message is received, set link state
                    to "established", update the peer's identifier using the received value and return.
            5-REFUSAL:
                1. If a negative link acknowledgment message is received,
                   determine if the reason is fatal or non-fatal.
                    5-REFUSAL-FATAL:
                        1. If fatal, close and delete link. The closure must wake up
                           all waiters with a POLLHUP and POLLFREE notification.
                    5-REFUSAL-NON-FATAL:
                        1. If not-fatal, schedule a retry.
                            NOTE: Since there is a possibility of the scheduled retry undoing an unlink from the peer,
                                  when the peer sends a link and unlink messages after sending the non-fatal refusal,
                                  the scheduled dispatches should return an atomic reference that enables cancelling
                                  the dispatch. To cancel the dispatch, one should set the value to "null". The messaging
                                  system, will also set the value to "null" using getAndSet(null) enabling to verify if
                                  the message was dispatched.
            5-UNLINK:
                1. If an unlink message is received, send an UNLINK message and close the link.


        When receiving a LINK message:
        1. If link exists (discard if not a newer clock identifier):
            1-LINKING: Link is in a "LINKING" state
                1. Set link state to ESTABLISHED and save peer's identifier.
                2. If there is a scheduled retry, cancel it, and dispatch it immediatelly.
                3. If there isn't a scheduled retry, then, nothing more is required.
                NOTE: If the link state is LINKING, then a link message has already been sent and
                due to the symmetry of the linking procedure, the peer should also establish the LINK on his side.
            1-ESTABLISHED:
                1. Can't happen because a peer cannot send a LINK message unless it does not have a link.
                If the link was established and closed recently, then both peers confirmed the unlink operation,
                meaning, neither peer can be in an ESTABLISHED state.
                <discarded>. However, if it could happen:
                    Keep ESTABLISHED, DO NOT SAVE peer's clock, and send a LINKACK with a non-fatal negative response
                    "ALREADY_LINKED". This makes sure a link retry can happen in the future, potentially, when the
                    link has already received the
                    NOTE: Linking symmetry assumes it will always lead to the same result,
                    so there is no need to check the message.
            1-UNLINKING:
                1. Newer link message is being received:
                    Keep the same state, send a LINKACK with a non-fatal negative response
                    "ALREADY_LINKED". This makes sure the link establishment request can be retried until
                     the peer receives the unlink message that closes the link, and enables the link
                     to be established again.
            1-WAITING-TO-UNLINK:
                1. Update peer's identifier, change to UNLINKING and send an UNLINK msg.
        2. If link does not exist, analyze LINK message and link restrictions (maxLinks).
            2-COMPATIBLE:
                1. Create link with peer's clock identifier.
                2. Set link state to "ESTABLISHED".
                3. Send a positive LINKACK message.
            2-NOT_COMPAT:
                1. Send a negative LINKACK informing which reason is behind the refusal.

        When receiving a LINKACK message :
        1. If link exists (discard if not a newer clock identifier):
            1-LINKING: Link state is LINKING
                1. Set link state to "established", update the peer's identifier using
                 the received value and return.
            1-ESTABLISHED: Link state is ESTABLISHED
                1. Ignore message. Peer clock identifier must match.
                NOTE: A no-match is not possible, because for a newer clock identifier to
                be present in the message, an unlink procedure had to be performed which
                involves both peers agreeing on the unlink.
            1-UNLINKING: Link state is UNLINKING
                1. Ignore message.
            1-WAITING-TO-UNLINK:
                1. If a close flag is set, meaning an UNLINK message has been received before the LINK/LINKACK message,
                then send an UNLINK msg and close the link.
                2. If the close flag is not set: Update peer's identifier, change to UNLINKING and send an UNLINK msg.

        2. If link does not exist:
            - Ignore message.

        ** Unlinking **

        When invoking unlink():
        1. If does not exist, do nothing and return.
        2. If the link exists:
            2-LINKING:
                1. If has scheduled retry, cancel it and close link.
                2. If there isn't a scheduled retry, then a link message has been sent.
                   Set state to WAITING-TO-UNLINK.
            2-ESTABLISHED:
                1. Set state to UNLINKING and send unlink message.
            2-UNLINKING:
                1. Return.
            2-WAIITING-TO-UNLINK:
                1. Return.

        When receiving UNLINK:
        1. If link does not exists, do nothing and return. This scenarion shouldn't be possible.
        2. If link exists:
            2-LINKING:
                1. Change to state WAITING-TO-UNLINK, update peer's identifier and set close flag to true.
            2-ESTABLISHED:
                1. Send unlink message and close link.
            2-UNLINKING:
                1. Close link.
            2-WAIITING-TO-UNLINK:
                1. Ignore this UNLINK msg. The other peer is in an UNLINKING state because it sent this
                UNLINK message. And since the WAITING-TO-UNLINK state, means waiting for the message that
                would establish the link before sending an UNLINK message, the peer will receive the required
                UNLINK message to close the link when the LINK/LINKACK message is received by this socket.
                However, this socket does need to have a flag indicating that it can close immediatelly,
                instead of passing to the UNLINKING state.
     */

    private void adjustOutgoingCredits(LinkNew link, int credits){
        outFCS.applyCreditVariation(credits);
        // Waiters can only be notified when there are available
        // credits. Therefore, if current amount of credits is
        // equal or superior to the amount of positive credits
        // received, wake up waiters up to the received amount of credits.
        // Else, wake up only the amount of waiters that the available
        // credits allow.
        int wakeUps = Math.min(outFCS.getCredits(), credits);
        if(wakeUps > 0)
            waitQ.fairWakeUp(0, wakeUps, 0, PollFlags.POLLOUT);
    }
}
