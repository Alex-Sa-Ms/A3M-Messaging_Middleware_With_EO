package pt.uminho.di.a3m.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.core.messaging.Msg;
import pt.uminho.di.a3m.sockets.DummySocket;

import java.util.concurrent.atomic.AtomicReference;

class SocketMananerImplTest {
    SocketManager socketManager;
    Protocol reqProt = new Protocol(123456, "REQ");
    Protocol repProt = new Protocol(1234567, "REP");
    Protocol routerProt = new Protocol(12345678, "ROUTER");
    Protocol dealerProt = new Protocol(123456789, "DEALER");
    SocketProducer reqProducer = sid -> new DummySocket(sid, reqProt);
    SocketProducer repProducer = sid -> new DummySocket(sid, repProt);
    SocketProducer routerProducer = sid -> new DummySocket(sid, routerProt);
    SocketProducer dealerProducer = sid -> new DummySocket(sid, dealerProt);

    @BeforeEach
    void init(){
        socketManager = new SocketMananerImpl("dummyNodeId",new MessageDispatcher() {
            @Override
            public void dispatch(Msg msg) {}

            @Override
            public AtomicReference<Msg> scheduleDispatch(Msg msg, long dispatchTime) {
                return new AtomicReference<>(msg);
            }
        });
    }

    void registerProducers(){
        socketManager.registerProducer(reqProducer);
        socketManager.registerProducer(repProducer);
        socketManager.registerProducer(routerProducer);
        socketManager.registerProducer(dealerProducer);
    }

    /**
     * Tests socket basic lifecycle.
     * Tests the following operations:
     * creation, start, get and closure.
     */
    @Test
    void testBasicSocketLifecycle() throws InterruptedException {
        // register the producers
        registerProducers();

        String dealerTagId = "DealerSocket";
        // assert the dealer socket does not exist
        assert socketManager.getSocket(dealerTagId) == null;
        // create a dealer socket
        DummySocket dealerSocket = socketManager.createSocket(dealerTagId, dealerProt.id(), DummySocket.class);
        // assert dealer socket's state is CREATED
        assert dealerSocket.getState() == SocketState.CREATED;
        // start dealer socket
        dealerSocket.start();
        // assert dealer socket's state is READY
        assert dealerSocket.getState() == SocketState.READY;
        // get dealer socket using tag identifier and assert
        // its instance matches
        assert socketManager.getSocket(dealerTagId) == dealerSocket;
        // close the dealer socket and assert its state is CLOSED
        dealerSocket.asyncClose();
        assert dealerSocket.getState() == SocketState.CLOSED;

        String routerTagId = "RouterSocket";
        // assert the router socket does not exist
        assert socketManager.getSocket(routerTagId) == null;
        // create and start a router socket
        DummySocket routerSocket = (DummySocket) socketManager.startSocket(routerTagId, routerProt.id());
        // assert router socket's state is READY
        assert routerSocket.getState() == SocketState.READY;
        // assert attempting to start the router socket results in an exception
        try {
            routerSocket.start();
            assert false; // should not reach here due to exception
        }catch (Exception ignored){}
        // get router socket using tag identifier and assert
        // its instance matches
        assert socketManager.getSocket(routerTagId, DummySocket.class) == routerSocket;
        // close the router socket and assert its state is CLOSED
        routerSocket.asyncClose();
        assert routerSocket.getState() == SocketState.CLOSED;
    }

    /**
     * Tests if socket producers can be registered,
     * removed and have their existence verified correctly.
     */
    @Test
    void testRegisterRemoveExistsProducer() {
        // asserts the producers are not registered
        assert !socketManager.existsProducer(reqProt.id());
        assert !socketManager.existsProducer(repProt.id());
        assert !socketManager.existsProducer(routerProt.id());
        assert !socketManager.existsProducer(dealerProt.id());

        // register the producers
        registerProducers();

        // assert all of them are registered
        assert socketManager.existsProducer(reqProt.id());
        assert socketManager.existsProducer(repProt.id());
        assert socketManager.existsProducer(routerProt.id());
        assert socketManager.existsProducer(dealerProt.id());

        // assert that two producers cannot be registered
        // with the same protocol identifier
        try{
            socketManager.registerProducer(reqProducer);
            assert false; // should not reach this statement because of an exception
        }catch (Exception ignored){}

        // remove the producers
        socketManager.removeProducer(reqProt.id());
        socketManager.removeProducer(repProt.id());
        socketManager.removeProducer(routerProt.id());
        socketManager.removeProducer(dealerProt.id());

        // assert all of them were removed
        assert !socketManager.existsProducer(reqProt.id());
        assert !socketManager.existsProducer(repProt.id());
        assert !socketManager.existsProducer(routerProt.id());
        assert !socketManager.existsProducer(dealerProt.id());
    }
}