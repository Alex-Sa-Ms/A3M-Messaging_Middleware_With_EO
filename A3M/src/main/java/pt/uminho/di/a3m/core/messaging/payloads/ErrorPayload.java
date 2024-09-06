package pt.uminho.di.a3m.core.messaging.payloads;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
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

    public byte getCode() {
        return code;
    }

    public String getText() {
        return text;
    }

    private static byte[] toByteArray(byte errorByte){
        return new byte[]{errorByte};
    }

    public static ErrorPayload parseFrom(byte[] payload){
        try {
            CoreMessages.ErrorPayload errPayload = CoreMessages.ErrorPayload.parseFrom(payload);
            if(errPayload.getCode() == ByteString.EMPTY || errPayload.getCode().size() > 1)
                return null;
            else
                return new ErrorPayload(errPayload.getCode().byteAt(0), errPayload.getText());
        } catch (InvalidProtocolBufferException e) {
            return null;
        }
    }
}