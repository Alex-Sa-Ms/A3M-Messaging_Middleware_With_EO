package pt.uminho.di.a3m.sockets.request_reply;

import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.sockets.auxiliary.LinkSocketWatched;

import java.util.LinkedList;
import java.util.Queue;

public class BufferedLinkSocket extends LinkSocketWatched {
    private final Queue<Payload> outQ = new LinkedList<>();

    @Override
    public synchronized boolean trySend(Payload payload) throws InterruptedException {
        assert payload != null && payload.getType() == MsgType.DATA;

        // if there are payloads queued, then add to the payload to the back of the queue
        if(!outQ.isEmpty()) {
            outQ.add(payload);
            return true;
        }

        // try sending, if it is not possible, then queue the message
        boolean sent = super.trySend(payload);
        if(!sent) outQ.add(payload);
        return true;
    }

    public synchronized void trySendBufferedPayloads(){
        boolean sent = true;
        Payload payload;
        while ((payload = outQ.peek()) != null && sent){
            try { sent = super.trySend(payload); }
            catch (InterruptedException ie) { sent = false; }
            if(sent) outQ.remove();
        }
    }
}
