package pt.uminho.di.a3m.core.flowcontrol;

public class InFlowControlState {
    private int capacity = 0;
    private int batch = 0; // current batch
    private int batchSize = 0; // batch size
    private float batchSizePercentage = 0.05f; // percentage of the capacity that makes the batch size

    /**
     * Create an instance that holds incoming flow control state,
     * with the provided initial capacity.
     * @param initialCapacity amount of messages willing to be queued at the same time
     */
    public InFlowControlState(int initialCapacity) {
        this.capacity = initialCapacity;
    }

    /**
     * Create an instance that holds incoming flow control state,
     * with the provided initial capacity and with batch size
     * adjusted using the given batch size percentage.
     * @param initialCapacity amount of messages willing to be queued at the same time
     * @param batchSizePercentage percentage of the capacity that
     *                            should make the batch size. When the capacity
     *                            is positive, the minimum batch size is of 1 credit.
     * @throws IllegalArgumentException If the provided batch size percentage is not a
     * value between 0 (exclusive) and 1 (inclusive).
     */
    public InFlowControlState(int initialCapacity, float batchSizePercentage) {
        checkBatchSizePercentage(batchSizePercentage);
        this.capacity = initialCapacity;
        this.batchSizePercentage = batchSizePercentage;
        batchSize = calculateBatchSize(initialCapacity, batchSizePercentage);
    }

    private static void checkBatchSizePercentage(float batchSizePercentage){
        if(batchSizePercentage <= 0 || batchSizePercentage > 1)
            throw new IllegalArgumentException("Batch size percentage must be " +
                    "a value between 0 (exclusive) and 1 (inclusive).");
    }

    /**
     * @return capacity of the link, i.e., maximum amount
     * of messages that can be queued at a time.
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * @return amount of credits that need to be batched before
     * returning credits to the sender.
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * @return percentage of the capacity that makes
     * the size of a batch. Regardless of the batch size
     * percentage, when the capacity is
     * positive, the batch size is at least 1, and when the
     * capacity is zero or negative, the batch size is 0.
     */
    public float getBatchSizePercentage() {
        return batchSizePercentage;
    }

    /**
     * Calculates the batch size using the provided capacity and batch size percentage.
     * When the capacity is positive, the minimum size for a batch is 1. If the capacity
     * is 0 or negative, then the peer must not be able to send more messages, so credits,
     * should not be provided until the capacity changes to a positive value.
     * @param capacity amount of messages willing to be queued at the same time
     * @param percentage batch size percentage
     * @return size of batch
     */
    public static int calculateBatchSize(int capacity, float percentage){
        int bs = (int) ((float) Math.max(0, capacity) * percentage);
        if(capacity > 0 && bs == 0)
            bs = 1;
        return bs;
    }

    /**
     * Updates the batch size using the provided percentage.
     * @param newPercentage percentage of the capacity that
     *                            should make the batch size. When the capacity
     *                            is positive, the minimum batch size is of 1 credit.
     * @return amount of credits to be sent to the peer
     * @throws IllegalArgumentException If the provided batch size percentage is not a
     * value between 0 (exclusive) and 1 (inclusive).
     */
    public int setBatchSizePercentage(float newPercentage) {
        checkBatchSizePercentage(newPercentage);
        batchSize = calculateBatchSize(capacity, newPercentage);
        batchSizePercentage = newPercentage;
        // If the current batch equals or surpasses the new batch size,
        // credits must be sent to the peer
        int toSend = 0;
        if(batch >= batchSize){
            // gets remaining of dividing the current batch
            // by the new batch size
            int remaining = batch % batchSize;
            // credits to send correspond to the current batch
            // minus the remaining of the integer division
            toSend = batch - remaining;
            // sets the current batch to the remaining value
            batch = remaining;
        }
        return toSend;
    }

    /**
     * Set capacity to the provided value and returns
     * the credits variation that needs to be sent to the
     * peer. The current batch is cleared.
     * @param newCapacity new capacity
     * @return amount of credits to be sent to the peer
     */
    public int setCapacity(int newCapacity) {
        // Calculate difference between old and new capacity.
        // The difference must be sent as credits to the sender,
        // so it can adjust them to match the new capacity.
        int diff = newCapacity - capacity;
        // updates capacity and gets the amount of credits to be sent
        return adjustCapacity(diff);
    }

    /**
     * Adjusts the capacity using the provided credit
     * variation and returns the credits variation that needs
     * to be sent to the peer. The current batch is cleared.
     * @param credits variation of credits to apply to the
     *                current capacity.
     * @return amount of credits to be sent to the peer
     */
    public int adjustCapacity(int credits){
        // updates capacity
        this.capacity += credits;
        // updates batch size
        batchSize = calculateBatchSize(capacity, batchSizePercentage);
        // variation is equal to the difference in capacity plus the current batch
        int toSend = credits + batch;
        batch = 0;
        return toSend;
    }

    /**
     * Method that applies the effect of the delivery of an incoming message.
     * This method increments the current batch by 1.
     * If the current batch reaches the batch size value, then
     * the batch size is returned and the current batch is set to 0.
     * @return amount of credits to be sent to the peer
     */
    public int deliver(){
        int toSend = 0;
        batch++;
        if(batch == batchSize){
            toSend = batchSize;
            batch = 0;
        }
        return toSend;
    }
}
