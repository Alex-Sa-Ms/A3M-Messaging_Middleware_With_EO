package pt.uminho.di.a3m.core;

public class DummySocket extends Socket{
    // NOTE: The protocol must be static and final, however, for 
    // tests purposes, allowing the protocol to be defined is helpful
    Protocol protocol;

    protected DummySocket(SocketIdentifier sid, Protocol protocol) {
        super(sid);
        this.protocol = protocol;        
    }

    @Override
    public Protocol getProtocol() {
        //if(protocol == null) {
        //    int dummySum = 0;
        //    for(char c : "DUMMY".toCharArray())
        //        dummySum += c;
        //    protocol = new Protocol(dummySum, "DUMMY");
        //}
        return protocol;
    }

    @Override
    protected void init() {}

    @Override
    protected void destroy() {
        destroyCompleted();
    }
}
