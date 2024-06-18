package version2;

import version2.msgs.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
    ***** Algoritmo para controlo de fluxo por janela *****
    *
    * Problemas:
    *   (a) Permitir mudança de tamanho de janela em qualquer momento.
    *   (b) Como recuperar de crédito negativo quando o novo tamanho
    *       de janela é inferior ao nº de mensagens por confirmar.
    *       (b.1) Quando o novo tamanho de janela é positivo
    *       (b.2) Quando o novo tamanho de janela é 0
    *   (c) Como garantir que os batches de créditos ficam alinhados
    *       e que não existem sobras?
    *       (c.1) No inicio
    *       (c.2) Depois da mudança de tamanho da janela
    *
    * Contador total de créditos é necessário por duas razões:
    *   1. Permitir recuperar de crédito negativo quando
    *       o nº de mensagens cujo processamento ainda não foi
    *       confirmado supera o novo tamanho de janela.
    *   2. para que no caso de
    *
    *
    *
 */
public class Receiver extends Peer {
    private int windowSize;
    private float batchSizePercentage; // percentage of window size that make a batch
    private int batchSize; // size of batch that triggers a message of acks to be sent
    private int acksBatch = 0; // current batch of acknowledgements
    private int inTotal = 0; // total message received
    private Integer recover = null; // Number that inTotal must have to achieve recovery.

    private Sender sender;
    private Thread readerThread;
    private final Lock lock = new ReentrantLock(true);

    public Receiver(int windowSize, float batchSizePercentage){
        this.windowSize = windowSize;
        this.batchSizePercentage = batchSizePercentage;
        batchSize = calculateBatchSize(windowSize, batchSizePercentage);
    }

    private int calculateBatchSize(int windowSize, float percentage){
        int size = (int) ((float) windowSize * percentage);
        // if window size is positive, then the minimum batch size is 1.
        if(windowSize > 0 && size == 0)
            size = 1;
        return size;
    }

    public void setSender(Sender sender){
        this.sender = sender;
    }

    private void receiveDataMsg(){
        try {
            lock.lock();
            inTotal++;
            acksBatch++;
            // if the total of messages received equals the recover value
            if (recover != null && inTotal == recover) {
                // sends the batch of acks that will make the sender exit recover mode
                sendAcksBatch();
                // exit recover mode
                recover = null;
                // update the batch size to match the window size
                batchSize = calculateBatchSize(windowSize, batchSizePercentage);
            } else if (acksBatch >= batchSize) {
                sendAcksBatch();
            }
        }finally {
            lock.unlock();
        }
    }

    private void sendAcksBatch(){
        send(sender, new AcksMsg(acksBatch));
        acksBatch = 0;
    }

    // To change window size
    public void setWindowSize(int newWindowSize){
        try {
            lock.lock();
            // updates window size
            windowSize = newWindowSize;
            // updates batch size
            batchSize = calculateBatchSize(newWindowSize, batchSizePercentage);
            // sends the new window size to the sender.
            send(sender, new WindowSizeMsg(newWindowSize));
            // When the window size is reduced, the current batch may surpass
            // the new batch size. In this case, the number of acks sent equals to
            // the highest multiple of the new batch size that is lower than the current batch.
            if (batchSize > 0 && acksBatch >= batchSize) {
                int r = acksBatch % batchSize; // remainder of division of current batch by the new batch size
                send(sender, new AcksMsg(acksBatch - r));
                acksBatch = r;
            }
        }finally {
            lock.unlock();
        }
    }

    /*
        Using a recover value enables the receiver to send a batch with a great amount of
        acks, instead of sending acks one by one, promoting efficient use of network resources.
        Recovery is required when the window size is changed to a smaller value,
        while the sender has an amount of unacknowledged messages higher than the
        new window size.
     */
    private void setRecover(int recover){
        try {
            lock.lock();
            // if recover message arrived in time, set the recover mode
            if (recover > inTotal) {
                this.recover = recover;
                batchSize = recover - inTotal;
            }
            // Else, the recover message arrived late, and the batches may have been affected by additional acknowledgments.
            else{
                // The verification only needs to be done if the window size is positive. With a window of size 0, messages
                // cannot be sent, so the batches could have not been affected.
                if(windowSize > 0) {
                    // The remainder must be 0 for the batches not to have been affected
                    int remainder = (inTotal - recover) % batchSize;

                    // if the remainder is not 0
                    if (remainder != 0) {
                        // Checks if the current batch has enough acks to fix the problem
                        if (batchSize > remainder) {
                            send(sender, new AcksMsg(remainder));
                            batchSize -= remainder;
                        } // Else uses the recover mode to fix it
                        else this.recover = inTotal + remainder;
                    }
                }
            }
        }finally {
            lock.unlock();
        }
    }

    public void run(){
        readerThread = new Thread(() -> {
            while(true){
                Msg msg = receive();
                if(msg instanceof DataMsg)
                    receiveDataMsg();
                else if(msg instanceof RecoverMsg rmsg)
                    setRecover(rmsg.getTotalCredits());
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

    public int getInTotal() {
        try{
            lock.lock();
            return inTotal;
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

    public int getCurrentBatch() {
        try{
            lock.lock();
            return acksBatch;
        }finally {
            lock.unlock();
        }
    }

    public Integer getRecover() {
        try{
            lock.lock();
            return recover;
        }finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        try{
            lock.lock();
            return "Receiver{" +
                    "windowSize=" + windowSize +
                    ", batchSizePercentage=" + batchSizePercentage +
                    ", batchSize=" + batchSize +
                    ", acksBatch=" + acksBatch +
                    ", inTotal=" + inTotal +
                    ", recover=" + recover +
                    ", isQueueEmpty=" + isQueueEmpty() +
                    '}';
        }finally {
            lock.unlock();
        }
    }
}


