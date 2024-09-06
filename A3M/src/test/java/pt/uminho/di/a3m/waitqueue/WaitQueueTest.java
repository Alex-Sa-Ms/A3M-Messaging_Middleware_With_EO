package pt.uminho.di.a3m.waitqueue;

import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.list.IListNode;
import pt.uminho.di.a3m.list.ListNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

class WaitQueueTest {

    @Test
    void initQueue() {
        WaitQueue queue = new WaitQueue();
        assert IListNode.isEmpty(queue.getHead());
    }

    @Test
    void initEntry() {
        WaitQueue queue = new WaitQueue();
        WaitQueueEntry entry = queue.initEntry();
        assert entry.getQueue() == queue
                && entry.getFunc() == null
                && entry.getPriv() == null
                && entry.getNode() == null
                && entry.getWaitFlags() == 0;
    }

    @Test
    void addEntry() {
        WaitQueue queue = new WaitQueue();
        WaitQueueEntry entry1 = queue.initEntry(),
                entry2 = queue.initEntry(),
                entry3 = queue.initEntry();

        WaitQueueFunc func = WaitQueueEntry::defaultWakeFunction;
        ParkState ps = new ParkState();
        entry1.add(func, ps);
        assert IListNode.isFirst(entry1.getNode(),queue.getHead());
        assert IListNode.size(queue.getHead()) == 1;
        assert entry1.getWaitFlags() == 0
                && entry1.getPriv() == ps
                && entry1.getQueue() == queue
                && entry1.getFunc() == func
                && entry1.getNode() != null
                && entry1.getNode().getPrev() == queue.getHead()
                && entry1.getNode().getNext() == queue.getHead();

        entry2.add(func, ps);
        assert IListNode.isFirst(entry2.getNode(),queue.getHead());
        assert IListNode.size(queue.getHead()) == 2;
        assert entry2.getWaitFlags() == 0
                && entry2.getPriv() == ps
                && entry2.getQueue() == queue
                && entry2.getFunc() == func
                && entry2.getNode() != null;
        assert entry2.getNode().getPrev() == queue.getHead()
                && entry2.getNode().getNext() == entry1.getNode();

        entry3.add(func, ps);
        assert IListNode.isFirst(entry3.getNode(),queue.getHead());
        assert IListNode.size(queue.getHead()) == 3;
        assert entry3.getWaitFlags() == 0
                && entry3.getPriv() == ps
                && entry3.getQueue() == queue
                && entry3.getFunc() == func
                && entry3.getNode() != null;
        assert entry3.getNode().getPrev() == queue.getHead()
                && entry3.getNode().getNext() == entry2.getNode();
    }

    @Test
    void addExclusiveEntry() {
        WaitQueue queue = new WaitQueue();
        WaitQueueEntry entry1 = queue.initEntry(),
                entry2 = queue.initEntry(),
                entry3 = queue.initEntry();
        assert IListNode.isEmpty(queue.getHead());

        WaitQueueFunc func = WaitQueueEntry::defaultWakeFunction;
        ParkState ps = new ParkState();
        entry1.addExclusive(func, ps);
        assert IListNode.isLast(entry1.getNode(),queue.getHead());
        assert IListNode.size(queue.getHead()) == 1;
        assert entry1.getWaitFlags() == WaitQueueFlags.EXCLUSIVE
                && entry1.getPriv() == ps
                && entry1.getQueue() == queue
                && entry1.getFunc() == func
                && entry1.getNode() != null
                && entry1.getNode().getPrev() == queue.getHead()
                && entry1.getNode().getNext() == queue.getHead();

        entry2.addExclusive(func, ps);
        assert IListNode.isLast(entry2.getNode(),queue.getHead());
        assert IListNode.size(queue.getHead()) == 2;
        assert entry2.getWaitFlags() == WaitQueueFlags.EXCLUSIVE
                && entry2.getPriv() == ps
                && entry2.getQueue() == queue
                && entry2.getFunc() == func
                && entry2.getNode() != null;
        assert entry2.getNode().getPrev() == entry1.getNode()
                && entry2.getNode().getNext() == queue.getHead();

        entry3.addExclusive(func, ps);
        assert IListNode.isLast(entry3.getNode(),queue.getHead());
        assert IListNode.size(queue.getHead()) == 3;
        assert entry3.getWaitFlags() == WaitQueueFlags.EXCLUSIVE
                && entry3.getPriv() == ps
                && entry3.getQueue() == queue
                && entry3.getFunc() == func
                && entry3.getNode() != null;
        assert entry3.getNode().getPrev() == entry2.getNode()
                && entry3.getNode().getNext() == queue.getHead();
    }

    @Test
    void deleteEntry() {
        WaitQueueFunc func = WaitQueueEntry::defaultWakeFunction;
        ParkState ps = new ParkState();

        WaitQueue queue = new WaitQueue();
        int nrEntries = 10;
        WaitQueueEntry[] entries = new WaitQueueEntry[nrEntries];

        for (int i = 0; i <= nrEntries; i++){
            // add entries
            for (int j = 0; j < i; j++){
                entries[j] = queue.initEntry();
                entries[j].add(func, ps);
            }

            // assert they were inserted
            assert queue.size() == i;

            // remove entries
            for (int j = 0; j < i; j++)
                entries[j].delete();

            // assert queue is empty
            assert queue.isEmpty();
        }
    }

    @Test
    void isEntryQueued() {
        WaitQueue queue = new WaitQueue();
        WaitQueueEntry entry1 = queue.initEntry(),
                       entry2 = queue.initEntry();
        assert !entry1.isQueued();
        assert !entry2.isQueued();

        entry1.add(WaitQueueEntry::defaultWakeFunction, new ParkState());
        entry2.addExclusive(WaitQueueEntry::defaultWakeFunction, new ParkState());
        assert entry1.isQueued();
        assert entry2.isQueued();

        entry1.delete();
        entry2.delete();
        assert !entry1.isQueued();
        assert !entry2.isQueued();
    }

    @Test
    void isEntryDeleted() {
        WaitQueue queue = new WaitQueue();
        WaitQueueEntry entry1 = queue.initEntry(),
                entry2 = queue.initEntry();
        assert !entry1.isDeleted();
        assert !entry2.isDeleted();

        entry1.add(WaitQueueEntry::defaultWakeFunction, new ParkState());
        entry2.addExclusive(WaitQueueEntry::defaultWakeFunction, new ParkState());
        assert !entry1.isDeleted();
        assert !entry2.isDeleted();

        entry1.delete();
        entry2.delete();
        assert entry1.isDeleted();
        assert entry2.isDeleted();
    }

    @Test
    void isQueueEmpty() {
        WaitQueue queue = new WaitQueue();
        WaitQueueEntry entry1 = queue.initEntry(),
                entry2 = queue.initEntry();
        assert queue.isEmpty();

        entry1.add(WaitQueueEntry::defaultWakeFunction, new ParkState());
        assert !queue.isEmpty();

        entry2.addExclusive(WaitQueueEntry::defaultWakeFunction, new ParkState());
        assert !queue.isEmpty();

        entry1.delete();
        assert !queue.isEmpty();

        entry2.delete();
        assert queue.isEmpty();
    }

    @Test
    void size() {
        WaitQueue queue = new WaitQueue();
        WaitQueueEntry entry1 = queue.initEntry(),
                entry2 = queue.initEntry();
        assert queue.size() == 0;

        entry1.add(WaitQueueEntry::defaultWakeFunction, new ParkState());
        assert queue.size() == 1;

        entry2.addExclusive(WaitQueueEntry::defaultWakeFunction, new ParkState());
        assert queue.size() == 2;

        entry1.delete();
        assert queue.size() == 1;

        entry2.delete();
        assert queue.size() == 0;
    }


    // ***** Wake up tests ***** //

    private void sleepWhile(Predicate<Object> predicate){
        while (predicate.test(null)) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        //while (queue.size() < sizeGoal) {
        //    try {
        //        Thread.sleep(1);
        //    } catch (InterruptedException e) {
        //        throw new RuntimeException(e);
        //    }
        //}
    }

    private void sleepUntilQueueSizeDescendsTo(WaitQueue queue, int sizeGoal){
        while (queue.size() > sizeGoal) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Tests the default wait function, default auto delete wake function
     * and the queue's wake up function (with the number of exclusive entries
     * to wake up as 1) using only NON-EXCLUSIVE entries.
     */
    @Test
    public void defaultWaitAndAutoDeleteFunctionsNonExclusive() throws InterruptedException {
        WaitQueue waitQueue = new WaitQueue();

        int nrThreads = 10;
        AtomicInteger alive = new AtomicInteger(0), // to check when all threads become alive
                      woken = new AtomicInteger(0); // to check when all thread have been woken up

        for(int i = 0; i < nrThreads; i++) {
            // create wait queue entry and task for each thread
            WaitQueueEntry entry = waitQueue.initEntry();
            Runnable task = () -> {
                // inform that the current thread is alive
                alive.incrementAndGet();
                // Adds a NON-EXCLUSIVE entry with an auto delete
                // entry on wake-up function, and the park state
                // required to wake up the current thread.
                entry.add(WaitQueueEntry::autoDeleteWakeFunction, new ParkState());
                // If the thread was successfully woken up,
                // informs that using the "woken" atomic variable
                if(WaitQueueEntry.defaultWaitFunctionTimeout(entry))
                    woken.incrementAndGet();
            };
            new Thread(task).start();
        }

        // busy-waits until all threads are alive.
        // I want to invoke the wakeUp() method
        // multiple times, therefore I don't want
        // to wait for all of them to have been queued
        while (alive.get() != 10)
            Thread.onSpinWait();

        // performs wake-up calls, with a difference
        // of 1 millisecond between each call, until
        // all threads have been woken up.
        while (woken.get() != 10) {
            // Set to wake-up one exclusive thread, however,
            // it does not matter as there are no exclusive entries
            waitQueue.wakeUp(0, 1, 0, 0);
            Thread.sleep(1);
        }

        // wait queue should be empty since every entry was woken up
        assert waitQueue.isEmpty();
    }

    /**
     * Tests the default wait function, default auto delete wake function
     * and the queue's wake up function (with the number of exclusive entries
     * to wake up as 1) using only EXCLUSIVE entries.
     */
    @Test
    public void defaultWaitAndAutoDeleteFunctionsExclusive() throws InterruptedException {
        WaitQueue waitQueue = new WaitQueue();

        int nrThreads = 10;
        AtomicInteger woken = new AtomicInteger(0); // to check when all thread have been woken up

        for(int i = 0; i < nrThreads; i++) {
            // create wait queue entry and task for each thread
            WaitQueueEntry entry = waitQueue.initEntry();
            Runnable task = () -> {
                // Adds an EXCLUSIVE entry with an auto delete
                // entry on wake-up function, and the park state
                // required to wake up the current thread.
                entry.addExclusive(WaitQueueEntry::autoDeleteWakeFunction, new ParkState());
                // If the thread was successfully woken up,
                // informs that using the "woken" atomic variable
                if(WaitQueueEntry.defaultWaitFunctionTimeout(entry))
                    woken.incrementAndGet();
            };
            new Thread(task).start();
        }

        // sleep to give time for the entries to be added to the queue
        sleepWhile(o -> waitQueue.size() < nrThreads);

        // performs wake-up calls, with a difference
        // of 1 millisecond between each call, until
        // all threads have been woken up.
        int i = 0;
        for (;woken.get() != 10; i++) {
            // wake-up call set to wake up one exclusive entry only
            waitQueue.wakeUp(0, 1, 0, 0);
            Thread.sleep(1);
        }

        // wait queue should be empty since every entry was woken up
        assert waitQueue.isEmpty();
        // Since the entries are registered as exclusive, only
        // one entry should be woken up per wake-up call, therefore
        // the minimum wake-up calls must match the number of entries.
        assert i >= nrThreads;
    }

    /**
     * Tests the default wait function, default auto delete wake function
     * and the queue's wake up function (with nrExclusive set to do a
     * global wake-up regardless of the exclusivity of the entries)
     * using only NON-EXCLUSIVE entries.
     */
    @Test
    public void defaultWaitAndAutoDeleteFunctionsGlobalWakeUpCalls() throws InterruptedException {
        WaitQueue waitQueue = new WaitQueue();

        int nrThreads = 10;
        AtomicInteger woken = new AtomicInteger(0); // to check when all thread have been woken up

        for(int i = 0; i < nrThreads; i++) {
            // create wait queue entry and task for each thread
            WaitQueueEntry entry = waitQueue.initEntry();
            int finalI = i;
            Runnable task = () -> {
                // Adds an EXCLUSIVE entry when "i" is odd
                // and a NON-EXCLUSIVE entry when "i" is even
                if(finalI % 2 == 0)
                    entry.add(WaitQueueEntry::autoDeleteWakeFunction, new ParkState());
                else
                    entry.addExclusive(WaitQueueEntry::autoDeleteWakeFunction, new ParkState());
                // If the thread was successfully woken up,
                // informs that using the "woken" atomic variable
                if(WaitQueueEntry.defaultWaitFunctionTimeout(entry))
                    woken.incrementAndGet();
            };
            new Thread(task).start();
        }

        // sleep to give time for the entries to be added to the queue
        sleepWhile(o -> waitQueue.size() < nrThreads);

        // The argument "nrExclusive" should be 0 or negative to wake
        // up all entries regardless of the exclusivity.
        waitQueue.wakeUp(0, 0, 0, 0);

        // sleep to give time for the threads to wake up
        sleepWhile(o -> woken.get() < nrThreads);

        // wait queue should be empty since every entry was woken up
        assert waitQueue.isEmpty();
    }

    /**
     * Tests the default wait function, default auto delete wake function
     * and the queue's wake up function (with nrExclusive set to wake up
     * a specific amount of exclusive entries)
     * using only NON-EXCLUSIVE entries.
     */
    @Test
    public void defaultWaitAndAutoDeleteFunctionsWakeNExclusive() throws InterruptedException {
        WaitQueue waitQueue = new WaitQueue();

        int nrThreads = 10;
        AtomicInteger woken = new AtomicInteger(0); // to check when all thread have been woken up

        for(int i = 0; i < nrThreads; i++) {
            // create wait queue entry and task for each thread
            WaitQueueEntry entry = waitQueue.initEntry();
            int finalI = i;
            Runnable task = () -> {
                // Adds an EXCLUSIVE entry when "i" is odd
                // and a NON-EXCLUSIVE entry when "i" is even
                if(finalI % 2 == 0)
                    entry.add(WaitQueueEntry::autoDeleteWakeFunction, new ParkState());
                else
                    entry.addExclusive(WaitQueueEntry::autoDeleteWakeFunction, new ParkState());
                // If the thread was successfully woken up,
                // informs that using the "woken" atomic variable
                if(WaitQueueEntry.defaultWaitFunctionTimeout(entry))
                    woken.incrementAndGet();
            };
            new Thread(task).start();
        }

        // sleep to give time for the entries to be added to the queue
        sleepWhile(o -> waitQueue.size() < nrThreads);

        // Set the number of exclusive entries to wake up
        // to half of the exclusive entries. Since this is
        // an integer division, the number may not correspond
        // to the actual half depending on the "nrThreads" value
        int nExcl = (nrThreads / 2) / 2;

        // Wakes up all non-exclusive entries and
        // "nExcl" exclusive entries
        waitQueue.wakeUp(0, nExcl, 0, 0);

        // sleep to give time for the threads to wake up
        sleepWhile(o -> woken.get() < (nrThreads / 2) + nExcl);

        // sleep an extra time to make sure no more threads
        // are waking up
        Thread.sleep(5);

        // The number of woken entries should correspond
        // to the addition of half of the entries (the non-exclusive entries)
        // and "nExcl"
        assert woken.get() == (nrThreads / 2) + nExcl;

        // Wait queue size should equal to the difference
        // between "nrThreads" and "woken"
        assert waitQueue.size() == nrThreads - woken.get();

        // Wait queue should only have exclusive entries
        AtomicBoolean allExclusive = new AtomicBoolean(true);
        IListNode.forEach(waitQueue.getHead(),entry -> {
            if((entry.getWaitFlags() & WaitQueueFlags.EXCLUSIVE) == 0)
                allExclusive.set(false);
        } );
        assert allExclusive.get();
    }

    /**
     * Tests the default wait function, DEFAULT WAKE FUNCTION (W/OUT AUTO DELETE)
     * and the queue's wake up function (with nrExclusive set to do a
     * global wake-up regardless of the exclusivity of the entries)
     * using only NON-EXCLUSIVE entries.
     */
    @Test
    public void defaultWakeFunctionWithoutDeleteGlobalWakeUp() throws InterruptedException {
        WaitQueue waitQueue = new WaitQueue();

        int nrThreads = 10;
        AtomicInteger alive = new AtomicInteger(0), // to check when all threads become alive
                woken = new AtomicInteger(0); // to check when all thread have been woken up

        for(int i = 0; i < nrThreads; i++) {
            // create wait queue entry and task for each thread
            WaitQueueEntry entry = waitQueue.initEntry();
            int finalI = i;
            Runnable task = () -> {
                // inform that the current thread is alive
                alive.incrementAndGet();
                // Adds an EXCLUSIVE entry when "i" is odd
                // and a NON-EXCLUSIVE entry when "i" is even
                if(finalI % 2 == 0)
                    entry.add(WaitQueueEntry::defaultWakeFunction, new ParkState());
                else
                    entry.addExclusive(WaitQueueEntry::defaultWakeFunction, new ParkState());
                // If the thread was successfully woken up,
                // informs that using the "woken" atomic variable
                if(WaitQueueEntry.defaultWaitFunctionTimeout(entry))
                    woken.incrementAndGet();
            };
            new Thread(task).start();
        }

        // sleep to give time for the entries to be added to the queue
        sleepWhile(o -> waitQueue.size() < nrThreads);

        // The argument "nrExclusive" should be 0 or negative to wake
        // up all entries regardless of the exclusivity.
        waitQueue.wakeUp(0, 0, 0, 0);

        // sleep to give time for the entries to be added to the queue
        sleepWhile(o -> woken.get() < nrThreads);

        // wait queue size should equal nrThreads since the wake-up
        // callback (defaultWakeFunction) does not delete entries
        assert waitQueue.size() == nrThreads;
    }

    /**
     * Finds the n-th exclusive entry
     * @param queue Queue where the exclusive entry is to be found
     * @param n index among exclusive entries
     * @return entry containing the entry and its index if found
      */
    Map.Entry<WaitQueueEntry, Integer> findNExclusiveEntry(WaitQueue queue, int n){
        WaitQueueEntry excl = null;
        int idxExcl = 0;
        if(n >= 0) {
            IListNode.Iterator<WaitQueueEntry> it = IListNode.iterator(queue.getHead());
            while (it.hasNext()) {
                WaitQueueEntry entry = it.next();
                if ((entry.getWaitFlags() & WaitQueueFlags.EXCLUSIVE) != 0
                        && n-- == 0) {
                    excl = entry;
                    break;
                }
                idxExcl++;
            }
        }

        return new AbstractMap.SimpleEntry<>(excl,idxExcl);
    }

    /**
     * Fair wake up makes exclusive entries move to the tail after being woken up
     * to allow other exclusive entries to be woken up.
     */
    @Test
    void fairWakeUp() throws InterruptedException {
        WaitQueue waitQueue = new WaitQueue();

        int nrThreads = 10;
        AtomicInteger woken = new AtomicInteger(0); // to check when all thread have been woken up

        // last woken exclusive entry
        AtomicReference<WaitQueueEntry> lastWokenExcl = new AtomicReference<>(null);

        for(int i = 0; i < nrThreads; i++) {
            // create wait queue entry and task for each thread
            WaitQueueEntry entry = waitQueue.initEntry();
            int finalI = i;
            Runnable task = () -> {
                // Adds an EXCLUSIVE entry when "i" is odd
                // and a NON-EXCLUSIVE entry when "i" is even
                if(finalI % 2 == 0)
                    entry.add(WaitQueueEntry::defaultWakeFunction, new ParkState());
                else
                    entry.addExclusive(WaitQueueEntry::defaultWakeFunction, new ParkState());
                // If the thread was successfully woken up,
                // informs that using the "woken" atomic variable
                if(WaitQueueEntry.defaultWaitFunctionTimeout(entry)) {
                    woken.incrementAndGet();
                    if((entry.getWaitFlags() & WaitQueueFlags.EXCLUSIVE) != 0)
                        lastWokenExcl.set(entry);
                }
            };
            new Thread(task).start();
        }

        // sleep to give time for the entries to be added to the queue
        sleepWhile(o -> waitQueue.size() < nrThreads);

        // save the index and the reference of the
        // first exclusive entry in the list
        var pair = findNExclusiveEntry(waitQueue, 0);
        WaitQueueEntry fstExcl = pair.getKey();
        int idxFstExcl = pair.getValue();

        // since half of the entries are exclusive
        // and exclusive entries are inserted at the tail,
        // then the index should equal to the number of
        // non-exclusive entries, i.e., half the total entries (nrThreads / 2)
        assert idxFstExcl == nrThreads / 2;
        assert fstExcl != null;

        // The "non-fair" wake up does not move exclusive entries to
        // the tail, therefore, invoking this method with "nrExclusive"
        // equal to 1 (i.e. to wake up a single exclusive entry)
        // should prove that all non-exclusive entries and only one
        // exclusive entry are woken up. Also, the woken up exclusive
        // entry should prove to not have switched places.
        waitQueue.wakeUp(0, 1, 0, 0);

        // sleep to give time for the entries to be added to the queue
        sleepWhile(o -> woken.get() < nrThreads / 2 + 1);

        // sleep a bit more to ensure no more threads are being woken up
        Thread.sleep(5);

        // assert the number of woken threads corresponds
        // to half of the entries (non-exclusive entries)
        // plus one exclusive entry
        assert woken.get() == nrThreads / 2 + 1;

        // assert that the last woken entry corresponds to
        // the first exclusive entry found previously
        assert fstExcl == lastWokenExcl.get();

        // assert the woken exclusive entry has not changed position
        pair = findNExclusiveEntry(waitQueue, 0);
        assert fstExcl == pair.getKey()
                && idxFstExcl == pair.getValue();

        // wait queue size should equal nrThreads since the wake-up
        // callback (defaultWakeFunction) does not delete entries
        assert waitQueue.size() == nrThreads;

        // Now, after doing a fair wake-up with "nrExclusive" equal to 1,
        // the wake-up call to entries that were previously woken will not
        // be successful (as the threads have woken up and are not showing
        // the intent to be woken up). The first exclusive entry has also
        // been woken up, therefore, since its wake-up call will not be successful,
        // the fair wake-up call will continue to search for an exclusive entry
        // that can be woken up successfully. This wil result in the second exclusive
        // entry being woken up and moved to the tail.
        WaitQueueEntry sndExcl = IListNode.get(waitQueue.getHead(), idxFstExcl + 1);

        waitQueue.fairWakeUp(0,1,0,0);

        // sleep a bit to make sure any threads that
        // want to wake up, have time to do so
        Thread.sleep(5);

        // assert woken increased by one the same
        assert woken.get() == nrThreads / 2 + 2;

        // assert the size of the queue remains the same
        assert waitQueue.size() == nrThreads;

        // assert the first exclusive entry remained in the same place
        assert IListNode.get(waitQueue.getHead(), idxFstExcl) == fstExcl;

        // assert the second exclusive entry was moved to the tail, i.e.,
        // is now the last entry
        assert IListNode.isLast(sndExcl.getNode(), waitQueue.getHead());
    }

    /**
     * Fair wake up makes exclusive entries move to the tail after being woken up
     * to allow other exclusive entries to be woken up. However, the entries should
     * not be moved if the entry was deleted by the wake function. To test this,
     * the exclusive entries will be registered with the function "autoDeleteWakeFunction"
     */
    @Test
    void fairWakeUpWithAutoDeleteWakeFunction() throws InterruptedException {
        WaitQueue waitQueue = new WaitQueue();

        int nrThreads = 10;
        AtomicInteger woken = new AtomicInteger(0); // to check when all thread have been woken up

        // will act as a queue to check the order in which
        // the exclusive entries where woken up
        Map<WaitQueueEntry,Boolean> exclEntries = new ConcurrentHashMap<>();

        for(int i = 0; i < nrThreads; i++) {
            // create wait queue entry and task for each thread
            WaitQueueEntry entry = waitQueue.initEntry();
            int finalI = i;
            Runnable task = () -> {
                // Adds an EXCLUSIVE entry when "i" is odd
                // and a NON-EXCLUSIVE entry when "i" is even
                if(finalI % 2 == 0)
                    entry.add(WaitQueueEntry::defaultWakeFunction, new ParkState());
                else
                    entry.addExclusive(WaitQueueEntry::autoDeleteWakeFunction, new ParkState());
                // If the thread was successfully woken up,
                // informs that using the "woken" atomic variable
                if(WaitQueueEntry.defaultWaitFunctionTimeout(entry)) {
                    woken.incrementAndGet();
                    if((entry.getWaitFlags() & WaitQueueFlags.EXCLUSIVE) != 0)
                        exclEntries.put(entry,true);
                }
            };
            new Thread(task).start();
        }

        // sleep to give time for the entries to be added to the queue
        sleepWhile(o -> waitQueue.size() < nrThreads);

        // save the reference of the first two exclusive entries
        WaitQueueEntry fstExcl = findNExclusiveEntry(waitQueue, 0).getKey(),
                       sndExcl = findNExclusiveEntry(waitQueue, 1).getKey();
        assert fstExcl != null && sndExcl != null;

        // Fair wake up is set to wake up two exclusive entries
        waitQueue.fairWakeUp(0, 2, 0, 0);

        // sleep to give time for the entries to be added to the queue
        sleepWhile(o -> woken.get() < nrThreads / 2 + 1);

        // sleep a bit more to ensure no more threads are being woken up
        Thread.sleep(5);

        // assert the first two exclusive entries were
        // the ones that where woken up
        assert exclEntries.containsKey(fstExcl);
        assert exclEntries.containsKey(sndExcl);

        // assert the number of woken threads corresponds
        // to half of the entries (non-exclusive entries)
        // plus two (exclusive entries)
        assert woken.get() == nrThreads / 2 + 2;

        // assert the woken exclusive entries have been deleted
        assert fstExcl.isDeleted();
        assert sndExcl.isDeleted();

        // wait queue size should be short in two entries
        assert waitQueue.size() == nrThreads - 2;

        // Fair wake up all remaining exclusive entries
        waitQueue.fairWakeUp(0, 0, 0, 0);

        // sleep to give time for threads to wake up
        // Only the remain exclusive entries need to
        // wake up for woken to reach a value equal to "nrThreads"
        sleepWhile(o -> woken.get() < nrThreads);

        // sleep a bit more to ensure no more threads are being woken up
        Thread.sleep(5);

        // assert wait queue size is half
        assert waitQueue.size() == nrThreads / 2;
        // assert only non-exclusive entries remain, i.e.,
        // a first exclusive entry could not be found in the queue
        assert findNExclusiveEntry(waitQueue, 0).getKey() == null;
    }
}