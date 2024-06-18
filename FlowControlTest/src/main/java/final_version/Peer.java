package final_version;

import final_version.msgs.Msg;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

class Peer {
    final BlockingQueue<Msg> inqueue = new LinkedBlockingQueue<>();

    private void newIncomingMsg(Msg msg){
        inqueue.add(msg);
    }

    protected void send(Peer p, Msg msg){
        p.newIncomingMsg(msg);
    }

    public boolean isQueueEmpty(){
        return inqueue.isEmpty();
    }

    protected Msg receive(){
        try {
            return inqueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
