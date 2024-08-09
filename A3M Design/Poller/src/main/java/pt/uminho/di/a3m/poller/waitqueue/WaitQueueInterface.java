package pt.uminho.di.a3m.poller.waitqueue;

public interface WaitQueueInterface {
    // Initializes a wait queue entry so that it can be added to the queue
    // at any moment through an add() method of the entry.
    WaitQueueEntry initEntry();

    // Wakes up a maximum of "nrExclusive" exclusive entries and all non-exclusive entries.
    // A "nrExclusive" equal or less than 0 results in all entries being woken up regardless of their exclusivity.
    int wakeUp(int mode, int nrExclusive, int wakeFlags, int key);

    // Similar to wake up but exclusive entries that are woken up are moved to
    // the tail to enable other exclusive entries to be woken up by future wake-up calls.
    int fairWakeUp(int mode, int nrExclusive, int wakeFlags, int key);

    boolean isEmpty();
}
