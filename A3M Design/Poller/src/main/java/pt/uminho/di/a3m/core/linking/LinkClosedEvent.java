package pt.uminho.di.a3m.core.linking;

import pt.uminho.di.a3m.core.Link;
import pt.uminho.di.a3m.core.LinkSocket;
import pt.uminho.di.a3m.core.SocketEvent;

public class LinkClosedEvent extends SocketEvent {
    private final LinkSocket linkSocket;
    public LinkClosedEvent(LinkSocket linkSocket) {
        super();
        this.linkSocket = linkSocket;
    }
    public LinkSocket getLinkSocket() {
        return linkSocket;
    }
}
