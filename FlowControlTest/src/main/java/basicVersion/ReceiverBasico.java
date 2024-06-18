package basicVersion;

public class ReceiverBasico {
    public int maxCredits;
    public int inTotal = 0; // total message received
    public float batchSizePercentage = 0.1f; // percentage of maxCredits that make a batch
    public int batchSize;
    public int currentBatch = 0;
    public SenderBasico sender;
    public Integer recover = null; // total credits until recovery.

    public ReceiverBasico(int maxCredits, float batchSizePercentage){
        this.maxCredits = maxCredits;
        this.batchSizePercentage = batchSizePercentage;
        batchSize = calculateBatchSize(maxCredits, batchSizePercentage);
    }

    private int calculateBatchSize(int maxCredits, float percentage){
        return (int) ((float) maxCredits * percentage);
    }

    public void setSender(SenderBasico sender){
        this.sender = sender;
    }

    public void receiveMsg(){
        inTotal++;
        currentBatch++;
        if(recover != null && inTotal == recover){
            recover = null;
            sender.receiveCreditsBatch(currentBatch);
            currentBatch = 0;
        }
        else if(currentBatch == batchSize){
            sender.receiveCreditsBatch(currentBatch);
            currentBatch = 0;
        }
    }

    public void setMaxCredits(int newMaxCredits){
        maxCredits = newMaxCredits;
        batchSize = calculateBatchSize(newMaxCredits, batchSizePercentage);
        sender.setMaxCredits(newMaxCredits);
        if(currentBatch >= batchSize){
            int auxBatch = currentBatch;
            currentBatch = 0;
            sender.receiveCreditsBatch(auxBatch);
        }
    }

    public void setRecover(int recover){
        if(recover > inTotal)
            this.recover = recover;
    }
}
