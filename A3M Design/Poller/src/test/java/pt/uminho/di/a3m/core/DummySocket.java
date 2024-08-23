package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.core.events.SocketEvent;
import pt.uminho.di.a3m.core.messages.SocketMsg;

import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;

public class DummySocket extends Socket{
    // NOTE: The protocol must be static and final, however, for 
    // tests purposes, allowing the protocol to be defined is helpful
    Protocol protocol;

    protected DummySocket(SocketIdentifier sid, Protocol protocol) {
        super(sid);
        this.protocol = protocol;        
    }

    @Override
    public Protocol getProtocol() {
        return protocol;
    }

    @Override
    protected Set<Protocol> getCompatibleProtocols() {
        return null;
    }

    @Override
    protected byte[] receive(Long timeout, boolean notifyIfNone) {
        return new byte[0];
    }

    @Override
    protected boolean send(byte[] payload, Long timeout, boolean notifyIfNone) {
        return false;
    }

    @Override
    protected void init() {}

    @Override
    protected void destroy() {
        destroyCompleted();
    }

    @Override
    protected Object getCustomOption(String option) {
        return null;
    }

    @Override
    protected void setCustomOption(String option, Object value) {

    }

    @Override
    protected void customHandleEvent(SocketEvent event) {

    }

    @Override
    protected void customFeedMsg(SocketMsg msg) {

    }

    @Override
    protected Supplier<Queue<SocketMsg>> getInQueueSupplier(Link link) {
        return null;
    }
}
