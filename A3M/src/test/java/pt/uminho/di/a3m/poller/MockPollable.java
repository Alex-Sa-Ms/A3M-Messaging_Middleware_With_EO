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
