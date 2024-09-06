package pt.uminho.di.a3m.core;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import haslab.eo.EOMiddleware;
import haslab.eo.msgs.ClientMsg;
import pt.uminho.di.a3m.core.messaging.*;
import pt.uminho.di.a3m.core.messaging.payloads.ErrorPayload;

import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

// This class should be closed before the exon instance,
// but after closing all sockets.
public class MessageManagementSystem implements MessageDispatcher{
    final EOMiddleware eom;
    final SocketManager sm;
    final MessageProcessor processor;

    private volatile short state = CREATED;
    private static final short CREATED = 1;
    private static final short RUNNING = 0;
    private static final short CLOSING = -1;
    private static final short CLOSED = -2;

    public MessageManagementSystem(EOMiddleware eom, SocketManager sm) {
        this.eom = eom;
        this.sm = sm;
        this.processor = new MessageProcessor();
    }

    public void start(){
        state = RUNNING;
        processor.start();
    }

    public boolean isClosed(){
        return state == CLOSED;
    }

    public void close(){
        if(state == RUNNING)
            state = CLOSING;
    }

    /**
     * If the MMS is running, initiates the closing process
     * and waits for the closure by waiting for the responsible thread.
     * @param timeout Time to wait in milliseconds. Follows the Thread.join() semantics.
     * @see Thread#join()
     */
    public void closeAndWait(Long timeout) throws InterruptedException {
        if (state == RUNNING) {
            state = CLOSING;
            if(timeout == null)
                processor.join();
            else
                processor.join(timeout);
        }
    }

    public void closeAndWait() throws InterruptedException {
        if (state == RUNNING) {
            state = CLOSING;
            if (processor != null)
                processor.join();
        }
    }

    private ByteString toByteString(byte[] array){
        return ByteString.copyFrom(array);
    }

    private ByteString toByteString(byte b){
        return toByteString(new byte[]{b});
    }

    @Override
    public void dispatch(Msg msg) {
        // do not dispatch if the state differs from RUNNING
        if(state != RUNNING) return;

        if(msg == null) return;
        CoreMessages.Message.Builder builder =
                CoreMessages.Message.newBuilder()
                                    .setType(toByteString(msg.getType()))
                                    .setPayload(toByteString(msg.getPayload()));
        if(msg instanceof SocketMsg sMsg){
            builder.setClockId(sMsg.getClockId())
                   .setSrcTagId(sMsg.getSrcTagId())
                   .setDestTagId(sMsg.getDestTagId());
        }
        try {
            boolean success = eom.send(msg.getDestNodeId(), builder.build().toByteArray(), 0L);
            // The flow control is assumed to be "disabled" on the Exon middleware,
            // So the message should be queued immediately.
            assert success;
        } catch (Exception e) {
            Logger.getAnonymousLogger().severe(Arrays.toString(e.getStackTrace()));
        }
    }

    @Override
    public AtomicReference<Msg> scheduleDispatch(Msg msg, long dispatchTime) {
        // do not dispatch if the state differs from RUNNING
        if(state != RUNNING) return new AtomicReference<>(null);

        AtomicReference<Msg> msgRef;
        // if dispatch time has already passed, then dispatch message immediately
        if (System.currentTimeMillis() > dispatchTime){
            dispatch(msg);
            msgRef = new AtomicReference<>(null);
        }
        // else, schedule a dispatch event
        else {
            msgRef = new AtomicReference<>(msg);
            processor.addEvent(new DispatchEvent(msgRef, dispatchTime));
        }
        return msgRef;
    }

    private Msg createSocketNotFoundMsg(SocketIdentifier src, SocketIdentifier dest){
        Payload errorPayload = new ErrorPayload(ErrorType.SOCK_NFOUND, null);
        return new SocketMsg(src, dest, Integer.MIN_VALUE, errorPayload);
    }

    interface Event extends Comparable<Event> {
        /**
         * Execute event logic.
         * @param mms owner of the event
         */
        void execute(MessageManagementSystem mms);

        /** 
         * Gets the time at which the event should happen.
         * @return time of the event. Corresponds to the number of milliseconds
         * since the Unix epoch (January 1, 1970, 00:00:00 UTC), as returned by `System.currentTimeMillis()`.
         */
        long getEventTime();

        @Override
        default int compareTo(Event o){
            return Long.compare(this.getEventTime(),o.getEventTime());
        }
    }

    private record DispatchEvent(AtomicReference<Msg> msg, long eventTime) implements Event {
        @Override
            public void execute(MessageManagementSystem mms) {
                Msg m = msg.getAndSet(null);
                mms.dispatch(m);
            }

            @Override
            public long getEventTime() {
                return eventTime;
            }
        }

    class MessageProcessor extends Thread {
        // events queue
        final Queue<Event> eventsQ = new PriorityQueue<>();
        // lock to synchronize insertions
        final Lock lock = new ReentrantLock();

        @Override
        public void run() {
            long timeout = Long.MAX_VALUE;
            ClientMsg cMsg = null;
            while (state == RUNNING) {
                // receive messages while not interrupted and
                // the operation does not time out.
                while(!Thread.interrupted()){
                    try {
                        cMsg = eom.receive(timeout);
                        // break out of the receiving loop if timed out
                        if(cMsg == null) {
                            // clear any interrupt status
                            Thread.interrupted();
                            break;
                        }
                        // If a message was received, parse it and forward it
                        // to the appropriate socket (if it exists)
                        try {
                            CoreMessages.Message msg = CoreMessages.Message.parseFrom(cMsg.msg);
                            // if it is a socket message
                            if(!msg.getSrcTagId().isEmpty() && !msg.getDestTagId().isEmpty()){
                                // if message does not have type and/or the clock identifier,
                                // then it is malformed. Just ignore it.
                                if(msg.getType().isEmpty() || msg.getType().isEmpty()) continue;
                                // get identifier of the destination socket and check if it exists
                                Socket socket = sm.getSocket(msg.getDestTagId());
                                // if socket does not exist, then send error "socket not found"
                                if(socket == null) {
                                    dispatch(createSocketNotFoundMsg(
                                            new SocketIdentifier(eom.getIdentifier(), msg.getDestTagId()),
                                            new SocketIdentifier(cMsg.nodeId, msg.getSrcTagId())));
                                }
                                // else form the message and forward it to the socket
                                else {
                                    SocketMsg socketMsg = new SocketMsg(
                                            new SocketIdentifier(cMsg.nodeId, msg.getSrcTagId()),
                                            new SocketIdentifier(eom.getIdentifier(), msg.getDestTagId()),
                                            msg.getType().byteAt(0),
                                            msg.getClockId(),
                                            msg.getPayload().toByteArray());
                                    socket.onIncomingMessage(socketMsg);
                                }
                            }
                        } catch (InvalidProtocolBufferException e) {
                            // ignored malformed messages
                        }
                    } catch (InterruptedException ignored) {
                        // when the loop condition is ran, the thread will
                        // detect that it was interrupted.
                    }
                }

                // execute events that have reached their execution time
                try {
                    lock.lock();
                    Event event;
                    while ((event = eventsQ.peek()) != null
                            && System.currentTimeMillis() > event.getEventTime()){
                        // remove event
                        eventsQ.poll();
                        event.execute(MessageManagementSystem.this);
                    }
                    // define new timeout
                    timeout = event == null ? Long.MAX_VALUE
                            : event.getEventTime() - System.currentTimeMillis();
                } finally {
                    lock.unlock();
                }
            }
            // sets state to closed
            state = CLOSED;
        }

        public void addEvent(Event event) {
            try {
                lock.lock();
                // if the events queue is empty or the new event happens earlier
                // than the first element in the queue, then interrupt the thread,
                // if waiting to receive a message, is woken up with InterruptedException,
                // and is able to determine when the next event needs to be executed.
                Event fst = eventsQ.peek();
                if(fst == null || event.getEventTime() < fst.getEventTime())
                    this.interrupt();
                eventsQ.add(event);
            } finally {
                lock.unlock();
            }
            eventsQ.add(event);
        }
    }
}
