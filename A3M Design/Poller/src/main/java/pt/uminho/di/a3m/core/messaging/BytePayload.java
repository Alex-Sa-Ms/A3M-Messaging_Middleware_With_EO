package pt.uminho.di.a3m.core.messaging;

public class BytePayload implements Payload{
    private final byte type;
    private final byte[] payload;
    public BytePayload(byte type, byte[] payload) {
        this.type = type;
        this.payload = payload;
    }

    @Override
    public byte getType() {
        return type;
    }

    @Override
    public byte[] getPayload() {
        return payload;
    }
}
