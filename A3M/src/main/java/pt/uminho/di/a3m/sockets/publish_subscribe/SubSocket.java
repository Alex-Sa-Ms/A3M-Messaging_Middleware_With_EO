package pt.uminho.di.a3m.sockets.publish_subscribe;

import pt.uminho.di.a3m.core.LinkSocket;
import pt.uminho.di.a3m.core.Protocol;
import pt.uminho.di.a3m.core.Socket;
import pt.uminho.di.a3m.core.SocketIdentifier;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.configurable_socket.ConfigurableSocket;
import pt.uminho.di.a3m.sockets.publish_subscribe.messaging.PSMsg;
import pt.uminho.di.a3m.sockets.publish_subscribe.messaging.SubscriptionsPayload;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SubSocket extends ConfigurableSocket {
    public static final Protocol protocol = SocketsTable.SUB_PROTOCOL;
    public static final Set<Protocol> compatProtocols = Set.of(SocketsTable.PUB_PROTOCOL);
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
     * Receives publish-subscribe message in byte array format. Can be parsed using {@link PSMsg#parseFrom(byte[])}.
     * @see ConfigurableSocket#send(byte[], Long, boolean) ConfigurableSocket#send(byte[], Long, boolean) for
     * all information regarding the semantics of the 'receive()' method.
     */
    @Override
    public byte[] receive(Long timeout, boolean notifyIfNone) throws InterruptedException {
        return super.receive(timeout, notifyIfNone);
    }

    /**
     * Receives publish-subscribe message.
     * @see ConfigurableSocket#send(byte[], Long, boolean) ConfigurableSocket#send(byte[], Long, boolean) for
     * all information regarding the semantics of the 'receive()' method.
     */
    public PSMsg recv(Long timeout, boolean notifyIfNone) throws InterruptedException {
        byte[] payload = super.receive(timeout, notifyIfNone);
        return PSMsg.parseFrom(payload);
    }

    /** @see SubSocket#recv(Long, boolean) */
    public PSMsg recv(Long timeout) throws InterruptedException {
        byte[] payload = super.receive(timeout, false);
        return PSMsg.parseFrom(payload);
    }

    /** @see SubSocket#recv(Long, boolean) */
    public PSMsg recv() throws InterruptedException {
        byte[] payload = super.receive(null, false);
        return PSMsg.parseFrom(payload);
    }

    public void subscribe(List<String> topics){
        if(topics == null)
            throw new IllegalArgumentException("Topics is null.");

        SubscriptionsPayload subscribe;
        getLock().writeLock().lock();
        try {
            subscribe = new SubscriptionsPayload(SubscriptionsPayload.SUBSCRIBE);
            for (String topic : topics)
                if (subscriptions.add(topic))
                    subscribe.addTopic(topic);
        } finally {
            getLock().writeLock().unlock();
        }

        if(subscribe.hasTopics()) {
            for (LinkSocket ls : getLinkSockets()) {
                try { ls.send(subscribe, 0L); }
                catch (InterruptedException | LinkClosedException ignored) {}
            }
        }
    }

    public void subscribe(String topic){
        subscribe(List.of(topic));
    }

    // TODO - must ensure order across subscribe and unsubscribe messages too,
    //  since an UNSUBSCRIBE message may arrive before the SUBSCRIBE message
    //  and consequently have no effect.

    public void unsubscribe(List<String> topics){
        if(topics == null)
            throw new IllegalArgumentException("Topics is null.");

        SubscriptionsPayload unsubscribe;
        getLock().writeLock().lock();
        try {
            unsubscribe = new SubscriptionsPayload(SubscriptionsPayload.UNSUBSCRIBE);
            for (String topic : topics)
                if (subscriptions.remove(topic))
                    unsubscribe.addTopic(topic);
        } finally {
            getLock().writeLock().unlock();
        }

        if(unsubscribe.hasTopics()) {
            for (LinkSocket ls : getLinkSockets()) {
                try { ls.send(unsubscribe, 0L); }
                catch (Exception ignored) {}
            }
        }
    }

    public void unsubscribe(String topic){
        unsubscribe(List.of(topic));
    }
}
