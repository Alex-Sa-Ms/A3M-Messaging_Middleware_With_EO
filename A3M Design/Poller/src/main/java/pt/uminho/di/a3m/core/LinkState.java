package pt.uminho.di.a3m.core;

public enum LinkState {
    UNINITIALIZED, // At the moment of creation
    LINKING, // After sending a link request
    UNLINKING, // After sending an unlink message and an unlink message from the peer is required to close the link.
    ESTABLISHED, // After accepting a link
    CLOSED; // After close() is invoked
}