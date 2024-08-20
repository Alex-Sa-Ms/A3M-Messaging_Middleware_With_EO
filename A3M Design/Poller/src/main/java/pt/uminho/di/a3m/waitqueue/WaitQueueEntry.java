package pt.uminho.di.a3m.waitqueue;

import pt.uminho.di.a3m.list.ListNode;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static pt.uminho.di.a3m.auxiliary.Timeout.calculateEndTime;

/**
 * This class works in conjunction with the WaitQueue class.
 * Since instances of both classes can be accessed by multiple
 * threads concurrently, the use of locking mechanisms is essential.
 * Having that said, two locking mechanisms are used:
 *  1. WaitQueue lock;
 *  2. WaitQueueEntry synchronized block.
 * The locking mechanisms must be acquired in the order
 * above to prevent deadlocks.
 */
public class WaitQueueEntry {
    private WaitQueue queue;
    private int waitFlags;
    private WaitQueueFunc func;
    private Object priv;
    private ListNode<WaitQueueEntry> node;

    WaitQueueEntry(WaitQueue queue, int waitFlags, WaitQueueFunc func, Object priv, ListNode<WaitQueueEntry> node) {
        this.queue = queue;
        this.waitFlags = waitFlags;
        this.func = func;
        this.priv = priv;
        this.node = node;
    }

    // Must be used inside a synchronized block of this instance
    private WaitQueue _getQueue() {
        return queue;
    }

    // Must be used inside a synchronized block of this instance
    private int _getWaitFlags() {
        return waitFlags;
    }

    // Must be used inside a synchronized block of this instance
    private WaitQueueFunc _getFunc() {
        return func;
    }

    // Must be used inside a synchronized block of this instance
    private Object _getPriv() {
        return priv;
    }

    // Must be used inside a synchronized block of this instance
    private ListNode<WaitQueueEntry> _getNode() {
        return node;
    }

    // Must be used inside a synchronized block of this instance
    private void _setQueue(WaitQueue queue) {
        this.queue = queue;
    }

    // Must be used inside a synchronized block of this instance
    private void _setNode(ListNode<WaitQueueEntry> node) {
        if(this.node == null)
            this.node = node;
    }

    // Must be used inside a synchronized block of this instance
    private void _setWaitFlags(int waitFlags) {
        this.waitFlags = waitFlags;
    }

    // Must be used inside a synchronized block of this instance
    private void _setFunc(WaitQueueFunc func) {
        this.func = func;
    }

    // Must be used inside a synchronized block of this instance
    private void _setPriv(Object priv) {
        this.priv = priv;
    }

    // Fills entry before adding to queue.
    // Must be used inside a synchronized block of this instance.
    private void _fillEntry(int waitFlags, WaitQueueFunc func, Object priv){
        if(func == null || priv == null)
            throw new IllegalArgumentException("Could not initialize wait queue entry:" +
                    "A wake function and a private object linked to the waiter are required.");
        _setWaitFlags(waitFlags);
        _setFunc(func);
        _setPriv(priv);
        ListNode<WaitQueueEntry> node = ListNode.create(this);
        _setNode(node);
    }

    WaitQueue getQueue() {
        synchronized (this) {
            return queue;
        }
    }

    public int getWaitFlags() {
        synchronized (this) {
            return waitFlags;
        }
    }

    public WaitQueueFunc getFunc() {
        synchronized (this) {
            return func;
        }
    }

    public Object getPriv() {
        synchronized (this) {
            return priv;
        }
    }

    ListNode<WaitQueueEntry> getNode() {
        synchronized (this) {
            return node;
        }
    }

    void setQueue(WaitQueue queue) {
        synchronized (this) {
            this.queue = queue;
        }
    }

    void setNode(ListNode<WaitQueueEntry> node) {
        synchronized (this) {
            this.node = node;
        }
    }

    // Must be used inside a synchronized block of this instance
    void setWaitFlags(int waitFlags) {
        synchronized (this) {
            this.waitFlags = waitFlags;
        }
    }

    // Must be used inside a synchronized block of this instance
    void setFunc(WaitQueueFunc func) {
        synchronized (this) {
            this.func = func;
        }
    }

    // Must be used inside a synchronized block of this instance
    void setPriv(Object priv) {
        synchronized (this) {
            this.priv = priv;
        }
    }

    void setAll(WaitQueue queue, int waitFlags, WaitQueueFunc func, Object priv, ListNode<WaitQueueEntry> node){
        synchronized (this){
            this.queue = queue;
            this.waitFlags = waitFlags;
            this.func = func;
            this.priv = priv;
            this.node = node;
        }
    }

    // Fills entry before adding to queue.
    // Must be used inside a synchronized block of this instance.
    void setAllExceptQueue(int waitFlags, WaitQueueFunc func, Object priv, ListNode<WaitQueueEntry> node){
        synchronized (this){
            this.waitFlags = waitFlags;
            this.func = func;
            this.priv = priv;
            this.node = node;
        }
    }

    // ***** Public interface ***** //

    // Error string for add operation
    private static String addErrorString(ListNode<WaitQueueEntry> node){
        String err = "The entry could not be added: ";
        if(node != null)
            err += "The entry has already been added to a queue.";
        else
            err += "After deletion, the entry cannot be added.";
        return err;
    }

    private void _add(WaitQueueFunc func, Object priv, boolean exclusive){
        // Racy attribution but it is safeguarded by
        // re-checking the queue variable after
        // acquiring the queue's lock and entering
        // the synchronized block.
        WaitQueue q = queue;
        if(q != null) {
            try {
                q.getLock().lock();
                synchronized (this) {
                    if (node == null && queue != null) {
                        // sets the exclusive wait flag if "exclusive" is true
                        if(!exclusive){
                            _fillEntry(0, func, priv);
                            q._addFirst(node);
                        }else {
                            _fillEntry(WaitQueueFlags.EXCLUSIVE, func, priv);
                            q._addLast(node);
                        }
                    } else throw new IllegalStateException(addErrorString(node));
                }
            }finally {
                q.getLock().unlock();
            }
        }
    }

    public void add(WaitQueueFunc func, Object priv){
        _add(func,priv,false);
    }

    public void addExclusive(WaitQueueFunc func, Object priv){
        _add(func,priv,true);
    }

    public void delete(){
        // Racy attribution and comparisons,
        // however, after acquiring the lock
        // and entering the synchronized block,
        // appropriate verifications are done
        // before deleting the entry
        WaitQueue q = queue;
        if(node != null && q != null) {
            try {
                q.getLock().lock();
                synchronized (this) {
                    if (node != null) {
                        queue._delete(node);
                        queue = null;
                        node = null;
                    }
                }
            }finally {
                q.getLock().unlock();
            }
        }
    }

    public boolean isQueued(){
        synchronized (this) {
            return node != null && ListNode.isQueued(node);
        }
    }

    public boolean isDeleted(){
        synchronized (this) {
            return queue == null;
        }
    }

    // ***** Default Wake functions ***** //

    // returns 0 if wake up was not performed.
    // returns positive number if wake up was performed.

    /**
     * Another wake functions may store the park state
     * inside a more elaborate private object, so this
     * method enables the wake function to provide the
     * park state instance instead of the method assuming
     * that the park state will be the private object.
     * @param ps park state instance associated with the entry
     * @return 1 if the thread was unparked.
     *         0 if the thread was not parked, therefore,
     *         couldn't be unparked.
     */
    public int parkStateWakeUp(ParkState ps){
        synchronized (this){
            // adds woken flag
            if(ps.parked.compareAndSet(true, false)) {
                LockSupport.unpark(ps.thread);
                return 1;
            }
            return 0;
        }
    }

    /**
     * Wakes up thread using LockSupport.unpark(). The private object of the entry required to match
     * this wake function should be a ParkState object containing the reference of the thread to 
     * wake up and an atomic bool which the thread should set to 'true' before parking and 
     * to 'false' after unparking. The value of the bool is used to determine if the unpark() method 
     * should be called and therefore avoid to provide an unnecessary parking credit which allows 
     * the thread to skip the parking process.
     * @param entry wait queue entry
     * @param mode wake mode
     * @param wakeFlags wake flags
     * @param key event(s) that resulted in the wake-up call
     * @return non-zero if a wake-up was performed. zero if the wake-up was not performed. 
     */
    public static int defaultWakeFunction(WaitQueueEntry entry, int mode, int wakeFlags, Object key){
        return entry.parkStateWakeUp((ParkState) entry.getPriv());
    }

    /**
     * Wakes up thread using LockSupport.unpark() and if the thread was successfully woken up, deletes
     * the wait queue entry from the wait queue. The private object of the entry required to match
     * this wake function should be a ParkState object containing the reference of the thread to 
     * wake up and an atomic bool which the thread should set to 'true' before parking and 
     * to 'false' after unparking. The value of the bool is used to determine if the unpark() method 
     * should be called and therefore avoid to provide an unnecessary parking credit which allows 
     * the thread to skip the parking process.
     * @param entry wait queue entry
     * @param mode wake mode
     * @param wakeFlags wake flags
     * @param key event(s) that resulted in the wake-up call
     * @return non-zero if a wake-up was performed. zero if the wake-up was not performed. 
     */
    public static int autoDeleteWakeFunction(WaitQueueEntry entry, int mode, int wakeFlags, Object key){
        int ret = defaultWakeFunction(entry, mode, wakeFlags, key);
        if(ret != 0)
            entry.delete();
        return ret;
    }

    // ***** Default Wait function ***** //

    /**
     * Employed by default wait functions to wait using the park state.
     * This function parks the current thread until the end
     * time or until it is given the permission to unpark or until
     * it is interrupted. Since the thread may have been interrupted,
     * regardless of a successful unpark, the interrupt flag of the
     * thread should be checked.
     * @param endTime timestamp at which the method should return
     *                if the permit to unpark is not given. If
     *                the thread should wait indefinitely then
     *                this value may be null. Otherwise, it must
     *                be a positive value obtained through the addition of
     *                a timeout and System.currentTimeMillis()
     * @param ps Park State object used to set when the thread parks and
     *           unparks, but also to determine if the thread was given
     *           the permission to unpark.
     * @param usePermit If "true", the current park state along with
     *                  a possible LockSupport unpark permit are used to
     *                  determine if the thread should park or not.
     *                  Otherwise, if "false", the park state is set to "true"
     *                  and entering the parking loop is inevitable.
     *                  <p> As the name of the parameter dictates,
     *                  the method should use an (unpark) permit if it exists.
     *                  Having that said, before entering the parking
     *                  loop, if "usePermit" is set to "true", the
     *                  method will initially invoke the parking method which
     *                  will essentially test for an unpark permit (and
     *                  consume it if existent). This is done before reaching
     *                  the parking loop, since the existence of an unpark permit
     *                  along with a park state that matches it (i.e. a park state equal
     *                  to "false"), results in skipping the parking loop.
     *                  If an unpark permit does not exist or the park state is set to "true",
     *                  then entering the parking loop follows and waiting for a wake-up call
     *                  that calls LockSupport.unpark() and sets the park state to 'false' is
     *                  required.
     * @return true if the thread received the permission to unpark. 
     *         false othewise (timed out or was interrupted).
     * @throws IllegalCallerException Thrown if the current thread is not the owner
     *                                of the park state associated with the entry.
     * @apiNote Exposed since there is a possibility of wanting to associate the same park state
     *  with multiple wait queue entries.
     */
    public static boolean parkStateWaitFunction(Long endTime, ParkState ps, boolean usePermit){
        // If current thread is not the owner
        // of the park state, then it should
        // not be waiting using this entry
        if(ps.thread != Thread.currentThread())
            throw new IllegalCallerException("The current thread is not the owner of the wait queue entry.");

        // set the intent to park
        if(!usePermit)
            ps.parked.set(true);

        // if a timeout was not provided,
        // parks until the flag is set to
        // false by the wake function
        if (endTime == null) {
            // checks if an unpark permit exists and consumes it
            if(usePermit) LockSupport.park();
            while(ps.parked.get()){ 
                // If thread was interrupted return immediatelly.
                // Although the interrupt flag may have been set,
                // the thread may have been unparked concurrently,
                // so the return value must reflect that possibility
                if(Thread.currentThread().isInterrupted())
                    return !ps.parked.getAndSet(false);
                LockSupport.park();
            }
        }
        // Else, parks until the timeout expires
        // or the park flag is set to false
        else {
            long timeout;
            // checks if an unpark permit exists and consumes it
            if(usePermit) {
                timeout = endTime - System.currentTimeMillis();
                LockSupport.parkUntil(timeout);
            }
            while ((timeout = (endTime - System.currentTimeMillis())) > 0 &&
                    ps.parked.get() && !Thread.currentThread().isInterrupted()) {
                LockSupport.parkUntil(endTime);
            }
            // sets the park state to inform that the thread is no longer parked
            return timeout > 0 || !ps.parked.getAndSet(false);
        }

        return true;
    }

    /**
     * Default wait function compatible with any wake function
     * that uses park state. If the provided entry is queued,
     * this function makes the current thread wait until woken up
     * or until the end timeout is reached (if not null and positive) 
     * or until it is interrupted. Since the thread may have been interrupted,
     * regardless of a successful unpark, the interrupt flag of the
     * thread should be checked. Otherwise, with a not queued entry, the method returns 'true'
     * immediately.
     * @param entry entry added to queue. If not queued, the
     *              method will return 'true' immediately.
     * @param ps Park state used to wait. If 'null' the park state is
     *           assumed to be the private object.
     * @param endTimeout timestamp, calculated using System.currentTimeMillis(),
     *                  at which the operation should time out and return 'false'.
     * @param usePermit If "true", the current park state along with
     *                  a possible LockSupport unpark permit are used to
     *                  determine if the thread should park or not.
     *                  Otherwise, if "false", the park state is set to "true"
     *                  and entering the parking loop is inevitable.
     * @return "false" if the thread was not woken up. "true" if the thread was
     *         woken up or if the entry was not queued.
     * @throws IllegalCallerException Thrown if the current thread is not the owner
     *                                of the park state associated with the entry.
     * @throws ClassCastException Thrown if a park state is not provided (i.e. is null)
     *                            and the wait entry does not have a park state as
     *                            its private object.
     * @see WaitQueueEntry#parkStateWaitFunction(Long, ParkState, boolean)
     * @see WaitQueueEntry#parkStateWakeUp(ParkState)
     */
    public static boolean defaultWaitFunction(WaitQueueEntry entry, ParkState ps, Long endTimeout, boolean usePermit) {
        // returns immediatelly if the end timeout has been reached
        if(endTimeout != null && endTimeout <= System.currentTimeMillis())
            return false;

        // If the entry is not queued, returns "true" immediatelly.
        // This assumed the entry was indeed queued before, and may
        // have been deleted as a consequence of the wake-up callback.
        if(!entry.isQueued())
            return true;

        // Gets park state from the entry
        if (ps == null)
            ps = (ParkState) entry.getPriv();

        return parkStateWaitFunction(endTimeout, ps, usePermit);
    }

    /**
     * Default wait function (using timeout value) compatible with any
     * wake function that uses park state. If the provided entry is queued,
     * this function makes the current thread wait until woken up
     * or until the expiration of the timeout (if not null and positive) or 
     * until it is interrupted. Since the thread may have been interrupted,
     * regardless of a successful unpark, the interrupt flag of the
     * thread should be checked. Otherwise, with a not queued entry, the
     * method returns 'true' immediately.
     * @param entry entry added to queue. If not queued, the
     *              method will return 'true' immediately.
     * @param ps Park state used to wait. If 'null' the park state is
     *           assumed to be the private object.
     * @param timeout maximum time (in milliseconds) allowed to wait before
     *                being woken up, otherwise the operation should time out.
     *                A timeout value of zero or less will result in no action, i.e.
     *                the method will return "false" immediately.
     * @param usePermit If "true", the current park state along with
     *                  a possible LockSupport unpark permit are used to
     *                  determine if the thread should park or not.
     *                  Otherwise, if "false", the park state is set to "true"
     *                  and entering the parking loop is inevitable.
     * @return "false" if the thread was not woken up. "true" if the thread was
     *         woken up or if the entry was not queued.
     * @throws IllegalCallerException Thrown if the current thread is not the owner
     *                                of the park state associated with the entry.
     * @throws ClassCastException Thrown if a park state is not provided (i.e. is null)
     *                            and the wait entry does not have a park state as
     *                            its private object.
     * @see WaitQueueEntry#defaultWaitFunctionTimeout(WaitQueueEntry, ParkState, Long, boolean)
     * @see WaitQueueEntry#parkStateWakeUp(ParkState)
     */
    public static boolean defaultWaitFunctionTimeout(WaitQueueEntry entry, ParkState ps, Long timeout, boolean usePermit){
        Long endTime = calculateEndTime(timeout);
        return defaultWaitFunction(entry, ps, endTime, usePermit);
    }

    /**
     * Park state is assumed to be the private object of the entry and the use
     * of a permit is assumed to not be wanted.
     * @see WaitQueueEntry#defaultWaitFunctionTimeout(WaitQueueEntry, ParkState, Long, boolean)
     */
    public static boolean defaultWaitFunctionTimeout(WaitQueueEntry entry, Long timeout){
        return defaultWaitFunctionTimeout(entry, null, timeout, false);
    }

    /**
     * Park state is assumed to be the private object of the entry, the operation blocks
     * until a wake-up call or an interrupt signal, and the use
     * of a permit is assumed to not be wanted.
     * @see WaitQueueEntry#defaultWaitFunctionTimeout(WaitQueueEntry, ParkState, Long, boolean)
     */
    public static boolean defaultWaitFunctionTimeout(WaitQueueEntry entry){
        return defaultWaitFunctionTimeout(entry, null, null, false);
    }
}
