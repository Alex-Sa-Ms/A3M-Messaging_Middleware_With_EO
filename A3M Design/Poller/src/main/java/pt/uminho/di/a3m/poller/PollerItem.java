package pt.uminho.di.a3m.poller;

import pt.uminho.di.a3m.list.ListNode;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;

import java.util.concurrent.atomic.AtomicReference;

class PollerItem {
    // Static instance to mark the overflow list as not active,
    // or to inform that a poller item is not in the overflow list.
    static final PollerItem NOT_ACTIVE = new PollerItem(null, null, null, 0);
    private final ListNode<PollerItem> readyLink; // used to link the poller item to the ready list
    private PollerItem overflowLink = PollerItem.NOT_ACTIVE;// used to link the poller item to the overflow list
    private final Pollable p; // pollable being monitored through this instance
    private WaitQueueEntry wait; // wait queue entry queued in p's queue
    private final Poller poller; // poller that owns this instance
    private int events; // events bit mask

    private PollerItem(Pollable p, WaitQueueEntry wait, Poller poller, int events) {
        this.readyLink = ListNode.create(this);
        this.p = p;
        this.wait = wait;
        this.poller = poller;
        this.events = events;
    }

    static PollerItem init(Poller poller, Pollable pollable, int events){
        return new PollerItem(
                pollable,
                null,
                poller,
                events
        );
    }

    Pollable getPollable() {
        return p;
    }

    ListNode<PollerItem> getReadyLink() {
        return readyLink;
    }

    public PollerItem getOverflowLink() {
        return overflowLink;
    }

    public void setOverflowLink(PollerItem overflowLink) {
        this.overflowLink = overflowLink;
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
}
