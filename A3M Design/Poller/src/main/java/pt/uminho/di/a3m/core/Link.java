package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.core.flowcontrol.InFlowControlState;
import pt.uminho.di.a3m.core.flowcontrol.OutFlowControlState;
import pt.uminho.di.a3m.core.messages.SocketMsg;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.waitqueue.WaitQueue;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

public class Link {
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

    public LinkIdentifier getId() {
        return id;
    }

    /**
     * Sets the incoming message queue
     * if a queue has not been set already.
     * @param inMsgQ incoming message queue
     */
    void setIncomingMessageQueue(Queue<SocketMsg> inMsgQ) {
        if(this.inMsgQ == null)
            this.inMsgQ = inMsgQ;
    }

    /**
     * Sets the peer's protocol if the
     * peer's protocol has not been set yet.
     * @param peerProtocol peer's protocol
     */
    public void setPeerProtocol(Protocol peerProtocol) {
        if(this.peerProtocol == null)
            this.peerProtocol = peerProtocol;
    }

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
        // do not add if closed
        if(state.get() == LinkState.CLOSED)
            throw new IllegalStateException("Link is closed.");
        // add new message to incoming messages queue
        inMsgQ.add(msg);
        // notify waiters
        waitQ.fairWakeUp(0,1,0,PollFlags.POLLIN);
    }

    boolean handleLinkMessage(SocketMsg msg){
        // TODO - handleLinkMsg() / mudar tipo para receber LinkMsg?
        return false;
    }

    boolean handleFlowControlMessage(SocketMsg msg){
        // TODO - handleFlowControlMessage() / mudar tipo para receber FlowControlMsg?
        return false;
    }

    void close(){
        // TODO - close()
    }
}