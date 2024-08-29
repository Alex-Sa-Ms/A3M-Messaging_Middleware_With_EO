package pt.uminho.di.a3m.core;

import com.google.protobuf.InvalidProtocolBufferException;
import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.core.flowcontrol.FlowCreditsPayload;
import pt.uminho.di.a3m.core.flowcontrol.InFlowControlState;
import pt.uminho.di.a3m.core.flowcontrol.OutFlowControlState;
import pt.uminho.di.a3m.core.linking.LinkClosedEvent;
import pt.uminho.di.a3m.core.linking.LinkEstablishedEvent;
import pt.uminho.di.a3m.core.messaging.*;
import pt.uminho.di.a3m.core.messaging.payloads.BytePayload;
import pt.uminho.di.a3m.core.messaging.payloads.CoreMessages.ErrorPayload;
import pt.uminho.di.a3m.core.messaging.payloads.SerializableMap;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.poller.PollTable;
import pt.uminho.di.a3m.poller.Pollable;
import pt.uminho.di.a3m.waitqueue.ParkState;
import pt.uminho.di.a3m.waitqueue.WaitQueue;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;
import pt.uminho.di.a3m.waitqueue.WaitQueueFunc;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class Link implements Pollable {
    private final LinkIdentifier id;
    private Integer peerProtocol = null;
    private final AtomicReference<LinkState> state = new AtomicReference<>(LinkState.UNINITIALIZED);
    private final Socket owner; // socket that owns the link
    private final InFlowControlState inFCS;
    private final OutFlowControlState outFCS = new OutFlowControlState(0);
    private final WaitQueue waitQ = new WaitQueue();
    private Queue<SocketMsg> inMsgQ = null;

    /**
     * Link constructor
     * @param id identifier of the link
     * @param initialCapacity initial amount of credits provided to the sender
     * @param socket socket that owns the link
     */
    public Link(LinkIdentifier id, int initialCapacity, Socket socket) {
        if(!LinkIdentifier.isValid(id))
            throw new IllegalArgumentException("Not a valid link identifier.");
        if(socket == null)
            throw new IllegalArgumentException("Socket is null.");
        this.id = id;
        this.inFCS = new InFlowControlState(initialCapacity);
        this.owner = socket;
    }

    public void unlink() {
        // TODO - unlink
    }

    /**
     * Record that combines a park state instance and events of interest
     * to be used by methods of the link instance that imply waiting
     * for I/O operations.
     * @param ps park state
     * @param events events of interest
     */
    private record ParkStateWithEvents(ParkState ps, int events){}

    /**
     * @return link identifier
     */
    public LinkIdentifier getId() {
        return id;
    }

    /**
     * Get link state atomically.
     * @return link current state
     */
    public LinkState getState() {
        return state.get();
    }

    /**
     * Sets the incoming message queue
     * if a queue has not been set already.
     * @param inMsgQ incoming message queue
     * @apiNote Thread must hold socket lock when the method is called. 
     */
    void setIncomingMessageQueue(Queue<SocketMsg> inMsgQ) {
        if(this.inMsgQ == null)
            this.inMsgQ = inMsgQ;
    }

    /**
     * Sets the peer's protocol if the
     * peer's protocol has not been set yet.
     * @param protocolId peer's protocol identifier
     * @apiNote Thread must hold socket lock when the method is called. 
     */
    public void setPeerProtocol(int protocolId) {
        if(this.peerProtocol == null)
            this.peerProtocol = protocolId;
    }

    /**
     * Queues incoming message. In the future, the return value should
     * return the reason behind the failure of the insertion. This version
     * assumes non-byzantine actors, however, if in the future byzantine
     * preventive measures are employed, one of them could be to check
     * if the sender is not obeying the flow control restrictions.
     * @param msg When the socket is in COOKED mode, the message to be
     *            queued is a data message. Else, if the socket is in RAW mode,
     *            then the queue can also have custom control messages queued.
     * @apiNote Thread must hold socket lock when the method is called.           
     */
    void queueIncomingMessage(SocketMsg msg){
        // do not add if closed
        if(state.get() == LinkState.CLOSED)
            throw new IllegalStateException("Link is closed.");
        // add new message to incoming messages queue
        inMsgQ.add(msg);
        // notify waiters
        waitQ.fairWakeUp(0,1,0,PollFlags.POLLIN);
    }

    /**
     * Polls incoming message, and if successful,
     * assumes message as delivered/handled. If
     * the delivery of the message completes a
     * credits batch, that batch is sent to the
     * sender.
     * @return socket message or "null" if there wasn't
     * an available message.
     * @apiNote Socket lock is assumed to be held by the caller.
     */
    private SocketMsg tryPollingMessage(){
        SocketMsg msg = inMsgQ.poll();
        if(SocketMsg.isType(msg, MsgType.DATA)) {
            // if a message was polled,
            // "mark" the message as delivered and
            // send a credits batch when required
            int batch = inFCS.deliver();
            sendCredits(batch);
        }
        return msg;
    }

    /**
     * Sends credits to the peer associated with this link.
     * @param credits credits to be sent. Can be negative
     *                if removing credits from the peer
     *                is desirable.
     * @apiNote Socket lock is assumed to be held by the caller and
     * the link state is assumed to not be closed.
     */
    private void sendCredits(int credits) {
        if(credits != 0)
            owner.dispatch(
                    new SocketMsg(id.srcId(), id.destId(), new FlowCreditsPayload(credits)));
    }

    /**
     * If the link is not closed, adjusts the peer's capacity
     * using provided credit variation and clears the current batch.
     * @param credits variation of credits to apply to the
     *                peer's current capacity.
     */
    public void adjustPeerCreditsCapacity(int credits) {
        try {
            owner.getLock().lock();
            if (state.get() != LinkState.CLOSED) {
                int batch = inFCS.adjustPeerCapacity(credits);
                sendCredits(batch);
            }
        }finally {
            owner.getLock().unlock();
        }
    }

    /**
     * If the link is not closed, sets peer capacity to the provided
     * value, sends current batch and clears the current batch.
     * @param newCapacity new peer capacity
     */
    public void setPeerCreditsCapacity(int newCapacity) {
        try {
            owner.getLock().lock();
            if(state.get() != LinkState.CLOSED) {
                int batch = inFCS.setPeerCapacity(newCapacity);
                sendCredits(batch);
            }
        } finally {
            owner.getLock().unlock();
        }
    }

    /**
     * Updates the batch size using the provided percentage.
     * @param newPercentage percentage of the peer's capacity that
     *                            should make the batch size. When the capacity
     *                            is positive, the minimum batch size is of 1 credit.
     * @throws IllegalArgumentException If the provided batch size percentage is not a
     * value between 0 (exclusive) and 1 (inclusive).
     */
    public void setCreditsBatchSizePercentage(float newPercentage) {
        try {
            owner.getLock().lock();
            if(state.get() != LinkState.CLOSED) {
                int batch = inFCS.setBatchSizePercentage(newPercentage);
                sendCredits(batch);
            }
        } finally {
            owner.getLock().unlock();
        }
    }

    private boolean hasFlowControlPermission(SocketMsg msg){
        return !SocketMsg.isType(msg, MsgType.DATA) || outFCS.hasCredits();
    }

    /**
     * Tries to dispatch message using the provided payload. If the payload is
     * data payload (analogous to data msg), then in addition to requiring the
     * validation of the socket to dispatch the message, it also requires a credit.
     * @param msg message to be dispatched
     * @param skipCustomVerification if the verification under the socket's custom
     *                               semantics can be skipped.
     * @return "true" if the message was dispatched. "false" if the message could
     * not be sent immediately.
     * @throws IllegalArgumentException Can throw illegal argument exception if the
     * payload is not considered valid under the socket semantics. This differs from
     * receiving "false" as the message may not be accepted under the current state but
     * may be in the future.
     * @implNote Socket lock is assumed to be held by the caller.
     */
    private boolean tryDispatchingMessage(SocketMsg msg, boolean skipCustomVerification){
        boolean ret = false;
        if(owner.isOutgoingMessageValid(msg, skipCustomVerification)
                && (!SocketMsg.isType(msg, MsgType.DATA) || outFCS.trySend())) {
            owner.dispatch(msg);
            ret = true;
        }
        return ret;
    }

    /**
     * 
     * @param msg
     * @apiNote Thread must hold socket lock when the method is called.
     */
    void handleLinkMessage(SocketMsg msg){
        assert msg != null;
        switch (msg.getType()){
            case MsgType.LINK:
                handleLinkRequest(msg);
                break;
            case MsgType.UNLINK:
                close(false);
                break;
            default:
                break;
        }
    }

    private void handleLinkRequest(SocketMsg msg) {
        if(state.get() == LinkState.LINKING) {
            // deserializes the payload into a map
            SerializableMap payload = null;
            try {
                payload = SerializableMap.deserialize(msg.getPayload());

                // checks protocol compatibility
                boolean isCompatible = false;
                if (payload.hasInt("protocolId")) {
                    peerProtocol = payload.getInt("protocolId");
                    if (owner.getCompatibleProtocols().stream().anyMatch(p -> p.id() == peerProtocol))
                        isCompatible = true;
                }

                // If not compatible, close link. Since a link is
                // already created, this means a link request as been
                // sent. This fact, in conjunction, to the linking protocol
                // symmetry, means the incompatibility can be perceived by
                // the remote peer through the link request that has been sent.
                if (!isCompatible) close();

                // set state to "established"
                state.set(LinkState.ESTABLISHED);

                // adjust outgoing capacity using the received credits,
                // and wake up waiters on writing event.
                if (payload.hasInt("credits")) {
                    int outCapacity = payload.getInt("credits");
                    adjustOutgoingCredits(outCapacity);
                }

                // emit a link establishment event to let any required custom
                // logic be performed
                owner.customHandleEvent(new LinkEstablishedEvent(this, payload));
            } catch (Exception ignored) {
            }
        }
    }

    void handleErrorMessage(SocketMsg msg){
        assert msg != null && msg.getType() == MsgType.ERROR;
        // convert payload
        ErrorPayload payload = null;
        try {
            payload = ErrorPayload.parseFrom(msg.getPayload());
        } catch (InvalidProtocolBufferException e) {
            // Ignore faulty error message for now.
            Logger.getLogger(id.toString()).warning("Received faulty error msg payload: " +
                    "{\n\tBytes: " + Arrays.toString(msg.getPayload()) +
                    ",\n\tUTF8: " + StandardCharsets.UTF_8.decode(ByteBuffer.wrap(msg.getPayload())) + "}");
            return;
        }

        if(payload.getCode().size() == 1){
            byte code = payload.getCode().byteAt(0);
            switch (code){
                case ErrorType.SOCK_NFOUND, ErrorType.SOCK_NAVAIL ->{
                    if(state.get() == LinkState.LINKING) {
                        int peerCapacity =
                                owner.getOption("peerCapacity", Integer.class);
                        long dispatchTime =
                                owner.getOption("retryInterval", Long.class)
                                + System.currentTimeMillis();
                        SocketMsg linkRequest =
                                createLinkRequest(id.srcId(), id.destId(),
                                        owner.getProtocol().id(), peerCapacity);
                        owner.scheduleDispatch(linkRequest, dispatchTime);
                    } // else, ignore because might be a delayed message
                }
                default -> {} // do nothing
            }
        }

        // Ignore faulty error message for now.
        Logger.getLogger(id.toString()).warning("Received faulty error msg payload: " +
                "{\n\tCode: " + Arrays.toString(payload.getCode().toByteArray()) +
                ",\n\tText: \"" + payload.getText() + "\"}");
    }

    static SocketMsg createLinkRequest(SocketIdentifier src, SocketIdentifier dest, int protocolId, int credits){
        SerializableMap map = new SerializableMap();
        map.putInt("protocolId", protocolId);
        map.putInt("credits", credits);
        return new SocketMsg(src, dest, MsgType.LINK, map.serialize());
    }

    /**
     * Handles a credits flow control message.
     * @param msg not null flow control message
     * @apiNote Thread must hold socket lock when the method is called.
     */
    void handleFlowControlMessage(SocketMsg msg){
        assert msg != null;
        FlowCreditsPayload fcp = FlowCreditsPayload.convertFrom(msg.getType(), msg.getPayload());
        assert fcp != null;
        adjustOutgoingCredits(fcp.getCredits());
    }

    private void adjustOutgoingCredits(int credits){
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

    /**
     * Closes link.
     * @param local Indicates if the call was invoked locally, or a consequence
     *              of an UNLINK message from the peer.
     * @apiNote Thread must hold socket lock when the method is called.
     */
    private void close(boolean local){
        if(state.getAndSet(LinkState.CLOSED) != LinkState.CLOSED) {
            // notify with POLLHUP to inform the closure,
            // and POLLFREE to make the wake-up callbacks
            // remove the entry regardless of whether the waiter
            // is waiting or not.
            waitQ.wakeUp(0,0,0,PollFlags.POLLHUP | PollFlags.POLLFREE);
            // if call was local, send an UNLINK message to the peer.
            if(local)
                owner.dispatch(
                        new SocketMsg(id.srcId(), id.destId(),
                                new BytePayload(MsgType.UNLINK, null)));
            // Inform link closure to the custom socket logic
            owner.customHandleEvent(new LinkClosedEvent(this));
        }
    }

    /**
     * Closes link. To be invoked by the socket, when closing the link
     * is a local initiative.
     * @apiNote Thread must hold socket lock when the method is called. 
     */
    void close(){
        close(true);
    }
    
    /**
     * Wait queue function for callers that
     * use the link directly and are interested
     * in I/O events. This wake-up function wakes
     * up a thread when there is a match between the key
     * and the events of interest. The events of interest are
     * assumed to include POLLERR and POLLHUP, in order for threads
     * to wake-up, when the link is closed or an error occurs.
     */
    private static final WaitQueueFunc directEventWakeFunc = (entry, mode, flags, key) -> {
        ParkStateWithEvents pswe = (ParkStateWithEvents) entry.getPriv();
        int ret = 0;
        if((pswe.events & (int) key) != 0)
            ret = entry.parkStateWakeUp(pswe.ps);
        return ret;
    };

    /**
     * <p>Adds a direct caller of the link object to the wait queue as an exclusive waiter.
     * <p>Subscribes to hang-up and error events in addition to the mentioned events.
     * <p>The wake-up function used is directExclusiveCallerWakeFunc.</p>
     * @param events events of interest
     * @param initialParkState initial park state
     * @return queued wait queue entry
     * @implSpec Wait queue has own lock, so the queuing action does not 
     * require any other locking mechanism to be secured when the method is
     * called. However, the link is assumed to not be closed, since queuing
     * a waiter when the link is closed is pointless as no more notifications
     * will be sent.
     */
    private WaitQueueEntry queueDirectEventWaiter(int events, boolean initialParkState){
        // subscribes hang-up and error events
        events |= PollFlags.POLLHUP | PollFlags.POLLERR;
        // Creates private object to match the wake-up function.
        // Contains the park state required to wake up the thread and
        // the mask with events of interest.
        // The park state is initialized with "true" to ensure a possible
        // notification is not missed.
        ParkStateWithEvents pswe = new ParkStateWithEvents(new ParkState(initialParkState), events);
        WaitQueueEntry wait = waitQ.initEntry();
        wait.addExclusive(directEventWakeFunc, pswe);
        return wait;
    }

    /**
     * Waits until an incoming message, from the peer associated 
     * with this link, is available or the deadline is reached or
     * the thread is interrupted.
     * If the socket is in COOKED mode, the returned messages are
     * data messages. If in RAW mode, the returned messages may also
     * be custom control messages.
     * @param deadline waiting limit to poll an incoming message
     * @return available incoming message or "null" if the operation timed out
     * without a message becoming available.
     * @apiNote Caller must not hold socket lock as it will result in a deadlock
     * when a blocking operation with a non-expired deadline is requested. 
     */
    SocketMsg receive(Long deadline) throws InterruptedException {
        SocketMsg msg;
        WaitQueueEntry wait;
        ParkState ps;
        boolean timedOut = Timeout.hasTimedOut(deadline);

        try {
            owner.getLock().lock();

            // if link is closed and queue is empty, messages cannot be received.
            if (state.get() == LinkState.CLOSED && inMsgQ.isEmpty())
                throw new LinkClosedException();

            // try retrieving a message immediately
            msg = tryPollingMessage();
            if (msg != null) return msg;

            // return if timed out
            if (timedOut) return null;

            // If a message could not be retrieved, make the caller a waiter
            wait = queueDirectEventWaiter(PollFlags.POLLIN, true);
            ps = ((ParkStateWithEvents) wait.getPriv()).ps();
        } finally {
            owner.getLock().unlock();
        }

        while (true) {
            if (timedOut || state.get() == LinkState.CLOSED)
                break;
            if (Thread.currentThread().isInterrupted()) {
                wait.delete();
                throw new InterruptedException();
            }
            // Wait until woken up
            WaitQueueEntry.defaultWaitFunction(wait, ps, deadline, true);
            try {
                owner.getLock().lock();

                // attempt to poll a message
                msg = tryPollingMessage();
                if (msg != null) break;

                // if a message was not available and the operation has not
                // timed out, sets parked state to true preemptively,
                // so that a notification is not missed
                timedOut = Timeout.hasTimedOut(deadline);
                if (!timedOut) ps.parked.set(true);
            } finally {
                owner.getLock().unlock();
            }
        }
        // delete entry since a hanging wait queue entry is not desired.
        wait.delete();
        return msg;
    }

    /**
     * Sends message to the peer associated with this link.
     * @param payload message content
     * @param deadline timestamp at which the method must return
     *                 indicating a timeout if sending was not possible
     *                 due to the lack of permission (i.e. credits).
     * @return "true" if message was sent. "false" if permission
     *          was not acquired during the specified timeout.
     * @throws IllegalArgumentException If the payload is not valid under the
     * socket's semantics and current state.
     * @apiNote Caller must not hold socket lock as it will result in a deadlock
     * when a blocking operation with a non-expired deadline is requested. 
     */
    public boolean send(Payload payload, Long deadline) throws InterruptedException {
        boolean ret = false;
        WaitQueueEntry wait;
        ParkState ps;
        boolean timedOut = Timeout.hasTimedOut(deadline);
        SocketMsg msg = new SocketMsg(id.srcId(), id.destId(), payload);

        try {
            owner.getLock().lock();

            // if link is closed, messages cannot be sent.
            if (state.get() == LinkState.CLOSED)
                throw new LinkClosedException();

            // if message is not a data message, or if it is a data message and there
            // are available credits, then attempt to send the message. Without
            // bypassing the verifications under the custom semantics
            if(hasFlowControlPermission(msg)) {
                ret = tryDispatchingMessage(msg, false);
                // if sending failed, then the message is invalid,
                // meaning another waiter should get the opportunity of sending.
                if(!ret) waitQ.fairWakeUp(0,1,0,PollFlags.POLLOUT);
            }

            // return if timed out
            if (timedOut)
                return false;

            // If the message could not be sent, make the caller a waiter
            wait = queueDirectEventWaiter(PollFlags.POLLOUT, true);
            ps = ((ParkStateWithEvents) wait.getPriv()).ps();
        } finally {
            owner.getLock().unlock();
        }

        while (true) {
            if (timedOut || state.get() == LinkState.CLOSED)
                break;
            if (Thread.currentThread().isInterrupted()) {
                wait.delete();
                throw new InterruptedException();
            }
            // Wait until woken up
            WaitQueueEntry.defaultWaitFunction(wait, ps, deadline, true);
            try {
                owner.getLock().lock();

                // attempt to send the message again
                if(hasFlowControlPermission(msg)) {
                    ret = tryDispatchingMessage(msg, false);
                    if(!ret) waitQ.fairWakeUp(0,1,0,PollFlags.POLLOUT);
                    break;
                }

                // if permission to send the message was not granted and
                // the operation has not timed out,sets parked state to
                // true preemptively, so that a notification is not missed
                timedOut = Timeout.hasTimedOut(deadline);
                if (!timedOut) ps.parked.set(true);
            } finally {
                owner.getLock().unlock();
            }
        }
        // delete entry since a hanging wait queue entry is not desired.
        wait.delete();
        return ret;
    }
    
    /**
     * @return mask of available events
     * @apiNote Thread must hold socket lock when the method is called. 
     */
    private int getAvailableEventsMask(){
        int events = 0;
        if(outFCS.hasCredits())
            events |= PollFlags.POLLOUT;
        if(!inMsgQ.isEmpty())
            events |= PollFlags.POLLIN;
        if(state.get() == LinkState.CLOSED)
            events |= PollFlags.POLLHUP;
        return events;
    }

    @Override
    public int poll(PollTable pt) {
        try{
            owner.getLock().lock();
            if(!PollTable.pollDoesNotWait(pt)){
                WaitQueueEntry wait =
                        state.get() != LinkState.CLOSED ? waitQ.initEntry() : null;
                pt.pollWait(this, wait);
            }
            return getAvailableEventsMask();
        }finally {
            owner.getLock().unlock();
        }
    }
}