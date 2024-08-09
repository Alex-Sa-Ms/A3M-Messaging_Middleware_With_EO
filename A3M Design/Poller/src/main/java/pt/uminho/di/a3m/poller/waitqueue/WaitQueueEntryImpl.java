package pt.uminho.di.a3m.poller.waitqueue;

import pt.uminho.di.a3m.list.ListNode;

public class WaitQueueEntryImpl {
    private int waitFlags;
    private WaitQueueFunc func;
    private Object priv;
    private ListNode<WaitQueueEntryImpl> node;

    protected WaitQueueEntryImpl(int waitFlags, WaitQueueFunc func, Object priv) {
        this.waitFlags = waitFlags;
        this.func = func;
        this.priv = priv;
    }

    protected int getWaitFlags() {
        return waitFlags;
    }

    protected WaitQueueFunc getFunc() {
        return func;
    }

    protected Object getPriv() {
        return priv;
    }

    protected ListNode<WaitQueueEntryImpl> getNode() {
        return node;
    }

    protected void setNode(ListNode<WaitQueueEntryImpl> node) {
        if(this.node == null)
            this.node = node;
    }

    protected void delete(){
        func = null;
        priv = null;
        node = null;
    }
}
