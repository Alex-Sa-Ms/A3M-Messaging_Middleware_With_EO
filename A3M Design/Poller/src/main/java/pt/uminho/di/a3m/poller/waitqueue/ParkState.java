package pt.uminho.di.a3m.poller.waitqueue;

import java.util.concurrent.atomic.AtomicBoolean;

public class ParkState {
    public final Thread thread;
    public final AtomicBoolean parked = new AtomicBoolean(true);

    public ParkState(Thread thread) {
        this.thread = thread;
    }
}
