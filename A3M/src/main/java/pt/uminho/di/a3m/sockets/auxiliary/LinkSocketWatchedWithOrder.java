package pt.uminho.di.a3m.sockets.auxiliary;

import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.payloads.BytePayload;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Link socket with watcher and with send methods that add
 * an order attribute to data payloads.
 */
public class LinkSocketWatchedWithOrder extends LinkSocketWatched {
    int next = 0;
    Lock orderLock = new ReentrantLock();

    protected BytePayload createDataPayloadWithOrder(byte[] payload) {
        byte[] payloadWithOrder =
                ByteBuffer.allocate(payload.length + 4) // allocate space for payload plus order number
                        .putInt(next) // put order number
                        .put(payload) // put payload
                        .array(); // convert to byte array
        return new BytePayload(MsgType.DATA, payloadWithOrder);
    }

    public boolean trySendDataWithOrder(byte[] payload) throws InterruptedException {
        boolean locked = orderLock.tryLock();
        if (locked) {
            try {
                boolean sent = super.send(createDataPayloadWithOrder(payload), 0L);
                if (sent) next++;
                return sent;
            } finally {
                orderLock.unlock();
            }
        } else return false;
    }

    @Override
    public boolean trySend(Payload payload) throws InterruptedException {
        if (payload == null)
            throw new IllegalArgumentException("Payload is null.");
        if (payload.getType() == MsgType.DATA)
            return trySendDataWithOrder(payload.getPayload());
        else
            return super.trySend(payload);
    }

    /**
     * Example of how to send messages with deadlines or timeouts,
     * when synchronization between sending is required. If a thread
     * cannot execute the send operation due to the lack of credits,
     * then all threads that attempt to do so, will also not be successful.
     * This is relevant, as maintaining consistency between order numbers is mandatory,
     * i.e. we don't want to acquire an order number, let another thread acquire the order
     * number that follows, and potentially end up with only the second thread to send a message,
     * leaving a gap on the order numbers which will stall the progress.
     * So, the solution is to protect the sending of the message using a reentrant lock, and waiting
     * for lock acquisition using tryLock. If acquiring the lock is not possible during the provided
     * timeout or deadline, then the reason behind this incapability, most likely, is that the link
     * is being throttled by the flow control.
     *
     * @param payload  message content to send
     * @param deadline .
     * @return true if the message was sent. false, if the operation timed out.
     * @throws InterruptedException if the thread was interrupted while waiting.
     */
    public boolean sendDataWithOrder(byte[] payload, Long deadline) throws InterruptedException {
        boolean locked = false;
        try {
            if (deadline == null) {
                orderLock.lock();
                locked = true;
            } else
                locked = orderLock.tryLock(Timeout.calculateTimeout(deadline), TimeUnit.MILLISECONDS);

            if (locked) {
                boolean sent = super.send(createDataPayloadWithOrder(payload), deadline);
                if (sent) next++;
                return sent;
            } else return false;
        } finally {
            if (locked) orderLock.unlock();
        }
    }

    @Override
    public boolean send(Payload payload, Long deadline) throws InterruptedException {
        if (payload == null)
            throw new IllegalArgumentException("Payload is null.");
        if (payload.getType() == MsgType.DATA)
            return sendDataWithOrder(payload.getPayload(), deadline);
        else
            return super.send(payload, deadline);
    }
}
