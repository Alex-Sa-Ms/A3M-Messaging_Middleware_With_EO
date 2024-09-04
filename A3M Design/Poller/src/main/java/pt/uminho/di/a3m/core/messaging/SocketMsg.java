package pt.uminho.di.a3m.core.messaging;

import pt.uminho.di.a3m.core.SocketIdentifier;

import java.util.Arrays;

public class SocketMsg extends Msg{
    private String srcTagId;
    private String destTagId;
    private int clockId;

    public SocketMsg(String srcNodeId, String srcTagId, String destNodeId, String destTagId, byte type, int clockId, byte[] payload) {
        super(srcNodeId, destNodeId, type, payload);
        this.srcTagId = srcTagId;
        this.destTagId = destTagId;
        this.clockId = clockId;
    }

    public SocketMsg(SocketIdentifier srcId, SocketIdentifier destId, byte type, int clockId, byte[] payload){
        this(srcId.nodeId(), srcId.tagId(), destId.nodeId(), destId.tagId(), type, clockId, payload);
    }

    public SocketMsg(String srcNodeId, String srcTagId, String destNodeId, String destTagId, int clockId, Payload payload) {
        super(srcNodeId, destNodeId, payload);
        this.srcTagId = srcTagId;
        this.destTagId = destTagId;
        this.clockId = clockId;
    }

    public SocketMsg(SocketIdentifier srcId, SocketIdentifier destId, int clockId, Payload payload){
        this(srcId.nodeId(), srcId.tagId(), destId.nodeId(), destId.tagId(), payload.getType(), clockId, payload.getPayload());
    }

    public SocketMsg(SocketMsg msg){
        super(msg);
        this.clockId = msg.clockId;
        this.srcTagId = msg.srcTagId;
        this.destTagId = msg.destTagId;
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

    public int getClockId() {
        return clockId;
    }

    @Override
    public String toString() {
        return "SockMsg{" +
                "srcId=" + getSrcId() +
                ", destId=" + getDestId() +
                ", type=" + getType() +
                ", clockId=" + getClockId() +
                ", payload=" + Arrays.toString(getPayload())
                + '}';
    }
}