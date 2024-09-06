package pt.uminho.di.a3m.core;

@FunctionalInterface
public interface SocketProducer {
    Socket get(SocketIdentifier sid);
}
