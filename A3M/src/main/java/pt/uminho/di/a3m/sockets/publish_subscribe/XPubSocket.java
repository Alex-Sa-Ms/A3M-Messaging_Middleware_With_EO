package pt.uminho.di.a3m.sockets.publish_subscribe;

import pt.uminho.di.a3m.core.Protocol;
import pt.uminho.di.a3m.core.SocketIdentifier;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.publish_subscribe.messaging.SubscriptionsPayload;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class XPubSocket extends PubSocket{
    public static final Protocol protocol = SocketsTable.XPUB_PROTOCOL;
    public static final Set<Protocol> compatProtocols = Set.of(SocketsTable.SUB_PROTOCOL, SocketsTable.XSUB_PROTOCOL);
    private Queue<SubscriptionsPayload> subsMsgQueue = new ConcurrentLinkedQueue<>();
    public XPubSocket(SocketIdentifier sid) {
        super(sid);
    }

    @Override
    public Protocol getProtocol() {
        return protocol;
    }

    @Override
    public Set<Protocol> getCompatibleProtocols() {
        return compatProtocols;
    }

    // TODO - extend PubSocket behavior to allow receiving subscrition payloads
    //          - accumulate CLONES [^1] of the subscription payloads in a queue and make the
    //          - poll return true for POLLIN when there are messages in the queue
    //          - and also wake up waiters on receive() using POLLIN
    //          - subscribe payloads should only be queued when a subscription for
    //              the given payload is created. (use onTopicCreation())
    //          - unsubscribe payloads should only be queued when a subscription for
    //              the given payload is deleted. (use onTopicDeletion())
    //          - When closing the socket an unsubscribe payload could be created for each
    //            existing topic, however, an XPubSocket is usually paired with a XSubSocket
    //            which has no reason to say open when the XPubSocket closes. Therefore, such
    //            closure will make the connected PubSockets/XPubSockets to remove the subscriptions
    //            made using the subscriptions received from the XPubSocket.
    //  [^1] must be clones of the subscription payloads to avoid potential modifications
    //      of the payloads which can lead to misbehavior
}
