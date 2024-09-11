package pt.uminho.di.a3m.sockets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.core.*;
import pt.uminho.di.a3m.sockets.push_pull.PullSocket;
import pt.uminho.di.a3m.sockets.push_pull.PushSocket;

import java.io.IOException;
import java.net.*;
import java.util.AbstractMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class OneWayPipelineTests {
    private final static List<SocketProducer> producerList = List.of(PushSocket::new, PullSocket::new);
    private A3MMiddleware middleware;
    private int port;
    private int nrPushSockets = 1;
    private PushSocket[] pushSockets;
    private int nrPullSockets = 1;
    private PullSocket[] pullSockets;

    private static int getAvailablePort(){
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static AbstractMap.SimpleEntry<Integer, A3MMiddleware> createAndStartMiddlewareInstance() throws SocketException, UnknownHostException {
        for (int i = 0; i < 100; i++) {
            try {
                int port = getAvailablePort();
                A3MMiddleware m = new A3MMiddleware("Node", null, port, null, producerList);
                m.start();
                return new AbstractMap.SimpleEntry<>(port, m);
            } catch (Exception ignored) {}
        }
        throw new BindException("Could not find an available adress.");
    }

    void initPushSockets(){
        pushSockets = new PushSocket[nrPushSockets];
        for (int i = 0; i < nrPushSockets; i++)
            pushSockets[i] = middleware.createSocket(
                    "PushSocket" + i,
                    PushSocket.protocol.id(),
                    PushSocket.class);
    }

    void initPullSockets(){
        pullSockets = new PullSocket[nrPullSockets];
        for (int i = 0; i < nrPullSockets; i++)
            pullSockets[i] = middleware.createSocket(
                    "PullSocket" + i,
                    PullSocket.protocol.id(),
                    PullSocket.class);
    }

    @BeforeEach
    void init() throws SocketException, UnknownHostException {
        var entry = createAndStartMiddlewareInstance();
        port = entry.getKey();
        middleware = entry.getValue();
        initPushSockets();
        initPullSockets();
    }

    @Test
    void pushSocketCannotReceive() throws InterruptedException {
        try {
            pushSockets[0].receive(0L, false);
            // cannot reach here due to UnsupportedOperationException being thrown.
            assert false;
        } catch (UnsupportedOperationException ignored) {}

        // assert capacity cannot be changed
        try {
            pushSockets[0].setOption("capacity", 10);
            assert false;
        } catch (UnsupportedOperationException ignored) {}
    }


    @Test
    void pullSocketCannotSend() throws InterruptedException {
        try {
            pullSockets[0].send("Hello".getBytes(), 0L, false);
            // cannot reach here due to UnsupportedOperationException being thrown.
            assert false;
        } catch (UnsupportedOperationException ignored) {}
    }

    /**
     * Check that all messages are received and are received in order.
     */
    @Test
    void oneToOneSendReceive() throws InterruptedException {
        final int nrMsgs = 10000;

        AtomicBoolean receivedAll = new AtomicBoolean(false);
        Thread receiver = new Thread(() -> {
            int counter = 0;
            try {
                byte[] arrMsg;
                String msg;
                while (counter < nrMsgs){
                    arrMsg = pullSockets[0].receive(null, false);
                    if(arrMsg != null) {
                        msg = SocketTestingUtilities.decodeByteArrayToString(arrMsg);
                        assert Objects.equals(msg, String.valueOf(counter));
                        counter++;
                    }
                }
                receivedAll.set(true);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        receiver.start();

        SocketIdentifier pullSocketId = pullSockets[0].getId();
        pushSockets[0].link(pullSocketId);

        for (int i = 0; i < nrMsgs; i++)
            pushSockets[0].send(String.valueOf(i).getBytes(), null, false);

        receiver.join();
        assert receivedAll.get();
    }

    @Test
    void oneToOneSendReceiveButMultiThreaded() throws InterruptedException {
        // NOTE: assert work can be evenly distributed (nrMsgs % nrSockets == 0)
        int nrReceivers = 10;
        int nrSenders = 10;
        final int nrMsgs = 10000;
        int nrMsgsPerSender = nrMsgs / nrSenders;
        int nrMsgsPerReceiver = nrMsgs / nrReceivers;

        AtomicInteger globalCounter = new AtomicInteger(0);
        Thread[] receivers = new Thread[nrReceivers];
        for (int i = 0; i < nrReceivers; i++) {
            receivers[i] = new Thread(() -> {
                byte[] arrMsg;
                int counter = 0;
                try {
                    while (counter < nrMsgsPerReceiver){
                        arrMsg = pullSockets[0].receive(null, false);
                        if(arrMsg != null) counter++;
                    }
                    // add to global counter the amount of messages received
                    globalCounter.addAndGet(counter);
                } catch (InterruptedException ignored) {}
            });
            receivers[i].start();
        }

        Thread[] senders = new Thread[nrSenders];
        for (int i = 0; i < nrSenders; i++) {
            int finalI = i;
            senders[i] = new Thread(() -> {
                int counter = 0;
                int msg;
                boolean sent;
                try {
                    while (counter < nrMsgsPerSender){
                        msg = finalI * nrMsgsPerSender + counter;
                        sent = pushSockets[0].send(String.valueOf(msg).getBytes(), null, false);
                        //System.out.println(finalI + ": Sent " + msg);
                        assert sent;
                        counter++;
                    }
                } catch (InterruptedException ignored) {}
            });
            senders[i].start();
        }

        SocketIdentifier pullSocketId = pullSockets[0].getId();
        pushSockets[0].link(pullSocketId);

        for (int i = 0; i < nrSenders; i++)
            senders[i].join();

        for (int i = 0; i < nrReceivers; i++)
            receivers[i].join();

        System.out.println(globalCounter.get());
        assert globalCounter.get() == nrMsgs;
    }

    @Test
    void manyToManySendReceive() throws InterruptedException, SocketException, UnknownHostException {
        // NOTE: assert work can be evenly distributed (nrMsgs % nrSockets == 0)
        nrPullSockets = 10;
        nrPushSockets = 10;
        final int nrMsgs = 10000;
        int nrMsgsPerPushSocket = nrMsgs / nrPushSockets;

        // perform init again so that the new number is taken into consideration
        init();

        // global counter for messages received
        AtomicInteger globalCounter = new AtomicInteger(0);
        Thread[] receivers = new Thread[nrPullSockets];
        for (int i = 0; i < nrPullSockets; i++) {
            int finalI = i;
            receivers[i] = new Thread(() -> {
                byte[] arrMsg;
                try {
                    while (globalCounter.get() < nrMsgs){
                        arrMsg = pullSockets[finalI].receive(100L, false);
                        if(arrMsg != null) globalCounter.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {}
            });
            receivers[i].start();
        }

        Thread[] senders = new Thread[nrPushSockets];
        for (int i = 0; i < nrPushSockets; i++) {
            int finalI = i;
            senders[i] = new Thread(() -> {
                int counter = 0;
                int msg;
                boolean sent;
                try {
                    while (counter < nrMsgsPerPushSocket){
                        msg = finalI * nrMsgsPerPushSocket + counter;
                        sent = pushSockets[finalI].send(String.valueOf(msg).getBytes(), null, false);
                        //System.out.println(finalI + ": Sent " + msg);
                        assert sent;
                        counter++;
                    }
                } catch (InterruptedException ignored) {}
            });
            senders[i].start();
        }

        for (int i = 0; i < nrPushSockets; i++) {
            for (int j = 0; j < nrPullSockets; j++) {
                pushSockets[i].link(pullSockets[j].getId());
            }
        }

        for (int i = 0; i < nrPushSockets; i++)
            senders[i].join();

        for (int i = 0; i < nrPullSockets; i++)
            receivers[i].join();

        System.out.println(globalCounter.get());
        assert globalCounter.get() == nrMsgs;
    }
}
