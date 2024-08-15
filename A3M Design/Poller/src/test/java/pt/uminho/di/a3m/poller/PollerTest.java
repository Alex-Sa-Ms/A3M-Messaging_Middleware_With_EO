package pt.uminho.di.a3m.poller;

import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.poller.exceptions.PollerClosedException;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PollerTest {

    @Test
    void create() {
        Poller poller = Poller.create();
        assert poller.isEmpty();
        assert !poller.isClosed();
        assert !poller.hasWaiters();
    }

    @Test
    void add() {
        Poller poller = Poller.create();

        // adding null pollable should throw
        // IllegalArgumentException
        try {
            poller.add(null, 0);
            assert false; // should not get here
        } catch (IllegalArgumentException ignored) {}

        // Create a valid pollable
        MockSocket s1 = new MockSocket("1");
        // assert there aren't waiters
        assert s1.getWaitQ().isEmpty();

        // add a valid pollable
        poller.add(s1, 0);
        // assert the pollable has been registered
        assert poller.size() == 1;
        // assert poller is now a waiter of s1
        assert s1.getWaitQ().size() == 1;

        // check that trying to add it again is not possible
        int ret = poller.add(s1, 0);
        assert ret == Poller.PEXIST;

        // Add a second valid pollable
        MockSocket s2 = new MockSocket("2");
        poller.add(s2, PollFlags.POLLIN | PollFlags.POLLOUT);
        // assert the pollable has been registered
        assert poller.size() == 2;
        // assert poller is now a waiter of s2
        assert s2.getWaitQ().size() == 1;

        // Confirm POLLEXCLUSIVE cannot be added with POLLONESHOT
        MockSocket s3 = new MockSocket("3");
        try {
            poller.add(s3, PollFlags.POLLET | PollFlags.POLLEXCLUSIVE | PollFlags.POLLONESHOT);
            assert false; // should not get here
        } catch (IllegalArgumentException ignored) {}

        // Confirm POLLEXCLUSIVE can be added without POLLONESHOT
        poller.add(s3, PollFlags.POLLET | PollFlags.POLLEXCLUSIVE);
        // assert the pollable has been registered
        assert poller.size() == 3;
        // assert poller is now a waiter of s3
        assert s3.getWaitQ().size() == 1;

        // Confirm POLLONESHOT can be added without POLLEXCLUSIVE
        MockSocket s4 = new MockSocket("4");
        poller.add(s4, PollFlags.POLLET | PollFlags.POLLONESHOT);
        // assert the pollable has been registered
        assert poller.size() == 4;
        // assert poller is now a waiter of s4
        assert s4.getWaitQ().size() == 1;
    }

    @Test
    void modify() {
        Poller poller = Poller.create();
        // Adds valid pollable
        MockSocket s1 = new MockSocket("1");
        poller.add(s1, 0);

        // Check that adding any event flag, POLLET and POLLONESHOT
        // when modifying is okay as long as POLLEXCLUSIVE is not involved
        poller.modify(s1, PollFlags.POLLIN | PollFlags.POLLOUT
                | PollFlags.POLLERR | PollFlags.POLLHUP
                | PollFlags.POLLET | PollFlags.POLLONESHOT);

        // Check that removing any event flag, POLLET and POLLONESHOT
        // when modifying is okay as long as POLLEXCLUSIVE is not involved
        poller.modify(s1, 0);

        // Check that modifying the event mask to have the POLLEXCLUSIVE flag
        // is not possible.
        try {
            poller.modify(s1, PollFlags.POLLET | PollFlags.POLLEXCLUSIVE);
            assert false; // IllegalStateException should be thrown
        } catch (IllegalStateException ignored) {}

        // Check that modifying the event mask to remove
        // POLLEXCLUSIVE is not possible.
        MockSocket s2 = new MockSocket("2");
        poller.add(s2, PollFlags.POLLET | PollFlags.POLLEXCLUSIVE);
        try {
            poller.modify(s2, 0);
            assert false; // IllegalStateException should be thrown
        } catch (IllegalStateException ignored) {}
    }

    @Test
    void delete() {
        Poller poller = Poller.create();

        // deleting null pollable should throw
        // IllegalArgumentException
        try {
            poller.delete(null);
            assert false; // should not get here
        } catch (IllegalArgumentException ignored) {}

        // Delete a non registered pollable
        // should return Poller.PNOEXIST
        MockSocket s1 = new MockSocket("1");
        int ret = poller.delete(s1);
        assert ret == Poller.PNOEXIST;

        // add socket to poller
        ret = poller.add(s1, 0);
        assert ret == 0;
        assert poller.size() == 1;
        assert s1.getWaitQ().size() == 1;

        // check that poller is deleted successfully
        // from the poller and that the pollable
        // no longer has the poller registered in its wait queue
        ret = poller.delete(s1);
        assert ret == 0;
        assert poller.isEmpty();
        assert s1.getWaitQ().isEmpty();
    }

    @Test
    void hasWaiters() {
        Poller poller = Poller.create();
        // assert poller does not have waiters
        assert !poller.hasWaiters();

        // create socket with 0 so that immediate send is not allowed.
        MockSocket s1 = new MockSocket("1", 0);
        // assert immediate send is unsuccessful
        assert !s1.trySendMsg("Hello");

        // register socket in poller with POLLOUT as the event of interest
        poller.add(s1, PollFlags.POLLOUT);
        // create thread and verify that it waits and times out because
        // a send event is not received.
        AtomicBoolean timedOut = new AtomicBoolean(false);
        Thread t = new Thread(() -> {
            try {
                // waits 2 seconds until timeout
                List<PollEvent<Object>> rEvents = poller.await(1, 200L);
                // timed out if events were not received
                timedOut.set(rEvents == null);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        t.start();

        // wait until there are waiters.
        // This serves as verification
        while (!poller.hasWaiters())
            Thread.onSpinWait();

        // wait until thread dies
        while (t.isAlive())
            Thread.onSpinWait();

        assert timedOut.get();
    }

    @Test
    void awaitLevelTriggered() {
        Poller poller = Poller.create();
        long timeout = 100L;
        // assert poller does not have waiters
        assert !poller.hasWaiters();

        // create socket with 0 credits
        // so that immediate send is not allowed.
        MockSocket s1 = new MockSocket("1", 0);
        // assert immediate send is unsuccessful
        assert !s1.trySendMsg("Hello");

        // register socket in poller with POLLOUT
        // as the event of interest
        poller.add(s1, PollFlags.POLLOUT);
        // create 2 threads and make them wait for events
        AtomicBoolean[] timedOut = new AtomicBoolean[2];

        for (int i = 0; i < 2; i++) {
            timedOut[i] = new AtomicBoolean(false);
            int finalI = i;
            Runnable task = () -> {
                try {
                    List<PollEvent<Object>> rEvents = poller.await(1, timeout);
                    // set if thread timed out, so that the main thread
                    // can analyse the results
                    timedOut[finalI].set(rEvents == null);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };
            new Thread(task).start();
        }

        // wait until both threads are waiters
        while (poller.getNrWaiters() < 2)
            Thread.onSpinWait();

        long time = System.currentTimeMillis();

        // update credits so that a POLLOUT
        // event is sent
        s1.updateCredits(1);

        // since the socket events were registered
        // as level-triggered, both thread should
        // wake up, i.e., they shouldn't time out
        while (poller.getNrWaiters() > 0)
            Thread.onSpinWait();

        // calculate time between the queuing of both waiters,
        // until the dequeueing, to see that the timeout was
        // not waited.
        time = System.currentTimeMillis() - time;

        // assert that the threads did not time out
        assert time < timeout;
        assert !timedOut[0].get();
        assert !timedOut[1].get();
    }

    @Test
    void awaitEdgeTriggered() {
        Poller poller = Poller.create();
        long timeout = 100L;
        // assert poller does not have waiters
        assert !poller.hasWaiters();

        // create socket with 0 credits
        // so that immediate send is not allowed.
        MockSocket s1 = new MockSocket("1", 0);
        // assert immediate send is unsuccessful
        assert !s1.trySendMsg("Hello");

        // register socket in poller with POLLOUT flag
        // and in edge-trigger mode
        poller.add(s1, PollFlags.POLLOUT | PollFlags.POLLET);
        // create 2 threads to wait for events
        AtomicBoolean[] timedOut = new AtomicBoolean[2];
        Thread[] threads = new Thread[2];

        for (int i = 0; i < 2; i++) {
            timedOut[i] = new AtomicBoolean(false);
            int finalI = i;
            Runnable task = () -> {
                try {
                    List<PollEvent<Object>> rEvents = poller.await(1, timeout);
                    timedOut[finalI].set(rEvents == null);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };
            threads[finalI] = new Thread(task);
            threads[finalI].start();
        }

        // wait until both threads are waiters.
        while (poller.getNrWaiters() < 2)
            Thread.onSpinWait();

        // update credits so that a
        // POLLOUT event is sent
        s1.updateCredits(1);

        // wait until both threads die
        while (threads[0].isAlive() || threads[1].isAlive())
            Thread.onSpinWait();

        // since the events were registered in edge-trigger mode,
        // the first thread will clear the readiness of the socket
        // until the next event, which results in only one of
        // poller waiters being woken up.
        assert (timedOut[0].get() || timedOut[1].get())
                && !(timedOut[0].get() && timedOut[1].get());
    }

    @Test
    void awaitPollOneShot() {
        Poller poller = Poller.create();
        long timeout = 100L;
        // assert poller does not have waiters
        assert !poller.hasWaiters();

        // create socket with 0 credits so
        // that immediate send is not allowed.
        MockSocket s1 = new MockSocket("1", 0);
        // assert immediate send is unsuccessful
        assert !s1.trySendMsg("Hello");

        // register socket in poller with POLLOUT flag,
        // in edge-trigger mode and with POLLONESHOT flag
        // to prevent multiple wake ups.
        int events = PollFlags.POLLOUT | PollFlags.POLLET | PollFlags.POLLONESHOT;
        poller.add(s1, events);
        // create 2 threads to wait for events
        AtomicBoolean[] timedOut = new AtomicBoolean[2];
        Thread[] threads = new Thread[2];

        for (int i = 0; i < 2; i++) {
            timedOut[i] = new AtomicBoolean(false);
            int finalI = i;
            Runnable task = () -> {
                try {
                    List<PollEvent<Object>> rEvents = poller.await(1, timeout);
                    timedOut[finalI].set(rEvents == null);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };
            threads[finalI] = new Thread(task);
            threads[finalI].start();
        }

        // wait until both threads are waiters
        while (poller.getNrWaiters() < 2)
            Thread.onSpinWait();

        // update credits so that a POLLOUT
        // event is sent
        s1.updateCredits(1);

        // wait until one of the threads dies
        while (threads[0].isAlive() && threads[1].isAlive())
            Thread.onSpinWait();

        // assert at least one of the thread is alive
        assert (threads[0].isAlive() || threads[1].isAlive());

        // rearm the events so that the
        // second waiter can wake up
        poller.modify(s1, events);

        // wait until the last thread dies
        while (threads[0].isAlive() || threads[1].isAlive())
            Thread.onSpinWait();

        // assert that none of the threads timed out,
        // i.e. that both were woken up
        assert !timedOut[0].get() && !timedOut[1].get();
    }

    @Test
    void close() {
        // poller created and closed
        Poller poller = Poller.create();
        poller.close();
        assert poller.isClosed();
        assert poller.isEmpty();

        // poller created and
        // filled with some sockest
        poller = Poller.create();
        MockSocket s1 = new MockSocket("1", 0),
                s2 = new MockSocket("2", 0),
                s3 = new MockSocket("3", 0);
        poller.add(s1, 0);
        poller.add(s2, 0);
        poller.add(s3, 0);
        assert poller.size() == 3;
        assert s1.getWaitQ().size() == 1;
        assert s2.getWaitQ().size() == 1;
        assert s3.getWaitQ().size() == 1;

        // add some waiters
        int nrWaiters = 3;
        for (int i = 0; i < nrWaiters; i++) {
            Poller finalPoller = poller;
            new Thread(() -> {
                try {
                    finalPoller.await();
                } catch (InterruptedException ignored) {
                } catch (PollerClosedException pce) {
                    assert true;
                }
            }).start();
        }

        // wait for the threads to add themselves as waiters
        while (poller.getNrWaiters() < nrWaiters)
            Thread.onSpinWait();

        // close poller, check correct unregistration
        // of interest in the pollables and wake up
        // of waiters
        poller.close();
        assert poller.isEmpty();
        assert s1.getWaitQ().isEmpty();
        assert s2.getWaitQ().isEmpty();
        assert s3.getWaitQ().isEmpty();
        assert poller.isClosed();

        // wait for the poller to not have any more waiters,
        // meaning that all were woken-up due to the closure
        // of the poller.
        while (poller.hasWaiters())
            Thread.onSpinWait();
    }

    @Test
    void immediatePoll() throws InterruptedException {
        // socket created with 0 credits so that
        // a non-blocking immediate poll can fail.
        MockSocket s1 = new MockSocket("1", 0);

        // the individual immediate poll returns 0
        // when it times out
        assert Poller.poll(s1, PollFlags.POLLOUT, 0L) == 0;

        // give one credit so that the next
        // individual immediate poll can return
        // the POLLOUT event
        s1.updateCredits(1);
        assert (Poller.poll(s1, PollFlags.POLLOUT, 0L) & PollFlags.POLLOUT) != 0;

        // remove credit to make individual poll block
        s1.updateCredits(-1);

        // creates thread that must block
        AtomicInteger rEvents = new AtomicInteger();
        Thread t = new Thread(() -> {
            try {
                rEvents.set(Poller.poll(s1, PollFlags.POLLOUT, null));
                System.out.println("Woke up");
            } catch (InterruptedException ignored) {
            }
        });
        t.start();

        // wait until thread 't' becomes a waiter
        while (s1.getWaitQ().isEmpty())
            Thread.onSpinWait();

        // provide credit so thath thread 't' may return
        s1.updateCredits(1);

        // waits for thread 't' to die
        int i = 0;
        while (t.isAlive()) {
            Thread.sleep(50);
            //Thread.onSpinWait();
        }
    }

    // test where a pollable is closed, meaning
    // it will want to be set free by the waiters
    @Test
    void freePollable() throws InterruptedException {
        // create poller and register socket 1
        // in edge-triggered mode, meaning
        // only one waiter is notified and
        // all events are cleared
        Poller poller = Poller.create();
        MockSocket s1 = new MockSocket("1", 0);
        poller.add(s1, PollFlags.POLLET);
        assert poller.size() == 1;
        assert s1.getWaitQ().size() == 1;

        // add some waiters
        int nrWaiters = 3;
        AtomicInteger[] rcvEvents = new AtomicInteger[nrWaiters];
        AtomicInteger woken = new AtomicInteger(0);
        for (int i = 0; i < nrWaiters; i++) {
            Poller finalPoller = poller;
            int finalI = i;
            new Thread(() -> {
                try {
                    // initialize rcvEvents
                    rcvEvents[finalI] = new AtomicInteger(0);
                    // wait for events
                    var rEvents = finalPoller.await();
                    // increase woken
                    woken.incrementAndGet();
                    // set rcvd events
                    rcvEvents[finalI] = new AtomicInteger(rEvents.getFirst().events);
                } catch (InterruptedException ignored) {
                } catch (PollerClosedException pce) {
                    assert true;
                }
            }).start();
        }

        // wait for the threads to add themselves as waiters
        while (poller.getNrWaiters() < nrWaiters)
            Thread.onSpinWait();

        // close socket 1, so that it can inform the
        // poller that it must set it free
        s1.close();
        assert s1.isClosed();

        // wait for the socket's wait queue to be empty,
        // i.e., waits for the poller to remove itself
        // from the socket's wait queue, and for a poller waiter
        // to wake up
        while (!s1.getWaitQ().isEmpty() && woken.get() < 1)
            Thread.onSpinWait();

        // assert poller is not empty since
        // the poller's responsibility when receiving
        // POLLFREE is to remove itself from the pollable's
        // wait queue. The removal of the pollable's from
        // the poller must be done "manually" when the poller's
        // users receive POLLHUP as the only event from the pollable.
        assert !poller.isEmpty();

        // sleep a bit to make sure no more threads wake up
        Thread.sleep(5);

        // assert only 1 thread was woken up
        List<AtomicInteger> rEvents =
                Arrays.stream(rcvEvents).filter(i -> i.get() != 0).toList();
        if(rEvents.size() != 1){
            System.out.println(rEvents);
        }
        assert rEvents.size() == 1;
        // assert POLLHUP was received and that POLLFREE wasn't
        // (POLLFREE shouldn't be passed by a poll() method)
        int events = rEvents.getFirst().get();
        assert (events & PollFlags.POLLHUP) != 0 &&
                (events & PollFlags.POLLFREE) == 0;
    }
}