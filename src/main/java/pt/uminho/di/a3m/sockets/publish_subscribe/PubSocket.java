package pt.uminho.di.a3m.sockets.publish_subscribe;

import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.*;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.auxiliary.LinkSocketWatched;
import pt.uminho.di.a3m.sockets.configurable_socket.ConfigurableSocket;
import pt.uminho.di.a3m.sockets.publish_subscribe.messaging.PSPayload;
import pt.uminho.di.a3m.sockets.publish_subscribe.messaging.SubscriptionsPayload;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
    public static final Set<Protocol> compatProtocols = Set.of(SocketsTable.SUB_PROTOCOL, SocketsTable.XSUB_PROTOCOL);
    // for fast retrieval of subscribers that have subscribed a topic that is a prefix
    // of the topic of the message being published
    private final PatriciaTrie<Subscription> subscriptions = new PatriciaTrie<>();
    // maps subscribers to their subscribed topics
    private final Map<SocketIdentifier,Set<String>> subscribers = new HashMap<>();
    // Queue of subscribe/unsubscribe tasks that could not be
    // executed immediately by the middleware thread
    private final ConcurrentLinkedQueue<SubscriptionTask> tasks = new ConcurrentLinkedQueue<>();
    // Needs a "private" lock different from the socket's base lock since
    // sending operations can hold the read lock for a long time, leaving
    // the middleware thread to execute basic socket operations such as closing links,
    // delivering messages, etc.
    private final ReadWriteLock plock = new ReentrantReadWriteLock();
    
    public PubSocket(SocketIdentifier sid) {
        super(sid, false, true, true);
    }

    private record SubscriptionTask(
        PubLinkSocket linkSocket,
        byte type, // SUBSCRIBE or UNSUBSCRIBE
        List<String> topics // can only be null for an unsubscribe task and it simbolizes removing all subscriptions.
    ) {}

    private static class Subscription {
        private final Set<PubLinkSocket> subscribers = new HashSet<>();

        /**
         * Adds a new subscriber
         * @apiNote assumes the socket's lock to be held
         */
        void addSubscriber(PubLinkSocket subscriber){
            subscribers.add(subscriber);
        }

        /**
         * Removes a subscriber
         * @apiNote assumes the socket's lock to be held
         */
        void removeSubscriber(PubLinkSocket subscriber){
            subscribers.remove(subscriber);
        }

        boolean hasSubscribers(){
            return !subscribers.isEmpty();
        }
    }

    @Override
    protected LinkSocketWatched createLinkSocketInstance(int peerProtocolId) {
        return new PubLinkSocket();
    }

    @Override
    protected void customOnLinkEstablished(LinkSocket linkSocket) {
        ((PubLinkSocket) linkSocket).setCreditsWatcher();
        super.customOnLinkEstablished(linkSocket);
    }

    @Override
    protected void customOnLinkClosed(LinkSocket linkSocket) {
        super.customOnLinkClosed(linkSocket);
        PubLinkSocket pubLinkSocket = (PubLinkSocket) linkSocket;
        pubLinkSocket.removeCreditsWatcherAndStopReservations();
        boolean locked = plock.writeLock().tryLock();
        try {
            // execute or "schedule" the unsubscription of all topics
            if (locked && tasks.isEmpty())
                unsubscribeAll(pubLinkSocket);
            else
                tasks.add(new SubscriptionTask(pubLinkSocket, UNSUBSCRIBE, null));
        }finally {
            if(locked)
                plock.writeLock().unlock();
        }
    }

    @Override
    public void unlink(SocketIdentifier peerId) {
        boolean wLocked = plock.writeLock().tryLock();
        try {
            LinkSocket linkSocket = getLinkSocket(peerId);
            if(linkSocket instanceof PubLinkSocket pubLinkSocket) {
                // if the write lock could be acquired, then
                // remove subscriptions so that messages
                // are not sent to this peer
                if(wLocked)
                    unsubscribeAll(pubLinkSocket);
                // else, add a task to unsubscribe all topics
                else
                    tasks.add(new SubscriptionTask(pubLinkSocket, UNSUBSCRIBE, null));
                // stop any ongoing reservations
                pubLinkSocket.removeCreditsWatcherAndStopReservations();
            }
            super.unlink(peerId);
        } finally {
            if(wLocked) plock.writeLock().unlock();
        }
    }

    /**
     * Unsubscribes all topics associated with the link socket.
     * @param linkSocket link socket
     * @implNote Assumes socket's write-lock to be held.
     */
    private void unsubscribeAll(PubLinkSocket linkSocket){
        // unsubscribe all topics
        tasks.removeIf(sb -> sb.linkSocket() == linkSocket);
        if(linkSocket != null)
            handleUnsubscribeTask(linkSocket, null);
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
            boolean locked = plock.writeLock().tryLock();
            try {
                // if write lock was acquired and there aren't any subscription
                // tasks that need to be handled first, then let the middleware
                // thread handle the task
                if (locked && tasks.isEmpty())
                    handleSubscriptionTask(
                            (PubLinkSocket) linkSocket,
                            subPayload.getType(),
                            subPayload.getTopics());
                // else, queue a subscription task to be handled by a user thread
                // before a publishing a message
                else
                    tasks.add(new SubscriptionTask(
                                    (PubLinkSocket) linkSocket,
                                    subPayload.getType(),
                                    subPayload.getTopics()));
            } finally {
                if (locked) plock.writeLock().unlock();
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
    private void handleSubscriptionTask(PubLinkSocket linkSocket, byte type, Collection<String> topics){
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
    private void handleSubscribeTask(PubLinkSocket linkSocket, Collection<String> topics) {
        plock.writeLock().lock();
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
                    subscription = subscriptions.computeIfAbsent(topic,
                            t -> {
                        onTopicCreation(topic);
                        return new Subscription();
                    });
                    // adds link socket to the topic's collection of subscribers
                    subscription.addSubscriber(linkSocket);
                }
            }
        } finally {
            plock.writeLock().unlock();
        }
    }

    /**
     * Handles an unsubscribe task. Removes the peer's interest in the given topics.
     * @param linkSocket link socket
     * @param topics topics to be unsubscribed, or null if all topics should be unsubscribed.
     */
    private void handleUnsubscribeTask(PubLinkSocket linkSocket, Collection<String> topics) {
        plock.writeLock().lock();
        try {
            Subscription subscription;
            Set<String> subscriberTopics = subscribers.get(linkSocket.getPeerId());

            // if topics is null, then all the topics
            // of the subscriber must be removed.
            if(topics == null) {
                subscribers.remove(linkSocket.getPeerId());
                topics = subscriberTopics;
            }

            if(subscriberTopics != null) {
                for (String topic : topics) {
                    // if a subscription exists for the topic, then
                    // removes the link socket from its collection of subscribers
                    if(subscriberTopics.remove(topic)) {
                        subscription = subscriptions.get(topic);
                        subscription.removeSubscriber(linkSocket);
                        if (!subscription.hasSubscribers()) {
                            subscriptions.remove(topic);
                            onTopicDeletion(topic);
                        }
                    }
                }
            }
        } finally {
            plock.writeLock().unlock();
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

    protected void onTopicCreation(String topic){}

    protected void onTopicDeletion(String topic){}

    @Override
    protected SocketMsg tryReceiving() {
        throw new UnsupportedOperationException();
    }

    /* TODO - Order per topic is not provided, not globally nor locally.
            Locally, the order would have to be dictated by the write lock as
            to not allow sending of messages simultaneously. However, that would
            mean threads would be competing for the write lock to know which
            thread could send the message to the topic first. Regarding the global
            scale, while holding the write lock allows the order of the messages
            of the same topic to be delivered in the same order, the competition
            among threads publishing to the same topic would still exist. This leads
            me to believe that while the publisher socket can be used by multiple threads,
            each thread should publish not publish to the same topics if order consistency
            is required. Or, the user may opt to implement its own ordering of messages
            over the middleware.
            Another point is that if there are multiple publishers publishing to the same topic,
            then order must be determined in another way, over the middleware. This is one more
            reason to why relying in a single thread publisher per topic is ideal if ordering is
            relevant.
            .
            Nevertheless, if such local order is still desirable, then:
            Option 1:
                - Opt for the reservation model;
                - Hold write lock while searching for subscribers of interest and
                effectively queuing the message to be sent. May lead to "stalling"
                of the threads using the publisher socket since simply adding messages
                each link's outgoing queue can lead to memory exhaustion.
                -> (Doesn't seem to be a good solution)
            Option 2:
                - Have a map that maps topics to outgoing queues,
                so that order can be maintained (locally) across topics.
         */

    /**
     * Performs a synchronized send of a message. The synchronization
     * means that once the message is sent to a subscriber, the message
     * must also be sent to all the other subscribers that were subscribed
     * to the message's topic at the moment of invocation of this method.
     * Therefore, the operation can only time out when there are subscribers
     * interested in the message but which the sending of the message could
     * not be completed to a single subscriber within the given timeout.
     * @param payload publish-subscribe payload to be sent
     * @param timeout maximum amount of time to wait for the synchronized sending
     *                operation to be initiated.
     * @return true if message was sent (to all subscribers of the moment
     * for the given topic). false, otherwise.
     * @apiNote If there aren't any subscribers, the method returns "true" immediately.
     * @throws IllegalArgumentException if the payload is not a publish-subscribe payload.
     * @throws IllegalStateException if the state is not in a ready state, which is the only state
     * that allows messages to be sent.
     */
    public boolean send(PSPayload payload, Long timeout) throws InterruptedException {
        if(payload == null)
            throw new IllegalArgumentException("Message is null.");

        Long deadline = Timeout.calculateEndTime(timeout);

        // execute all pending subscription tasks
        int nrTasks;
        boolean readLocked = false;
        if(!tasks.isEmpty()){
            if(timeout == null)
                plock.writeLock().lock();
            else
                if(!plock.writeLock().tryLock(timeout, TimeUnit.MILLISECONDS))
                    return false;
            try {
                // check if socket is in a ready state
                if(getState() != SocketState.READY)
                    throw new IllegalStateException("Socket's state does not allow sending.");
                // Only handle subscription tasks that were present
                // at the moment of this verification.
                // Any tasks added after it are to be handled in a
                // following send invocation.
                nrTasks = tasks.size();
                handleSubscriptionTasks(nrTasks);
                // Acquire read lock before releasing the write lock
                // to enable other threads to send messages concurrently
                plock.readLock().lock();
                readLocked = true;
            } finally {
                plock.writeLock().unlock();
            }
        }

        // find all subscribers interested in the message,
        // i.e. all subscribers interested in a topic (or topics)
        // which has the message's topic as prefix
        boolean reserved;
        if(!readLocked) plock.readLock().lock();
        try {
            // check if socket is in a ready state
            if(getState() != SocketState.READY)
                throw new IllegalStateException("Socket's state does not allow sending.");
            // gets all subscriptions which are prefixes of the topic
            List<Map.Entry<String, Subscription>> prefixesList =
                    subscriptions.prefixesList(payload.getTopic());
            // Attempt to reserve a credit for all subscribers.
            // If it fails, then do not send to any subscriber.
            reserved = makeReservations(prefixesList, deadline);
            // After reserving credits for all subscribers,
            // consume the reservations and send the message
            // to all of them
            if(reserved) consumeReservationsAndSendMessage(prefixesList, payload);
        } finally {
            plock.readLock().unlock();
        }

        return reserved;
    }

    private boolean makeReservations(List<Map.Entry<String, Subscription>> prefixesList, Long deadline) throws InterruptedException {
        InterruptedException interruptException = null;
        boolean reserved = false;
        int reservations = 0; // number of reservations made

        for (Map.Entry<String, Subscription> entry : prefixesList) {
            for (PubLinkSocket subscriber : entry.getValue().subscribers) {
                try {
                    reserved = subscriber.tryReserveUntil(deadline);
                } catch (InterruptedException ie) {
                    // catch the interrupt exception,
                    // and throw it only after
                    // cancelling all reservations.
                    interruptException = ie;
                    reserved = false;
                } catch (LinkClosedException ignored) {
                    // make it pass as a successful reservation
                    reserved = true;
                }
                // if a credit was reserved for the subscriber, then
                // jump to the next subscriber
                if (reserved) {
                    reservations++;
                    continue;
                }
                // Else, if the operation timed out or the thread was
                // interrupted, then cancel all reservations made
                cancelReservations(prefixesList, reservations);
                // throw the interrupted exception after cancelling
                // all reservations
                if (interruptException != null)
                    throw interruptException;
                // Or, just return false, to indicate that the operation timed out
                return false;
            }
        }
        return true;
    }

    private void cancelReservations(List<Map.Entry<String, Subscription>> prefixesList, int reservations) {
        if(reservations <= 0) return;
        for (Map.Entry<String,Subscription> entry : prefixesList) {
            for (PubLinkSocket subscriber : entry.getValue().subscribers) {
                subscriber.cancelReservation();
                reservations--;
                if(reservations == 0) return;
            }
        }
    }

    private void consumeReservationsAndSendMessage(List<Map.Entry<String, Subscription>> prefixesList, PSPayload payload){
        for (Map.Entry<String, Subscription> entry : prefixesList) {
            for (PubLinkSocket subscriber : entry.getValue().subscribers) {
                boolean sent;
                try {
                    sent = subscriber.trySend(payload);
                } catch (LinkClosedException | InterruptedException ignored) {}
            }
        }
    }

    public boolean send(PSPayload payload) throws InterruptedException {
        return send(payload, null);
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
        PSPayload psPayload = PSPayload.parseFrom(payload);
        if(PSPayload.parseFrom(payload) == null)
            throw new IllegalArgumentException("Not a valid publish-subscribe message.");
        return send(psPayload, timeout);
    }

    /**
     * Creates PubSocket.
     * @param middleware middleware instance
     * @param tagId tag identifier of the socket
     * @return PubSocket instance
     * @implNote Assumes the middleware to have the PubSocket producer registered.
     */
    public static PubSocket createSocket(A3MMiddleware middleware, String tagId){
        if(middleware == null)
            throw new IllegalArgumentException("Middleware is null.");
        return middleware.createSocket(tagId, protocol.id(), PubSocket.class);
    }

    /**
     * Creates and starts a PubSocket.
     * @param middleware middleware instance
     * @param tagId tag identifier of the socket
     * @return PubSocket instance
     * @implNote Assumes the middleware to have the PubSocket producer registered.
     */
    public static PubSocket startSocket(A3MMiddleware middleware, String tagId){
        if(middleware == null)
            throw new IllegalArgumentException("Middleware is null.");
        return middleware.startSocket(tagId, protocol.id(), PubSocket.class);
    }
}
