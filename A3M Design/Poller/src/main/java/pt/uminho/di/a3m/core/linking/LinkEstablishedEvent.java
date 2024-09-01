package pt.uminho.di.a3m.core.linking;

import pt.uminho.di.a3m.core.Link;
import pt.uminho.di.a3m.core.LinkSocket;
import pt.uminho.di.a3m.core.SocketEvent;
import pt.uminho.di.a3m.core.messaging.payloads.SerializableMap;

public class LinkEstablishedEvent extends SocketEvent {
    private final LinkSocket linkSocket;
    private final SerializableMap payload;

    public LinkEstablishedEvent(LinkSocket linkSocket, SerializableMap payload) {
        super();
        this.linkSocket = linkSocket;
        this.payload = payload;
    }

    public LinkEstablishedEvent(LinkSocket linkSocket){
        this(linkSocket, null);
    }

    public LinkSocket getLinkSocket() {
        return linkSocket;
    }

    public SerializableMap getPayload() {
        return payload;
    }
}
