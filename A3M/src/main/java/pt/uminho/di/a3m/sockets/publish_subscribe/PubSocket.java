package pt.uminho.di.a3m.sockets.publish_subscribe;

import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.*;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.configurable_socket.ConfigurableSocket;
import pt.uminho.di.a3m.sockets.publish_subscribe.messaging.PSMsg;
import pt.uminho.di.a3m.sockets.publish_subscribe.messaging.SubscriptionsPayload;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * <ul>
 *     <li>Publishing mode uses topics.</li>
 *     <li>Filtering done on publisher side.</li>
 *     <li>Ensures order within topic. Order across topics is not guaranteed.</li>
 *     <li>Sending of message is synchronized across subscribers of the same topic.
 *     If a subscriber fails to send a message due to lack of outgoing credits,
 *     then all subscribers of the same topic will not receive a new message from the
 *     topic until the slowest subscriber regains permission to send and effectivelly
 *     sends the last message of the topic. Sending blocks when a topic is throttled
 *     by a slower subscriber.</li>
 *     <li>Due to synchronizing requirements, send operations with a timeout
 *     will only timeout when a subscriber of the given topic is perceived as not
 *     capable of receiving the message (lack of outgoing credits to send the message)
 *     before effectively attempting to send the message. Once sending is initiated,
 *     meaning, when the message has already been sent to one of the subscribers, the
 *     sending procedure will not end until the message is sent to all subscribers
 *     or the thread is interrupted.</li>
 * </ul>
 */
public class PubSocket extends ConfigurableSocket {
    public static final Protocol protocol = SocketsTable.PUB_PROTOCOL;
    public static final Set<Protocol> compatProtocols = Set.of(SocketsTable.SUB_PROTOCOL);
    private final PatriciaTrie<Subscription> subscriptions = new PatriciaTrie<>();

    private static class Subscription {
        private final Set<LinkSocket> subscribers = new HashSet<>();

        /**
         * Adds a new subscriber
         * @apiNote assumes the socket's lock to be held
         */
        void addSubscriber(LinkSocket subscriber){
            subscribers.add(subscriber);
        }

        /**
         * Removes a subscriber
         * @apiNote assumes the socket's lock to be held
         */
        void removeSubscriber(LinkSocket subscriber){
            subscribers.remove(subscriber);
        }

        boolean hasSubscribers(){
            return !subscribers.isEmpty();
        }

        /**
         * Gets subscribers, adding them to a collection given as argument and
         * removing any subscribers with which a link is no longer established.
         * @param dest collection where the subscribers should be added to
         */
        synchronized void getSubscribers(Collection<LinkSocket> dest){
            if(dest != null) {
                Iterator<LinkSocket> it = subscribers.iterator();
                LinkSocket s;
                while (it.hasNext()) {
                    s = it.next();
                    if (s.getState() == LinkState.ESTABLISHED)
                        dest.add(s);
                    else
                        it.remove();
                }
            }
        }
    }

    public PubSocket(SocketIdentifier sid) {
        super(sid, false, true, true);
    }

    @Override
    protected SocketMsg handleIncomingDataMessage(LinkSocket linkSocket, SocketMsg msg) {
        // ignore data messages, since a publisher cannot receive data messages.
        return null;
    }

    @Override
    protected SocketMsg handleIncomingControlMessage(SocketMsg msg) {
        if(msg == null) return null;
        LinkSocket linkSocket = getLinkSocket(msg.getSrcId());
        if(linkSocket == null) return null;
        SubscriptionsPayload subPayload =
                SubscriptionsPayload.parseFrom(
                        msg.getType(),
                        msg.getPayload());
        // if message is a valid subscription message
        if(subPayload != null){
            if(subPayload.getType() == SubscriptionsPayload.SUBSCRIBE)
                handleSubscribeMsg(linkSocket, subPayload);
            else
                handleUnsubscribeMsg(linkSocket, subPayload);
        }
        return null;
    }

    private void handleSubscribeMsg(LinkSocket linkSocket, SubscriptionsPayload subPayload) {
        getLock().writeLock().lock();
        try {
            Map.Entry<String, Subscription> entry;
            Subscription subscription;
            for (String topic : subPayload.getTopics()){
                // create subscription for the topic
                // if one is not found
                subscription = subscriptions.computeIfAbsent(topic, t -> new Subscription());
                // adds link socket to the topic's collection of subscribers
                subscription.addSubscriber(linkSocket);
            }
        } finally {
            getLock().writeLock().unlock();
        }
    }

    /* TODO - need to find a way to not use write lock when handling these messages
        since the middleware thread must not be blocked waiting for a write lock.
        .
        Solution: Maybe follow a similar approach to the poller.
            1. Use VListNode.
            2. Add subscribers to tail atomically.
                2.1. Create VListNode for subscriber.
                2.2. Set the subscription's head node as the next of the node.
                2.3. Set the next attribute of the head's current previous node
                to point to the new node.
                2.4. Update the node's prev and the head's prev accordingly.
                 (Doesn't work...)
    */


    private void handleUnsubscribeMsg(LinkSocket linkSocket, SubscriptionsPayload subPayload) {
        getLock().writeLock().lock();
        try {
            Subscription subscription;
            for (String topic : subPayload.getTopics()){
                // if a subscription exists for the topic, then
                // removes the link socket from its collection of subscribers
                subscription = subscriptions.get(topic);
                if(subscription != null) {
                    subscription.removeSubscriber(linkSocket);
                    if(!subscription.hasSubscribers())
                        subscriptions.remove(topic);
                }
            }
        } finally {
            getLock().writeLock().unlock();
        }
    }

    @Override
    public Protocol getProtocol() {
        return protocol;
    }

    @Override
    public Set<Protocol> getCompatibleProtocols() {
        return compatProtocols;
    }

    @Override
    protected SocketMsg tryReceiving() {
        throw new UnsupportedOperationException();
    }

    // TODO - some operations could be registered, in a data structure
    //  which enables low contention on queuing/dequeuing, as events
    //  that need to happen (maybe ConcurrentLinkedQueue).
    //  That way, when a user thread acquires the write lock,
    //  it can perform all of those operations before effectively doing the
    //  job it wanted to do. By doing this, slowing down the middleware thread
    //  is avoided. Correctness is still achieved since the queued operations
    //  are performed before a message is published.
    //  Before queuing the operation, doing a writeLock.tryLock() is desirable
    //  as to verify if the operation can be performed instantaneously. However,
    //  the operation cannot be performed if there are already tasks queued
    //  since it can disrupt the order of processing. Maybe set a limit of tasks
    //  that the middleware thread can handle, or just decide that if there are
    //  tasks queued, then just queue the task and return.
    //  Having worker(s) doing these tasks could be a solution to avoid
    //  making user threads have too much work to do before publishing a message.
    //  These workers would essentially, perform the work while user threads are
    //  not using the socket. (Maybe a pool of workers, that increments or decreases
    //  with the amount of publisher sockets that need to have the subscriptions managed).

    /**
     * Performs a synchronized send of a message. The synchronization
     * means that once the message is sent to a subscriber, the message
     * must also be sent to all the other subscribers that were subscribed
     * to the message's topic at the moment of invocation of this method.
     * Therefore, the operation can only time out when there are subscribers
     * interested in the message but which the sending of the message could
     * not be completed to a single subscriber within the given timeout.
     * @param msg publish-subscribe message to be sent
     * @param timeout maximum amount of time to wait for the synchronized sending
     *                operation to be initiated.
     * @return true if message was sent (to all subscribers of the moment
     * for the given topic). false, otherwise.
     * @apiNote If there aren't any subscribers, the method returns "true" immediately.
     * @throws IllegalArgumentException if the payload is not a publish-subscribe payload.
     */
    public boolean send(PSMsg msg, Long timeout) throws InterruptedException {
        if(msg == null)
            throw new IllegalArgumentException("Message is null.");

        Long deadline = Timeout.calculateEndTime(timeout);

        // find all subscribers interested in the message,
        // i.e. all subscribers interested in a topic (or topics)
        // which has the message's topic as prefix
        List<LinkSocket> subscribers = new ArrayList<>();
        getLock().readLock().lock();
        try {
            // gets all subscriptions which are prefixes of the topic
            List<Map.Entry<String,Subscription>> prefixesList =
                    subscriptions.prefixesList(msg.getTopic());
            // gather all subscribers from the subscriptions
            prefixesList.forEach(e -> e.getValue().getSubscribers(subscribers));
        } finally {
            getLock().readLock().unlock();
        }

        /* TODO - to ensure the message is sent when interrupted, make link sockets
                have a reservation counter and an outgoing queue. The reservation counter must match the amount of credits.
                The reservation counter, as the name suggests, is used to make reservations.
                Before sending a message, a reservation must be made for every subscriber.
                If credits are reduced to a number inferior to the reservation counter, then, when
                linkSocket.send() with a data message is invoked, messages are sent until the credits
                reach 0 and then the excess is queued on the outgoing queue until credits arrive that
                enable the messages to be sent. Reservations are obviously not allowed while the counter
                is above or equal to the credit count.
                If a subscriber does not allow a reservation at the moment, then the thread must wait until
                 the reservation is allowed or the deadline is reached.
                 If the deadline is reached before getting a reservation, then all reservations are undone,
                 and the thread may return with false to indicate the operation timed out. If all reservations
                 are made in a non-blocking way or before the deadline, then sending is now possible to all
                 subscribers.
                 If the thread is interrupted while waiting, all reservations should be canceled before throwing
                 the exception.
        * */

        // if sent to any subscriber, then commit to send to all subscribers
        boolean sent = false;
        ListIterator<LinkSocket> it = subscribers.listIterator();
        LinkSocket subscriber;
        while (it.hasNext()){
            subscriber = it.next();
            try{
                sent = subscriber.send(msg, deadline);
                System.out.println("Sent: " + sent);
                if(sent) {
                    it.remove();
                    deadline = null;
                }
            }catch (LinkClosedException lce){
                it.remove();
            }
        }

        // if the message was sent to any subscriber,
        // commit to making all subscribers receive the message
        if(sent) {
            while (it.hasPrevious()) {
                subscriber = it.previous();
                try { subscriber.send(msg, null); }
                catch (LinkClosedException ignored) {}
            }
        }

        return sent;
    }

    public boolean send(PSMsg msg) throws InterruptedException {
        return send(msg, null);
    }

    @Override
    protected boolean trySending(Payload payload) {
        throw new UnsupportedOperationException();
    }

    /**
     *
     * @param payload Content that should be sent.
     * @param timeout maximum time allowed for a message to be sent. After such time
     *                is elaped, the method must return false.
     * @param notifyIfNone does not serve any purpose since messages are discarded
     *                     when there aren't subscribers interested in the messages.
     * @return true if message is sent. false, if the operation timed out.
     * @throws IllegalArgumentException if the payload does not correspond to a valid
     * publish-subscribe message.
     */
    @Override
    public boolean send(byte[] payload, Long timeout, boolean notifyIfNone) throws InterruptedException {
        PSMsg msg = PSMsg.parseFrom(payload);
        if(PSMsg.parseFrom(payload) == null)
            throw new IllegalArgumentException("Not a valid publish-subscribe message.");
        return send(msg, timeout);
    }
}
