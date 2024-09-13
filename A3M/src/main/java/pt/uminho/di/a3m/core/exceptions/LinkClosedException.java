package pt.uminho.di.a3m.core.exceptions;

public class LinkClosedException extends RuntimeException {
    public LinkClosedException() {}
    public LinkClosedException(String message) {
        super(message);
    }
}
