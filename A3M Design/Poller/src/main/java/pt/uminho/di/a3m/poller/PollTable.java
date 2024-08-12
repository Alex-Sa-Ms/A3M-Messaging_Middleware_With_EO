package pt.uminho.di.a3m.poller;

public class PollTable {
    private int key;
    private final Object priv;
    private final PollQueueingFunc func;

    public PollTable(int key, Object priv, PollQueueingFunc func) {
        this.key = key;
        this.priv = priv;
        this.func = func;
    }

    public int getKey() {
        return key;
    }

    public Object getPriv() {
        return priv;
    }

    public PollQueueingFunc getFunc() {
        return func;
    }

    public void setKey(int key) {
        this.key = key;
    }
}
