package pt.uminho.di.a3m.core;

public interface SocketManager extends SocketManagerPublic, SocketProducerRegistry{
    void startSocket(Socket socket);
}
