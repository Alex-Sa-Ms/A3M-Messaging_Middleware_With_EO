package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.core.messaging.payloads.BytePayload;
import pt.uminho.di.a3m.poller.PollEvent;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.poller.Poller;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

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
    final private AtomicInteger next = new AtomicInteger(0);

    protected SimpleSocket(SocketIdentifier sid) {
        super(sid);
    }

    @Override
    protected void init() {
        // empty because it does not require any special initializing procedures
    }

    @Override
    protected void destroy() {
        // empty because it does not require any special closing procedures
    }

    private void resubscribeReadEvent(LinkSocket linkSocket){
        readPoller.modify(linkSocket, PollFlags.POLLIN | PollFlags.POLLONESHOT);
    }

    private void resubscribeWriteEvent(LinkSocket linkSocket){
        writePoller.modify(linkSocket, PollFlags.POLLOUT | PollFlags.POLLONESHOT);
    }

    @Override
    protected void customOnLinkEstablished(LinkSocket linkSocket) {
        // add link socket to pollers
        readPoller.add(linkSocket, PollFlags.POLLIN | PollFlags.POLLONESHOT);
        writePoller.add(linkSocket, PollFlags.POLLOUT | PollFlags.POLLONESHOT);
    }

    @Override
    protected void customOnLinkClosed(LinkSocket linkSocket) {
        // remove link socket from pollers
        readPoller.delete(linkSocket);
        writePoller.delete(linkSocket);
    }

    @Override
    protected boolean customOnIncomingMessage(SocketMsg msg) {
        // notify waiters
        System.out.println(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(msg.getPayload())));
        System.out.flush();
        getWaitQueue().fairWakeUp(0,1,0,PollFlags.POLLIN);
        // Return false because it does not use custom control messages,
        // and all data messages should be queued in the appropriate link's queue.
        return false;
    }

    @Override
    public Protocol getProtocol() {
        return protocol;
    }

    @Override
    public Set<Protocol> getCompatibleProtocols() {
        return compatProtocols;
    }

    private static final ToIntFunction<SocketMsg> orderExtractor
            = msg -> ByteBuffer.wrap(msg.getPayload()).getInt();

    @Override
    protected Supplier<Queue<SocketMsg>> getInQueueSupplier(LinkSocket linkSocket) {
        return () -> new PriorityQueue<>(Comparator.comparingInt(orderExtractor)){
            private int next = 0;

            @Override
            public SocketMsg peek() {
                SocketMsg msg = super.peek();
                // make peek() return null if the first element in the queue is not
                // the next in order.
                if(msg != null && orderExtractor.applyAsInt(msg) != next)
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

    private byte[] removeOrderValueFromPayload(byte[] payload){
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.position(4); // skip int
        byte[] newPayload = new byte[buffer.remaining()];
        buffer.get(newPayload);
        return newPayload;
    }

    @Override
    public byte[] receive(Long timeout, boolean notifyIfNone) throws InterruptedException {
        SocketMsg msg;
        Long deadline = Timeout.calculateEndTime(timeout);
        while(true) {
            List<PollEvent<Object>> rlist = readPoller.await(deadline, 1);
            if (rlist == null) return null; // if waiting timed out
            PollEvent<Object> linkReady = rlist.getFirst();
            LinkIdentifier linkId = (LinkIdentifier) rlist.getFirst().data;
            if ((linkReady.events & PollFlags.POLLIN) != 0) {
                LinkSocket linkSocket = getLinkSocket(linkId.destId());
                if(linkSocket != null) {
                    resubscribeReadEvent(linkSocket);
                    msg = linkSocket.receive(0L); // non-blocking receive
                    if (msg != null)
                        return removeOrderValueFromPayload(msg.getPayload());
                }
            }
        }
    }

    @Override
    public boolean send(byte[] payload, Long timeout, boolean notifyIfNone) throws InterruptedException {
        boolean send;
        if(payload == null) throw new IllegalArgumentException("Payload is null.");
        byte[] payloadWithOrder = ByteBuffer.allocate(payload.length + 4) // allocate space for payload plus order number
                                            .putInt(next.getAndIncrement()) // put order number
                                            .put(payload) // put payload
                                            .array(); // convert to byte array
        Payload p = new BytePayload(MsgType.DATA, payloadWithOrder); // create payload object
        Long deadline = Timeout.calculateEndTime(timeout);
        while(true) {
            List<PollEvent<Object>> wlist = writePoller.await(deadline, 1);
            if (wlist == null) return false; // if waiting timed out
            PollEvent<Object> linkReady = wlist.getFirst();
            LinkIdentifier linkId = (LinkIdentifier) wlist.getFirst().data;
            if ((linkReady.events & PollFlags.POLLOUT) != 0) {
                LinkSocket linkSocket = getLinkSocket(linkId.destId());
                if(linkSocket != null) {
                    resubscribeWriteEvent(linkSocket);
                    send = linkSocket.send(p, 0L); // non-blocking send
                    if (send) return true;
                }
            }
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
            if ((linkReady.events & PollFlags.POLLIN) != 0) {
                LinkSocket linkSocket = getLinkSocket(linkId.destId());
                if(linkSocket != null) {
                    resubscribeReadEvent(linkSocket);
                    return true;
                }
            }
        }
    }

    /**
     * Non-blocking check of available link to send.
     * @return true if there is a link available to send.
     */
    private boolean isReadyToSend() throws InterruptedException {
        while(true) {
            List<PollEvent<Object>> wlist = writePoller.await(0L, 1);
            if (wlist == null) return false; // if waiting timed out
            PollEvent<Object> linkReady = wlist.getFirst();
            LinkIdentifier linkId = (LinkIdentifier) wlist.getFirst().data;
            if ((linkReady.events & PollFlags.POLLOUT) != 0) {
                LinkSocket linkSocket = getLinkSocket(linkId.destId());
                if(linkSocket != null) {
                    resubscribeWriteEvent(linkSocket);
                    return true;
                }
            }
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
    public LinkSocket linkSocket(SocketIdentifier peerId){
        return getLinkSocket(peerId);
    }
}
