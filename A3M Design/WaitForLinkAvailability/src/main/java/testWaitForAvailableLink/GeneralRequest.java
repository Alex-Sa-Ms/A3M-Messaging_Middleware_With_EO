package testWaitForAvailableLink;

import pt.uminho.di.a3m.SocketIdentifier;

public class GeneralRequest {
    private final int clock;
    private SocketIdentifier sid = null;

    public GeneralRequest(int clock) {
        this.clock = clock;
    }

    public int getClock() {
        return clock;
    }

    public SocketIdentifier getSocketIdentifier() {
        return sid;
    }

    public void setSocketIdentifier(SocketIdentifier sid) {
        this.sid = sid;
    }

    @Override
    public String toString() {
        return "GenReq{" +
                "c=" + clock +
                ", sid=" + sid +
                '}';
    }
}
