package pt.uminho.di.a3m.core.linking;

import pt.uminho.di.a3m.core.Link;
import pt.uminho.di.a3m.core.LinkIdentifier;
import pt.uminho.di.a3m.core.SocketEvent;

public class LinkClosedEvent extends SocketEvent {
    private final Link link;
    public LinkClosedEvent(Link link) {
        super();
        this.link = link;
    }
    public Link getLink() {
        return link;
    }
}
