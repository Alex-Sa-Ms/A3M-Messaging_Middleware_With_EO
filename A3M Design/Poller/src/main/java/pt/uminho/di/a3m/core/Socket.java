package pt.uminho.di.a3m.core;

import java.util.concurrent.atomic.AtomicReference;

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

    protected Socket(SocketIdentifier sid) {
        this.sid = sid;
    }

    void setCoreComponents(MessageDispatcher dispatcher, SocketManager socketMananer) {
        this.dispatcher = dispatcher;
        this.socketManager = socketMananer;
    }

    public SocketState getState() {
        return state.get();
    }

    public SocketIdentifier getId() {
        return sid;
    }

    public abstract Protocol getProtocol();

    public void start() {
        // TODO - start()
    }

    void feedMsg(Msg msg) {
        // TODO - feedMsg()
    }

    //void feedCookie(Cookie cookie) {
    //    // TO DO - feedCookie()
    //}

    public void close() {
        // TODO - close()
    }
}
