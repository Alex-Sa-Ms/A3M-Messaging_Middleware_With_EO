package flow_control_test;

import flow_control_test.msgs.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class Receiver extends Peer {
    private int capacity; // max amount of credits the transmitter may have
    private float batchSizePercentage; // percentage of window size that make a batch
    private int batchSize; // size of batch that triggers a message of acks to be sent
    private int batch = 0; // current batch
    private Sender sender;
    private final Lock lock = new ReentrantLock(true);
    private Deque<DataMsg> deliverQueue = new ArrayDeque<>();
    private Condition newMsgCond = lock.newCondition();
    private Thread readerThread;

    public Receiver(int capacity, float batchSizePercentage){
        if(capacity < 0)
            throw new IllegalArgumentException("Capacity must not be null.");
        if(batchSizePercentage <= 0 || batchSizePercentage > 1)
            throw new IllegalArgumentException("Batch size percentage must be a value between 0 (exclusive) and 1 (inclusive).");
        this.capacity = capacity;
        this.batchSizePercentage = batchSizePercentage;
        batchSize = calculateBatchSize(capacity, batchSizePercentage);
    }

    // The minimum size for a batch if the capacity is a positive value is 1.
    private int calculateBatchSize(int capacity, float percentage){
        int bs = (int) ((float) capacity * percentage);
        if(capacity > 0 && bs == 0)
            bs = 1;
        return bs;
    }

    public void setSender(Sender sender){
        this.sender = sender;
    }

    private void receiveDataMsg(DataMsg msg){
        try {
            lock.lock();
            deliverQueue.add(msg);
            newMsgCond.signal();
        }finally {
            lock.unlock();
        }
    }

    public DataMsg deliverDataMsg() throws InterruptedException {
        try {
            lock.lock();
            DataMsg msg;
            while ((msg = deliverQueue.poll()) == null)
                newMsgCond.await();
            batch++; // add msg to batch

            // send batch if the batch size was reached
            if(batch == batchSize) {
                send(sender, new CreditsMsg(batch));
                batch = 0;
            }
            return msg;
        }finally {
            lock.unlock();
        }
    }

    // To change capacity
    public void setCapacity(int newCapacity){
        try {
            lock.lock();
            // Calculate difference between old and new capacity. The difference must be sent as credits
            // to the sender, so it can adjust them to match the new capacity.
            int diff = newCapacity - capacity; // capacity delta
            // updates capacity
            capacity = newCapacity;
            // updates batch size
            batchSize = calculateBatchSize(capacity, batchSizePercentage);
            // sends the difference in capacity plus the current batch
            send(sender, new CreditsMsg(diff + batch));
            batch = 0;
        }finally {
            lock.unlock();
        }
    }

    public void run(){
        readerThread = new Thread(() -> {
            while(true){
                Msg msg = receive();
                if(msg instanceof DataMsg dmsg)
                    receiveDataMsg(dmsg);
            }
        });
        readerThread.start();
    }

    public int getCapacity() {
        try{
            lock.lock();
            return capacity;
        }finally {
            lock.unlock();
        }
    }

    public float getBatchSizePercentage() {
        try{
            lock.lock();
            return batchSizePercentage;
        }finally {
            lock.unlock();
        }
    }

    public int getBatchSize() {
        try{
            lock.lock();
            return batchSize;
        }finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        try{
            lock.lock();
            return "Receiver{" +
                    "capacity=" + capacity +
                    ", batchSizePercentage=" + batchSizePercentage +
                    ", batchSize=" + batchSize +
                    ", batch=" + batch +
                    ", isQueueEmpty=" + isQueueEmpty() +
                    '}';
        }finally {
            lock.unlock();
        }
    }
}


