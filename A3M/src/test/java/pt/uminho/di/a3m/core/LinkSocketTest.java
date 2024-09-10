package pt.uminho.di.a3m.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.auxiliary.Debugging;
import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.core.flowcontrol.InFlowControlState;
import pt.uminho.di.a3m.core.messaging.Msg;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.core.messaging.payloads.BytePayload;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.poller.PollTable;
import pt.uminho.di.a3m.poller.Poller;
import pt.uminho.di.a3m.sockets.DummySocket;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

class LinkSocketTest {
    String nodeId = "Node";
    Protocol dummyProtocol = new Protocol("Dummy".hashCode(), "Dummy");
    int nrSockets = 3;
    SocketIdentifier[] sids = new SocketIdentifier[nrSockets];
    DummySocket[] sockets = new DummySocket[nrSockets];
    SocketTestingUtilities.DirectMessageDispatcher dispatcher = new SocketTestingUtilities.DirectMessageDispatcher();

    private void waitUntil(Supplier<Boolean> predicate) throws InterruptedException {
        while (!predicate.get())
            Thread.sleep(5);
    }

    @BeforeEach
    void initSocketsAndLinkManagers(){
        SocketManager socketManager = new SocketMananerImpl(nodeId, dispatcher);
        socketManager.registerProducer(sid -> new DummySocket(sid, dummyProtocol));
        for (int i = 0; i < nrSockets; i++) {
            sids[i] = new SocketIdentifier(nodeId, "Socket" + i);
            sockets[i] = socketManager.createSocket("Socket" + i, dummyProtocol.id(), DummySocket.class);
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
        assert linkSocket.getPeerProtocolId() == dummyProtocol.id();
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

    /**
     * @param i source
     * @param j destination
     * @return retrives link socket that goes from source socket (i)
     * to destination socket (j). Or, null if the link socket does not exist.
     */
    private LinkSocket getLinkSocket(int i, int j){
        return sockets[i].getLinkSocket(sids[j]);
    }


    @Test
    void poll() throws InterruptedException {
        int events;

        // link socket0 to socket1
        linkSockets(0, 1, true);
        // assert socket0 and socket1 can send because they have outgoing credits
        // but cannot receive because a message has not been received yet
        LinkSocket ls0_1 = getLinkSocket(0, 1),
                   ls1_0 = getLinkSocket(1, 0);
        assert ls0_1 != null;
        assert ls1_0 != null;

        int interest = PollFlags.POLLIN | PollFlags.POLLOUT;
        events = Poller.poll(ls0_1, interest, null);
        assert (events & PollFlags.POLLIN) == 0;
        assert (events & PollFlags.POLLOUT) != 0;
        events = Poller.poll(ls1_0, interest, null);
        assert (events & PollFlags.POLLIN) == 0;
        assert (events & PollFlags.POLLOUT) != 0;

        // make them exchange messages
        String msg0_1 = "I'm socket 0!";
        ls0_1.send(new BytePayload(MsgType.DATA, msg0_1.getBytes()), null);
        String msg1_0 = "I'm socket 1!";
        ls1_0.send(new BytePayload(MsgType.DATA, msg1_0.getBytes()), null);

        Thread.sleep(20L);

        // assert poll shows they can receive and send messages
        events = Poller.poll(ls0_1, interest, null);
        assert (events & PollFlags.POLLIN) != 0;
        assert (events & PollFlags.POLLOUT) != 0;
        events = Poller.poll(ls1_0, interest, null);
        assert (events & PollFlags.POLLIN) != 0;
        assert (events & PollFlags.POLLOUT) != 0;

        // assert socket0 can receive socket1's message
        SocketMsg sockMsg = ls0_1.receive(null);
        assert Msg.isType(sockMsg, MsgType.DATA);
        String msg = String.valueOf(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(sockMsg.getPayload())));
        System.out.println(msg);
        assert msg.equals(msg1_0);
        // assert socket1 can receive socket0's message
        sockMsg = ls1_0.receive(null);
        assert Msg.isType(sockMsg, MsgType.DATA);
        msg = String.valueOf(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(sockMsg.getPayload())));
        System.out.println(msg);
        assert msg.equals(msg0_1);

        // close
        unlinkSockets(0,1,true);
        PollTable pt = new PollTable(PollFlags.POLLHUP, null, null);
        assert (ls0_1.poll(pt) & PollFlags.POLLHUP) != 0;
        assert (ls1_0.poll(pt) & PollFlags.POLLHUP) != 0;
    }

    @Test
    void receiveWithDeadline() throws InterruptedException {
        // link socket0 to socket1
        linkSockets(0, 1, true);
        LinkSocket ls0_1 = getLinkSocket(0, 1);
        LinkSocket ls1_0 = getLinkSocket(1, 0);
        // do non-blocking receive
        SocketMsg msg = ls0_1.receive(0L);
        assert msg == null;
        // send message for non-blocking receive to not fail
        ls1_0.send(new BytePayload(MsgType.DATA, null), 0L);
        while (!ls0_1.hasIncomingMessages())
            Thread.sleep(5L);
        msg = ls0_1.receive(0L);
        assert msg != null;
        // do blocking receive with deadline
        msg = ls0_1.receive(Timeout.calculateEndTime(10L));
        assert msg == null;
        // send message for blocking receive with deadline to not fail
        ls1_0.send(new BytePayload(MsgType.DATA, null), 0L);
        msg = ls0_1.receive(Timeout.calculateEndTime(1000L));
        assert msg != null;
    }

    @Test
    void concurrentReceive() throws InterruptedException {
        // link socket0 to socket1
        linkSockets(0, 1, true);
        LinkSocket ls0_1 = getLinkSocket(0, 1),
                   ls1_0 = getLinkSocket(1, 0);

        final int nrReceivers = 3;
        final int nrMsgs = 1000;
        Thread[] receivers = new Thread[nrReceivers];
        int[] counters = new int[nrReceivers];
        AtomicInteger counter = new AtomicInteger(0);
        for (int i = 0; i < nrReceivers; i++) {
            int finalI = i;
            receivers[i] = new Thread(() -> {
                int ownCounter = 0;
                SocketMsg msg;
                try {
                    while (counter.get() != nrMsgs) {
                        msg = ls1_0.receive(null);
                        if(msg != null) {
                            counter.incrementAndGet();
                            ownCounter++;
                        }
                    }
                    ls1_0.unlink();
                } catch (LinkClosedException ignored){
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    counters[finalI] = ownCounter;
                }
            });
            receivers[i].start();
        }

        // send messages
        for (int i = 0; i < nrMsgs; i++) {
            Payload payload =
                    new BytePayload(MsgType.DATA, String.valueOf(i).getBytes());
            ls0_1.send(payload, null);
        }

        // wait for receivers
        for (int i = 0; i < nrReceivers; i++) {
            receivers[i].join();
        }

        // assert sum of counters
        assert Arrays.stream(counters).sum() == nrMsgs;
        for (int i = 0; i < nrReceivers; i++) {
            System.out.println(i + " counter = " + counters[i]);
        }
    }

    @Test
    void sendWithDeadline() throws InterruptedException {
        // change socket1's initial capacity to 0
        sockets[1].setOption("capacity", 0);
        // link socket0 to socket1
        linkSockets(0, 1, true);
        LinkSocket ls0_1 = getLinkSocket(0, 1);
        LinkSocket ls1_0 = getLinkSocket(1, 0);
        // do non-blocking send
        Payload payload = new BytePayload(MsgType.DATA, null);
        boolean sent = ls0_1.send(payload, 0L);
        assert !sent;
        // increment link's capacity message for non-blocking send to not fail
        ls1_0.setCapacity(1);
        while (!ls0_1.hasOutgoingCredits())
            Thread.sleep(5L);
        sent = ls0_1.send(payload, 0L);
        assert sent;
        // do blocking send with deadline
        sent = ls0_1.send(payload, Timeout.calculateEndTime(10L));
        assert !sent;
        // increment link's capacity message for blocking send with timeout to not fail
        ls1_0.adjustCapacity(1);
        sent = ls0_1.send(payload, Timeout.calculateEndTime(1000L));
        assert sent;
    }

    @Test
    void concurrentSend() throws InterruptedException {
        // link socket0 to socket1
        linkSockets(0, 1, true);
        LinkSocket ls0_1 = getLinkSocket(0, 1),
                   ls1_0 = getLinkSocket(1, 0);

        final int nrSenders = 3;
        final int nrMsgs = 1000;
        Thread[] senders = new Thread[nrSenders];
        int[] counters = new int[nrSenders];
        AtomicInteger counter = new AtomicInteger(0);
        for (int i = 0; i < nrSenders; i++) {
            int finalI = i;
            senders[i] = new Thread(() -> {
                int ownCounter = 0;
                SocketMsg msg;
                try {
                    int next;
                    Payload payload;
                    while ((next = counter.getAndIncrement()) < nrMsgs) {
                        payload = new BytePayload(MsgType.DATA, String.valueOf(next).getBytes());
                        ls1_0.send(payload, null);
                        Debugging.printlnOrdered(finalI + ": sent " + next);
                        ownCounter++;
                    }
                }catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    counters[finalI] = ownCounter;
                }
            });
            senders[i].start();
        }

        // receive messages
        for (int i = 0; i < nrMsgs; i++) {
            ls0_1.receive(null);
        }

        // wait for senders
        for (int i = 0; i < nrSenders; i++) {
            senders[i].join();
        }

        assert !ls0_1.hasIncomingMessages();

        // assert sum of counters
        assert Arrays.stream(counters).sum() == nrMsgs;
        for (int i = 0; i < nrSenders; i++) {
            System.out.println(i + " counter = " + counters[i]);
        }
    }

    @Test
    void unlink() throws InterruptedException {
        linkSockets(0, 1, true);
        LinkSocket ls0_1 = getLinkSocket(0, 1),
                   ls1_0 = getLinkSocket(1, 0);
        ls0_1.unlink();
        ls1_0.unlink();
        waitUntil(() -> ls0_1.getState() == LinkState.CLOSED);
        waitUntil(() -> ls1_0.getState() == LinkState.CLOSED);
        assert ls0_1.getState() == LinkState.CLOSED;
        assert ls1_0.getState() == LinkState.CLOSED;
    }

    @Test
    void checkOutgoingCredits() {
        // set socket0's capacity to 0 credits
        sockets[0].setOption("capacity", 0);
        // set socket1's capacity to 1 credit
        sockets[1].setOption("capacity", 1);
        linkSockets(0, 1, true);
        LinkSocket ls0_1 = getLinkSocket(0, 1);
        LinkSocket ls1_0 = getLinkSocket(1, 0);
        // assert that link socket from socket0 to
        // socket1 has 1 outgoing credit
        assert ls0_1.hasOutgoingCredits();
        assert ls0_1.getOutgoingCredits() == 1;
        // assert that link socket from socket1 to
        // socket0 does not have outgoing credits
        assert !ls1_0.hasOutgoingCredits();
        assert ls1_0.getOutgoingCredits() == 0;
    }

    @Test
    void checkIncomingMessages() throws InterruptedException {
        linkSockets(0, 1, true);
        LinkSocket ls0_1 = getLinkSocket(0, 1);
        LinkSocket ls1_0 = getLinkSocket(1, 0);
        assert !ls0_1.hasIncomingMessages();
        assert ls0_1.countIncomingMessages() == 0;
        assert !ls1_0.hasIncomingMessages();
        assert ls1_0.countIncomingMessages() == 0;

        // send message from socket0 to socket1
        ls0_1.send(new BytePayload(MsgType.DATA, null), null);

        // wait until it arrives
        assert (Poller.poll(ls1_0, PollFlags.POLLIN, null) & PollFlags.POLLIN) != 0;
        assert ls1_0.hasIncomingMessages();
        assert ls1_0.countIncomingMessages() == 1;
    }

    @Test
    void hasAvailableIncomingMessages() throws InterruptedException {
        // make socket1 queue not return ever, just to show that having,
        // messages does not necessarily mean that they are available for receiving
        sockets[1].setInQueueSupplier(() -> new LinkedList<>(){
            // peek() is used to verify availability
            @Override
            public SocketMsg peek() {
                return null;
            }

            // poll() is used to retrieve an available message
            @Override
            public SocketMsg poll() {
                return null;
            }
        });

        // link socket0 to socket1
        linkSockets(0, 1, true);
        LinkSocket ls0_1 = getLinkSocket(0, 1);
        LinkSocket ls1_0 = getLinkSocket(1, 0);

        // check neither have incoming messages queued,
        // and consequently there aren't also available messages.
        assert !ls0_1.hasIncomingMessages();
        assert ls0_1.countIncomingMessages() == 0;
        assert !ls0_1.hasAvailableIncomingMessages();
        assert !ls1_0.hasIncomingMessages();
        assert ls1_0.countIncomingMessages() == 0;
        assert !ls1_0.hasAvailableIncomingMessages();

        // make sockets exchange messages
        ls0_1.send(new BytePayload(MsgType.DATA, null), null);
        ls1_0.send(new BytePayload(MsgType.DATA, null), null);

        // wait until both sockets receive
        waitUntil(ls0_1::hasIncomingMessages);
        waitUntil(ls1_0::hasIncomingMessages);

        // assert the count
        assert ls0_1.countIncomingMessages() == 1;
        assert ls1_0.countIncomingMessages() == 1;

        // assert socket 0 has an available message, so it can be polled,
        // and the next receiving operation will be successful
        assert ls0_1.hasAvailableIncomingMessages();
        assert (Poller.poll(ls0_1, PollFlags.POLLIN, null) & PollFlags.POLLIN) != 0;
        SocketMsg msg = ls0_1.receive(0L);
        assert msg != null;

        // check that message was removed from socket0's incoming queue
        assert !ls0_1.hasIncomingMessages();
        assert ls0_1.countIncomingMessages() == 0;
        assert !ls0_1.hasAvailableIncomingMessages();
        assert (Poller.poll(ls0_1, PollFlags.POLLIN, 0L) & PollFlags.POLLIN) == 0;

        // assert socket 1 does not have an available message, meaning
        // a receiving or polling operation cannot be successful,
        // regardless of the existence of a message in the queue.
        assert !ls1_0.hasAvailableIncomingMessages();
        assert (Poller.poll(ls1_0, PollFlags.POLLIN, 0L) & PollFlags.POLLIN) == 0;
        msg = ls1_0.receive(0L);
        assert msg == null;
    }

    @Test
    void capacityAndBatchRelatedOperations() throws InterruptedException {
        int credits = 100;

        sockets[0].setOption("capacity", credits);
        // set batch size percentage to half the capacity
        sockets[0].setOption("batchSizePercentage", 0.5f);
        linkSockets(0, 1, true);
        LinkSocket ls0_1 = getLinkSocket(0, 1);
        LinkSocket ls1_0 = getLinkSocket(1, 0);

        // assert capacity and batch size percentage equal the default values of the socket
        int capacity = sockets[0].getOption("capacity", Integer.class);
        assert ls0_1.getCapacity() == capacity;
        float batchSizePercentage = sockets[0].getOption("batchSizePercentage", Float.class);
        assert ls0_1.getBatchSizePercentage() == batchSizePercentage;
        assert ls0_1.getBatchSize() == InFlowControlState.calculateBatchSize(capacity, batchSizePercentage);

        // assert outgoing credits of socket1 to send messages to socket0
        // equals the capacity defined by socket0
        assert ls1_0.getOutgoingCredits() == ls0_1.getCapacity();

        // make socket1 waste a quarter the outgoing credits with non-blocking send operations
        boolean sent;
        for (int i = 0; i < credits / 4; i++) {
            sent = ls1_0.send(new BytePayload(MsgType.DATA, null), 0L);
            assert sent;
        }

        // make socket0 set the capacity of the link to 0
        ls0_1.setCapacity(0);

        // wait until socket1's outgoing credits are negative
        // and have an absolute value equal to the amount of messages sent
        waitUntil(() -> ls1_0.getOutgoingCredits() == - credits / 4);

        // make socket0 set the batch size to 5 credits by
        // adjusting the capacity from 0 to 5 credits and setting
        // a batch size percentage of 100%.
        // "batch size = capacity * batch size percentage"
        ls0_1.setCreditsBatchSizePercentage(1f);
        ls0_1.adjustCapacity(5); // 100% of 1 credit equals to a batch size of 1 credit
        assert ls0_1.getBatchSize() == 5;

        // After socket0 drains all received messages, socket1
        // must have 5 positive credits
        SocketMsg msg = null;
        do { msg = ls0_1.receive(0L); }
        while (msg != null);

        // wait for the socket1 to get a positive credit
        waitUntil(ls1_0::hasOutgoingCredits);
        assert ls1_0.getOutgoingCredits() == 5;
    }
}