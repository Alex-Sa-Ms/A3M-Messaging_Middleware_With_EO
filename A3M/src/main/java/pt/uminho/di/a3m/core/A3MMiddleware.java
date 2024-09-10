package pt.uminho.di.a3m.core;

import haslab.eo.EOMiddleware;
import haslab.eo.TransportAddress;
import haslab.eo.associations.DiscoveryManager;
import haslab.eo.associations.DiscoveryService;
import pt.uminho.di.a3m.sockets.simple_socket.SimpleSocket;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class A3MMiddleware implements DiscoveryManager, SocketManagerPublic {
    private final SocketMananerImpl socketManager;
    private final EOMiddleware eom;
    private final MessageManagementSystem mms;
    private int state = CREATED;
    private Lock lock = new ReentrantLock();

    // ****** Middleware possible states ****** //
    public static final int CREATED = 0;
    public static final int RUNNING = 1;
    public static final int CLOSING = 2;
    public static final int CLOSED = 3;

    /**
     * Creates a middleware instance.
     * @param nodeId identifier of the node
     * @param address IP address for the datagram socket. May be null for a wildcard address.
     * @param port port number for a datagram socket
     * @param N Exon middleware related value. This value determines
     *          the maximum number of slots to be requested to a node at a time.
     * @param socketProducers list of socket producers. If the provided list is null or empty,
     *                        the default collection of producers is used.
     * @throws SocketException if there is a problem creating the underlying datagram socket
     */
    public A3MMiddleware(String nodeId, String address, int port, int N, List<SocketProducer> socketProducers) throws SocketException, UnknownHostException {
        this.eom = EOMiddleware.start(nodeId, address, port, N);
        this.socketManager = new SocketMananerImpl(nodeId);
        this.mms = new MessageManagementSystem(eom, this.socketManager);
        this.socketManager.setDispatcher(this.mms);
        if(socketProducers == null || socketProducers.isEmpty())
            socketProducers = defaultProducers;
        registerSocketProducers(socketProducers);
    }

    /**
     * Creates a middleware instance.
     * @param nodeId identifier of the node
     * @param address IP address for the datagram socket. May be null for a wildcard address.
     * @param port port number for a datagram socket
     * @param N Exon middleware related value. This value determines
     *          the maximum number of slots to be requested to a node at a time.
     * @throws SocketException if there is a problem creating the underlying datagram socket
     */
    public A3MMiddleware(String nodeId, String address, int port, int N) throws SocketException, UnknownHostException {
        this(nodeId, address, port, N, null);
    }

    /**
     * Creates a middleware instance.
     * @param nodeId identifier of the node
     * @param port port number for a datagram socket
     * @param N Exon middleware related value. This value determines
     *          the maximum number of slots to be requested to a node at a time.
     * @throws SocketException if there is a problem creating the underlying datagram socket
     */
    public A3MMiddleware(String nodeId, int port, int N) throws SocketException, UnknownHostException {
        this(nodeId, null, port, 100);
    }

    /**
     * Creates a middleware instance.
     * @param nodeId identifier of the node
     * @param port port number for a datagram socket
     * @throws SocketException if there is a problem creating the underlying datagram socket
     */
    public A3MMiddleware(String nodeId, int port) throws SocketException, UnknownHostException {
        this(nodeId, port, 100);
    }

    public void start(){
        try {
            lock.lock();
            if(state == CREATED){
                state = RUNNING;
                mms.start();
            }
            else throw new IllegalStateException("Middleware instance has already been started before.");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String getNodeIdentifier(TransportAddress taddr) {
        return eom.getNodeIdentifier(taddr);
    }

    @Override
    public TransportAddress getNodeTransportAddress(String nodeId) {
        return eom.getNodeTransportAddress(nodeId);
    }

    @Override
    public void registerNode(String nodeId, TransportAddress taddr) {
        eom.registerNode(nodeId, taddr);
    }

    @Override
    public void unregisterNode(String nodeId) {
        eom.unregisterNode(nodeId);
    }

    @Override
    public void setDiscoveryService(DiscoveryService discoveryService) {
        eom.setDiscoveryService(discoveryService);
    }

    @Override
    public Socket getSocket(String tagId) {
        try {
            lock.lock();
            if(state == RUNNING || state == CLOSING)
                return socketManager.getSocket(tagId);
        } finally {
            lock.unlock();
        }
        return null;
    }

    @Override
    public <T extends Socket> T getSocket(String tagId, Class<T> socketClass) {
        try {
            lock.lock();
            if(state == RUNNING || state == CLOSING)
                return socketManager.getSocket(tagId, socketClass);
        } finally {
            lock.unlock();
        }
        return null;
    }

    @Override
    public Socket createSocket(String tagId, int protocolId) {
        try {
            lock.lock();
            if(state == RUNNING)
                return socketManager.createSocket(tagId, protocolId);
        } finally {
            lock.unlock();
        }
        return null;
    }

    @Override
    public <T extends Socket> T createSocket(String tagId, int protocolId, Class<T> socketClass) {
        try {
            lock.lock();
            if(state == RUNNING)
                return socketManager.createSocket(tagId, protocolId, socketClass);
        } finally {
            lock.unlock();
        }
        return null;
    }

    @Override
    public Socket startSocket(String tagId, int protocolId) {
        try {
            lock.lock();
            if(state == RUNNING)
                return socketManager.startSocket(tagId, protocolId);
        } finally {
            lock.unlock();
        }
        return null;
    }

    @Override
    public <T extends Socket> T startSocket(String tagId, int protocolId, Class<T> socketClass) {
        try {
            lock.lock();
            if(state == RUNNING)
                return socketManager.startSocket(tagId, protocolId, socketClass);
        } finally {
            lock.unlock();
        }
        return null;
    }

    @Override
    public void closeSocket(Socket s) {
        if (socketManager != null)
            socketManager.closeSocket(s);
    }

    @Override
    public void closeSocket(String tagId) {
        if (socketManager != null)
            socketManager.closeSocket(tagId);
    }

    private static final List<SocketProducer> defaultProducers =
            List.of(SimpleSocket::createSocket);

    public static List<SocketProducer> getDefaultProducers() {
        return new ArrayList<>(defaultProducers);
    }

    private void registerSocketProducers(List<SocketProducer> producers){
        if(producers != null)
            for (SocketProducer producer : producers)
                socketManager.registerProducer(producer);
    }
}
