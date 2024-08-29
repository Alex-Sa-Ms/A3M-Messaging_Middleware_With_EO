package pt.uminho.di.a3m.core.linking;

import pt.uminho.di.a3m.core.Link;
import pt.uminho.di.a3m.core.SocketEvent;
import pt.uminho.di.a3m.core.messaging.payloads.SerializableMap;

public class LinkEstablishedEvent extends SocketEvent {
    private final Link link;
    private final SerializableMap payload;

    public LinkEstablishedEvent(Link link, SerializableMap payload) {
        super();
        this.link = link;
        this.payload = payload;
    }

    public Link getLink() {
        return link;
    }

    public SerializableMap getPayload() {
        return payload;
    }
}
