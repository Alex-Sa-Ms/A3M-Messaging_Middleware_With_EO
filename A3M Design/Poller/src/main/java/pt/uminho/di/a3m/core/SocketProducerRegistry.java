package pt.uminho.di.a3m.core;

interface SocketProducerRegistry {
    void registerProducer(SocketProducer producer);
    boolean removeProducer(int protocolId);
    boolean existsProducer(int protocolId);
}
