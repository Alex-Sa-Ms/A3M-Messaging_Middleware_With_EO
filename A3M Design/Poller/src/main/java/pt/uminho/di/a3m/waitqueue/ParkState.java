package pt.uminho.di.a3m.waitqueue;

import java.util.concurrent.atomic.AtomicBoolean;

public class ParkState {
    public final Thread thread;
    public final AtomicBoolean parked = new AtomicBoolean();

    /**
     * Creates a park state instance, sets the
     * given thread as the owner of the instance
     * and starting park state as "parkState".
     */
    public ParkState(Thread thread, boolean parkState) {
        if(thread == null)
            thread = Thread.currentThread();
        this.thread = thread;
        this.parked.set(parkState);
    }

    /**
     * Creates a park state instance, sets the
     * current thread as the owner of the instance
     * and starting park state as "false".
     */
    public ParkState(){
        this(Thread.currentThread(), false);
    }

    /**
     * Creates a park state instance, sets the
     * current thread as the owner of the instance
     * and starting park state as "parkState".
     */
    public ParkState(boolean parkState){
        this(null, parkState);
    }

    @Override
    public String toString() {
        return "{" +
                "t=" + thread.getName() + " | " + thread.getState() +
                ", p=" + parked +
                '}';
    }
}
