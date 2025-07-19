package pt.uminho.di.a3m.core;

import com.google.protobuf.ByteString;
import haslab.eo.EOMiddleware;
import haslab.eo.msgs.ClientMsg;
import pt.uminho.di.a3m.core.messaging.*;
import pt.uminho.di.a3m.core.messaging.payloads.SerializableMap;

import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * This class should be closed before the exon instance,
 * but after closing all sockets.
 */
public class MessageManagementSystem implements MessageDispatcher{
    private final EOMiddleware eom;
    private SocketManager sm;
    private final MessageProcessor processor;

    private volatile short state = CREATED;
    static final short CREATED = 1;
    static final short RUNNING = 0;
    static final short CLOSING = -1;
    static final short CLOSED = -2;

    public MessageManagementSystem(EOMiddleware eom, SocketManager sm) {
        this.eom = eom;
        this.sm = sm;
        this.processor = new MessageProcessor("MessageProcessor-" + eom.getIdentifier());
    }

    public MessageManagementSystem(EOMiddleware eom) {
        this(eom, null);
    }

    public void setSocketManager(SocketManager sm) {
        if(this.sm == null) this.sm = sm;
    }

    short getState() {
        return state;
    }

    public void start(){
        state = RUNNING;
        processor.start();
    }

    public boolean isClosed(){
        return state == CLOSED;
    }

    public void close(){
        if(state == RUNNING) {
            state = CLOSING;
            processor.interrupt();
        }
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
            processor.interrupt();
            if(timeout == null)
                processor.join();
            else if(timeout > 0L)
                processor.join(timeout);
        }
    }

    public void closeAndWait() throws InterruptedException {
        if (state == RUNNING) {
            state = CLOSING;
            processor.interrupt();
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
                                    .setType(toByteString(msg.getType()));
        if(msg.getPayload() != null)
            builder.setPayload(toByteString(msg.getPayload()));
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
            // TODO - maybe handling the InterruptedException must be passed above? Should it attempt to send
            //  again and then throw the exception?
            Logger.getAnonymousLogger().severe(Arrays.toString(e.getStackTrace()));
            if(Thread.currentThread() != processor)
                throw new RuntimeException(e);
        }
    }

    /**
     * Schedules the dispatch of a message.
     * @param msg message to be dispatched
     * @param dispatchTime time at which the dispatch should be executed.
     *                     The actual dispatch time may be a bit delayed
     *                     depending on how busy the middleware is.
     * @return atomic reference that allows cancelling the delivery of the message.
     */
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

    private Msg createSocketNotFoundMsg(SocketIdentifier src, SocketIdentifier dest, int destClockId){
        SerializableMap map = LinkManager.createErrorMsgPayload(ErrorType.SOCK_NFOUND, destClockId);
        return new SocketMsg(src, dest, MsgType.ERROR, Integer.MIN_VALUE, map.serialize());
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
        long timeout = Long.MAX_VALUE;
        // lock to synchronize insertions
        // final Lock lock = new ReentrantLock();

        public MessageProcessor(String name) {
            super(name);
        }

        private void calculateAndSetTimeout(Event event){
            timeout = event == null ? Long.MAX_VALUE
                    : event.getEventTime() - System.currentTimeMillis();
        }

        @Override
        public void run() {
            ClientMsg cMsg = null;
            while (state == RUNNING) {
                // receive messages while not interrupted and
                // the operation does not time out.
                do {
                    try {
                        cMsg = eom.receive(timeout);
                        // break out of the receiving loop if timed out
                        if (cMsg != null) {
                            // If a message was received, parse it and forward it
                            // to the appropriate socket (if it exists)
                            try {
                                CoreMessages.Message msg = CoreMessages.Message.parseFrom(cMsg.msg);
                                // if it is a socket message
                                if (!msg.getSrcTagId().isEmpty() && !msg.getDestTagId().isEmpty()) {
                                    // if message does not have type and/or the clock identifier,
                                    // then it is malformed. Just ignore it.
                                    if (msg.getType().isEmpty() || msg.getType().isEmpty()) continue;
                                    // get identifier of the destination socket and check if it exists
                                    Socket socket = sm.getSocket(msg.getDestTagId());
                                    // if socket does not exist, then send error "socket not found"
                                    if (socket == null) {
                                        // TODO - maybe only send socket not found for link messages?
                                        dispatch(createSocketNotFoundMsg(
                                                new SocketIdentifier(eom.getIdentifier(), msg.getDestTagId()),
                                                new SocketIdentifier(cMsg.nodeId, msg.getSrcTagId()),
                                                msg.getClockId()));
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
                            } catch (Exception e) {
                                // ignored malformed messages and behavior
                                e.printStackTrace();
                            }
                        }
                    } catch (InterruptedException ignored) {
                        // when the loop condition is ran, the thread will
                        // detect that it was interrupted.
                        cMsg = null; // to exit loop
                    }
                } while (!Thread.interrupted() && cMsg != null);

                // execute events that have reached their execution time
                Event event;
                while ((event = eventsQ.peek()) != null
                        && System.currentTimeMillis() >= event.getEventTime()) {
                    // remove event
                    eventsQ.poll();
                    event.execute(MessageManagementSystem.this);
                }
                // define new timeout
                calculateAndSetTimeout(event);
            }
            // sets state to closed
            state = CLOSED;
        }

        public void addEvent(Event event) {
            eventsQ.add(event);
            calculateAndSetTimeout(eventsQ.peek());
        }

        /*
        // Implementation to be used when scheduling of events are to be allowed
        // for client threads. The handling of the events must also be protected
        // by the lock.
        public void addEvent(Event event) {
            try {
                lock.lock();
                // if the events queue is empty or the new event happens earlier
                // than the first element in the queue, then interrupt the thread,
                // if waiting to receive a message, is woken up with InterruptedException,
                // and is able to determine when the next event needs to be executed.
                Event fst = eventsQ.peek();
                if(Thread.currentThread() != this
                        && (fst == null || event.getEventTime() < fst.getEventTime()))
                    this.interrupt();
                eventsQ.add(event);
            } finally {
                lock.unlock();
            }
        }
         */
    }
}
