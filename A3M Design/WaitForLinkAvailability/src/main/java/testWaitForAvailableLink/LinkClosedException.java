package testWaitForAvailableLink;

public class LinkClosedException extends Exception {
    public LinkClosedException() {
    }

    public LinkClosedException(String message) {
        super(message);
    }
}
