package pt.uminho.di.a3m.sockets.auxiliary;

import pt.uminho.di.a3m.core.LinkSocket;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;

/**
 * Link socket with watcher. Allows setting the watcher entry.
 */
public class LinkSocketWatched extends LinkSocket {
    private WaitQueueEntry watcherWaitEntry = null;

    public WaitQueueEntry getWatcherWaitEntry() {
        return watcherWaitEntry;
    }

    public void setWatcherWaitEntry(WaitQueueEntry watcherWaitEntry) {
        this.watcherWaitEntry = watcherWaitEntry;
    }

    public boolean trySend(Payload payload) throws InterruptedException {
        return send(payload, 0L);
    }
}
