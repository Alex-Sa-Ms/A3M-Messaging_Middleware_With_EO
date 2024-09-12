package pt.uminho.di.a3m.sockets.auxiliary;

import pt.uminho.di.a3m.waitqueue.ParkState;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;

/**
 * Park State subclass that includes events of interest and the "notify if none" flag.
 */
public class ParkStateSocket extends ParkState {
    private final int events; // events of interest to the waiter
    private final boolean notifyIfNone;
    private WaitQueueEntry wait = null;

    // wake up mode to inform when there aren't any links
    public static final int CHECK_IF_NONE = 1;

    public ParkStateSocket(boolean parkState, boolean notifyIfNone) {
        super(parkState);
        this.notifyIfNone = notifyIfNone;
        this.events = 0;
    }

    public ParkStateSocket(boolean parkState, int events, boolean notifyIfNone) {
        super(parkState);
        this.events = events;
        this.notifyIfNone = notifyIfNone;
    }

    public int getEvents() {
        return events;
    }

    public boolean isNotifyIfNone() {
        return notifyIfNone;
    }

    public WaitQueueEntry getWaitQueueEntry() {
        return wait;
    }

    public void setWaitQueueEntry(WaitQueueEntry wait) {
        this.wait = wait;
    }
}
