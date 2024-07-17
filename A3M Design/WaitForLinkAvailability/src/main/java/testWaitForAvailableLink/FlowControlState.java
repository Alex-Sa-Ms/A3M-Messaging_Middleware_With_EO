package testWaitForAvailableLink;

import java.util.LinkedList;
import java.util.concurrent.locks.Condition;

public class FlowControlState {
    public int credits = 0;
    public final LinkedList<Integer> newRequests = new LinkedList<>();
    public int linkUnsettled = 0; // unsettled link requests
    public int genUnsettled = 0; // unsettled general requests
    public final Condition availCond; // availability condition
    public boolean closed = false;

    public FlowControlState(Condition availCond) {
        this.availCond = availCond;
    }

    public FlowControlState(int credits, Condition availCond) {
        this.credits = credits;
        this.availCond = availCond;
    }

    boolean hasCredits(){
        return credits > 0;
    }

    int countNewRequests(){
        return newRequests.size();
    }
    int countUnsettledLinkRequests(){
        return linkUnsettled;
    }
    int countUnsettledGeneralRequests(){
        return genUnsettled;
    }
    int countUnsettledRequests(){
        return linkUnsettled + genUnsettled;
    }

    int countTotalRequests(){
        return newRequests.size() + linkUnsettled + genUnsettled;
    }

    boolean hasNewRequests(){
        return !newRequests.isEmpty();
    }
    boolean hasUnsettledLinkRequests(){
        return linkUnsettled > 0;
    }
    boolean hasUnsettledGeneralRequests(){
        return genUnsettled > 0;
    }
    boolean hasUnsettledRequests(){
        return linkUnsettled + genUnsettled > 0;
    }

    boolean hasRequests(){
        return newRequests.size() + linkUnsettled + genUnsettled > 0;
    }

    // returns the clock of the first new request
    Integer peekNewRequest(){
        if(hasNewRequests())
            return newRequests.getFirst();
        else
            return null;
    }

    boolean isAvailable(){
        return credits > countTotalRequests();
        //return !closed && credits > countTotalRequests();
    }

    public boolean isClosed() {
        return closed;
    }
}
