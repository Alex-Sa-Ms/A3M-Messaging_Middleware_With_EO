package version1;

import version1.msgs.*;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Sender extends Peer {
    private int windowSize;
    private int credits;
    private int outTotal = 0;
    private Receiver receiver;
    private Thread readerThread;
    private final Lock lock = new ReentrantLock(true);

    public Sender(int windowSize){
        this.windowSize = windowSize;
        this.credits = windowSize;
    }

    public void setReceiver(Receiver receiver){
        this.receiver = receiver;
    }

    public void sendMsg(){
        try{
            lock.lock();
            if(credits > 0){
                credits--;
                outTotal++;
                send(receiver, new DataMsg());
            }
        }finally {
            lock.unlock();
        }
    }

    public void setWindowSize(int newWindowSize){
        try{
            lock.lock();
            credits -= windowSize - newWindowSize;
            windowSize = newWindowSize;
            send(receiver, new RecoverMsg(outTotal));
        }finally {
            lock.unlock();
        }
    }

    public void receiveCreditsBatch(int credits){
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
                    receiveCreditsBatch(cmsg.getCredits());
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

    public int getCredits() {
        try{
            lock.lock();
            return credits;
        }finally {
            lock.unlock();
        }
    }

    public int getOutTotal() {
        try{
            lock.lock();
            return outTotal;
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
                    ", credits=" + credits +
                    ", outTotal=" + outTotal +
                    '}';
        }finally {
            lock.unlock();
        }
    }
}
