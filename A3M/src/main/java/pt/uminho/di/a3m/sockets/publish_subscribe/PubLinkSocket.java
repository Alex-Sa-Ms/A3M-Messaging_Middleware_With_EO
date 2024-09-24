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

    // - Counter of reservations to send data messages.
    // - To send a data message a reservation must be made first.
    // - Reservation counter cannot be superior to the number of credits.
    // This means that changing the capacity of a link can lead to unwanted behavior.
    private int reservations = 0;

    // - Counter of reservations of threads that are currently attempting to send.
    // - Ensures consistency of behavior when a thread attempting to send a date message
    // releases the link socket's synchronized temporarily. Releasing the link socket's
    // synchronized monitor is required to avoid deadlocks with link's wait queue lock.
    // If the synchronized monitor is not released before doing a write operation on the link,
    // the locking mechanisms are acquired in the following order:
    //      1. link socket's synchronized monitor;
    //      2. link's wait queue lock.
    // Now, for instance, if a flow control message is received, the middleware thread
    // acquires the locking mechanisms in the order that follows:
    //      1. link's wait queue lock;
    //      2. link socket's synchronized monitor.
    // Since, the acquisition of the synchronized monitor is mandatory when a POLLOUT
    // event is received by the credits watcher callback, then it is imperative that
    // any writing operations on the link release the synchronized monitor.
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
                    synchronized (this){
                        if(reservations < getOutgoingCredits())
                            this.notify();
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
            synchronized (this) { notifyAll(); }
        }
    }

    /**
     * Attempts to reserve a credit to send a data message.
     * @return true if a credit was reserved. false, otherwise.
     */
    public synchronized boolean tryReserve(){
        boolean reserved = reservations < getOutgoingCredits();
        if(reserved) reservations++;
        return reserved;
    }

    /**
     * Attempts to reserve a credit to send a data message within
     * the deadline.
     * @param deadline deadline to reserve a credit
     * @return true if a credit was reserved. false, otherwise.
     * @throws LinkClosedException if link was closed.
     */
    public synchronized boolean tryReserveUntil(Long deadline) throws InterruptedException {
        // waits until reserving a credit is possible
        while (reservations >= getOutgoingCredits()
                && !Timeout.hasTimedOut(deadline)) {
            if(creditsWatcher == null)
                throw new LinkClosedException();
            if(deadline == null)
                this.wait();
            else
                this.wait(deadline - System.currentTimeMillis());
        }

        if(creditsWatcher == null)
            throw new LinkClosedException();

        int credits = getOutgoingCredits();
        boolean reserved = reservations < credits;
        if(reserved){
            reservations++;
            if(reservations < credits)
                this.notify();
        }

        return reserved;
    }

    public synchronized void cancelReservation(){
        if(reservations > 0) reservations--;
    }

    @Override
    public boolean trySendDataWithOrder(byte[] payload) throws InterruptedException {
        /*  TODO - > Problem 1: cannot remove reservation since another thread could make a reservation
                    thinking that there are credits to accomodate another reservation, while, in fact,
                    a message is on the process of being sent.
                   > Problem 2: sending without having something to "lock" the reservation is also not
                    possible since a bad utilization of the socket would mean multiple sends
                    being allowed and the reservations could become a negative value.
                   > Solution:
                        1. Make creditsWatcher volatile
                        2. Use synchronized on the link socket for all operations regarding reservations
                            with the synchronized being acquired before doing a reading operation on the link.
                        3. In a normal case, holding synchronized while performing writing operations on the
                         link would not be a problem, however, since there is a need for a credits watcher to exist
                         that needs to hold the synchronized block of the link socket, then,
                         holding the synchronized block for writing operations means a possibility for deadlock
                         if the writing operation attempts to notify a waiter (i.e. if it needs to hold the link's
                         wait queue lock already held by the thread notifying the credits watcher callback).
                            - So, writing operations should be safeguarded by the reservation algorithm so that while
                            the synchronized block is released during the writing operation, the state does not
                            become inconsistent with what a thread returning from the writing operation would expect.
         */
        boolean ret = false;
        // lock a reservation
        synchronized (this){
            if(reservations > 0 && lockedReservations < reservations) {
                lockedReservations++;
                ret = true;
            }
        }
        // if there is a reservation that has not been locked,
        // try to send the message
        if(ret) {
            ret = super.trySendDataWithOrder(payload);
            synchronized (this) {
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
