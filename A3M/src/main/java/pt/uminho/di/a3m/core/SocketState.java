package pt.uminho.di.a3m.core;

public enum SocketState {
    CREATED, // Initial state
    READY, // After being started
    CLOSING, // When close() is invoked and before all associated custom resources are released
    CLOSED, // After close() and all custom resources are released
    ERROR; // When a fatal error occurs that leaves the socket unusable
}
