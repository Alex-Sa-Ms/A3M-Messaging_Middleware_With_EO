package pt.uminho.di.a3m.sockets.publish_subscribe;

import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.poller.PollQueueingFunc;
import pt.uminho.di.a3m.poller.PollTable;
import pt.uminho.di.a3m.sockets.auxiliary.LinkSocketWatchedWithOrder;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;
import pt.uminho.di.a3m.waitqueue.WaitQueueFunc;

class PubLinkSocket extends LinkSocketWatchedWithOrder {
    // Watches POLLOUT events so that notifying
    // reservation waiters is possible
    private WaitQueueEntry creditsWatcher = null;

    // - Counter of reservations to send data messages.
    // - To send a data message a reservation must be made first.
    // - Reservation counter cannot be superior to the number of credits.
    // This means that changing the capacity of a link can lead to unwanted behavior.
    private int reservations = 0;

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
    public synchronized boolean trySendDataWithOrder(byte[] payload) throws InterruptedException {
        boolean sent = false;
        if(reservations > 0) {
            sent = super.trySendDataWithOrder(payload);
            if(sent) reservations--;
        }
        return sent;
    }

    @Override
    public boolean sendDataWithOrder(byte[] payload, Long deadline) throws InterruptedException {
        throw new UnsupportedOperationException("Only trySend() is allowed, since reservations are mandatory" +
                "to send data messages.");
    }
}
