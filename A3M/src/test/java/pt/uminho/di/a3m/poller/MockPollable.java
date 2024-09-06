package pt.uminho.di.a3m.poller;

import pt.uminho.di.a3m.waitqueue.ParkState;
import pt.uminho.di.a3m.waitqueue.WaitQueue;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MockPollable implements Pollable {
    private final String id;
    private final Lock lock = new ReentrantLock();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private int sendCredits = 0;
    private final Deque<String> outQ = new ArrayDeque<>();
    private final Deque<String> inQ = new ArrayDeque<>();
    private final WaitQueue waitQ = new WaitQueue();

    public MockPollable(String id) {
        this.id = id;
    }

    public MockPollable(String id, int sendCredits) {
        this.id = id;
        this.sendCredits = sendCredits;
    }

    protected int getSendCredits() {
        return sendCredits;
    }

    protected Deque<String> getOutQ() {
        return outQ;
    }

    protected Deque<String> getInQ() {
        return inQ;
    }

    protected WaitQueue getWaitQ() {
        return waitQ;
    }

    @Override
    public Object getId() {
        return this.id;
    }

    @Override
    public int poll(PollTable pt) {
        if(!PollTable.pollDoesNotWait(pt)){
            pt.pollWait(this, waitQ.initEntry());
        }
        return _getAvailableEventsMask();
    }

    private int _getAvailableEventsMask(){
        int events = 0;
        if(sendCredits > 0)
            events |= PollFlags.POLLOUT;
        if(!inQ.isEmpty())
            events |= PollFlags.POLLIN;
        if(closed.get())
            events |= PollFlags.POLLHUP;
        return events;
    }

    private void _notifyWaiters(int nrExclusive, int events){
        waitQ.fairWakeUp(0,nrExclusive,0,events);
    }

    private void _notifyWaiters(int nrExclusive){
        // gather available events
        int events = _getAvailableEventsMask();
        _notifyWaiters(nrExclusive, events);
    }

    void newMsg(String msg){
        if(msg == null)
            return;
        try{
            lock.lock();
            // do not add if closed
            if(closed.get())
                throw new IllegalStateException("Socket is closed.");
            // add new message to read-queue
            inQ.add(msg);
            // notify waiters
            _notifyWaiters(1,PollFlags.POLLIN);
        }finally {
            lock.unlock();
        }
    }

    void updateCredits(int credits){
        try{
            lock.lock();
            // do not update credits if closed
            if(closed.get())
                throw new IllegalStateException("Socket is closed.");
            // update credits
            sendCredits += credits;
            // notify waiters
            if(credits > 0)
                _notifyWaiters(credits, PollFlags.POLLOUT);
        }finally {
            lock.unlock();
        }
    }

    public boolean trySendMsg(String msg){
        try{
            lock.lock();
            if(sendCredits > 0){
                outQ.add(msg);
                return true;
            }
            // throw exception if socket is closed and
            // sending is no longer allowed (i.e. there are no credits)
            if(closed.get())
                throw new IllegalStateException("Socket is closed.");
            // return false if message could not be sent
            return false;
        }finally {
            lock.unlock();
        }
    }

    // Queuing function for direct waiters, i.e. for waiters
    // that invoke methods of the instance directly, such as
    // sendMsg() and receiveMsg(). Queues the waiters as exclusive
    // using the park state present in the poll tables "priv" attribute.
    private void queueDirectCalled(int events, ParkState ps){
        WaitQueueEntry wait = waitQ.initEntry();
        // TODO - auto delete wake function is not good for exclusive calls.
        //  For exclusive entries, saying that it woke up successfully when
        //  the event of interest was not received is not good. See how
        //  the poller handles exclusive entries.
        wait.addExclusive(WaitQueueEntry::autoDeleteWakeFunction,ps);
    }

    public boolean sendMsg(String msg, Long timeout){
        // TODO - sendMsg() see problem of queuing above
        /*Long endTime = Timeout.calculateEndTime(timeout);
        try{
            lock.lock();
            if (sendCredits > 0) {
                outQ.add(msg);
                return true;
            }
            ParkState ps = new ParkState();
            PollTable pt = new PollTable(PollFlags.POLLOUT, ps, directQingFunc());
            pt.pollWait();
            if(endTime == null) {
                while(){

                }

            }else{

            }
            // throw exception if socket is closed and
            // sending is no longer allowed (i.e. there are no credits)
            if(closed.get())
                throw new IllegalStateException("Socket is closed.");
            // return false if message could not be sent
            return false;
        }finally {
            lock.unlock();
        }

         */
        return false;
    }

    public String tryReceiveMsg(){
        try{
            lock.lock();
            if(!inQ.isEmpty()){
                return inQ.poll();
            }
            // return null if there aren't messages to be received
            return null;
        }finally {
            lock.unlock();
        }
    }

    public String receiveMsg(){
        // TODO - receiveMsg()
        /*
        try{
            lock.lock();
            if(!inQ.isEmpty()){
                return inQ.poll();
            }
            // throw exception if socket is closed and
            // receiving is no longer possible
            // (i.e. there are no messages to be received)
            if(closed.get())
                throw new IllegalStateException("Socket is closed.");
            // return null if there aren't messages to be received
            return null;
        }finally {
            lock.unlock();
        }
        */
        return null;
    }

    public void close(){
        try{
            lock.lock();
            closed.getAndSet(true);
            sendCredits = 0; // sending is no longer allowed
            // notify with POLLHUP to inform the closure,
            // and POLLFREE to make the wake-up callbacks
            // remove the entry regardless of whether the waiter
            // is waiting or not
            waitQ.wakeUp(0,0,0,PollFlags.POLLHUP | PollFlags.POLLFREE);
        }finally {
            lock.unlock();
        }
    }

    public boolean isClosed(){
        return closed.get();
    }
}
