package testWaitForAvailableLink.MostUnfairVersion;

import pt.uminho.di.a3m.SocketIdentifier;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;

public class UnfairFlowControlState {
    public final SocketIdentifier sid;
    public int credits = 0;
    Set<Long> requests = new HashSet<>(); // specific requests
    //public int requests = 0;
    //public int specSignaled = 0; // link-specific signaled
    //public int genSignaled = 0; // general signaled
    public boolean sigSpecific = true; // if 'true' next signal should be to a link-specific thread. Otherwise, signal a general thread.
    public final Condition availCond; // availability condition
    public boolean closed = false;

    public UnfairFlowControlState(SocketIdentifier sid, int credits, Condition availCond) {
        this.sid = sid;
        this.credits = credits;
        this.availCond = availCond;
    }

    public UnfairFlowControlState(SocketIdentifier sid, Condition availCond) {
        this(sid, 0, availCond);
    }

    boolean hasCredits(){
        return credits > 0;
    }
    boolean hasRequests(){
        return !requests.isEmpty();
        //return requests > 0;
    }
    int countRequests(){
        return requests.size();
        //return requests;
    }
    public boolean isClosed() {
        return closed;
    }
}
