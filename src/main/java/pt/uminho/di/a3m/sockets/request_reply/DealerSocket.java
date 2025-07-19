package pt.uminho.di.a3m.sockets.request_reply;

import pt.uminho.di.a3m.core.A3MMiddleware;
import pt.uminho.di.a3m.core.Protocol;
import pt.uminho.di.a3m.core.SocketIdentifier;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.configurable_socket.ConfigurableSocket;

import java.util.*;

public class DealerSocket extends ConfigurableSocket {
        final static public Protocol protocol = SocketsTable.DEALER_PROTOCOL;
    final static public Set<Protocol> compatProtocols = Set.of(SocketsTable.REP_PROTOCOL,
                                                               SocketsTable.DEALER_PROTOCOL,
                                                               SocketsTable.ROUTER_PROTOCOL);
    public DealerSocket(SocketIdentifier sid) {
        super(sid, true, true, false);
    }

    @Override
    public Protocol getProtocol() {
        return protocol;
    }

    @Override
    public Set<Protocol> getCompatibleProtocols() {
        return compatProtocols;
    }
    
    /**
     * Creates DealerSocket.
     * @param middleware middleware instance
     * @param tagId tag identifier of the socket
     * @return DealerSocket instance
     * @implNote Assumes the middleware to have the DealerSocket producer registered.
     */
    public static DealerSocket createSocket(A3MMiddleware middleware, String tagId){
        if(middleware == null)
            throw new IllegalArgumentException("Middleware is null.");
        return middleware.createSocket(tagId, protocol.id(), DealerSocket.class);
    }

    /**
     * Creates and starts a DealerSocket.
     * @param middleware middleware instance
     * @param tagId tag identifier of the socket
     * @return DealerSocket instance
     * @implNote Assumes the middleware to have the DealerSocket producer registered.
     */
    public static DealerSocket startSocket(A3MMiddleware middleware, String tagId){
        if(middleware == null)
            throw new IllegalArgumentException("Middleware is null.");
        return middleware.startSocket(tagId, protocol.id(), DealerSocket.class);
    }
}
