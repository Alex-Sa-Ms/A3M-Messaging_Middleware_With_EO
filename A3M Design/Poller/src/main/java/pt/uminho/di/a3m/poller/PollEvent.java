package pt.uminho.di.a3m.poller;

/**
 * PollEvent is used to pass the Pollable instances to the poll(),
 * and for the poller to return the ids of the pollables along
 * with the available events of the pollable. However, due to
 * its generic nature, it can be used for any situation that
 * requires storing arbitrary data along with an event mask.
 * @param <T>
 */
public class PollEvent<T> {
    public final T data;
    public final int events;
    public PollEvent(T data, int events) {
        this.data = data;
        this.events = events;
    }
}
