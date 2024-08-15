package pt.uminho.di.a3m.auxiliary;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Spin lock implementation. Spin because it does not
 * sleep while trying to acquire the lock. The acquisition
 * is done in a busy-loop. Supports nesting.
 */
//  Seems like the SpinLock is worse than the normal lock.
public class SpinLock implements Lock {
    private AtomicReference<Thread> holder = new AtomicReference<>(null);
    private int depth = 0; // nesting depth

    @Override
    public void lock() {
        boolean ret = holder.get() == Thread.currentThread();
        if(!ret) {
            while(!holder.compareAndSet(null, Thread.currentThread()))
                Thread.onSpinWait();
            ret = holder.get() == Thread.currentThread();
        }
        if(ret)
            depth++;
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        boolean ret = holder.get() == Thread.currentThread();
        if(!ret) {
            while(!Thread.currentThread().isInterrupted()
                    && !holder.compareAndSet(null, Thread.currentThread()))
                Thread.onSpinWait();
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException();
            ret = holder.get() == Thread.currentThread();
        }
        if(ret)
            depth++;
    }

    @Override
    public boolean tryLock() {
        boolean ret = holder.get() == Thread.currentThread();
        if(!ret)
            ret = holder.compareAndSet(null, Thread.currentThread());
        if(ret)
            depth++;
        return ret;
    }

    /**
     *
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the {@code time} argument
     * @return if locking was a success
     * @throws InterruptedException Not thrown. Spin lock should only
     * be used when the locking contention time is assumed to be minuscule.
     */
    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        if(unit == null)
            throw new IllegalArgumentException("Unit is null.");
        boolean ret = holder.get() == Thread.currentThread();
        if(!ret) {
            long endTimeNanos = System.nanoTime() + unit.toNanos(time);
            while(!holder.compareAndSet(null, Thread.currentThread())
                    && endTimeNanos - System.nanoTime() > 0)
                Thread.onSpinWait();
            ret = holder.get() == Thread.currentThread();
        }
        if(ret)
            depth++;
        return ret;
    }

    @Override
    public void unlock() {
        if(holder.get() == Thread.currentThread()) {
            depth--;
            if(depth == 0)
                holder.set(null);
        }
    }

    /**
     * Conditions not supported as spin lock is not
     * the ideal for problems that require conditions.
     * @return null
     */
    @Override
    public Condition newCondition() {
        return null;
    }


    void testLock(Lock lock){
        long time = System.nanoTime();
        int nrThreads = 10;
        int nrAcquisitions = 1000000;
        final int[] acqs = { 0 };
        for (int i = 0; i < nrThreads; i++){
            new Thread(()->{
                boolean ret = false;
                while(!ret) {
                    lock.lock();
                    if (acqs[0] < 10000)
                        acqs[0]++;
                    else
                        ret = true;
                    lock.unlock();
                }
            }).start();
        }
        time = System.nanoTime() - time;
        System.out.println("Time: " + time);
    }

    @Test
    void testSpinLock(){
        SpinLock lock = new SpinLock();
        testLock(lock);
        System.gc();
    }

    @Test
    void testReeentrantLock(){
        ReentrantLock lock = new ReentrantLock();
        testLock(lock);
        System.gc();
    }
}
