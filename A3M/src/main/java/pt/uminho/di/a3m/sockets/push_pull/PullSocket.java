package pt.uminho.di.a3m.sockets.push_pull;

import pt.uminho.di.a3m.core.Protocol;
import pt.uminho.di.a3m.core.SocketIdentifier;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.configurable_socket.ConfigurableSocket;

import java.util.Collections;
import java.util.Set;

/**
 * One-way Pipeline Pull Socket:
 * <ul>
 *     <li>Requires FIFO ordering when receiving data messages.</li>
 *     <li>Round-robin when receiving.</li>
 *     <li>Sending data messages is not allowed.</li>
 * </ul>
 */
public class PullSocket extends ConfigurableSocket {
    final static public Protocol protocol = SocketsTable.PULL_PROTOCOL;
    final static public Set<Protocol> compatProtocols = Collections.singleton(SocketsTable.PUSH_PROTOCOL);

    /**
     * Creates a pull socket instance.
     * @param sid identifier of the socket
     */
    public PullSocket(SocketIdentifier sid) {
        super(sid, true, false, true);
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
