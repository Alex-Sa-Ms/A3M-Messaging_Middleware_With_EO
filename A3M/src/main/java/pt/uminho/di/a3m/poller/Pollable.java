package pt.uminho.di.a3m.poller;

import pt.uminho.di.a3m.poller.exceptions.PollableClosedException;

public interface Pollable {
    /**
     * @return locally unique identifier of the pollable
     */
    Object getId();

    /**
     * Returns currently available events and queues the caller
     * if a PollQueuingFunc is provided.
     * <p> If for some reason, the pollable does not allow queuing,
     * such as when the pollable is closed to polling (queuing event waiters),
     * a wait queue entry must not be passed to the PollQueueFunc function,
     * i.e., null should be passed instead.
     * @param pt poll table which may contain a queuing function and
     *           a private object if the caller intends to add itself to
     *           the wait queue of the pollable.
     * @return event mask with currently available events.
     */
    int poll(PollTable pt);

}
