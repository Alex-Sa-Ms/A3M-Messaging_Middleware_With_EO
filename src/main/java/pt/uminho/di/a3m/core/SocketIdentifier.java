package pt.uminho.di.a3m.core;

import java.util.Objects;

public record SocketIdentifier(String nodeId, String tagId) {
    public static boolean isValid(SocketIdentifier sid){
        return sid != null
                && sid.nodeId != null
                && sid.tagId != null;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;

        SocketIdentifier that = (SocketIdentifier) object;

        if (!Objects.equals(nodeId, that.nodeId)) return false;
        return Objects.equals(tagId, that.tagId);
    }

    @Override
    public String toString() {
        return "SockId{" + nodeId + '.' + tagId + '}';
    }
}