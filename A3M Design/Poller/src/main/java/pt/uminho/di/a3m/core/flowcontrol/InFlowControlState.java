package pt.uminho.di.a3m.core.flowcontrol;

public class InFlowControlState {
    private int peerCapacity = 0;
    private int batch = 0; // current batch
    private int batchSize = 0; // batch size
    private float batchSizePercentage = 0.05f; // percentage of the peer's capacity that makes the batch size

    /**
     * Create an instance that holds incoming flow control state,
     * with the provided initial peer's capacity.
     * @param initialCapacity initial amount of credits a peer must start with
     */
    public InFlowControlState(int initialCapacity) {
        this.peerCapacity = initialCapacity;
    }

    /**
     * Create an instance that holds incoming flow control state,
     * with the provided initial peer's capacity and with batch size
     * adjusted using the given batch size percentage.
     * @param initialCapacity initial amount of credits a peer must start with
     * @param batchSizePercentage percentage of the peer's capacity that
     *                            should make the batch size. When the capacity
     *                            is positive, the minimum batch size is of 1 credit.
     * @throws IllegalArgumentException If the provided batch size percentage is not a
     * value between 0 (exclusive) and 1 (inclusive).
     */
    public InFlowControlState(int initialCapacity, float batchSizePercentage) {
        checkBatchSizePercentage(batchSizePercentage);
        this.peerCapacity = initialCapacity;
        this.batchSizePercentage = batchSizePercentage;
        batchSize = calculateBatchSize(initialCapacity, batchSizePercentage);
    }

    private static void checkBatchSizePercentage(float batchSizePercentage){
        if(batchSizePercentage <= 0 || batchSizePercentage > 1)
            throw new IllegalArgumentException("Batch size percentage must be " +
                    "a value between 0 (exclusive) and 1 (inclusive).");
    }

    /**
     * Calculates the batch size using the provided capacity and batch size percentage.
     * When the capacity is positive, the minimum size for a batch is 1. If the capacity
     * is 0 or negative, then the peer must not be able to send more messages, so credits,
     * should not be provided until the capacity changes to a positive value.
     * @param capacity peer's current capacity
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
     * @param newPercentage percentage of the peer's capacity that
     *                            should make the batch size. When the capacity
     *                            is positive, the minimum batch size is of 1 credit.
     * @return amount of credits to be sent to the peer
     * @throws IllegalArgumentException If the provided batch size percentage is not a
     * value between 0 (exclusive) and 1 (inclusive).
     */
    public int setBatchSizePercentage(float newPercentage) {
        checkBatchSizePercentage(newPercentage);
        batchSize = calculateBatchSize(peerCapacity, newPercentage);
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
     * Set peer capacity to the provided value and returns
     * the credits variation that needs to be sent to the
     * peer. The current batch is cleared.
     * @param newCapacity new peer capacity
     * @return amount of credits to be sent to the peer
     */
    public int setPeerCapacity(int newCapacity) {
        // Calculate difference between old and new capacity.
        // The difference must be sent as credits to the sender,
        // so it can adjust them to match the new capacity.
        int diff = newCapacity - peerCapacity;
        // updates capacity and gets the amount of credits to be sent
        return adjustPeerCapacity(diff);
    }

    /**
     * Adjusts the peer's capacity using the provided credit
     * variation and returns the credits variation that needs
     * to be sent to the peer. The current batch is cleared.
     * @param credits variation of credits to apply to the
     *                peer's current capacity.
     * @return amount of credits to be sent to the peer
     */
    public int adjustPeerCapacity(int credits){
        // updates capacity
        this.peerCapacity += credits;
        // updates batch size
        batchSize = calculateBatchSize(peerCapacity, batchSizePercentage);
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
