package pt.uminho.di.a3m.sockets.configurable_socket;

import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.*;
import pt.uminho.di.a3m.core.exceptions.NoLinksException;
import pt.uminho.di.a3m.core.exceptions.SocketClosedException;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.core.messaging.payloads.BytePayload;
import pt.uminho.di.a3m.core.options.ImmutableOptionHandler;
import pt.uminho.di.a3m.poller.*;
import pt.uminho.di.a3m.waitqueue.ParkState;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;
import pt.uminho.di.a3m.waitqueue.WaitQueueFunc;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Configurable socket:
 * <ul>
 *     <li>Enables FIFO ordering of data messages.</li>
 *     <li>Allows disabling sending/receiving of messages.</li>
 *     <li>Sends/Receives data messages using round-robin, but skipping
 *     destinations that are not available for the operations.</li>
 * </ul>
 */
public class ConfigurableSocket extends Socket {
    final static private String protocolName = "ConfigurableSocket";
    final static public Protocol protocol = new Protocol(protocolName.hashCode(), protocolName);
    final static public Set<Protocol> compatProtocols = Collections.singleton(protocol);
    private static final int CHECK_IF_NONE = 1; // wake up mode to inform when there aren't any links
    private Poller readPoller = null;
    private Poller writePoller = null;
    private final boolean orderData;

    /**
     * Creates a socket with a combination of the following capabilities:
     *  <p>- Be able to receive data messages (reads == true);
     *  <p>- Be able to send data messages (writes == true);
     *  <p>- Data messages must be ordered (orderData == true).
     *  (This assumes the compatible sockets send/receive with order)
     * <p>The socket must be able to read and/or write. Choosing not to have both
     * (i.e. setting reads and writes to false) will result in an exception.</p>
     * @param sid identifier of the socket
     * @param reads if socket should be able to read
     * @param writes if socket should be able to write
     * @param orderData if socket should ensure FIFO order of data messages.
     * @throws IllegalArgumentException The socket must have at least the read or write capability,
     * therefore, this exception is thrown if both "reads" and "writes" parameters are false.
     */
    public ConfigurableSocket(SocketIdentifier sid, boolean reads, boolean writes, boolean orderData) {
        super(sid);
        if(!(reads || writes))
            throw new IllegalArgumentException("Socket must at least be able to read or write.");

        if(reads) readPoller = Poller.create();
        else {
            // if reading is not possible, set capacity to 0 and make it unchangeable
            registerOption("capacity", new ImmutableOptionHandler<>(0));
        }

        if(writes) writePoller = Poller.create();

        this.orderData = orderData;
    }

    /**
     * Creates a socket with read, write and FIFO ordering capabilities.
     * @param sid identifier of the socket
     */
    public ConfigurableSocket(SocketIdentifier sid) {
        this(sid, true, true, true);
    }

    @Override
    protected void init() {
        // empty because it does not require any special initializing procedures
    }

    @Override
    protected void destroy() {
        // empty because it does not require any special closing procedures
    }

    /**
     * Checks if the key contains any event of interest. If it does contain, then notify
     * the events present in the intersection of the key with the events of interest mask
     * @param key events available
     * @param events events of interest
     */
    private void checkEventsAndNotifyWaiters(int key, int events){
        int i = key & events;
        if(i != 0) getWaitQueue().fairWakeUp(0, 1, 0, i);
    }

    /** Wake function that notifies when an event happens to the link socket. */
    private final WaitQueueFunc linkWatcherWakeFunction = (entry, mode, flags, key) -> {
        int iKey = (int) key;
        // if POLLHUP is received, remove the watcher and
        // make the pollers remove the link socket from the interest list
        if((iKey & PollFlags.POLLHUP) != 0) {
            entry.delete();
            SimpleLinkSocket sls = ((SimpleLinkSocket) entry.getPriv());
            sls.setWatcherWaitEntry(null);
            if(readPoller != null) readPoller.delete(sls.getId());
            if(writePoller != null) writePoller.delete(sls.getId());
        }
        // if POLLIN is received, notify a socket waiter with POLLIN
        if(readPoller != null)
            checkEventsAndNotifyWaiters(iKey, PollFlags.POLLIN);
        // if POLLOUT is received, notify a socket waiter with POLLOUT
        if(writePoller != null)
            checkEventsAndNotifyWaiters(iKey, PollFlags.POLLOUT);
        return 1;
    };

    /** Function that queues a link event watcher. */
    private final PollQueueingFunc linkWatcherQueueFunction = (p, wait, pt) -> {
        if(wait != null){
            SimpleLinkSocket sls = (SimpleLinkSocket) pt.getPriv();
            // register wait queue entry used to watch the credits
            sls.setWatcherWaitEntry(wait);
            // add the wait queue entry to the link socket's wait queue
            wait.add(linkWatcherWakeFunction, sls);
        }
    };

    //private void resubscribeReadEvent(LinkIdentifier linkId){
    //    readPoller.modify(linkId, PollFlags.POLLIN | PollFlags.POLLET | PollFlags.POLLONESHOT);
    //}

    //private void resubscribeWriteEvent(LinkIdentifier linkId){
    //    writePoller.modify(linkId, PollFlags.POLLOUT | PollFlags.POLLET | PollFlags.POLLONESHOT);
    //}

    /**
     * If a subclass decides to override this method,
     * its implementation must call this method.
     * @param linkSocket link socket of the newly established link
     */
    @Override
    protected void customOnLinkEstablished(LinkSocket linkSocket) {
        // register a watcher of the link
        int events = linkSocket.poll(
                new PollTable(
                        PollFlags.POLLALL,
                        linkSocket,
                        linkWatcherQueueFunction));
        // add link socket to pollers (added after link watcher to ensure they are notified before
        // the link watcher, since the link watcher assumes the pollers to be notified first)
        if(readPoller != null){
            readPoller.add(linkSocket, PollFlags.POLLIN /*| PollFlags.POLLET | PollFlags.POLLONESHOT*/);
            // notify if receiving is immediately possible
            checkEventsAndNotifyWaiters(events, PollFlags.POLLIN);
        }
        if(writePoller != null) {
            writePoller.add(linkSocket, PollFlags.POLLOUT /*| PollFlags.POLLET | PollFlags.POLLONESHOT*/);
            // notify if sending is immediately possible
            checkEventsAndNotifyWaiters(events, PollFlags.POLLOUT);
        }
    }

    /**
     * If a subclass decides to override this method,
     * its implementation must call this method.
     * @param linkSocket link socket of the closed link
     */
    @Override
    protected void customOnLinkClosed(LinkSocket linkSocket) {
        // Notify with CHECK_IF_NONE mode, so that waiters that
        // require waking up when there aren't any links can do so.
        if(countLinks() == 0)
            getWaitQueue().wakeUp(CHECK_IF_NONE, 0, 0, 0);
    }

    /**
     * If a subclass decides to override this method,
     * its implementation must call this method for the
     * data messages with order to be parsed.
     * @param msg incoming message to be handled
     */
    @Override
    protected SocketMsg customOnIncomingMessage(SocketMsg msg) {
        // Custom control messages are ignored
        if(msg == null || msg.getType() != MsgType.DATA) return null;
        // All data messages should be queued in the appropriate link's queue.
        else {
            if(readPoller == null)
                return null;
            else {
                if (orderData)
                    return SocketMsgWithOrder.parseFrom(msg);
                else
                    return msg;
            }
        }
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

    private Queue<SocketMsg> createIncomingQueueWithOrder(){
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

    @Override
    protected Queue<SocketMsg> createIncomingQueue(int peerProtocolId) {
        if(orderData)
            return createIncomingQueueWithOrder();
        else
            return new LinkedList<>();
    }

    protected static class SimpleLinkSocket extends LinkSocket{
        private WaitQueueEntry watcherWaitEntry = null;

        WaitQueueEntry getWatcherWaitEntry() {
            return watcherWaitEntry;
        }

        void setWatcherWaitEntry(WaitQueueEntry watcherWaitEntry) {
            this.watcherWaitEntry = watcherWaitEntry;
        }

        public boolean trySend(Payload payload) throws InterruptedException {
            return send(payload, 0L);
        }
    }

    protected static class SimpleLinkSocketWithOrder extends SimpleLinkSocket{
        int next = 0;
        Lock orderLock = new ReentrantLock();

        protected BytePayload createDataPayloadWithOrder(byte[] payload){
            byte[] payloadWithOrder =
                    ByteBuffer.allocate(payload.length + 4) // allocate space for payload plus order number
                              .putInt(next) // put order number
                              .put(payload) // put payload
                              .array(); // convert to byte array
            return new BytePayload(MsgType.DATA, payloadWithOrder);
        }

        public boolean trySendDataWithOrder(byte[] payload) throws InterruptedException {
            boolean locked = orderLock.tryLock();
            if(locked) {
                try {
                    boolean sent = super.send(createDataPayloadWithOrder(payload), 0L);
                    if (sent) next++;
                    return sent;
                } finally {
                    orderLock.unlock();
                }
            }else return false;
        }

        @Override
        public boolean trySend(Payload payload) throws InterruptedException {
            if(payload == null)
                throw new IllegalArgumentException("Payload is null.");
            if(payload.getType() == MsgType.DATA)
                return trySendDataWithOrder(payload.getPayload());
            else
                return super.trySend(payload);
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
         * @param payload message content to send
         * @param deadline .
         * @return true if the message was sent. false, if the operation timed out.
         * @throws InterruptedException if the thread was interrupted while waiting.
         */
        public boolean sendDataWithOrder(byte[] payload, Long deadline) throws InterruptedException {
            boolean locked = false;
            try {
                if (deadline == null) {
                    orderLock.lock();
                    locked = true;
                } else
                    locked = orderLock.tryLock(Timeout.calculateTimeout(deadline), TimeUnit.MILLISECONDS);

                if (locked) {
                    boolean sent = super.send(createDataPayloadWithOrder(payload), deadline);
                    if (sent) next++;
                    return sent;
                } else return false;
            }finally {
                if(locked) orderLock.unlock();
            }
        }

        @Override
        public boolean send(Payload payload, Long deadline) throws InterruptedException {
            if(payload == null)
                throw new IllegalArgumentException("Payload is null.");
            if(payload.getType() == MsgType.DATA)
                return sendDataWithOrder(payload.getPayload(), deadline);
            else
                return super.send(payload, deadline);
        }
    }

    @Override
    protected SimpleLinkSocket createLinkSocketInstance(int peerProtocolId) {
        if(orderData)
            return new SimpleLinkSocketWithOrder();
        else
            return new SimpleLinkSocket();
    }

    private static class ParkStateSimpleSocket extends ParkState {
        private final int events; // events of interest to the waiter
        private final boolean notifyIfNone;
        private WaitQueueEntry wait = null;

        public ParkStateSimpleSocket(boolean parkState, boolean notifyIfNone) {
            super(parkState);
            this.notifyIfNone = notifyIfNone;
            this.events = 0;
        }

        public ParkStateSimpleSocket(boolean parkState, int events, boolean notifyIfNone) {
            super(parkState);
            this.events = events;
            this.notifyIfNone = notifyIfNone;
        }

        public int getEvents() {
            return events;
        }

        public boolean isNotifyIfNone() {
            return notifyIfNone;
        }

        public WaitQueueEntry getWaitQueueEntry() {
            return wait;
        }

        public void setWaitQueueEntry(WaitQueueEntry wait) {
            this.wait = wait;
        }
    }

    private static final WaitQueueFunc directWaiterWakeFunc = (wait, mode, flags, key) -> {
        int iKey = (int) key;
        ParkStateSimpleSocket ps = (ParkStateSimpleSocket) wait.getPriv();
        if((iKey & ps.getEvents()) != 0 ||
                (mode == CHECK_IF_NONE && ps.isNotifyIfNone())) {
            return wait.parkStateWakeUp(ps);
        }
        return 0;
    };

    private static final PollQueueingFunc directWaiterQueuingFunc = (p, wait, pt) -> {
        if(wait != null){
            wait.addExclusive(directWaiterWakeFunc, pt.getPriv());
            ((ParkStateSimpleSocket) pt.getPriv()).setWaitQueueEntry(wait);
        }
    };

    /**
     * Used to get the socket identifier associated with a link that is
     * ready for a certain event.
     * <p></p>
     * @param poller poller from which an event should be polled. Currently, used only
     *               for readPoller and writePoller.
     * @param event event of interest. Currently, used only for POLLIN or POLLOUT.
     * @return identifier of the link that is ready for the requested event
     * @implNote Assumes that the caller of this function will resubscribe the events
     * after being done with the link.
     */
    private LinkIdentifier getLinkReady(Poller poller, int event) throws InterruptedException {
        List<PollEvent<Object>> rlist = poller.await(1, 0L);
        LinkIdentifier linkId = null;
        if (rlist != null){
            PollEvent<Object> linkReady = rlist.getFirst();
            if((linkReady.events & event) != 0)
                linkId = (LinkIdentifier) linkReady.data;
        }
        return linkId;
    }

    private LinkIdentifier getLinkReadyToReceive() throws InterruptedException {
        return getLinkReady(readPoller, PollFlags.POLLIN);
    }

    private byte[] tryReceiving() throws InterruptedException {
        LinkIdentifier linkId = getLinkReadyToReceive();
        if (linkId != null) {
            LinkSocket linkSocket = getLinkSocket(linkId.destId());
            if (linkSocket != null) {
                try {
                    SocketMsg msg = linkSocket.receive(0L); // non-blocking receive
                    if (msg != null)
                        return msg.getPayload();
                }finally {
                    //resubscribeReadEvent(linkId);
                    if(isReadyToReceive())
                        getWaitQueue().fairWakeUp(0,1,0,PollFlags.POLLIN);
                }
            }
        }
        return null;
    }

    /**
     * Waits for a message to be received.
     * @param timeout maximum time allowed for a message to be received. After such time
     *                is elaped, the method must return null.
     * @param notifyIfNone "true" when a NoLinksException is desirable to
     *                     stop the waiting operation when there aren't any links.
     * @return null if operation timed out. Non-null value if a message was received successfully.
     * @throws InterruptedException .
     * @throws SocketClosedException if socket is closed
     * @throws NoLinksException if the "notify if none" flag is set
     * and there aren't any links regardless of the state being established,
     * linking, etc.
     */
    @Override
    public byte[] receive(Long timeout, boolean notifyIfNone) throws InterruptedException {
        // if read poller is null, then the socket cannot receive messages
        if(readPoller == null)
            throw new UnsupportedOperationException("Socket does not allow receiving data messages.");

        byte[] payload;
        Long deadline = Timeout.calculateEndTime(timeout);
        ParkStateSimpleSocket ps = null;
        try {
            getLock().readLock().lock();

            if(getState() == SocketState.CLOSED)
                throw new SocketClosedException();

            // check if there are any links when the notifyIfNone flag is used
            if(notifyIfNone && countLinks() == 0)
                throw new NoLinksException();

            // queue the thread if the operation is blocking
            if(!Timeout.hasTimedOut(deadline)) {
                // queue the thread
                ps = new ParkStateSimpleSocket(
                        true,
                        PollFlags.POLLIN | PollFlags.POLLERR | PollFlags.POLLHUP,
                        notifyIfNone);
                PollTable pt = new PollTable(PollFlags.POLLIN, ps, directWaiterQueuingFunc);
                poll(pt);
            }
        } finally {
            getLock().readLock().unlock();
        }

        // if operation is non-blocking, just attempt to receive
        // and return the result.
        if(ps == null) return tryReceiving();

        try {
            while(true) {
                if(getState() == SocketState.CLOSED)
                    throw new SocketClosedException();

                payload = tryReceiving();
                if (payload != null)
                    return payload;

                boolean wokenUp = WaitQueueEntry.defaultWaitUntilFunction(
                        ps.getWaitQueueEntry(), ps, deadline, true);

                if(wokenUp) {
                    // if the "notify if none" flag is set,
                    // and there aren't any links, notify with a
                    // NoLinksException
                    if (notifyIfNone && countLinks() == 0)
                        throw new NoLinksException();
                }

                ps.setParkState(true);

                if(!wokenUp && Timeout.hasTimedOut(deadline))
                    return null;
            }
        } finally {
            ps.getWaitQueueEntry().delete();
        }
    }

    private LinkIdentifier getLinkReadyToSend() throws InterruptedException {
        return getLinkReady(writePoller, PollFlags.POLLOUT);
    }

    private boolean trySendingDataMsg(byte[] payload) throws InterruptedException {
        LinkIdentifier linkId = getLinkReadyToSend();
        if (linkId != null) {
            SimpleLinkSocket linkSocket =
                    (SimpleLinkSocket) getLinkSocket(linkId.destId());
            if (linkSocket != null) {
                try { return linkSocket.trySend(new BytePayload(MsgType.DATA, payload)); }
                finally {
                    //resubscribeWriteEvent(linkId);
                    if(isReadyToSend())
                        getWaitQueue().fairWakeUp(0,1,0,PollFlags.POLLOUT);
                }
            }
        }
        return false;
    }

    /**
     * Waits to send a message.
     * @param timeout maximum time allowed for a message to be sent. After such time
     *                is elaped, the method must return false.
     * @param notifyIfNone "true" when a NoLinksException is desirable to
     *                     stop the waiting operation when there aren't any links.
     * @return false if operation timed out. True if a message was sent successfully.
     * @throws InterruptedException .
     * @throws SocketClosedException if socket is closed
     * @throws NoLinksException if the "notify if none" flag is set
     * and there aren't any links regardless of the state being established,
     * linking, etc.
     */
    @Override
    public boolean send(byte[] payload, Long timeout, boolean notifyIfNone) throws InterruptedException {
        // if write poller is null, then the socket cannot send messages
        if(writePoller == null)
            throw new UnsupportedOperationException("Socket does not allow sending data messages.");

        boolean sent = false;
        Long deadline = Timeout.calculateEndTime(timeout);
        ParkStateSimpleSocket ps = null;
        try {
            getLock().readLock().lock();
            if (getState() == SocketState.CLOSED)
                throw new SocketClosedException();

            // check if there are any links when the notifyIfNone flag is used
            if(notifyIfNone && countLinks() == 0)
                throw new NoLinksException();

            // queue the thread if the operation is blocking
            if(!Timeout.hasTimedOut(deadline)) {
                ps = new ParkStateSimpleSocket(
                        true,
                        PollFlags.POLLOUT | PollFlags.POLLERR | PollFlags.POLLHUP,
                        notifyIfNone);
                PollTable pt = new PollTable(PollFlags.POLLOUT, ps, directWaiterQueuingFunc);
                poll(pt);
            }
        } finally {
            getLock().readLock().unlock();
        }

        // if operation is non-blocking, just attempt to send
        // and return the result.
        if(ps == null) return trySendingDataMsg(payload);

        try {
            while (true) {
                if(getState() == SocketState.CLOSED)
                    throw new SocketClosedException();

                // checks if thread was interrupted
                if(Thread.currentThread().isInterrupted())
                    throw new InterruptedException();

                sent = trySendingDataMsg(payload);
                if (sent) return true;

                boolean wokenUp = WaitQueueEntry.defaultWaitUntilFunction(
                        ps.getWaitQueueEntry(), ps, deadline, true);
                if (wokenUp) {
                    // if the "notify if none" flag is set,
                    // and there aren't any links, notify with a
                    // NoLinksException
                    if (notifyIfNone && countLinks() == 0)
                        throw new NoLinksException();
                }

                ps.setParkState(true);

                if (!wokenUp && Timeout.hasTimedOut(deadline))
                    return false;
            }
        } finally {
            ps.getWaitQueueEntry().delete();
        }
    }

    /**
     * Non-blocking check of available link to receive.
     * @return true if there is a link available for receive.
     */
    private boolean isReadyToReceive() throws InterruptedException {
        // if read poller is null, then the socket cannot receive messages
        if(readPoller == null) return false;

        while(true) {
            List<PollEvent<Object>> rlist = readPoller.awaitUntil(1, 0L);
            if (rlist == null) return false; // if waiting timed out
            PollEvent<Object> linkReady = rlist.getFirst();
            //LinkIdentifier linkId = (LinkIdentifier) rlist.getFirst().data;
            //resubscribeReadEvent(linkId);
            if ((linkReady.events & PollFlags.POLLIN) != 0)
                return true;
        }
    }

    /**
     * Non-blocking check of available link to send.
     * @return true if there is a link available to send.
     */
    private boolean isReadyToSend() throws InterruptedException {
        // if write poller is null, then the socket cannot send messages
        if(writePoller == null) return false;

        while(true) {
            List<PollEvent<Object>> wlist = writePoller.awaitUntil(1, 0L);
            if (wlist == null) return false; // if waiting timed out
            PollEvent<Object> linkReady = wlist.getFirst();
            //LinkIdentifier linkId = (LinkIdentifier) wlist.getFirst().data;
            //resubscribeWriteEvent(linkId);
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
    SimpleLinkSocket linkSocket(SocketIdentifier peerId){
        return (SimpleLinkSocket) getLinkSocket(peerId);
    }
}
