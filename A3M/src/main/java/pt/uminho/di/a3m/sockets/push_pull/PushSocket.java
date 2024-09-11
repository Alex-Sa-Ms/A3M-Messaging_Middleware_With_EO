package pt.uminho.di.a3m.sockets.push_pull;

import pt.uminho.di.a3m.core.Protocol;
import pt.uminho.di.a3m.core.SocketIdentifier;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.configurable_socket.ConfigurableSocket;

import java.util.*;

/**
 * One-way Pipeline Push Socket:
 * <ul>
 *     <li>Requires FIFO ordering when sending data messages.</li>
 *     <li>Round-robin when sending.</li>
 *     <li>Receiving data messages is not allowed.</li>
 * </ul>
 */
public class PushSocket extends ConfigurableSocket {
    final static public Protocol protocol = SocketsTable.PUSH_PROTOCOL;
    final static public Set<Protocol> compatProtocols = Collections.singleton(SocketsTable.PULL_PROTOCOL);

    /**
     * Creates a push socket instance.
     * @param sid identifier of the socket
     */
    public PushSocket(SocketIdentifier sid) {
        super(sid, false, true, true);
    }

    @Override
    public Protocol getProtocol() {
        return protocol;
    }

    @Override
    public Set<Protocol> getCompatibleProtocols() {
        return compatProtocols;
    }
}

