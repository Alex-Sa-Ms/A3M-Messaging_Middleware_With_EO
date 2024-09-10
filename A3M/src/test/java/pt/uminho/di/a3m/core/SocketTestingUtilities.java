package pt.uminho.di.a3m.core;

import com.google.protobuf.InvalidProtocolBufferException;
import pt.uminho.di.a3m.auxiliary.Debugging;
import pt.uminho.di.a3m.core.messaging.Msg;
import pt.uminho.di.a3m.core.messaging.SocketMsg;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SocketTestingUtilities {
    /**
     * Dispatches messages with random delay if the random delay flag is set.
     */
    public static class DirectMessageDispatcher implements MessageDispatcher {
        private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
        private final Map<SocketIdentifier, Socket> sockets = new ConcurrentHashMap<>(); // link managers
        private boolean randomDelay = false;
        private final Random random = new Random(2024);
        private long minDelay = 0L; // min delay to dispatch a message in seconds
        private long maxDelay = 20L; // max delay (exclusive) to dispatch a message in seconds

        public DirectMessageDispatcher() {
        }

        public DirectMessageDispatcher(Socket s) {
            registerSocket(s);
        }

        public void registerSocket(Socket s) {
            if (s != null)
                sockets.put(s.getId(), s);
        }

        public void setRandomDelay(boolean randomDelay) {
            this.randomDelay = randomDelay;
        }

        public void setDelays(long minDelay, long maxDelay){
            this.minDelay = minDelay;
            this.maxDelay = maxDelay;
        }

        private void _dispatch(SocketMsg msg) {
            try {
                if (msg != null) {
                    Socket socket = sockets.get(msg.getDestId());
                    if (socket != null) {
                        socket.onIncomingMessage(msg);
                        //try {
                        //    Debugging.printlnOrdered(LinkManager.linkRelatedMsgToString(msg));
                        //} catch (InvalidProtocolBufferException ignored) {}
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void dispatch(Msg msg) {
            if (randomDelay) {
                long delay = random.nextLong(minDelay, maxDelay);
                scheduler.schedule(() -> _dispatch((SocketMsg) msg), delay, TimeUnit.MILLISECONDS);
            } else scheduler.execute(() -> _dispatch((SocketMsg) msg));
        }

        @Override
        public AtomicReference<Msg> scheduleDispatch(Msg msg, long dispatchTime) {
            AtomicReference<Msg> ref = new AtomicReference<>(msg);
            long delay = Math.max(0L, dispatchTime - System.currentTimeMillis());
            scheduler.schedule(() -> {
                SocketMsg m = (SocketMsg) ref.getAndSet(null);
                if (m != null) {
                    this.dispatch(m);
                }
            }, delay, TimeUnit.MILLISECONDS);
            return ref;
        }
    }

    public static SocketManager createSocketManager(String nodeId, MessageDispatcher messageDispatcher){
        return new SocketMananerImpl(nodeId, messageDispatcher);
    }
}
