package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.auxiliary.ParkStateSocket;
import pt.uminho.di.a3m.core.exceptions.NoLinksException;
import pt.uminho.di.a3m.core.exceptions.SocketClosedException;
import pt.uminho.di.a3m.core.messaging.Msg;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.core.messaging.payloads.BytePayload;
import pt.uminho.di.a3m.core.options.GenericOptionHandler;
import pt.uminho.di.a3m.core.options.OptionHandler;
import pt.uminho.di.a3m.poller.*;
import pt.uminho.di.a3m.waitqueue.ParkState;
import pt.uminho.di.a3m.waitqueue.WaitQueue;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;
import pt.uminho.di.a3m.waitqueue.WaitQueueFunc;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import static pt.uminho.di.a3m.core.auxiliary.ParkStateSocket.CHECK_IF_NONE;

// TODO 1 - create tryOperation() methods where operation(..., 0L) is possible.
//          - make it not throw interrupted exception
//          - make it not notify waiters
//          - apply them where operation(..., 0L) is used.
// TODO 2 - blocking operations with timeout/deadline that are actually
//   non-blocking since the timeout is 0L or the deadline has been reached,
//   should not queue themselves in wait queues and should not notify waiters.
//   This is because, notifying waiters in non-blocking operations is pointless
//   as a waiter must have already been notified if a notification was justified.
// TODO 3 - Add overloaded versions that do not allow passing nodeId and tagId instead
//  of a socket identifier
// TODO 4 - Overloaded versions that allows strings instead of byte arrays.

/**
 * Abstract socket class that defines the basic behavior of a socket.
 * A subclass must implement all the abstract methods obedying the instructions
 * provided in the super method description. Consulting which methods are overridable
 * is of interest when implementing a subclass. For instance, for a PUSH socket of the
 * One-way pipeline (Push-Pull) messaging protocol must not have the option to receive
 * messages, so, the subclass may override this method and make it throw the
 * java.lang.UnsupportedOperationException.
 */
public abstract class Socket {
    private final SocketIdentifier sid;
    private SocketManager socketManager = null;
    private MessageDispatcher dispatcher = null;
    private volatile SocketState state = SocketState.CREATED;
    final boolean cookedMode;
    final ReadWriteLock lock = new ReentrantReadWriteLock();
    final WaitQueue waitQ = new WaitQueue();
    private Exception error = null;

    /**
     * Initialize socket.
     * @param sid socket identifier
     * @param cookedMode if socket must be in cooked mode or raw mode. Cooked mode means
     *                   messages are passed to the socket's customOnIncomingMessage() handler
     *                   and if this handler's return value is "false" for data messages, then
     *                   the data messages are queued in the associated link's incoming queue.
     *                   While in raw mode, data and control messages are queued directly in the
     *                   appropriate link's incoming and must be retrieved using the `receive()` method.
     */
    protected Socket(SocketIdentifier sid, boolean cookedMode){
        this.sid = sid;
        this.cookedMode = cookedMode;
    }

    /**
     * Initialize socket in cooked mode.
     * @param sid socket identifier
     */
    protected Socket(SocketIdentifier sid) {
        this(sid, true);
    }

    // ******** Getters & Setters ******** //

    final MessageDispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     * Gives access to the socket's lock for the custom logic to use.
     * @return socket's lock
     */
    protected final ReadWriteLock getLock(){
        return lock;
    }

    /**
     * Gives access to the socket's wait queue for the custom logic to use.
     * @return socket's wait queue
     */
    protected final WaitQueue getWaitQueue(){
        return waitQ;
    }

    /** @return current socket state */
    public final SocketState getState() {
        return state;
    }

    /** @return socket identifier */
    public final SocketIdentifier getId() {
        return sid;
    }

    public final Exception getError() {
        return error;
    }

    public final boolean isCompatibleProtocol(int protocolId){
        for(Protocol prot : getCompatibleProtocols()){
            if(prot.id() == protocolId)
                return true;
        }
        return false;
    }

    final void setDispather(MessageDispatcher dispatcher){
        this.dispatcher = dispatcher;
    }

    final void setSocketManager(SocketManager socketManager){
        this.socketManager = socketManager;
    }

    final SocketManager getSocketManager(){
        return this.socketManager;
    }

    final void setCoreComponents(MessageDispatcher dispatcher, SocketManager socketMananer) {
        this.dispatcher = dispatcher;
        this.socketManager = socketMananer;
    }

    /**
     * Set exception that led to the error of the socket.
     * The socket state is changed to ERROR.
     * @param error exception that led to the error.
     * @implNote Does not notify waiters. The caller of the function
     * must do so if required.
     */
    protected final void setError(Exception error) {
        lock.writeLock().lock();
        try {
            if(state == SocketState.READY) {
                this.state = SocketState.ERROR;
                this.error = error;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    protected final void resetErrorState(){
        lock.writeLock().lock();
        try {
            if(state == SocketState.ERROR){
                state = SocketState.READY;
                error = null;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ******** Socket Options ******** //

    // Map of option handlers. Handlers are required to prevent changing options to unacceptable values.
    private final Map<String, OptionHandler<?>> options = defaultSocketOptions();

    /**
     * To initialize the default options of a socket.
     * @return map with default socket options (option handlers)
     */
    private static Map<String, OptionHandler<?>> defaultSocketOptions() {
        Map<String, OptionHandler<?>> options = new HashMap<>();
        // Sets default batch size percentage to 25%.
        // Defines the percentage to be used by new links.
        options.put("batchSizePercentage", new GenericOptionHandler<>(0.25f, Float.class){
            @Override
            public void set(Object value) {
                if(!(value instanceof Float) || (float) value <= 0f || (float) value > 1f)
                    throw new IllegalArgumentException("Default batch size percentage must be " +
                            "a float value between 0 (exclusive) and 1 (inclusive).");
                super.set(value);
            }
        });
        // Sets default capacity to 100 credits.
        // Defines the amount of outgoing credits that new peers will have as starting point.
        options.put("capacity", new GenericOptionHandler<>(100, Integer.class));
        // Sets link limit handler. Does not have any effect on currently established or requested links.
        options.put("maxLinks", new GenericOptionHandler<>(Integer.MAX_VALUE, Integer.class));
        // Set flag that allows disabling the acceptance of incoming link requests. Does not affect currently
        // established or requested links.
        options.put("allowIncomingLinkRequests", new GenericOptionHandler<>(true, Boolean.class));
        // Sets interval of time that should be waited before retrying the linking process when
        // a non-fatal linking process cancelation is received.
        options.put("retryInterval", new GenericOptionHandler<>(50L, Long.class));
        return options;
    }

    public static class OptionEntry{
        String option;
        Object value;
        public OptionEntry(String option, Object value) {
            this.option = option;
            this.value = value;
        }
        public Object getValue() {
            return value;
        }
    }

    /**
     * Gets socket option. If not a default socket option,
     * lets the custom socket logic handle the retrieval.
     * @param option option from which the associated value should be retrieved.
     * @param optionClass class to which the object should be cast to.
     * @return cast value associated with the option or "null" if the option does
     * not have a value associated.
     * @param <Option> class of the option value
     * @throws IllegalArgumentException if the option does not exist or the option class provided is null.
     * @throws ClassCastException if the value is not null and its class does not match
     * the requested class.
     */
    public final <Option> Option getOption(String option, Class<Option> optionClass){
        if(optionClass == null)
            throw new IllegalArgumentException("Option class must not be null.");
        try {
            lock.readLock().lock();
            OptionHandler<?> handler = options.get(option);
            if(handler != null)
                return optionClass.cast(handler.get());
            else
                throw new IllegalArgumentException("Option does not exist.");
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Retrieves multiple socket options. Invalid options have a 'null' value
     * associated in the returned array.
     * @param optionsList list of options
     * @return array of values associated with the options.
     * The values are matched with the options through the position.
     */
    public final Object[] getOptions(List<String> optionsList){
        if(optionsList == null)
            throw new IllegalArgumentException("List is null");
        lock.readLock().lock();
        try {
            Object[] values = new Object[optionsList.size()];
            String option;
            for (int i = 0; i < optionsList.size(); i++) {
                option = optionsList.get(i);
                if(option != null){
                    OptionHandler<?> handler = options.get(option);
                    if(handler != null) {
                        try { values[i] = handler.get(); }
                        catch (Exception ignored) {}
                    }
                }
            }
            return values;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * If there is a handler associated with the
     * option, invoke the set() method of the handler,
     * using the provided value. If there isn't a handler,
     * creates a generic handler that allows setting and
     * getting the option.
     * @param option identifier of the option
     * @param value value to be "set" to the option
     * @throws IllegalArgumentException if the option does not exist
     */
    public final <Option> void setOption(String option, Option value){
        try {
            lock.writeLock().lock();
            OptionHandler<?> handler = options.get(option);
            if(handler != null)
                handler.set(value);
            else
                throw new IllegalArgumentException("Option does not exist.");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Sets multiple socket options. Ignores invalid options.
     * @param optionPairs list of pairs of option and value to be set.
     */
    public final void setOptions(List<OptionEntry> optionPairs){
        lock.writeLock().lock();
        try {
            for (OptionEntry entry : optionPairs){
                if(entry != null && entry.option != null){
                    OptionHandler<?> handler = options.get(entry.option);
                    if(handler != null) {
                        try { handler.set(entry.value); }
                        catch (Exception ignored){}
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * To register option handlers.
     * @param option identifier of the option
     * @param handler option handler. Use GenericOptionHandler when
     *                wanting the traditional get-set behavior.
     */
    protected final void registerOption(String option, OptionHandler<?> handler){
        try {
            lock.writeLock().lock();
            options.put(option, handler);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ********** Link logic ********** //
    final LinkManager linkManager = new LinkManager(this, lock);
    private final Map<SocketIdentifier, LinkSocket> linkSockets = new HashMap<>(); // maps peer socket identifiers to link sockets

    public void link(SocketIdentifier peerId){
        lock.writeLock().lock();
        try {
            if(state == SocketState.READY)
                linkManager.link(peerId);
            else
                throw new IllegalStateException("Linking is only allowed when the socket is \"READY\".");
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void link(String nodeId, String tagId){
        link(new SocketIdentifier(nodeId, tagId));
    }

    public void unlink(SocketIdentifier peerId){
        linkManager.unlink(peerId);
    }

    public void unlink(String nodeId, String tagId){
        unlink(new SocketIdentifier(nodeId, tagId));
    }

    protected final LinkSocket getLinkSocket(SocketIdentifier peerId){
        lock.readLock().lock();
        try {
            return linkSockets.get(peerId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @return list containing all link sockets
     * @implNote Holds the socket's read lock.
     */
    protected final List<LinkSocket> getLinkSockets(){
        lock.readLock().lock();
        try {
            return new ArrayList<>(linkSockets.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Executes an action for all link sockets.
     * @implNote Holds the socket's read lock.
     */
    protected final void forEachLinkSocket(Consumer<LinkSocket> action){
        lock.readLock().lock();
        try {
            for (LinkSocket linkSocket : linkSockets.values())
                action.accept(linkSocket);
        } finally {
            lock.readLock().unlock();
        }
    }

    public final boolean isLinked(SocketIdentifier peerId){
        lock.readLock().lock();
        try {
            return linkSockets.containsKey(peerId);
        } finally {
            lock.readLock().unlock();
        }
    }

    public final int countEstablishedLinks(){
        lock.readLock().lock();
        try {
            return linkSockets.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** @return amount of links regardless of their state. */
    public final int countLinks(){
        return linkManager.countLinks();
    }

    /**
     * Waits for a link to be established.
     * @param peerId peer's socket identifier
     * @param timeout amount of time willing to be waited for the establishment.
     *                A null value means waiting forever.
     * @return Event mask. If 0 is returned, then waiting operation expired.
     * If POLLHUP is received, the link either does not exist or was closed.
     * If POLLIN and POLLOUT are received, then the link is established. Althought,
     * POLLIN and POLLOUT are received, that only means the link was established. It
     * must not be considered as an accurate polling of the sending/receiving capacity.
     * @throws InterruptedException if an interrupt signal was detected while waiting.
     */
    public final int waitForLinkEstablishment(SocketIdentifier peerId, Long timeout) throws InterruptedException {
        Long deadline = Timeout.calculateEndTime(timeout);
        // if link socket exists, then the link is already registered.
        if(isLinked(peerId)) return PollFlags.POLLINOUT_BITS;
        else {
            Link link = linkManager.getLink(peerId);
            if(link != null) {
                // do non-blocking verification if the time has expired
                boolean timedOut = Timeout.hasTimedOut(deadline);
                if (timedOut) return checkLinkEstablishment(link);
                // add the thread as a waiter of the link
                ParkState ps = new ParkState(true);
                AtomicReference<WaitQueueEntry> wait = new AtomicReference<>(null);
                link.poll(new PollTable(0, null, (p, waitEntry, pt) -> {
                    if (waitEntry != null) {
                        wait.set(waitEntry);
                        waitEntry.add(WaitQueueEntry::defaultWakeFunction, ps);
                    }
                }));
                // if adding thread as waiter was not possible,
                // then the link must have been closed, so return POLLHUP
                if (wait.get() == null) return PollFlags.POLLHUP;
                // If no events were returned by the poll,
                // wait for wake-up call, for the deadline to be reached or
                // the thread to be interrupted.
                int ret = checkLinkEstablishment(link);
                try {
                    if(ret == 0) {
                        WaitQueueEntry.parkStateWaitUntilFunction(deadline, ps, true);
                        ret = checkLinkEstablishment(link);
                    }
                } finally {
                    // ensure the wait queue entry is deleted
                    wait.get().delete();
                }
                return ret;
            }else {
                // if link does not exist, return POLLHUP immediately
                return PollFlags.POLLHUP;
            }
        }
    }

    private int checkLinkEstablishment(Link link){
        return switch (link.getState()) {
            case ESTABLISHED -> PollFlags.POLLINOUT_BITS;
            case CLOSED -> PollFlags.POLLHUP;
            default -> 0;
        };
    }

    /**
     * Waits for a link to be closed.
     * @param peerId peer's socket identifier
     * @param timeout amount of time willing to be waited for the closure.
     *                A null value means waiting forever.
     * @return events mask. If 0 is returned, then waiting operation expired.
     * If one of the returned flags is POLLHUP, the link either does not exist or was closed.
     * @throws InterruptedException if an interrupt signal was detected while waiting.
     */
    public final int waitForLinkClosure(SocketIdentifier peerId, Long timeout) throws InterruptedException {
        // if link socket exists, then the link is already registered.
        if(isLinked(peerId)) return 0;
        else {
            Link link = linkManager.getLink(peerId);
            if(link != null) {
                // subscribe to POLLHUP event and wait
                return Poller.poll(link,PollFlags.POLLHUP,timeout);
            }else {
                // if link does not exist, return POLLHUP immediately
                return PollFlags.POLLHUP;
            }
        }
    }

    /** Wait queue to wait for any link establishment. Created if required. */
    final AtomicReference<WaitQueue> waitAnyLinkQ = new AtomicReference<>(null);

    /**
     * Creates wait any link establishment queue if null.
     */
    private void createWaitAnyLinkQueueIfRequired(){
        if (waitAnyLinkQ.get() == null) {
            synchronized (waitAnyLinkQ) {
                if (waitAnyLinkQ.get() == null) {
                    waitAnyLinkQ.set(new WaitQueue());
                }
            }
        }
    }

    /**
     * Gets the identifier of the first peer with which a link is established.
     * If there isn't a link established, a return null is returned if the notifyIfNone
     * flag is false. If the flag is true, then an IllegalStateException is thrown to notify.
     *
     * @param notifyIfNone should be set if being notified by an IllegalStateException is desired
     *                     when there aren't any links (regardless of the link states).
     * @return socket identifier of the first peer with which a link is established. 'null' if there isn't
     * one and the "notifyIfNone" flag is not set.
     * @throws NoLinksException if there aren't any links and the "notify of none" flag is set
     */
    private SocketIdentifier auxWaitForAnyLinkEstablishment(boolean notifyIfNone){
        lock.readLock().lock();
        try {
            if(state == SocketState.CLOSED || state == SocketState.CLOSING)
                throw new IllegalStateException("Socket is closed.");
            if(!linkSockets.isEmpty())
                return linkSockets.keySet().iterator().next();
            if(notifyIfNone && !linkManager.hasLinks())
                throw new NoLinksException();
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Method that can be used to wait for a link to be established. The method returns the
     * identifier of the first established link it finds.
     * @param timeout time willing to be waited
     * @param notifyIfNone if set, an exception will be thrown when there aren't any links
     *                     (not only in established state but on all other states).
     * @return socket identifier of a peer that is established. null if operation timed out
     * before a link was established.
     * @throws InterruptedException if thread was interrupted during the waiting operation.
     * @throws IllegalStateException if socket is closed.
     * @throws NoLinksException if there aren't any links and the "notify of none" flag is set
     */
    public final SocketIdentifier waitForAnyLinkEstablishment(Long timeout, boolean notifyIfNone) throws InterruptedException {
        Long deadline = Timeout.calculateEndTime(timeout);

        SocketIdentifier sid = auxWaitForAnyLinkEstablishment(notifyIfNone);
        if(sid != null) return sid;

        // create wait any link queue if required
        createWaitAnyLinkQueueIfRequired();

        WaitQueueEntry wait = waitAnyLinkQ.get().initEntry();
        ParkState ps = new ParkState(false);
        wait.add(WaitQueueEntry::defaultWakeFunction, ps);
        try {
            while (true) {
                ps.setParkState(true);
                sid = auxWaitForAnyLinkEstablishment(notifyIfNone);
                if (sid != null) return sid;
                if(Timeout.hasTimedOut(deadline)) return null;
                WaitQueueEntry.parkStateWaitUntilFunction(deadline, ps, true);
            }
        }finally { wait.delete(); }
    }

    // ********** Socket unmodifiable methods ********** //

    /**
     * Schedule the dispatch of a message.  
     * @param msg message to be dispatched
     * @param dispatchTime time at which the dispatch should be executed.
     *                     Must be obtained using System.currentTimeMillis()
     * @apiNote Currently, can only be invoked by the middleware thread, since the
     * scheduling of messages is not protected by locks.
     */
    final AtomicReference<Msg> scheduleDispatch(SocketMsg msg, long dispatchTime) {
        return dispatcher.scheduleDispatch(msg, dispatchTime);
    }

    final void dispatch(SocketMsg msg){
        dispatcher.dispatch(msg);
    }
    
    /**
     * <p>
     * To be used by the message management system to deliver
     * messages directed to the socket.
     * </p>
     * <p>
     *     This method interceps messages that are part of
     *     default socket functionality, such as linking, and
     *     lets the rest of the messages be handled by the custom
     *     socket functionalities through customFeedMsg().
     * </p>
     * <p>
     *     This method also makes data messages undergo an additional procedure
     *     related to the credit-based flow control mechanism. Sending a data message
     *     requires a credit, and since credits are not endless, credits must be
     *     provided to the sender to keep the flow of the communication.
     *     In order to facilitate the development of new types of
     *     sockets, the sockets are designed in a way that enables automatic
     *     provision of credits to the sender. Since the main purpose of the flow
     *     control mechanism is to prevent the sender from overwhelming the receiver,
     *     the receiver must only replenish the credit consumed by the sender when
     *     the data message is handled. With all that said, the automatic provision of credits
     *     is done when a data message is dequeued from the link's incoming queue or
     *     the custom feed method return value for the data message is "true", which means
     *     the message was handled and does not need to be queued in the link's incoming queue.
     * </p>
     * @param msg socket message to be handled
     */
    final void onIncomingMessage(SocketMsg msg) {
        assert msg != null;
        // if message is of data or custom control type,
        // check if it can be processed
        if(msg.getType() == MsgType.DATA
                || !MsgType.isReservedType(msg.getType()))
            onIncomingDataOrCustomControlMessage(msg);
        else
            linkManager.handleMsg(msg);
    }

    private void onIncomingDataOrCustomControlMessage(SocketMsg msg){
        SocketIdentifier peerId = msg.getSrcId();
        LinkSocket linkSocket = getLinkSocket(peerId);
        // if link socket does not exist, then the link has not
        // yet been established with the peer.
        if(linkSocket == null){
            // The message is passed to the link manager in order
            // to check if it can be used to establish the link and
            // then be processed. If it is not accepted by the link
            // manager, then it is discarded.
            if(!linkManager.handleMsg(msg)) return;
            // since the message was deemed valid by the link manager,
            // we assume the link was registered and a link socket can be retrieved
            linkSocket = getLinkSocket(peerId);
            if (linkSocket == null) return;
        }
        // If socket is in COOKED mode, then pass the message to be processed by the custom socket.
        if(cookedMode){
            SocketMsg rMsg = customOnIncomingMessage(linkSocket, msg);
            if(msg.getType() == MsgType.DATA){
               if(rMsg != null && rMsg.getType() == MsgType.DATA)
                   linkSocket.getLink().queueIncomingMessage(rMsg);
               else
                   linkSocket.getLink().acknowledgeDeliverAndIncrementBatch();
            }
        }
        // If socket is in RAW mode, then queue message immediately
        else{ linkSocket.getLink().queueIncomingMessage(msg); }
    }

    //final void onCookie(Cookie cookie) {
    //    // TODO - onCookie()
    //}

    public final void start() {
        try {
            lock.writeLock().lock();
            if (state != SocketState.CREATED)
                throw new IllegalArgumentException("Socket has already been started once.");
            // performs custom initializing procedure if socket is in cooked mode
            if(cookedMode) init();
            // if dispatcher is not set, then the socket has not yet
            // been registered as started by the socket manager.
            if(dispatcher == null) socketManager.startSocket(this);
            // sets state to ready
            state = SocketState.READY;
        }finally {
            lock.writeLock().unlock();
        }
    }

    /** Wakes up all threads that invoked waitForAnyLinkEstablishment().*/
    private void wakeAnyLinkWaiters(SocketIdentifier sid) {
        lock.readLock().lock();
        try {
            WaitQueue waitQueue = waitAnyLinkQ.get();
            if(waitQueue != null && !waitQueue.isEmpty())
                waitQueue.wakeUp(0,0,0,sid);
        } finally {
            lock.readLock().unlock();
        }
    }

    public final void onLinkEstablished(Link link) {
        assert link != null;
        try {
            lock.writeLock().lock();
            // create link socket
            LinkSocket linkSocket = createLinkSocket(link, linkManager);
            // set incoming queue
            Queue<SocketMsg> queue =
                    validateAndCreateIncomingQueue(link.getPeerProtocolId());
            link.setInMsgQ(queue);
            // register link socket
            linkSockets.put(link.getDestId(), linkSocket);
            customOnLinkEstablished(linkSocket);
            // wake up all threads waiting for a link to be established
            wakeAnyLinkWaiters(sid);
        }finally {
            lock.writeLock().unlock();
        }
    }

    private LinkSocket createLinkSocket(Link link, LinkManager linkManager) {
        LinkSocket linkSocket = createLinkSocketInstance(link.getPeerProtocolId());
        // If method was overriden by subclass and returns a null instance,
        // then create a regular link socket.
        if(linkSocket == null) linkSocket = new LinkSocket();
        // set link and link manager which are required for basic behavior
        linkSocket.setLinkAndManager(link, linkManager);
        return linkSocket;
    }

    /**
     * Closes socket. Returns when socket is closed or the timeout expires or
     * the thread is interrupted. The timeout expiring does not mean forcing the
     * clousre of the socket. Due to exactly-once semantics, only after all links
     * are closed and the resources required by the custom logic are freed, will the
     * socket close.
     * @param timeout maximum time willing to be waited for the socket to be closed.
     * @throws InterruptedException if the thread was interrupted while waiting.
     */
    public final boolean close(Long timeout) throws InterruptedException {
        try {
            lock.writeLock().lock();
            SocketState tmpState = state;
            if (tmpState == SocketState.CLOSED)
                return true;
            if (tmpState != SocketState.CLOSING)
                closeInternal();
        } finally {
            lock.writeLock().unlock();
        }
        return (Poller.poll(pollThis, PollFlags.POLLHUP, timeout) & PollFlags.POLLHUP) != 0;
    }

    /**
     * Closes socket. Returns after the socket is closed.
     * @throws InterruptedException if the thread was interrupted.
     */
    public final void close() throws InterruptedException {
        close(null);
    }

    /**
     * Initiates closing procedure of the socket and returns immediately.
     */
    public final void asyncClose() {
        try { close(0L);
        } catch (InterruptedException ignored) {}
    }

    /**
     * Internal close call. Called to close and invoke
     * cleaning procedures when appropriate.
     */
    protected final void closeInternal() {
        SocketState tmpState = state;
        if(tmpState != SocketState.CLOSED) {
            if (tmpState == SocketState.READY) {
                // set state to CLOSING
                state = SocketState.CLOSING;
                // Unlinking all links is required before closing the socket.
                for (LinkSocket linkSocket : linkSockets.values()) {
                    linkSocket.unlink();
                }
            }
            if (!linkManager.hasLinks()) {
                state = SocketState.CLOSED;
                // perform custom closing procedures if socket is in cooked mode
                if(cookedMode && tmpState != SocketState.CREATED) destroy();
                socketManager.closeSocket(this);
                waitQ.wakeUp(0,0,0,PollFlags.POLLHUP | PollFlags.POLLFREE);
            }
        }
    }

    /** Wakes up all threads that invoked waitForAnyLinkEstablishment() if
     * there isn't any link regardless of the state. */
    private void wakeAnyLinkWaitersIfNoLinks() {
        if(!linkManager.hasLinks())
            wakeAnyLinkWaiters(null);
    }

    public final void onLinkClosed(Link link) {
        try {
            lock.writeLock().lock();
            LinkSocket linkSocket = linkSockets.remove(link.getDestId());
            if(linkSocket != null) customOnLinkClosed(linkSocket);
            if (!linkManager.hasLinks()) {
                // Notify with CHECK_IF_NONE mode, so that waiters that
                // require waking up when there aren't any links can do so.
                getWaitQueue().wakeUp(CHECK_IF_NONE, 0, 0, 0);

                if(state == SocketState.CLOSING)
                    closeInternal();
            }
            wakeAnyLinkWaitersIfNoLinks();
        }finally {
            lock.writeLock().unlock();
        }
    }

    private static Queue<SocketMsg> getDefaultInQueue(){
        return new LinkedList<>();
    }

    /**
     * Attempts to retrieve a new and custom queue provided by the socket's custom logic.
     * If a null queue is provided, then a default linked queue is used instead.
     * @param peerProtocolId peer's protocol identifier that may be relevant to decide the type of queue.
     * @return queue for socket messages
     */
    private Queue<SocketMsg> validateAndCreateIncomingQueue(int peerProtocolId){
        Queue<SocketMsg> queue = createIncomingQueue(peerProtocolId);
        if(queue == null) queue = getDefaultInQueue();
        return queue;
    }

    /**
     * Using a poller with link-related objects (link, link socket, and any other object
     * that monitors are returns the link identifier), gets the socket identifier of a peer
     * which is ready for a certain event.
     * @param linkPoller poller of link-related objects from which an event should be polled.
     * @param event event of interest
     * @return identifier of the link that is ready for the requested event
     */
    protected LinkIdentifier getLinkReady(Poller linkPoller, int event) throws InterruptedException {
        List<PollEvent<Object>> rlist;
        LinkIdentifier linkId = null;
        int maxIters = linkPoller.size(); // prevent infinite iteration
        for (int i = 0; linkId == null && i < maxIters ; i++) {
            rlist = linkPoller.await(1, 0L);
            if(rlist != null){
                PollEvent<Object> linkReady = rlist.getFirst();
                if((linkReady.events & event) != 0)
                    linkId = (LinkIdentifier) linkReady.data;
            }
        }
        return linkId;
    }

    // ********** Abstract socket methods ********** //
    protected abstract void init();

    /**
     * To implement custom closing procedures, such as destroying and
     * closing any resources initialized by the custom socket logic.
     * @implSpec implementation must avoid - more like "not include" - any blocking operations.
     */
    protected abstract void destroy();
    protected abstract void customOnLinkEstablished(LinkSocket linkSocket);
    protected abstract void customOnLinkClosed(LinkSocket linkSocket);
    /**
     * Custom logic to process an incoming data or control message when the socket is in COOKED mode.
     * When the message passed as parameter is of data type, if a socket message is to
     * be returned, it must have its type as data as well. Only data messages are queued
     * in the appropriate link's queue. Custom control messages returned are discarded.
     * @param linkSocket link socket associated with the message
     * @param msg data or control message to be handled
     * @return null if message does not require further handling, or a socket message
     * to be queued in the appropriate link's queue. The data message can be returned as received,
     * if it should be queued as it was received.
     */
    protected abstract SocketMsg customOnIncomingMessage(LinkSocket linkSocket, SocketMsg msg);

    /**
     * Creates an incoming queue. Custom sockets may override this method to
     * supply queues that best meet the socket's semantics, such as providing a queue
     * that uses a Comparator to order messages on insertion.
     * @implSpec <p>The poll() method is used to retrieve a message from the queue.</p>
     * <p>The peek() method is used to detect if a queue can be polled. For instance,
     * if total order is required, the peek() method must only return the first element
     * of the queue when it is the next element that should be polled.</p>
     * <p>isEmpty() or size() must report how many elements are in the queue accurately.</p>
     * <p>The supplied queue should not have size restrictions, as the exactly-once
     * semantics do not tolerate the discarding of messages, therefore, we assume the message
     * is added to the queue without any problem.</p>
     * <p>Also, when overriding the method, it must be taken into consideration the mode of
     * the socket. On COOKED mode, only data messages will be added to the queue.
     * On RAW mode, both data and custom control messages are added. In the latter case,
     * one may want to have two queues disguised as one.</p>
     * @param peerProtocolId peer's protocol identifier which may be relevant for
     *                       the election of a queue.
     * @return supplier of an incoming queue for the given link
     */
    protected Queue<SocketMsg> createIncomingQueue(int peerProtocolId){
        return getDefaultInQueue();
    }

    /**
     * Method to create a link socket instance. Since link sockets are the central for communication,
     * having all custom information and behavior related to the link in a single point may be
     * desirable from the efficiency and usability points of view. So, specializations of socket can
     * override this method to specify the kind of instance they desire to be associated to a link with
     * the provided peer protocol.
     * @return non-null link socket
     */
    protected LinkSocket createLinkSocketInstance(int peerProtocolId){
        return new LinkSocket();
    }

    /**
     * @return the messaging protocol that the socket talks. Must be non-null.
     */
    public abstract Protocol getProtocol();

    /**
     * @return set of compatible messaging protocols that peers can talk.
     */
    public abstract Set<Protocol> getCompatibleProtocols();

    // ******** Receiving ********* //

    private static final WaitQueueFunc directWaiterWakeFunc = (wait, mode, flags, key) -> {
        int iKey = (int) key;
        ParkStateSocket ps = (ParkStateSocket) wait.getPriv();
        if((iKey & ps.getEvents()) != 0 ||
                (mode == CHECK_IF_NONE && ps.isNotifyIfNone())) {
            return wait.parkStateWakeUp(ps);
        }
        return 0;
    };

    private static final PollQueueingFunc directWaiterQueuingFunc = (p, wait, pt) -> {
        if(wait != null){
            wait.addExclusive(directWaiterWakeFunc, pt.getPriv());
            ((ParkStateSocket) pt.getPriv()).setWaitQueueEntry(wait);
        }
    };

    /**
     * Used by receive()/receiveMsg() to attempt to receive a message.
     * @return null if there isn't a message available to be received.
     * Non-null value, otherwise.
     */
    protected abstract SocketMsg tryReceiving() throws InterruptedException;

    /**
     * Waits for a message to be received.
     * @param timeout maximum time allowed for a message to be received. After such time
     *                is elaped, the method must return null.
     * @param notifyIfNone "true" when a NoLinksException is desirable to
     *                     stop the waiting operation when there aren't any links.
     * @return null if operation timed out. Non-null value if a message was received successfully.
     * @throws InterruptedException .
     * @throws IllegalStateException if the socket has not yet been started.
     * @throws SocketClosedException if socket is closed
     * @throws NoLinksException if the "notify if none" flag is set
     * and there aren't any links regardless of the state being established,
     * linking, etc.
     */
    protected SocketMsg receiveMsg(Long timeout, boolean notifyIfNone) throws InterruptedException {
        SocketMsg msg;
        Long deadline = Timeout.calculateEndTime(timeout);
        ParkStateSocket ps = null;
        try {
            getLock().readLock().lock();

            if(state == SocketState.CREATED)
                throw new IllegalStateException("Socket has not yet been started.");
            if(state == SocketState.CLOSED)
                throw new SocketClosedException();

            // check if there are any links when the notifyIfNone flag is used
            if(notifyIfNone && countLinks() == 0)
                throw new NoLinksException();

            // queue the thread if the operation is blocking
            if(!Timeout.hasTimedOut(deadline)) {
                // queue the thread
                ps = new ParkStateSocket(
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
                if(state == SocketState.CLOSED)
                    throw new SocketClosedException();

                msg = tryReceiving();
                if (msg != null) return msg;

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

    /**
     * Waits for a message to be received.
     * @implNote Uses "receive(timeout, notifyIfNone) : SocketMsg".
     * @param timeout maximum time allowed for a message to be received. After such time
     *                is elaped, the method must return null.
     * @param notifyIfNone "true" when a NoLinksException is desirable to
     *                     stop the waiting operation when there aren't any links.
     * @return null if operation timed out. Non-null value if a message was received successfully.
     * @throws InterruptedException .
     * @throws IllegalStateException if the socket has not yet been started.
     * @throws SocketClosedException if socket is closed
     * @throws NoLinksException if the "notify if none" flag is set
     * and there aren't any links regardless of the state being established,
     * linking, etc.
     */
    public byte[] receive(Long timeout, boolean notifyIfNone) throws InterruptedException{
        SocketMsg msg = receiveMsg(timeout, notifyIfNone);
        return msg != null ? msg.getPayload() : null;
    }

    /**
     * Waits for a message to be received.
     * @apiNote Assumes that notifying when there aren't any links is not desirable.
     * @see Socket#receive(Long, boolean)
     */
    public byte[] receive(Long timeout) throws InterruptedException{
        return receive(timeout, false);
    }

    /**
     * Waits for a message to be received.
     * @apiNote Assumes the operation should be blocking
     * and that notifying when there aren't any links is not desirable.
     * @see Socket#receive(Long, boolean)
     */
    public byte[] receive() throws InterruptedException{
        return receive(null, false);
    }

    // ******** Sending ********* //

    protected abstract boolean trySending(Payload payload) throws InterruptedException;

    /**
     * Waits to send a payload.
     * @param payload Content and its type that should be sent.
     * @param timeout maximum time allowed for a message to be sent. After such time
     *                is elaped, the method must return false.
     * @param notifyIfNone "true" when a NoLinksException is desirable to
     *                     stop the waiting operation when there aren't any links.
     * @return false if operation timed out. True if a message was sent successfully.
     * @throws InterruptedException if the thread was interrupted while sending.
     * @throws SocketClosedException if socket is closed
     * @throws NoLinksException if the "notify if none" flag is set
     * and there aren't any links regardless of the state being established,
     * linking, etc.
     */
    protected boolean sendPayload(Payload payload, Long timeout, boolean notifyIfNone) throws InterruptedException {
        if(payload == null)
            throw new IllegalArgumentException("Payload must not be null.");

        boolean sent;
        Long deadline = Timeout.calculateEndTime(timeout);
        ParkStateSocket ps = null;
        try {
            getLock().readLock().lock();

            if(state == SocketState.CREATED)
                throw new IllegalArgumentException("Socket has not yet been started.");
            if (state == SocketState.CLOSED)
                throw new SocketClosedException();

            // check if there are any links when the notifyIfNone flag is used
            if(notifyIfNone && countLinks() == 0)
                throw new NoLinksException();

            // queue the thread if the operation is blocking
            if(!Timeout.hasTimedOut(deadline)) {
                ps = new ParkStateSocket(
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
        if(ps == null) return trySending(payload);

        try {
            while (true) {
                if(state == SocketState.CLOSED)
                    throw new SocketClosedException();

                // checks if thread was interrupted
                if(Thread.currentThread().isInterrupted())
                    throw new InterruptedException();

                sent = trySending(payload);
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
     * Waits to send a data payload.
     * @param payload Content that should be sent.
     * @param timeout maximum time allowed for a message to be sent. After such time
     *                is elaped, the method must return false.
     * @param notifyIfNone "true" when a NoLinksException is desirable to
     *                     stop the waiting operation when there aren't any links.
     * @return false if operation timed out. True if a message was sent successfully.
     * @implNote uses sendPayload().
     * @throws InterruptedException if the thread was interrupted while sending.
     * @throws SocketClosedException if socket is closed
     * @throws NoLinksException if the "notify if none" flag is set
     * and there aren't any links regardless of the state being established,
     * linking, etc.
     */
    public boolean send(byte[] payload, Long timeout, boolean notifyIfNone) throws InterruptedException {
        return sendPayload(new BytePayload(MsgType.DATA, payload),timeout, notifyIfNone);
    }

    /**
     * Waits to send a data payload.
     * @apiNote Assumes that notifying when there aren't any links is not desirable.
     * @see Socket#send(byte[], Long, boolean)
     */
    public boolean send(byte[] payload, Long timeout) throws InterruptedException {
        return send(payload,timeout,false);
    }

    /**
     * Waits to send a data payload.
     * @apiNote Assumes that the operation should be blocking and that
     * notifying when there aren't any links is not desirable.
     * @see Socket#send(byte[], Long, boolean)
     */
    public void send(byte[] payload) throws InterruptedException {
        send(payload,null,false);
    }

    // ******** Polling ********* //

    private static class SocketPollable implements Pollable {
        final Socket socket;

        public SocketPollable(Socket socket) {
            this.socket = socket;
        }

        @Override
        public Object getId() {
            return socket.getId();
        }

        @Override
        public int poll(PollTable pt) {
            return socket.poll(pt);
        }
    }

    private final SocketPollable pollThis = new SocketPollable(this);

    /**
     * Default implementation only informs POLLERR and POLLHUP events.
     * <p>Specializations of this class are encouraged to override this method to
     * add POLLIN and POLLOUT events. Calling this method and logically or-ing the new
     * events is a possibility.</p>
     * @return events mask
     * @apiNote At least the read lock should be used when calling this method.
     */
    protected int getAvailableEventsMask(){
        int events = 0;
        if(state == SocketState.CLOSED)
            events |= PollFlags.POLLHUP;
        if(error != null)
            events |= PollFlags.POLLERR;
        return events;
    }

    /**
     * This default implementation of poll() queues waiters if the socket is not closed
     * and notifies POLLERR and POLLHUP events. For POLLIN and POLLOUT events the specialization
     * class can override the getAvailableEventsMask() method.
     * @implSpec polling only reads the state, so any specialization should only use
     * the read lock when implementing this method.
     * @param pt poll table which may contain a queuing function and
     *           a private object if the caller intends to add itself to
     *           the wait queue of the pollable.
     * @return event mask
     */
    protected int poll(PollTable pt) {
        lock.readLock().lock();
        try {
            if (!PollTable.pollDoesNotWait(pt)) {
                WaitQueueEntry wait =
                        state != SocketState.CLOSED ? waitQ.initEntry() : null;
                pt.pollWait(pollThis, wait);
            }
            return getAvailableEventsMask();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Adds socket to poller.
     * @param poller poller instance
     * @param events events of interest
     */
    public void addToPoller(Poller poller, int events){
        if(poller != null)
            poller.add(pollThis, events);
    }

    /**
     * Adds socket to poller.
     * @param poller poller instance
     * @param socket socket instance
     * @param events events of interest
     */
    public static void addToPoller(Poller poller, Socket socket, int events){
        if(poller != null && socket != null)
            poller.add(socket.pollThis, events);
    }

    /**
     * Individual poll of the socket.
     * @param events events of interest
     * @param timeout Time limit for polling events.
     * @return available events
     */
    public int poll(int events, Long timeout) throws InterruptedException {
       return Poller.poll(pollThis, events, timeout) & events;
    }

    /**
     * Individual poll of the socket.
     * @param socket socket instance
     * @param events events of interest
     * @param timeout Time limit for polling events.
     * @return available events
     */
    public int poll(Socket socket, int events, Long timeout) throws InterruptedException {
        if(socket != null)
            return Poller.poll(pollThis, events, timeout) & events;
        else
            throw new IllegalArgumentException("Socket is null.");
    }
}
