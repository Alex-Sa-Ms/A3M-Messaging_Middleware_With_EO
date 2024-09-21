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

import static pt.uminho.di.a3m.sockets.publish_subscribe.messaging.SubscriptionsPayload.SUBSCRIBE;
import static pt.uminho.di.a3m.sockets.publish_subscribe.messaging.SubscriptionsPayload.UNSUBSCRIBE;

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
    // for fast retrieval of subscribers that have subscribed a topic that is a prefix
    // of the topic of the message being published
    private final PatriciaTrie<Subscription> subscriptions = new PatriciaTrie<>();
    // maps subscribers to their subscribed topics
    private final Map<SocketIdentifier,Set<String>> subscribers = new HashMap<>();
    // Queue of subscribe/unsubscribe tasks that could not be
    // executed immediately by the middleware thread
    private final ConcurrentLinkedQueue<SubscriptionTask> tasks = new ConcurrentLinkedQueue<>();

    public PubSocket(SocketIdentifier sid) {
        super(sid, false, true, true);
    }

    private record SubscriptionTask(
        LinkSocket linkSocket,
        byte type, // SUBSCRIBE or UNSUBSCRIBE
        List<String> topics // can only be null for an unsubscribe task and it simbolizes removing all subscriptions.
    ) {}

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

    @Override
    protected void customOnLinkClosed(LinkSocket linkSocket) {
        super.customOnLinkClosed(linkSocket);
        boolean locked = getLock().writeLock().tryLock();
        try {
            // execute or "schedule" the unsubscription of all topics
            if (locked && tasks.isEmpty())
                handleUnsubscribeTask(linkSocket, null);
            else
                tasks.add(new SubscriptionTask(linkSocket, UNSUBSCRIBE, null));
        }finally {
            if(locked)
                getLock().writeLock().unlock();
        }
    }

    @Override
    public void unlink(SocketIdentifier peerId) {
        getLock().writeLock().lock();
        try {
            LinkSocket linkSocket = getLinkSocket(peerId);
            // unsubscribe all topics
            if(linkSocket != null)
                handleUnsubscribeTask(linkSocket, null);
            // then, unlink
            super.unlink(peerId);
        } finally {
            getLock().writeLock().unlock();
        }
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
        if(subPayload != null) {
            boolean locked = getLock().writeLock().tryLock();
            try {
                // if write lock was acquired and there aren't any subscription
                // tasks that need to be handled first, then let the middleware
                // thread handle the task
                if (locked && tasks.isEmpty())
                    handleSubscriptionTask(linkSocket, subPayload.getType(), subPayload.getTopics());
                // else, queue a subscription task to be handled by a user thread
                // before a publishing a message
                else
                    tasks.add(new SubscriptionTask(linkSocket, subPayload.getType(), subPayload.getTopics()));
            } finally {
                if (locked) getLock().writeLock().unlock();
            }
        }
        return null;
    }

    /**
     * Handles a subscription (subscribe/unsubscribe) task.
     * @param linkSocket link socket associated with the peer that wants
     *                   to subscribe/unsubscribe to topics.
     * @param type type of subscription
     */
    private void handleSubscriptionTask(LinkSocket linkSocket, byte type, Collection<String> topics){
        if (type == SUBSCRIBE)
            handleSubscribeTask(linkSocket, topics);
        else {
            handleUnsubscribeTask(linkSocket, topics);
        }
    }


    /**
     * Handles a subscribe task. Registers the peer associated with the link
     * socket as interested in the given topics.
     * @param linkSocket link socket
     * @param topics topics to be subscribed.
     */
    private void handleSubscribeTask(LinkSocket linkSocket, Collection<String> topics) {
        getLock().writeLock().lock();
        try {
            Set<String> subscriberTopics =
                    subscribers.computeIfAbsent(
                            linkSocket.getPeerId(),
                            peerId -> new HashSet<>());

            Subscription subscription;
            for (String topic : topics){
                // register subscriber if it is not subscribed yet
                // create subscription for the topic if one is not found
                if(subscriberTopics.add(topic)) {
                    subscription = subscriptions.computeIfAbsent(topic, t -> new Subscription());
                    // adds link socket to the topic's collection of subscribers
                    subscription.addSubscriber(linkSocket);
                }
            }
        } finally {
            getLock().writeLock().unlock();
        }
    }

    /**
     * Handles an unsubscribe task. Removes the peer's interest in the given topics.
     * @param linkSocket link socket
     * @param topics topics to be unsubscribed, or null if all topics should be unsubscribed.
     */
    private void handleUnsubscribeTask(LinkSocket linkSocket, Collection<String> topics) {
        getLock().writeLock().lock();
        try {
            Subscription subscription;
            Set<String> subscriberTopics = subscribers.get(linkSocket.getPeerId());

            // if topics is null, then all the topics
            // of the subscriber must be removed.
            if(topics == null) topics = subscriberTopics;

            if(subscriberTopics != null) {
                for (String topic : topics) {
                    // if a subscription exists for the topic, then
                    // removes the link socket from its collection of subscribers
                    if(subscriberTopics.remove(topic)) {
                        subscription = subscriptions.get(topic);
                        subscription.removeSubscriber(linkSocket);
                        if (!subscription.hasSubscribers())
                            subscriptions.remove(topic);
                    }
                }
            }
        } finally {
            getLock().writeLock().unlock();
        }
    }

    /**
     * Handles subscription tasks.
     * @implNote Assumes write lock to be owned by the caller.
     */
    private void handleSubscriptionTasks(int nrTasks){
        SubscriptionTask task;
        for (int i = 0; i < nrTasks && (task = tasks.poll()) != null; i++)
            handleSubscriptionTask(task.linkSocket(), task.type(), task.topics());
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

        // execute all pending subscription tasks
        int nrTasks;
        boolean readLocked = false;
        if(!tasks.isEmpty()){
            getLock().writeLock().lock();
            try {
                // Only handle subscription tasks that were present
                // at the moment of this verification.
                // Any tasks added after it are to be handled in a
                // following send invocation.
                nrTasks = tasks.size();
                handleSubscriptionTasks(nrTasks);
                // Acquire read lock before releasing the write lock
                // to enable other threads to send messages concurrently
                getLock().readLock().lock();
                readLocked = true;
            } finally {
                getLock().writeLock().unlock();
            }
        }

        // find all subscribers interested in the message,
        // i.e. all subscribers interested in a topic (or topics)
        // which has the message's topic as prefix
        List<LinkSocket> subscribers = new ArrayList<>();
        if(!readLocked) getLock().readLock().lock();
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

        // TODO - No order inside topic is provided

        // if sent to any subscriber, then commit to send to all subscribers
        // by setting the deadline to "null"
        boolean sent = false;
        ListIterator<LinkSocket> it = subscribers.listIterator();
        LinkSocket subscriber;
        while (it.hasNext()){
            subscriber = it.next();
            try{
                sent = subscriber.send(msg, deadline);
                if(sent) {
                    it.remove();
                    deadline = null;
                }
            }catch (LinkClosedException lce){
                it.remove();
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
