package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.core.messaging.Msg;
import pt.uminho.di.a3m.core.messaging.SocketMsg;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.List;
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

        /**
         * Actives random delay and sets new delays.
         * @param minDelay minimum delay
         * @param maxDelay maximum delay (exclusive)
         */
        public void setDelays(long minDelay, long maxDelay){
            this.randomDelay = true;
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

    private static int getAvailablePort(){
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static AbstractMap.SimpleEntry<Integer, A3MMiddleware> createAndStartMiddlewareInstance(List<SocketProducer> producerList) throws SocketException, UnknownHostException {
        for (int i = 0; i < 100; i++) {
            try {
                int port = getAvailablePort();
                A3MMiddleware m = new A3MMiddleware("Node", null, port, null, producerList);
                m.start();
                return new AbstractMap.SimpleEntry<>(port, m);
            } catch (BindException ignored){
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        throw new BindException("Could not find an available adress.");
    }

    public static String decodeByteArrayToString(byte[] arrMsg){
        if(arrMsg == null) return null;
        else return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(arrMsg)).toString();
    }
}
