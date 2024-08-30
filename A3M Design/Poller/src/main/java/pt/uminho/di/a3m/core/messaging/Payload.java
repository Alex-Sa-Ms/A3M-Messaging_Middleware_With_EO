package pt.uminho.di.a3m.core.messaging;

public interface Payload {
    /**
     * Used to identify the type of payload.
     * @return type of payload
     */
    byte getType();

    /**
     * @return the actual payload
     */
    byte[] getPayload();

    /**
     * Fills payload object by the parsing
     * the content of the payload.
     *
     * @param type    type of the payload
     * @param payload content of the payload
     * @return
     */
    // TODO - if I decide to continue on implementing all payloads like this
    //boolean parseFrom(byte type, byte[] payload);
}
