package pt.uminho.di.a3m.sockets.publish_subscribe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.core.*;
import pt.uminho.di.a3m.poller.PollFlags;
import pt.uminho.di.a3m.sockets.SocketsTable;
import pt.uminho.di.a3m.sockets.publish_subscribe.messaging.PSPayload;
import pt.uminho.di.a3m.sockets.publish_subscribe.messaging.SubscriptionsPayload;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;

import static pt.uminho.di.a3m.core.SocketTestingUtilities.*;

public class PublishSubscribeTests {
    A3MMiddleware middleware;
    int port;
    private final static List<SocketProducer> producerList =
            List.of(SubSocket::new, PubSocket::new, XPubSocket::new, XSubSocket::new);


    @BeforeEach
    void init() throws SocketException, UnknownHostException {
        var entry = createAndStartMiddlewareInstance(producerList);
        port = entry.getKey();
        middleware = entry.getValue();
    }

    @Test
    void subscribeAndUnsubscribeTest() throws InterruptedException {
        SubSocket subscriber = middleware.startSocket("Subscriber", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        PubSocket publisher = middleware.startSocket("Publisher", SocketsTable.PUB_PROTOCOL_ID, PubSocket.class);
        // link and wait for the link to be established
        subscriber.link(publisher.getId());
        assert (subscriber.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        // publish a message to topic "News" and check
        // that it is not received by the subscriber
        final String newsTopic = "News";
        final PSPayload newsMsg = new PSPayload(newsTopic,"Breaking News!");
        assert publisher.send(newsMsg,0L);
        assert subscriber.recv(20L) == null;
        // subscribe to "News"
        subscriber.subscribe(newsTopic);
        // wait a bit for the subscription to arrive
        Thread.sleep(20L);
        // send the same message and assert that it does arrive at the subscriber now
        assert publisher.send(newsMsg,0L);
        PSPayload payload = subscriber.recv();
        assert newsMsg.equals(payload);
        // unsubscribe from "News"
        subscriber.unsubscribe(newsTopic);
        // wait a bit for the unsubscribe message to arrive
        Thread.sleep(20L);
        // send the message again and assert that it does not arrive at the subscriber
        assert publisher.send(newsMsg,0L);
        assert subscriber.recv(20L) == null;
    }


    @Test
    void orderedReceiveTest() throws InterruptedException {
        // creates a socket manager with a message dispatcher (for testing purposes)
        // that can add delay to the delivery of messages
        DirectMessageDispatcher dispatcher = new DirectMessageDispatcher();
        SocketManager socketManager = createSocketManager("Node", dispatcher);
        socketManager.registerProducer(SubSocket::new);
        socketManager.registerProducer(PubSocket::new);
        // create sockets and register them in the message dispatcher
        SubSocket subscriber = socketManager.startSocket("Subscriber", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        PubSocket publisher = socketManager.startSocket("Publisher", SocketsTable.PUB_PROTOCOL_ID, PubSocket.class);
        dispatcher.registerSocket(subscriber);
        dispatcher.registerSocket(publisher);
        // link sockets and wait for the link to be established
        subscriber.link(publisher.getId());
        assert (subscriber.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        // subscribe to "News"
        subscriber.subscribe("News");
        // wait a bit for the subscription to arrive
        Thread.sleep(20L);
        // set a delay of 20ms to ensure this message arrives
        // after the messages that will follow
        dispatcher.setDelays(20L,21L);
        PSPayload newsMsg = new PSPayload("News","Breaking News!");
        publisher.send(newsMsg,0L);
        // remove delay and send the remaining messages
        dispatcher.setRandomDelay(false);
        PSPayload randomMsg = new PSPayload("Random","Something...");
        publisher.send(randomMsg,0L);
        PSPayload noticiasMsg = new PSPayload("News/Portugal","Noticias de ultima hora!");
        publisher.send(noticiasMsg,0L);
        // assert only newsMsg and noticiasMsg arrive
        // since these have a prefix in common with the subscription,
        // and that they arrive in the order in which the send() method was invoked
        assert newsMsg.equals(subscriber.recv());
        assert noticiasMsg.equals(subscriber.recv());
        assert subscriber.recv(10L) == null;
    }

    @Test
    void publishTimeoutTest() throws InterruptedException {
        PubSocket publisher = middleware.startSocket("Publisher", SocketsTable.PUB_PROTOCOL_ID, PubSocket.class);
        SubSocket subscriber = middleware.startSocket("Subscriber", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        // set subscriber's capacity to 0, so that the publish operation times out
        subscriber.setOption("capacity",0);
        // link and wait for the link to be established
        subscriber.link(publisher.getId());
        assert (subscriber.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        // subscribe to "News"
        subscriber.subscribe("News");
        // wait a bit for the subscription to arrive
        Thread.sleep(20L);
        // attempt to send and check that the operation times out
        PSPayload msg = new PSPayload("News", "A news message...");
        boolean sent = publisher.send(msg, 20L);
        assert !sent;
    }

    @Test
    void publishTimeoutAndCancelReservationsTest() throws InterruptedException {
        PubSocket publisher = middleware.startSocket("Publisher", SocketsTable.PUB_PROTOCOL_ID, PubSocket.class);
        SubSocket subscriber1 = middleware.startSocket("Subscriber1", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        SubSocket subscriber2 = middleware.startSocket("Subscriber2", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        // set subscriber1's capacity to 1, so that if cancelling a reservation is not
        // working properly, a second publish operation cannot happen
        subscriber1.setOption("capacity",1);
        // set subscriber2's capacity to 0, so that the publish operation times out
        subscriber2.setOption("capacity",0);
        // link socks and wait for the links to be established
        subscriber1.link(publisher.getId());
        subscriber2.link(publisher.getId());
        assert (subscriber1.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        assert (subscriber2.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        // subscriber1 must subscribe to a prefix of the topic of the
        // message that is going to be published, so that the reservation
        // of a credit happens before the reservation of a credit for a
        // subscriber that subscribed to the actual topic
        subscriber1.subscribe("");
        // subscriber2 subscribes to the actual topic
        subscriber2.subscribe("News");
        // wait a bit for the subscribe messages to arrive
        Thread.sleep(20L);
        // attempt to publish to the topic subscribed by subscriber2
        // and check that the operation times out
        PSPayload msg = new PSPayload("News", "A news message...");
        boolean sent = publisher.send(msg, 20L);
        assert !sent;
        // now, publish to a topic not subscribed by subscriber2,
        // and check that the message is sent and arrives at subscriber1
        msg = new PSPayload("AnyTopic", "Some message...");
        sent = publisher.send(msg, 0L);
        assert sent;
        assert subscriber1.recv() != null;
    }

    @Test
    void publishInterruptedAndCancelReservationsTest() throws InterruptedException {
        PubSocket publisher = middleware.startSocket("Publisher", SocketsTable.PUB_PROTOCOL_ID, PubSocket.class);
        SubSocket subscriber1 = middleware.startSocket("Subscriber1", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        SubSocket subscriber2 = middleware.startSocket("Subscriber2", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        // set subscriber1's capacity to 1, so that if cancelling a reservation is not
        // working properly, a second publish operation cannot happen
        subscriber1.setOption("capacity",1);
        // set subscriber2's capacity to 0, so that the publish operation times out
        subscriber2.setOption("capacity",0);
        // link socks and wait for the links to be established
        subscriber1.link(publisher.getId());
        subscriber2.link(publisher.getId());
        assert (subscriber1.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        assert (subscriber2.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        // subscriber1 must subscribe to a prefix of the topic of the
        // message that is going to be published, so that the reservation
        // of a credit happens before the reservation of a credit for a
        // subscriber that subscribed to the actual topic
        subscriber1.subscribe("");
        // subscriber2 subscribes to the actual topic
        subscriber2.subscribe("News");
        // wait a bit for the subscribe messages to arrive
        Thread.sleep(20L);
        // interrupt the current thread so that the publish
        // operation is canceled
        Thread.currentThread().interrupt();
        // attempt to publish to the topic subscribed by subscriber2
        // and check that the operation is interrupted
        PSPayload msg = new PSPayload("News", "A news message...");
        try {
            publisher.send(msg);
            assert false;
        } catch (InterruptedException ignored) {}
        // now, publish to a topic not subscribed by subscriber2,
        // and check that the message is sent and arrives at subscriber1
        msg = new PSPayload("AnyTopic", "Some message...");
        boolean sent = publisher.send(msg, 0L);
        assert sent;
        assert subscriber1.recv() != null;
    }

    /**
     * Close link with a subscriber that does not have credits
     * so that the publish operation is unlocked and the
     * other subscribers can receive the message.
     */
    void publishUnlockedByLinkClosure(boolean publisherClosesLink) throws InterruptedException {
        PubSocket publisher = middleware.startSocket("Publisher", SocketsTable.PUB_PROTOCOL_ID, PubSocket.class);
        SubSocket subscriber1 = middleware.startSocket("Subscriber1", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        SubSocket subscriber2 = middleware.startSocket("Subscriber2", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        // set subscriber2's capacity to 0, so that the publish operation is blocked
        subscriber2.setOption("capacity",0);
        // link socks and wait for the links to be established
        subscriber1.link(publisher.getId());
        subscriber2.link(publisher.getId());
        assert (subscriber1.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        assert (subscriber2.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        // make subscribers subscribe to the topic "News"
        subscriber1.subscribe("News");
        subscriber2.subscribe("News");
        // wait a bit for the subscribe messages to arrive
        Thread.sleep(20L);
        // create a thread that attempts to receive a
        // message on subscriber1 and after checking that
        // the operation timed out, make the link between
        // the publisher and subscriber2 be closed so that
        // the publishing operation can be completed
        Thread t = new Thread(() -> {
            try {
                // check that subscriber1 cannot receive
                // the message within a 20ms interval
                assert subscriber1.recv(20L) == null;
                // make the link between publisher and subscriber2
                // be closed, so that the publisher is released of
                // having to make a reservation for subscriber2
                if(publisherClosesLink){
                    publisher.unlink(subscriber2.getId());
                }else {
                    subscriber2.unlink(publisher.getId());
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        // check that the message eventually is sent
        PSPayload msg = new PSPayload("News", "A news message...");
        boolean sent = publisher.send(msg);
        assert sent;
        assert subscriber1.recv() != null;
    }

    /**
     * Makes a subscriber that does not have credits
     * close the link with the publisher so that
     * the publish operation is unlocked and the other subscribers
     * can receive the message.
     */
    @Test
    void publishUnlockedBySubscriberClosingLinkTest() throws InterruptedException {
        publishUnlockedByLinkClosure(false);
    }

    /**
     * Makes a publisher close the link with the subscriber
     * that does not have credits, so that the publish operation
     * is unlocked and the other subscribers can receive the message.
     */
    @Test
    void publishUnlockedByPublisherClosingLinkTest() throws InterruptedException {
        publishUnlockedByLinkClosure(true);
    }

    @Test
    void interruptPublisherDuringBlockingSendTest() throws InterruptedException {
        PubSocket publisher = middleware.startSocket("Publisher", SocketsTable.PUB_PROTOCOL_ID, PubSocket.class);
        SubSocket subscriber = middleware.startSocket("Subscriber", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        // set subscriber's capacity to 0, so that the publish operation will
        // stall due to lack of a credit to reserve
        subscriber.setOption("capacity",0);
        // link and wait for the link to be established
        subscriber.link(publisher.getId());
        assert (subscriber.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        // subscribe to "News"
        subscriber.subscribe("News");
        // wait a bit for the subscription to arrive
        Thread.sleep(20L);
        // interrupt the thread
        Thread.currentThread().interrupt();
        // attempt to do a blocking send and check that the
        // operation is interrupted by the InterruptedException
        try {
            PSPayload msg = new PSPayload("News", "A news message...");
            publisher.send(msg);
            assert false; // interrupted exception must be thrown
        } catch (InterruptedException ignored) {}
    }

    @Test
    void interruptPublisherDuringNonBlockingSendTest() throws InterruptedException {
        PubSocket publisher = middleware.startSocket("Publisher", SocketsTable.PUB_PROTOCOL_ID, PubSocket.class);
        SubSocket subscriber = middleware.startSocket("Subscriber", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        // set subscriber's capacity to 0, so that the publish operation will
        // stall due to lack of a credit to reserve
        subscriber.setOption("capacity",0);
        // link and wait for the link to be established
        subscriber.link(publisher.getId());
        assert (subscriber.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        // subscribe to "News"
        subscriber.subscribe("News");
        // wait a bit for the subscription to arrive
        Thread.sleep(20L);
        // interrupt the thread
        Thread.currentThread().interrupt();
        // attempt to do a non-blocking send and check that the operation times out
        // and is not interrupted by InterruptedException
        try {
            PSPayload msg = new PSPayload("News", "A news message...");
            boolean sent = publisher.send(msg, 0L);
            assert !sent;
        } catch (InterruptedException e) {
            // should not get here
            assert false;
        }
    }

    /**
     * Make subscription tasks be scheduled.
     */
    @Test
    void scheduleSubscriptionTasks() throws InterruptedException {
        PubSocket publisher = middleware.startSocket("Publisher", SocketsTable.PUB_PROTOCOL_ID, PubSocket.class);
        SubSocket subscriber1 = middleware.startSocket("Subscriber1", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        SubSocket subscriber2 = middleware.startSocket("Subscriber2", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        // set subscriber2's capacity to 0, so that the first publish operation is blocked
        // while holding the read lock which prevents subscription tasks to be handled immediately
        subscriber2.setOption("capacity",0);
        // link socks and wait for the links to be established
        subscriber1.link(publisher.getId());
        subscriber2.link(publisher.getId());
        assert (subscriber1.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        assert (subscriber2.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        // make subscriber2 subscribe to the topic "News"
        subscriber2.subscribe("News");
        // wait a bit for the subscribe messages to arrive
        Thread.sleep(20L);
        Thread main = Thread.currentThread();
        // create a thread that waits for the main thread to be blocked
        // on the send() operation, so that it can make subscriber1 subscribe to
        // the "News" topic and then close the link between the publisher
        // and subscriber2 in order for the send operation to complete,
        // and a new message be published which should be received by subscriber1
        Thread t = new Thread(() -> {
            try {
                // wait for the main thread to be in a waiting state
                while (!main.getState().equals(Thread.State.WAITING));
                // make subscriber1 subscribe to "News" and then
                // wait a bit for the subscribe messages to arrive
                subscriber1.subscribe("News");
                Thread.sleep(20L);
                // close the link between publisher and subscriber2
                subscriber2.unlink(publisher.getId());
            } catch (InterruptedException ignored) {}
        });
        t.start();
        // do a blocking publish operation
        PSPayload msg = new PSPayload("News", "A news message...");
        assert publisher.send(msg);
        // publish a new message and check that subscriber1
        // receives the message, meaning the subscribe task
        // was handled.
        msg = new PSPayload("News", "A different message...");
        assert publisher.send(msg);
        assert msg.equals(subscriber1.recv());
    }

    @Test
    void sendWhenPubSocketNotReadyTest() throws InterruptedException {
        PubSocket publisher = middleware.createSocket("Publisher", SocketsTable.PUB_PROTOCOL_ID, PubSocket.class);
        // check that attempting to publish a message when the socket is
        // not ready throws an IllegalStateException
        assert publisher.getState() == SocketState.CREATED;
        PSPayload msg = new PSPayload("News","News...");
        try {
            publisher.send(msg);
            assert false; // illegal state exception should be thrown
        } catch (IllegalStateException ignored) {}
        // start socket, so that it can be closed
        publisher.start();
        // close socket
        publisher.close();
        assert publisher.getState() == SocketState.CLOSED;
        try {
            publisher.send(msg);
            assert false; // illegal state exception should be thrown
        } catch (IllegalStateException ignored) {}
    }

    @Test
    void subscribeWhenSubSocketNotReady() throws InterruptedException {
        SubSocket subscriber = middleware.createSocket("Subscriber", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        // check that attempting to subscribe a topic when the socket is
        // not ready throws an IllegalStateException
        assert subscriber.getState() == SocketState.CREATED;
        try {
            subscriber.subscribe("News");
            assert false; // illegal state exception should be thrown
        } catch (IllegalStateException ignored) {}
        // start socket, so that it can be closed
        subscriber.start();
        // close socket
        subscriber.close();
        assert subscriber.getState() == SocketState.CLOSED;
        try {
            subscriber.subscribe("News");
            assert false; // illegal state exception should be thrown
        } catch (IllegalStateException ignored) {}
    }

    @Test
    void unsubscribeWhenSubSocketNotReady() throws InterruptedException {
        SubSocket subscriber = middleware.createSocket("Subscriber", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        // check that attempting to unsubscribe a topic when the socket is
        // not ready throws an IllegalStateException
        assert subscriber.getState() == SocketState.CREATED;
        try {
            subscriber.unsubscribe("News");
            assert false; // illegal state exception should be thrown
        } catch (IllegalStateException ignored) {}
        // start socket, so that it can be closed
        subscriber.start();
        // close socket
        subscriber.close();
        assert subscriber.getState() == SocketState.CLOSED;
        try {
            subscriber.unsubscribe("News");
            assert false; // illegal state exception should be thrown
        } catch (IllegalStateException ignored) {}
    }

    @Test
    void XPubSocketReceivingSubscribeAndUnsubscribeMessagesTest() throws InterruptedException {
        XPubSocket xpublisher = middleware.startSocket("XPublisher", SocketsTable.XPUB_PROTOCOL_ID, XPubSocket.class);
        SubSocket subscriber = middleware.startSocket("Subscriber", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        // link and wait for the link to be established
        subscriber.link(xpublisher.getId());
        assert (subscriber.waitForLinkEstablishment(xpublisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        // subscribe to "News"
        subscriber.subscribe("News");
        assert xpublisher.receive() != null;
        // unsubscribe to "News"
        subscriber.unsubscribe("News");
        assert xpublisher.receive() != null;
    }

    @Test
    void XSubSocketSubscribingAndUnsubscribingUsingMessages() throws InterruptedException {
        XSubSocket xSubscriber = middleware.startSocket("XSubscriber", SocketsTable.XSUB_PROTOCOL_ID, XSubSocket.class);
        PubSocket publisher = middleware.startSocket("Publisher", SocketsTable.PUB_PROTOCOL_ID, PubSocket.class);
        // link and wait for the link to be established
        xSubscriber.link(publisher.getId());
        assert (xSubscriber.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        // create subscribe message for the "News" topic
        final String newsTopic = "News";
        SubscriptionsPayload subPayload = new SubscriptionsPayload(
                SubscriptionsPayload.SUBSCRIBE,
                List.of(newsTopic));
        xSubscriber.send(subPayload);
        // wait a bit for the subscription to arrive
        Thread.sleep(20L);
        // send the same message and assert that it arrives at the xsubscriber
        final PSPayload newsMsg = new PSPayload(newsTopic,"Breaking News!");
        assert publisher.send(newsMsg,0L);
        PSPayload payload = xSubscriber.recv();
        assert newsMsg.equals(payload);
        // unsubscribe from "News"
        subPayload = new SubscriptionsPayload(
                SubscriptionsPayload.UNSUBSCRIBE,
                List.of(newsTopic));
        xSubscriber.send(subPayload);
        // wait a bit for the unsubscribe message to arrive
        Thread.sleep(20L);
        // send the message again and assert that it does not arrive at the subscriber
        assert publisher.send(newsMsg,0L);
        assert xSubscriber.recv(20L) == null;
    }

    @Test
    void multiHopTest() throws InterruptedException {
        SubSocket subscriber = middleware.startSocket("Subscriber", SocketsTable.SUB_PROTOCOL_ID, SubSocket.class);
        XPubSocket xPublisher = middleware.startSocket("XPublisher", SocketsTable.XPUB_PROTOCOL_ID, XPubSocket.class);
        XSubSocket xSubscriber = middleware.startSocket("XSubscriber", SocketsTable.XSUB_PROTOCOL_ID, XSubSocket.class);
        PubSocket publisher = middleware.startSocket("Publisher", SocketsTable.PUB_PROTOCOL_ID, PubSocket.class);
        // link subscriber to xPublisher
        subscriber.link(xPublisher.getId());
        // link xSubscriber to publisher
        xSubscriber.link(publisher.getId());
        // wait for the links to be established
        assert (subscriber.waitForLinkEstablishment(xPublisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;
        assert (xSubscriber.waitForLinkEstablishment(publisher.getId(), null) & PollFlags.POLLINOUT_BITS) != 0;

        // make subscriber subscribe to "News"
        subscriber.subscribe("News");
        // check that the subscribe message can be received
        // by xPublisher
        byte[] subMsg = xPublisher.receive();
        assert subMsg != null;
        // forward the subscribe message to publisher using
        // the xSubscriber socket
        xSubscriber.send(subMsg);
        // wait a bit for the subscribe message to be received by the publisher
        Thread.sleep(20L);
        // make publisher publish a message to the "News" topic
        PSPayload psPayload = new PSPayload("News", "A message...");
        publisher.send(psPayload);
        // assert the published message reaches the xSubscriber
        byte[] msg = xSubscriber.receive();
        assert msg != null;
        // forward the message to the subscriber using xPublisher
        xPublisher.send(msg);
        // assert the message reaches the subscriber
        assert psPayload.equals(subscriber.recv());

        // make subscriber unsubscribe from "News"
        subscriber.unsubscribe("News");
        subMsg = xPublisher.receive();
        assert subMsg != null;
        xSubscriber.send(subMsg);
        // wait a bit for the subscribe message to be received by the publisher
        Thread.sleep(20L);
        // make publisher publish again a message to the "News" topic
        publisher.send(psPayload);
        // assert the published message does not reach xSubscriber now
        msg = xSubscriber.receive(20L);
        assert msg == null;
    }

    // TODO - test multiple threads publishing to multiple topics concurrently

}