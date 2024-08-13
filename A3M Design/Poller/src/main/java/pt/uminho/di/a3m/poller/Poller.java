package pt.uminho.di.a3m.poller;

import pt.uminho.di.a3m.list.ListNode;
import pt.uminho.di.a3m.poller.exceptions.PollableClosedException;
import pt.uminho.di.a3m.poller.exceptions.PollerClosedException;
import pt.uminho.di.a3m.waitqueue.ParkState;
import pt.uminho.di.a3m.waitqueue.WaitQueue;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;
import pt.uminho.di.a3m.waitqueue.WaitQueueFunc;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static pt.uminho.di.a3m.auxiliary.Timeout.calculateEndTime;

/* TODO -
          1. make Poller and test
          2. make mock Link
          3. make mock Socket
          4. test
          5. Try busy looping on wait queue modifications (using onSpinWait()) and do performance comparison
                - Basically, attempt to create a spin lock.
    */

// TODO - nesting pode vir a ser necessário para permitir criar uma instância com todos os links
//  do socket subscritos, ou, a simples existência de um poller com todos os links registados
//  pode ser suficiente.

// TODO - write that pollables may not be discarded "completely" (for example, releasing the
//  id for use by another pollable) until the wait queue is completely empty.
public class Poller {
    private final Lock lock = new ReentrantLock();

    // list of pollables that are "supposedly" ready
    private ListNode<PollerItem> readyList = ListNode.init();

    // wait queue for the poller's "users" (threads that use the poller)
    private final WaitQueue waitQ = new WaitQueue();

    // For quick look-ups of registered pollables.
    // Maps pollable's id to poller item.
    // The insertions are made using the hash code of the pollable's identifier.
    private final Map<Object, PollerItem> interestMap = new HashMap<>();

    // used to prevent new operations from the moment
    // the poller was closed.
    private AtomicBoolean closed = new AtomicBoolean(false);

    private Poller() {}

    // ****** Poller instance auxiliary methods ****** //

    /**
     * Must hold poller's lock.
     * @return true if the wait queue has waiters.
     */
    boolean hasWaiters(){
        return !waitQ.isEmpty();
    }

    private void removePollerItem(PollerItem pItem){
        interestMap.remove(pItem.getPollable().getId());
    }

    /**
     * Checks if poller is closed and throws exception
     * if it is.
     * Must hold lock.
     */
    void checkClosed(){
        // throw exception if the poller
        // has been closed
        if(closed.get())
            throw new PollerClosedException();
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
                    poller.waitQ.wakeUp(0, 1, 0, 0);
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

    /**
     * Remove poller item from wait queue
     * @param pItem poller item
     */
    private void unregisterPollerItem(PollerItem pItem){
        WaitQueueEntry wait = pItem.getWait();
        if(wait != null)
            wait.delete();
        pItem.setWait(null);
    }

    /**
     * Must be called with lock held.
     * Deletes poller item from poller.
     * or when "force" is true.
     * @param pItem poller item
     */
    private void deletePollerItem(PollerItem pItem){
        // removes item from the pollable's wait queue
        unregisterPollerItem(pItem);

        // removes entry from the interest map
        removePollerItem(pItem);

        // removes item from the ready list if present
        if(pItem.isReady())
            ListNode.delete(pItem.getReadyLink());
    }

    /**
     * @return list of pairs of pollable id and mask of available events for that pollable.
     * Empty list is returned if there isn't a pollable with available events.
     * @param maxEvents maximum number of events that should be returned
     * @throws InterruptedException If thread was interrupted before entering this method.
     */
    private List<PollEvent<Object>> getReadyEvents(int maxEvents) throws InterruptedException, PollerClosedException {
        // check if thread has been interrupted before
        // attempting to fetch ready events.
        if(Thread.currentThread().isInterrupted())
            throw new InterruptedException();

        checkClosed();

        List<PollEvent<Object>> rEvents = new ArrayList<>();
        PollTable pt = new PollTable(~0,null,null);

        try{
            PollerItem pItem;
            int aEvents;
            lock.lock();

            // throw exception if the poller has been closed
            if(closed.get())
                throw new PollerClosedException();

            // Saves current ready list in a temporary variable
            // and initiates a new ready list. This is done
            // for performance reasons, as to not keep track
            // of pollables re-added to the list at the tail,
            // which hinder the traversal of the list using an iterator.
            ListNode<PollerItem> tmpList = readyList;
            readyList = ListNode.init();
            ListNode.Iterator<PollerItem> it = ListNode.iterator(tmpList);

            while(it.hasNext()){
                // if the number of max events to be returned
                // is reached, break the loop.
                if(rEvents.size() >= maxEvents)
                    break;

                pItem = it.next();

                // removes pollable from "ready" list
                it.removeAndInit();

                // gets currently available events
                pt.setKey(pItem.getEvents());
                aEvents = itemPoll(pItem.getPollable(), pt);

                // if there aren't events available,
                // skip the rest of the iteration.
                if(aEvents == 0)
                    continue;

                // Register ready events of this pollable on the list
                rEvents.add(new PollEvent<>(pItem.getPollable().getId(), aEvents));

                // TODO - POLLHUP is passed when the pollable is closed, therefore,
                //  when a thread receives this event, it should,
                //  remove the pollable. delete() may be done so that
                //  it does not throw an exception and instead provides
                //  a value that informs that the pollable is not registered
                //  instead of forcing to handle an exception.
                //  With that said, I thought POLLFREE should have been removed.
                //  However, although it may not to be required to delete the entry,
                //  it is recommended as new pollables with the same id could be created
                //  and therefore result in trouble. POLLFREE is at least required to remove the
                //  wait queue entry.
                // If the POLLONESHOT flag was used, disarm events.
                if((pItem.getEvents() & PollFlags.POLLONESHOT) != 0) {
                    pItem.setEvents(pItem.getEvents() & PollFlags.POLLER_PRIVATE_BITS);
                } else if ((pItem.getEvents() & PollFlags.POLLFREE) != 0) {
                    removePollerItem(pItem);
                } else if ((pItem.getEvents() & PollFlags.POLLET) == 0) {
                    // if pollable was registered with level-trigger,
                    // then the pollable should be added back to the
                    // ready list so that other waiters can also be
                    // aware of the availability. Added as last,
                    // so that the order of the ready list is preserved.
                    ListNode.moveToLast(pItem.getReadyLink(),readyList);
                }
            }

            // add the remaining items in the
            // tmp list back to the ready list
            ListNode.concat(readyList, tmpList);
        }finally {
            lock.unlock();
        }

        return rEvents;
    }

    // ***** Poller instance public methods ***** //

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

            checkClosed();

            // checks if the pollable as already been registered
            if (interestMap.containsKey(p.getId()))
                throw new IllegalStateException("Pollable is already registered.");

            // creates poller item and uses it to register the pollable
            PollerItem pItem = PollerItem.init(this, p, events);
            interestMap.put(p.getId(), pItem);

            PollTable pt = new PollTable(events, pItem, pQueuingFunc);

            // Add poller item to the pollable's wait queue and
            // get currently available events.
            int aEvents = itemPoll(p, pt);

            // Checks if the pollable is pollable, i.e.,
            // is not closed. If it is closed the POLLFREE
            // flag will be returned.
            if((aEvents & PollFlags.POLLFREE) != 0){
                // removes the pollable from the interest map
                removePollerItem(pItem);
                // TODO - if POLLFREE is removed, then this should be removed as well
                // throws exception informing that the pollable
                // is closed.
                throw new PollableClosedException();
            }

            // if there are available events and the item is
            // not yet marked as ready, then add it to the ready list
            // and wake up waiters
            if(aEvents != 0 && !pItem.isReady()){
                ListNode.addLast(readyList, pItem.getReadyLink());
                if(hasWaiters())
                    waitQ.wakeUp(0,1,0,0);
            }
        }finally {
            lock.unlock();
        }
    }

    /**
     * Modifies events mask of a registered pollable.
     * @param p target of the modification
     * @param events new events mask
     */
    public void modify(Pollable p, int events) throws PollerClosedException{
        if(p == null)
            throw new IllegalArgumentException("Pollable is null.");

        // validates combination of flags
        if ((events & PollFlags.POLLEXCLUSIVE) != 0)
            throw new IllegalStateException("Event mask modifications with POLLEXCLUSIVE " +
                    "are not allowed: 'events' includes POLLEXCLUSIVE.");

        // adds error and hang up flags
        events |= PollFlags.POLLERR | PollFlags.POLLHUP;

        try {
            lock.lock();

            checkClosed();

            PollerItem pItem = interestMap.get(p.getId());
            if(pItem == null)
                throw new NoSuchElementException("Pollable is not registered.");

            if((pItem.getEvents() & PollFlags.POLLEXCLUSIVE) != 0)
                throw new IllegalStateException("Event mask modifications with POLLEXCLUSIVE " +
                        "are not allowed: pollable was registered with POLLEXCLUSIVE.");

            // update events
            pItem.setEvents(events);

            PollTable pt = new PollTable(events, null, null);

            int aEvents = itemPoll(p, pt);
            if(aEvents != 0 && !pItem.isReady()) {
                ListNode.addLast(readyList, pItem.getReadyLink());
                if (hasWaiters())
                    waitQ.wakeUp(0, 1, 0, 0);
            }
        }finally {
            lock.unlock();
        }
    }

    // TODO - delete() missing javadoc
    public void delete(Pollable p) throws PollerClosedException{
        if(p == null)
            throw new IllegalArgumentException("Pollable is null.");

        try {
            lock.lock();

            checkClosed();

            // checks if pollable is registered
            PollerItem pItem = interestMap.get(p.getId());
            if(pItem == null)
                throw new NoSuchElementException("Pollable is not registered.");

            // deletes pollable's item
            deletePollerItem(pItem);
        }finally {
            lock.unlock();
        }
    }

    // TODO - await() missing javadoc
    public List<PollEvent<Object>> await(int maxEvents, Long timeout) throws InterruptedException, PollerClosedException {
        if(maxEvents <= 0)
            throw new IllegalArgumentException("Maximum number of events must be a positive value.");

        List<PollEvent<Object>> rEvents;
        Long endTimeout = calculateEndTime(timeout);
        boolean timedOut = endTimeout == 0;

        // Racy call. If a non-blocking operation was requested,
        // this avoids waiting for the lock to be acquired and
        // allows the call to return immediatelly. Otherwise,
        // there shouldn't be a problem since after lock is acquired,
        // this will be re-tested.
        boolean ready = ListNode.isEmpty(readyList);

        while(true){
            if(ready){
                rEvents = getReadyEvents(maxEvents);
                if(!rEvents.isEmpty())
                    return rEvents;
            }

            // if timed out or poller closed,
            // return null
            if (timedOut)
                return null;

            if(Thread.currentThread().isInterrupted())
                throw new InterruptedException();

            WaitQueueEntry wait = waitQ.initEntry();

            try {
                lock.lock();
                checkClosed();
                // Final availability check under lock.
                // If there aren't events available,
                // then queues itself.
                ready = ListNode.isEmpty(readyList);
                if(!ready)
                    wait.addExclusive(WaitQueueEntry::autoDeleteWakeFunction,
                                       new ParkState());
            }finally {
                lock.unlock();
            }

            // waits until it times out or is woken up
            if(!ready)
                timedOut = !WaitQueueEntry.defaultWaitFunction(wait, null, endTimeout);

            // deletes entry if waiting operation timed out
            if(timedOut)
                wait.delete();

            // sets ready to true, to try and check if there
            // are events ready, even if the waiting operation timed out
            ready = true;
        }
    }

    public List<PollEvent<Object>> await(int maxEvents) throws InterruptedException, PollerClosedException {
        return await(maxEvents, null);
    }

    public List<PollEvent<Object>> await() throws InterruptedException, PollerClosedException {
        return await(Integer.MAX_VALUE, null);
    }

    public void close() throws PollerClosedException{
        try{
            lock.lock();
            checkClosed();
            interestMap.values().forEach(this::deletePollerItem);
            waitQ.wakeUp(0,0,0,0);
        }finally {
            lock.unlock();
        }
    }

    /**
     * @return close state of the poller
     */
    public boolean isClosed(){
        try {
            lock.lock();
            return closed.get();
        }finally {
            lock.unlock();
        }
    }

    // ***** Poll call methods ***** //

    // TODO - make individual poll method?

    public static List<PollEvent<Object>> poll(List<PollEvent<Pollable>> interestList){
        // TODO - poll()
        throw new java.lang.UnsupportedOperationException("Not implemented yet.");
    }
}
