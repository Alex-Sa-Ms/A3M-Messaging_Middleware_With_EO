package pt.uminho.di.a3m.sockets.request_reply;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import pt.uminho.di.a3m.core.SocketIdentifier;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Request-Reply Message. Allows routing information be set.
 */
public class RRMsg {
    private final Deque<Object> routingIds = new ArrayDeque<>();
    private byte[] payload;

    /**
     * Creates a Request-Reply message from a collection of
     * routing identifiers and a payload array.
     * @param routingIds routing identifiers, or null, if they do not exist.
     * @param payload payload array
     */
    private RRMsg(Collection<Object> routingIds, byte[] payload) {
        if(routingIds != null)
            this.routingIds.addAll(routingIds);
        if(payload == null)
            throw new IllegalArgumentException("Payload cannot be null.");
        this.payload = payload;
    }

    public RRMsg(){
        this.payload = new byte[]{};
    }

    public RRMsg(byte[] payload){
        this.payload = payload;
    }

    public byte[] getPayload() {
        return payload;
    }

    /**
     * Parses a Request-Reply message in byte array format.
     * @param rrMsgArr request-reply message in byte array format
     * @throws IllegalArgumentException if the provided array is not in byte array format.
     */
    public static RRMsg parseFrom(byte[] rrMsgArr){
        try {
            RequestReply.RRMsg protoRRMsg = RequestReply.RRMsg.parseFrom(rrMsgArr);
            List<Object> routingIds =
                    protoRRMsg.getRoutingIdsList()
                              .stream().map(routingId -> {
                                    if (routingId.hasIntIdentifier())
                                        return routingId.getIntIdentifier();
                                    else if(routingId.hasSocketIdentifier()) {
                                        RequestReply.SocketIdentifier rrSid = routingId.getSocketIdentifier();
                                        return new SocketIdentifier(rrSid.getNodeId(), rrSid.getTagId());
                                    }
                                    else return null;
                              }).toList();
            return new RRMsg(routingIds, protoRRMsg.getPayload().toByteArray());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Not a request-reply message in byte array format.");
        }
    }

    /**
     * Duplicates a request-reply message.
     * @param rrMsg request-reply message to be duplicated.
     * @return duplicate of the provided request-reply message
     */
    public static RRMsg duplicate(RRMsg rrMsg){
        if(rrMsg == null)
            throw new IllegalArgumentException("Message is null.");
        return new RRMsg(new ArrayDeque<>(rrMsg.routingIds), rrMsg.payload);
    }

    public boolean hasRoutingIdentifiers(){
        return !routingIds.isEmpty();
    }

    /**
     * Prepends integer routing identifier.
     * @param id integer identifier
     */
    public void addRoutingIdentifier(int id){
        routingIds.addFirst(id);
    }

    /**
     * Prepends socket routing identifier.
     * @param sid socket identifier
     */
    public void addRoutingIdentifier(SocketIdentifier sid){
        routingIds.addFirst(sid);
    }

    /**
     * Polls the first routing identifier.
     * @return routing identifier. It may be an integer or a {@link SocketIdentifier SocketIdentifier}.
     */
    public Object pollRoutingIdentifier(){
        return routingIds.poll();
    }

    /**
     * Sets new payload.
     * @param payload new payload byte array.
     */
    public void setPayload(byte[] payload){
        if(payload == null)
            throw new IllegalArgumentException("Payload cannot be null.");
        this.payload = payload;
    }

    private RequestReply.RoutingId.Builder socketIdentifierBuilder(SocketIdentifier sid){
        RequestReply.SocketIdentifier.Builder protoSidBuilder =
                RequestReply.SocketIdentifier.newBuilder()
                                             .setNodeId(sid.nodeId())
                                             .setTagId(sid.tagId());
        return RequestReply.RoutingId.newBuilder().setSocketIdentifier(protoSidBuilder);
    }

    private RequestReply.RoutingId.Builder intIdentifierBuilder(int id){
        return RequestReply.RoutingId.newBuilder().setIntIdentifier(id);
    }

    public byte[] toByteArray(){
        RequestReply.RRMsg.Builder builder = RequestReply.RRMsg.newBuilder();
        for (Object routingId : this.routingIds){
            if(routingId instanceof Integer i)
                builder.addRoutingIds(intIdentifierBuilder(i));
            else if (routingId instanceof SocketIdentifier sid)
                builder.addRoutingIds(socketIdentifierBuilder(sid));
        }
        builder.setPayload(ByteString.copyFrom(this.payload));
        return builder.build().toByteArray();
    }

    @Override
    public String toString() {
        return "RRMsg{" +
                "routingIds=" + routingIds +
                ", payload=" + StandardCharsets.UTF_8.decode(ByteBuffer.wrap(payload)) +
                '}';
    }
}
