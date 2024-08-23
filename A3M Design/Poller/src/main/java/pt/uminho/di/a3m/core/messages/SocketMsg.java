package pt.uminho.di.a3m.core.messages;

public class SocketMsg extends Msg{
    private final String srcTagId;
    private final String destTagId;
    public SocketMsg(String srcNodeId, String destNodeId, byte type, byte[] payload, String srcTagId, String destTagId) {
        super(srcNodeId, destNodeId, type, payload);
        this.srcTagId = srcTagId;
        this.destTagId = destTagId;
    }

    public String getSrcTagId() {
        return srcTagId;
    }

    public String getDestTagId() {
        return destTagId;
    }
}
