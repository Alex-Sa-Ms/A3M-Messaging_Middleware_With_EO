package pt.uminho.di.a3m.sockets.publish_subscribe;

import pt.uminho.di.a3m.core.Protocol;
import pt.uminho.di.a3m.core.SocketIdentifier;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.publish_subscribe.messaging.SubscriptionsPayload;

import java.nio.ByteBuffer;
import java.util.Set;

import static pt.uminho.di.a3m.sockets.publish_subscribe.messaging.SubscriptionsPayload.*;

public class XSubSocket extends SubSocket{
    public static final Protocol protocol = SocketsTable.XSUB_PROTOCOL;
    public static final Set<Protocol> compatProtocols = Set.of(SocketsTable.PUB_PROTOCOL, SocketsTable.XPUB_PROTOCOL);

    public XSubSocket(SocketIdentifier sid) {
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

    /**
     * Extracts from the payload a byte representing the type of
     * subscription operation ({@link SubscriptionsPayload#SUBSCRIBE}
     * or {@link SubscriptionsPayload#UNSUBSCRIBE}) and a
     * subscription payload ({@link SubscriptionsPayload}). If a valid
     * payload is received, then, the send operation is analogous
     * to invoking subscribe() or unsubscribe() depending on the byte
     * that represents the type of subscription operation.
     * @implNote The operation is non-blocking meaning the "timeout"
     * and "notifyIfNone" arguments are not considered.
     */
    @Override
    public boolean send(byte[] payload, Long timeout, boolean notifyIfNone) {
        if(payload == null || payload.length == 0) throw new IllegalArgumentException("Invalid subscription payload.");
        try{
            // extract bytes from which a subscription payload will be parsed
            byte[] subPayloadArr = new byte[payload.length - 1];
            System.arraycopy(payload, 1, subPayloadArr, 0, payload.length - 1);
            // get type of subscription payload
            byte type = payload[0];
            // attempt to parse a subscription payload
            SubscriptionsPayload subPayload = SubscriptionsPayload.parseFrom(type, subPayloadArr);
            if(subPayload == null) throw new IllegalArgumentException("Invalid subscription payload.");
            if(subPayload.getType() == SUBSCRIBE){
                subscribe(subPayload.getTopics());
            }else{ // if(psPayload.getType() == UNSUBSCRIBE){
                unsubscribe(subPayload.getTopics());
            }
        }catch (Exception e){
            throw new IllegalArgumentException("Invalid subscription payload.");
        }
        return true;
    }

    /** @see XSubSocket#send(byte[], Long, boolean) */
    @Override
    public boolean send(byte[] payload, Long timeout) {
        return send(payload, timeout, false);
    }

    /** @see XSubSocket#send(byte[], Long, boolean) */
    @Override
    public void send(byte[] payload) {
        send(payload, null, false);
    }

    /**
     * Converts a subscription payload into a byte array
     * containing a byte that identifies the type of the
     * subscription payload (retrieved using {@link SubscriptionsPayload#getType()})
     * and subscription payload in a byte array form
     * (retrieved using {@link SubscriptionsPayload#getPayload()}).
     *
     * @param subPayload subscription payload containing the type and topics
     *                   to be associated with the operation.
     * @see XSubSocket#send(byte[], Long, boolean)
     */
    public void send(SubscriptionsPayload subPayload){
        if(subPayload == null || subPayload.getPayload() == null)
            throw new IllegalArgumentException("Invalid subscription payload.");
        ByteBuffer bb = ByteBuffer.allocate(subPayload.getPayload().length + 1);
        bb.put(subPayload.getType()).put(subPayload.getPayload());
        send(bb.array());
    }
}
