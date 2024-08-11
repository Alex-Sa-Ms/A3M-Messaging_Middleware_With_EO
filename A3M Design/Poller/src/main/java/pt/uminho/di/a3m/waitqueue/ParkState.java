package pt.uminho.di.a3m.waitqueue;

import java.util.concurrent.atomic.AtomicBoolean;

public class ParkState {
    public final Thread thread;
    public final AtomicBoolean parked = new AtomicBoolean(false);

    /**
     * Creates a park state instance, and sets the 
     * current thread as the owner of the instance.
     */
    public ParkState() {
        thread = Thread.currentThread();
    }

    /**
     * Creates a park state instance, and sets the 
     * given thread as the owner of the instance.
     * @param thread owner of the park state
     */
    public ParkState(Thread thread) {
        this.thread = thread;
    }

    @Override
    public String toString() {
        return "{" +
                "t=" + thread.getName() + " | " + thread.getState() +
                ", p=" + parked +
                '}';
    }
}
