package pt.uminho.di.a3m.core;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.poller.PollTable;
import pt.uminho.di.a3m.poller.Poller;
import pt.uminho.di.a3m.waitqueue.ParkState;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

class LinkSocketTest {
    int nrSockets = 3;
    SocketIdentifier[] sids = new SocketIdentifier[nrSockets];
    SimpleSocket[] sockets = new SimpleSocket[nrSockets];
    LinkSocketTestDispatcher dispatcher = new LinkSocketTestDispatcher();

    private void waitUntil(Supplier<Boolean> predicate) throws InterruptedException {
        while (!predicate.get())
            Thread.sleep(5);
    }

    /**
     * Dispatches messages with random delay if the random delay flag is set.
     */
    private class LinkSocketTestDispatcher implements MessageDispatcher{
        private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
        private final Map<SocketIdentifier,Socket> sockets = new ConcurrentHashMap<>(); // link managers
        private boolean randomDelay = false;
        private final Random random = new Random(2024);
        private final long minDelay = 0L; // min delay to dispatch a message in seconds
        private final long maxDelay = 20L; // max delay (exclusive) to dispatch a message in seconds

        public LinkSocketTestDispatcher() {}
        public LinkSocketTestDispatcher(Socket s) {
            registerSocket(s);
        }

        public void registerSocket(Socket s){
            if(s != null)
                sockets.put(s.getId(), s);
        }

        public void setRandomDelay(boolean randomDelay) {
            this.randomDelay = randomDelay;
        }

        Lock printLock = new ReentrantLock();

        private void println(String s){
            try{
                printLock.lock();
                System.out.println(s);
                System.out.flush();
            } finally {
                printLock.unlock();
            }
        }

        private void _dispatch(SocketMsg msg){
            if(msg != null){
                Socket socket = sockets.get(msg.getDestId());
                if(socket != null) {
                    socket.onIncomingMessage(msg);
                    //try {
                    //    println(LinkManager.linkRelatedMsgToString(msg));
                    //} catch (InvalidProtocolBufferException ignored) {}
                }
            }
        }

        @Override
        public void dispatch(SocketMsg msg) {
            if(randomDelay) {
                long delay = random.nextLong(minDelay, maxDelay);
                scheduler.schedule(() -> _dispatch(msg), delay, TimeUnit.MILLISECONDS);
            }
            else _dispatch(msg);
        }

        @Override
        public AtomicReference<SocketMsg> scheduleDispatch(SocketMsg msg, long dispatchTime) {
            AtomicReference<SocketMsg> ref = new AtomicReference<>(msg);
            long delay = Math.max(0L, dispatchTime - System.currentTimeMillis());
            scheduler.schedule(() -> {
                SocketMsg m = ref.getAndSet(null);
                if(m != null) {
                    this.dispatch(m);
                }
            }, delay, TimeUnit.MILLISECONDS);
            return ref;
        }
    }
    
    @BeforeEach
    void initSocketsAndLinkManagers(){
        for (int i = 0; i < nrSockets; i++) {
            sids[i] = new SocketIdentifier("Node" + i, "Socket" + i);
            sockets[i] = new SimpleSocket(sids[i]);
            sockets[i].setCoreComponents(dispatcher, new SocketMananerImpl("Node" + i, dispatcher));
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

    void assertBasicSocketInformation(LinkSocket linkSocket, int i, int j, Link.LinkState linkState){
        assert linkSocket != null;
        assert linkSocket.getOwnerId().equals(sids[i]);
        assert linkSocket.getPeerId().equals(sids[j]);
        assert linkSocket.getPeerProtocolId() == SimpleSocket.protocol.id();
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
                assertBasicSocketInformation(linkSocketI_J, i, j, Link.LinkState.ESTABLISHED);
                linkSocketJ_I = sockets[j].linkSocket(sids[i]);
                assertBasicSocketInformation(linkSocketJ_I, j, i, Link.LinkState.ESTABLISHED);
                // unlink sockets i and j and wait until the unlink process is finished
                unlinkSockets(i,j,true);
                // assert basic properties again
                assert sockets[i].linkSocket(sids[j]) == null;
                assertBasicSocketInformation(linkSocketI_J, i, j, Link.LinkState.CLOSED);
                assert sockets[j].linkSocket(sids[i]) == null;
                assertBasicSocketInformation(linkSocketJ_I, j, i, Link.LinkState.CLOSED);
            }
        }
    }

    @Test
    void poll() throws InterruptedException {
        // assert socket0 and socket1 do not have any ready events before linking
        int events = sockets[0].poll(null);
        assert events == 0;
        events = sockets[1].poll(null);
        assert events == 0;
        // link socket0 to socket1
        linkSockets(0, 1, true);
        // assert socket0 and socket1 can send because they have outgoing credits
        // but cannot receive because a message has not been received yet
        int interest = PollFlags.POLLIN | PollFlags.POLLOUT;
        events = Poller.poll(sockets[0], interest, null);
        assert (events & PollFlags.POLLIN) == 0;
        assert (events & PollFlags.POLLOUT) != 0;
        events = Poller.poll(sockets[1], interest, null);
        assert (events & PollFlags.POLLIN) == 0;
        assert (events & PollFlags.POLLOUT) != 0;
        // make them exchange messages
        String msgPrefix = "I'm socket";
        for (int i = 0; i < 2; i++)
            sockets[i].send((msgPrefix + i).getBytes(),0L,true);
        // assert poll shows they can receive and send messages
        events = Poller.poll(sockets[0], interest, null);
        assert (events & PollFlags.POLLIN) != 0;
        assert (events & PollFlags.POLLOUT) != 0;
        events = Poller.poll(sockets[1], interest, null);
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

    // TODO - not working for some reason. Seems like there is a deadlock?

    @Test
    void testOrderedReceive() throws InterruptedException {
        // activate random delay
        dispatcher.setRandomDelay(true);
        // link socket0 to socket1
        linkSockets(0,1,true);
        // make socket0 send a sequence of messages and assert they arrive in order
        int N = 50;
        // send string messages with 0 until N - 1
        for (int i = 0; i < N; i++)
            sockets[0].send(String.valueOf(i).getBytes(), null, true);
        byte[] arrMsg;
        // assert messages are received in the order they were sent
        for (int i = 0; i < N; i++) {
            arrMsg = sockets[1].receive(null, true);
            String msg = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(arrMsg)).toString();
            System.out.println("received: " + msg);
            assert msg.equals(String.valueOf(i));
        }
    }

    @Test
    void receive() {
        // TODO - receive()
    }

    @Test
    void send() {
        // TODO - send()
    }

    @Test
    void unlink() {
        // TODO - unlink()
    }

    @Test
    void hasOutgoingCredits() {
        // TODO - hasOutgoingCredits()
    }

    @Test
    void getOutgoingCredits() {
        // TODO - getOutgoingCredits()
    }

    @Test
    void countIncomingMessages() {
        // TODO - countIncomingMessages()
    }

    @Test
    void hasIncomingMessages() {
        // TODO - hasIncomingMessages()
    }

    @Test
    void hasAvailableIncomingMessages() {
        // TODO - hasAvailableIncomingMessages()
    }

    @Test
    void getCapacity() {
        // TODO - getCapacity()
    }

    @Test
    void setCapacity() {
        // TODO - setCapacity()
    }

    @Test
    void adjustCapacity() {
        // TODO - adjustCapacity()
    }

    @Test
    void getBatchSize() {
        // TODO - getBatchSize()
    }

    @Test
    void getBatchSizePercentage() {
        // TODO - getBatchSizePercentage()
    }

    @Test
    void setCreditsBatchSizePercentage() {
        // TODO - setCreditsBatchSizePercentage()
    }
}