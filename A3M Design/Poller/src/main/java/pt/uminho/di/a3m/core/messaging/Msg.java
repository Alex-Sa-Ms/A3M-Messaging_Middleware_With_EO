package pt.uminho.di.a3m.core.messaging;
public class Msg{
    private final String srcNodeId;
    private final String destNodeId;
    private final byte type;
    private final byte[] payload;

    public Msg(String srcNodeId, String destNodeId, byte type, byte[] payload) {
        this.srcNodeId = srcNodeId;
        this.destNodeId = destNodeId;
        this.type = type;
        this.payload = payload;
    }

    public Msg(String srcNodeId, String destNodeId, Payload payload) {
        this.srcNodeId = srcNodeId;
        this.destNodeId = destNodeId;
        this.type = payload.getType();
        this.payload = payload.getPayload();
    }

    public String getSrcNodeId() {
        return srcNodeId;
    }

    public String getDestNodeId() {
        return destNodeId;
    }

    public byte getType() {
        return type;
    }

    public byte[] getPayload() {
        return payload;
    }

    public static boolean isType(Msg msg, byte type){
        return msg != null && msg.type == type;
    }
}
