package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.core.messaging.SocketMsg;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;

public class DummySocket extends Socket{
    // NOTE: The protocol must be static and final, however, for 
    // tests purposes, allowing the protocol to be defined is helpful
    private Protocol protocol;
    private final Set<Protocol> compatProtocols = new HashSet<>();

    protected DummySocket(SocketIdentifier sid, Protocol protocol) {
        super(sid);
        this.protocol = protocol;
        this.compatProtocols.add(this.protocol);
    }

    /**
     * @param nodeId node identifier
     * @param tagId socket's tag identifier
     * @param protocolId protocol identifier
     * @param protocolName protocol name
     * @param compatProtocols set of compatible protocols. If 'null' the socket is assumed to be compatible with its own protocol.
     */
    public DummySocket(String nodeId, String tagId, int protocolId, String protocolName, Set<Protocol> compatProtocols) {
        super(new SocketIdentifier(nodeId, tagId));
        this.protocol = new Protocol(protocolId, protocolName);
        if(compatProtocols == null)
            this.compatProtocols.add(this.protocol);
        else
            this.compatProtocols.addAll(compatProtocols);
    }

    @Override
    public Protocol getProtocol() {
        return protocol;
    }

    @Override
    public Set<Protocol> getCompatibleProtocols() {
        return new HashSet<>(compatProtocols);
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public void setCompatProtocols(Set<Protocol> compatProtocols){
        this.compatProtocols.clear();
        if(compatProtocols != null)
            this.compatProtocols.addAll(compatProtocols);
        else
            this.compatProtocols.add(protocol);
    }

    @Override
    public byte[] receive(Long timeout, boolean notifyIfNone) {
        return new byte[0];
    }

    @Override
    public boolean send(byte[] payload, Long timeout, boolean notifyIfNone) {
        return false;
    }

    @Override
    public boolean isOutgoingCustomMsgValid(SocketMsg msg) {
        return false;
    }

    @Override
    protected void init() {}

    @Override
    protected void destroy() {
        destroyCompleted();
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
