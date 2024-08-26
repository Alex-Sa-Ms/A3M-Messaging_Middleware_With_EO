package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.core.messaging.Msg;
import pt.uminho.di.a3m.core.messaging.SocketMsg;

public interface MessageDispatcher {
    void dispatch(Msg msg);
    void scheduleDispatch(SocketMsg msg, long dispatchTime);
}
