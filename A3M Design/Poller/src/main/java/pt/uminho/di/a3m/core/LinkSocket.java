package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.poller.PollTable;
import pt.uminho.di.a3m.poller.Pollable;

public class LinkSocket implements Pollable {
    private final Link link;
    private final LinkManager manager;

    public LinkSocket(Link link, LinkManager manager) {
        if(link == null || manager == null)
            throw new IllegalArgumentException("Link or manager is null.");
        this.link = link;
        this.manager = manager;
    }


    @Override
    public Object getId() {
        return link.getId();
    }

    @Override
    public int poll(PollTable pt) {
        return manager.pollLink(link, pt);
    }

    /**
     * Waits until an incoming message, from the peer associated with this
     * link, is available or the deadline is reached or the thread is interrupted.
     * If the socket is in COOKED mode, the returned messages are data messages.
     * If in RAW mode, the returned messages may also be custom control messages.
     * @param deadline waiting limit to poll an incoming message
     * @return available incoming message or "null" if the operation timed out
     * without a message becoming available.
     * @apiNote Caller must not hold socket lock as it will result in a deadlock
     * when a blocking operation with a non-expired deadline is requested.
     */
    SocketMsg receive(Long deadline) throws InterruptedException {
        return manager.receive(link, deadline);
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
        return manager.send(link, payload, deadline);
    }

    // TODO - add other methods related to link that can be exposed,
    //  such as changing capacity.
}
