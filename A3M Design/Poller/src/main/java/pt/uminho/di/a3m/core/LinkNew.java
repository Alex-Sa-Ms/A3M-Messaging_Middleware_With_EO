package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.core.flowcontrol.InFlowControlState;
import pt.uminho.di.a3m.core.flowcontrol.OutFlowControlState;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
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
        LINKING, // when waiting for the peer's answer regarding the link establishment
        UNLINKING, // when waiting for an unlink message to close the link
        WAITING_TO_UNLINK, // when waiting for a link/link acknowledgment message to change to unlinking
        ESTABLISHED,
        CLOSED;
    }

    public Integer getPeerProtocol() {
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


    /**
     * Lets setting the peer protocol once known. Setting this
     * value is limited to one time.
     * @param peerProtocolId peer's protocol id
     */
    public void setPeerProtocol(Integer peerProtocolId) {
        if(this.peerProtocolId == null)
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
}
