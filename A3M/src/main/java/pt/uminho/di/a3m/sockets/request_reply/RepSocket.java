package pt.uminho.di.a3m.sockets.request_reply;

import pt.uminho.di.a3m.core.*;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.poller.PollQueueingFunc;
import pt.uminho.di.a3m.poller.PollTable;
import pt.uminho.di.a3m.poller.Poller;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.auxiliary.LinkSocketWatched;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;
import pt.uminho.di.a3m.waitqueue.WaitQueueFunc;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class RepSocket extends Socket {
    public static final Protocol protocol = SocketsTable.REP_PROTOCOL;
    public static final Set<Protocol> compatProtocols = Set.of(SocketsTable.REQ_PROTOCOL, SocketsTable.DEALER_PROTOCOL);
    // read poller to keep track of which links allow a message to be received
    private final Poller readPoller = Poller.create();
    private final AtomicReference<LinkSocket> requester = new AtomicReference<>(null);

    /**
     * Create a replier socket instance.
     * @param sid socket identifier
     */
    public RepSocket(SocketIdentifier sid) {
        super(sid);
    }

    @Override
    protected void init() {
        // empty because it does not require any special initializing procedures
    }

    @Override
    protected void destroy() {
        readPoller.close();
    }

    /** Wake function that notifies when reading is possible. */
    private final WaitQueueFunc linkWatcherWakeFunction = (entry, mode, flags, key) -> {
        int iKey = (int) key;
        LinkSocket requester = this.requester.get();
        if(requester == null){
            if((iKey & PollFlags.POLLIN) != 0)
                getWaitQueue().fairWakeUp(0,1,0,PollFlags.POLLIN);
        }
        /*else {
            // If there aren't credits to send a reply to the requester,
            // the replier needs to be woken up when a credits that allows
            // the operation to be completed arrives. However, having a credit
            // limitation means slowing down both the replier and the requester.


            // TODO - possibly have the link socket have a queue with replies
                that need to be sent, that way when credits arrive, there is no need
                to signal a waiter, the messages that can be forwarded, are forwarded
                by doing a non-blocking send on a loop.
                Solution:
                    1. Set up a decent capacity at the REQ socket for efficiency reasons,
                        that way queuing of messages can be minimized.
                    2. Create a link socket watched subclass to talk with REQ sockets. This
                    class should have an attribute that is a message that needs to be dispatched.
                    3. Make the link socket be queued with POLLIN and POLLOUT.
                    4. When an attempt to send fails, the message is set on the link socket so that
                    when the link watcher gets a POLLOUT notification it can check for the existence of
                    messages to send. (proper synchronization is required so that a POLLOUT notification
                    is not missed and thus leaving the message(s) queued)
                    5. DEALER should have a link socket watched subclass too, however, it requires
                    a queue instead of a single reference since many requests may have replies that
                    cannot be send due to lack of credits.

            // if a flow control credit has arrived from the requester,
            // wake up a sending waiter since sending is now possible.
            if(entry.getPriv() == requester && (iKey & PollFlags.POLLOUT) != 0)
                getWaitQueue().fairWakeUp(0,1,0,PollFlags.POLLOUT);
        } */
        return 1;
    };

    /** Function that queues a link event watcher. */
    private final PollQueueingFunc linkWatcherQueueFunction = (p, wait, pt) -> {
        if(wait != null){
            LinkSocketWatched rls = (LinkSocketWatched) pt.getPriv();
            rls.setWatcherWaitEntry(wait);
            wait.add(linkWatcherWakeFunction, rls);
        }
    };

    @Override
    protected void customOnLinkEstablished(LinkSocket linkSocket) {
        int events = linkSocket.poll(
                new PollTable(
                        PollFlags.POLLIN /* | PollFlags.POLLOUT*/, // only required if the link watcher decides to notify POLLOUT events.
                        linkSocket,
                        linkWatcherQueueFunction));
        readPoller.add(linkSocket, PollFlags.POLLIN);
        if(requester.get() == null && (events & PollFlags.POLLIN) != 0)
            getWaitQueue().fairWakeUp(0, 1, 0, PollFlags.POLLIN);
    }

    @Override
    protected void customOnLinkClosed(LinkSocket linkSocket) {
        LinkSocketWatched rls = (LinkSocketWatched) linkSocket;
        WaitQueueEntry wait = rls.getWatcherWaitEntry();
        if(wait != null) wait.delete();
        rls.setWatcherWaitEntry(null);
        readPoller.delete(linkSocket);
    }

    @Override
    protected SocketMsg customOnIncomingMessage(SocketMsg msg) {
        if(msg != null && msg.getType() == MsgType.DATA){
            getLock().readLock().lock();
            try {
                if(isReadyToReceive())
                    getWaitQueue().fairWakeUp(0, 1, 0, PollFlags.POLLIN);
                return msg;
            } finally {
                getLock().readLock().unlock();
            }
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
     * Closes link with peer if a message is not expected to be sent to it.
     * @param peerId peer's socket identifier
     */
    @Override
    public void unlink(SocketIdentifier peerId) {
        getLock().writeLock().lock();
        try {
            LinkSocket req = requester.get();
            if(req != null && Objects.equals(req.getPeerId(), peerId))
                throw new IllegalStateException("An answer as not yet been sent to the requester." +
                        "If unlinking is still desirable, use forceUnlink().");
            super.unlink(peerId);
        } finally {
            getLock().writeLock().unlock();
        }
    }

    /**
     * Force link to be closed, regardless of whether a reply
     * is expected to be sent to the peer or not.
     * @param peerId peer's socket identifier
     */
    public void forceUnlink(SocketIdentifier peerId) {
        getLock().writeLock().lock();
        try {
            LinkSocket req = requester.get();
            if(req != null && Objects.equals(req.getPeerId(), peerId)){
                requester.set(null);
                if(isReadyToReceive())
                    getWaitQueue().fairWakeUp(0,1,0,PollFlags.POLLIN);
            }
            super.unlink(peerId);
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

    @Override
    protected SocketMsg tryReceiving() throws InterruptedException {
        SocketMsg msg = null;
        getLock().writeLock().lock();
        try {
            // If socket is in a "waiting to send a reply" state,
            // the attempt to receive a request will fail.
            // While this does not prevent hanging when the user decides to
            // invoke receive() after receiving a request but before sending
            // a reply, this allows other threads to wait for a request, while
            // a request is being handled by another thread.
            if(requester.get() != null) return null;

            LinkIdentifier linkId;
            while (msg == null && (linkId = getLinkReadyToReceive()) != null) {
                LinkSocket linkSocket = getLinkSocket(linkId.destId());
                if (linkSocket != null) {
                    try {
                        msg = linkSocket.receive(0L); // non-blocking receive
                    }catch (LinkClosedException ignored) {}
                }
            }

            // sets requester
            if(msg != null)
                requester.set(getLinkSocket(msg.getSrcId()));

            return msg;
        } finally {
            getLock().writeLock().unlock();
        }
    }

    private LinkIdentifier getLinkReadyToReceive() throws InterruptedException {
        return getLinkReady(readPoller, PollFlags.POLLIN);
    }

    /**
     * @param payload payload to send. Must be a data message.
     * @return true if sent. false, if timed out.
     * @throws IllegalStateException sending a reply must be preceeded by the reception of a request.
     */
    @Override
    protected boolean trySending(Payload payload) throws InterruptedException {
        assert payload != null && payload.getType() == MsgType.DATA;
        getLock().writeLock().lock();
        try {
            boolean sent = true;
            LinkSocket req = requester.get();
            if(req != null) {
                try{
                    sent = req.send(payload, 0L);
                }catch (LinkClosedException ignored){
                    // requester reference will be removed below enabling
                    // other requests to be handled
                }

                if(sent) {
                    requester.set(null);
                    if(isReadyToReceive())
                        getWaitQueue().fairWakeUp(0, 1, 0, PollFlags.POLLIN);
                }
            }
            else throw new IllegalStateException("Receiving a request must preceed the sending of a reply.");
            return sent;
        } finally {
            getLock().writeLock().unlock();
        }
    }

    /**
     * @return true if there is a reply ready to be received.
     * @implNote Assumes socket read or write lock to be held.
     */
    private boolean isReadyToReceive() {
        return requester.get() == null && readPoller.hasEventsQuickCheck();
    }

    /**
     * @return true if a request has not been sent and there is a link available to send.
     * @implNote Assumes socket read or write lock to be held.
     */
    private boolean isReadyToSend() {
        return requester.get() != null;
    }

    /**
     * @return available events
     */
    @Override
    protected int getAvailableEventsMask() {
        int events = super.getAvailableEventsMask();
        getLock().readLock().lock();
        try {
            if (isReadyToReceive())
                events |= PollFlags.POLLIN;
            if (isReadyToSend())
                events |= PollFlags.POLLOUT;
        } finally {
            getLock().readLock().unlock();
        }
        return events;
    }
}
