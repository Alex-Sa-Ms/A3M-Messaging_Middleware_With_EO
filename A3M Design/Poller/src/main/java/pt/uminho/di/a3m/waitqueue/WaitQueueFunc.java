package pt.uminho.di.a3m.waitqueue;

@FunctionalInterface
public interface WaitQueueFunc {
    /**
     *
     * @param mode Wake up mode. For usability, to be used under the wait queue holder semantics.
     *             May be used to determine which tasks should be woken up.
     * @param flags Wake up flags. For usability, to be used under the wait queue holder semantics.
     * @param key Wake up key used by waiters to check if the event is of interest
     * @return NEGATIVE value if there was an error or
     * if the function was executed by a waiter with priority.
     * 0 (ZERO) if the wake-up was not performed.
     * POSITIVE value if the task was handled by an exclusive waiter.
     */
    int apply(WaitQueueEntry entry, int mode, int flags, int key);
}
