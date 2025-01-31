package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.core.flowcontrol.InFlowControlState;
import pt.uminho.di.a3m.core.flowcontrol.OutFlowControlState;
import pt.uminho.di.a3m.core.messaging.Msg;
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

// TODO - should the wait queue be exposed by link socket as protected,
//  to allow more custom signaling, such as if bailing an exclusive send
//  or receive happens?

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
    private final LinkIdentifier id;
    private LinkState state = LinkState.LINKING;
    private Integer peerProtocolId = null;
    private final WaitQueue waitQ = new WaitQueue(); // for I/O events on the link

    /**
     * Link constructor
     * @param id identifier of the link
     * @param clockId clock identifier of the link
     * @param initialCapacity initial amount of credits provided to the sender
     * @param observer link observer
     */
    public Link(LinkIdentifier id, int clockId, int initialCapacity, float batchSizePercentage, LinkObserver observer) {
        if(!LinkIdentifier.isValid(id))
            throw new IllegalArgumentException("Not a valid link identifier.");
        this.id = id;
        this.clockId = clockId;
        this.inFCS = new InFlowControlState(initialCapacity, batchSizePercentage);
        this.observer = observer;
    }

    /**
     * Link constructor
     * @param ownerId link's owner identifier
     * @param peerId link's peer identifier
     * @param clockId clock identifier of the link
     * @param initialCapacity initial amount of credits provided to the sender
     * @param observer link observer
     */
    public Link(SocketIdentifier ownerId, SocketIdentifier peerId, int clockId, int initialCapacity, float batchSizePercentage, LinkObserver observer) {
        this(new LinkIdentifier(ownerId, peerId), clockId, initialCapacity, batchSizePercentage, observer);
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

    // To keep track of scheduled LINK message.
    // If "null" a scheduling task was not issued.
    // If the reference itself is null, then the message has been sent.
    // If the reference is not null, then the message has not yet been sent.
    private AtomicReference<Msg> scheduled = null;

    // For the linking/unlinking process. Holds the value present in
    // the peer's LINKREPLY msg that was received before the peer's LINK msg.
    // When a LINK msg is received, this variable is verified.
    // If 'null' the socket is not waiting for a link msg.
    // If holding a positive reply code, then the link can be established.
    // If holding a negative non-fatal reply code, then a link request may be scheduled.
    // If holding a negative fatal reply code, then the link may be closed.
    private Integer linkReplyMsgReceived = null;

    // sent data messages counter
    private int sentCounter = 0;

    // delivered data messages (from peer) counter
    private int deliveryCounter = 0;

    // peer's "dataCounter" value at the moment the link transitions to UNLINKING.
    // Received via the peer's UNLINK msg.
    // Represents the number of peer's data messages that must be delivered before
    // the link can be closed, i.e. when peerDataCounter == unlinkDataGoal
    // the link can be closed.
    private int unlinkDeliveryGoal = Integer.MIN_VALUE;


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
    AtomicReference<Msg> getScheduled() {
        return scheduled;
    }

    /**
     * Set scheduled (LINK) message.
     * @param scheduled atomic reference to the scheduled message.
     */
    void setScheduled(AtomicReference<Msg> scheduled) {
        this.scheduled = scheduled;
    }

    /**
     * Cancels scheduled message if there is one.
     * @return 0 if there wasn't a scheduled message.
     *         1 if the message was sent.
     *         -1 if the message was not sent.
     */
    int cancelScheduledMessage(){
        int ret = 0;
        if(scheduled != null){
            // Retrieves the message and cancels its
            // dispatching atomically.
            Msg msg = scheduled.getAndSet(null);
            // If "msg" is null, then the message was already dispatched.
            ret = msg == null ? 1 : -1;
            // Set "scheduled" to null to symbolize that there isn't a scheduled task anymore.
            scheduled = null;
        }
        return ret;
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

    synchronized public int getSentCounter() {
        return sentCounter;
    }

    synchronized public int getUnlinkDeliveryGoal() {
        return unlinkDeliveryGoal;
    }

    synchronized public void setUnlinkDeliveryGoal(int unlinkDeliveryGoal) {
        this.unlinkDeliveryGoal = unlinkDeliveryGoal;
    }

    synchronized public boolean isUnlinkDeliveryGoalSet(){
        return unlinkDeliveryGoal != Integer.MIN_VALUE;
    }

    // ********* Link Observer ********* //

    public interface LinkObserver {
        /**
         * To remove the link when it is closed. This event is triggered
         * when all the data messages from the peer have been received.
         * @param link link that is ready to complete the unlinking
         *             process and be closed
         */
        void onCloseEvent(Link link);

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

    private final LinkObserver observer;

    // ********** Incoming flow Methods ********** //
    private Queue<SocketMsg> inMsgQ = null;
    private final InFlowControlState inFCS;

    synchronized void printInMsgQ(){
        System.out.println(inMsgQ);
    }

    /**
     * Inform the need to send a batch of credits
     * to the peer associated with this link.
     * @param batch credits to be sent.
     */
    private void sendCredits(int batch) {
        if(batch != 0) observer.onBatchReadyEvent(this, batch);
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
    void queueIncomingMessage(SocketMsg msg){
        boolean notify = false;
        synchronized (this) {
            // do not add if closed
            if (state != LinkState.CLOSED) {
                // add new message to incoming messages queue
                try {
                    // notify only when a message becomes available
                    notify = inMsgQ.peek() == null;
                    inMsgQ.add(msg);
                    notify &= inMsgQ.peek() != null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if(notify)
            waitQ.fairWakeUp(0, 1, 0, PollFlags.POLLIN);
    }

    /**
     * Method used when an incoming message is handled immediately,
     * and does not require queuing.
     */
    void acknowledgeDelivery(){
        // updates flow control state to trigger
        // credits to be sent to the peer
        synchronized (this) {
            if (state != LinkState.CLOSED) {
                int batch = inFCS.deliver();
                sendCredits(batch);
            }
        }
        // updates delivery counter required
        // for the unlinking process
        updateDeliveryCounterAndCloseIfUnlinking();
    }

    /**
     * Polls incoming message, and if successful, assumes message
     * as delivered/handled. If the delivery of the message completes a
     * credits batch, that batch is sent to the sender.
     * @return socket message or "null" if there wasn't an available message.
     * @implNote Assumes the caller to hold the link's monitor (synchronized)
     */
    private SocketMsg tryPollingMessage() {
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
     * Updates delivery counter and returns whether the link
     * is ready to be closed.
     * @return true if the link is ready to be closed, i.e. if the link is
     * in the UNLINKING state and the delivery counter meets the delivery goal.
     * @apiNote Assumes the caller to NOT hold the link's monitor
     */
    private boolean updateDeliveryCounter(){
        synchronized (this) {
            deliveryCounter++;
            return state == LinkState.UNLINKING
                    && deliveryCounter == unlinkDeliveryGoal;
        }
    }

    /**
     * Updates peer's delivered counter and signals a close
     * event if the link state is ready to finish the unlink process.
     * @apiNote Assumes the caller to NOT hold the link's monitor
     */
    private void updateDeliveryCounterAndCloseIfUnlinking() {
        boolean close = updateDeliveryCounter();
        if(close) observer.onCloseEvent(this);
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
     * @throws LinkClosedException when the link is closed and there are no more messages
     * to be received.
     * @apiNote Caller must not hold socket lock as it will result in a deadlock
     * when a blocking operation with a non-expired deadline is requested.
     */
    public SocketMsg receive(Long deadline) throws InterruptedException {
        SocketMsg msg;
        WaitQueueEntry wait = null;
        ParkStateWithEvents ps = null;
        boolean timedOut = Timeout.hasTimedOut(deadline);

        synchronized (this) {
            // if link is closed and queue is empty, messages cannot be received.
            if (getState() == LinkState.CLOSED && !hasIncomingMessages())
                throw new LinkClosedException();
            // try retrieving a message immediately
            msg = tryPollingMessage();
            if (msg == null && !timedOut) {
                // If a message could not be retrieved, make the caller a waiter
                wait = queueDirectEventWaiter(PollFlags.POLLIN, true);
                // if queuing was successful, get the park state object
                if (wait != null)
                    ps = (ParkStateWithEvents) wait.getPriv();
            }
        }

        // if a wait queue entry does not exist, then
        // the method must return now.
        if(wait == null) {
            if (msg != null) updateDeliveryCounterAndCloseIfUnlinking();
            return msg;
        }

        try {
            while (true) {
                if (timedOut || getState() == LinkState.CLOSED)
                    break;
                // Wait until woken up
                WaitQueueEntry.defaultWaitUntilFunction(wait, ps, deadline, true);
                synchronized (this) {
                    // attempt to poll a message
                    msg = tryPollingMessage();
                    if (msg != null) break;
                    // if a message was not available and the operation has not
                    // timed out, sets parked state to true preemptively,
                    // so that a notification is not missed
                    timedOut = Timeout.hasTimedOut(deadline);
                    if (!timedOut) ps.setParkState(true);
                }
            }
        } finally {
            // delete entry since a hanging wait queue entry is not desired.
            wait.delete();
            if (hasAvailableIncomingMessages())
                waitQ.fairWakeUp(0, 1, 0, PollFlags.POLLIN);
        }

        if (msg != null) updateDeliveryCounterAndCloseIfUnlinking();
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

    void adjustOutgoingCredits(int credits){
        boolean notify;
        synchronized (this) {
            int balance = outFCS.applyCreditVariation(credits);
            // Notify only the credits received are positive
            // and when the link credits transitions to having
            // a positive amount of credits
            notify = balance - credits <= 0
                    && balance > 0;
        }
        if (notify)
            waitQ.fairWakeUp(0, 1, 0, PollFlags.POLLOUT);
    }

    /**
     * Tries consuming an outgoing credit to enable a message to be sent.
     * @return true if a credit was consumed. false, if a credit could not be consumed.
     * @implNote Assumes caller to hold the link's monitor (synchronized)
     */
    private boolean tryConsumingCredit(){
        return outFCS.trySend();
    }

    /**
     * Signals an outgoing message event with the given
     * payload and increments the counter of data messages sent.
     * @param payload payload to be dispatched
     * @apiNote Assumes the called to hold the link's monitor
     */
    private void dispatch(Payload payload){
        observer.onOutgoingMessage(this, payload);
        if(payload.getType() == MsgType.DATA)
            sentCounter++;
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
     * @throws LinkClosedException If the link was closed.
     * @apiNote Caller must not hold socket lock as it will result in a deadlock
     * when a blocking operation with a non-expired deadline is requested.
     */
    public boolean send(Payload payload, Long deadline) throws InterruptedException {
        boolean ret = false;
        WaitQueueEntry wait = null;
        ParkStateWithEvents ps = null;
        boolean timedOut = Timeout.hasTimedOut(deadline);

        if (payload == null)
            throw new IllegalArgumentException("Invalid payload.");

        // if payload is a reserved type different from DATA,
        // then the send operation is not allowed.
        if (MsgType.isReservedType(payload.getType())
                && payload.getType() != MsgType.DATA)
            throw new IllegalArgumentException("Invalid payload type.");

        synchronized (this) {
            // if link is closed, messages cannot be sent.
            if (getState() == LinkState.CLOSED)
                throw new LinkClosedException();
            // If message is a control message, then it can be dispatched immediately.
            // In case of a data message, permission from the flow control is required.
            if (payload.getType() != MsgType.DATA || tryConsumingCredit()) {
                dispatch(payload);
                ret = true;
            }
            // If the message could not be sent and the
            // operation is blocking, make the caller a waiter
            if(!timedOut && !ret) {
                wait = queueDirectEventWaiter(PollFlags.POLLOUT, true);
                assert wait != null;
                ps = (ParkStateWithEvents) wait.getPriv();
            }
        }

        // if a wait queue entry does not exist, then
        // the method must return now.
        if(wait == null)
            return ret;

        try {
            while (true) {
                if (timedOut || getState() == LinkState.CLOSED)
                    break;
                // Wait until woken up
                WaitQueueEntry.defaultWaitUntilFunction(wait, ps, deadline, true);
                synchronized (this) {
                    // attempt to send the message again
                    if (tryConsumingCredit()) {
                        dispatch(payload);
                        ret = true;
                        break;
                    }
                    // if permission to send the message was not granted and
                    // the operation has not timed out,sets parked state to
                    // true preemptively, so that a notification is not missed
                    timedOut = Timeout.hasTimedOut(deadline);
                    if (!timedOut) ps.setParkState(true);
                }
            }
        } finally {
            // delete entry since a hanging wait queue entry is not desired.
            wait.delete();
            if (hasOutgoingCredits())
                waitQ.fairWakeUp(0, 1, 0, PollFlags.POLLOUT);
        }

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
        if(inMsgQ != null && inMsgQ.peek() != null)
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

    void establish(){
        int outCredits;
        synchronized (this) {
            state = LinkState.ESTABLISHED;
            // wakes waiters if outgoing credits are positive
            outCredits = outFCS.getCredits();
        }
        // calling wake-up is required as there may be
        // non-exclusive waiters just waiting for the
        // link to be established, so waking up the
        // bare minimum of exclusive waiters is required
        // when there aren't credits to wake up waiters.
        if(outCredits > 0)
            waitQ.fairWakeUp(0, 1, 0, PollFlags.POLLOUT);
        else
            // do non-fair wake up, to wake up exclusive threads
            // and let the exclusive waiter that won't be able to
            // send a message remain in its position.
            waitQ.wakeUp(0, 1, 0, 0);
    }

    public void unlink() {
        boolean close;
        synchronized (this){
            state = LinkState.UNLINKING;
            close = unlinkDeliveryGoal == deliveryCounter;
        }
        if(close) observer.onCloseEvent(this);
    }

    void close() {
        synchronized (this) {
            // remove a possibly scheduled message
            if (scheduled != null) scheduled.set(null);
            state = LinkState.CLOSED;
        }
        // notify with POLLHUP to inform the closure, and POLLFREE 
        // to make the wake-up callbacks remove the entry regardless 
        // of whether the waiter is waiting or not.
        waitQ.wakeUp(0,0,0, PollFlags.POLLHUP | PollFlags.POLLFREE);
    }
}
