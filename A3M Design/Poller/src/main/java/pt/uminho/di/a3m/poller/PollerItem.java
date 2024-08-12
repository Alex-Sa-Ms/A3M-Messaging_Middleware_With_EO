package pt.uminho.di.a3m.poller;

import pt.uminho.di.a3m.list.ListNode;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;

class PollerItem {
    private final ListNode<PollerItem> readyLink; // used to link the poller item to the ready list
    private final Pollable p; // pollable being monitored through this instance
    private WaitQueueEntry wait; // wait queue entry queued in p's queue
    private final Poller poller; // poller that owns this instance
    private int events; // events bit mask
    private boolean dying; // TODO - Is this variable a requirement?

    private PollerItem(ListNode<PollerItem> readyLink, Pollable p, WaitQueueEntry wait, Poller poller, int events, boolean dying) {
        this.readyLink = readyLink;
        this.p = p;
        this.wait = wait;
        this.poller = poller;
        this.events = events;
        this.dying = dying;
    }

    static PollerItem init(Poller poller, Pollable pollable, int events){
        return new PollerItem(
                ListNode.init(),
                pollable,
                null,
                poller,
                events,
                false
        );
    }

    Pollable getPollable() {
        return p;
    }

    ListNode<PollerItem> getReadyLink() {
        return readyLink;
    }

    Pollable getP() {
        return p;
    }

    WaitQueueEntry getWait() {
        return wait;
    }

    Poller getPoller() {
        return poller;
    }

    int getEvents() {
        return events;
    }

    boolean isDying() {
        return dying;
    }

    /**
     * @return true if the item is in the ready list
     */
    boolean isReady(){
        return !ListNode.isEmpty(readyLink);
    }

    /**
     * Sets wait queue entry
     * @param wait wait queue entry
     */
    public void setWait(WaitQueueEntry wait) {
        this.wait = wait;
    }

    public void setEvents(int events) {
        this.events = events;
    }

    public void setDying(boolean dying) {
        this.dying = dying;
    }
}
