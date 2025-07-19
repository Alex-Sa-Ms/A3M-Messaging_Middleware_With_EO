package pt.uminho.di.a3m.sockets.auxiliary;

import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.core.messaging.payloads.BytePayload;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

//  If FIFO order is required involving both data and control messages,
//  the data messages would need another order value used only
//  between data messages so that it would not disturb the order
//  of the incoming queue which assumes control messages to not be present
//  when the socket is in cooked mode.

/**
 * Link socket with watcher and with send methods that add
 * an order attribute to payloads. An order counter is
 * used for data messages and another for control messages, so
 * that control messages are not delayed by flow control.
 */
public class LinkSocketWatchedWithOrder extends LinkSocketWatched {
    private final AtomicInteger controlNext = new AtomicInteger(0);
    private final Queue<SocketMsgWithOrder> controlQueue = new OrderedQueue<>(SocketMsgWithOrder::getOrder);
    private int dataNext = 0;
    private final Lock orderLock = new ReentrantLock();

    protected BytePayload createPayloadWithOrder(int order, byte type, byte[] payload) {
        byte[] payloadWithOrder = SocketMsgWithOrder.createPayloadWithOrder(order, payload);
        return new BytePayload(type, payloadWithOrder);
    }

    public boolean trySendDataWithOrder(byte[] payload) throws InterruptedException {
        boolean locked = orderLock.tryLock();
        if (locked) {
            try {
                boolean sent = super.send(createPayloadWithOrder(dataNext, MsgType.DATA, payload), 0L);
                if (sent) dataNext++;
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
            return sendControlWithOrder(payload, 0L);
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
                boolean sent = super.send(createPayloadWithOrder(dataNext, MsgType.DATA, payload), deadline);
                if (sent) dataNext++;
                return sent;
            } else return false;
        } finally {
            if (locked) orderLock.unlock();
        }
    }

    public boolean sendControlWithOrder(Payload payload, Long deadline) throws InterruptedException {
        assert payload != null;
        payload = createPayloadWithOrder(
                controlNext.getAndIncrement(),
                payload.getType(),
                payload.getPayload());
        return super.send(payload, deadline);
    }

    @Override
    public boolean send(Payload payload, Long deadline) throws InterruptedException {
        if (payload == null)
            throw new IllegalArgumentException("Payload is null.");
        if (payload.getType() == MsgType.DATA)
            return sendDataWithOrder(payload.getPayload(), deadline);
        else
            return sendControlWithOrder(payload, deadline);
    }

    /**
     * Parses a socket message into a socket message with order, queues
     * it in a control messages queue, and then returns whether a control
     * message queue can be handled or not.
     * @param msg control message to be queued
     * @return true if a control message can be polled for processing.
     */
    public synchronized boolean queueControlMessage(SocketMsg msg){
        assert msg != null && Objects.equals(msg.getSrcId(),getPeerId());
        // parse message and queue it
        SocketMsgWithOrder msgWithOrder = SocketMsgWithOrder.parseFrom(msg);
        controlQueue.add(msgWithOrder);
        // return whether a control message can be handled or not
        return controlQueue.peek() != null;
    }

    /** @return true if the control queue has messages, the true value does not
     * mean the messages can be polled. False, if the control queue is empty. */
    public synchronized boolean hasControlMessages(){
        return !controlQueue.isEmpty();
    }

    /** @return number of control messages in the queue. This value does not correspond
     * to the number of messages that can be polled. */
    public synchronized int countControlMessages(){
        return controlQueue.size();
    }

    /** @return true if a control message can be polled. */
    public synchronized boolean canPollControlMessage(){
        return controlQueue.peek() != null;
    }

    /** @return a socket message if the message at the head of the control messages
     * queue is the next message that should be handled. null, if the order number
     * of the message at the head does not correspond to the order number of the
     * message that should be handled. */
    public synchronized SocketMsg pollControlMessage(){
        return controlQueue.poll();
    }
}
