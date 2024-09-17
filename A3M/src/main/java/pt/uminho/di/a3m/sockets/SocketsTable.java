package pt.uminho.di.a3m.sockets;

import pt.uminho.di.a3m.core.Protocol;
import pt.uminho.di.a3m.core.SocketProducer;
import pt.uminho.di.a3m.sockets.push_pull.PullSocket;
import pt.uminho.di.a3m.sockets.push_pull.PushSocket;

import java.util.ArrayList;
import java.util.List;

public class SocketsTable {
    private static final List<SocketProducer> defaultProducers =
            List.of(PushSocket::new,
                    PullSocket::new);

    public static List<SocketProducer> getDefaultProducers() {
        return new ArrayList<>(defaultProducers);
    }

    // ************ Protocol identifiers ************ //

    // ****** One-Way Pipeline ****** //
    public static final int PUSH_PROTOCOL_ID = 1;
    public static final Protocol PUSH_PROTOCOL = new Protocol(PUSH_PROTOCOL_ID, "One-Way Pipeline Push");
    public static final int PULL_PROTOCOL_ID = 2;
    public static final Protocol PULL_PROTOCOL = new Protocol(PULL_PROTOCOL_ID, "One-Way Pipeline Pull");

    // ****** Request-Reply ****** //

    public static final int REQ_PROTOCOL_ID = 3;
    public static final Protocol REQ_PROTOCOL = new Protocol(REQ_PROTOCOL_ID, "Req-Rep Requester");
    public static final int REP_PROTOCOL_ID = 4;
    public static final Protocol REP_PROTOCOL = new Protocol(REP_PROTOCOL_ID, "Req-Rep Replier");
    public static final int ROUTER_PROTOCOL_ID = 5;
    public static final Protocol ROUTER_PROTOCOL = new Protocol(ROUTER_PROTOCOL_ID, "Req-Rep Router");
    public static final int DEALER_PROTOCOL_ID = 6;
    public static final Protocol DEALER_PROTOCOL = new Protocol(DEALER_PROTOCOL_ID, "Req-Rep Dealer");
}
