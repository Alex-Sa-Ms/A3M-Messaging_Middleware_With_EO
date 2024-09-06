package pt.uminho.di.a3m.core;

import pt.uminho.di.a3m.core.messaging.Msg;
import pt.uminho.di.a3m.core.messaging.SocketMsg;

import java.util.concurrent.atomic.AtomicReference;

public interface MessageDispatcher {
    /**
     * Dispatches message if it follows a valid format.
     * To be a valid message it must not be null and must
     * not have any null tag and node identifiers.
     * @param msg message to be dispatched
     */
    void dispatch(Msg msg);

    /**
     * Allows scheduling the dispatch of a message if the
     * message is valid. If the dispatch time has already
     * been passed, then the message is sent immediately.
     * <p> The method returns an atomic reference containing
     * the provided message. When consulting this reference,
     * if the message is present, then it hasn't been sent yet.
     * To cancel the delivery, one may set the reference to "null".
     * <p>To be a valid message it must not be null and must
     * not have any null tag and node identifiers.
     * @param msg message to be dispatched
     * @param dispatchTime time at which the dispatch should be executed.
     *                     The actual dispatch time may be a bit delayed
     *                     depending on how busy the middleware is.
     * @return atomic reference that allows cancelling the delivery of the message.
     */
    AtomicReference<Msg> scheduleDispatch(Msg msg, long dispatchTime);
}
