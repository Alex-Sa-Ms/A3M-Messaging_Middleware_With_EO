package pt.uminho.di.a3m.core;

public enum LinkState {
    UNINITIALIZED, // At the moment of creation
    PENDING, // After sending a link request
    ESTABLISHED, // After accepting a link
    CLOSED; // After close() is invoked
}