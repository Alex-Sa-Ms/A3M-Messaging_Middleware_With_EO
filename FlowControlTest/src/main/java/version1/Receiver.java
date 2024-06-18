package version1;

import version1.msgs.*;

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
    * O transmissor e o receptor mantêm registo do
    * tamanho da janela atual e um contador total de créditos.
    *
    *
    *
 */
public class Receiver extends Peer{
    private int windowSize;
    private int inTotal = 0; // total message received
    private float batchSizePercentage = 0.1f; // percentage of window size that make a batch
    private int batchSize;
    private int currentBatch = 0;
    private Integer recover = null; // total credits until recovery.
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
            currentBatch++;
            if (recover != null && inTotal == recover) {
                recover = null;
                batchSize = calculateBatchSize(windowSize, batchSizePercentage);
                send(sender, new CreditsMsg(currentBatch));
                currentBatch = 0;
            } else if (currentBatch >= batchSize) {
                send(sender, new CreditsMsg(currentBatch));
                currentBatch = 0;
            }
        }finally {
            lock.unlock();
        }
    }

    public void setWindowSize(int newWindowSize){
        try {
            lock.lock();
            windowSize = newWindowSize;
            //batchSize = calculateBatchSize(newWindowSize, batchSizePercentage);
            send(sender, new WindowSizeMsg(newWindowSize));
            if (currentBatch >= batchSize) {
                send(sender, new CreditsMsg(currentBatch));
                currentBatch = 0;
            }
        }finally {
            lock.unlock();
        }
    }

    private void setRecover(int recover){
        try {
            lock.lock();
            if (recover > inTotal) {
                this.recover = recover;
            }else{
                batchSize = calculateBatchSize(windowSize, batchSizePercentage);
            }
        }finally {
            lock.unlock();
        }
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
            return currentBatch;
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
                    ", inTotal=" + inTotal +
                    ", batchSizePercentage=" + batchSizePercentage +
                    ", batchSize=" + batchSize +
                    ", currentBatch=" + currentBatch +
                    ", recover=" + recover +
                    '}';
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
}


