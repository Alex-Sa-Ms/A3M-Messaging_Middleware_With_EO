package pt.uminho.di.a3m.poller;

import pt.uminho.di.a3m.waitqueue.WaitQueueEntry;

@FunctionalInterface
public interface PollQueueingFunc {
    void apply(Pollable p, WaitQueueEntry wait, PollTable pt);
}
