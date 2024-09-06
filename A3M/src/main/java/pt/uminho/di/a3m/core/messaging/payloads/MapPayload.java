package pt.uminho.di.a3m.core.messaging.payloads;

import com.google.protobuf.InvalidProtocolBufferException;
import pt.uminho.di.a3m.core.messaging.Payload;

public class MapPayload implements Payload {
    private final byte type;
    private final SerializableMap map;
    public MapPayload(byte type, SerializableMap map) {
        this.type = type;
        this.map = map;
    }

    public SerializableMap getMap() {
        return map;
    }

    @Override
    public byte getType() {
        return type;
    }
    @Override
    public byte[] getPayload() {
        return map.serialize();
    }

    /**
     * Attempts to create a MapPayload instance from the provided
     * type and payload.
     * @param type type of the payload
     * @param payload data
     * @return MapPayload if conversion is successful
     */
    public static MapPayload convertFrom(byte type, byte[] payload) throws InvalidProtocolBufferException {
        return new MapPayload(type, SerializableMap.deserialize(payload));
    }

    public static MapPayload convertFrom(Payload payload) throws InvalidProtocolBufferException {
        if(payload == null)
            return null;
        else
            return convertFrom(payload.getType(), payload.getPayload());
    }
}
