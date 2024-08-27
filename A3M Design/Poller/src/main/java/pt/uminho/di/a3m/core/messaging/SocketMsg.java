package pt.uminho.di.a3m.core.messaging;

import pt.uminho.di.a3m.core.SocketIdentifier;

public class SocketMsg extends Msg{
    private final String srcTagId;
    private final String destTagId;

    public SocketMsg(String srcNodeId, String srcTagId, String destNodeId, String destTagId, byte type, byte[] payload) {
        super(srcNodeId, destNodeId, type, payload);
        this.srcTagId = srcTagId;
        this.destTagId = destTagId;
    }

    public SocketMsg(SocketIdentifier srcId, SocketIdentifier destId, byte type, byte[] payload){
        this(srcId.nodeId(), srcId.tagId(), destId.nodeId(), destId.tagId(), type, payload);
    }

    public SocketMsg(String srcNodeId, String srcTagId, String destNodeId, String destTagId, Payload payload) {
        super(srcNodeId, destNodeId, payload);
        this.srcTagId = srcTagId;
        this.destTagId = destTagId;
    }

    public SocketMsg(SocketIdentifier srcId, SocketIdentifier destId, Payload payload){
        this(srcId.nodeId(), srcId.tagId(), destId.nodeId(), destId.tagId(), payload.getType(), payload.getPayload());
    }

    public String getSrcTagId() {
        return srcTagId;
    }

    public String getDestTagId() {
        return destTagId;
    }

    public SocketIdentifier getSrcId(){
        return new SocketIdentifier(getSrcNodeId(),getSrcTagId());
    }

    public SocketIdentifier getDestId(){
        return new SocketIdentifier(getDestNodeId(),getDestTagId());
    }
}