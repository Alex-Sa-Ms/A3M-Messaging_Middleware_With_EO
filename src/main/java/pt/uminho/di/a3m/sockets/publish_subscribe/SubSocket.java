package pt.uminho.di.a3m.sockets.publish_subscribe;

import pt.uminho.di.a3m.core.*;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.configurable_socket.ConfigurableSocket;
import pt.uminho.di.a3m.sockets.publish_subscribe.messaging.PSPayload;
import pt.uminho.di.a3m.sockets.publish_subscribe.messaging.SubscriptionsPayload;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO - unsubscribe() is not instantaneous, so, should
//  filtering be done at subscriber side too?

public class SubSocket extends ConfigurableSocket {
    public static final Protocol protocol = SocketsTable.SUB_PROTOCOL;
    public static final Set<Protocol> compatProtocols = Set.of(SocketsTable.PUB_PROTOCOL, SocketsTable.XPUB_PROTOCOL);
    // set of subscribed topics
    private final Set<String> subscriptions = new HashSet<>();

    public SubSocket(SocketIdentifier sid) {
        super(sid, true, false, true);
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
    protected void customOnLinkEstablished(LinkSocket publisher) {
        super.customOnLinkEstablished(publisher);
        SubscriptionsPayload subscribe;
        getLock().readLock().lock();
        try {
             subscribe = new SubscriptionsPayload(
                     SubscriptionsPayload.SUBSCRIBE,
                     subscriptions);
        } finally { getLock().readLock().unlock(); }
        try { publisher.send(subscribe, 0L); }
        catch (InterruptedException | LinkClosedException ignored) {}
    }

    /**
     * Receives publish-subscribe payload in byte array format. Can be parsed using {@link PSPayload#parseFrom(byte[])}.
     * @see ConfigurableSocket#send(byte[], Long, boolean) ConfigurableSocket#send(byte[], Long, boolean) for
     * all information regarding the semantics of the 'receive()' method.
     */
    @Override
    public byte[] receive(Long timeout, boolean notifyIfNone) throws InterruptedException {
        return super.receive(timeout, notifyIfNone);
    }

    /**
     * Receives publish-subscribe payload.
     * @see ConfigurableSocket#send(byte[], Long, boolean) ConfigurableSocket#send(byte[], Long, boolean) for
     * all information regarding the semantics of the 'receive()' method.
     */
    public PSPayload recv(Long timeout, boolean notifyIfNone) throws InterruptedException {
        byte[] payload = receive(timeout, notifyIfNone);
        return PSPayload.parseFrom(payload);
    }

    /** @see SubSocket#recv(Long, boolean) */
    public PSPayload recv(Long timeout) throws InterruptedException {
        return recv(timeout, false);
    }

    /** @see SubSocket#recv(Long, boolean) */
    public PSPayload recv() throws InterruptedException {
        return recv(null, false);
    }

    /**
     * To subscribe a list of topics.
     * @param topics list of topics that should be subscribed.
     * @throws IllegalStateException if the state is not in a ready state,
     * which is the only state that allows topics to be subscribed/unsubscribed.
     */
    public void subscribe(List<String> topics){
        if(topics == null)
            throw new IllegalArgumentException("Topics is null.");

        SubscriptionsPayload subscribe;
        getLock().writeLock().lock();
        try {
            // check if socket is in a ready state
            if(getState() != SocketState.READY)
                throw new IllegalStateException("Socket's state does not allow subscribing.");
            subscribe = new SubscriptionsPayload(SubscriptionsPayload.SUBSCRIBE);
            for (String topic : topics)
                if (subscriptions.add(topic))
                    subscribe.addTopic(topic);
            getLock().readLock().lock();
        } finally {
            getLock().writeLock().unlock();
        }

        try {
            if (subscribe.hasTopics()) {
                forEachLinkSocket(linkSocket -> {
                    try { linkSocket.send(subscribe, 0L); }
                    catch (InterruptedException | LinkClosedException ignored) {}
                });
            }
        }finally {
            getLock().readLock().unlock();
        }
    }

    /**
     * To subscribe a topic.
     * @param topic topic that should be subscribed.
     * @throws IllegalStateException if the state is not in a ready state,
     * which is the only state that allows topics to be subscribed/unsubscribed.
     */
    public void subscribe(String topic){
        subscribe(List.of(topic));
    }

    /**
     * To unsubscribe a list of topics.
     * @param topics list of topics that should be unsubscribed.
     * @throws IllegalStateException if the state is not in a ready state,
     * which is the only state that allows topics to be subscribed/unsubscribed.
     */
    public void unsubscribe(List<String> topics){
        if(topics == null)
            throw new IllegalArgumentException("Topics is null.");

        SubscriptionsPayload unsubscribe;
        getLock().writeLock().lock();
        try {
            // check if socket is in a ready state
            if(getState() != SocketState.READY)
                throw new IllegalStateException("Socket's state does not allow unsubscribing.");
            unsubscribe = new SubscriptionsPayload(SubscriptionsPayload.UNSUBSCRIBE);
            for (String topic : topics)
                if (subscriptions.remove(topic))
                    unsubscribe.addTopic(topic);
            getLock().readLock().lock();
        } finally {
            getLock().writeLock().unlock();
        }

        try {
            if (unsubscribe.hasTopics()) {
                forEachLinkSocket(linkSocket -> {
                    try { linkSocket.send(unsubscribe, 0L); }
                    catch (InterruptedException | LinkClosedException ignored) {}
                });
            }
        }finally {
            getLock().readLock().unlock();
        }
    }

    /**
     * To unsubscribe a topic.
     * @param topic topic that should be unsubscribed.
     * @throws IllegalStateException if the state is not in a ready state,
     * which is the only state that allows topics to be subscribed/unsubscribed.
     */
    public void unsubscribe(String topic){
        unsubscribe(List.of(topic));
    }
    
    /**
     * Creates SubSocket.
     * @param middleware middleware instance
     * @param tagId tag identifier of the socket
     * @return SubSocket instance
     * @implNote Assumes the middleware to have the SubSocket producer registered.
     */
    public static SubSocket createSocket(A3MMiddleware middleware, String tagId){
        if(middleware == null)
            throw new IllegalArgumentException("Middleware is null.");
        return middleware.createSocket(tagId, protocol.id(), SubSocket.class);
    }

    /**
     * Creates and starts a SubSocket.
     * @param middleware middleware instance
     * @param tagId tag identifier of the socket
     * @return SubSocket instance
     * @implNote Assumes the middleware to have the SubSocket producer registered.
     */
    public static SubSocket startSocket(A3MMiddleware middleware, String tagId){
        if(middleware == null)
            throw new IllegalArgumentException("Middleware is null.");
        return middleware.startSocket(tagId, protocol.id(), SubSocket.class);
    }
}
