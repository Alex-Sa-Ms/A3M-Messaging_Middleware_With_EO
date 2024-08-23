package pt.uminho.di.a3m.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Class that manages sockets.
 */
class SocketMananerImpl implements SocketManager{
    // ReadWriteLock to optimize retrievals but also to protect from inconsistencies.
    private final String nodeId;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Map<String, Socket> sockets = new HashMap<>();
    private final Map<Integer, SocketProducer> producers = new ConcurrentHashMap<>();
    private final MessageDispatcher dispatcher;

    public SocketMananerImpl(String nodeId, MessageDispatcher dispatcher) {
        this.nodeId = nodeId;
        this.dispatcher = dispatcher;
    }

    private SocketProducer _getProducer(int protocolId){
        return producers.get(protocolId);
    }

    private Socket _getSocket(String tagId){
        return sockets.get(tagId);
    }

    private Socket _removeSocket(String tagId){
        return sockets.remove(tagId);
    }

    @Override
    public <T extends Socket> T getSocket(String tagId, Class<T> socketClass) {
        try{
            rwLock.readLock().lock();
            if(socketClass != null) {
                Socket socket = _getSocket(tagId);
                if(socket == null)
                    return null;
                else if(socketClass.isInstance(socket))
                    return socketClass.cast(socket);
                else throw new IllegalArgumentException("The provided class does not match the socket's class.");
            }
            else throw new IllegalArgumentException("Socket class must not be null.");
        }finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public Socket getSocket(String tagId) {
        return getSocket(tagId, Socket.class);
    }

    private <T extends Socket> T _createSocket(String tagId, int protocolId, Class<T> socketClass){
        // Check if tag and class are not null
        if(tagId == null)
            throw new IllegalArgumentException("Tag identifier must not be null.");
        if(socketClass == null)
            throw new IllegalArgumentException("Socket class must not be null.");

        // check if a socket with the tag identifier already exists
        Socket socket = sockets.get(tagId);
        if(socket != null)
            throw new IllegalArgumentException("Tag identifier has already been used.");

        // checks if there is a producer associated with the protocol identifier
        SocketProducer producer = _getProducer(protocolId);
        if(producer == null)
            throw new IllegalArgumentException("Not a valid protocol id.");

        // creates a socket
        socket = producer.get(new SocketIdentifier(nodeId, tagId));

        // checks if socket matches the requested socket class
        if(!socketClass.isInstance(socket))
            throw new IllegalArgumentException("Provided socket class does " +
                    "not match the socket associated with the protocol identifier.");

        // registers the socket and sets core components
        sockets.put(tagId, socket);
        socket.setCoreComponents(dispatcher, this);
        return socketClass.cast(socket);
    }

    @Override
    public <T extends Socket> T createSocket(String tagId, int protocolId, Class<T> socketClass) {
        try{
            rwLock.writeLock().lock();
            return _createSocket(tagId, protocolId, socketClass);
        }finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public Socket createSocket(String tagId, int protocolId) {
        return createSocket(tagId, protocolId, Socket.class);
    }

    @Override
    public <T extends Socket> T startSocket(String tagId, int protocolId, Class<T> socketClass) {
        try{
            rwLock.writeLock().lock();
            Socket socket = _createSocket(tagId, protocolId, socketClass);
            socket.start(); // starts socket making it available for operations
            return socketClass.cast(socket);
        }finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public Socket startSocket(String tagId, int protocolId) {
        return startSocket(tagId, protocolId, Socket.class);
    }

    /**
     * Starts socket's closing procedure and performs a clean-up procedure,
     * such as removing the socket from the middleware's collection, when
     * the socket is closed. When the socket is not able to close immediately,
     * the clean-up is postponed until the socket finally closes and invokes
     * this close() method again using its manager reference.
     * @param s socket to be closed and removed from the middleware.
     */
    private void _closeSocket(Socket s){
        if(s == null || s.getId() == null || s.getId().tagId() == null)
            throw new IllegalArgumentException("Something is null: socket, socket identifier or tag identifier.");
        try{
            rwLock.writeLock().lock();
            // confirms socket instances are the same,
            // and are not simply instances with the same tag id
            Socket tmp = _getSocket(s.getId().tagId());
            if(s != tmp)
                throw new IllegalArgumentException("The socket is not associated with this middleware instance.");

            // closes the socket if it is not closed or being closed
            if(s.getState() != SocketState.CLOSING && s.getState() != SocketState.CLOSED)
                s.close();

            // if socket is closed, it can be removed from the middleware
            if(s.getState() == SocketState.CLOSED)
                sockets.remove(s.getId().tagId());
        }finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Starts socket's closing procedure and performs a clean-up procedure,
     * such as removing the socket from the middleware's collection, when
     * the socket is closed. When the socket is not able to close immediately,
     * the clean-up is postponed until the socket finally closes and invokes
     * this close() method again using its manager reference.
     * @param s socket to be closed and removed from the middleware.
     */
    @Override
    public void closeSocket(Socket s) {
        try {
            rwLock.writeLock().lock();
            _closeSocket(s);
        }finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Starts socket's closing procedure and performs a clean-up procedure,
     * such as removing the socket from the middleware's collection, when
     * the socket is closed. When the socket is not able to close immediately,
     * the clean-up is postponed until the socket finally closes and invokes
     * this close() method again using its manager reference.
     * @param tagId Tag identifier of the socket that must be closed and
     *              removed from the middleware.
     */
    @Override
    public void closeSocket(String tagId) {
        if(tagId == null)
            throw new IllegalArgumentException("Tag identifier must not be null.");
        try{
            rwLock.writeLock().lock();
            Socket s = getSocket(tagId);
            _closeSocket(s);
        }finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void registerProducer(SocketProducer producer) {
        if(producer == null)
            throw new IllegalArgumentException("Producer is null.");

        Socket s = producer.get(new SocketIdentifier(nodeId, "testTagId"));
        if(s == null)
            throw new IllegalArgumentException("Producer supplies null socket.");

        // assert supplied sockets have CREATED as their state
        if(s.getState() != SocketState.CREATED)
            throw new IllegalArgumentException("Supplied socket must have its state has the initial state (CREATED).");

        // check if setting the core components is allowed
        s.setCoreComponents(null, null);

        // get the protocol identifier and register the producer
        // if such protocol identifier does not exist
        int protocolId = s.getProtocol().id();
        if(producers.putIfAbsent(protocolId, producer) != null)
            throw new IllegalStateException("A producer for the given protocol identifier is already registered.");
    }

    @Override
    public boolean removeProducer(int protocolId) {
        return producers.remove(protocolId) != null;
    }

    @Override
    public boolean existsProducer(int protocolId) {
        return producers.containsKey(protocolId);
    }
}
