package pt.uminho.di.a3m.core;

import haslab.eo.EOMiddleware;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.sockets.configurable_socket.ConfigurableSocket;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

class MessageManagementSystemTest {
    String nodeId = "Node";
    EOMiddleware eom;
    SocketManager sm;
    MessageManagementSystem mms;
    int nrSockets = 3;
    SocketIdentifier[] sids = new SocketIdentifier[nrSockets];
    ConfigurableSocket[] sockets = new ConfigurableSocket[nrSockets];

    @BeforeEach
    void init() throws SocketException, UnknownHostException {
        eom = EOMiddleware.start(nodeId, null);
        mms = new MessageManagementSystem(eom);
        sm = SocketTestingUtilities.createSocketManager(nodeId, mms);
        mms.setSocketManager(sm);
        sm.registerProducer(ConfigurableSocket::createSocket);
        for (int i = 0; i < nrSockets; i++) {
            sids[i] = new SocketIdentifier(nodeId, "Socket" + i);
            sockets[i] = sm.createSocket("Socket" + i, ConfigurableSocket.protocol.id(), ConfigurableSocket.class);
        }
    }

    @Test
    void startAndCloseRoutine() throws InterruptedException {
        assert mms.getState() == MessageManagementSystem.CREATED;
        mms.start();
        assert mms.getState() == MessageManagementSystem.RUNNING;
        mms.close();
        if(mms.getState() == MessageManagementSystem.CLOSING){
            while(mms.getState() != MessageManagementSystem.CLOSED)
                Thread.sleep(5L);
        }
        assert mms.isClosed();
    }

    @Test
    void closeAndWait() throws InterruptedException {
        mms.start();
        assert mms.getState() == MessageManagementSystem.RUNNING;
        mms.closeAndWait();
        assert mms.isClosed();
    }

    @Test
    void closeAndWaitWithTimeout() throws InterruptedException {
        mms.start();
        assert mms.getState() == MessageManagementSystem.RUNNING;
        mms.closeAndWait(0L);
        // This assertion could also be CLOSED, however,
        // it is very improbable that such case happens.
        assert mms.getState() == MessageManagementSystem.CLOSING;
    }

    @Test
    void dispatch() throws InterruptedException {
        // start messaging system
        mms.start();
        // link socket0 to socket1
        sockets[0].link(sids[1]);
        // wait for them to link
        while(!sockets[1].isLinked(sids[0]))
            Thread.sleep(5L);
        // send message and receive
        String msg = "Hello";
        sockets[0].send(msg.getBytes(), 0L, false);
        byte[] arrMsg = sockets[1].receive(null, false);
        assert msg.equals(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(arrMsg)).toString());
    }

    @Test
    void scheduleDispatch() throws InterruptedException {
        // start messaging system
        mms.start();
        // set socket0's retry interval as 50 ms
        sockets[0].setOption("retryInterval",50L);
        // make socket1's max links as 0 so that it non-fatally refuses
        // socket0's link request. This means socket0 will schedule a request.
        sockets[1].setOption("maxLinks", 0);
        // Start counting the time. It should take at least 50L.
        long start = System.currentTimeMillis();
        // link socket0 to socket1
        sockets[0].link(sids[1]);
        // sleep a bit then set socket1's max links to 1,
        // so that it accepts socket0's link request
        Thread.sleep(10L);
        sockets[1].setOption("maxLinks", 1);
        // wait for them to link
        while(!sockets[1].isLinked(sids[0]))
            Thread.sleep(5L);
        assert sockets[0].isLinked(sids[1]);
        // check time is at least 50 ms
        assert System.currentTimeMillis() - start >= 50L;
    }
}