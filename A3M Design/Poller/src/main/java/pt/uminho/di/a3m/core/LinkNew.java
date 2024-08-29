package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.core.flowcontrol.InFlowControlState;
import pt.uminho.di.a3m.core.flowcontrol.OutFlowControlState;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.waitqueue.WaitQueue;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

public class LinkNew {
    final LinkIdentifier id;
    private Integer peerProtocolId = null;
    private final int clockId;
    private int peerClockId = Integer.MIN_VALUE;
    final AtomicReference<LinkState> state = new AtomicReference<>(null);
    final InFlowControlState inFCS;
    final OutFlowControlState outFCS = new OutFlowControlState(0);
    final WaitQueue waitQ = new WaitQueue();
    private Queue<SocketMsg> inMsgQ = null;
    private AtomicReference<SocketMsg> scheduled = null; // To keep track of scheduled LINK message

    // TODO - remove unused variables
    private Queue<SocketMsg> outMsgQ = null; // TODO - is the outqueue still required?

    // For the linking/unlinking process. Holds the value present in
    // the peer's LINKACK msg that was received before the peer's LINK msg.
    // When a LINK msg is received, this variable is verified.
    // If 'null' the socket is not waiting for a link msg.
    // If holding a positive acknowledgement code, then the link can be established.
    // If holding a negative non-fatal acknowledgement code, then a link request may be scheduled.
    // If holding a negative fatal acknowledgement code, then the link may be closed.
    private Integer linkAckMsgReceived = null;

    /**
     * Link constructor
     * @param id identifier of the link
     * @param clockId clock identifier of the link
     * @param initialCapacity initial amount of credits provided to the sender
     */
    public LinkNew(LinkIdentifier id, int clockId, int initialCapacity) {
        if(!LinkIdentifier.isValid(id))
            throw new IllegalArgumentException("Not a valid link identifier.");
        this.id = id;
        this.clockId = clockId;
        this.inFCS = new InFlowControlState(initialCapacity);
    }

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

    public Integer getPeerProtocolId() {
        return peerProtocolId;
    }

    public int getClockId() {
        return clockId;
    }

    public int getPeerClockId() {
        return peerClockId;
    }

    /** @return scheduled (LINK) message or null if there isn't one. */
    public AtomicReference<SocketMsg> getScheduled() {
        return scheduled;
    }

    public Integer isLinkAckMsgReceived() {
        return linkAckMsgReceived;
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
     * Lets setting the peer's clock identifier.
     * @param peerClockId peer's clock identifier
     */
    public void setPeerClockId(int peerClockId) {
        this.peerClockId = peerClockId;
    }

    /**
     * Lets setting an incoming message queue once the required information
     * to determine the best message queue is gathered. Setting this
     * value is limited to one time.
     * @param inMsgQ incoming message queue
     */
    public void setInMsgQ(Queue<SocketMsg> inMsgQ) {
        if(this.inMsgQ == null)
            this.inMsgQ = inMsgQ;
    }

    /**
     * Set scheduled (LINK) message.
     * @param scheduled atomic reference to the scheduled message.
     */
    public void setScheduled(AtomicReference<SocketMsg> scheduled) {
        this.scheduled = scheduled;
    }

    /**
     * Cancels scheduled message if there is one.
     * @return scheduled message or 'null' if the message was already dispatched
     * or if a message was not scheduled.
     */
    public SocketMsg cancelScheduledMessage(){
        SocketMsg msg = null;
        if(scheduled != null){
            msg = scheduled.getAndSet(null);
            scheduled = null;
        }
        return msg;
    }

    public void setLinkAckMsgReceived(Integer linkAckMsgReceived) {
        this.linkAckMsgReceived = linkAckMsgReceived;
    }

    public void close() {
        if(scheduled != null) scheduled.set(null); // remove a possibly scheduled message
        state.set(LinkState.CLOSED);
        // Wake up all waiters with POLLHUP and
        // POLLFREE to inform the closure.
        waitQ.wakeUp(0,0,0, PollFlags.POLLHUP | PollFlags.POLLFREE);
    }
}
