package pt.uminho.di.a3m.core.messaging.payloads;

import com.google.protobuf.ByteString;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;

public class ErrorPayload implements Payload {
    private final byte code;
    private final String text;

    public ErrorPayload(byte code, String text) {
        this.code = code;
        this.text = text;
    }

    public ErrorPayload(byte code) {
        this(code, null);
    }

    @Override
    public byte getType() {
        return MsgType.ERROR;
    }

    @Override
    public byte[] getPayload() {
        ByteString errorCode = ByteString.copyFrom(ErrorPayload.toByteArray(code));
        CoreMessages.ErrorPayload.Builder builder =
                CoreMessages.ErrorPayload.newBuilder().setCode(errorCode);
        if(text != null)
            builder.setText(text);
        return builder.build().toByteArray();
    }

    public static byte[] toByteArray(byte errorByte){
        return new byte[]{errorByte};
    }
}