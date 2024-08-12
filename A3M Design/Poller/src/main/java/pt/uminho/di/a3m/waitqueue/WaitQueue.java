package pt.uminho.di.a3m.waitqueue;

import pt.uminho.di.a3m.list.ListNode;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class WaitQueue{
    private final Lock lock = new ReentrantLock();
    private final ListNode<WaitQueueEntry> head = ListNode.init();
    private int size = 0;

    // ***** package methods ***** //

    ListNode<WaitQueueEntry> getHead() {
        return head;
    }

    // Adds non-exclusive wait entry at the head of the list.
    // Must have lock acquired
    void _addFirst(ListNode<WaitQueueEntry> node) {
        ListNode.addFirst(node, head);
        size++;
    }

    // Adds non-exclusive wait entry at the head of the list.
    void addFirst(ListNode<WaitQueueEntry> node) {
        try{
            lock.lock();
            _addFirst(node);
        }finally {
            lock.unlock();
        }
    }

    // Adds non-exclusive wait entry at the tail of the list.
    // Must have lock acquired
    void _addLast(ListNode<WaitQueueEntry> node) {
        ListNode.addLast(node, head);
        size++;
    }

    // Adds non-exclusive wait entry at the tail of the list.
    void addLast(ListNode<WaitQueueEntry> node) {
        try{
            lock.lock();
            _addLast(node);
        }finally {
            lock.unlock();
        }
    }

    // Delete wait entry at the head of the list.
    // Must have lock acquired
    void _delete(ListNode<WaitQueueEntry> node) {
        ListNode.delete(node);
        size--;
    }

    // Delete wait entry at the head of the list.
    void delete(ListNode<WaitQueueEntry> node) {
        try{
            lock.lock();
            _delete(node);
        }finally {
            lock.unlock();
        }
    }

    Lock getLock() {
        return lock;
    }

    // ***** public methods ***** //

    public WaitQueueEntry initEntry() {
        return new WaitQueueEntry(this, 0, null, null, null);
    }

    public int wakeUp(int mode, int nrExclusive, int wakeFlags, int key) {
        try{
            lock.lock();
            ListNode.Iterator<WaitQueueEntry> it = ListNode.iterator(head);
            // "current" and "next" variables are required to execute a loop
            // safe against removals done by the wake function
            WaitQueueEntry curr = it.hasNext() ? it.next() : null,
                               next;
            while(curr != null){
                // Saves the next entry to be processed, as the current
                // entry's pointers to the next entry may be updated by
                // the wake function.
                next = it.hasNext() ? it.next() : null;
                int ret = curr.getFunc().apply(curr, mode, wakeFlags, key);
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

    public int fairWakeUp(int mode, int nrExclusive, int wakeFlags, int key) {
        try{
            lock.lock();
            ListNode.Iterator<WaitQueueEntry> it = ListNode.iterator(head);
            WaitQueueEntry curr = it.hasNext() ? it.next() : null,
                               next,
                               last;
            // Last entry. Since exclusive entries are moved to the tail
            // upon being woken up, it is required to break the loop
            // after waking up the last entry.
            last = ListNode.getLast(head).getObject();
            while(curr != null){
                next = it.hasNext() ? it.next() : null;
                int ret = curr.getFunc().apply(curr, mode, wakeFlags, key);
                if(ret < 0)
                    break;
                if(ret != 0 && (curr.getWaitFlags() & WaitQueueFlags.EXCLUSIVE) != 0){
                    // Moves woken up exclusive entries to the tail,
                    // if they were not deleted. Shouldn't interfere
                    // with the iterator as the iterator is positioned
                    // in the node next to the current one
                    if(curr.isQueued())
                        ListNode.moveToLast(curr.getNode(), head);
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

    public boolean isEmpty() {
        try{
            lock.lock();
            return ListNode.isEmpty(head);
        }finally {
            lock.unlock();
        }
    }

    public int size(){
        try{
            lock.lock();
            assert size == ListNode.size(head);
            return size;
        }finally {
            lock.unlock();
        }
    }
}
