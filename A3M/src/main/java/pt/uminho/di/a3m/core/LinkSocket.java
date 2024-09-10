package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.poller.PollTable;
import pt.uminho.di.a3m.poller.Pollable;

// TODO - test this
public class LinkSocket implements Pollable {
    private Link link = null;
    private LinkManager manager = null;

    public LinkSocket(){}

    Link getLink() {
        return link;
    }

    /**
     * Since specializations of link socket are allowed, and to prevent unwanted capturing
     * of the link and link manager references, these variables are set using this final
     * and package-private method.
     * @param link link
     * @param manager link manager
     */
    final void setLinkAndManager(Link link, LinkManager manager){
        if(link == null || manager == null)
            throw new IllegalArgumentException("Link or manager is null.");
        this.link = link;
        this.manager = manager;
    }

    @Override
    public final LinkIdentifier getId() {
        return link.getId();
    }

    public final SocketIdentifier getOwnerId() {
        return link.getOwnerId();
    }

    public final int getPeerProtocolId(){
        return link.getPeerProtocolId();
    }

    public final SocketIdentifier getPeerId(){
        return link.getDestId();
    }

    public final LinkState getState(){
        return link.getState();
    }

    @Override
    public int poll(PollTable pt) {
        return link.poll(pt);
    }

    /**
     * Waits until an incoming message, from the peer associated with this
     * link, is available or the deadline is reached or the thread is interrupted.
     * If the socket is in COOKED mode, the returned messages are data messages.
     * If in RAW mode, the returned messages may also be custom control messages.
     * @param deadline waiting limit to poll an incoming message
     * @return available incoming message or "null" if the operation timed out
     * without a message becoming available.
     * @throws LinkClosedException when the link is closed and there are no more messages
     * to be received.
     * @apiNote Caller must not hold socket lock as it will result in a deadlock
     * when a blocking operation with a non-expired deadline is requested.
     */
    public SocketMsg receive(Long deadline) throws InterruptedException {
        return link.receive(deadline);
    }

    /**
     * Sends message to the peer associated with this link. This method
     * is not responsible for verifying if the control message follows
     * the custom semantics of the socket.
     * @param payload message content
     * @param deadline timestamp at which the method must return
     *                 indicating a timeout if sending was not possible
     *                 due to the lack of permission (i.e. credits).
     * @return "true" if message was sent. "false" if permission
     *          was not acquired during the specified timeout.
     * @throws IllegalArgumentException If the payload is null or if the payload type
     * corresponds to a reserved type other than DATA.
     * @throws InterruptedException If thread was interrupted.
     * @apiNote Caller must not hold socket lock as it will result in a deadlock
     * when a blocking operation with a non-expired deadline is requested.
     */
    public boolean send(Payload payload, Long deadline) throws InterruptedException {
        return link.send(payload, deadline);
    }

    public void unlink(){
        manager.unlink(link.getId().destId());
    }

    // ********** Outgoing flow Methods ********** //

    /** @return whether the link has outgoing credits or not. */
    public synchronized boolean hasOutgoingCredits(){
        return link.hasOutgoingCredits();
    }

    /**
     * @return amount of outgoing credits (permits to send data messages).
     */
    public int getOutgoingCredits() {
        return link.getOutgoingCredits();
    }

    // ********** Incoming flow Methods ********** //

    /**
     * While this method informs how many elements are in the queue,
     * it does not inform if there is a message ready to be polled.
     * To obtain such information, use hasAvailableIncomingMessages().
     *
     * @return the amount of messages in the queue */
    public int countIncomingMessages(){
        return link.countIncomingMessages();
    }

    /**
     * While this method informs if the queue has elements, it is not an
     * indicator that a message can be polled. To obtain such information,
     * use hasAvailableIncomingMessages().
     *
     * @return true if the queue has elements. false, otherwise. */
    public boolean hasIncomingMessages(){
        return link.hasIncomingMessages();
    }

    /** @return true if the queue has at least one element that can be polled. false, otherwise. */
    public boolean hasAvailableIncomingMessages(){
        return link.hasAvailableIncomingMessages();
    }

    /**
     * @return capacity of the link, i.e., maximum amount
     * of messages that can be queued at a time.
     */
    public int getCapacity(){
        return link.getCapacity();
    }

    /**
     * If the link is not closed, sets queuing capacity to the provided
     * value, sends current batch and clears the current batch.
     * @param newCapacity new capacity
     */
    public void setCapacity(int newCapacity) {
        link.setCapacity(newCapacity);
    }

    /**
     * If the link is not closed, adjusts the capacity
     * using provided credit variation and clears the current batch.
     * @param credits variation of credits to apply to the
     *                current capacity.
     */
    public void adjustCapacity(int credits) {
        link.adjustCapacity(credits);
    }

    /**
     * @return amount of credits that need to be batched before
     * returning credits to the sender.
     */
    public int getBatchSize() {
        return link.getBatchSize();
    }

    /**
     * @return percentage of the capacity that makes
     * the size of a batch. Regardless of the batch size
     * percentage, when the capacity is
     * positive, the batch size is at least 1, and when the
     * capacity is zero or negative, the batch size is 0.
     */
    public float getBatchSizePercentage() {
        return link.getBatchSizePercentage();
    }

    /**
     * Updates the batch size using the provided percentage.
     * @param newPercentage percentage of the capacity that should make the batch size.
     *                      When the capacity is positive, the minimum batch size is of 1 credit.
     * @throws IllegalArgumentException If the provided batch size percentage is not a
     * value between 0 (exclusive) and 1 (inclusive).
     */
    public void setCreditsBatchSizePercentage(float newPercentage) {
        link.setCreditsBatchSizePercentage(newPercentage);
    }
}
