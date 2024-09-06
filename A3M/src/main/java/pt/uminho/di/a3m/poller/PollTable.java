package pt.uminho.di.a3m.poller;

import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;

public class PollTable {
    private int key;
    private Object priv;
    private PollQueueingFunc func;

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

    public void setPriv(Object priv) {
        this.priv = priv;
    }

    public void setFunc(PollQueueingFunc func) {
        this.func = func;
    }

    public static boolean pollDoesNotWait(PollTable pt){
        return pt == null || pt.getFunc() == null;
    }

    public void pollWait(Pollable p, WaitQueueEntry entry){
        this.func.apply(p, entry, this);
    }
}
