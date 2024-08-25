package pt.uminho.di.a3m.core.flowcontrol;

import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;

import java.nio.ByteBuffer;

public class FlowCreditsPayload implements Payload {
    private final int credits;
    public FlowCreditsPayload(int credits) {
        this.credits = credits;
    }
    @Override
    public byte getType() {
        return MsgType.FLOW;
    }
    @Override
    public byte[] getPayload() {
        return ByteBuffer.allocate(4).putInt(credits).array();
    }
    public int getCredits() {
        return credits;
    }

    /**
     * Checks if payload type matches and converts the payload array
     * to a FlowCreditsPayload payload.
     * @param type type of the payload
     * @param payload payload array
     * @return a FlowCreditsPayload if the source payload is a legit
     * flow control credits payload. Otherwise, returns null.
     */
    public static FlowCreditsPayload convertFrom(byte type, byte[] payload){
        if(type != MsgType.FLOW || payload.length != 4)
            return null;
        else
            return new FlowCreditsPayload(
                    ByteBuffer.wrap(payload).getInt());
    }

    /**
     * Converts a flow control credits payload in another format,
     * for instance BytePayload, to a FlowCreditsPayload payload.
     * @param payload conversion source
     * @return a FlowCreditsPayload if the source payload is a legit
     * flow control credits payload. Otherwise, returns null.
     */
    public static FlowCreditsPayload convertFrom(Payload payload){
        if(payload == null)
            return null;
        else
            return convertFrom(payload.getType(), payload.getPayload());
    }
}