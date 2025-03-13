package pt.uminho.di.a3m.sockets.request_reply;

import pt.uminho.di.a3m.core.*;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.poller.*;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.auxiliary.LinkSocketWatched;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;
import pt.uminho.di.a3m.waitqueue.WaitQueueFunc;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Request Socket:
 * <p>- Sends a request and expects a response (or an unlink from the replier)
 * before a new request can be issued.</p>
 * <p>- Requests are sent in a round-robin fashion. </p>
 * <p>- Any thread can get the reply to the request. However, if multiple threads
 * are to use this socket, by waiting on send() and only invoking receive() after
 * successfully sending a request, threads are guaranteed to receive the answer
 * to their request.</p>
 */
public class ReqSocket extends Socket {
    final static public Protocol protocol = SocketsTable.REQ_PROTOCOL;
    final static public Set<Protocol> compatProtocols = Set.of(SocketsTable.REP_PROTOCOL, SocketsTable.ROUTER_PROTOCOL);
    // write poller to keep track of which links allow a message to be sent
    private final Poller writePoller = Poller.create();
    // when a message is sent, the identifier of the socket that should return the message is set here.
    private final AtomicReference<SocketIdentifier> replier = new AtomicReference<>(null);
    // reply message
    private final AtomicReference<SocketMsg> reply = new AtomicReference<>(null);
    private final AtomicBoolean replierClosed = new AtomicBoolean(false);

    /**
     * Create a requester socket instance.
     * @param sid socket identifier
     */
    public ReqSocket(SocketIdentifier sid) {
        super(sid);
    }

    @Override
    protected void init() {
        // empty because it does not require any special initializing procedures
    }

    @Override
    protected void destroy() {
        writePoller.close();
    }

    /**
     * Notifies POLLOUT if the events contain POLLOUT and
     * if there isn't a request waiting for a reply.
     */
    private void checkAndNotifyPOLLOUT(int events) {
        if (replier.get() == null && (events & PollFlags.POLLOUT) != 0)
            getWaitQueue().fairWakeUp(0, 1, 0, PollFlags.POLLOUT);
    }

    /** Wake function that notifies when an event happens to the link socket. */
    private final WaitQueueFunc linkWatcherWakeFunction = (entry, mode, flags, key) -> {
        int iKey = (int) key;
        checkAndNotifyPOLLOUT(iKey);
        return 1;
    };

    /** Function that queues a link event watcher. */
    private final PollQueueingFunc linkWatcherQueueFunction = (p, wait, pt) -> {
        if(wait != null){
            LinkSocketWatched rls = (LinkSocketWatched) pt.getPriv();
            // register wait queue entry used to watch the credits
            rls.setWatcherWaitEntry(wait);
            // add the wait queue entry to the link socket's wait queue
            wait.add(linkWatcherWakeFunction, rls);
        }
    };

    @Override
    protected void customOnLinkEstablished(LinkSocket linkSocket) {
        // register a watcher of the link
        int events = linkSocket.poll(
                new PollEntry(
                        PollFlags.POLLOUT,
                        linkSocket,
                        linkWatcherQueueFunction));
        writePoller.add(linkSocket, PollFlags.POLLOUT);
        checkAndNotifyPOLLOUT(events);
    }

    /**
     * Notifies a reader of the expected replier having been closed
     * if the socket identifier provided as parameter matches.
     * @param sid socket identifier
     */
    private void handleClosedReplier(SocketIdentifier sid){
        getLock().readLock().lock();
        try {
            if(reply.get() == null && Objects.equals(replier.get(),sid)) {
                replierClosed.set(true);
                getWaitQueue().wakeUp(0, 0, 0, PollFlags.POLLIN);
            }
        } finally {
            getLock().readLock().unlock();
        }
    }

    @Override
    protected void customOnLinkClosed(LinkSocket linkSocket) {
        LinkSocketWatched rls = (LinkSocketWatched) linkSocket;
        WaitQueueEntry wait = rls.getWatcherWaitEntry();
        if(wait != null) wait.delete();
        rls.setWatcherWaitEntry(null);
        writePoller.delete(rls.getId());
        handleClosedReplier(linkSocket.getPeerId());
    }

    @Override
    protected SocketMsg customOnIncomingMessage(LinkSocket linkSocket, SocketMsg msg) {
        if(msg != null && msg.getType() == MsgType.DATA){
            getLock().readLock().lock();
            boolean notify;
            try {
                // if the sender of the data message corresponds to the
                // expected replier, then set it to be received.
                notify = Objects.equals(replier.get(), msg.getSrcId())
                        && reply.compareAndSet(null, msg);
            } finally {
                getLock().readLock().unlock();
            }
            // signal ALL waiters waiting to receive a message,
            // since waiting for a message should only be allowed
            // if there is a message to wait for.
            if(notify) getWaitQueue().wakeUp(0, 0, 0, PollFlags.POLLIN);
        }
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

    /**
     * Closes link with peer and removes link socket from write poller
     * to ensure messages are not sent when the link is unlinking.
     * @param peerId peer identifier
     */
    private void _unlink(SocketIdentifier peerId){
        LinkSocket linkSocket = getLinkSocket(peerId);
        if(linkSocket != null){
            writePoller.delete(linkSocket);
            super.unlink(peerId);
        }
    }

    /**
     * Closes link with peer if a message is not expected from it.
     * @param peerId peer's socket identifier
     * @throws IllegalStateException if trying to unlink when a reply has not
     * yet been received from the socket in question.
     */
    @Override
    public void unlink(SocketIdentifier peerId) {
        getLock().writeLock().lock();
        try {
            if(peerId != null && peerId.equals(replier.get()))
                throw new IllegalStateException("Answer from the replier has not yet been received. " +
                        "If unlinking is still desirable, use forceUnlink().");
            _unlink(peerId);
        } finally {
            getLock().writeLock().unlock();
        }
    }

    /**
     * Force link to be closed, regardless of whether a reply
     * is expected to be received from the peer or not.
     * @param peerId peer's socket identifier
     */
    public void forceUnlink(SocketIdentifier peerId) {
        getLock().writeLock().lock();
        try {
            if(peerId != null && peerId.equals(replier.get()))
                replier.set(null);
            _unlink(peerId);
        } finally {
            getLock().writeLock().unlock();
        }
    }

    @Override
    protected Queue<SocketMsg> createIncomingQueue(int peerProtocolId) {
        return new LinkedList<>();
    }

    @Override
    protected LinkSocketWatched createLinkSocketInstance(int peerProtocolId) {
        return new LinkSocketWatched();
    }

    /**
     * @return reply if it has already been received.
     * @throws IllegalStateException if the thread is attempting to receive
     * when the socket is expecting a request to be sent and not a reply to be received.
     * @throws LinkClosedException if the link with the replier was closed before the message was received.
     */
    @Override
    protected SocketMsg tryReceiving() throws InterruptedException {
        SocketMsg msg;
        getLock().writeLock().lock();
        try {
            // checks if socket is in a waiting for reply state
            if(replier.get() == null)
                throw new IllegalStateException("To wait for a reply, a request must be sent first.");
            // if the link with the replier has been closed
            // before the reply has arrived, clean up to allow
            // another request to be sent and then throw link closed exception
            if(replierClosed.getAndSet(false)){
                SocketIdentifier tmpReplier = replier.get();
                replier.set(null);
                throw new LinkClosedException("Link with replier(" + tmpReplier + ") was closed before the message was received.");
            }
            msg = reply.getAndSet(null);
            if(msg != null)
                replier.set(null);
        } finally {
            getLock().writeLock().unlock();
        }
        // notify that sending is possible
        if(msg != null)
            getWaitQueue().fairWakeUp(0,1,0,PollFlags.POLLOUT);
        return msg;
    }

    private LinkIdentifier getLinkReadyToSend() throws InterruptedException {
        return getLinkReady(writePoller, PollFlags.POLLOUT);
    }

    @Override
    protected boolean trySending(Payload payload) throws InterruptedException {
        assert payload != null && payload.getType() == MsgType.DATA;
        getLock().writeLock().lock();
        try {
            boolean sent = false;
            LinkIdentifier linkId;
            if(replier.get() == null) {
                while (!sent && (linkId = getLinkReadyToSend()) != null) {
                    LinkSocket linkSocket = getLinkSocket(linkId.destId());
                    if (linkSocket != null) {
                        try {
                            sent = linkSocket.send(payload, 0L);
                            if (sent) replier.set(linkSocket.getPeerId());
                        } catch (LinkClosedException ignored) {}
                    }
                }
            }
            return sent;
        } finally {
            getLock().writeLock().unlock();
        }
    }

    /**
     * @return true if there is a reply ready to be received.
     */
    private boolean isReadyToReceive() throws InterruptedException {
        return reply.get() != null;
    }

    /**
     * @return true if a request has not been sent and there is a link available to send.
     */
    private boolean isReadyToSend() throws InterruptedException {
        return replier.get() == null && writePoller.hasEventsQuickCheck();
    }

    @Override
    protected int getAvailableEventsMask() {
        int events = super.getAvailableEventsMask();
        try {
            getLock().readLock().lock();
            try {
                if(isReadyToReceive())
                    events |= PollFlags.POLLIN;
                if(isReadyToSend())
                    events |= PollFlags.POLLOUT;
            } finally {
                getLock().readLock().unlock();
            }
        } catch (InterruptedException ignored) {
            // ignored, because this operation is non-blocking
        }
        return events;
    }

    LinkSocket linkSocket(SocketIdentifier sid){
        return getLinkSocket(sid);
    }
    
    /**
     * Creates ReqSocket.
     * @param middleware middleware instance
     * @param tagId tag identifier of the socket
     * @return ReqSocket instance
     * @implNote Assumes the middleware to have the ReqSocket producer registered.
     */
    public static ReqSocket createSocket(A3MMiddleware middleware, String tagId){
        if(middleware == null)
            throw new IllegalArgumentException("Middleware is null.");
        return middleware.createSocket(tagId, protocol.id(), ReqSocket.class);
    }

    /**
     * Creates and starts a ReqSocket.
     * @param middleware middleware instance
     * @param tagId tag identifier of the socket
     * @return ReqSocket instance
     * @implNote Assumes the middleware to have the ReqSocket producer registered.
     */
    public static ReqSocket startSocket(A3MMiddleware middleware, String tagId){
        if(middleware == null)
            throw new IllegalArgumentException("Middleware is null.");
        return middleware.startSocket(tagId, protocol.id(), ReqSocket.class);
    }
}
