package pt.uminho.di.a3m.sockets.request_reply;

import pt.uminho.di.a3m.core.*;
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
}
