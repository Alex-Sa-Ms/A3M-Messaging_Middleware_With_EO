package pt.uminho.di.a3m.core;

public record SocketIdentifier(String nodeId, String tagId) {
    public static boolean isValid(SocketIdentifier sid){
        return sid != null
                && sid.nodeId != null
                && sid.tagId != null;
    }

    @Override
    public String toString() {
        return "SockId{" + nodeId + '.' + tagId + '}';
    }
}