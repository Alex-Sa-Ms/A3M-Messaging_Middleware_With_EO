package pt.uminho.di.a3m.poller.waitqueue;

import pt.uminho.di.a3m.list.ListNode;

import java.util.concurrent.locks.LockSupport;

public class WaitQueueEntry {
    private WaitQueue queue;
    private int waitFlags;
    private WaitQueueFunc func;
    private Object priv;
    private ListNode<WaitQueueEntry> node;

    protected WaitQueueEntry(WaitQueue queue, int waitFlags, WaitQueueFunc func, Object priv, ListNode<WaitQueueEntry> node) {
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
    private void fillEntry(int waitFlags, WaitQueueFunc func, Object priv){
        if(func == null || priv == null)
            throw new IllegalArgumentException("Could not initialize wait queue entry:" +
                    "A wake function and a private object linked to the waiter are required.");
        _setWaitFlags(waitFlags);
        _setFunc(func);
        _setPriv(priv);
        ListNode<WaitQueueEntry> node = ListNode.create(this);
        _setNode(node);
    }

    protected WaitQueue getQueue() {
        synchronized (this) {
            return _getQueue();
        }
    }

    public int getWaitFlags() {
        synchronized (this) {
            return _getWaitFlags();
        }
    }

    public WaitQueueFunc getFunc() {
        synchronized (this) {
            return _getFunc();
        }
    }

    public Object getPriv() {
        synchronized (this) {
            return _getPriv();
        }
    }

    protected ListNode<WaitQueueEntry> getNode() {
        synchronized (this) {
            return _getNode();
        }
    }

    protected void setQueue(WaitQueue queue) {
        synchronized (this) {
            _setQueue(queue);
        }
    }

    protected void setNode(ListNode<WaitQueueEntry> node) {
        synchronized (this) {
            _setNode(node);
        }
    }

    // Must be used inside a synchronized block of this instance
    protected void setWaitFlags(int waitFlags) {
        synchronized (this) {
            _setWaitFlags(waitFlags);
        }
    }

    // Must be used inside a synchronized block of this instance
    protected void setFunc(WaitQueueFunc func) {
        synchronized (this) {
            _setFunc(func);
        }
    }

    // Must be used inside a synchronized block of this instance
    protected void setPriv(Object priv) {
        synchronized (this) {
            _setPriv(priv);
        }
    }

    protected void setAll(WaitQueue queue, int waitFlags, WaitQueueFunc func, Object priv, ListNode<WaitQueueEntry> node){
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
    protected void setAllExceptQueue(int waitFlags, WaitQueueFunc func, Object priv, ListNode<WaitQueueEntry> node){
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
        if(!ListNode.isEmpty(node))
            err += "The entry has already been added to a queue.";
        else
            err += "After deletion, the entry cannot be added.";
        return err;
    }

    public void add(WaitQueueFunc func, Object priv){
        synchronized (this) {
            if (node == null && queue != null) {
                fillEntry(0, func, priv);
                queue.addFirst(node);
            } else throw new IllegalStateException(addErrorString(node));
        }
    }

    public void addExclusive(WaitQueueFunc func, Object priv){
        synchronized (this) {
            if (node == null && queue != null) {
                fillEntry(WaitQueueFlags.EXCLUSIVE, func, priv);
                queue.addLast(node);
            } else throw new IllegalStateException(addErrorString(node));
        }
    }

    public void delete(){
        synchronized (this) {
            if (!ListNode.isEmpty(node)) {
                // Cannot invoke ListNode.delete() because the lock
                // of the queue must be acquired to modify the list
                queue.delete(node);
                queue = null;
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
            return node != null && ListNode.isDeleted(node);
        }
    }

    // ***** Default Wake function ***** //

    // returns 0 if wake up was not performed.
    // returns positive number if wake up was performed.
    private int wakeUp(){
        synchronized (this){
            // adds woken flag
            ParkState ps = (ParkState) priv;
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
    public static int defaultWakeFunction(WaitQueueEntry entry, int mode, int wakeFlags, int key){
        return entry.wakeUp();
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
    public static int autoDeleteWakeFunction(WaitQueueEntry entry, int mode, int wakeFlags, int key){
        int ret = defaultWakeFunction(entry, mode, wakeFlags, key);
        if(ret != 0)
            entry.delete();
        return ret;
    }


    /**
     * Default wait function. Inserts the entry in the wait queue if it is not
     * yet inserted. Then, waits until be woken up or the timeout expires.
     * If the return value is negative and the user does not intend to wait
     * again using the entry, then the entry should be deleted by invoking "entry.delete()".
     * @param entry entry to be added to the queue
     * @param exclusive if the entry should be added as exclusive or non-exclusive
     * @param timeout maximum time (in milliseconds) allowed to be woken up before timing out.
     *                A timeout value of 0 or less will result in no action, i.e.
     *                the entry will not be queued.
     * @return "false" if the thread was not woken up. "true" otherwise.
     */
    public static boolean defaultWaitFunction(WaitQueueEntry entry, boolean exclusive, Long timeout){
        Long endTime = null;
        if(timeout != null) {
            if (timeout <= 0)
                return false;
            else {
                try {
                    endTime = Math.addExact(System.currentTimeMillis(), timeout);
                }catch (ArithmeticException ae){
                    endTime = Long.MAX_VALUE;
                }
            }
        }
        
        ParkState ps;
        
        // If the entry has not been added yet,
        // creates park state object,
        // sets the intent to park and
        // adds the entry to the wait queue.
        if(!entry.isQueued()) {
            ps = new ParkState(Thread.currentThread());
            ps.parked.set(true);
            if (!exclusive)
                entry.add(WaitQueueEntry::autoDeleteWakeFunction, ps);
            else
                entry.addExclusive(WaitQueueEntry::autoDeleteWakeFunction, ps);
        }else {
            // if entry is queued, gets park state
            // from the entry
            ps = (ParkState) entry.getPriv();
        }
        
        // if a timeout was not provided,
        // parks until the flag is set to
        // false by the wake function
        if (timeout == null) {
            while(ps.parked.get())
                LockSupport.park();
        }
        // Else, parks until the timeout expires
        // or the park flag is set to false
        else {
            while ((timeout = (endTime - System.currentTimeMillis())) > 0 &&
                    ps.parked.get()) {
                LockSupport.park(timeout);
            }
            boolean ret = timeout > 0 || !ps.parked.get();
            ps.parked.set(false); // sets the park state to inform that the thread is no longer parked
            return ret;
        }
        
        return true;
    }

    /**
     * Default wait function. Inserts the entry in the wait queue if it is not
     * yet inserted. Then, waits until be woken up or the timeout expires.
     * If the return value is negative and the user does not intend to wait
     * again using the entry, then the entry should be deleted by invoking "entry.delete()".
     * @param exclusive If the entry should be added as exclusive or non-exclusive.
     *                  If the entry has been added already, this value is ignored.
     * @param timeout maximum time (in milliseconds) allowed to be woken up before timing out.
     *                A timeout value of 0 or less will result in no action, i.e.
     *                the entry will not be queued.
     * @return "false" if the thread was not woken up. "true" otherwise.
     */
    public boolean defaultWaitFunction(boolean exclusive, Long timeout){
        return WaitQueueEntry.defaultWaitFunction(this, exclusive, timeout);
    }
}
