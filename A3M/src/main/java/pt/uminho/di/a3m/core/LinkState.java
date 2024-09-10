package pt.uminho.di.a3m.core;

public enum LinkState {
    // When waiting for the peer's answer regarding the link establishment.
    // If a LINK message from the peer has been received, then any data/control
    // message can make the link progress to established.
    LINKING,
    // when waiting for an unlink message to close the link
    UNLINKING,
    // when wanting to cancel a linking process
    CANCELLING,
    ESTABLISHED,
    CLOSED;
}
