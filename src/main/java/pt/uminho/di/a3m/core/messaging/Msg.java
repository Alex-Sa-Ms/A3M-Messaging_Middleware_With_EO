package pt.uminho.di.a3m.core.messaging;
public class Msg{
    private String srcNodeId;
    private String destNodeId;
    private byte type;
    private byte[] payload;
    public Msg(Msg msg){
        if(msg == null)
            throw new IllegalArgumentException("Msg is null.");
        this.srcNodeId = msg.srcNodeId;
        this.destNodeId = msg.destNodeId;
        this.type = msg.type;
        this.payload = msg.payload;
    }

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
        return msg != null && msg.getType() == type;
    }

    private void setType(byte type){
        this.type = type;
    }
    protected void setPayload(byte[] payload) {
        this.payload = payload;
    }
}
