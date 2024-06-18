package version2;

import version2.msgs.*;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Sender extends Peer {
    private int windowSize;
    private int unacked = 0; // number of unacknowledged messages
    private int outTotal = 0;
    private Receiver receiver;
    private Thread readerThread;
    private final Lock lock = new ReentrantLock(true);

    public Sender(int windowSize){
        this.windowSize = windowSize;
    }

    public void setReceiver(Receiver receiver){
        this.receiver = receiver;
    }

    public void sendMsg(){
        try{
            lock.lock();
            if(unacked < windowSize){
                outTotal++;
                unacked++;
                send(receiver, new DataMsg());
            }
        }finally {
            lock.unlock();
        }
    }

    public void setWindowSize(int newWindowSize){
        try{
            lock.lock();
            windowSize = newWindowSize;
            send(receiver, new RecoverMsg(outTotal));
        }finally {
            lock.unlock();
        }
    }

    public void receiveAcks(int acks){
        try{
            lock.lock();
            this.unacked -= acks;
        }finally {
            lock.unlock();
        }
    }

    public void run(){
        readerThread = new Thread(() -> {
            while(true){
                Msg msg = receive();
                if(msg instanceof AcksMsg amsg)
                    receiveAcks(amsg.getAcks());
                else if (msg instanceof WindowSizeMsg mcmsg)
                    setWindowSize(mcmsg.getWindowSize());
            }
        });
        readerThread.start();
    }

    public int getWindowSize() {
        try{
            lock.lock();
            return windowSize;
        }finally {
            lock.unlock();
        }
    }

    public int getUnacked() {
        try{
            lock.lock();
            return unacked;
        }finally {
            lock.unlock();
        }

    }

    @Override
    public String toString() {
        try{
            lock.lock();
            return "Sender{" +
                    "windowSize=" + windowSize +
                    ", unacked=" + unacked +
                    ", outTotal=" + outTotal +
                    ", isQueueEmpty=" + isQueueEmpty() +
                    '}';
        }finally {
            lock.unlock();
        }
    }
}
