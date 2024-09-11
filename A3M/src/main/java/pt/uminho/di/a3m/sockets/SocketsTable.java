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

    // ************ Protocol identifiers ************ //

    // ****** One-Way Pipeline ****** //
    public static final int PUSH_ID = 1;
    public static final Protocol PUSH_PROTOCOL = new Protocol(PUSH_ID, "One-Way Pipeline Push");
    public static final int PULL_ID = 2;
    public static final Protocol PULL_PROTOCOL = new Protocol(PULL_ID, "One-Way Pipeline Pull");

    public static List<SocketProducer> getDefaultProducers() {
        return new ArrayList<>(defaultProducers);
    }
}
