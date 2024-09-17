package pt.uminho.di.a3m.sockets.request_reply;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.core.A3MMiddleware;
import pt.uminho.di.a3m.core.SocketProducer;
import pt.uminho.di.a3m.core.SocketTestingUtilities;
import pt.uminho.di.a3m.sockets.push_pull.PullSocket;
import pt.uminho.di.a3m.sockets.push_pull.PushSocket;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

public class RequestReplyTests {
    A3MMiddleware middleware;
    int port;
    private final static List<SocketProducer> producerList =
            List.of(ReqSocket::new, RepSocket::new, DealerSocket::new, RouterSocket::new);


    @BeforeEach
    void init() throws SocketException, UnknownHostException {
        var entry = SocketTestingUtilities.createAndStartMiddlewareInstance(producerList);
        port = entry.getKey();
        middleware = entry.getValue();
    }

    @Test
    void reqSocketReceiveBeforeSend() throws InterruptedException {
        // A request socket must not be able
        // to receive a message before sending a request.
        ReqSocket reqSocket = middleware.startSocket("req",ReqSocket.protocol.id(),ReqSocket.class);
        int nrAttempts = 100;
        int exceptions = 0; // nr of exceptions thrown
        for (int i = 0; i < nrAttempts; i++) {
            try {
                reqSocket.receive();
            } catch (IllegalStateException ise) {
                exceptions++;
            }
        }
        assert nrAttempts == exceptions;
    }

    @Test
    void reqSocketConsecutiveSends() throws InterruptedException {
        // A request socket must not be able
        // to send a second request before receiving
        // the reply to the first request.
        ReqSocket reqSocket = middleware.startSocket("req",ReqSocket.protocol.id(),ReqSocket.class);
        RepSocket repSocket0 = middleware.startSocket("rep0",RepSocket.protocol.id(),RepSocket.class);
        RepSocket repSocket1 = middleware.startSocket("rep1",RepSocket.protocol.id(),RepSocket.class);
        reqSocket.link(repSocket0.getId());
        reqSocket.link(repSocket1.getId());

        // wait until the sockets are linked
        while (!repSocket0.isLinked(reqSocket.getId()))
            Thread.onSpinWait();
        while (!repSocket1.isLinked(reqSocket.getId()))
            Thread.onSpinWait();

        final int nrAttempts = 100;
        int sent = 0; // nr of successful sends
        for (int i = 0; i < nrAttempts; i++) {
            if(reqSocket.send("Hello".getBytes(),0L,false))
                sent++;
        }
        // assert the send operation was only successful once
        assert sent == 1;
    }

    @Test
    void repSocketSendBeforeReceive() throws InterruptedException {
        // A replier (rep) socket must not be able
        // to send a message before receiving a request.
        RepSocket repSocket = middleware.startSocket("req",RepSocket.protocol.id(),RepSocket.class);
        int nrAttempts = 100;
        int exceptions = 0; // nr of exceptions thrown
        for (int i = 0; i < nrAttempts; i++) {
            try {
                repSocket.send("Hello".getBytes());
            } catch (IllegalStateException ise) {
                exceptions++;
            }
        }
        assert nrAttempts == exceptions;
    }

    @Test
    void repSocketConsecutiveReceives() throws InterruptedException {
        // A replier socket must not be able
        // to receive a second request before sending
        // the reply to the first request.
        ReqSocket reqSocket0 = middleware.startSocket("req0",ReqSocket.protocol.id(),ReqSocket.class);
        ReqSocket reqSocket1 = middleware.startSocket("req1",ReqSocket.protocol.id(),ReqSocket.class);
        RepSocket repSocket = middleware.startSocket("rep",RepSocket.protocol.id(),RepSocket.class);
        reqSocket0.link(repSocket.getId());
        reqSocket1.link(repSocket.getId());

        // wait until the sockets are linked
        while (!repSocket.isLinked(reqSocket0.getId()))
            Thread.onSpinWait();
        while (!repSocket.isLinked(reqSocket1.getId()))
            Thread.onSpinWait();

        // make request sockets send a request each
        reqSocket0.send("request0".getBytes());
        reqSocket1.send("request1".getBytes());

        // wait for the first request to be received
        byte[] msg = repSocket.receive();
        assert msg != null;

        final int nrAttempts = 100;
        int received = 0; // nr of successful receives
        for (int i = 0; i < nrAttempts; i++) {
            if (repSocket.receive(0L) != null)
                received++;
        }

        // assert receiving was not possible in any attempt
        assert received == 0;

        // assert that after sending a reply, a second request
        // can be received
        repSocket.send("reply".getBytes());
        msg = repSocket.receive();
        assert msg != null;
    }

    /**
     * Tests exchange of requests and replies between
     * a REQ socket and a REP socket.
     */
    @Test
    void synchronousRequestReplyFlow() throws InterruptedException {
        ReqSocket reqSocket = middleware.startSocket("req",ReqSocket.protocol.id(),ReqSocket.class);
        RepSocket repSocket = middleware.startSocket("rep",RepSocket.protocol.id(),RepSocket.class);
        reqSocket.link(repSocket.getId());

        int nrExchanges = 1000;

        Thread requester = new Thread(() -> {
            byte[] msg;
            String strMsg;
            for (int i = 0; i < nrExchanges; i++) {
                try {
                    strMsg = String.valueOf(i);
                    reqSocket.send(strMsg.getBytes());
                    msg = reqSocket.receive();
                    assert strMsg.equals(SocketTestingUtilities.decodeByteArrayToString(msg));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        requester.start();

        Thread replier = new Thread(() -> {
            byte[] msg;
            String strMsg;
            for (int i = 0; i < nrExchanges; i++) {
                try {
                    msg = repSocket.receive();
                    strMsg = SocketTestingUtilities.decodeByteArrayToString(msg);
                    assert String.valueOf(i).equals(strMsg);
                    repSocket.send(strMsg.getBytes());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        replier.start();

        replier.join();
        requester.join();
    }

    @Test
    void isReplySentToLastRequester() throws InterruptedException {
        ReqSocket[] reqSockets = new ReqSocket[2];
        reqSockets[0] = middleware.startSocket("req0",ReqSocket.protocol.id(),ReqSocket.class);
        reqSockets[1] = middleware.startSocket("req1",ReqSocket.protocol.id(),ReqSocket.class);
        RepSocket repSocket = middleware.startSocket("rep",RepSocket.protocol.id(),RepSocket.class);
        reqSockets[0].link(repSocket.getId());
        reqSockets[1].link(repSocket.getId());

        int nrExchanges = 100;

        Thread[] requesters = new Thread[2];
        for (int i = 0; i < 2; i++) {
            int finalI = i;
            requesters[i] = new Thread(() -> {
                byte[] msg;
                // request equals the index of the requester
                String strMsg = String.valueOf(finalI);
                for (int j = 0; j < nrExchanges / 2; j++) {
                    try {
                        reqSockets[finalI].send(strMsg.getBytes());
                        msg = reqSockets[finalI].receive();
                        assert strMsg.equals(SocketTestingUtilities.decodeByteArrayToString(msg));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            requesters[i].start();
        }

        Thread replier = new Thread(() -> {
            byte[] msg;
            for (int i = 0; i < nrExchanges; i++) {
                try {
                    // if the reply is sent to the correct requester,
                    // then the requester will receive its own index.
                    msg = repSocket.receive();
                    repSocket.send(msg);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        replier.start();

        replier.join();
        for (int i = 0; i < 2; i++)
            requesters[i].join();
    }

    @Test
    void replierQueuingReply() throws InterruptedException {
        ReqSocket reqSocket = middleware.startSocket("req", ReqSocket.protocol.id(), ReqSocket.class);
        // make request socket have a capacity of 0, so that the replier
        // cannot send the reply.
        reqSocket.setOption("capacity",0);
        RepSocket repSocket = middleware.startSocket("rep", RepSocket.protocol.id(), RepSocket.class);
        reqSocket.link(repSocket.getId());
        while (!repSocket.isLinked(reqSocket.getId()))
            Thread.onSpinWait();
        // send request
        reqSocket.send("Hello".getBytes());
        // receive the request
        byte[] msg = repSocket.receive();
        assert msg != null;
        // send a reply
        repSocket.send(msg);
        // check that receiving is not possible
        msg = reqSocket.receive(25L); // timeout to wait a bit and check that the message effectively does not arrive
        assert msg == null;
        // make request socket change the capacity so that the replier
        // receives a credit to send the reply
        reqSocket.linkSocket(repSocket.getId()).setCapacity(1);
        // check that the message is finally received
        msg = reqSocket.receive();
        assert msg != null;
    }

    @Test
    void multiHopTest() throws InterruptedException {
        DealerSocket client = middleware.startSocket("client",DealerSocket.protocol.id(),DealerSocket.class);
        RouterSocket routerA = middleware.startSocket("routerA",RouterSocket.protocol.id(),RouterSocket.class);
        DealerSocket toRouterB = middleware.startSocket("toRouterB",DealerSocket.protocol.id(),DealerSocket.class);
        RouterSocket routerB = middleware.startSocket("routerB",RouterSocket.protocol.id(),RouterSocket.class);
        DealerSocket toService = middleware.startSocket("toService",DealerSocket.protocol.id(),DealerSocket.class);
        DealerSocket service = middleware.startSocket("service",DealerSocket.protocol.id(),DealerSocket.class);

        client.link(routerA.getId());
        toRouterB.link(routerB.getId());
        toService.link(service.getId());

        byte[] request = "Hello".getBytes();
        byte[] msg;

        // client sends message
        client.send(request);

        // router A receives and forwards request to router B
        msg = routerA.receive();
        toRouterB.send(msg);

        // router B receives and forwards request to service
        msg = routerB.receive();
        toService.send(msg);

        // service receives the request, sets a new payload and sends the reply back
        byte[] reply = "Hi".getBytes();
        msg = service.receive();
        RRMsg rrMsg = RRMsg.parseFrom(msg);
        assert Arrays.equals(request, rrMsg.getPayload());
        rrMsg.setPayload(reply);
        service.send(rrMsg.toByteArray());

        // router B receives reply and sends it to router A
        msg = toService.receive();
        routerB.send(msg);

        // router A receives reply and sends it to client
        msg = toRouterB.receive();
        routerA.send(msg);

        // client receives message
        msg = client.receive();
        // assert the reply is correct
        assert Arrays.equals(reply, msg);
    }
}