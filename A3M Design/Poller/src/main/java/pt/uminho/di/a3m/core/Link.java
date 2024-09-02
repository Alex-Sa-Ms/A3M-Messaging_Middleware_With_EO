package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.core.flowcontrol.InFlowControlState;
import pt.uminho.di.a3m.core.flowcontrol.OutFlowControlState;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.poller.PollTable;
import pt.uminho.di.a3m.poller.Pollable;
import pt.uminho.di.a3m.waitqueue.ParkState;
import pt.uminho.di.a3m.waitqueue.WaitQueue;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;
import pt.uminho.di.a3m.waitqueue.WaitQueueFunc;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <b>Locking mechanisms</b>
 * <p>There are three locking mechanisms used with Link objects:</p>
 * <ol>
 *     <li>socket lock</li>
 *     <li>synchronized blocks</li>
 *     <li>wait queue lock</li>
 * </ol>
 *  These mechanisms when employed should be acquired in the order above.
 *  <p>The socket lock is required for state transitions, such as LINKING -> ESTABLISHED,
 *  and to modify certain variables that can only transition to new values when the socket
 *  lock is held, which are most of the variables involved in deciding which state transition
 *  should occur. Examples of these variables are: state, peerProtocolId, clockId, etc.</p>
 *  <p>Synchronized blocks are used to ensure consistency between actions that do not
 *  require the socket lock to be held, i.e., when the operations do not involve state transitions.</p>
 *  <p>Wait queue lock is the lock used to ensure consistency when queuing and waking up waiters.</p>
 */
public class Link implements Pollable {
    public enum LinkState {
        // When waiting for the peer's answer regarding the link establishment.
        // If a LINK message from the peer has been received, then any data/control
        // message can make the link progress to established.
        LINKING,
        // when waiting for an unlink message to close the link
        UNLINKING,
        // when wanting to cancel a linking process
        CANCELLING,
        ESTABLISHED,
        CLOSED;
    }

    private final LinkIdentifier id;
    private LinkState state = LinkState.LINKING;
    private Integer peerProtocolId = null;
    private final WaitQueue waitQ = new WaitQueue(); // for I/O events on the link

    /**
     * Link constructor
     * @param id identifier of the link
     * @param clockId clock identifier of the link
     * @param initialCapacity initial amount of credits provided to the sender
     * @param dispatcher link observer
     */
    public Link(LinkIdentifier id, int clockId, int initialCapacity, LinkDispatcher dispatcher) {
        if(!LinkIdentifier.isValid(id))
            throw new IllegalArgumentException("Not a valid link identifier.");
        this.id = id;
        this.clockId = clockId;
        this.inFCS = new InFlowControlState(initialCapacity);
        this.dispatcher = dispatcher;
    }

    /**
     * Link constructor
     * @param ownerId link's owner identifier
     * @param peerId link's peer identifier
     * @param clockId clock identifier of the link
     * @param initialCapacity initial amount of credits provided to the sender
     * @param dispatcher link observer
     */
    public Link(SocketIdentifier ownerId, SocketIdentifier peerId, int clockId, int initialCapacity, LinkDispatcher dispatcher) {
        this(new LinkIdentifier(ownerId, peerId), clockId, initialCapacity, dispatcher);
    }

    public LinkIdentifier getId() {
        return id;
    }

    public SocketIdentifier getOwnerId() {
        return id.srcId();
    }

    public SocketIdentifier getDestId() {
        return id.destId();
    }

    synchronized LinkState getState() {
        return state;
    }

    synchronized void setState(LinkState state) {
        this.state = state;
    }

    public Integer getPeerProtocolId() {
        return peerProtocolId;
    }

    /**
     * Lets setting the peer protocol once known. Setting this
     * value is limited to one time.
     * @param peerProtocolId peer's protocol id
     */
    void setPeerProtocolId(Integer peerProtocolId) {
        this.peerProtocolId = peerProtocolId;
    }

    /**
     * Lets setting an incoming message queue once the required information
     * to determine the best message queue is gathered. Setting this
     * value is limited to one time.
     * @param inMsgQ incoming message queue
     */
    synchronized void setInMsgQ(Queue<SocketMsg> inMsgQ) {
        if(this.inMsgQ == null) this.inMsgQ = inMsgQ;
    }

    // ********* Linking/Unlinking related methods ********* //

    private final int clockId;
    private int peerClockId = Integer.MIN_VALUE;
    private AtomicReference<SocketMsg> scheduled = null; // To keep track of scheduled LINK message
    // For the linking/unlinking process. Holds the value present in
    // the peer's LINKREPLY msg that was received before the peer's LINK msg.
    // When a LINK msg is received, this variable is verified.
    // If 'null' the socket is not waiting for a link msg.
    // If holding a positive reply code, then the link can be established.
    // If holding a negative non-fatal reply code, then a link request may be scheduled.
    // If holding a negative fatal reply code, then the link may be closed.
    private Integer linkReplyMsgReceived = null;

    int getClockId() {
        return clockId;
    }

    int getPeerClockId() {
        return peerClockId;
    }

    /**
     * Lets setting the peer's clock identifier.
     * @param peerClockId peer's clock identifier
     */
    void setPeerClockId(int peerClockId) {
        this.peerClockId = peerClockId;
    }


    /** @return scheduled (LINK) message or null if there isn't one. */
    AtomicReference<SocketMsg> getScheduled() {
        return scheduled;
    }

    /**
     * Set scheduled (LINK) message.
     * @param scheduled atomic reference to the scheduled message.
     */
    void setScheduled(AtomicReference<SocketMsg> scheduled) {
        this.scheduled = scheduled;
    }

    /**
     * Cancels scheduled message if there is one.
     * @return scheduled message or 'null' if the message was already dispatched
     * or if a message was not scheduled.
     */
    SocketMsg cancelScheduledMessage(){
        SocketMsg msg = null;
        if(scheduled != null){
            msg = scheduled.getAndSet(null);
            scheduled = null;
        }
        return msg;
    }

    Integer isLinkReplyMsgReceived() {
        return linkReplyMsgReceived;
    }

    void setLinkReplyMsgReceived(Integer linkReplyMsgReceived) {
        this.linkReplyMsgReceived = linkReplyMsgReceived;
    }


    /** @return true if the peer metadata has not yet been received. */
    boolean isWaitingPeerMetadata(){
        // If the peer protocol has not yet been received,
        // then we assume the metadata has not yet been received.
        return getPeerProtocolId() == null;
    }

    /**
     * Sets peer's information received on a LINK/LINKREPLY message.
     * This method limits itself to setting the values. It does not
     * do any special procedures, such as waking up waiters.
     * @param protocolId peer's protocol identifier
     * @param clockId peer's clock identifier
     * @param outCredits peer's provided credits
     */
    void setPeerMetadata(int protocolId, int clockId, int outCredits){
        // set peer's protocol
        setPeerProtocolId(protocolId);
        // update peer's link clock identifier
        setPeerClockId(clockId);
        // update outgoing credits
        outFCS.applyCreditVariation(outCredits);
    }

    /**
     * Resets peer metadata.
     * Used when the linking process must be retried.
     */
    void resetPeerMetadata(){
        setPeerProtocolId(null);
        setPeerClockId(Integer.MIN_VALUE);
        outFCS.applyCreditVariation(-outFCS.getCredits()); // makes credits reset to 0
    }

    // ********* Link Observer ********* //

    public interface LinkDispatcher {
        /**
         * Dispatch flow control message with the provided credits batch
         * to the peer associated with the link.
         * @param link link that wants to dispatch the batch.
         * @param batch credits to be dispatched
         */
        void onBatchReadyEvent(Link link, int batch);

        /**
         * Dispatch data or control message to the peer associated with the link.
         * This method does not check if the message is valid under the socket semantics.
         * @param link link that wants to dispatch a data or control message
         * @param payload payload
         * @throws IllegalArgumentException If payload is not valid.
         */
        void onOutgoingMessage(Link link, Payload payload) throws IllegalArgumentException;
    }

    private final LinkDispatcher dispatcher;

    // ********** Incoming flow Methods ********** //
    private Queue<SocketMsg> inMsgQ = null;
    private final InFlowControlState inFCS;

    /**
     * Inform the need to send a batch of credits
     * to the peer associated with this link.
     * @param batch credits to be sent.
     */
    private void sendCredits(int batch) {
        if(batch != 0) dispatcher.onBatchReadyEvent(this, batch);
    }

    /**
     * While this method informs how many elements are in the queue,
     * it does not inform if there is a message ready to be polled.
     * To obtain such information, use hasAvailableIncomingMessages().
     *
     * @return the amount of messages in the queue */
    public synchronized int countIncomingMessages(){
        return inMsgQ.size();
    }

    /**
     * While this method informs if the queue has elements, it is not an
     * indicator that a message can be polled. To obtain such information,
     * use hasAvailableIncomingMessages().
     *
     * @return true if the queue has elements. false, otherwise. */
    public synchronized boolean hasIncomingMessages(){
        return !inMsgQ.isEmpty();
    }

    /** @return true if the queue has at least one element that can be polled. false, otherwise. */
    public synchronized boolean hasAvailableIncomingMessages(){
        return inMsgQ.peek() != null;
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
     */
    synchronized void queueIncomingMessage(SocketMsg msg){
        // do not add if closed
        if(state != LinkState.CLOSED) {
            // add new message to incoming messages queue
            inMsgQ.add(msg);
            // notify waiters
            waitQ.fairWakeUp(0, 1, 0, PollFlags.POLLIN);
        }
    }

    /**
     * Method used when an incoming message is handled immediately,
     * and does not require queuing.
     */
    synchronized void acknowledgeDeliverAndIncrementBatch(){
        if(state != LinkState.CLOSED){
            inFCS.deliver();
        }
    }

    /**
     * Polls incoming message, and if successful, assumes message
     * as delivered/handled. If the delivery of the message completes a
     * credits batch, that batch is sent to the sender.
     * @return socket message or "null" if there wasn't an available message.
     */
    private synchronized SocketMsg tryPollingMessage(){
        SocketMsg msg = inMsgQ.poll();
        if(SocketMsg.isType(msg, MsgType.DATA)) {
            // if a message was polled,
            // "mark" the message as delivered and
            // send a credits batch when required
            int batch = inFCS.deliver();
            if(state != LinkState.CLOSED)
                sendCredits(batch);
        }
        return msg;
    }

    /**
     * @return capacity of the link, i.e., maximum amount
     * of messages that can be queued at a time.
     */
    public synchronized int getCapacity(){
        return inFCS.getCapacity();
    }

    /**
     * If the link is not closed, sets queuing capacity to the provided
     * value, sends current batch and clears the current batch.
     * @param newCapacity new capacity
     */
    public synchronized void setCapacity(int newCapacity) {
        if (state != LinkState.CLOSED) {
            int batch = inFCS.setCapacity(newCapacity);
            sendCredits(batch);
        }
    }
    
    /**
     * If the link is not closed, adjusts the capacity
     * using provided credit variation and clears the current batch.
     * @param credits variation of credits to apply to the
     *                current capacity.
     */
    public synchronized void adjustCapacity(int credits) {
        if (state != LinkState.CLOSED) {
            int batch = inFCS.adjustCapacity(credits);
            sendCredits(batch);
        }
    }

    /**
     * @return amount of credits that need to be batched before
     * returning credits to the sender.
     */
    public synchronized int getBatchSize(){
        return inFCS.getBatchSize();
    }

    /**
     * @return percentage of the capacity that makes
     * the size of a batch. Regardless of the batch size
     * percentage, when the capacity is
     * positive, the batch size is at least 1, and when the
     * capacity is zero or negative, the batch size is 0.
     */
    public synchronized float getBatchSizePercentage(){
        return inFCS.getBatchSizePercentage();
    }

    /**
     * Updates the batch size using the provided percentage.
     * @param newPercentage percentage of the capacity that should make the batch size.
     *                      When the capacity is positive, the minimum batch size is of 1 credit.
     * @throws IllegalArgumentException If the provided batch size percentage is not a
     * value between 0 (exclusive) and 1 (inclusive).
     */
    public synchronized void setCreditsBatchSizePercentage(float newPercentage) {
        if (state != LinkState.CLOSED) {
            int batch = inFCS.setBatchSizePercentage(newPercentage);
            sendCredits(batch);
        }
    }

    /**
     * Waits until an incoming message, from the peer associated with this
     * link, is available or the deadline is reached or the thread is interrupted.
     * If the socket is in COOKED mode, the returned messages are data messages.
     * If in RAW mode, the returned messages may also be custom control messages.
     * @param deadline waiting limit to poll an incoming message
     * @return available incoming message or "null" if the operation timed out
     * without a message becoming available.
     * @apiNote Caller must not hold socket lock as it will result in a deadlock
     * when a blocking operation with a non-expired deadline is requested.
     */
    public SocketMsg receive(Long deadline) throws InterruptedException {
        SocketMsg msg;
        WaitQueueEntry wait;
        ParkStateWithEvents ps;
        boolean timedOut = Timeout.hasTimedOut(deadline);

        synchronized (this){
            // if link is closed and queue is empty, messages cannot be received.
            if (getState() == LinkState.CLOSED && hasIncomingMessages())
                throw new LinkClosedException();
            // try retrieving a message immediately
            msg = tryPollingMessage();
            if (msg != null) return msg;
            // return if timed out
            if (timedOut) return null;
            // If a message could not be retrieved, make the caller a waiter
            wait = queueDirectEventWaiter(PollFlags.POLLIN, true);
            ps = (Link.ParkStateWithEvents) wait.getPriv();
        }

        while (true) {
            if (timedOut || getState() == LinkState.CLOSED)
                break;
            if (Thread.currentThread().isInterrupted()) {
                wait.delete();
                throw new InterruptedException();
            }
            // Wait until woken up
            WaitQueueEntry.defaultWaitFunction(wait, ps, deadline, true);
            synchronized (this){
                // attempt to poll a message
                msg = tryPollingMessage();
                if (msg != null) break;
                // if a message was not available and the operation has not
                // timed out, sets parked state to true preemptively,
                // so that a notification is not missed
                timedOut = Timeout.hasTimedOut(deadline);
                if (!timedOut) ps.parked.set(true);
            }
        }
        // delete entry since a hanging wait queue entry is not desired.
        wait.delete();
        return msg;
    }
    
    // ********** Outgoing flow Methods ********** //
    private final OutFlowControlState outFCS = new OutFlowControlState(0);

    public synchronized boolean hasOutgoingCredits(){
        return outFCS.hasCredits();
    }

    /**
     * @return amount of outgoing credits, i.e.,
     * amount of permits to send data messages.
     */
    public synchronized int getOutgoingCredits() {
        return outFCS.getCredits();
    }

    synchronized void adjustOutgoingCredits(int credits){
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
     * Tries consuming an outgoing credit to enable a message to be sent.
     * @return true if a credit was consumed. false, if a credit could not be consumed.
     */
    private boolean tryConsumingCredit(){
        return outFCS.trySend();
    }

    /**
     * Sends message to the peer associated with this link. This method
     * is not responsible for verifying if the control message follows
     * the custom semantics of the socket.
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
    public boolean send(Payload payload, Long deadline) throws InterruptedException {
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

        synchronized (this){
            // if link is closed, messages cannot be sent.
            if (getState() == LinkState.CLOSED)
                throw new LinkClosedException();
            // If message is a control message, then it can be dispatched immediately.
            // In case of a data message, permission from the flow control is required.
            if(payload.getType() != MsgType.DATA || hasOutgoingCredits()) {
                dispatcher.onOutgoingMessage(this, payload);
                return true;
            }
            // return if timed out
            if (timedOut) return false;
            // If the message could not be sent, make the caller a waiter
            wait = queueDirectEventWaiter(PollFlags.POLLOUT, true);
            ps = (ParkStateWithEvents) wait.getPriv();
        }

        while (true) {
            if (timedOut || getState() == LinkState.CLOSED)
                break;
            if (Thread.currentThread().isInterrupted()) {
                wait.delete();
                throw new InterruptedException();
            }
            // Wait until woken up
            WaitQueueEntry.defaultWaitFunction(wait, ps, deadline, true);
            synchronized (this){
                // attempt to send the message again
                if(hasOutgoingCredits())  {
                    dispatcher.onOutgoingMessage(this, payload);
                    ret = true;
                    break;
                }
                // if permission to send the message was not granted and
                // the operation has not timed out,sets parked state to
                // true preemptively, so that a notification is not missed
                timedOut = Timeout.hasTimedOut(deadline);
                if (!timedOut) ps.parked.set(true);
            }
        }
        // delete entry since a hanging wait queue entry is not desired.
        wait.delete();
        return ret;
    }

    // ********* Polling methods ********* //

        /**
     * @return mask of available events
     * @apiNote Thread must hold socket lock when the method is called. 
     */
    private int getAvailableEventsMask(){
        int events = 0;
        if(outFCS.hasCredits())
            events |= PollFlags.POLLOUT;
        if(inMsgQ.peek() != null)
            events |= PollFlags.POLLIN;
        if(state == LinkState.CLOSED)
            events |= PollFlags.POLLHUP;
        return events;
    }

    @Override
    public synchronized int poll(PollTable pt) {
        if (!PollTable.pollDoesNotWait(pt)) {
            WaitQueueEntry wait =
                    state != LinkState.CLOSED ? waitQ.initEntry() : null;
            pt.pollWait(this, wait);
        }
        return getAvailableEventsMask();
    }
    
    /**
     * Class that extends the park state to include events of interest,
     * enabling the use of the default wake-up methods.
     */
    static class ParkStateWithEvents extends ParkState{
        private final int events;

        public ParkStateWithEvents(boolean parkState, int events) {
            super(parkState);
            this.events = events;
        }

        public int getEvents() {
            return events;
        }
    }

    /**
     * Wait queue function for callers that use the link directly,
     * such as to receive or send a message, and are interested in I/O events.
     * This wake-up function wakes up a thread when there is a match between the key
     * and the events of interest. The events of interest are assumed to include POLLERR
     * and POLLHUP, in order for threads to wake-up, when the link is closed or an error occurs.
     */
    private static final WaitQueueFunc directEventWakeFunc = (entry, mode, flags, key) -> {
        ParkStateWithEvents ps = (ParkStateWithEvents) entry.getPriv();
        int ret = 0;
        // autoDeleteWakeFunction is not used, since a successfully woken up
        // waiter may not be able to retrieve the intended resource.
        if((ps.events & (int) key) != 0)
            ret = WaitQueueEntry.defaultWakeFunction(entry, mode, flags, key);
        return ret;
    };
    
    /**
     * <p>Adds a "direct" caller of the link object to the wait queue as an exclusive waiter.</p>
     * <p>The entry must be removed when the waiter is no longer interested in the events.</p>
     * <p>Subscribes to hang-up and error events in addition to the mentioned events.</p>
     * @param events events of interest
     * @param initialParkState initial park state
     * @return queued wait queue entry if the link is not closed. null, otherwise.
     * @implSpec Wait queue has own lock, so the queuing action does not require
     * any other locking mechanism to be secured when the method is called.
     */
    private WaitQueueEntry queueDirectEventWaiter(int events, boolean initialParkState){
        if(state != LinkState.CLOSED) {
            // subscribes hang-up and error events
            events |= PollFlags.POLLHUP | PollFlags.POLLERR;
            // park state is initialized with "true" to be ready for a
            // notification as soon as the entry is added to the queue
            ParkStateWithEvents ps = new ParkStateWithEvents(initialParkState, events);
            WaitQueueEntry wait = waitQ.initEntry();
            wait.addExclusive(directEventWakeFunc, ps);
            return wait;
        }else return null;
    }
    
    // ********* All-round methods ********* //

    synchronized void establish(){
        state = LinkState.ESTABLISHED;
        // wakes waiters if outgoing credits are positive
        int outCredits = outFCS.getCredits();
        if(!waitQ.isEmpty()) {
            // calling wake-up is required as there may be
            // non-exclusive waiters just waiting for the
            // link to be established, so waking up the
            // bare minimum of exclusive waiters is required
            // when there aren't credits to wake up waiters.
            int wakeUps = Math.max(outCredits, 1);
            waitQ.fairWakeUp(0, wakeUps, 0, PollFlags.POLLOUT);
        }
    }

    synchronized void close() {
        // remove a possibly scheduled message
        if(scheduled != null) scheduled.set(null); 
        state = LinkState.CLOSED;
        // notify with POLLHUP to inform the closure, and POLLFREE 
        // to make the wake-up callbacks remove the entry regardless 
        // of whether the waiter is waiting or not.
        waitQ.wakeUp(0,0,0, PollFlags.POLLHUP | PollFlags.POLLFREE);
    }
}
