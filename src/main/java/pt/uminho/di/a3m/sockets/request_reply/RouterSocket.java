package pt.uminho.di.a3m.sockets.request_reply;

import pt.uminho.di.a3m.auxiliary.Timeout;
import pt.uminho.di.a3m.core.A3MMiddleware;
import pt.uminho.di.a3m.core.LinkSocket;
import pt.uminho.di.a3m.core.Protocol;
import pt.uminho.di.a3m.core.SocketIdentifier;
import pt.uminho.di.a3m.core.exceptions.LinkClosedException;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.SocketMsg;
import pt.uminho.di.a3m.core.messaging.payloads.BytePayload;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.configurable_socket.ConfigurableSocket;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RouterSocket extends ConfigurableSocket {
    public static final Protocol protocol = SocketsTable.ROUTER_PROTOCOL;
    public static final Set<Protocol> compatProtocols = Set.of(SocketsTable.REQ_PROTOCOL, SocketsTable.DEALER_PROTOCOL, SocketsTable.ROUTER_PROTOCOL);
    // mapping of link sockets' clock identifiers to the associated link socket
    public Map<Integer, LinkSocket> linkSockets = new ConcurrentHashMap<>();

    public RouterSocket(SocketIdentifier sid) {
        super(sid, true, false, false);
    }

    @Override
    public Protocol getProtocol() {
        return protocol;
    }

    @Override
    public Set<Protocol> getCompatibleProtocols() {
        return compatProtocols;
    }

    @Override
    protected void customOnLinkEstablished(LinkSocket linkSocket) {
        linkSockets.put(linkSocket.getClockId(), linkSocket);
        super.customOnLinkEstablished(linkSocket);
    }

    @Override
    protected void customOnLinkClosed(LinkSocket linkSocket) {
        linkSockets.remove(linkSocket.getClockId());
        super.customOnLinkClosed(linkSocket);
    }

    @Override
    public void unlink(SocketIdentifier peerId) {
        LinkSocket linkSocket = getLinkSocket(peerId);
        if(linkSocket != null){
            linkSockets.remove(linkSocket.getClockId());
            super.unlink(peerId);
        }
    }

    /**
     * Parses or creates a request-reply message from the incoming message,
     * then adds the routing identifier of the source of the message to the
     * list of routing identifiers of the message.
     * @param msg incoming message to be handled
     * @return socket message with the new routing identifier added. null, if the
     * message is null or if the message is not of type DATA.
     */
    @Override
    protected SocketMsg handleIncomingDataMessage(LinkSocket linkSocket, SocketMsg msg) {
        RRMsg rrMsg;
        try {
            rrMsg = RRMsg.parseFrom(msg.getPayload());
        } catch (IllegalArgumentException iae) {
            // message is a regular payload, i.e., does not follow
            // the request-reply format. So, a request-reply message
            // must be created.
            rrMsg = new RRMsg(msg.getPayload());
        }
        // get clock identifier associated with link
        if (linkSocket == null) return null;
        int clockId = linkSocket.getClockId();
        // add the routing identifier to the message
        rrMsg.addRoutingIdentifier(clockId);
        msg = new SocketMsg(msg.getSrcId(), msg.getDestId(), MsgType.DATA,
                msg.getClockId(), rrMsg.toByteArray());
        return msg;
    }

    /**
     * @param routingId integer routing identifier
     * @return socket identifier associated with the integer routing identifier.
     * May return null if there isn't a socket identifier associated with the
     * integer routing identifer, such as if the link was closed or never existed.
     * @apiNote Integer routing identifiers are only parseable by immediate adjacent sockets.
     * While passing an integer routing identifier that corresponds to a socket that is not
     * adjacent may return a non-null value, it will not correspond to the correct socket identifier.
     */
    public SocketIdentifier getSocketIdentifier(int routingId){
        LinkSocket linkSocket = linkSockets.get(routingId);
        return linkSocket != null ? linkSocket.getPeerId() : null;
    }

    /**
     * Receives a request-reply message. The first routing identifier of message
     * is the identifier of the sender. This first routing identifier may
     * be parsed to a SocketIdentifier, if desirable, using {@link RouterSocket#getSocketIdentifier(int)}.
     * @apiNote This method is equivalent to invoking {@link RRMsg#parseFrom(byte[])} on the result of
     * {@link RouterSocket#receive(Long, boolean)}.
     * @throws InterruptedException if thread was interrupted while waiting for a message.
     */
    public RRMsg recv(Long timeout, boolean notifyIfNone) throws InterruptedException {
        byte[] payload = receive(timeout, notifyIfNone);
        if(payload != null) return RRMsg.parseFrom(payload);
        else return null;
    }

    /** @see RouterSocket#recv(Long, boolean)  */
    public RRMsg recv(Long timeout) throws InterruptedException {
        return recv(timeout, false);
    }

    /** @see RouterSocket#recv(Long, boolean)  */
    public RRMsg recv() throws InterruptedException {
        return recv(null, false);
    }

    /**
     * Extracts the destination of the message from the
     * routing identifiers list of the message and then
     * attempts to send the message within the specified timeout.
     * @param msg request-reply message to be sent.
     * @param timeout maximum time allowed to wait for permission to send the message.
     * @throws InterruptedException if the thread was interrupted while sending the message.
     * @apiNote This method uses and modifies the message object.
     * @see ConfigurableSocket#send(byte[], Long, boolean)
     */
    public boolean send(RRMsg msg, Long timeout) throws InterruptedException {
        if(msg == null)
            throw new IllegalArgumentException("Message must not be null.");
        Object routingId = msg.pollRoutingIdentifier();
        if(routingId == null)
            throw new IllegalArgumentException("No destination found in the message.");

        LinkSocket linkSocket;
        if(routingId instanceof SocketIdentifier peerId)
            linkSocket = getLinkSocket(peerId);
        else // routing id is an integer
            linkSocket = linkSockets.get((int) routingId);

        // silently discard a message for which the destination socket
        // does not exist or has been closed.
        if(linkSocket == null) return true;

        try {
            byte[] payload = msg.hasRoutingIdentifiers() ? msg.toByteArray() : msg.getPayload();
            return linkSocket.send(new BytePayload(MsgType.DATA, payload), Timeout.calculateEndTime(timeout));
        }catch (LinkClosedException lce){
            // silently discard a message
            return true;
        }
    }

    /** @see RouterSocket#send(RRMsg, Long) */
    public void send(RRMsg msg) throws InterruptedException {
        send(msg, null);
    }

    /**
     * "notify if none" does not have any effect on sending operations of this socket.
     * @throws IllegalArgumentException if the payload is either null or does not follow
     * a Request-Reply message format.
     */
    @Override
    public boolean send(byte[] payload, Long timeout, boolean notifyIfNone) throws InterruptedException {
        if(payload == null)
            throw new IllegalArgumentException("Payload must not be null.");
        RRMsg rrMsg = RRMsg.parseFrom(payload);
        return send(rrMsg, timeout);
    }

    @Override
    protected boolean trySending(Payload payload) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Creates RouterSocket.
     * @param middleware middleware instance
     * @param tagId tag identifier of the socket
     * @return RouterSocket instance
     * @implNote Assumes the middleware to have the RouterSocket producer registered.
     */
    public static RouterSocket createSocket(A3MMiddleware middleware, String tagId){
        if(middleware == null)
            throw new IllegalArgumentException("Middleware is null.");
        return middleware.createSocket(tagId, protocol.id(), RouterSocket.class);
    }

    /**
     * Creates and starts a RouterSocket.
     * @param middleware middleware instance
     * @param tagId tag identifier of the socket
     * @return RouterSocket instance
     * @implNote Assumes the middleware to have the RouterSocket producer registered.
     */
    public static RouterSocket startSocket(A3MMiddleware middleware, String tagId){
        if(middleware == null)
            throw new IllegalArgumentException("Middleware is null.");
        return middleware.startSocket(tagId, protocol.id(), RouterSocket.class);
    }
}
