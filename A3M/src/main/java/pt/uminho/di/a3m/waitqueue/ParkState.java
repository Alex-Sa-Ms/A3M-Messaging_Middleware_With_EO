package pt.uminho.di.a3m.waitqueue;

import pt.uminho.di.a3m.auxiliary.Timeout;

import java.util.concurrent.atomic.AtomicBoolean;

public class ParkState {
    private boolean parked = false;

    /**
     * Creates a park state instance and sets
     * starting park state as "false".
     */
    public ParkState(){
        this(false);
    }

    /**
     * Creates a park state instance and sets
     * the starting park state.
     */
    public ParkState(boolean parkState){
        this.parked = parkState;
    }

    /**
     * Waits to be unparked. If state is already "unparked", then returns immediately.
     * <p>If the thread is interrupted, the parked state is set to false.</p>
     * @throws InterruptedException if interrupted while waiting.
     */
    public synchronized void waitForUnpark() throws InterruptedException {
        try {
            while (parked) wait();
        } finally {
            parked = false;
        }
    }

    /**
     * Waits to be unparked. If state is already "unparked", then returns immediately.
     * <p>If the thread is interrupted, the parked state is set to false.</p>
     * @param timeout amount of time willing to be waited for an unpark.
     * @return true when unparked. false, otherwise.
     * @throws InterruptedException if interrupted while waiting.
     */
    public synchronized boolean waitForUnpark(Long timeout) throws InterruptedException {
        try {
            if(timeout != null)
                waitForUnparkUntil(System.currentTimeMillis() + timeout);
            else
                waitForUnpark();
            return !parked;
        } finally {
            parked = false;
        }
    }

    /**
     * Waits to be unparked. If state is already "unparked", then returns immediately.
     * <p>If the thread is interrupted, the parked state is set to false.</p>
     * @param deadline limit until which the waiter is willing to wait for an unpark.
     * @return true when unparked. false, otherwise.
     * @throws InterruptedException if interrupted while waiting.
     */
    public synchronized boolean waitForUnparkUntil(Long deadline) throws InterruptedException {
        try {
            if(deadline != null) {
                long timeout;
                while (parked && (timeout = deadline - System.currentTimeMillis()) > 0)
                    wait(timeout);
            } else {
                waitForUnpark();
            }
            return !parked;
        } finally {
            parked = false;
        }
    }

    /**
     * Set park state.
     */
    public synchronized void setParkState(boolean parked){
        this.parked = parked;
    }

    /**
     * @return parked state
     */
    public synchronized boolean getParked(){
        return parked;
    }

    /**
     * Sets park state to true, then waits to be unparked.
     * <p>If the thread is interrupted, the parked state is set to false.</p>
     * @param timeout amount of time willing to be waited for an unpark.
     * @return true when unparked. false, otherwise.
     * @throws InterruptedException if interrupted while waiting.
     */
    public synchronized boolean parkAndWaitToUnpark(Long timeout) throws InterruptedException {
        try {
            parked = true;
            waitForUnpark(timeout);
            return !parked;
        } finally {
            parked = false;
        }
    }

    /**
     * Sets park state to true, then waits to be unparked.
     * <p>If the thread is interrupted, the parked state is set to false.</p>
     * @param deadline limit until which the waiter is willing to wait for an unpark.
     * @return true when unparked. false, otherwise.
     * @throws InterruptedException if interrupted while waiting.
     */
    public synchronized boolean parkAndWaitToUnparkUntil(Long deadline) throws InterruptedException {
        try {
            parked = true;
            waitForUnparkUntil(deadline);
            return !parked;
        } finally {
            parked = false;
        }
    }

    /**
     * Unparks if park state was set to true.
     * @return true if unpark was performed. false otherwise.
     */
    public synchronized boolean unpark() {
        if(parked){
            parked = false;
            notify();
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "{parked=" + parked + '}';
    }
}
