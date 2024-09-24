package pt.uminho.di.a3m.sockets.publish_subscribe;

import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.poller.PollQueueingFunc;
import pt.uminho.di.a3m.poller.PollTable;
import pt.uminho.di.a3m.sockets.auxiliary.LinkSocketWatchedWithOrder;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;
import pt.uminho.di.a3m.waitqueue.WaitQueueFunc;

import java.util.concurrent.atomic.AtomicInteger;

class PubLinkSocket extends LinkSocketWatchedWithOrder {
    // Watches POLLOUT events so that notifying
    // reservation waiters is possible
    private volatile WaitQueueEntry creditsWatcher = null;

    // Object used to serve as a monitor for operations related to reservations
    private final Object reservationsLock = new Object();

    // - Counter of reservations to send data messages.
    // - To send a data message a reservation must be made first.
    // - Reservation counter cannot be superior to the number of credits.
    // This means that changing the capacity of a link can lead to unwanted behavior.
    private int reservations = 0;

    // - Counter of reservations of threads that are currently attempting to send.
    // - Ensures consistency of behavior when a thread attempting to send a date message
    // releases the reservations' synchronized monitor temporarily. Releasing the reservations'
    // synchronized monitor is required to avoid deadlocks with link's wait queue lock.
    // If the synchronized monitor is not released before doing a write operation on the link,
    // the locking mechanisms are acquired in the following order:
    //      1. reservations' synchronized monitor;
    //      2. link's wait queue lock.
    // Now, for instance, if a flow control message is received, the middleware thread
    // acquires the locking mechanisms in the order that follows:
    //      1. link's wait queue lock;
    //      2. reservations' synchronized monitor.
    // Since, the acquisition of the synchronized monitor is mandatory when a POLLOUT
    // event is received by the credits watcher callback (to notify any waiters trying
    // to make a reservation), then it is imperative that any writing operations on the
    // link is not done while holding the synchronized monitor.
    private int lockedReservations = 0;

    /**
     * Sets a credits watcher.
     * @implNote Assumes the underlying link to have been set up,
     * so that a poll() operation can be performed. And, that it
     * is not invoked in a concurrency context.
     */
    public final void setCreditsWatcher() {
        if(creditsWatcher == null) {
            WaitQueueFunc wakeFunc = (entry, mode, flags, key) -> {
                int iKey = (int) key;
                // if POLLOUT event was received, then notify a waiter
                // waiting to reserve a credit
                if((iKey & PollFlags.POLLOUT) != 0) {
                    synchronized (reservationsLock){
                        if(reservations < getOutgoingCredits())
                            reservationsLock.notify();
                    }
                }
                return 1;
            };
            PollQueueingFunc queueingFunc = (p, wait, pt) -> {
                if(wait != null){
                    creditsWatcher = wait;
                    wait.add(wakeFunc, this);
                }
            };
            PollTable pt = new PollTable(PollFlags.POLLOUT,null,queueingFunc);
            this.poll(pt);
        }
    }

    /**
     * Removes the credits watcher and notifies
     * any waiters waiting to reserve a credit.
     * @implNote Assumes an invocation outside a concurrency context.
     */
    public void removeCreditsWatcherAndStopReservations(){
        if(creditsWatcher != null){
            creditsWatcher.delete();
            creditsWatcher = null;
            synchronized (reservationsLock) { notifyAll(); }
        }
    }

    /**
     * Attempts to reserve a credit to send a data message.
     * @return true if a credit was reserved. false, otherwise.
     */
    public boolean tryReserve(){
        synchronized (reservationsLock) {
            boolean reserved = reservations < getOutgoingCredits();
            if (reserved) reservations++;
            return reserved;
        }
    }

    /**
     * Attempts to reserve a credit to send a data message within
     * the deadline.
     * @param deadline deadline to reserve a credit
     * @return true if a credit was reserved. false, otherwise.
     * @throws LinkClosedException if link was closed.
     */
    public boolean tryReserveUntil(Long deadline) throws InterruptedException {
        synchronized (reservationsLock) {
            // waits until reserving a credit is possible
            int credits;
            while (reservations >= (credits = getOutgoingCredits())
                    && !Timeout.hasTimedOut(deadline)) {
                if (creditsWatcher == null)
                    throw new LinkClosedException();
                if (deadline == null)
                    reservationsLock.wait();
                else
                    reservationsLock.wait(deadline - System.currentTimeMillis());
            }

            if (creditsWatcher == null)
                throw new LinkClosedException();

            boolean reserved = reservations < credits;
            if (reserved) {
                reservations++;
                if (reservations < credits)
                    reservationsLock.notify();
            }

            return reserved;
        }
    }

    public void cancelReservation(){
        synchronized (reservationsLock) {
            if (reservations > 0) {
                reservations--;
                if(reservations < getOutgoingCredits())
                    reservationsLock.notify();
            }
        }
    }

    @Override
    public boolean trySendDataWithOrder(byte[] payload) throws InterruptedException {
        boolean ret = false;
        // lock a reservation
        synchronized (reservationsLock){
            if(reservations > 0 && lockedReservations < reservations) {
                lockedReservations++;
                ret = true;
            }
        }
        // if there is a reservation that has not been locked,
        // try to send the message
        if(ret) {
            // synchronize send invocations to ensure the
            // non-blocking attempt is successful and that it
            // does not fail due to another thread owning the lock
            synchronized (this) {
                ret = super.trySendDataWithOrder(payload);
            }
            synchronized (reservationsLock) {
                // if message was sent, then
                // settle (remove) the reservation
                if (ret) reservations--;
                // regardless of the result,
                // unlock the reservaiton
                lockedReservations--;
            }
        }
        return ret;
    }

    @Override
    public boolean sendDataWithOrder(byte[] payload, Long deadline) throws InterruptedException {
        throw new UnsupportedOperationException("Only trySend() is allowed, since reservations are mandatory" +
                "to send data messages.");
    }
}
