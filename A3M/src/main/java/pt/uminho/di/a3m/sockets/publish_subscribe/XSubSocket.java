package pt.uminho.di.a3m.sockets.publish_subscribe;

import pt.uminho.di.a3m.core.Protocol;
import pt.uminho.di.a3m.core.SocketIdentifier;
import pt.uminho.di.a3m.sockets.SocketsTable;

import java.util.Set;

public class XSubSocket extends SubSocket{
    public static final Protocol protocol = SocketsTable.XSUB_PROTOCOL;
    public static final Set<Protocol> compatProtocols = Set.of(SocketsTable.PUB_PROTOCOL, SocketsTable.XPUB_PROTOCOL);

    public XSubSocket(SocketIdentifier sid) {
        super(sid);
    }

    @Override
    public Protocol getProtocol() {
        return protocol;
    }

    @Override
    public Set<Protocol> getCompatibleProtocols() {
        return compatProtocols;
    }

    // TODO - extend SubSocket behavior to allow sending subscrition payloads
    //      if send(byte[] payload) is used, must check that the message is a valid
    //      subscription payload and ensure that data messages cannot be sent.
}
