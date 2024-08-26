package pt.uminho.di.a3m.core.messaging;

public class ErrorType {
    // Socket not found
    public static final byte SOCK_NFOUND = 0x01;

    // Socket currently not available. Socket exists but is not accepting
    // link establishments at the moment.
    public static final byte SOCK_NAVAIL = 0x02;

    // >>> I don't think this makes sense to exist.
    // >>> What is there even to do when such message is received?
    // Socket not linked. When a socket A sends a message to a socket B
    // with which it is not linked yet and the is not expected during the
    // linking procedure.
    //public static final byte SOCK_NLINKED = 0x03;
}
