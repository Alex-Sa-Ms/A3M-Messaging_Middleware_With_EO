package pt.uminho.di.a3m.sockets.publish_subscribe.messaging;

import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Publish-Subscribe Message
 */
public class PSPayload implements Payload {
    private String topic;
    private byte[] content;

    public PSPayload(byte[] topic, byte[] content) {
        if(topic == null)
            throw new IllegalArgumentException("Topic is null");
        if(content == null)
            throw new IllegalArgumentException("Content is null");
        this.topic = new String(topic);
        this.content = content;
    }

    public PSPayload(String topic, byte[] content) {
        if(topic == null)
            throw new IllegalArgumentException("Topic is null");
        if(content == null)
            throw new IllegalArgumentException("Content is null");
        this.topic = topic;
        this.content = content;
    }

    public PSPayload(String topic, String content) {
        if(topic == null)
            throw new IllegalArgumentException("Topic is null");
        if(content == null)
            throw new IllegalArgumentException("Content is null");
        this.topic = topic;
        this.content = content.getBytes();
    }

    public PSPayload(String topic){
        this(topic, new byte[]{});
    }

    public PSPayload(byte[] topic){
        this(topic, new byte[]{});
    }

    public String getTopic() {
        return topic;
    }

    public byte[] getTopicBytes() {
        return topic.getBytes();
    }

    public byte[] getContent() {
        return content;
    }

    public String getContentStr() {
        return new String(content);
    }

    public void setTopic(String topic) {
        if(topic == null) throw new IllegalArgumentException("Topic is null.");
        this.topic = topic;
    }

    public void setTopicBytes(byte[] topic) {
        if(topic == null) throw new IllegalArgumentException("Topic is null.");
        this.topic = new String(topic);
    }

    public void setContent(byte[] content) {
        if(content == null) throw new IllegalArgumentException("Content is null.");
        this.content = content;
    }

    public void setContentStr(String content) {
        if(content == null) throw new IllegalArgumentException("Content is null.");
        this.content = content.getBytes();
    }

    /**
     * @apiNote Not relevant for user use.
     */
    @Override
    public byte getType() {
        return MsgType.DATA;
    }

    /**
     * Serializes this instance into a byte array.
     * @return byte array that represents this instance.
     */
    @Override
    public byte[] getPayload(){
        byte[] topicArr = this.topic.getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(topicArr.length + this.content.length + 4 * 2);
        buffer.putInt(topicArr.length);
        buffer.put(topicArr);
        buffer.putInt(this.content.length);
        buffer.put(this.content);
        return buffer.array();
    }

    /**
     * Parses a socket message of type data, extracting
     * the topic and the content.
     * @param rawPayload Socket message payload
     * @return publish-subscribe message, or null if the raw payload
     * is not a publish-subscribe message.
     */
    public static PSPayload parseFrom(byte[] rawPayload){
        if(rawPayload != null) {
            try {
                ByteBuffer buffer = ByteBuffer.wrap(rawPayload);
                // get topic
                int length = buffer.getInt();
                byte[] topic = new byte[length];
                buffer.get(topic, 0, length);
                // get payload
                length = buffer.getInt();
                byte[] payload = new byte[length];
                buffer.get(payload, 0, length);
                return new PSPayload(topic, payload);
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;

        PSPayload psMsg = (PSPayload) object;

        if (!topic.equals(psMsg.topic)) return false;
        return Arrays.equals(content, psMsg.content);
    }

    @Override
    public String toString() {
        return "PSMsg{" +
                "topic='" + topic + '\'' +
                ", content='" + new String(content) + '\'' +
                '}';
    }
}
