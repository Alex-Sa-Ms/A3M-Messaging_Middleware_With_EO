package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.core.messaging.SocketMsg;

import java.util.concurrent.atomic.AtomicReference;

public class MessageManagementSystem implements MessageDispatcher{
    @Override
    public void dispatch(SocketMsg msg) {

    }

    @Override
    public AtomicReference<SocketMsg> scheduleDispatch(SocketMsg msg, long dispatchTime) {
        return null;
    }
}
