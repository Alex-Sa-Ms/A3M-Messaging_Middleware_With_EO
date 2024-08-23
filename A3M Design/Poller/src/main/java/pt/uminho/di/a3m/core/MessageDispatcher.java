package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.core.messages.Msg;

public interface MessageDispatcher {
    void dispatch(Msg msg);
}
