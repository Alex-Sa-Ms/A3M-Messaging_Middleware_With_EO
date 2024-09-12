package pt.uminho.di.a3m.sockets.configurable_socket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.core.*;
import pt.uminho.di.a3m.core.exceptions.NoLinksException;
import pt.uminho.di.a3m.poller.PollFlags;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

class ConfigurableSocketTest {
    String nodeId = "Node";
    int nrSockets = 3;
    SocketIdentifier[] sids = new SocketIdentifier[nrSockets];
    ConfigurableSocket[] sockets = new ConfigurableSocket[nrSockets];
    SocketTestingUtilities.DirectMessageDispatcher dispatcher = new SocketTestingUtilities.DirectMessageDispatcher();

    private void waitUntil(Supplier<Boolean> predicate) throws InterruptedException {
        while (!predicate.get())
            Thread.sleep(5);
    }
    
    @BeforeEach
    void initSocketsAndLinkManagers(){
        SocketManager socketManager = SocketTestingUtilities.createSocketManager(nodeId, dispatcher);
        socketManager.registerProducer(ConfigurableSocket::new);
        for (int i = 0; i < nrSockets; i++) {
            //sids[i] = new SocketIdentifier("Node" + i, "Socket" + i);
            //sockets[i] = SimpleSocket.createSocket(sids[i]);
            //((Socket) sockets[i]).setCoreComponents(dispatcher, new SocketMananerImpl("Node" + i, dispatcher));
            sids[i] = new SocketIdentifier(nodeId, "Socket" + i);
            sockets[i] = socketManager.createSocket("Socket" + i, ConfigurableSocket.protocol.id(), ConfigurableSocket.class);
            dispatcher.registerSocket(sockets[i]);
        }
    }

    void linkSockets(int i, int j, boolean wait){
        sockets[i].link(sids[j]);
        if(wait) {
            try {
                waitUntil(() -> sockets[j].isLinked(sids[i]));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    void linkEverySocket(boolean wait){
        for (int i = 0; i < nrSockets; i++) {
            for (int j = i + 1; j < nrSockets; j++) {
                linkSockets(i, j, wait);
            }
        }
    }

    void unlinkSockets(int i, int j, boolean wait){
        sockets[i].unlink(sids[j]);
        if(wait) {
            try {
                waitUntil(() -> !sockets[i].isLinked(sids[j]));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    void unlinkEverySocket(boolean wait){
        for (int i = 0; i < nrSockets; i++) {
            for (int j = i + 1; j < nrSockets; j++) {
                unlinkSockets(i, j, wait);
            }
        }
    }

    void assertBasicSocketInformation(LinkSocket linkSocket, int i, int j, LinkState linkState){
        assert linkSocket != null;
        assert linkSocket.getOwnerId().equals(sids[i]);
        assert linkSocket.getPeerId().equals(sids[j]);
        assert linkSocket.getPeerProtocolId() == ConfigurableSocket.protocol.id();
        assert linkSocket.getState() == linkState;
        assert linkSocket.getCapacity() == sockets[i].getOption("capacity", Integer.class);
        assert linkSocket.getOutgoingCredits() == sockets[j].getOption("capacity", Integer.class);;
    }

    @Test
    void checkBasicLinkSocketInformation() throws InterruptedException {
        // set every socket's capacity to their own index
        for (int i = 0; i < nrSockets; i++) {
            sockets[i].setOption("capacity", i);
        }
        // link every socket
        linkEverySocket(true);
        // assert every socket's basic information
        LinkSocket linkSocketI_J, linkSocketJ_I;
        for (int i = 0; i < nrSockets; i++) {
            for (int j = i + 1; j < nrSockets; j++) {
                linkSocketI_J = sockets[i].linkSocket(sids[j]);
                assertBasicSocketInformation(linkSocketI_J, i, j, LinkState.ESTABLISHED);
                linkSocketJ_I = sockets[j].linkSocket(sids[i]);
                assertBasicSocketInformation(linkSocketJ_I, j, i, LinkState.ESTABLISHED);
                // unlink sockets i and j and wait until the unlink process is finished
                unlinkSockets(i,j,true);
                // assert basic properties again
                assert sockets[i].linkSocket(sids[j]) == null;
                assertBasicSocketInformation(linkSocketI_J, i, j, LinkState.CLOSED);
                assert sockets[j].linkSocket(sids[i]) == null;
                assertBasicSocketInformation(linkSocketJ_I, j, i, LinkState.CLOSED);
            }
        }
    }

    @Test
    void poll() throws InterruptedException {
        // assert socket0 and socket1 do not have any ready events before linking
        int events = sockets[0].poll(PollFlags.POLLALL, 0L);
        assert events == 0;
        events = sockets[1].poll(PollFlags.POLLALL, 0L);
        assert events == 0;
        // link socket0 to socket1
        linkSockets(0, 1, true);
        // assert socket0 and socket1 can send because they have outgoing credits
        // but cannot receive because a message has not been received yet
        int interest = PollFlags.POLLIN | PollFlags.POLLOUT;
        events = sockets[0].poll(interest, null);
        assert (events & PollFlags.POLLIN) == 0;
        assert (events & PollFlags.POLLOUT) != 0;
        events = sockets[1].poll(interest, null);
        assert (events & PollFlags.POLLIN) == 0;
        assert (events & PollFlags.POLLOUT) != 0;
        // make them exchange messages
        String msgPrefix = "I'm socket";
        for (int i = 0; i < 2; i++)
            sockets[i].send((msgPrefix + i).getBytes(),0L,true);
        Thread.sleep(10L);
        // assert poll shows they can receive and send messages
        events = sockets[0].poll(interest, null);
        assert (events & PollFlags.POLLIN) != 0;
        assert (events & PollFlags.POLLOUT) != 0;
        events = sockets[1].poll(interest, null);
        assert (events & PollFlags.POLLIN) != 0;
        assert (events & PollFlags.POLLOUT) != 0;
        // assert socket0 can receive socket1's message
        byte[] msgArr = sockets[0].receive(null, true);
        String msg = String.valueOf(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(msgArr)));
        System.out.println(msg);
        assert msg.equals(msgPrefix + 1);
        // assert socket1 can receive socket0's message
        msgArr = sockets[1].receive(null, true);
        msg = String.valueOf(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(msgArr)));
        System.out.println(msg);
        assert msg.equals(msgPrefix + 0);
    }

    @Test
    void testOrderedReceive() throws InterruptedException {
        // activate random delay
        dispatcher.setRandomDelay(true);
        // link socket0 to socket1
        linkSockets(0,1,true);
        // make socket0 send a sequence of messages and assert they arrive in order
        int N = sockets[1].getOption("capacity", Integer.class);
        // send string messages with 0 until N - 1
        for (int i = 0; i < N; i++)
            sockets[0].send(String.valueOf(i).getBytes(), null, true);
        byte[] arrMsg;
        // assert messages are received in the order they were sent
        for (int i = 0; i < N; i++) {
            arrMsg = sockets[1].receive(null, true);
            String msg = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(arrMsg)).toString();
            assert msg.equals(String.valueOf(i));
        }
    }

    @Test
    void testOrderedReceiveMultipleReceivers() throws InterruptedException {
        int N = 1000;
        AtomicInteger counter = new AtomicInteger(0);
        Thread[] threads = new Thread[nrSockets];
        // activate random delay
        dispatcher.setRandomDelay(true);

        // link socket 0 to remaining sockets and create listening thread
        for (int i = 1; i < nrSockets; i++) {
            linkSockets(0, i, true);
            int finalI = i;
            threads[i] = new Thread(() -> {
                byte[] arrMsg;
                int last = -1;
                // loop while all messages sent by socket0
                // have not been received
                try {
                    while (counter.get() < N) {
                        // assert that new messages contain a value higher
                        // than all messages previously received.
                        arrMsg = sockets[finalI].receive(50L, true);
                        if (arrMsg != null) {
                            String msg = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(arrMsg)).toString();
                            int parsed = Integer.parseInt(msg);
                            assert parsed > last;
                            last = parsed;
                            counter.incrementAndGet();
                        }
                    }
                }catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            threads[i].start();
        }

        // send string messages with 0 until N - 1
        for (int i = 0; i < N; i++)
            sockets[0].send(String.valueOf(i).getBytes(), null, true);

        // wait for all threads to finish
        for (int i = 1; i < nrSockets; i++) {
            threads[i].join();
        }

        sockets[0].close();
    }

    @Test
    void receiveWithNotifyIfNone() throws InterruptedException {
        // check that the exception is thrown immediately, when
        // there aren't any links.
        try{
            sockets[0].receive(null, true);
            assert false;
        }catch (NoLinksException ignored){}

        // set message delays to around 20ms, enabling the next
        // receive call to not return immediately regardless of
        // a link not being established
        dispatcher.setDelays(20L, 25L);

        // make socket1 not allow incoming link requests so that
        // the no links exception is thrown when the request is received
        // and the link is closed.
        sockets[1].setOption("allowIncomingLinkRequests", false);
        // start counting time to verify the exception was
        // not thrown immediately.
        long start = System.currentTimeMillis();
        try{
            sockets[0].link(sids[1]);
            sockets[0].receive(null, true);
        }catch (NoLinksException ignored){
            // assert the time was at least 20 ms
            assert System.currentTimeMillis() - start >= 20L;
        }

        // allow socket1 to accept incoming link requests
        sockets[1].setOption("allowIncomingLinkRequests", true);

        // create thread to wait for the link to be established,
        // and send a message after the establishment.
        new Thread(() -> {
            try {
                waitUntil(() -> sockets[1].isLinked(sids[0]));
                sockets[1].send("Hello".getBytes(), 0L, false);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();

        try{
            // check that the exception is not thrown
            // because a link although not established,
            // may be established in the future.
            sockets[0].link(sids[1]);
            assert !sockets[0].isLinked(sids[1]);
            byte[] msg = sockets[0].receive(null, true);
            assert msg != null;
        }catch (NoLinksException nle){
            assert false;
        }
    }

    @Test
    void sendWithNotifyIfNone() throws InterruptedException {
        // check that the exception is thrown immediately, when
        // there aren't any links.
        try{
            sockets[0].send("Hello".getBytes(), null, true);
            assert false;
        }catch (NoLinksException ignored){}

        // set message delays to around 20ms, enabling the next
        // send() call to not return immediately regardless of
        // a link not being established
        dispatcher.setDelays(20L, 25L);

        // make socket1 not allow incoming link requests so that
        // the no links exception is thrown when the request is received
        // and the link is closed.
        sockets[1].setOption("allowIncomingLinkRequests", false);
        // start counting time to verify the exception was
        // not thrown immediately.
        long start = System.currentTimeMillis();
        try{
            sockets[0].link(sids[1]);
            sockets[0].send("Hello".getBytes(), null, true);
        }catch (NoLinksException ignored){
            // assert the time was at least 20 ms
            assert System.currentTimeMillis() - start >= 20L;
        }

        // allow socket1 to accept incoming link requests
        sockets[1].setOption("allowIncomingLinkRequests", true);

        // create thread to wait for the link to be established,
        // and the reception of a message after the establishment.
        AtomicBoolean received = new AtomicBoolean(false);
        Thread receiver = new Thread(() -> {
            try {
                waitUntil(() -> sockets[1].isLinked(sids[0]));
                if(sockets[1].receive(null, false) != null)
                    received.set(true);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        receiver.start();

        try{
            // check that the exception is not thrown
            // because a link although not established,
            // may be established in the future.
            sockets[0].link(sids[1]);
            assert !sockets[0].isLinked(sids[1]);
            sockets[0].send("Hello".getBytes(), null, true);
            receiver.join();
            assert received.get();
        }catch (NoLinksException nle){
            assert false;
        }
    }

}