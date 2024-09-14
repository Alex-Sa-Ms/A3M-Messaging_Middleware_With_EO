package pt.uminho.di.a3m.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.core.exceptions.NoLinksException;
import pt.uminho.di.a3m.core.options.GenericOptionHandler;
import pt.uminho.di.a3m.poller.PollEvent;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.poller.Poller;
import pt.uminho.di.a3m.sockets.DummySocket;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

class SocketTest {
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
            sockets[i] = socketManager.startSocket("Socket" + i, dummyProtocol.id(), DummySocket.class);
            dispatcher.registerSocket(sockets[i]);
        }
    }

    @Test
    void errorTests() {
        Exception e = new LinkClosedException();
        sockets[0].setError(e);
        assert sockets[0].getState() == SocketState.ERROR;
        assert Objects.equals(sockets[0].getError(),e);
    }

    @Test
    void optionsTests() {
        // get existing options and ensure they have expected values
        int capacity = sockets[0].getOption("capacity", Integer.class);
        Object[] options = sockets[0].getOptions(List.of("batchSizePercentage","maxLinks"));
        assert options[0] != null && options[0] instanceof Float;
        assert options[1] != null && options[1] instanceof Integer;

        // check that setting values works
        int newCapacity = capacity / 2;
        sockets[0].setOption("capacity", newCapacity);
        assert sockets[0].getOption("capacity", Integer.class) == newCapacity;

        // attempt to get non-existing options
        try {
            Boolean option1 = sockets[0].getOption("option1", Boolean.class);
            assert false;
        } catch (IllegalArgumentException ignored) {}
        options = sockets[0].getOptions(List.of("option2", "option3"));
        assert options[0] == null;
        assert options[1] == null;

        // attempt to set values to options that do not exist, and check that it is not possible
        try{
            sockets[0].setOption("option1", true);
            assert false;
        }catch (IllegalArgumentException ignored){}
        sockets[0].setOptions(List.of(new Socket.OptionEntry("option2", 2),
                                      new Socket.OptionEntry("option3", 3f)));
        options = sockets[0].getOptions(List.of("option2", "option3"));
        assert options[0] == null;
        assert options[1] == null;

        // register new options
        boolean option1 = true;
        int option2 = 10;
        float option3 = 1.5f;
        sockets[0].registerOption("option1", new GenericOptionHandler<>(option1, Boolean.class));
        sockets[0].registerOption("option2", new GenericOptionHandler<>(option2, Integer.class));
        sockets[0].registerOption("option3", new GenericOptionHandler<>(option3, Float.class));

        // assert they are now retrievable and modifiable
        assert sockets[0].getOption("option1", Boolean.class) == option1;
        options = sockets[0].getOptions(List.of("option2", "option3"));
        assert (int) options[0] == option2;
        assert (float) options[1] == option3;

        sockets[0].setOption("option1", !option1);
        assert sockets[0].getOption("option1", Boolean.class) == !option1;;
        sockets[0].setOptions(List.of(new Socket.OptionEntry("option2", option2 / 2),
                                      new Socket.OptionEntry("option3", option3 / 2)));
        options = sockets[0].getOptions(List.of("option2", "option3"));
        assert (int) options[0] == option2 / 2;
        assert (float) options[1] == option3 / 2;
    }

    @Test
    void linkOperations() throws InterruptedException {
        // assert sockets are not linked to any socket
        for (int i = 0; i < nrSockets; i++)
            for (int j = 0; j < nrSockets; j++)
                assert !sockets[i].isLinked(sids[j]);
        assert sockets[0].countEstablishedLinks() == 0;
        assert sockets[0].countLinks() == 0;

        // link socket0 to socket1
        sockets[0].link(sids[1]);
        assert sockets[0].countEstablishedLinks() >= 0;
        assert sockets[0].countLinks() == 1;
        waitUntil(() -> sockets[1].isLinked(sids[0]));
        assert sockets[0].isLinked(sids[1]);
        assert sockets[0].countEstablishedLinks() == 1;
        assert sockets[1].countEstablishedLinks() == 1;
        // get link sockets
        LinkSocket ls0_1 = sockets[0].getLinkSocket(sids[1]);
        assert ls0_1 != null;
        LinkSocket ls1_0 = sockets[1].getLinkSocket(sids[0]);
        assert ls1_0 != null;

        // link socket0 to socket2
        sockets[0].link(sids[2]);
        waitUntil(() -> sockets[2].isLinked(sids[0]));
        assert sockets[0].isLinked(sids[2]);
        assert sockets[0].countLinks() == 2;
        assert sockets[0].countEstablishedLinks() == 2;
        assert sockets[2].countEstablishedLinks() == 1;

        // unlink socket0 from socket2
        sockets[0].unlink(sids[2]);
        waitUntil(() -> !sockets[0].isLinked(sids[2]));
        assert !sockets[2].isLinked(sids[0]);
        assert sockets[0].countLinks() == 1;
        assert sockets[0].countEstablishedLinks() == 1;
        assert sockets[2].countEstablishedLinks() == 0;

        // unlink socket0 from socket1
        sockets[0].unlink(sids[1]);
        waitUntil(() -> !sockets[0].isLinked(sids[1]));
        assert !sockets[1].isLinked(sids[0]);
        assert sockets[0].countLinks() == 0;
        assert sockets[0].countEstablishedLinks() == 0;
        assert sockets[1].countEstablishedLinks() == 0;
    }

    @Test
    void waitForSpecificLinkEstablishment() throws InterruptedException {
        // set delays
        dispatcher.setRandomDelay(true);

        AtomicInteger wokeUp = new AtomicInteger(0);
        int nrWaiters = 5;
        Thread[] waiters = new Thread[nrWaiters];
        for (int i = 0; i < nrWaiters; i++) {
            waiters[i] = new Thread(()->{
                try {
                    // wait for socket0 to establish link with socket1
                    int ret;
                    while (((ret = sockets[0].waitForLinkEstablishment(sids[1], null)) & PollFlags.POLLHUP) != 0);
                    if((ret & PollFlags.POLLINOUT_BITS) != 0)
                        wokeUp.incrementAndGet();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            waiters[i].start();
        }

        // link socket0 to socket1
        sockets[0].link(sids[1]);

        for (int i = 0; i < nrWaiters; i++)
            waiters[i].join();

        assert wokeUp.get() == nrWaiters;

        // assert that non-blocking and blocking with timeout operations
        // return immediately with POLLHUP since there isn't a link
        // (regardless of the state) with the intended socket
        int ret = sockets[0].waitForLinkEstablishment(sids[2], 0L);
        assert ret == PollFlags.POLLHUP;
        ret = sockets[0].waitForLinkEstablishment(sids[2], 20L);
        assert ret == PollFlags.POLLHUP;
    }

    @Test
    void waitForLinkClosure() throws InterruptedException {
        // link socket0 to socket1
        sockets[0].link(sids[1]);
        waitUntil(() -> sockets[1].isLinked(sids[0]));
        assert sockets[0].isLinked(sids[1]);

        // start link closure waiters
        AtomicInteger wokeUp = new AtomicInteger(0);
        int nrWaiters = 5;
        Thread[] waiters = new Thread[nrWaiters];
        for (int i = 0; i < nrWaiters; i++) {
            waiters[i] = new Thread(()->{
                try {
                    // wait for socket0 and socket1 to unlink
                    while ((sockets[0].waitForLinkClosure(sids[1], null) & PollFlags.POLLHUP) == 0);
                    wokeUp.incrementAndGet();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            waiters[i].start();
        }

        // set delays
        dispatcher.setRandomDelay(true);

        // set unlink process in motion
        sockets[1].unlink(sids[0]);

        for (int i = 0; i < nrWaiters; i++)
            waiters[i].join();

        assert wokeUp.get() == nrWaiters;

        // assert that non-blocking and blocking with timeout operations
        // return immediately with POLLHUP since there isn't a link
        // (regardless of the state) with the intended socket
        int ret = sockets[0].waitForLinkClosure(sids[2], 0L);
        assert ret == PollFlags.POLLHUP;
        ret = sockets[0].waitForLinkClosure(sids[2], 20L);
        assert ret == PollFlags.POLLHUP;
    }

    @Test
    void waitForAnyLinkEstablishment() throws InterruptedException {
        dispatcher.setRandomDelay(true);

        // assert that non-blocking and blocking with timeout operations
        // return null due to the lack of existence of a link (regardless of the state)
        int ret = sockets[0].waitForLinkEstablishment(sids[2], 0L);
        assert ret == PollFlags.POLLHUP;
        ret = sockets[0].waitForLinkEstablishment(sids[2], 20L);
        assert ret == PollFlags.POLLHUP;

        // assert that a blocking operation without timeout, throws
        // NoLinksException when the "notify if none" flag is set and
        // there aren't any links.
        try{
            sockets[0].waitForAnyLinkEstablishment(null, true);
            assert false;
        }catch (NoLinksException ignored){}

        AtomicInteger wokeUp = new AtomicInteger(0);
        int nrWaiters = 5;
        Thread[] waiters = new Thread[nrWaiters];
        for (int i = 0; i < nrWaiters; i++) {
            waiters[i] = new Thread(()->{
                try {
                    // wait for socket0 to establish link with socket1
                    SocketIdentifier sid = sockets[0].waitForAnyLinkEstablishment(null, false);
                    assert sid != null;
                    wokeUp.incrementAndGet();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            waiters[i].start();
        }

        // link socket0 to socket1
        sockets[0].link(sids[1]);

        for (int i = 0; i < nrWaiters; i++)
            waiters[i].join();

        assert wokeUp.get() == nrWaiters;
    }

    @Test
    void waitForAnyLinkEstablishmentWithNotifyIfNoneFlag() throws InterruptedException {
        // make socket1 reject incoming requests
        sockets[1].setOption("allowIncomingLinkRequests", false);
        // set dispatcher delays to allow time between the refusal of the link request
        // and the start of the waiting for link any link establishment operation
        dispatcher.setDelays(20L, 25L);
        // send link request to socket1
        sockets[0].link(sids[1]);
        assert sockets[0].countLinks() == 1;
        try{
            sockets[0].waitForAnyLinkEstablishment(null, true);
            assert false;
        }catch (NoLinksException ignored){}
    }

    @Test
    void close() throws InterruptedException {
        // Don't forget to use poll() to check that POLLHUP is received
        // and POLLFREE for poller instances

        AtomicInteger pollhupSignaled = new AtomicInteger(0);

        // create thread to wait for POLLHUP without a poller instance
        Thread waiter = new Thread(() -> {
            try {
                int events = sockets[0].poll(PollFlags.POLLHUP, null);
                assert (events & PollFlags.POLLHUP) != 0;
                pollhupSignaled.incrementAndGet();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        waiter.start();

        // create thread to wait for POLLHUP with a poller instance
        Thread pollerWaiter = new Thread(() -> {
            Poller poller = Poller.create();
            sockets[0].addToPoller(poller, PollFlags.POLLHUP);
            try {
                List<PollEvent<Object>> eventList = poller.await(1, null);
                assert (eventList != null && eventList.size() == 1);
                int events = eventList.getFirst().events;
                assert (events & PollFlags.POLLHUP) != 0;
                pollhupSignaled.incrementAndGet();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        pollerWaiter.start();

        // link socket0 to all sockets
        for (int i = 1; i < nrSockets; i++) {
            sockets[0].link(sids[i]);
            int finalI = i;
            waitUntil(() -> sockets[finalI].isLinked(sids[0]));
            assert sockets[0].isLinked(sids[i]);
        }

        // close socket
        sockets[0].close();

        // assert sockets are unlinked
        for (int i = 1; i < nrSockets; i++){
            assert !sockets[0].isLinked(sids[i]);
            assert !sockets[i].isLinked(sids[0]);
        }

        // wait for waiters
        waiter.join();
        pollerWaiter.join();

        // assert both waiters woke up with a POLLHUP event
        assert pollhupSignaled.get() == 2;

        // assert socket0's link queue is empty, meaning
        // POLLFREE was delivered to the poller instance,
        // and it removed itself from the socket0's queue
        assert sockets[0].getWaitQueue().isEmpty();
    }

    @Test
    void asyncClose() throws InterruptedException {
        // link socket0 to all sockets
        for (int i = 1; i < nrSockets; i++) {
            sockets[0].link(sids[i]);
            int finalI = i;
            waitUntil(() -> sockets[finalI].isLinked(sids[0]));
            assert sockets[0].isLinked(sids[i]);
        }
        // close socket
        sockets[0].close(0L);
        // wait for closure
        waitUntil(() -> sockets[0].getState() == SocketState.CLOSED);
        // assert sockets are unlinked
        for (int i = 1; i < nrSockets; i++){
            assert !sockets[0].isLinked(sids[i]);
            assert !sockets[i].isLinked(sids[0]);
        }
    }
}