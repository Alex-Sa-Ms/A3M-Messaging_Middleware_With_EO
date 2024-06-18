package version2.msgs;

public class AcksMsg extends Msg {
    int acks;

    public AcksMsg(int acks) {
        this.acks = acks;
    }

    public int getAcks() {
        return acks;
    }

    public void setAcks(int acks) {
        this.acks = acks;
    }
}
