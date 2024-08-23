package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.core.events.SocketEvent;
import pt.uminho.di.a3m.core.messages.SocketMsg;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public abstract class Socket{
    /**
     * Notas:
     * 1. Deve-se adicionar nos métodos abstratos que podem não ser
     * implementados a seguinte informação: "Método deve emitir a
     * exceção java.lang.UnsupportedOperationException quando a
     * operação não faz sentido para o socket.". Um exemplo pode ser
     * o padrão de comunicação Push-Pull, em que não faz sentido
     * implementar métodos receive() para o socket de tipo PUSH,
     * logo estes métodos devem emitir a exceção.
     */
    private final SocketIdentifier sid;
    private SocketManager socketManager = null;
    private MessageDispatcher dispatcher = null;
    private final AtomicReference<SocketState> state = new AtomicReference<>(SocketState.CREATED);
    private final Lock lock = new ReentrantLock(); // TODO - a more efficient locking mechanism may be required in the future

    protected Socket(SocketIdentifier sid) {
        this.sid = sid;
    }

    public final SocketState getState() {
        return state.get();
    }

    public final SocketIdentifier getId() {
        return sid;
    }

    protected final Lock getLock(){
        return lock;
    }

    final void setCoreComponents(MessageDispatcher dispatcher, SocketManager socketMananer) {
        this.dispatcher = dispatcher;
        this.socketManager = socketMananer;
    }

    // ********** Final methods ********** //

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
    final void feedMsg(SocketMsg msg) {
        // TODO - feedMsg()
    }

    //final void feedCookie(Cookie cookie) {
    //    // TO DO - feedCookie()
    //}

    protected final boolean sendMsg(SocketIdentifier destId, byte[] payload, Long timeout /*, Cookie cookie*/){
        // TODO - sendMsg
        return false;
    }

    protected final SocketMsg recvMsg(SocketIdentifier peerId, Long timeout){
        // TODO - recvMsg
        return null;
    }

    public final void link(SocketIdentifier sid){
        // TODO - link()
    }

    public final void unlink(SocketIdentifier sid){
        // TODO - unlink()
    }

    public final boolean isLinked(SocketIdentifier sid){
        // TODO - isLinked()
        return false;
    }

    public final int waitForLink(SocketIdentifier sid){
        // TODO - waitForLink()
        return -1;
    }

    public final SocketIdentifier waitForAnyLink(boolean notifyIfNone){
        // TODO - waitForAnyLink()
        return null;
    }

    public final <O> O getOption(String option, Class<O> objectClass){
        // TODO - getOption()
        return null;
    }

    public final void setOption(String option, Object value){
        // TODO - setOption()
    }

    public final void start() {
        try {
            lock.lock();
            if (state.get() != SocketState.CREATED)
                throw new IllegalArgumentException("Socket has already been started.");
            // performs custom initializing procedure
            init();
            // sets state to ready if socket's state is "CREATED"
            state.compareAndSet(SocketState.CREATED, SocketState.READY);
        }finally {
            lock.unlock();
        }
    }

    /**
     * Method used to confirm the custom closing procedures have
     * been performed, enabling the socket to proceed from the
     * CLOSING state to CLOSED, and effectively be removed from the
     * middleware.
     */
    protected final void destroyCompleted(){
        try {
            lock.lock();
            // sets state to CLOSED if the current state is CLOSING,
            // and performs last closing procedures.
            // and
            if(state.compareAndSet(SocketState.CLOSING, SocketState.CLOSED)){
                // Calls the socket manager close() method to perform
                // the clean-up procedures such as removing the socket.
                if(socketManager != null)
                    socketManager.closeSocket(this);
                // TODO - wake up waiters with POLLFREE | POLLHUP
            }
        } finally {
            lock.unlock();
        }
    }

    // TODO - close with timeout, if already closing wait for closure. Wait using polling mechanism.
    //
    public final void close() {
        try {
            lock.lock();
            SocketState tmpState = state.get();
            if (tmpState == SocketState.CLOSED)
                return;
            // TODO - wait using polling mechanism
            if (tmpState == SocketState.CLOSING)
                throw new IllegalArgumentException("Socket is closing or has already closed.");
            // set state to CLOSING
            state.set(SocketState.CLOSING);
            // calls custom closing procedure and expects destroyCompleted()
            // to be called in order for the state to proceed to CLOSE and
            // for the middleware to perform the required socket clean-up
            // procedures.
            destroy();
        } finally {
            lock.unlock();
        }
    }

    // ********** Abstract methods ********** //
    protected abstract void init();
    /**
     * Custom closing procedures.
     * Must invoke destroyCompleted() when
     * the procedures are done to effectively
     * close the socket.
     */
    protected abstract void destroy();
    protected abstract Object getCustomOption(String option);
    protected abstract void setCustomOption(String option, Object value);
    protected abstract void customHandleEvent(SocketEvent event);
    protected abstract void customFeedMsg(SocketMsg msg);

    /**
     * Method to get an incoming queue supplier. Custom sockets may override this method to
     * supply queues that best meets the socket's semantics, such as providing a queue
     * that uses a Comparator to order messages on insertion.
     * @implSpec The supplied queue should not have size restrictions, as the exactly-once
     * semantics do not tolerate the discarding of messages, therefore, we assume the message
     * is added to the queue without any problem.
     * @param link link which may include peer's relevant information for the election
     *             of a queue.
     * @return supplier of an incoming queue for the given link
     */
    protected Supplier<Queue<SocketMsg>> getInQueueSupplier(Link link){
        return () -> new LinkedList();
    }
    protected abstract Protocol getProtocol();
    protected abstract Set<Protocol> getCompatibleProtocols();
    protected abstract byte[] receive(Long timeout, boolean notifyIfNone);
    protected abstract boolean send(byte[] payload, Long timeout, boolean notifyIfNone);
}
