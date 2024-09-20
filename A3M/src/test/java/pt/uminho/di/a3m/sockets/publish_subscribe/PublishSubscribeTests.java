package pt.uminho.di.a3m.sockets.publish_subscribe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.core.A3MMiddleware;
import pt.uminho.di.a3m.core.SocketProducer;
import pt.uminho.di.a3m.core.SocketTestingUtilities;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.publish_subscribe.messaging.PSMsg;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;

public class PublishSubscribeTests {
    A3MMiddleware middleware;
    int port;
    private final static List<SocketProducer> producerList =
            List.of(SubSocket::new, PubSocket::new);


    @BeforeEach
    void init() throws SocketException, UnknownHostException {
        var entry = SocketTestingUtilities.createAndStartMiddlewareInstance(producerList);
        port = entry.getKey();
        middleware = entry.getValue();
    }

    @Test
    void simpleSubscribeAndPublishTest() throws InterruptedException {
        SubSocket subscriber = middleware.startSocket("Subscriber", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        PubSocket publisher = middleware.startSocket("Publisher", SocketsTable.PUB_PROTOCOL_ID, PubSocket.class);
        // link and wait for the link to be established
        subscriber.link(publisher.getId());
        assert (subscriber.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        // subscribe to "News"
        subscriber.subscribe("News");
        // wait a bit for the subscription to arrive
        Thread.sleep(20L);
        // publish multiple messages
        PSMsg newsMsg = new PSMsg("News","Breaking News!");
        publisher.send(newsMsg,0L);
        PSMsg randomMsg = new PSMsg("Random","Something...");
        publisher.send(randomMsg,0L);
        PSMsg noticiasMsg = new PSMsg("News/Portugal","Noticias de ultima hora!");
        publisher.send(noticiasMsg,0L);
        // assert only newsMsg and noticiasMsg arrive
        // since these have a prefix in common with the subscription
        assert newsMsg.equals(subscriber.recv());
        assert noticiasMsg.equals(subscriber.recv());
        assert subscriber.recv(10L) == null;
    }
}
