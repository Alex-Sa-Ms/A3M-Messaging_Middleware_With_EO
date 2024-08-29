package pt.uminho.di.a3m.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.core.messaging.CoreMessages;
import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.SerializableMap;
import pt.uminho.di.a3m.core.messaging.SocketMsg;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

class LinkManagerTest {
    Protocol protocol;
    Protocol incompatibleProtocol;
    SocketIdentifier sid1, sid2;
    DummySocket socket1, socket2;
    LinkManager lm1, lm2;
    ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);


    private class DirectDispatcherToLinkManager implements MessageDispatcher{
        private final LinkManager destLm; // link manager that is the destination
        public DirectDispatcherToLinkManager(LinkManager destLm) {
            this.destLm = destLm;
        }

        @Override
        public void dispatch(SocketMsg msg) {
            scheduler.execute(() -> destLm.handleMsg(msg));
            String type = "";
            String payload = "";

            try {
                switch (msg.getType()) {
                    case MsgType.ERROR -> {
                        type = "ERROR";
                        payload = String.valueOf(CoreMessages.ErrorPayload.parseFrom(msg.getPayload()).getCode());
                    }
                    case MsgType.LINK -> {
                        type = "LINK";
                        payload = SerializableMap.deserialize(msg.getPayload()).toString();
                    }
                    case MsgType.LINKACK -> {
                        type = "LINKACK";
                        payload = SerializableMap.deserialize(msg.getPayload()).toString();
                    }
                    case MsgType.UNLINK -> {
                        type = "UNLINK";
                        payload = String.valueOf(ByteBuffer.wrap(msg.getPayload()).getInt());
                    }
                    case MsgType.DATA -> {
                        type = "DATA";
                        payload = String.valueOf(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(msg.getPayload())));
                    }
                    default -> {
                        type = String.valueOf(msg.getType());
                        payload = String.valueOf(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(msg.getPayload())));
                    }
                };
            }catch (Exception ignored){}
            String print = "Dispatched: src=" + msg.getSrcId() + ", dest=" + msg.getDestId()
                    + ", type=" + type + ", payload=" + payload;
            System.out.println(print);
        }

        @Override
        public AtomicReference<SocketMsg> scheduleDispatch(SocketMsg msg, long dispatchTime) {
            AtomicReference<SocketMsg> ref = new AtomicReference<>(msg);
            long delay = Math.max(0L, dispatchTime - System.currentTimeMillis());
            scheduler.schedule(() -> {
                SocketMsg m = ref.getAndSet(null);
                if(m != null) {
                    destLm.handleMsg(m);
                }
            }, delay, TimeUnit.MILLISECONDS);
            return ref;
        }
    }

    @BeforeEach
    void initSocketsAndLinkManagers(){
        protocol = new Protocol(12345, "Protocol12345");
        incompatibleProtocol = new Protocol(54321, "Protocol54321");
        // create socket and link manager 1
        sid1 = new SocketIdentifier("NodeA", "SocketA");
        socket1 = new DummySocket(sid1, protocol);
        lm1 = new LinkManager(socket1);
        // create socket and link manager 2
        sid2 = new SocketIdentifier("NodeB", "SocketB");
        socket2 = new DummySocket(sid2, protocol);
        lm2 = new LinkManager(socket2);
        // set custom message dispatcher to send socket1 messages
        // directly to the socket2's link manager
        socket1.setCoreComponents(new DirectDispatcherToLinkManager(lm2), null);
        // set custom message dispatcher to send socket2 messages
        // directly to the socket1's link manager
        socket2.setCoreComponents(new DirectDispatcherToLinkManager(lm1), null);
    }

    private void waitUntil(Supplier<Boolean> predicate) throws InterruptedException {
        while (!predicate.get())
            Thread.sleep(5);
    }

    @Test
    void link() throws InterruptedException {
        lm1.link(sid2);
        // waiting for socket2 to be linked,
        // because in the normal flow of the
        // 3-way handshake, the initiator
        // is the first to establish the link,
        // as the other socket only receives the
        // required LINKACK msg after the initiator
        // has received the answer to its LINK msg.
        waitUntil(() -> lm2.isLinked(sid1));
        assert lm1.isLinked(sid2);
    }

    @Test
    void unlink() throws InterruptedException {
        lm1.link(sid2);
        waitUntil(() -> lm2.isLinked(sid1));
        lm2.unlink(sid1);
        waitUntil(() -> lm2.isUnlinked(sid1));
        assert lm1.isUnlinked(sid2);
    }

    @Test
    void linkSchedulingAfterNonFatalRefusal() throws InterruptedException {
        // set socket2's max links to 0, so that the socket1
        // link request can be refused with a non-fatal reason
        socket2.setOption("maxLinks",0);
        // make socket1 send link request to socket2
        lm1.link(sid2);
        // wait until there is a scheduled message
        LinkNew link = lm1.links.get(sid2);
        waitUntil(() -> link != null && link.getScheduled() != null && link.getScheduled().get() != null);
        // assert socket1 is not yet linked with socket2
        assert !lm1.isLinked(sid2);
        // change socket2's max links to enable socket1 to link
        socket2.setOption("maxLinks",1);
        // waits for the sockets to link
        waitUntil(()->lm2.isLinked(sid1));
        assert lm1.isLinked(sid2);
    }

    @Test
    void linkCancel() throws InterruptedException {
        // unlink() while having a scheduled link request
        // set socket2's max links to 0, so that the socket1
        // link request can be refused with a non-fatal reason
        socket2.setOption("maxLinks",0);
        // make socket1 send link request to socket2
        lm1.link(sid2);
        // wait until there is a scheduled message
        LinkNew link = lm1.links.get(sid2);
        waitUntil(() -> link != null && link.getScheduled() != null && link.getScheduled().get() != null);
        // with a scheduled link request, unlink() should
        // result in the cancelement of the scheduled request
        lm1.unlink(sid2);
        assert lm1.isUnlinked(sid2);
    }

    /**
     * This test should result in an exception being thrown informing that
     * linking is not currently possible as an unlink process is currently
     * in progress for the same peer.
     */
    @Test
    void unlinkImmediatelyFollowedByLink() throws InterruptedException {
        lm1.link(sid2);
        waitUntil(() -> lm1.isLinked(sid2));
        try {
            // socket's 2 lock is held here to prevent the handling
            // of the unlink message and therefore ensure the
            // success on throwing the exception
            socket2.getLock().lock();
            lm1.unlink(sid2);
            try {
                lm1.link(sid2);
                assert false;
            } catch (Exception ignored) {}
        }
        finally {
            socket2.getLock().unlock();
        }
    }

    @Test
    void fatalRefusal() throws InterruptedException {
        // set socket2's protocol to a protocol that is
        // incompatible with socket1's protocol. And set
        // its compatible protocols to not be compatible
        // with socket1.
        // This should result in a fatal refusal.
        socket2.setProtocol(incompatibleProtocol);
        socket2.setCompatProtocols(Set.of(incompatibleProtocol));

        // check linking process is unsuccessful
        try{
            // acquire socket2's lock to prevent handling
            // of the LINK message by socket2 before
            // asserting that the socket1's link state
            // with socket2 is LINKING
            socket2.getLock().lock();
            lm1.link(sid2);
            assert lm1.isLinking(sid2);
        } finally {
            socket2.getLock().unlock();
        }
        // released the lock so that socket2 can handle
        // the link request. After dealing with the
        // request, the socket1 must have its link
        // with socket2 closed (unlinked)
        waitUntil(() -> lm1.isUnlinked(sid2));
        assert lm2.isUnlinked(sid1);
    }

    @Test
    void isUnlinking() throws InterruptedException {
        // establish link between socket1 and socket2
        lm1.link(sid2);
        waitUntil(() -> lm1.isLinked(sid2));
        try{
            // acquire socket2's lock to prevent the UNLINK
            // message from being handled until the UNLINK
            // state is asserted
            socket2.getLock().lock();
            lm1.unlink(sid2);
            assert lm1.isLinkState(sid2,ls -> ls == LinkNew.LinkState.UNLINKING);
        } finally {
            socket2.getLock().unlock();
        }
        // wait for the unlink process to finish
        waitUntil(() -> lm1.isUnlinked(sid2));
        assert lm2.isUnlinked(sid1);
    }

    @Test
    void cancelOngoingLinkingProcess() throws InterruptedException {
        try{
            // acquire socket2's lock to prevent the LINK
            // message from being handled before requesting
            // the unlink and before verifying that
            // socket1's state becomes CANCELLING
            socket2.getLock().lock();
            lm1.link(sid2);
            assert lm1.isLinking(sid2);
            lm1.unlink(sid2);
            assert lm1.isLinkState(sid2,ls -> ls == LinkNew.LinkState.CANCELLING);
        } finally {
            socket2.getLock().unlock();
        }
        // wait for the unlink process to finish
        waitUntil(() -> lm1.isUnlinked(sid2));
        assert lm2.isUnlinked(sid1);
    }

    /**
     * The protocol is assumed to be symmetric, so this test is more
     * for security purposes. If for some reason, the peer accepted
     * the link establishment, but is not of a compatible type,
     * then an unlinking process should follow.
     */
    @Test
    void peerAcceptedLinkButIsNotCompatible() throws InterruptedException {
        // set socket2's protocol to a protocol that is
        // incompatible with socket1's protocol. But add
        // socket1's protocol be its compatible protocols list.
        // This should result in accepting a link request from
        // socket1, but then socket1 will detect the incompatibility
        // and refuse the link establishment.
        socket2.setProtocol(incompatibleProtocol);
        socket2.setCompatProtocols(Set.of(socket1.getProtocol()));
        // Acquire socket1 lock to ensure the LINKACK
        // message coming from socket2 is not handled
        // until the socket2's lock is acquired after
        // socket2 sends a positive LINKACK message
        socket1.getLock().lock();
        // make socket1 send a link request to socket2
        lm1.link(sid2);
        // wait until socket2 is in LINKING state to
        // wait for socket1's answer
        waitUntil(() -> lm2.isLinking(sid1));
        // acquire socket2's lock so that the fatal LINKACK
        // msg sent by socket1 is not handled until
        // we assert socket1 has closed the link
        socket2.getLock().lock();
        // release socket1 lock so that the LINKACK msg can be handled
        socket1.getLock().unlock();
        // wait for socket1 to close the link
        waitUntil(() -> lm1.isUnlinked(sid2));
        // release socket2 lock so that the fatal LINKACK msg
        // from socket1 can be received
        socket2.getLock().unlock();
        // wait until the link is closed
        waitUntil(() -> lm2.isUnlinked(sid1));
        assert lm1.isUnlinked(sid2);
    }

    // socket1 and socket2 send link requests simultaneously
    @Test
    void simultaneousLinkRequestButCompatible() throws InterruptedException {
        // Acquire both locks to prevent handling of LINK message
        // before sending the LINK message themselves
        socket1.getLock().lock();
        socket2.getLock().lock();
        // make sockets request link with each other
        lm1.link(sid2);
        lm2.link(sid1);
        // assert both are linking
        assert lm1.isLinking(sid2);
        assert lm2.isLinking(sid1);
        // release lock so that both can establish the link
        socket1.getLock().unlock();
        socket2.getLock().unlock();
        waitUntil(() -> lm1.isLinked(sid2));
        waitUntil(() -> lm2.isLinked(sid1));
        // assert both are unlinked
        assert lm1.isLinked(sid2);
        assert lm2.isLinked(sid1);
    }

    @Test
    void simultaneousLinkRequestButNotCompatible() throws InterruptedException {
        // socket1 and socket2 send link requests simultaneously
        // but are not compatible
        socket2.setProtocol(incompatibleProtocol);
        socket2.setCompatProtocols(Set.of(incompatibleProtocol));
        // Acquire both locks to prevent handling of LINK message
        // before sending the LINK message themselves
        socket1.getLock().lock();
        socket2.getLock().lock();
        // make sockets request link with each other
        lm1.link(sid2);
        lm2.link(sid1);
        // assert both are linking
        assert lm1.isLinking(sid2);
        assert lm2.isLinking(sid1);
        // release lock so that both can close the link
        socket1.getLock().unlock();
        socket2.getLock().unlock();
        waitUntil(() -> lm1.isUnlinked(sid2));
        waitUntil(() -> lm2.isUnlinked(sid1));
        // assert both are unlinked
        assert lm1.isUnlinked(sid2);
        assert lm2.isUnlinked(sid1);
    }

    /**
     * There is always a socket that gets unlinked first.
     * This socket is able to request a new linking process
     * while the other link has not yet received the UNLINK
     * message to close the link. This test explores a scenario
     * where a new LINK msg arrives before the UNLINK msg.
     * The link should be established in the end since the new LINK
     * msg carries a clock identifier superior to the clock identifier
     * associated with the closing link.
     */
    @Test
    void handleNewLinkMsgBeforeUnlinkMsgArrives() throws InterruptedException {
        // establish the link between the two
        lm1.link(sid2);
        waitUntil(() -> lm2.isLinked(sid1));
        assert lm1.isLinked(sid2);
        // change socket2's message dispatcher to
        // catch the UNLINK msg and store it
        // in an atomic reference so that it can be
        // sent at any time. The message dispatcher
        // is also made to count the amount of LINK
        // msgs that have been sent.
        AtomicReference<SocketMsg> unlinkMsg = new AtomicReference<>(null);
        AtomicInteger linkMsgsSent = new AtomicInteger(0);
        socket2.setCoreComponents(new DirectDispatcherToLinkManager(lm1){
            @Override
            public void dispatch(SocketMsg msg) {
                if(msg.getType() == MsgType.UNLINK)
                    unlinkMsg.set(msg);
                else {
                    if (msg.getType() == MsgType.LINK)
                        linkMsgsSent.incrementAndGet();
                    super.dispatch(msg);
                }
            }
        }, null);

        // acquire socket1's lock so that before any message
        // sent by socket2 is handled, we can verify that
        // the socket2 has closed the link with socket1
        socket1.getLock().lock();
        // socket1 initiates the unlinking process
        // so that socket2 can close the link on
        // its side right after returning an UNLINK msg
        // and so enabling socket2 to request a new link
        // with socket1 before the UNLINK msg is delivered to socket1
        lm1.unlink(sid2);
        // wait for the socket2 to close the link
        waitUntil(() -> lm2.isUnlinked(sid1));
        // assert the UNLINK message was catched
        assert unlinkMsg.get() != null;
        // make socket2 send a link request to socket1
        lm2.link(sid1);
        // assert socket2 is in LINKING state and
        // socket1 is in UNLINKING state
        assert lm2.isLinking(sid1);
        assert lm1.isUnlinking(sid2);
        // release socket1's lock so that the LINK message
        socket1.getLock().unlock();
        // wait for a link message to be sent by socket2
        waitUntil(() -> linkMsgsSent.get() > 0);
        // deliver the UNLINK message sent by socket2 to the socket1
        scheduler.execute(() -> lm1.handleMsg(unlinkMsg.get()));
        // wait for the link to be established
        waitUntil(() -> lm1.isLinked(sid2));
        assert lm2.isLinked(sid1);
    }

    @Test
    void receivePositiveLinkAckMsgWhenCancelling() throws InterruptedException {
        // Acquire both locks to prevent handling of LINK message
        // before sending the LINK message themselves
        socket1.getLock().lock();
        socket2.getLock().lock();
        // make socket1 send a link request and invoke unlink() after that
        lm1.link(sid2);
        lm1.unlink(sid2);
        // make socket2 send a link request
        lm2.link(sid1);
        // assert that socket2 is in a LINKING state
        assert lm2.isLinking(sid1);
        // assert that socket1 is in CANCELLING state
        assert lm1.isLinkState(sid2, ls -> ls == LinkNew.LinkState.CANCELLING);
        // release socket2 to enable the handling of the LINK msg
        socket2.getLock().unlock();
        // release socket1 lock and check that eventually
        // the link is closed
        socket1.getLock().unlock();
        waitUntil(() -> lm2.isUnlinked(sid1));
        assert lm1.isUnlinked(sid2);
    }

    @Test
    void receiveNegativeLinkAckMsgWhenCancelling() throws InterruptedException {
        // make socket2 incompatible with socket1
        socket2.setProtocol(incompatibleProtocol);
        socket2.setCompatProtocols(Set.of(incompatibleProtocol));
        // lock socket2 to prevent handling of LINK msg
        // sent by socket1
        socket2.getLock().lock();
        // assert that after invoking link() followed by unlink(),
        // socket1 is in CANCELLING state
        lm1.link(sid2);
        lm1.unlink(sid2);
        assert lm1.isLinkState(sid2, ls -> ls == LinkNew.LinkState.CANCELLING);
        // acquire socket1 lock
        socket1.getLock().lock();
        // release socket2 to enable the handling of the LINK msg
        socket2.getLock().unlock();
        // wait a bit for the negative LINKACK message to be sent to socket1
        Thread.sleep(50);
        // assert socket1 is still in CANCELLING state
        assert lm1.isLinkState(sid2, ls -> ls == LinkNew.LinkState.CANCELLING);
        // release socket1 lock and check that eventually
        // the link is closed
        socket1.getLock().unlock();
        waitUntil(() -> lm1.isUnlinked(sid2));
        assert lm2.isUnlinked(sid1);
    }

    @Test
    void fatalRefusalDueToNotAllowingIncomingRequests() throws InterruptedException {
        // make socket2 reject incoming link requests
        socket2.setOption("allowIncomingLinkRequests", false);
        socket2.getLock().lock();
        // make socket1 attempt to link with socket2
        lm1.link(sid2);
        // verify that socket1 is attempting to link
        assert lm1.isLinking(sid2);
        // let socket2 handle the link request and
        // verify that the link is closed
        socket2.getLock().unlock();
        waitUntil(() -> lm1.isUnlinked(sid2));
        // assert that socket2 can initiate links
        lm2.link(sid1);
        waitUntil(() -> lm1.isLinked(sid2));
        assert lm2.isLinked(sid1);
    }

    @Test
    void receiveUnlinkMsgWhenInLinkingState() throws InterruptedException {
        // change socket1 dispatcher to catch the LINKACK message,
        // and inform when an UNLINK message has been sent
        AtomicReference<SocketMsg> linkackMsg = new AtomicReference<>(null);
        AtomicInteger unlinkMsgsSent = new AtomicInteger(0);
        socket1.setCoreComponents(new DirectDispatcherToLinkManager(lm2){
            @Override
            public void dispatch(SocketMsg msg) {
                if(msg.getType() == MsgType.LINKACK)
                    linkackMsg.set(msg);
                else {
                    if (msg.getType() == MsgType.UNLINK)
                        unlinkMsgsSent.incrementAndGet();
                    super.dispatch(msg);
                }
            }
        }, null);
        // make socket1 send link request to socket2
        lm1.link(sid2);
        // wait until socket1 is linked
        waitUntil(() -> lm1.isLinked(sid2));
        // assert socket2 is LINKING
        assert lm2.isLinking(sid1);
        // make socket1 unlink
        lm1.unlink(sid2);
        // If an UNLINK message is received when in LINKING state,
        // the socket that received the UNLINK message is assumed
        // to have already received the peer's metadata and sent a
        // successful answer. So, when the UNLINK message is received,
        // it can interpret it as a reason to send an UNLINK message
        // and close the link.
        waitUntil(() -> lm1.isUnlinked(sid2));
        // Feed the LINKACK msg, wait for it to be done,
        // and check that the positive LINKACK message had no
        // effect since the link was already closed.
        var s = scheduler.schedule(() -> lm1.handleMsg(linkackMsg.getAndSet(null)),0,TimeUnit.MILLISECONDS);
        waitUntil(s::isDone);
        // wait until the link is closed on both sides
        assert lm1.isUnlinked(sid2);
        assert lm2.isUnlinked(sid1);
    }

    @Test
    void receiveUnlinkMsgWhenCancelling() throws InterruptedException {
        // change socket1 dispatcher to catch the LINKACK message,
        // and inform when an UNLINK message has been sent
        AtomicReference<SocketMsg> linkackMsg = new AtomicReference<>(null);
        AtomicInteger unlinkMsgsSent = new AtomicInteger(0);
        socket1.setCoreComponents(new DirectDispatcherToLinkManager(lm2){
            @Override
            public void dispatch(SocketMsg msg) {
                if(msg.getType() == MsgType.LINKACK)
                    linkackMsg.set(msg);
                else {
                    if (msg.getType() == MsgType.UNLINK)
                        unlinkMsgsSent.incrementAndGet();
                    super.dispatch(msg);
                }
            }
        }, null);
        // make sockets send link request to each other
        lm1.link(sid2);
        lm2.link(sid1);
        // wait until socket1 is linked
        waitUntil(() -> lm1.isLinked(sid2));
        // assert socket2 is LINKING
        assert lm2.isLinking(sid1);
        // make socket2 change to a CANCELLING state by invoking unlink()
        lm2.unlink(sid1);
        assert lm2.isLinkState(sid1, ls -> ls == LinkNew.LinkState.CANCELLING);
        // make socket1 change an UNLINK msg
        lm1.unlink(sid2);
        // If an UNLINK message is received when in CANCELLING state,
        // the socket that received the UNLINK message is assumed
        // to have already received the peer's metadata and sent a
        // successful answer. So, when the UNLINK message is received,
        // it can interpret it as a reason to send an UNLINK message
        // and close the link.
        waitUntil(() -> lm1.isUnlinked(sid2));
        // Feed the LINKACK msg, wait for it to be done,
        // and check that the positive LINKACK message had no
        // effect since the link was already closed.
        var s = scheduler.schedule(() -> lm1.handleMsg(linkackMsg.getAndSet(null)),0,TimeUnit.MILLISECONDS);
        waitUntil(s::isDone);
        // wait until the link is closed on both sides
        assert lm1.isUnlinked(sid2);
        assert lm2.isUnlinked(sid1);
    }

    @Test
    void simultaneousLinkRequestsAndReceiveUnlinkMsgWhenInLinkingState() throws InterruptedException {
        // change socket1 dispatcher to catch the LINKACK message,
        // and inform when an UNLINK message has been sent
        AtomicReference<SocketMsg> linkackMsg = new AtomicReference<>(null);
        AtomicInteger unlinkMsgsSent = new AtomicInteger(0);
        socket1.setCoreComponents(new DirectDispatcherToLinkManager(lm2){
            @Override
            public void dispatch(SocketMsg msg) {
                if(msg.getType() == MsgType.LINKACK)
                    linkackMsg.set(msg);
                else {
                    if (msg.getType() == MsgType.UNLINK)
                        unlinkMsgsSent.incrementAndGet();
                    super.dispatch(msg);
                }
            }
        }, null);
        // acquire both locks to enable simultaneous link requests
        socket1.getLock().lock();
        socket2.getLock().lock();
        // make both sockets send link requets
        lm1.link(sid2);
        lm2.link(sid1);
        // release locks
        socket1.getLock().unlock();
        socket2.getLock().unlock();
        // wait until socket1 is linked
        waitUntil(() -> lm1.isLinked(sid2));
        // assert socket2 is LINKING
        assert lm2.isLinking(sid1);
        // make socket1 unlink
        lm1.unlink(sid2);
        // If an UNLINK message is received when in LINKING state,
        // the socket that received the UNLINK message is assumed
        // to have already received the peer's metadata and sent a
        // successful answer. So, when the UNLINK message is received,
        // it can interpret it as a reason to send an UNLINK message
        // and close the link.
        waitUntil(() -> lm1.isUnlinked(sid2));
        // Feed the LINKACK msg, wait for it to be done,
        // and check that the positive LINKACK message had no
        // effect since the link was already closed.
        var s = scheduler.schedule(() -> lm1.handleMsg(linkackMsg.getAndSet(null)),0,TimeUnit.MILLISECONDS);
        waitUntil(s::isDone);
        // wait until the link is closed on both sides
        assert lm1.isUnlinked(sid2);
        assert lm2.isUnlinked(sid1);
    }

    /*
    TODO - missing tests:
        1. receive LinkAck without metadata when cancelling and waiting for peer's metadata
        2. schedule link request when receiving linkack message when LINKING and not waiting for metadata
        3. receive linkack without metadata when waiting for metadata
        4. receive link msg after linkack msg without metadata and with successful code
        5. receive link msg after linkack msg without metadata and with fatal code
        6. receive link msg after linkack msg without metadata and with non-fatal code
        7. receive data/control msg to establish link
        8. scheduled related methods
            - send linkack msg with metadata and success code when peer is compatible and link request was scheduled
            - send linkack msg with metadata and fatal code when peer is not compatible and link request was scheduled
     */

    @Test
    void maxLinksDoesNotAllowLinking(){
        try{
            // set limit of links of socket1 to 0,
            // so that an exception occurs when attempting
            // to establish a link
            socket1.setOption("maxLinks",0);
            lm1.link(sid2);
            assert false; // should not get here due to an exception being thrown by link()
        }catch (Exception ignored){}
    }

    @Test
    void nullCannotBeUsedForLinkingOperations(){
        try{
            lm1.link(null);
            assert false; // should not get here
        }catch (Exception ignored){}

        try{
            lm1.unlink(null);
            assert false; // should not get here
        }catch (Exception ignored){}
    }
}