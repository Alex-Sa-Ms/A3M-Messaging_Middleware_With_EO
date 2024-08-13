package pt.uminho.di.a3m.poller;

import pt.uminho.di.a3m.poller.exceptions.PollableClosedException;

public interface Pollable {
    /**
     * @return locally unique identifier of the pollable
     */
    Object getId();

    /**
     * Returns currently available events.
     * <p> If the pollable is closed to polling (queuing event waiters),
     * a wait queue entry must not be passed to the wait queue function,
     * i.e., null should be passed instead.
     * @param pt poll table which contains the events of interest required
     *           to inform the availability of the pollable in regard to
     *           those events. It may also contain a queuing function and
     *           a private object if the caller intends to add itself to
     *           the wait queue of the pollable.
     * @return event mask with currently available events.
     */
    int poll(PollTable pt);

    // solved by passing null as the wait queue entry to the poll queuing function
    ///**
    // * If the pollable accepts new event waiters (i.e. is not closed
    // * to polling) the method must use the poll table to add
    // * the caller of the method to the wait queue and return an event mask.
    // * Otherwise, if it is closed, then a PollableClosedException must be
    // * thrown to inform that the caller will not be queued.
    // * @return event mask with currently available events
    // * @throws PollableClosedException when the pollable is closed to polling,
    // * and no longer allows queuing of waiters.
    // */
    //int tryPoll(PollTable pt) throws PollableClosedException;
}
