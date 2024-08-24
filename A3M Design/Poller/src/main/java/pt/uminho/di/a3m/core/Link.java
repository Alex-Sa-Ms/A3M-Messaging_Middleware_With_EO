package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.core.flowcontrol.InFlowControlState;
import pt.uminho.di.a3m.core.flowcontrol.OutFlowControlState;
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

public class Link implements Pollable {
    // TODO - Link class
    private final LinkIdentifier id;
    private Protocol peerProtocol = null;
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

    /**
     * Record that combines a park state instance and events of interest
     * to be used by methods of the link instance that imply waiting
     * for I/O operations.
     * @param ps park state
     * @param events events of interest
     */
    private record ParkStateWithEvents(ParkState ps, int events){}

    /**
     * Record that combines a park state instance and a payload.
     * To be used when queuing a waiter that wants to send a message.
     * @param ps park state
     * @param payload payload to be sent
     */
    private record ParkStateWithPayload(ParkState ps, Payload payload){}

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
     * @param peerProtocol peer's protocol
     * @apiNote Thread must hold socket lock when the method is called. 
     */
    public void setPeerProtocol(Protocol peerProtocol) {
        if(this.peerProtocol == null)
            this.peerProtocol = peerProtocol;
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
     * @implNote Socket lock is assumed to be held by the caller.
     */
    private SocketMsg tryPollingMessage(){
        SocketMsg msg = inMsgQ.poll();
        if(msg != null && Socket.isSocketDataMsg(msg)) {
            // if a message was polled,
            // "mark" the message as delivered and
            // send a credits batch when required
            int batch = inFCS.deliver();
            if(batch != 0)
                sendCredits(batch);
        }
        return msg;
    }

    /**
     * Sends credits to the peer associated with this link.
     * @param credits credits to be sent. Can be negative
     *                if removing credits from the peer
     *                is desirable.
     */
    private void sendCredits(int credits) {
        // TODO - sendCredits()
    }

    /**
     * Tries to dispatch message using the provided payload.
     * If the payload is data payload (analogous to data msg),
     * then in addition to requiring the validation of the
     * socket to dispatch the message, it also requires a credit.
     * @return "true" if the message was dispatched. "false" if
     * the message could not be sent immediately.
     * @throws IllegalArgumentException Can throw illegal argument
     * exception if the payload is not considered valid under the
     * socket semantics. This differs from receiving "false" as the
     * message may not be accepted under the current state but
     * may be in the future.
     * @implNote Socket lock is assumed to be held by the caller.
     */
    private boolean tryDispatchingMessage(Payload payload){
        boolean ret = false;
        if(owner.isPayloadValid(payload)
                && (!Socket.isSocketDataPayload(payload) || outFCS.trySend())) {
            owner.getMessageDispatcher().dispatch(
                    new SocketMsg(id.srcId(), id.destId(), payload));
            ret = true;
        }
        return ret;
    }

    /**
     * 
     * @param msg
     * @return
     * @apiNote Thread must hold socket lock when the method is called. 
     */
    boolean handleLinkMessage(SocketMsg msg){
        // TODO - handleLinkMsg() / mudar tipo para receber LinkMsg?
        return false;
    }

    /**
     * 
     * @param msg
     * @return
     * @apiNote Thread must hold socket lock when the method is called. 
     */
    boolean handleFlowControlMessage(SocketMsg msg){
        // TODO - handleFlowControlMessage() / mudar tipo para receber FlowControlMsg?
        return false;
    }

    /**
     *
     * @apiNote Thread must hold socket lock when the method is called. 
     */
    void close(){
        // TODO - close()
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
     * with this link, is available or the dealine is reached or
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

            // try retrieving a message immediatelly
            msg = tryPollingMessage();
            if (msg != null)
                return msg;

            if (timedOut)
                return null;

            // If a message could not be retrieved,
            // make the caller a waiter
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
                if (msg != null)
                    break;
                // if a message was not available and
                // the operation has not timed out,
                // sets parked state to true preemptively, 
                // so that a notification is not missed
                timedOut = Timeout.hasTimedOut(deadline);
                if (!timedOut)
                    ps.parked.set(true);
            } finally {
                owner.getLock().unlock();
            }
        }

        // delete entry since a hanging wait 
        // queue entry is not desired.
        wait.delete();
        return msg;
    }

    /**
     * Wait queue function for payload senders, i.e.,
     * callers interested in the POLLOUT event.
     * This wake-up function wakes up a thread when the
     * link is closed, is an error state, or when the
     * required sending conditions are met.
     */
    private static final WaitQueueFunc payloadSenderWakeFunc = (entry, mode, flags, key) -> {
        ParkStateWithPayload pswe = (ParkStateWithPayload) entry.getPriv();
        int ret = 0;
        if((PollFlags.POLLOUT & (int) key) != 0){
            // TODO - try sending the message, maybe use atomic reference to extract message to be sent,
            //  in order to avoid re-sending the message, or simply check if the waiter is parked or not.

        } // wakes up the thread if the link is closed or an error occurrs.
        else if(((PollFlags.POLLHUP | PollFlags.POLLERR) & (int) key) != 0)
            ret = entry.parkStateWakeUp(pswe.ps);
        return ret;
    };

    /**
     * <p>Adds a waiter that wants to send a payload as an exclusive waiter.
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
    private WaitQueueEntry queuePayloadSender(int events, boolean initialParkState){
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
    public boolean send(Payload payload, Long deadline){
        WaitQueueEntry wait;
        ParkState ps;
        boolean timedOut = Timeout.hasTimedOut(deadline);

        try {
            owner.getLock().lock();

            // if link is closed, messages cannot be sent.
            if (state.get() == LinkState.CLOSED)
                throw new LinkClosedException();

            // try sending a message immediatelly
            if(tryDispatchingMessage(payload))
                return true;

            if (timedOut)
                return false;

            // TODO - checking if payload is valid when attempting to
            //  dispatch a message makes a lot of sense. But so does
            //  doing such verification before waking up a waiter exclusively.
            //  However, since waking up and sending is not done atomically,
            //  maybe the dispatching of the message needs to be done at the
            //  wake up to prevent multiple verifications of the payload and
            //  to avoid pointless wake-up calls.

            // If the message could not be sent,
            // make the caller a waiter
            // TODO - keep developing send(), requires a method that queues a message to be sent.
            //  Instead of having an outgoing queue, the message can be queued with the waiter.
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
                msg = inMsgQ.poll();
                if (msg != null)
                    break;
                // if a message was not available and
                // the operation has not timed out,
                // sets parked state to true preemptively,
                // so that a notification is not missed
                timedOut = Timeout.hasTimedOut(deadline);
                if (!timedOut)
                    ps.parked.set(true);
            } finally {
                owner.getLock().unlock();
            }
        }

        // delete entry since a hanging wait
        // queue entry is not desired.
        wait.delete();
        return msg;








        // ---------

        boolean ret = false;
        try {
            owner.getLock().lock();

        } finally {
            owner.getLock().unlock();
        }

        return ret;
    }
    
    // TODO - delete this method if not employed in the end
    /**
     * Notify available messages. If "nrExclusive" is null,
     * notifies an exclusive waiter interested in the POLLIN event
     * for each available message in the incoming queue.
     */
    private void notifyAvailableMessages(Integer nrExclusive){
        try {
            owner.getLock().lock();
            if(!waitQ.isEmpty() && !inMsgQ.isEmpty()) {
                if(nrExclusive == null) 
                    nrExclusive = inMsgQ.size();
                waitQ.fairWakeUp(0, nrExclusive, 0, PollFlags.POLLIN);
            }
        }finally {
            owner.getLock().unlock();
        }
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
        // TODO - poll()
        return 0;
    }
}