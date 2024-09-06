package pt.uminho.di.a3m.core.SimpleSocket;

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

    public static SocketMsgWithOrder parseFrom(SocketMsg msg){
        if(msg == null || msg.getPayload() == null)
            return null;
        // gets order value and truncates payload to not contain the order
        ByteBuffer buffer = ByteBuffer.wrap(msg.getPayload());
        int order = buffer.getInt();
        byte[] truncPayload = new byte[buffer.remaining()];
        buffer.get(truncPayload);
        return new SocketMsgWithOrder(msg.getSrcId(), msg.getDestId(), msg.getType(), msg.getClockId(), truncPayload, order);
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