package pt.uminho.di.a3m.core.SimpleSocket;

import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.*;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.core.messaging.payloads.BytePayload;
import pt.uminho.di.a3m.poller.PollEvent;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.poller.Poller;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Simple socket:
 * <ul>
 *     <li>Requires total ordering of data messages.</li>
 *     <li>Sends message to the first available receiver.</li>
 *     <li>Receives message from the first available sender.</li>
 * </ul>
 */
public class SimpleSocket extends Socket {
    final static private String protocolName = "SimpleTotalOrderSocket";
    final static public Protocol protocol = new Protocol(protocolName.hashCode(), protocolName);
    final static public Set<Protocol> compatProtocols = Collections.singleton(protocol);
    final private Poller readPoller = Poller.create();
    final private Poller writePoller = Poller.create();
    final private Map<SocketIdentifier, Integer> orderCounters = new ConcurrentHashMap<>();

    protected SimpleSocket(SocketIdentifier sid) {
        super(sid);
    }

    public static SimpleSocket createSocket(SocketIdentifier sid){
        return new SimpleSocket(sid);
    }

    @Override
    protected void init() {
        // empty because it does not require any special initializing procedures
    }

    @Override
    protected void destroy() {
        // empty because it does not require any special closing procedures
    }

    private void resubscribeReadEvent(LinkIdentifier linkId){
        readPoller.modify(linkId, PollFlags.POLLIN | PollFlags.POLLET | PollFlags.POLLONESHOT);
    }

    private void resubscribeWriteEvent(LinkIdentifier linkId){
        writePoller.modify(linkId, PollFlags.POLLOUT | PollFlags.POLLET | PollFlags.POLLONESHOT);
    }

    @Override
    protected void customOnLinkEstablished(LinkSocket linkSocket) {
        // add link socket to pollers
        readPoller.add(linkSocket, PollFlags.POLLIN | PollFlags.POLLET | PollFlags.POLLONESHOT);
        writePoller.add(linkSocket, PollFlags.POLLOUT | PollFlags.POLLET | PollFlags.POLLONESHOT);
        // add counter
        orderCounters.put(linkSocket.getPeerId(), 0);
    }

    @Override
    protected void customOnLinkClosed(LinkSocket linkSocket) {
        // remove link socket from pollers
        readPoller.delete(linkSocket);
        writePoller.delete(linkSocket);
        // remove counter
        orderCounters.remove(linkSocket.getPeerId());
    }

    @Override
    protected SocketMsg customOnIncomingMessage(SocketMsg msg) {
        // notify waiters
        getWaitQueue().fairWakeUp(0,1,0,PollFlags.POLLIN);
        // Return false because it does not use custom control messages,
        // and all data messages should be queued in the appropriate link's queue.
        return SocketMsgWithOrder.parseFrom(msg);
    }

    @Override
    public Protocol getProtocol() {
        return protocol;
    }

    @Override
    public Set<Protocol> getCompatibleProtocols() {
        return compatProtocols;
    }

    private final Comparator<SocketMsg> orderComparator = (o1, o2) -> {
        SocketMsgWithOrder s1 = (SocketMsgWithOrder) o1;
        SocketMsgWithOrder s2 = (SocketMsgWithOrder) o2;
        return Integer.compare(s1.getOrder(),s2.getOrder());
    };

    @Override
    protected Queue<SocketMsg> createIncomingQueue(int peerProtocolId) {
        return new PriorityQueue<>(orderComparator){
            private int next = 0;

            @Override
            public SocketMsg peek() {
                SocketMsgWithOrder msg = (SocketMsgWithOrder) super.peek();
                // make peek() return null if the first element in the queue is not
                // the next in order.
                if(msg != null && msg.getOrder() != next)
                    msg = null;
                return msg;
            }

            @Override
            public SocketMsg poll() {
                // make poll() only extract the first element of the queue,
                // when it is the next one in order.
                SocketMsg msg = peek();
                if(msg != null) {
                    next++;
                    return super.poll();
                }else
                    return null;
            }
        };
    }

    public static class SimpleLinkSocket extends LinkSocket{
        int next = 0;
        Lock lock = new ReentrantLock();

        private BytePayload createDataPayload(byte[] payload){
            byte[] payloadWithOrder =
                    ByteBuffer.allocate(payload.length + 4) // allocate space for payload plus order number
                              .putInt(next) // put order number
                              .put(payload) // put payload
                              .array(); // convert to byte array
            return new BytePayload(MsgType.DATA, payloadWithOrder);
        }

        public boolean trySend(byte[] payload) throws InterruptedException {
            boolean locked = lock.tryLock();
            if(locked) {
                try {
                    boolean sent = super.send(createDataPayload(payload), 0L);
                    System.out.println("Sent: dest=" + this.getPeerId() + ", next=" + next);
                    if (sent) next++;
                    return sent;
                } finally {
                    lock.unlock();
                }
            }else return false;
        }

        /**
         * Example of how to send messages with deadlines or timeouts,
         * when synchronization between sending is required. If a thread
         * cannot execute the send operation due to the lack of credits,
         * then all threads that attempt to do so, will also not be successful.
         * This is relevant, as maintaining consistency between order numbers is mandatory,
         * i.e. we don't want to acquire an order number, let another thread acquire the order
         * number that follows, and potentially end up with only the second thread to send a message,
         * leaving a gap on the order numbers which will stall the progress.
         * So, the solution is to protect the sending of the message using a reentrant lock, and waiting
         * for lock acquisition using tryLock. If acquiring the lock is not possible during the provided
         * timeout or deadline, then the reason behind this incapability, most likely, is that the link
         * is being throttled by the flow control.
         * @param payload
         * @param deadline
         * @return
         * @throws InterruptedException
         */
        public boolean send(byte[] payload, Long deadline) throws InterruptedException {
            boolean locked;
            if(deadline == null){
                lock.lock();
                locked = true;
            } else
                locked = lock.tryLock(Timeout.calculateTimeout(deadline), TimeUnit.MILLISECONDS);
            if(locked) {
                try {
                    boolean sent = super.send(createDataPayload(payload), deadline);
                    if(sent) next++;
                    return sent;
                } finally {
                    lock.unlock();
                }
            }else return false;
        }

        @Override
        public boolean send(Payload payload, Long deadline) throws InterruptedException {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    protected LinkSocket createLinkSocketInstance(int peerProtocolId) {
        return new SimpleLinkSocket();
    }

    // TODO - "notify if none" is not being used
    @Override
    public byte[] receive(Long timeout, boolean notifyIfNone) throws InterruptedException {
        SocketMsg msg = null;
        Long deadline = Timeout.calculateEndTime(timeout);
        while(true) {
            List<PollEvent<Object>> rlist = readPoller.await(deadline, 1);
            if (rlist == null) return null; // if waiting timed out
            PollEvent<Object> linkReady = rlist.getFirst();
            LinkIdentifier linkId = (LinkIdentifier) rlist.getFirst().data;
            if ((linkReady.events & PollFlags.POLLIN) != 0) {
                LinkSocket linkSocket = getLinkSocket(linkId.destId());
                if(linkSocket != null) {
                    msg = linkSocket.receive(0L); // non-blocking receive
                }
            }
            resubscribeReadEvent(linkId);
            if (msg != null) return msg.getPayload();
        }
    }

    @Override
    public boolean send(byte[] payload, Long timeout, boolean notifyIfNone) throws InterruptedException {
        boolean sent = false;
        if(payload == null) throw new IllegalArgumentException("Payload is null.");
        Long deadline = Timeout.calculateEndTime(timeout);
        while(true) {
            List<PollEvent<Object>> wlist = writePoller.await(deadline, 1);
            if (wlist == null) return false; // if waiting timed out
            PollEvent<Object> linkReady = wlist.getFirst();
            LinkIdentifier linkId = (LinkIdentifier) wlist.getFirst().data;
            if ((linkReady.events & PollFlags.POLLOUT) != 0) {
                SimpleLinkSocket linkSocket = (SimpleLinkSocket) getLinkSocket(linkId.destId());
                if(linkSocket != null)
                    sent = linkSocket.trySend(payload); // non-blocking send
            }
            resubscribeWriteEvent(linkId);
            if (sent) return true;
        }
    }

    /**
     * Non-blocking check of available link to receive.
     * @return true if there is a link available for receive.
     */
    private boolean isReadyToReceive() throws InterruptedException {
        while(true) {
            List<PollEvent<Object>> rlist = readPoller.await(0L, 1);
            if (rlist == null) return false; // if waiting timed out
            PollEvent<Object> linkReady = rlist.getFirst();
            LinkIdentifier linkId = (LinkIdentifier) rlist.getFirst().data;
            resubscribeReadEvent(linkId);
            if ((linkReady.events & PollFlags.POLLIN) != 0)
                return true;
        }
    }

    /**
     * Non-blocking check of available link to send.
     * @return true if there is a link available to send.
     */
    private boolean isReadyToSend() throws InterruptedException {
        boolean ready = false;
        while(true) {
            List<PollEvent<Object>> wlist = writePoller.await(0L, 1);
            if (wlist == null) return false; // if waiting timed out
            PollEvent<Object> linkReady = wlist.getFirst();
            LinkIdentifier linkId = (LinkIdentifier) wlist.getFirst().data;
            resubscribeWriteEvent(linkId);
            if ((linkReady.events & PollFlags.POLLOUT) != 0)
                return true;
        }
    }

    @Override
    protected int getAvailableEventsMask() {
        int events = super.getAvailableEventsMask();
        try {
            if(isReadyToReceive())
                events |= PollFlags.POLLIN;
            if(isReadyToSend())
                events |= PollFlags.POLLOUT;
        } catch (InterruptedException ignored) {
            // ignored, because this operation is non-blocking
        }
        return events;
    }

    /**
     * Exposes link socket
     * @param peerId peer's socket identifier
     * @return link socket associated with the given identifier, or "null" if
     * there isn't a link socket associated to that identifier.
     */
    public SimpleLinkSocket linkSocket(SocketIdentifier peerId){
        return (SimpleLinkSocket) getLinkSocket(peerId);
    }
}
