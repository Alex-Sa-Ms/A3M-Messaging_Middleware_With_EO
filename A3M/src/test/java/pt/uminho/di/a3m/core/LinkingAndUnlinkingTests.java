package pt.uminho.di.a3m.core;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.core.messaging.*;
import pt.uminho.di.a3m.core.messaging.payloads.BytePayload;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

class LinkingAndUnlinkingTests {
    Protocol protocol;
    Protocol incompatibleProtocol;
    SocketIdentifier sid1, sid2;
    DummySocket socket1, socket2;
    LinkManager lm1, lm2;
    ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);

    private void dispatchTo(LinkManager destLm, SocketMsg msg){
        scheduler.execute(() -> destLm.handleMsg(msg));
        try {
            System.out.println(LinkManager.linkRelatedMsgToString(msg));
        } catch (InvalidProtocolBufferException e) {
            System.out.println("Exception parsing msg{" +
                    "src=" + msg.getSrcId() + ", dest=" + msg.getDestId()
                    + ", type=" + Byte.toUnsignedInt(msg.getType()) + ", clockId=" + msg.getClockId() +
                    ", payload=" + StandardCharsets.UTF_8.decode(ByteBuffer.wrap(msg.getPayload())) + "}");
        }
        System.out.flush();
    }


    private class DirectDispatcherToLinkManager implements MessageDispatcher{
        private final Map<SocketIdentifier,LinkManager> lms = new ConcurrentHashMap<>(); // link managers
        public DirectDispatcherToLinkManager() {}
        public DirectDispatcherToLinkManager(SocketIdentifier sid, LinkManager lm) {
            registerLinkManager(sid, lm);
        }

        public void registerLinkManager(SocketIdentifier sid, LinkManager lm){
            lms.put(sid, lm);
        }

        @Override
        public void dispatch(Msg msg) {
            LinkingAndUnlinkingTests.this.dispatchTo(lms.get(((SocketMsg) msg).getDestId()), (SocketMsg) msg);
        }

        @Override
        public AtomicReference<Msg> scheduleDispatch(Msg msg, long dispatchTime) {
            AtomicReference<Msg> ref = new AtomicReference<>(msg);
            long delay = Math.max(0L, dispatchTime - System.currentTimeMillis());
            scheduler.schedule(() -> {
                SocketMsg m = (SocketMsg) ref.getAndSet(null);
                if(m != null) {
                    this.dispatch(m);
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
        lm1 = socket1.linkManager;
        // create socket and link manager 2
        sid2 = new SocketIdentifier("NodeB", "SocketB");
        socket2 = new DummySocket(sid2, protocol);
        lm2 = socket2.linkManager;
        // create message dispatcher
        DirectDispatcherToLinkManager messageDispatcher = new DirectDispatcherToLinkManager();
        messageDispatcher.registerLinkManager(sid1, lm1);
        messageDispatcher.registerLinkManager(sid2, lm2);
        // set custom message dispatcher to send socket1 messages
        // directly to the socket2's link manager
        socket1.setCoreComponents(messageDispatcher, null);
        // set custom message dispatcher to send socket2 messages
        // directly to the socket1's link manager
        socket2.setCoreComponents(messageDispatcher, null);
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
        // required LINKREPLY msg after the initiator
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
    void linkAndUnlinkWithSelf() throws InterruptedException {
        lm1.link(sid1);
        waitUntil(() -> lm1.isLinked(sid1));
        lm1.unlink(sid1);
        waitUntil(() -> lm1.isUnlinked(sid1));
    }

    @Test
    void linkSchedulingAfterNonFatalRefusal() throws InterruptedException {
        // set socket2's max links to 0, so that the socket1
        // link request can be refused with a non-fatal reason
        socket2.setOption("maxLinks",0);
        // make socket1 send link request to socket2
        lm1.link(sid2);
        // wait until there is a scheduled message
        Link link = lm1.links.get(sid2);
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
    void closeLinkDueToFatalReasonAfterRejectingNonFatal() throws InterruptedException {
        // let socket2 perceive socket1 has compatible,
        // but make socket1 perceive socket2 as incompatible to
        // simulate fatal refusal
        socket1.setCompatProtocols(Collections.singleton(incompatibleProtocol));
        // set socket2's max links to 0, so that the socket1
        // link request can be refused with a non-fatal reason
        socket2.setOption("maxLinks",0);
        // make socket1 send link request to socket2
        lm1.link(sid2);
        // check that sockets do not link
        waitUntil(()->lm1.isUnlinked(sid2));
        assert lm2.isUnlinked(sid1);
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
        Link link = lm1.links.get(sid2);
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
            // lm2's lock is held here to prevent the handling
            // of the unlink message and therefore ensure the
            // success on throwing the exception
            lm2.writeLock().lock();
            lm1.unlink(sid2);
            try {
                lm1.link(sid2);
                assert false;
            } catch (Exception ignored) {}
        }
        finally {
            lm2.writeLock().unlock();
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
            // acquire lm2's lock to prevent handling
            // of the LINK message by socket2 before
            // asserting that the socket1's link state
            // with socket2 is LINKING
            lm2.writeLock().lock();
            lm1.link(sid2);
            assert lm1.isLinking(sid2);
        } finally {
            lm2.writeLock().unlock();
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
            // acquire lm2's lock to prevent the UNLINK
            // message from being handled until the UNLINK
            // state is asserted
            lm2.writeLock().lock();
            lm1.unlink(sid2);
            assert lm1.isLinkState(sid2,ls -> ls == LinkState.UNLINKING);
        } finally {
            lm2.writeLock().unlock();
        }
        // wait for the unlink process to finish
        waitUntil(() -> lm1.isUnlinked(sid2));
        assert lm2.isUnlinked(sid1);
    }

    @Test
    void cancelOngoingLinkingProcess() throws InterruptedException {
        try{
            // acquire lm2's lock to prevent the LINK
            // message from being handled before requesting
            // the unlink and before verifying that
            // socket1's state becomes CANCELLING
            lm2.writeLock().lock();
            lm1.link(sid2);
            assert lm1.isLinking(sid2);
            lm1.unlink(sid2);
            assert lm1.isLinkState(sid2,ls -> ls == LinkState.CANCELLING);
        } finally {
            lm2.writeLock().unlock();
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
        // Acquire lm1's lock to ensure the LINKREPLY
        // message coming from socket2 is not handled
        // until the lm2's lock is acquired after
        // socket2 sends a positive LINKREPLY message
        lm1.writeLock().lock();
        // make socket1 send a link request to socket2
        lm1.link(sid2);
        // wait until socket2 is in LINKING state to
        // wait for socket1's answer
        waitUntil(() -> lm2.isLinking(sid1));
        // acquire lm2's lock so that the fatal LINKREPLY
        // msg sent by socket1 is not handled until
        // we assert socket1 has closed the link
        lm2.writeLock().lock();
        // release lm1's lock so that the LINKREPLY msg can be handled
        lm1.writeLock().unlock();
        // wait for socket1 to close the link
        waitUntil(() -> lm1.isUnlinked(sid2));
        // release lm2's lock so that the fatal LINKREPLY msg
        // from socket1 can be received
        lm2.writeLock().unlock();
        // wait until the link is closed
        waitUntil(() -> lm2.isUnlinked(sid1));
        assert lm1.isUnlinked(sid2);
    }

    // socket1 and socket2 send link requests simultaneously
    @Test
    void simultaneousLinkRequestButCompatible() throws InterruptedException {
        // Acquire both locks to prevent handling of LINK message
        // before sending the LINK message themselves
        lm1.writeLock().lock();
        lm2.writeLock().lock();
        // make sockets request link with each other
        lm1.link(sid2);
        lm2.link(sid1);
        // assert both are linking
        assert lm1.isLinking(sid2);
        assert lm2.isLinking(sid1);
        // release lock so that both can establish the link
        lm1.writeLock().unlock();
        lm2.writeLock().unlock();
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
        lm1.writeLock().lock();
        lm2.writeLock().lock();
        // make sockets request link with each other
        lm1.link(sid2);
        lm2.link(sid1);
        // assert both are linking
        assert lm1.isLinking(sid2);
        assert lm2.isLinking(sid1);
        // release lock so that both can close the link
        lm1.writeLock().unlock();
        lm2.writeLock().unlock();
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
        socket2.setCoreComponents(new DirectDispatcherToLinkManager(sid1, lm1){
            @Override
            public void dispatch(Msg msg) {
                if(msg.getType() == MsgType.UNLINK)
                    unlinkMsg.set((SocketMsg) msg);
                else {
                    super.dispatch(msg);
                    if (msg.getType() == MsgType.LINK)
                        linkMsgsSent.incrementAndGet();
                }
            }
        }, null);

        // acquire lm1's lock so that before any message
        // sent by socket2 is handled, we can verify that
        // the socket2 has closed the link with socket1
        lm1.writeLock().lock();
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
        // release lm1's lock so that the LINK message
        lm1.writeLock().unlock();
        // wait for a link message to be sent by socket2
        waitUntil(() -> linkMsgsSent.get() > 0);
        // deliver the UNLINK message sent by socket2 to the socket1
        scheduler.execute(() -> dispatchTo(lm1, unlinkMsg.get()));
        // wait for the link to be established
        waitUntil(() -> lm1.isLinked(sid2));
        assert lm2.isLinked(sid1);
    }

    @Test
    void receivePositiveLinkReplyMsgWhenCancelling() throws InterruptedException {
        // Acquire both locks to prevent handling of LINK message
        // before sending the LINK message themselves
        lm1.writeLock().lock();
        lm2.writeLock().lock();
        // make socket1 send a link request and invoke unlink() after that
        lm1.link(sid2);
        lm1.unlink(sid2);
        // make socket2 send a link request
        lm2.link(sid1);
        // assert that socket2 is in a LINKING state
        assert lm2.isLinking(sid1);
        // assert that socket1 is in CANCELLING state
        assert lm1.isLinkState(sid2, ls -> ls == LinkState.CANCELLING);
        // release socket2 to enable the handling of the LINK msg
        lm2.writeLock().unlock();
        // release lm1's lock and check that eventually
        // the link is closed
        lm1.writeLock().unlock();
        waitUntil(() -> lm2.isUnlinked(sid1));
        assert lm1.isUnlinked(sid2);
    }

    @Test
    void receiveNegativeLinkReplyMsgWhenCancelling() throws InterruptedException {
        // make socket2 incompatible with socket1
        socket2.setProtocol(incompatibleProtocol);
        socket2.setCompatProtocols(Set.of(incompatibleProtocol));
        // lock lm2 to prevent handling of LINK msg
        // sent by socket1
        lm2.writeLock().lock();
        // assert that after invoking link() followed by unlink(),
        // socket1 is in CANCELLING state
        lm1.link(sid2);
        lm1.unlink(sid2);
        assert lm1.isLinkState(sid2, ls -> ls == LinkState.CANCELLING);
        // acquire lm1's lock
        lm1.writeLock().lock();
        // release socket2 to enable the handling of the LINK msg
        lm2.writeLock().unlock();
        // wait a bit for the negative LINKREPLY message to be sent to socket1
        Thread.sleep(50);
        // assert socket1 is still in CANCELLING state
        assert lm1.isLinkState(sid2, ls -> ls == LinkState.CANCELLING);
        // release lm1's lock and check that eventually
        // the link is closed
        lm1.writeLock().unlock();
        waitUntil(() -> lm1.isUnlinked(sid2));
        assert lm2.isUnlinked(sid1);
    }

    @Test
    void fatalRefusalDueToNotAllowingIncomingRequests() throws InterruptedException {
        // make socket2 reject incoming link requests
        socket2.setOption("allowIncomingLinkRequests", false);
        lm2.writeLock().lock();
        // make socket1 attempt to link with socket2
        lm1.link(sid2);
        // verify that socket1 is attempting to link
        assert lm1.isLinking(sid2);
        // let socket2 handle the link request and
        // verify that the link is closed
        lm2.writeLock().unlock();
        waitUntil(() -> lm1.isUnlinked(sid2));
        // assert that socket2 can initiate links
        lm2.link(sid1);
        waitUntil(() -> lm1.isLinked(sid2));
        assert lm2.isLinked(sid1);
    }

    @Test
    void receiveUnlinkMsgWhenInLinkingState() throws InterruptedException {
        // change socket1 dispatcher to catch the LINKREPLY message,
        // and inform when an UNLINK message has been sent
        AtomicReference<SocketMsg> linkreplyMsg = new AtomicReference<>(null);
        AtomicInteger unlinkMsgsSent = new AtomicInteger(0);
        socket1.setCoreComponents(new DirectDispatcherToLinkManager(sid2, lm2){
            @Override
            public void dispatch(Msg msg) {
                if(msg.getType() == MsgType.LINKREPLY)
                    linkreplyMsg.set((SocketMsg) msg);
                else {
                    super.dispatch(msg);
                    if (msg.getType() == MsgType.UNLINK)
                        unlinkMsgsSent.incrementAndGet();
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
        // Feed the LINKREPLY msg, wait for it to be done,
        // and check that the positive LINKREPLY message had no
        // effect since the link was already closed.
        var s = scheduler.schedule(() -> dispatchTo(lm1, linkreplyMsg.getAndSet(null)),0,TimeUnit.MILLISECONDS);
        waitUntil(s::isDone);
        // wait until the link is closed on both sides
        assert lm1.isUnlinked(sid2);
        assert lm2.isUnlinked(sid1);
    }

    @Test
    void receiveUnlinkMsgWhenCancelling() throws InterruptedException {
        // change socket1 dispatcher to catch the LINKREPLY message,
        // and inform when an UNLINK message has been sent
        AtomicReference<SocketMsg> linkreplyMsg = new AtomicReference<>(null);
        AtomicInteger unlinkMsgsSent = new AtomicInteger(0);
        socket1.setCoreComponents(new DirectDispatcherToLinkManager(sid2, lm2){
            @Override
            public void dispatch(Msg msg) {
                if(msg.getType() == MsgType.LINKREPLY)
                    linkreplyMsg.set((SocketMsg) msg);
                else {
                    super.dispatch(msg);
                    if (msg.getType() == MsgType.UNLINK)
                        unlinkMsgsSent.incrementAndGet();
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
        assert lm2.isLinkState(sid1, ls -> ls == LinkState.CANCELLING);
        // make socket1 change an UNLINK msg
        lm1.unlink(sid2);
        // If an UNLINK message is received when in CANCELLING state,
        // the socket that received the UNLINK message is assumed
        // to have already received the peer's metadata and sent a
        // successful answer. So, when the UNLINK message is received,
        // it can interpret it as a reason to send an UNLINK message
        // and close the link.
        waitUntil(() -> lm1.isUnlinked(sid2));
        // Feed the LINKREPLY msg, wait for it to be done,
        // and check that the positive LINKREPLY message had no
        // effect since the link was already closed.
        var s = scheduler.schedule(() -> dispatchTo(lm2, linkreplyMsg.getAndSet(null)),0,TimeUnit.MILLISECONDS);
        waitUntil(s::isDone);
        // wait until the link is closed on both sides
        assert lm1.isUnlinked(sid2);
        assert lm2.isUnlinked(sid1);
    }

    @Test
    void simultaneousLinkRequestsAndReceiveUnlinkMsgWhenInLinkingState() throws InterruptedException {
        // change socket1 dispatcher to catch the LINKREPLY message,
        // and inform when an UNLINK message has been sent
        AtomicReference<SocketMsg> linkreplyMsg = new AtomicReference<>(null);
        AtomicInteger unlinkMsgsSent = new AtomicInteger(0);
        socket1.setCoreComponents(new DirectDispatcherToLinkManager(sid2, lm2){
            @Override
            public void dispatch(Msg msg) {
                if(msg.getType() == MsgType.LINKREPLY)
                    linkreplyMsg.set((SocketMsg) msg);
                else {
                    super.dispatch(msg);
                    if (msg.getType() == MsgType.UNLINK)
                        unlinkMsgsSent.incrementAndGet();
                }
            }
        }, null);
        // acquire both locks to enable simultaneous link requests
        lm1.writeLock().lock();
        lm2.writeLock().lock();
        // make both sockets send link requets
        lm1.link(sid2);
        lm2.link(sid1);
        // release locks
        lm1.writeLock().unlock();
        lm2.writeLock().unlock();
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
        // Feed the LINKREPLY msg, wait for it to be done,
        // and check that the positive LINKREPLY message had no
        // effect since the link was already closed.
        var s = scheduler.schedule(() -> dispatchTo(lm2, linkreplyMsg.getAndSet(null)),0,TimeUnit.MILLISECONDS);
        waitUntil(s::isDone);
        // wait until the link is closed on both sides
        assert lm1.isUnlinked(sid2);
        assert lm2.isUnlinked(sid1);
    }


    @Test
    void receiveLinkReplyMsgWhenWaitingMetadata() throws InterruptedException {
        // change socket1 dispatcher to catch the LINK message,
        // and inform when a LINKREPLY message has been sent
        AtomicReference<SocketMsg> linkMsg = new AtomicReference<>(null);
        AtomicInteger linkReplyMsgsSent = new AtomicInteger(0);
        socket1.setCoreComponents(new DirectDispatcherToLinkManager(sid2, lm2){
            @Override
            public void dispatch(Msg msg) {
                if(msg.getType() == MsgType.LINK)
                    linkMsg.set((SocketMsg) msg);
                else {
                    super.dispatch(msg);
                    if (msg.getType() == MsgType.LINKREPLY)
                        linkReplyMsgsSent.incrementAndGet();
                }
            }
        }, null);
        // acquire lm1's lock to prevent immediate handling of socket2's LINK msg
        lm1.writeLock().lock();
        // make sockets send link request to each other
        lm1.link(sid2);
        lm2.link(sid1);
        // assert both sockets are LINKING
        assert lm1.isLinking(sid2);
        assert lm2.isLinking(sid1);
        // acquire lm2's lock to prevent immediate handling of socket1's LINKREPLY msg
        lm2.writeLock().lock();
        // release lm1's lock to let it answer socket2's link request
        lm1.writeLock().unlock();
        // wait until socket1 has sent the LINKREPLY msg (without metadata)
        waitUntil(() -> linkReplyMsgsSent.get() > 0);
        // assert socket1 is still LINKING
        assert lm1.isLinking(sid2);
        // release lm2's lock, and check that
        // both sockets are still LINKING
        lm2.writeLock().unlock();
        Thread.sleep(50);
        assert lm1.isLinking(sid2);
        assert lm2.isLinking(sid1);
        // Feed the socket1's LINK msg to socket2
        // and check that the sockets eventually link
        scheduler.schedule(() -> dispatchTo(lm2, linkMsg.getAndSet(null)),0,TimeUnit.MILLISECONDS);
        waitUntil(() -> lm1.isLinked(sid2));
        assert lm2.isLinked(sid1);
    }

    @Test
    void receiveLinkReplyMsgWhenCancellingAndWaitingMetadata() throws InterruptedException {
        // change socket1 dispatcher to catch the LINK message,
        // and inform when a LINKREPLY message has been sent
        AtomicReference<SocketMsg> linkMsg = new AtomicReference<>(null);
        AtomicInteger linkReplyMsgsSent = new AtomicInteger(0);
        socket1.setCoreComponents(new DirectDispatcherToLinkManager(sid2, lm2){
            @Override
            public void dispatch(Msg msg) {
                if(msg.getType() == MsgType.LINK)
                    linkMsg.set((SocketMsg) msg);
                else {
                    super.dispatch(msg);
                    if (msg.getType() == MsgType.LINKREPLY)
                        linkReplyMsgsSent.incrementAndGet();
                }
            }
        }, null);
        // acquire lm1's lock to prevent immediate handling of socket2's LINK msg
        lm1.writeLock().lock();
        // make sockets send link request to each other
        lm1.link(sid2);
        lm2.link(sid1);
        // invoke unlink() on socket2 to make it go into CANCELLING state
        lm2.unlink(sid1);
        // assert socket1 is LINKING and socket2 is CANCELLING
        assert lm1.isLinking(sid2);
        assert lm2.isLinkState(sid1, ls -> ls == LinkState.CANCELLING);
        System.out.println("----------1----------");
        // acquire lm2's lock to prevent immediate handling of socket1's LINKREPLY msg
        lm2.writeLock().lock();
        // release lm1's lock to let it answer socket2's link request
        lm1.writeLock().unlock();
        // wait until socket1 has sent the LINKREPLY msg (without metadata)
        waitUntil(() -> linkReplyMsgsSent.get() > 0);
        System.out.println("-----------2---------");
        // assert socket1 is still LINKING
        assert lm1.isLinking(sid2);
        // release lm2's lock, wait a bit and check that
        // socket is still LINKING and socket2 is still CANCELLING.
        // socket1 requires a LINKREPLY message, and socket2 requires
        // a LINK message to send the LINKREPLY message that socket1 requires.
        lm2.writeLock().unlock();
        Thread.sleep(50);
        assert lm1.isLinking(sid2);
        assert lm2.isLinkState(sid1, ls -> ls == LinkState.CANCELLING);
        // Feed the socket1's LINK msg to socket2
        // and check that the link is eventually
        // closed on both sides.
        scheduler.schedule(() -> dispatchTo(lm2,linkMsg.getAndSet(null)),0,TimeUnit.MILLISECONDS);
        waitUntil(() -> lm1.isUnlinked(sid2));
        System.out.println("----------3----------");
        assert lm2.isUnlinked(sid1);
    }

    /** Receive link msg after linkreply msg with successful code and without metadata. */
    @Test
    void receiveLinkMsgAfterLinkreplyWithSuccessCode() throws InterruptedException {
        // change socket2 dispatcher to catch the LINK message,
        // and inform when a LINKREPLY message has been sent
        AtomicReference<SocketMsg> linkMsg = new AtomicReference<>(null);
        AtomicInteger linkReplyMsgsSent = new AtomicInteger(0);
        socket2.setCoreComponents(new DirectDispatcherToLinkManager(sid1, lm1){
            @Override
            public void dispatch(Msg msg) {
                if(msg.getType() == MsgType.LINK)
                    linkMsg.set((SocketMsg) msg);
                else {
                    super.dispatch(msg);
                    if (msg.getType() == MsgType.LINKREPLY)
                        linkReplyMsgsSent.incrementAndGet();
                }
            }
        }, null);
        // make both sockets send link requests
        lm2.link(sid1);
        lm1.link(sid2);
        // since socket2's link request is intercepted,
        // socket1 will receive a LINKREPLY with a positive
        // answer and without metadata, before receiving
        // a LINK message.
        waitUntil(() -> linkReplyMsgsSent.get() > 0);
        // after the LINKREPLY msg is sent by socket2
        // the LINK msg can be sent
        var task = scheduler.schedule(() -> dispatchTo(lm1,linkMsg.get()),0,TimeUnit.MILLISECONDS);
        waitUntil(task::isDone);
        // check the link is eventually established
        waitUntil(() -> lm2.isLinked(sid1));
        waitUntil(() -> lm1.isLinked(sid2));
    }

    /** Receive link msg after linkreply msg with fatal code and without metadata. */
    @Test
    void receiveLinkMsgAfterLinkreplyWithFatalCode() throws InterruptedException {
        // make socket2 perceive socket1 as incompatible
        socket2.setCompatProtocols(Set.of(incompatibleProtocol));
        // change socket2 dispatcher to catch the LINK message,
        // and inform when a LINKREPLY message has been sent
        AtomicReference<SocketMsg> linkMsg = new AtomicReference<>(null);
        AtomicInteger linkReplyMsgsSent = new AtomicInteger(0);
        socket2.setCoreComponents(new DirectDispatcherToLinkManager(sid1, lm1){
            @Override
            public void dispatch(Msg msg) {
                if(msg.getType() == MsgType.LINK)
                    linkMsg.set((SocketMsg) msg);
                else {
                    super.dispatch(msg);
                    if (msg.getType() == MsgType.LINKREPLY)
                        linkReplyMsgsSent.incrementAndGet();
                }
            }
        }, null);
        // make both sockets send link requests
        lm2.link(sid1);
        lm1.link(sid2);
        // since socket2's link request is intercepted,
        // socket1 will receive a LINKREPLY with a positive
        // answer and without metadata, before receiving
        // a LINK message.
        waitUntil(() -> linkReplyMsgsSent.get() > 0);
        // after the LINKREPLY msg is sent by socket2
        // the LINK msg can be sent
        scheduler.schedule(() -> dispatchTo(lm1, linkMsg.get()),0,TimeUnit.MILLISECONDS);
        // check the link is not established
        waitUntil(() -> lm2.isUnlinked(sid1));
        waitUntil(() -> lm1.isUnlinked(sid2));
    }

    /**
     * Receive link msg after linkreply msg with non-fatal code and without metadata.
     * This test requires simultaneous link requests because for a LINKREPLY message
     * to not carry metadata, the socket must have sent metadata already.
     * Because the only non-fatal answer currently implemented is related to reaching
     * the maximum number of links, this test cannot be performed as this condition
     * does not matter when the link has already been created.
     *
     * So, if a new non-fatal condition arises that enables this test, then set
     * the conditions in the proper spaces below.
     */
    /*@Test
    void receiveLinkMsgAfterLinkreplyWithNonFatalCode() throws InterruptedException {
        // change socket2 dispatcher to catch the LINK message,
        // and inform when a LINKREPLY message has been sent
        AtomicReference<SocketMsg> linkMsg = new AtomicReference<>(null);
        AtomicInteger linkReplyMsgsSent = new AtomicInteger(0);
        socket2.setCoreComponents(new DirectDispatcherToLinkManager(lm1){
            @Override
            public void dispatch(SocketMsg msg) {
                if(msg.getType() == MsgType.LINK)
                    linkMsg.set(msg);
                else {
                    super.dispatch(msg);
                    if (msg.getType() == MsgType.LINKREPLY)
                        linkReplyMsgsSent.incrementAndGet();
                }
            }
        }, null);

        // SET CONDITION TO RESULT IN NON-FATAL REFUSAL HERE

        // make both sockets send link requests
        lm2.link(sid1);
        lm1.link(sid2);
        // since socket2's link request is intercepted,
        // socket1 will receive a LINKREPLY with a positive
        // answer and without metadata, before receiving
        // a LINK message.
        waitUntil(() -> linkReplyMsgsSent.get() > 0);
        // after the LINKREPLY msg is sent by socket2
        // the LINK msg can be sent
        scheduler.schedule(() -> dispatchTo(lm1,linkMsg.get()),0,TimeUnit.MILLISECONDS);

        // SET THE CONDITION BACK TO ENABLE LINKING

        // check the link is not established
        waitUntil(() -> lm2.isUnlinked(sid1));
        waitUntil(() -> lm1.isUnlinked(sid2));
    }*/

    /**
     * Simultaneous scheduling is not currently possible. It is not possible
     * because the only non-fatal answer is related to reaching the maximum
     * number of links, and this condition does not matter when the link
     * has already been created.
     * If a new non-fatal condition arises that enables this test, then set
     * the conditions in the proper spaces below.
     */
    /*@Test
    void simultaneousScheduleLinkRequest() throws InterruptedException {
        // acquire link manager locks
        lm1.getLock().lock();
        lm2.getLock().lock();
        // send link requests and then change maxLinks to 0
        // to emulate the effect of not reaching maximum number
        // of links. This will make the sockets reject link requests
        // with a non-fatal reason, therefore resulting in link requests
        // being scheduled.
        lm1.link(sid2);
        lm2.link(sid1);

        // SET CONDITION TO RESULT IN NON-FATAL REFUSAL HERE

        assert lm1.isLinking(sid2);
        assert lm2.isLinking(sid1);
        // release locks to let the sockets schedule link requests,
        // and wait until the requests are scheduled
        lm1.getLock().unlock();
        lm2.getLock().unlock();
        LinkNew link1 = lm1.links.get(sid2), // link in socket1
                link2 = lm2.links.get(sid1); // link in socket2
        waitUntil(() -> link1 != null && link1.getScheduled() != null && link1.getScheduled().get() != null);
        waitUntil(() -> link2 != null && link2.getScheduled() != null && link2.getScheduled().get() != null);

        // change the conditions back to enable the establishment of the link

        // SET THE CONDITION BACK TO ENABLE LINKING

        // check the sockets are eventually linked
        waitUntil(() -> lm1.isLinked(sid2));
        waitUntil(() -> lm2.isLinked(sid1));
    }
     */

    void dataOrControlMsgConfirmingLinkEstablishment(byte msgType) throws InterruptedException {
        // change socket1 dispatcher to catch the LINKREPLY message
        AtomicReference<SocketMsg> linkMsg = new AtomicReference<>(null);
        socket1.setCoreComponents(new DirectDispatcherToLinkManager(sid2, lm2){
            @Override
            public void dispatch(Msg msg) {
                if(msg.getType() == MsgType.LINKREPLY)
                    linkMsg.set((SocketMsg) msg);
                else {
                    super.dispatch(msg);
                }
            }
        }, null);
        // make socket1 send a link request to socket2
        lm1.link(sid2);
        // wait until socket1 is linked,
        // and assert socket2 is linking
        waitUntil(() -> lm1.isLinked(sid2));
        assert lm2.isLinking(sid1);
        // send a data/control msg to socket2 before the
        // LINKREPLY message that would make socket2 establish the link.
        Payload payload = new BytePayload(msgType, "Hello".getBytes());
        SocketMsg msg = new SocketMsg(sid1, sid2, lm1.links.get(sid2).getClockId(),payload);
        scheduler.schedule(() -> dispatchTo(lm2,msg), 0, TimeUnit.MILLISECONDS);
        // assert the link gets established with the data/control msg
         waitUntil(()->lm2.isLinked(sid1));
        // assert the LINK msg has no effect
        scheduler.schedule(() -> dispatchTo(lm2, linkMsg.get()), 0, TimeUnit.MILLISECONDS);
        Thread.sleep(25);
        assert lm1.isLinked(sid2);
        assert lm2.isLinked(sid1);
    }

    @Test
    void dataMsgConfirmingLinkEstablishment() throws InterruptedException {
        dataOrControlMsgConfirmingLinkEstablishment(MsgType.DATA);
    }

    @Test
    void controlMsgConfirmingLinkEstablishment() throws InterruptedException {
        dataOrControlMsgConfirmingLinkEstablishment((byte) 0xff);
    }

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

    // TODO - test new scenarios
}