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
}
