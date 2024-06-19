package flow_control_test;

import flow_control_test.msgs.CreditsMsg;
import flow_control_test.msgs.DataMsg;
import flow_control_test.msgs.Msg;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Sender extends Peer {
    private int credits;
    private Receiver receiver;
    private Thread readerThread;
    private final Lock lock = new ReentrantLock(true);

    // Invoked using the credits received in the link establishment
    public Sender(int credits){
        //if(credits < 0)
        //    throw new IllegalArgumentException("Starting credits must not be a negative value.");
        this.credits = credits;
    }

    public void setReceiver(Receiver receiver){
        this.receiver = receiver;
    }

    // returns true if message was sent
    public boolean sendMsg(){
        try{
            lock.lock();
            if(credits > 0){
                credits--;
                send(receiver, new DataMsg());
                return true;
            }
            return false;
        }finally {
            lock.unlock();
        }
    }

    public void receiveCredits(int credits){
        try{
            lock.lock();
            this.credits += credits;
        }finally {
            lock.unlock();
        }
    }

    public void run(){
        readerThread = new Thread(() -> {
            while(true){
                Msg msg = receive();
                if(msg instanceof CreditsMsg cmsg)
                    receiveCredits(cmsg.getCredits());
            }
        });
        readerThread.start();
    }

    public int getCredits() {
        try{
            lock.lock();
            return credits;
        }finally {
            lock.unlock();
        }

    }

    @Override
    public String toString() {
        try{
            lock.lock();
            return "Sender{" +
                    "credits=" + credits +
                    ", isQueueEmpty=" + isQueueEmpty() +
                    '}';
        }finally {
            lock.unlock();
        }
    }
}
