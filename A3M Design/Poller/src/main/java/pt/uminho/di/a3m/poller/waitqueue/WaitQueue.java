package pt.uminho.di.a3m.poller.waitqueue;

import pt.uminho.di.a3m.list.ListNode;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class WaitQueue implements WaitQueueInterface{
    private final Lock lock = new ReentrantLock();
    private final ListNode<WaitQueueEntryImpl> head = ListNode.init();

    // ***** private methods ***** //

    private WaitQueueEntryImpl createEntryImpl(int waitFlags, WaitQueueFunc func, Object priv){
        if(func == null || priv == null)
            throw new IllegalArgumentException("Could not initialize wait queue entry:" +
                    "A wake function and a private object linked to the waiter are required.");
        WaitQueueEntryImpl entry = new WaitQueueEntryImpl(waitFlags, func, priv);
        ListNode<WaitQueueEntryImpl> node = ListNode.create(entry);
        entry.setNode(node);
        return entry;
    }

    // ***** protected methods ***** //

    // Adds non-exclusive wait entry at the head of the list.
    protected void addEntry(WaitQueueEntry wait, WaitQueueFunc func, Object priv) {
        try{
            lock.lock();
            assert wait != null && wait.getEntry() != null;
            WaitQueueEntryImpl entry = createEntryImpl(WaitQueueFlags.NO_FLAGS, func, priv);
            ListNode.addFirst(entry.getNode(), head);
            wait.setEntry(entry);
        }finally {
            lock.unlock();
        }
    }

    // Adds non-exclusive wait entry at the tail of the list.
    protected void addExclusiveEntry(WaitQueueEntry wait, WaitQueueFunc func, Object priv) {
        try{
            lock.lock();
            assert wait != null && wait.getEntry() != null;
            WaitQueueEntryImpl entry = createEntryImpl(WaitQueueFlags.EXCLUSIVE, func, priv);
            ListNode.addLast(entry.getNode(), head);
            wait.setEntry(entry);
        }finally {
            lock.unlock();
        }
    }

    // Delete wait entry at the head of the list.
    protected void deleteEntry(WaitQueueEntry wait) {
        try{
            lock.lock();
            assert wait != null && wait.getEntry() != null && wait.getEntry().getNode() != null;
            // remove entry from the list
            ListNode.delete(wait.getEntry().getNode());
            // to prevent unwanted insertions, set entry and queue to null.
            wait.setEntry(null);
            wait.setQueue(null);
        }finally {
            lock.unlock();
        }
    }

    // ***** public methods ***** //

    @Override
    public WaitQueueEntry initEntry() {
        return new WaitQueueEntry(this);
    }

    @Override
    public int wakeUp(int mode, int nrExclusive, int wakeFlags, int key) {
        try{
            lock.lock();
            ListNode.Iterator<WaitQueueEntryImpl> it = ListNode.iterator(head);
            // "current" and "next" variables are required to execute a loop
            // safe against removals done by the wake function
            WaitQueueEntryImpl curr = it.hasNext() ? it.next() : null,
                               next;
            while(curr != null){
                // Saves the next entry to be processed, as the current
                // entry's pointers to the next entry may be updated by
                // the wake function.
                next = it.hasNext() ? it.next() : null;
                int ret = curr.getFunc().apply(mode, wakeFlags, key);
                // Wake up until there is an error or a priority task handles the event
                // (and does not want other waiters to be woken up)
                if(ret < 0)
                    break;
                // If the number of exclusive waiters woken up reaches 0, then stop waking up
                // waiters. Exclusive waiters are assumed to be added at the tail, therefore,
                // all non-exclusive waiters (if existent) have already been woken up.
                if(ret != 0 && (curr.getWaitFlags() & WaitQueueFlags.EXCLUSIVE) != 0 && (--nrExclusive) == 0)
                    break;
                curr = next;
            }
        }finally {
            lock.unlock();
        }
        return 0;
    }

    @Override
    public int fairWakeUp(int mode, int nrExclusive, int wakeFlags, int key) {
        try{
            lock.lock();
            ListNode.Iterator<WaitQueueEntryImpl> it = ListNode.iterator(head);
            WaitQueueEntryImpl curr = it.hasNext() ? it.next() : null,
                               next,
                               last;
            // Last entry. Since exclusive entries are moved to the tail
            // upon being woken up, it is required to break the loop
            // after waking up the last entry.
            last = ListNode.getLast(head).getObject();
            while(curr != null){
                next = it.hasNext() ? it.next() : null;
                int ret = curr.getFunc().apply(mode, wakeFlags, key);
                if(ret < 0)
                    break;
                if(ret != 0 && (curr.getWaitFlags() & WaitQueueFlags.EXCLUSIVE) != 0){
                    // Moves woken up exclusive entries to the tail,
                    // if they were not deleted. Shouldn't interfere
                    // with the iterator as the iterator is positioned
                    // in the node next to the current one
                    ListNode.moveToTail(curr.getNode(), head);
                    // stops waking up if the number of exclusive entries have been woken up
                    // or if the recorded last entry before modifications is reached.
                    if(--nrExclusive == 0 || curr == last)
                        break;
                }
                curr = next;
            }
        }finally {
            lock.unlock();
        }
        return 0;
    }

    @Override
    public boolean isEmpty() {
        try{
            lock.lock();
            return ListNode.isEmpty(head);
        }finally {
            lock.unlock();
        }
    }

    // ***** Default Wake function ***** //

    // TODO - default wake function in case a "null" function is provided. Check the autoremove_wake_function
    //          employed when a "null" function is provided in the poll table.
    //          When "null" function is provided, the private object is set to the current task
    //          to be used by the autoremove_wake_function.
    //          The autoremove_wake_function wakes up the task identified saved in the private object.
    public static int defaultWakeFunction(){
        return 0; // TODO
    }

    public static int autoRemoveWakeFunction(){
        return 0; // TODO
    }
}
