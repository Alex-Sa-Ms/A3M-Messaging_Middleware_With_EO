package pt.uminho.di.a3m;

/**
 * A socket is identified by the combination of two identifiers:
 *  - nodeId : identifier of the node that owns the socket;
 *  - tagId : locally unique identifier of the socket
 *            (used to distinguish the socket between
 *            the other sockets of the same node)
 */
public class SocketIdentifier {
    final public String nodeId;
    final public String tagId;
    public SocketIdentifier(String nodeId, String tagId) {
        this.nodeId = nodeId;
        this.tagId = tagId;
    }

    /**
     * Checks if the socket identifier is valid.
     * @param id identifier of a socket
     * @throws IllegalArgumentException if the identifier is null or
     * if any of its components are null.
     */
    public static void check(SocketIdentifier id){
        if(id == null || id.nodeId == null || id.tagId == null)
            throw new IllegalArgumentException("Identifier of a socket must not be null nor have a 'null' component.");
    }

    /**
     * Checks if the socket identifier is valid.
     * @param id identifier of a socket
     * @return 'null' if it is valid; Otherwise,
     * returns a string describing the problem
     * with the identifier.
     */
    public static String checkAndReturnError(SocketIdentifier id){
        if(id == null || id.nodeId == null || id.tagId == null)
            return "Identifier of a socket must not be null nor have a 'null' component.";
        return null;
    }

    public SocketIdentifier clone(){
        return new SocketIdentifier(nodeId, tagId);
    }

    @Override
    public String toString() {
        return "SockID{" +
                nodeId + ':' +
                tagId + '}';
    }
}
