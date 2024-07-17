package testWaitForAvailableLink;

import pt.uminho.di.a3m.SocketIdentifier;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Socket {
    Lock lock;
    IFlowControlCoordinator fcManager;

    public Socket(boolean fair) {
        lock = new ReentrantLock(fair);
        fcManager = new FlowControlCoordinator(lock.newCondition());
    }

    public Socket() {
        this(false);
    }

    public void newLink(SocketIdentifier sid, int credits){
        try {
            lock.lock();
            fcManager.newLinkState(sid, credits, lock.newCondition());
        }finally {
            lock.unlock();
        }
    }

    public void closeLink(SocketIdentifier sid){
        try {
            lock.lock();
            fcManager.closeAndRemoveLinkState(sid);
        }finally {
            lock.unlock();
        }
    }

    public boolean trySendMsg(SocketIdentifier sid){
        try{
            lock.lock();
            if(fcManager.isLinkAvailable(sid)) {
                fcManager.tryConsumeCredit(sid);
                return true;
            }else return false;
        }finally {
            lock.unlock();
        }
    }

    public boolean trySendMsg(){
        try{
            lock.lock();
            SocketIdentifier sid = fcManager.getAvailableLink();
            if(sid != null){
                fcManager.tryConsumeCredit(sid);
                return true;
            }else return false;
        }finally {
            lock.unlock();
        }
    }

    public boolean sendMsg(SocketIdentifier sid, long timeout) throws InterruptedException, LinkClosedException {
        try{
            lock.lock();
            if(fcManager.waitForLinkAvailability(sid, timeout)) {
                // lock acquired before waiting for link availability,
                // therefore there is no problem in settling a request
                // before sending the message because no other thread
                // can affect the socket while the lock is owned by
                // another thread.
                fcManager.tryConsumeCredit(sid); // equivalent to sending a message
                return true;
            }else return false;
        }finally {
            lock.unlock();
        }
    }

    public void sendMsg(SocketIdentifier sid) throws InterruptedException, LinkClosedException {
        try{
            lock.lock();
            fcManager.waitForLinkAvailability(sid);
            // lock acquired before waiting for link availability,
            // therefore there is no problem in settling a request
            // before sending the message because no other thread
            // can affect the socket while the lock is owned by
            // another thread.
            fcManager.tryConsumeCredit(sid); // equivalent to sending a message
        }finally {
            lock.unlock();
        }
    }

    public SocketIdentifier sendMsg(long timeout) throws InterruptedException {
        try{
            lock.lock();
            SocketIdentifier sid = fcManager.waitForAvailableLink(timeout);
            if(sid != null)
                fcManager.tryConsumeCredit(sid); // equivalent to sending a message
            return sid;
        }finally {
            lock.unlock();
        }
    }

    public SocketIdentifier sendMsg() throws InterruptedException {
        try{
            lock.lock();
            SocketIdentifier sid = fcManager.waitForAvailableLink();
            fcManager.tryConsumeCredit(sid); // equivalent to sending a message
            return sid;
        }finally {
            lock.unlock();
        }
    }

    public int replenishCredits(SocketIdentifier sid, int credits){
        try{
            lock.lock();
            return fcManager.replenishCredits(sid, credits);
        }finally {
            lock.unlock();
        }
    }
}
