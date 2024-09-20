package pt.uminho.di.a3m.sockets.configurable_socket;

import pt.uminho.di.a3m.core.*;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.core.exceptions.NoLinksException;
import pt.uminho.di.a3m.core.exceptions.SocketClosedException;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.core.options.ImmutableOptionHandler;
import pt.uminho.di.a3m.poller.*;
import pt.uminho.di.a3m.sockets.auxiliary.*;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;
import pt.uminho.di.a3m.waitqueue.WaitQueueFunc;

import java.util.*;
import java.util.function.ToIntFunction;

// TODO - Implement fair queueing? Round-robin is a type of fair queueing but just in terms of
//  the number of messages handled. Ask professor about this.

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
    private Poller readPoller = null;
    private Poller writePoller = null;
    private final boolean order;

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
     * @param order if socket should ensure FIFO order among data messages
     *              and, separately, FIFO order among custom control messages.
     * @throws IllegalArgumentException The socket must have at least the read or write capability,
     * therefore, this exception is thrown if both "reads" and "writes" parameters are false.
     */
    public ConfigurableSocket(SocketIdentifier sid, boolean reads, boolean writes, boolean order) {
        super(sid);
        if(!(reads || writes))
            throw new IllegalArgumentException("Socket must at least be able to read or write.");

        if(reads) readPoller = Poller.create();
        else {
            // if reading is not possible, set capacity to 0 and make it unchangeable
            registerOption("capacity", new ImmutableOptionHandler<>(0));
        }

        if(writes) writePoller = Poller.create();

        this.order = order;
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
        if(readPoller != null) readPoller.close();
        if(writePoller != null) writePoller.close();
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
            LinkSocketWatched lsw = (LinkSocketWatched) pt.getPriv();
            // register wait queue entry used to watch the credits
            lsw.setWatcherWaitEntry(wait);
            // add the wait queue entry to the link socket's wait queue
            wait.add(linkWatcherWakeFunction, lsw);
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
        //remove the watcher and make the pollers remove
        // the link socket from the interest list
        LinkSocketWatched lsw = (LinkSocketWatched) linkSocket;
        WaitQueueEntry wait = lsw.getWatcherWaitEntry();
        if(wait != null) wait.delete();
        lsw.setWatcherWaitEntry(null);
        if(readPoller != null) readPoller.delete(lsw.getId());
        if(writePoller != null) writePoller.delete(lsw.getId());
    }

    @Override
    public void unlink(SocketIdentifier peerId) {
        LinkSocket linkSocket = getLinkSocket(peerId);
        if(linkSocket != null){
            if(writePoller != null) writePoller.delete(linkSocket);
            if(readPoller != null) readPoller.delete(linkSocket);
            super.unlink(peerId);
        }
    }

    /**
     * If a subclass decides to override this method,
     * its implementation must call this method for the
     * data messages with order to be parsed.
     * @param linkSocket link socket associated with the message
     * @param msg incoming message to be handled
     */
    @Override
    protected SocketMsg customOnIncomingMessage(LinkSocket linkSocket, SocketMsg msg) {
        assert msg != null;
        if(msg.getType() == MsgType.DATA)
            return processIncomingDataMessage(linkSocket, msg);
        else
            return processIncomingControlMessage(linkSocket, msg);
    }

    /**
     * Basic handling of incoming data messages. If the socket
     * was set to not have data reading capabilities then incoming
     * data messages are discarded. Else, sends the message to be handled.
     * If the socket has ordering capabilities, before sending the message
     * to be handled by {@link ConfigurableSocket#handleIncomingDataMessage(LinkSocket, SocketMsg)},
     * transforms it into a socket message with order.
     * @param linkSocket link socket
     * @param msg data message to be processed
     * @return null if data messages were handled, or a socket message if
     * the message should be queued.
     */
    private SocketMsg processIncomingDataMessage(LinkSocket linkSocket, SocketMsg msg) {
        if(readPoller == null)
            return null;
        else {
            if (order)
                return SocketMsgWithOrder.parseFrom(msg);
            else
                return handleIncomingDataMessage(linkSocket, msg);
        }
    }

    /**
     * To handle incoming data messages when order is not required.
     * Currently, the handling corresponds to returning the message
     * as is, so that it is queued in the link's queue.
     * @param linkSocket link socket associated with the message
     * @param msg data message to be handled
     * @return msg to be queued in the link's incoming queue or null if the message
     * was handled.
     * @implNote Cannot be used to handle (immediately) messages with order
     * since the order present in the received message is essential for
     * the correct polling behavior of incoming data messages from a link.
     * @implSpec If the message should be discarded or is handled by this method,
     * then the return value should be 'null'. If the message should be
     * queued, then this method may return the message as is or modify it,
     * and then return the modified version to be queued in the link's
     * incoming queue.
     */
    protected SocketMsg handleIncomingDataMessage(LinkSocket linkSocket, SocketMsg msg){
        return msg;
    }

    /**
     * Processes incoming control messages. If order is required, then
     * messages are passed to the link socket so that they can be queued.
     * After queuing, pass all control messages that are allowed to be polled
     * to {@link ConfigurableSocket#handleIncomingControlMessage(SocketMsg)}.
     * If order is not required, the message is simply passed to
     * {@link ConfigurableSocket#handleIncomingControlMessage(SocketMsg)}.
     * @param linkSocket link socket associated with the message
     * @param msg custom control message to be handled
     * @return null. If order is required, messages are queued until all
     * previously required control messages arrive and are handled. If order
     * is not required, return null, so that control messages are simply discarded.
     * For custom handling, {@link ConfigurableSocket#handleIncomingControlMessage(SocketMsg)}
     * is overridable.
     */
    private SocketMsg processIncomingControlMessage(LinkSocket linkSocket, SocketMsg msg) {
        if(order){
            LinkSocketWatchedWithOrder lsWithOrder = (LinkSocketWatchedWithOrder) linkSocket;
            if(lsWithOrder.queueControlMessage(msg))
                while ((msg = lsWithOrder.pollControlMessage()) != null)
                    handleIncomingControlMessage(msg);
            return null;
        }
        else return handleIncomingControlMessage(msg);
    }

    /**
     * Currently, this method returns null as to mark it as handled and discard the message.
     * @param msg custom control message to be handled
     * @return null.
     */
    protected SocketMsg handleIncomingControlMessage(SocketMsg msg){
        //Custom control messages are ignored
        return null;
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
            = msg -> ((SocketMsgWithOrder) msg).getOrder();

    @Override
    protected Queue<SocketMsg> createIncomingQueue(int peerProtocolId) {
        if(order)
            return new OrderedQueue<>(orderExtractor);
        else
            return new LinkedList<>();
    }

    /**
     * @implSpec When order capabilities are not required, the instance created
     * must be a subclass of {@link LinkSocketWatched}. If order is required,
     * then the instance created must be a subclass of {@link LinkSocketWatchedWithOrder}.
     */
    @Override
    protected LinkSocketWatched createLinkSocketInstance(int peerProtocolId) {
        if(order)
            return new LinkSocketWatchedWithOrder();
        else
            return new LinkSocketWatched();
    }

    private LinkIdentifier getLinkReadyToReceive() throws InterruptedException {
        return getLinkReady(readPoller, PollFlags.POLLIN);
    }

    @Override
    protected SocketMsg tryReceiving() throws InterruptedException {
        SocketMsg msg = null;
        LinkIdentifier linkId;
        while (msg == null && (linkId = getLinkReadyToReceive()) != null) {
            LinkSocket linkSocket = getLinkSocket(linkId.destId());
            if (linkSocket != null) {
                try {
                    msg = linkSocket.receive(0L); // non-blocking receive
                    if (msg != null && isReadyToReceive())
                        getWaitQueue().fairWakeUp(0,1,0,PollFlags.POLLIN);
                }catch (LinkClosedException ignored) {}
            }
        }
        return msg;
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
        return super.receive(timeout, notifyIfNone);
    }

    private LinkIdentifier getLinkReadyToSend() throws InterruptedException {
        return getLinkReady(writePoller, PollFlags.POLLOUT);
    }

    @Override
    protected boolean trySending(Payload payload) throws InterruptedException{
        boolean sent = false;
        LinkIdentifier linkId;
        while (!sent && (linkId = getLinkReadyToSend()) != null) {
            LinkSocketWatched linkSocket =
                    (LinkSocketWatched) getLinkSocket(linkId.destId());
            if (linkSocket != null) {
                try {
                    sent = linkSocket.trySend(payload);
                    if(sent && isReadyToSend())
                        getWaitQueue().fairWakeUp(0, 1, 0, PollFlags.POLLOUT);
                }catch (LinkClosedException ignored){}
            }
        }
        return sent;
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
        return super.send(payload,timeout,notifyIfNone);
    }

    /**
     * Non-blocking check of available link to receive.
     * @return true if there is a link available for receive.
     */
    private boolean isReadyToReceive() throws InterruptedException {
        // if read poller is null, then the socket cannot receive messages
        if(readPoller == null) return false;
        return readPoller.hasEventsQuickCheck();
    }

    /**
     * Non-blocking check of available link to send.
     * @return true if there is a link available to send.
     */
    private boolean isReadyToSend() throws InterruptedException {
        // if write poller is null, then the socket cannot send messages
        if(writePoller == null) return false;
        return writePoller.hasEventsQuickCheck();
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
    LinkSocketWatched linkSocket(SocketIdentifier peerId){
        return (LinkSocketWatched) getLinkSocket(peerId);
    }
}
