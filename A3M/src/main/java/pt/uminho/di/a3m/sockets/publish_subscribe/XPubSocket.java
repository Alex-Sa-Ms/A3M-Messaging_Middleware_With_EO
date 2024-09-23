package pt.uminho.di.a3m.sockets.publish_subscribe;

import pt.uminho.di.a3m.core.Protocol;
import pt.uminho.di.a3m.core.SocketIdentifier;
import pt.uminho.di.a3m.core.exceptions.NoLinksException;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.core.messaging.payloads.BytePayload;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.publish_subscribe.messaging.SubscriptionsPayload;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class XPubSocket extends PubSocket{
    public static final Protocol protocol = SocketsTable.XPUB_PROTOCOL;
    public static final Set<Protocol> compatProtocols = Set.of(SocketsTable.SUB_PROTOCOL, SocketsTable.XSUB_PROTOCOL);
    private final Queue<SubscriptionsPayload> subsMsgQueue = new ConcurrentLinkedQueue<>();
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

    @Override
    protected void onTopicCreation(String topic) {
        SubscriptionsPayload subPayload =
                new SubscriptionsPayload(
                        SubscriptionsPayload.SUBSCRIBE,
                        List.of(topic));
        subsMsgQueue.add(subPayload);
        getWaitQueue().fairWakeUp(0, 1, 0, PollFlags.POLLIN);
    }

    @Override
    protected void onTopicDeletion(String topic){
        SubscriptionsPayload subPayload =
                new SubscriptionsPayload(
                        SubscriptionsPayload.UNSUBSCRIBE,
                        List.of(topic));
        subsMsgQueue.add(subPayload);
        getWaitQueue().fairWakeUp(0, 1, 0, PollFlags.POLLIN);
    }

    @Override
    protected SocketMsg tryReceiving() {
        SubscriptionsPayload subPayload = subsMsgQueue.poll();
        SocketMsg msg = null;
        if(subPayload != null) {
            // convert to byte payload where the payload also
            // contains the type of the subscription paylod
            ByteBuffer bb = ByteBuffer.allocate(subPayload.getPayload().length + 1);
            bb.put(subPayload.getType()).put(subPayload.getPayload());
            BytePayload bytePayload = new BytePayload(subPayload.getType(), bb.array());
            msg = new SocketMsg(this.getId(), this.getId(), 0, bytePayload);
        }
        return msg;
    }

    /**
     * Receives subscribe and unsubscribe messages. These messages
     * are a combination of a byte representing the type of the message
     * (either {@link SubscriptionsPayload#SUBSCRIBE} or {@link SubscriptionsPayload#UNSUBSCRIBE})
     * and a subscription payload converted into a byte array. To parse the message
     * use {@link SubscriptionsPayload#parseFrom(byte, byte[])}, with the
     * first byte being the type of the subscription payload and the remaining bytes
     * for the payload byte array.
     * @return returns subscribe/unsubscribe message in byte array format. Or, null if the
     * operation timed out.
     * @throws NoLinksException if "notifyIfNone" is set and there aren't any links.
     */
    @Override
    public byte[] receive(Long timeout, boolean notifyIfNone) throws InterruptedException {
        SocketMsg socketMsg = super.receiveMsg(timeout, notifyIfNone);
        return socketMsg != null ? socketMsg.getPayload() : null;
    }

    /**
     * @see XPubSocket#receive(Long, boolean)
     * @apiNote Assumes "notifyIfNone" as false.
     */
    @Override
    public byte[] receive(Long timeout) throws InterruptedException {
        return receive(timeout, false);
    }

    /**
     * @see XPubSocket#receive(Long, boolean)
     * @apiNote Assumes "timeout" as null and "notifyIfNone" as false.
     */
    @Override
    public byte[] receive() throws InterruptedException {
        return receive(null, false);
    }

    /** @see XPubSocket#receive(Long, boolean) */
    public SubscriptionsPayload recv(Long timeout, boolean notifyIfNone) throws InterruptedException {
        byte[] payload = receive(timeout, notifyIfNone);
        if(payload != null){
            // extract bytes from which a subscription payload will be parsed
            byte[] subPayloadArr = new byte[payload.length - 1];
            System.arraycopy(payload, 1, subPayloadArr, 0, payload.length - 1);
            // get type of subscription payload
            byte type = payload[0];
            // attempt to parse a subscription payload
            return SubscriptionsPayload.parseFrom(type, subPayloadArr);
        }
        return null;
    }

    @Override
    protected boolean isReadyToReceive() {
        return !subsMsgQueue.isEmpty();
    }
}
