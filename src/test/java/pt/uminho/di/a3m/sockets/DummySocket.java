package pt.uminho.di.a3m.sockets;

import pt.uminho.di.a3m.core.*;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Dummy socket:
 * <ul>
 *     <li>Allows changing the protocol and compatible protocols to facilitate
 *     testing the linking and unlinking procedures.</li>
 *      <li>Does not implement any custom behavior since this is not required
 *      to test the linking and unlinking procedures.</li>
 * </ul>
 */
public class DummySocket extends Socket {
    // NOTE: The protocol must be static and final, however, for 
    // tests purposes, allowing the protocol to be defined is helpful
    private Protocol protocol;
    private final Set<Protocol> compatProtocols = new HashSet<>();

    public DummySocket(SocketIdentifier sid, Protocol protocol) {
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

    @Override
    protected SocketMsg tryReceiving() throws InterruptedException {
        return null;
    }

    @Override
    protected boolean trySending(Payload payload) throws InterruptedException {
        return false;
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
    protected void init() {}

    @Override
    protected void destroy() {

    }

    @Override
    protected void customOnLinkEstablished(LinkSocket linkSocket) {

    }

    @Override
    protected void customOnLinkClosed(LinkSocket linkSocket) {

    }

    @Override
    protected SocketMsg customOnIncomingMessage(LinkSocket linkSocket, SocketMsg msg) {
        return msg;
    }

    private Supplier<Queue<SocketMsg>> inQueueSupplier = null;

    public void setInQueueSupplier(Supplier<Queue<SocketMsg>> inQueueSupplier) {
        this.inQueueSupplier = inQueueSupplier;
    }

    @Override
    protected Queue<SocketMsg> createIncomingQueue(int peerProtocolId) {
        return inQueueSupplier != null ? inQueueSupplier.get() : new ArrayDeque<>();
    }

    /**
     * Exposes link socket
     * @param peerId peer's socket identifier
     * @return link socket associated with the given identifier, or "null" if
     * there isn't a link socket associated to that identifier.
     */
    public LinkSocket linkSocket(SocketIdentifier peerId){
        return getLinkSocket(peerId);
    }
}
