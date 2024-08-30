package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.core.flowcontrol.InFlowControlState;
import pt.uminho.di.a3m.core.flowcontrol.OutFlowControlState;
import pt.uminho.di.a3m.core.messaging.MsgType;
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

// TODO - adjust methods visibility

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
    private final AtomicReference<LinkState> state = new AtomicReference<>(null);
    private Integer peerProtocolId = null;
    private final WaitQueue waitQ = new WaitQueue(); // for I/O events on the link

    /**
     * Link constructor
     * @param id identifier of the link
     * @param clockId clock identifier of the link
     * @param initialCapacity initial amount of credits provided to the sender
     * @param observer link observer
     */
    public Link(LinkIdentifier id, int clockId, int initialCapacity, LinkObserver observer) {
        this.observer = observer;
        if(!LinkIdentifier.isValid(id))
            throw new IllegalArgumentException("Not a valid link identifier.");
        this.id = id;
        this.clockId = clockId;
        this.inFCS = new InFlowControlState(initialCapacity);
    }

    /**
     * Link constructor
     * @param ownerId link's owner identifier
     * @param peerId link's peer identifier
     * @param clockId clock identifier of the link
     * @param initialCapacity initial amount of credits provided to the sender
     * @param observer link observer
     */
    public Link(SocketIdentifier ownerId, SocketIdentifier peerId, int clockId, int initialCapacity, LinkObserver observer) {
        this(new LinkIdentifier(ownerId, peerId), clockId, initialCapacity, observer);
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

    public LinkState getState() {
        return state.get();
    }

    public void setState(LinkState state) {
        this.state.set(state);
    }

    public Integer getPeerProtocolId() {
        return peerProtocolId;
    }

    /**
     * Lets setting the peer protocol once known. Setting this
     * value is limited to one time.
     * @param peerProtocolId peer's protocol id
     */
    public void setPeerProtocolId(Integer peerProtocolId) {
        this.peerProtocolId = peerProtocolId;
    }

    /**
     * Lets setting an incoming message queue once the required information
     * to determine the best message queue is gathered. Setting this
     * value is limited to one time.
     * @param inMsgQ incoming message queue
     */
    public void setInMsgQ(Queue<SocketMsg> inMsgQ) {
        if(this.inMsgQ == null) this.inMsgQ = inMsgQ;
    }

    // ********* Linking/Unlinking related methods ********* //

    private final int clockId;
    private int peerClockId = Integer.MIN_VALUE;
    private AtomicReference<SocketMsg> scheduled = null; // To keep track of scheduled LINK message
    // For the linking/unlinking process. Holds the value present in
    // the peer's LINKACK msg that was received before the peer's LINK msg.
    // When a LINK msg is received, this variable is verified.
    // If 'null' the socket is not waiting for a link msg.
    // If holding a positive acknowledgement code, then the link can be established.
    // If holding a negative non-fatal acknowledgement code, then a link request may be scheduled.
    // If holding a negative fatal acknowledgement code, then the link may be closed.
    private Integer linkAckMsgReceived = null;

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

    Integer isLinkAckMsgReceived() {
        return linkAckMsgReceived;
    }

    void setLinkAckMsgReceived(Integer linkAckMsgReceived) {
        this.linkAckMsgReceived = linkAckMsgReceived;
    }


    /** @return true if the peer metadata has not yet been received. */
    boolean isWaitingPeerMetadata(){
        // If the peer protocol has not yet been received,
        // then we assume the metadata has not yet been received.
        return getPeerProtocolId() == null;
    }

    /**
     * Sets peer's information received on a LINK/LINKACK message.
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

    public interface LinkObserver{
        void onBatchReadyEvent(Link link, int batch);
    }

    private final LinkObserver observer;



    // ********** Incoming flow Methods ********** //
    private Queue<SocketMsg> inMsgQ = null;
    private final InFlowControlState inFCS;

    /**
     * Inform the need to send a batch of credits
     * to the peer associated with this link.
     * @param batch credits to be sent.
     */
    private void sendCredits(int batch) {
        if(batch != 0) observer.onBatchReadyEvent(this, batch);
    }

    public boolean hasIncomingMessages(){
        return !inMsgQ.isEmpty();
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
    public void queueIncomingMessage(SocketMsg msg){
        // do not add if closed
        if(state.get() == LinkState.CLOSED)
            throw new IllegalStateException("Link is closed.");
        // add new message to incoming messages queue
        inMsgQ.add(msg);
        // notify waiters
        waitQ.fairWakeUp(0,1,0,PollFlags.POLLIN);
    }

    /**
     * Polls incoming message, and if successful, assumes message
     * as delivered/handled. If the delivery of the message completes a
     * credits batch, that batch is sent to the sender.
     * @return socket message or "null" if there wasn't an available message.
     */
    public SocketMsg tryPollingMessage(){
        SocketMsg msg = inMsgQ.poll();
        if(SocketMsg.isType(msg, MsgType.DATA)) {
            // if a message was polled,
            // "mark" the message as delivered and
            // send a credits batch when required
            int batch = inFCS.deliver();
            if(state.get() != LinkState.CLOSED)
                sendCredits(batch);
        }
        return msg;
    }
    
    /**
     * If the link is not closed, sets queuing capacity to the provided
     * value, sends current batch and clears the current batch.
     * @param newCapacity new capacity
     */
    public void setCapacity(int newCapacity) {
        if (state.get() != LinkState.CLOSED) {
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
    public void adjustCapacity(int credits) {
        if (state.get() != LinkState.CLOSED) {
            int batch = inFCS.adjustCapacity(credits);
            sendCredits(batch);
        }
    }

    /**
     * Updates the batch size using the provided percentage.
     * @param newPercentage percentage of the capacity that should make the batch size.
     *                      When the capacity is positive, the minimum batch size is of 1 credit.
     * @throws IllegalArgumentException If the provided batch size percentage is not a
     * value between 0 (exclusive) and 1 (inclusive).
     */
    public void setCreditsBatchSizePercentage(float newPercentage) {
        if (state.get() != LinkState.CLOSED) {
            int batch = inFCS.setBatchSizePercentage(newPercentage);
            sendCredits(batch);
        }
    }
    
    // ********** Outgoing flow Methods ********** //
    private final OutFlowControlState outFCS = new OutFlowControlState(0);

    boolean hasOutgoingCredits(){
        return outFCS.hasCredits();
    }

    void adjustOutgoingCredits(int credits){
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

    /* TODO [Socket send] - To send a message, the socket must have method that
        accepts dispatching a message if it is a DATA msg or if it
        is not inside the reserved space for core messages. Also,
        it must verify that the destination is linked and for
        DATA messages it must have a credit to send the message.
        ```
        if(payload.getType() == MsgType.DATA){
            boolean permission = linkManager.consumeCredit(destId, deadline);
            if(permission)
                dispatch(msg);
            else
                ... do something ... throw exception ... return false ... whatever
        }else if(!MsgType.isReservedType(payload.getType())){
            dispatch(destId, payload.getType(), payload.getPayload());
        } // else, ignore message because the custom logic does not have permission to send core messages.
        ```
    */
    // TODO [Socket receive] - When a message is received, the socket passes it to the
    //  link manager. The link manager then attempts to handle the message, if it decides
    //  it is not its duty to handle the message, then it informs so using the boolean return
    //  value (as true, as in, continue handling the message). Data and control messages are
    //  intercepted by the link manager, as there are some situations where the reception of these
    //  result in the establishment of the link making them valid messages that can be further
    //  processed by the socket instead of being discarded as they are when received in an invalid state,
    //  such as when the link does not exist. After checking that the data and control messages can continue
    //  to be processed, the socket is then responsible for feeding the message to the custom socket logic,
    //  which then decides if a message should continue to be handled by the socket, such as queueing a
    //  DATA message or if the process ends there, be it because a message is invalid or because the message
    //  was handled on the spot.

    /**
     * Attempt to consume credit until the deadline is reached.
     * @param deadline timestamp at which the method must return
     *                 even if a credit was not consumed. A 'null' value
     *                 means waiting forever.
     * @return true if a credit was consumed. false, otherwise.
     */
    private boolean consumeCredit(Long deadline){
        // TODO - consumeCredit()
        return false;
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
        if(!inMsgQ.isEmpty())
            events |= PollFlags.POLLIN;
        if(state.get() == LinkState.CLOSED)
            events |= PollFlags.POLLHUP;
        return events;
    }

    @Override
    public int poll(PollTable pt) {
        if (!PollTable.pollDoesNotWait(pt)) {
            WaitQueueEntry wait =
                    state.get() != LinkState.CLOSED ? waitQ.initEntry() : null;
            pt.pollWait(this, wait);
        }
        return getAvailableEventsMask();
    }
    
    /**
     * Class that extends the park state to include events of interest,
     * enabling the use of the default wake-up methods.
     */
    class ParkStateWithEvents extends ParkState{
        private int events;

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
    WaitQueueEntry queueDirectEventWaiter(int events, boolean initialParkState){
        if(state.get() != LinkState.CLOSED) {
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
        state.set(LinkState.ESTABLISHED);
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

    void close() {
        // remove a possibly scheduled message
        if(scheduled != null) scheduled.set(null); 
        state.set(LinkState.CLOSED);
        // notify with POLLHUP to inform the closure, and POLLFREE 
        // to make the wake-up callbacks remove the entry regardless 
        // of whether the waiter is waiting or not.
        waitQ.wakeUp(0,0,0, PollFlags.POLLHUP | PollFlags.POLLFREE);
    }
}
