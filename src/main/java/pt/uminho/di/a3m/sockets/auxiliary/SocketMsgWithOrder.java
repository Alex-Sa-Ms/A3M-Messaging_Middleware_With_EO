package pt.uminho.di.a3m.sockets.auxiliary;

import pt.uminho.di.a3m.core.SocketIdentifier;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;

import java.nio.ByteBuffer;

public class SocketMsgWithOrder extends SocketMsg {
    Integer order = null;

    public SocketMsgWithOrder(String srcNodeId, String srcTagId, String destNodeId, String destTagId, byte type, int clockId, byte[] payload, int order) {
        super(srcNodeId, srcTagId, destNodeId, destTagId, type, clockId, payload);
        this.order = order;
    }

    public SocketMsgWithOrder(SocketIdentifier srcId, SocketIdentifier destId, byte type, int clockId, byte[] payload, int order) {
        super(srcId, destId, type, clockId, payload);
        this.order = order;
    }

    public SocketMsgWithOrder(String srcNodeId, String srcTagId, String destNodeId, String destTagId, int clockId, Payload payload, int order) {
        super(srcNodeId, srcTagId, destNodeId, destTagId, clockId, payload);
        this.order = order;
    }

    public SocketMsgWithOrder(SocketIdentifier srcId, SocketIdentifier destId, int clockId, Payload payload, int order) {
        super(srcId, destId, clockId, payload);
        this.order = order;
    }

    /**
     * Adds order number to a payload, so that it can then be parsed
     * into a SocketMsgWithOrder.
     * @param order order number
     * @param payload payload that should be complemented with an order number
     * @return payload with an order number embedded
     */
    public static byte[] createPayloadWithOrder(int order, byte[] payload) {
        return ByteBuffer.allocate(payload.length + 4) // allocate space for payload plus order number
                        .putInt(order) // put order number
                        .put(payload) // put payload
                        .array(); // convert to byte array
    }

    /**
     * Converts a socket message into a socket message with order.
     * The order number is extracted from the message payload
     * and then the payload is truncated as to not include the
     * extracted order number.
     * @param msg message to have the order number extracted
     * @return socket message with order, or null, if the message
     * could not be parsed.
     */
    public static SocketMsgWithOrder parseFrom(SocketMsg msg){
        try {
            if(msg != null && msg.getPayload() != null) {
                // gets order value and truncates payload to not contain the order
                ByteBuffer buffer = ByteBuffer.wrap(msg.getPayload());
                int order = buffer.getInt();
                byte[] truncPayload = new byte[buffer.remaining()];
                buffer.get(truncPayload);
                return new SocketMsgWithOrder(msg.getSrcId(), msg.getDestId(), msg.getType(), msg.getClockId(), truncPayload, order);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public Integer getOrder() {
        return order;
    }

    @Override
    public String toString() {
        return "SockWithOrder{" +
                "order=" + order +
                "} ";
    }
}