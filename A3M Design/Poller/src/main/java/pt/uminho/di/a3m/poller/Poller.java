package pt.uminho.di.a3m.poller;

import pt.uminho.di.a3m.list.ListNode;
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

    // ****** Static Final Return Values ****** //
    /** Pollable closed */
    public final static int PCLOSED = -1;

    /** Pollable is already registered */
    public final static int PEXIST = -2;

    /** Pollable is not registered */
    public final static int PNOEXIST = -2;

    // ****** Poller instance auxiliary methods ****** //

    /**
     * Must hold poller's lock.
     * @return true if the wait queue has waiters.
     */
    boolean hasWaiters(){
        return !waitQ.isEmpty();
    }

    int getNrWaiters(){
        return waitQ.size();
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

    /**
     * If the events of interest contain the POLLEXCLUSIVE flag,
     * and the wake key does not contain POLLFREE, then checks
     * if the exclusive wake up is successful. The wake-up call is
     * successful if there is a match of any event, be it
     * POLLIN, POLLOUT, POLLERR or POLLHUP
     * @param events events of interest
     * @param key wake key
     * @return <p>- 0 if the following conditions are met:
     * (1) POLLEXCLUSIVE flag is set in events, (2) POLLFREE is not
     * in the key, and (3) there isn't a match of events between
     * 'events' and 'key'.
     * <p>- 1 otherwise.
     */
    private static int isSuccessfulExclusiveWake(int events, int key){
        int ret = 0;
        if ((events & PollFlags.POLLEXCLUSIVE) != 0 &&
                (key & PollFlags.POLLFREE) == 0) {
            switch (key & PollFlags.POLLINOUT_BITS) {
                case PollFlags.POLLIN:
                    if ((events & PollFlags.POLLIN) != 0)
                        ret = 1;
                    break;
                case PollFlags.POLLOUT:
                    if ((events & PollFlags.POLLOUT) != 0)
                        ret = 1;
                    break;
                case 0:
                    ret = 1;
                    break;
            }
        }
        return ret;
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
                    ListNode.addLast(pItem.getReadyLink(), poller.readyList);

                if(poller.hasWaiters()) {
                    ewake = isSuccessfulExclusiveWake(pItem.getEvents(), key);
                    // wake up a waiter
                    poller.waitQ.wakeUp(0, 1, 0, 0);
                }
            }

            // If the key contains a POLLFREE flag,
            // then it means the pollable is being closed
            // and the wait queue entry must be removed.
            if((key & PollFlags.POLLFREE) != 0){
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
        // If the pollable is closed to queuing.
        if(wait == null)
            return;

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
        if(wait != null) {
            wait.delete();
            pItem.setWait(null);
        }
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

                // If the POLLONESHOT flag was used, disarm events.
                if((pItem.getEvents() & PollFlags.POLLONESHOT) != 0) {
                    pItem.setEvents(pItem.getEvents() & PollFlags.POLLER_PRIVATE_BITS);
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
     * @return  <p>> 0 if success.
     *          <p>> PEXIST if the pollable is already registered.
     *          <p>> PCLOSED if pollable was closed to polling, i.e.,
     * it does not allow new event waiters to be registered.
     * @throws IllegalArgumentException If pollable is null or if
     * the flags do not follow a valid combination.
     * POLLEXCLUSIVE must not be paired with either
     * ~POLLET (level-triggered) and POLLONESHOT.
     * @throws PollerClosedException If poller is closed.
     */
    public int add(Pollable p, int events) throws PollerClosedException {
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
                return PEXIST;

            // creates poller item and uses it to register the pollable
            PollerItem pItem = PollerItem.init(this, p, events);
            interestMap.put(p.getId(), pItem);

            PollTable pt = new PollTable(events, pItem, pQueuingFunc);

            // Add poller item to the pollable's wait queue and
            // get currently available events.
            int aEvents = itemPoll(p, pt);

            // If pollable did not allow queueing,
            // throw exception to inform that the
            // pollable is closed to polling.
            if(pItem.getWait() == null)
                return PCLOSED;

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

        return 0;
    }

    /**
     * Modifies events mask of a registered pollable.
     * @param p target of the modification
     * @param events new events mask
     * @return <p> > 0 if success
     *         <p> > PNOEXIST if pollable is not registered.
     * @throws IllegalArgumentException if pollable is null.
     * @throws IllegalStateException if POLLEXCLUSIVE is involved in the modification,
     * i.e., if the flag is set in the new event mask or in the current event mask.
     * @throws PollerClosedException if poller is closed.
     */
    public int modify(Pollable p, int events) throws PollerClosedException{
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
                return PNOEXIST;

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
        
        return 0;
    }

    /**
     * Deletes pollable from interest list
     * @param p pollable to be deleted from the interest list
     * @return <p> > Number of remaining pollables being monitored. </p>
     *         <p> > PNOEXIST if pollable is not registered. </p>
     * @throws IllegalArgumentException if pollable is null        
     * @throws PollerClosedException if poller is closed
     */
    public int delete(Pollable p) throws PollerClosedException{
        if(p == null)
            throw new IllegalArgumentException("Pollable is null.");

        try {
            lock.lock();

            checkClosed();

            // checks if pollable is registered
            PollerItem pItem = interestMap.get(p.getId());
            if(pItem == null)
                return PNOEXIST;

            // deletes pollable's item
            deletePollerItem(pItem);

            return interestMap.size();
        }finally {
            lock.unlock();
        }
    }

    /**
     * @param maxEvents maximum number of events that should be returned
     * @param timeout maximum time allocated to wait for events.
     *                null to wait until there are events to return.
     *                0 or negative values for non-blocking operation.
     * @return <p> > list of pairs of pollable id and mask of available events for that pollable.
     *         <p> > null if timed out
     * @throws InterruptedException if thread was interrupted during the invocation of this method.
     * @throws IllegalArgumentException if the maximum number of events is not a positive value.
     * @throws PollerClosedException if poller is closed
     */
    public List<PollEvent<Object>> await(int maxEvents, Long timeout) throws InterruptedException, PollerClosedException {
        if(maxEvents <= 0)
            throw new IllegalArgumentException("Maximum number of events must be a positive value.");

        List<PollEvent<Object>> rEvents;
        Long endTimeout = calculateEndTime(timeout);
        boolean timedOut = Objects.equals(endTimeout,0L);

        // Racy call. If a non-blocking operation was requested,
        // this avoids waiting for the lock to be acquired and
        // allows the call to return immediatelly. Otherwise,
        // there shouldn't be a problem since after lock is acquired,
        // this will be re-tested.
        boolean ready = !ListNode.isEmpty(readyList);
        while(true){
            if(ready){
                rEvents = getReadyEvents(maxEvents);
                if(!rEvents.isEmpty()) {
                    if(hasWaiters())
                        waitQ.wakeUp(0,1,0,0);
                    return rEvents;
                }
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
                ready = !ListNode.isEmpty(readyList);
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

    /**
     * Closes the poller. Wakes up all waiting threads.
     * @throws PollerClosedException if poller is closed
     */
    public void close() throws PollerClosedException{
        try{
            lock.lock();
            checkClosed();
            closed.set(true);
            Iterator<PollerItem> it = interestMap.values().iterator();
            PollerItem pItem;
            while(it.hasNext()){
                pItem = it.next();
                it.remove();
                deletePollerItem(pItem);
            }
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

    /**
     * @return true if the poller does not have any pollable registered.
     */
    public boolean isEmpty(){
        try {
            lock.lock();
            return interestMap.isEmpty();
        }finally {
            lock.unlock();
        }
    }

    /**
     * @return number of pollables being monitored
     */
    public int size(){
        try {
            lock.lock();
            return interestMap.size();
        }finally {
            lock.unlock();
        }
    }

    // ***** Poll call methods ***** //

    /** Immediate poll wait queue function. */
    private static final WaitQueueFunc _ipollWakeQueueFunc = (entry, mode, flags, key) -> {
        // TODO - max events should not be supported by individual poll if exclusive
        //  polling is supported. But it may be supported, if exclusive polling is not allowed.
        PollEvent<ParkState> pe = (PollEvent<ParkState>) entry.getPriv();
        int events = pe.events;
        int ret = isSuccessfulExclusiveWake(events, key);
        System.out.println("is succ exclusive wake: " + ret);
        System.out.flush();

        // wake up the thread
        entry.parkStateWakeUp(pe.data);

        // sets success wake-up if events do not
        // contain the POLLEXCLUSIVE flag
        if ((events & PollFlags.POLLEXCLUSIVE) == 0)
            ret = 1;

        return ret;
    };

    /**
     * Polls a pollable using the given poll table. POLLERR and POLLHUP are
     * automatically added as events of interest.
     * @param p pollable to be polled
     * @param pt poll table which may contain queueing function
     * @return events matched between the poll table key and the pollable's available events.
     */
    private static int _pollPollable(Pollable p, PollTable pt){
        if(p == null)
            throw new IllegalArgumentException("Pollable is null.");

        // validates combination of flags
        int events = pt.getKey();
        if ((events & PollFlags.POLLEXCLUSIVE) != 0
                && (events & ~PollFlags.POLLER_EXCLUSIVE_OK_BITS) != 0)
            throw new IllegalArgumentException("Illegal combination of flags with POLLEXCLUSIVE.");

        // adds interest in error and hang up (close) events
        events |= PollFlags.POLLERR | PollFlags.POLLHUP;
        pt.setKey(events);
        
        // polls events and queues thread to receive events
        return pt.getKey() & p.poll(pt);
    }

    /**
     * Register loop for instant poll calls. The loop
     * adds the caller thread to the poll hooks of
     * the pollables in the interest list. The pollables
     * that follow the first pollable with available events,
     * will not have a poll hook added.
     * @param interestList list of pollables of interest along with the corresponding event mask
     * @param maxEvents maximum number of events that should be returned
     * @param ps park state used to create the poll hooks
     * @param rEvents list used by the method to register the available events
     * @return table (list) of wait queue entries (poll hooks). Cannot be null, but
     * may be empty if pollables are closed to polling or if the first pollable had
     * available events.
     */
    private static List<WaitQueueEntry> _pollRegisterLoop(List<PollEvent<Pollable>> interestList, int maxEvents, ParkState ps, List<PollEvent<Object>> rEvents) {
        List<WaitQueueEntry> waitTable = new ArrayList<>();
        PollTable pt = new PollTable(~0, null, (p, wait, qpt) -> {
            if (wait != null) {
                PollEvent<ParkState> pe = new PollEvent<>(ps, qpt.getKey());
                if((qpt.getKey() & PollFlags.POLLEXCLUSIVE) != 0)
                    wait.addExclusive(_ipollWakeQueueFunc, pe);
                else
                    wait.add(_ipollWakeQueueFunc, pe);
                waitTable.add(wait);
            }
        });

        int aEvents;

        // register in pollable's wait queue until
        // a pollable with events to return immediatelly,
        // or until the interest list ends
        int i = 0;
        for (;i < interestList.size(); i++) {
            PollEvent<Pollable> pe = interestList.get(i);
            pt.setKey(pe.events);
            aEvents = _pollPollable(pe.data, pt);
            if (aEvents != 0) {
                rEvents.add(new PollEvent<>(pe.data.getId(), aEvents));
                // remove queueing function and break when
                // the first available is found. Then,
                // continue polling with a loop that 
                // does not use a poll queueing function
                pt.setFunc(null);
                break;
            }
        }
        
        // sets position to the next pollable 
        i++;
        // attempts to fetch more events from the
        // remaining pollables
        if(rEvents.size() < maxEvents)
            _pollFetchEventsLoop(interestList, i, pt, maxEvents, rEvents);

        return waitTable;
    }


    /**
     * Available events fetching loop for instant poll calls. 
     * The loop starts polling at the given position 'i' and
     * adds the available events to the 'rEvents'.
     * @param interestList list of pollables of interest along with the corresponding event mask
     * @param i index of the interest list that defines the starting poll position
     * @param pt poll table that should be used for polling
     * @param maxEvents maximum number of events that the 'rEvents' can have
     *                  to return.
     * @param rEvents list used by the method to register the available events
     */
    private static void _pollFetchEventsLoop(List<PollEvent<Pollable>> interestList, int i, PollTable pt, int maxEvents, List<PollEvent<Object>> rEvents) {
        int aEvents;
        // register in pollable's wait queue until
        // a pollable with events to return immediatelly,
        // or until the interest list ends
        for (;i < interestList.size(); i++) {
            PollEvent<Pollable> pe = interestList.get(i);
            pt.setKey(pe.events);
            aEvents = _pollPollable(pe.data, pt);
            if (aEvents != 0) {
                rEvents.add(new PollEvent<>(pe.data.getId(), aEvents));
                // stops if maxEvents is reached
                if(rEvents.size() >= maxEvents)
                    break;
            }
        }
    }

    /**
     * Used for instant (occasional) polling. When a poller instance
     * is not justified, this method may be used to wait for events of
     * a list of pollables. Exclusive edge-trigger polling is allowed.
     * @param interestList list of pollables of interest along with the corresponding event mask
     * @param maxEvents maximum number of events that the 'rEvents' can have
     *                  to return.
     * @param timeout maximum time allocated to wait for events.
     *                null to wait until there are events to return.
     *                0 or negative values for non-blocking operation
     * @return list of poll events that match the id of a pollable
     *         with the corresponding available events. Empty list is
     *         returned when the operation times out.
     * @throws InterruptedException if the caller thread was interrupted.
     * @throws IllegalArgumentException if interest list is empty or
     *                                  if the maxEvents parameter is not positive.
     */
    public static List<PollEvent<Object>> poll(List<PollEvent<Pollable>> interestList, int maxEvents, Long timeout) throws InterruptedException {
        // calculate end time
        Long endTimeout = calculateEndTime(timeout);
        boolean timedOut = Objects.equals(endTimeout,0L);
        
        // Check parameters
        if(interestList == null || interestList.isEmpty())
            throw new IllegalArgumentException("Empty interest list provided.");
        if(maxEvents <= 0)
            throw new IllegalArgumentException("maxEvents must be a positive value.");
        
        // initialize required variables
        List<PollEvent<Object>> rEvents = new ArrayList<>();
        ParkState ps = new ParkState();
        // since the wait and wake functions do have a locking
        // mechanism to properly synchronize, the parked state
        // is set to true preemptively so that if a pollable
        // of interest attempts to wake up this waiter while it
        // is not parked yet, it will still unpark it, which will
        // result in the current thread receiving a permit that allows
        // it to skip the parking.
        ps.parked.set(true);
        
        // register in pollable's wait queues
        List<WaitQueueEntry> waitTable = _pollRegisterLoop(interestList, maxEvents, ps, rEvents);

        // Waits for events in a loop, if at least one pollable
        // allowed adding a poll hook (pollables may be closed to polling)
        // and if no event was retrieved during the register loop
        if(!waitTable.isEmpty() && rEvents.isEmpty()) {
            PollTable pt = new PollTable(~0, null, null);
            while (true) {
                // checks if thread was interrupted
                if(Thread.currentThread().isInterrupted())
                    throw new InterruptedException();
                // breaks from the waiting loop if timed out
                if(timedOut)
                    break;
                // waits for timeout, interrupt signal or wake-up call
                timedOut = WaitQueueEntry.parkStateWaitFunction(endTimeout, ps);
                // set parks state to true to make sure it if
                // in the next iteration, waiting is only done
                // if a permit for unparking has not been given yet
                ps.parked.set(true);
                // iterates over all pollables to fetch available events 
                _pollFetchEventsLoop(interestList, 0, pt, maxEvents, rEvents);
                // exits the loop if there are events available to be retrieved
                if(!rEvents.isEmpty())
                    break;
            }
            // sets parked to false, so that no more wake-up calls
            // are done to a completed poll
            ps.parked.set(false);
        }

        // delete wait queue entries (poll hooks)
        for(WaitQueueEntry wait : waitTable)
            wait.delete();

        return rEvents;
    }

    /**
     * Used for instant (occasional) polling. When a poller instance
     * is not justified, this method may be used to wait for events of
     * a list of pollables. Exclusive edge-trigger polling is allowed.
     * @param p pollables of interest
     * @param events events of interest
     * @param timeout maximum time allocated to wait for events.
     *                null to wait until there are events to return.
     *                0 or negative values for non-blocking operation
     * @return event mask with the available events, or 0 if the operation
     *         timed out.
     * @throws InterruptedException if the caller thread was interrupted.
     */
    public static int poll(Pollable p, int events, Long timeout) throws InterruptedException {
        List<PollEvent<Object>> el = poll(List.of(new PollEvent<>(p, events)),1,timeout);
        return !el.isEmpty() ? el.getFirst().events : 0;
    }
}
