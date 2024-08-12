package pt.uminho.di.a3m.poller;

import pt.uminho.di.a3m.list.ListNode;
import pt.uminho.di.a3m.waitqueue.WaitQueue;
import pt.uminho.di.a3m.waitqueue.WaitQueueFunc;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/* TODO -
          1. make Poller and test
          2. make mock Link
          3. make mock Socket
          4. test
          5. Try busy looping on wait queue modifications (using onSpinWait()) and do performance comparison
                - Basically, attempt to create a spin lock.
    */
public class Poller {
    private final Lock lock = new ReentrantLock();

    // list of pollables that are "supposedly" ready
    private final ListNode<PollerItem> readyList = ListNode.init();

    // wait queue for the poller's "users" (threads that use the poller)
    private final WaitQueue waitQ = new WaitQueue();

    // For quick look-ups of registered pollables.
    // Maps pollable's id to poller item.
    // The insertions are made using the hash code of the pollable's identifier.
    private final Map<Object, PollerItem> interestMap = new HashMap<>();

    private Poller() {}

    // ****** Auxiliary methods ****** //

    /**
     * Must hold poller's lock.
     * @return true if the wait queue has waiters.
     */
    boolean _hasWaiters(){
        return !waitQ.isEmpty();
    }

    /**
     * @return true if the wait queue has waiters.
     */
    boolean hasWaiters(){
        try{
            lock.lock();
            return _hasWaiters();
        }finally {
            lock.unlock();
        }
    }

    private static final WaitQueueFunc waitQueueFunc = (entry, mode, flags, key) -> {
        // Return value of the function. The return value is 1
        // if a waiter can be successfully woken up due to
        // this wake-up callback. Otherwise, it must be 0.
        // ewake is 0 when an exclusive wake-up is requested and
        // either the pollable is being freed or the available events (key)
        // do not match the events of interest.
        int ewake = 0;
        PollerItem pItem = (PollerItem) entry.getPriv();
        Poller poller = pItem.getPoller();

        try{
            poller.lock.lock();

            wakeUp:
            {
                // Checks if any events are subscribed
                // (POLLONESHOT may have disabled them)
                if ((pItem.getEvents() & ~PollFlags.POLLER_PRIVATE_BITS) == 0)
                    break wakeUp;

                // if the item is not in the ready list,
                // adds it at the tail.
                if(!pItem.isReady())
                    ListNode.addLast(poller.readyList, pItem.getReadyLink());

                if(poller.hasWaiters()) {
                    // Checks if an exclusive wake up can be successful
                    // due to this wake-up callback. The wake-up call is
                    // successful if there is a match of any event, be it
                    // POLLIN, POLLOUT, POLLERR or POLLHUP
                    if ((pItem.getEvents() & PollFlags.POLLEXCLUSIVE) != 0 &&
                            (key & PollFlags.POLLFREE) == 0) {
                        switch (key & PollFlags.POLLINOUT_BITS) {
                            case PollFlags.POLLIN:
                                if ((pItem.getEvents() & PollFlags.POLLIN) != 0)
                                    ewake = 1;
                                break;
                            case PollFlags.POLLOUT:
                                if ((pItem.getEvents() & PollFlags.POLLOUT) != 0)
                                    ewake = 1;
                                break;
                            case 0:
                                ewake = 1;
                                break;
                        }
                    }

                    // wake up all non-exclusive entries and
                    // at least one exclusive entry
                    poller.waitQ.fairWakeUp(0, 1, 0, 0);
                }
            }

            // TODO - how does this influence the waiters?
            //          Only one is told about this?
            //          No one is told?
            //          Remove automatically the poller item?
            // If the key contains a POLLFREE flag,
            // then it means the pollable is being closed
            // and the wait queue entry must be removed.
            if((key & PollFlags.POLLFREE) == 0){
                // TODO - Should I delete and re-initialize?
                pItem.getWait().delete();
                pItem.setWait(null);
            }
        }finally {
            poller.lock.unlock();
        }

        // Non-exclusive wake-up requested, therefore,
        // return value can be "success"
        if((pItem.getEvents() & PollFlags.POLLEXCLUSIVE) == 0)
            ewake = 1;

        return ewake;
    };

    private static final PollQueueingFunc pQueuingFunc = (p, wait, pt) -> {
        PollerItem pItem = (PollerItem) pt.getPriv();

        if((pt.getKey() & PollFlags.POLLEXCLUSIVE) != 0)
            wait.addExclusive(waitQueueFunc, pItem);
        else
            wait.add(waitQueueFunc, pItem);

        pItem.setWait(wait);
    };


    private static int itemPoll(Pollable p, PollTable pt) {
        int ret = p.poll(pt);
        return pt.getKey() & ret;
    }

    // ***** Public methods ***** //

    public static Poller create(){
        return new Poller();
    }

    /**
     * Registers interest in "events" events of the pollable "p".
     * POLLERR and POLLHUP are informed regardless of their presence
     * in the provided events mask.
     * @param p pollable with events of interest
     * @param events events of interest
     * @throws IllegalArgumentException If the flags do not follow
     * a valid combination. POLLEXCLUSIVE must not be paired with
     * either ~POLLET (level-triggered) and POLLONESHOT.
     * @throws IllegalStateException If pollable is already registered.
     * @throws PollableClosedException If the pollable is closed.
     */
    public void add(Pollable p, int events) throws PollableClosedException {
        if(p == null)
            throw new IllegalArgumentException("Pollable is null.");

        // validates combination of flags
        if ((events & PollFlags.POLLEXCLUSIVE) != 0
            && (events & ~PollFlags.POLLER_EXCLUSIVE_OK_BITS) != 0)
            throw new IllegalArgumentException("Illegal combination of flags with POLLEXCLUSIVE.");

        // adds interest in error and hang up (close) events
        events |= PollFlags.POLLERR | PollFlags.POLLHUP;

        try {
            lock.lock();

            // checks if the pollable as already been registered
            if (interestMap.containsKey(p.getId()))
                throw new IllegalStateException("Pollable is already registered.");

            // creates poller item and uses it to register the pollable
            PollerItem pollerItem = PollerItem.init(this, p, events);
            interestMap.put(p.getId(), pollerItem);

            PollTable pt = new PollTable(events, pollerItem, pQueuingFunc);

            // TODO - maybe think about having multiple locks with different granularities.
            //          1. main lock when changes of the registered pollables are required
            //          2. another lock when only the items are involved?
            //          - Acquired in the order above
            // Add poller item to the pollable's wait queue and
            // get currently available events.
            int aEvents = itemPoll(p, pt);

            // Checks if the pollable is pollable, i.e.,
            // is not closed. If it is closed the POLLFREE
            // flag will be returned.
            if((aEvents & PollFlags.POLLFREE) != 0){
                // removes the pollable from the interest map
                interestMap.remove(p.getId());
                // throws exception informing that the pollable
                // is closed.
                throw new PollableClosedException();
            }

            // if there are available events and the item is
            // not yet marked as ready, then add it to the ready list
            // and wake up waiters
            if(aEvents != 0 && !pollerItem.isReady()){
                ListNode.addLast(readyList, pollerItem.getReadyLink());
                if(hasWaiters())
                    waitQ.fairWakeUp(0,1,0,0);
            }
        }finally {
            lock.unlock();
        }
    }



    public void delete(Pollable p){
        if(p == null)
            throw new IllegalArgumentException("Pollable is null.");



        // TODO - delete()
        throw new java.lang.UnsupportedOperationException("Not implemented yet.");
    }

    public void modify(Pollable p, int events){
        if(p == null)
            throw new IllegalArgumentException("Pollable is null.");



        // TODO - modify()
        throw new java.lang.UnsupportedOperationException("Not implemented yet.");
    }

    public void await(Long timeout){
        // TODO - wait()
        throw new java.lang.UnsupportedOperationException("Not implemented yet.");
    }

    public void await(){
        await(null);
    }

    public void close(){
        // TODO - close()
        throw new java.lang.UnsupportedOperationException("Not implemented yet.");
    }

    public static List<PollEvent<Object>> poll(List<PollEvent<Pollable>> interestList){
        // TODO - poll()
        throw new java.lang.UnsupportedOperationException("Not implemented yet.");
    }
}
