package pt.uminho.di.a3m.core;

public record LinkIdentifier(SocketIdentifier srcId, SocketIdentifier destId) {
    static boolean isValid(LinkIdentifier linkId){
        return linkId != null
                && SocketIdentifier.isValid(linkId.srcId)
                && SocketIdentifier.isValid(linkId.destId);
    }
}