package pt.uminho.di.a3m.core;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

    final void setCoreComponents(MessageDispatcher dispatcher, SocketManager socketMananer) {
        this.dispatcher = dispatcher;
        this.socketManager = socketMananer;
    }

    public final SocketState getState() {
        return state.get();
    }

    public final SocketIdentifier getId() {
        return sid;
    }

    public abstract Protocol getProtocol();

    protected abstract void init();

    /**
     * Custom closing procedures.
     * Must invoke destroyCompleted() when
     * the procedures are done to effectively
     * close the socket.
     */
    protected abstract void destroy();

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

    final void feedMsg(Msg msg) {
        // TODO - feedMsg()
    }

    //final void feedCookie(Cookie cookie) {
    //    // TO DO - feedCookie()
    //}
}
