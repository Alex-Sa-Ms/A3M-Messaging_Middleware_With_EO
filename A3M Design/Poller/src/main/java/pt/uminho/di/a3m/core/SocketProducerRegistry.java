package pt.uminho.di.a3m.core;

import java.util.function.Supplier;

interface SocketProducerRegistry {
    void registerSupplier(Supplier<Socket> supplier);
    boolean removeSupplier(int protocolId);
    boolean existsSupplier(int protocolId);
}
