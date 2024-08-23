package pt.uminho.di.a3m.core;

interface SocketManagerPublic {
    Socket getSocket(String tagId);
    <T extends Socket> T getSocket(String tagId, Class<T> socketClass);
    Socket createSocket(String tagId, int protocolId);
    <T extends Socket> T createSocket(String tagId, int protocolId, Class<T> socketClass);
    Socket startSocket(String tagId, int protocolId);
    <T extends Socket> T startSocket(String tagId, int protocolId, Class<T> socketClass);
    void closeSocket(Socket s);
    void closeSocket(String tagId);
}
