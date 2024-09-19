package pt.uminho.di.a3m.sockets.publish_subscribe.messaging;

import pt.uminho.di.a3m.core.messaging.Payload;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Payload used to subscribe or unsubscribe topics.
 */
public class SubscriptionsPayload implements Payload {
    private byte type;
    private final List<String> topics = new ArrayList<>();
    public static final byte UNSUBSCRIBE = Byte.MAX_VALUE;
    public static final byte SUBSCRIBE = UNSUBSCRIBE - 0x01;

    public SubscriptionsPayload(byte type, Collection<String> topics) {
        if(!checkType(type))
            throw new IllegalArgumentException();
        this.type = type;
        addTopics(topics);
    }

    public SubscriptionsPayload(byte type) {
        this(type, null);
    }

    private static boolean checkType(byte type){
        return type == UNSUBSCRIBE || type == SUBSCRIBE;
    }

    public void addTopic(String topic){
        topics.add(topic);
    }

    public void addTopics(Collection<String> topics){
        if(topics != null)
            topics.stream().filter(Objects::nonNull)
                           .forEach(this.topics::add);
    }

    public boolean hasTopics(){
        return !topics.isEmpty();
    }

    /**
     * @return list of topics.
     * @apiNote list of topics returned is the list used by the instance,
     * i.e. a clone is not performed.
     */
    public List<String> getTopics(){
        return topics;
    }

    @Override
    public byte getType() {
        return type;
    }

    @Override
    public byte[] getPayload() {
        ByteBuffer intToBytes = ByteBuffer.allocate(4);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            intToBytes.putInt(topics.size());
            out.write(intToBytes.array());
            for (String subscription : topics){
                intToBytes.clear();
                intToBytes.putInt(subscription.length());
                out.write(intToBytes.array());
                out.write(subscription.getBytes());
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses a subscription payload.
     * @param type type of the payload
     * @param payload byte array from which a subscription payload should be extracted
     * @return subscription payload if the payload passed as parameter
     * allows a subscription payload to be extracted. null, otherwise.
     */
    public static SubscriptionsPayload parseFrom(byte type, byte[] payload){
        if(payload == null || !checkType(type))
            return null;

        try {
            SubscriptionsPayload subPayload = new SubscriptionsPayload(type);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            int count = buffer.getInt();
            int strSize;
            byte[] str;

            for (int i = 0; i < count; i++) {
                strSize = buffer.getInt();
                str = new byte[strSize];
                buffer.get(str, 0, strSize);
                subPayload.addTopic(new String(str));
            }

            return subPayload;
        }catch (Exception ignored){}

        return null;
    }

    /**
     * Parses a subscription payload.
     * @param payload payload from which a subscription payload object should
     *                be extracted.
     * @return subscription payload if the payload passed as parameter
     * allows a subscription payload to be extracted. null, otherwise.
     */
    public static SubscriptionsPayload parseFrom(Payload payload){
        if(payload == null)
            return null;
        return parseFrom(payload.getType(), payload.getPayload());
    }

    @Override
    public String toString() {
        return "SubscriptionsPayload{" +
                "type=" + (type == SUBSCRIBE ? "SUBSCRIBE" : "UNSUBSCRIBE") +
                ", topics=" + topics +
                '}';
    }
}
