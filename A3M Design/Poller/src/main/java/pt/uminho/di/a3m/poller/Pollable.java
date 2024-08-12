package pt.uminho.di.a3m.poller;

public interface Pollable {
    /**
     * @return locally unique identifier of the pollable
     */
    Object getId();

    /**
     * Returns currently available events.
     * <p> If the pollable is closed, the
     * POLLFREE flag must be returned and
     * the queueing function should not be
     * executed.
     * @param pt poll table which contains the events
     *           of interest required to inform the
     *           availability of the pollable in regard
     *           to those events. It may also contain
     *           a queuing function and a private object
     *           if the caller intends to add itself to
     *           the wait queue of the pollable.
     * @return event mask with currently available events.
     *
     */
    int poll(PollTable pt);
}
