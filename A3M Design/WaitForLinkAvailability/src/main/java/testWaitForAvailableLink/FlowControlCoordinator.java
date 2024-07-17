package testWaitForAvailableLink;

import pt.uminho.di.a3m.SocketIdentifier;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

public class FlowControlCoordinator implements IFlowControlCoordinator {
    // Each availability request has a clock value associated.
    // The clock increments with each created request.
    private int clock = 0;

    // Flow control state per link
    private final Map<SocketIdentifier, FlowControlState> linkStates = new HashMap<>();

    // State for availability requests of any link. Called general requests.
    private final List<GeneralRequest> genRequests = new LinkedList<>();
    private int genNext = 0; // index of the next new request. Requests with a smaller index (if existent) are unsettled requests.
    private final Condition genAvailCond;

    public FlowControlCoordinator(Condition genAvailCond) {
        this.genAvailCond = genAvailCond;
    }

    // TODO - DESCREVER SOLUCAO PARA POR NA TESE
    //          - CAN THE SIMPLE SOLUTION OF SIGNALING ONE AT A TIME BE MORE PERFORMANT? Would require list of available links.
    //          - THIS SOLUTION ALLOWS SOME FAIRNESS
    // TODO - add fairness and test
    //          - fairness total implica impedir que sejam consumidas mensagens quando existem creditos que o possibilitam
    //              - implica sinalizar uma thread de cada vez.


    /* ***** Public Methods ***** */

    public void newLinkState(SocketIdentifier sid, int credits, Condition availCond){
        if(linkStates.containsKey(sid))
            throw new IllegalStateException("A link state already exists associated with the given socket identifier.");
        else {
            FlowControlState linkState = new FlowControlState(credits, availCond);
            linkStates.put(sid, linkState);
            signalWaitingThreads(sid, linkState, credits);
        }
    }

    public void closeAndRemoveLinkState(SocketIdentifier sid){
        FlowControlState linkState = linkStates.remove(sid);
        linkState.closed = true;
        linkState.availCond.signalAll();

        // converts "unsettled" requests, associated with the link,
        // to "new" requests
        ListIterator<GeneralRequest> it = genRequests.listIterator();
        List<GeneralRequest> toConvert = new LinkedList<>();
        GeneralRequest req;
        while(it.nextIndex() < genNext){
            req = it.next();
            // if the unsettled request is associated with the link
            // being removed, then unsets the socket identifier,
            // saves the request in a temporary list and removes it
            // from the general requests list.
            if(sid.equals(req.getSocketIdentifier())) {
                req.setSocketIdentifier(null);
                toConvert.add(req);
                it.remove();
                genNext--; // updates the pointer to keep track of new requests
            }
        }

        // adds the requests beginning at the index where new requests are
        for(GeneralRequest r : toConvert)
            it.add(r);

        toConvert.clear();
    }

    public int getCredits(SocketIdentifier sid){
        FlowControlState linkState = getLinkState(sid);
        return linkState.credits;
    }

    public int tryConsumeCredits(SocketIdentifier sid, int credits){
        FlowControlState linkState = getLinkState(sid);
        int r = linkState.credits - credits;
        if(r >= 0)
            linkState.credits = r;
        else
            throw new IllegalStateException("Consuming " + credits + " credits would result in a balance of " + (-r) + " negative credits.");
        return r;
    }

    public int tryConsumeCredit(SocketIdentifier sid){
        return tryConsumeCredits(sid, 1);
    }

    public int replenishCredits(SocketIdentifier sid, int credits){
        FlowControlState linkState = getLinkState(sid);
        // adds credits
        linkState.credits += credits;
        signalWaitingThreads(sid, linkState, credits);
        // returns the current amount of credits held by the link
        return linkState.credits;
    }

    public boolean isLinkAvailable(SocketIdentifier sid){
        FlowControlState linkState = getLinkState(sid);
        return linkState.isAvailable();
    }

    public SocketIdentifier getAvailableLink(){
        for(Map.Entry<SocketIdentifier, FlowControlState> entry : linkStates.entrySet()){
            if (entry.getValue().isAvailable())
                return entry.getKey();
        }
        return null;
    }

    public boolean waitForLinkAvailability(SocketIdentifier sid, long timeout) throws InterruptedException, LinkClosedException {
        long timeoutTime = calculateTimeoutTime(timeout);

        // If the link has more credits than the amount of requests, then
        // the method can return immediatelly
        FlowControlState linkState = getLinkState(sid);
        if(linkState.isAvailable())
            return true;

        try {
            // creates availability request
            createLinkSpecificRequest(linkState);

            boolean proceed = false,
                    timedOut = false;
            while (!proceed && !timedOut) {
                timeout = Math.max(0L, timeoutTime - System.currentTimeMillis());
                timedOut = !linkState.availCond.await(timeout, TimeUnit.MILLISECONDS);
                if(linkState.isClosed())
                    throw new LinkClosedException("Link was closed.");
                proceed = linkState.hasUnsettledLinkRequests();
            }

            if(proceed){
                settleLinkSpecificRequest(linkState);
                return true;
            } else {
                // aborts request
                abortLinkSpecificRequest(linkState);
                return false;
            }
        }catch (InterruptedException | LinkClosedException e){
            abortLinkSpecificRequest(linkState);
            throw e;
        }
    }

    public void waitForLinkAvailability(SocketIdentifier sid) throws InterruptedException, LinkClosedException {
        // If the link has more credits than the amount of requests, then
        // the method can return immediatelly
        FlowControlState linkState = getLinkState(sid);
        if(linkState.isAvailable())
            return;

        try {
            // creates availability request
            createLinkSpecificRequest(linkState);

            boolean proceed = false;
            while (!proceed) {
                linkState.availCond.await();
                if(linkState.isClosed())
                    throw new LinkClosedException("Link was closed.");
                proceed = linkState.hasUnsettledLinkRequests();
            }

            settleLinkSpecificRequest(linkState);
        }catch (InterruptedException | LinkClosedException e){
            abortLinkSpecificRequest(linkState);
            throw e;
        }
    }

    public SocketIdentifier waitForAvailableLink(long timeout) throws InterruptedException{
        long timeoutTime = calculateTimeoutTime(timeout);

        // Checks if there is any available link that can return immediatelly
        SocketIdentifier sid = getAvailableLink();
        if(sid != null)
            return sid;

        try {
            // creates availability request
            createGeneralRequest();

            boolean proceed = false,
                    timedOut = false;
            while (!proceed && !timedOut) {
                timeout = Math.max(0L, timeoutTime - System.currentTimeMillis());
                timedOut = !genAvailCond.await(timeout, TimeUnit.MILLISECONDS);
                proceed = hasUnsettledGeneralRequests();
            }

            if(proceed){
                return settleGeneralRequest();
            } else {
                // aborts request
                abortGeneralRequest();
                return null;
            }
        }catch (InterruptedException ie){
            abortGeneralRequest();
            throw ie;
        }
    }

    public SocketIdentifier waitForAvailableLink() throws InterruptedException{
        // Checks if there is any available link that can return immediatelly
        SocketIdentifier sid = getAvailableLink();
        if(sid != null)
            return sid;

        try {
            // creates availability request
            createGeneralRequest();
            boolean proceed = false;
            while (!proceed) {
                genAvailCond.await();
                proceed = hasUnsettledGeneralRequests();
            }
            return settleGeneralRequest();
        }catch (InterruptedException ie){
            abortGeneralRequest();
            throw ie;
        }
    }

    // TODO - solucao ideal é os métodos passarem a consumir credito
    //  e depois fazer replenish do credito que nao foi usado
    public void creditNotConsumed(SocketIdentifier sid){
        FlowControlState linkState = getLinkState(sid);
        if(linkState.credits > linkState.countUnsettledRequests())
            signalWaitingThread(sid, linkState);
    }

    /* ***** Auxiliary Methods ***** */

    // returns current value of clock and increments it by 1 unit
    int getClockAndIncrement(){
        int r = clock;
        clock++;
        return r;
    }

    int countNewGeneralRequests(){
        return genRequests.size() - genNext;
    }

    int countUnsettledGeneralRequests(){
        return genNext;
    }

    int countTotalGeneralRequests(){
        return genRequests.size();
    }

    boolean hasNewGeneralRequests(){
        return genRequests.size() - genNext > 0;
    }

    boolean hasUnsettledGeneralRequests(){
        return genNext > 0;
    }

    boolean hasGeneralRequests(){
        return !genRequests.isEmpty();
    }

    // returns the clock of the first new general request
    Integer peekNewGeneralRequest(){
        if(hasNewGeneralRequests())
            return genRequests.get(genNext).getClock();
        else
            return null;
    }

    FlowControlState getLinkState(SocketIdentifier sid){
        FlowControlState linkState = linkStates.get(sid);
        if(linkState == null)
            throw new IllegalArgumentException("No state found for the given socket identifier.");
        else
            return linkState;
    }

    // Creates request for a specific socket availability
    private void createLinkSpecificRequest(FlowControlState linkState){
        linkState.newRequests.add(getClockAndIncrement());
    }

    // Create request for any socket availability
    private void createGeneralRequest(){
        genRequests.add(new GeneralRequest(getClockAndIncrement()));
    }

    // Aborts a link specific request. Attempts to remove a new request first.
    // If there isn't a new request, then removes an unsettled request.
    private void abortLinkSpecificRequest(FlowControlState linkState){
        // A request can only be aborted if there are requests
        assert linkState.hasRequests();

        if(linkState.hasNewRequests())
            linkState.newRequests.removeLast();
        else
            linkState.linkUnsettled--;
    }

    // Aborts a general request. Attempts to remove a new request first.
    // If there isn't a new request, then removes an unsettled request.
    // A general unsettled request results in the increment by 1 unit of the
    // unsettled requests of a specific link. To completely abort a general
    // unsettled request, the associated link flow control state must have its
    // unsettled requests decreased by 1 unit.
    private void abortGeneralRequest(){
        // A request can only be aborted if there are requests
        assert hasGeneralRequests();

        if(hasNewGeneralRequests()) {
            // removes a new request
            genRequests.removeLast();
            // updates the next request index if it becomes invalid
            if(genNext > genRequests.size())
                genNext = genRequests.size();
        }else {
            settleGeneralRequest();
        }
    }

    // Converts a link specific request from "new" to "unsettled"
    private void unsettleLinkSpecificRequest(FlowControlState linkState){
        assert linkState.hasNewRequests();
        linkState.newRequests.removeFirst();
        linkState.linkUnsettled++;
    }

    // Converts a general request from "new" to "unsettled"
    private void unsettleGeneralRequest(SocketIdentifier sid){
        // gets new general request
        GeneralRequest req = genRequests.get(genNext);
        assert req != null;
        // sets the link that will satisfy the general request
        req.setSocketIdentifier(sid);
        // increases link's amount of unsettled requests by 1 unit,
        // to contabilize the general request
        getLinkState(sid).genUnsettled++;
        // updates the next index to point to the next new general request
        genNext++;
    }

    // Converts a link specific request from "unsettled" to "settled"
    private void settleLinkSpecificRequest(FlowControlState linkState){
        assert linkState.hasUnsettledLinkRequests();
        linkState.linkUnsettled--;
    }

    // Converts a general request from "unsettled" to "settled"
    private SocketIdentifier settleGeneralRequest(){
        // removes an unsettled request
        GeneralRequest req = genRequests.removeFirst();
        // decreases the next request index in 1 unit
        // to maintain track of the start of new requests
        genNext--;
        // removes an unsettled request from the associated link
        SocketIdentifier sid = req.getSocketIdentifier();
        assert sid != null;

        FlowControlState linkState = getLinkState(sid);
        if(linkState != null && !linkState.isClosed()) {
            linkState.genUnsettled--;
            assert linkState.genUnsettled >= 0;
            return sid;
        } else {
            assert false;
            throw new IllegalStateException("State does not allow to settle a general request.");
        }
    }

    // Signals a waiting thread.
    private void signalWaitingThread(SocketIdentifier sid, FlowControlState linkState) {
        // determine if a general request or a specific request
        // should be handled by checking which one has the lowest clock
        Integer genClock = peekNewGeneralRequest(),
                linkClock = linkState.peekNewRequest();

        if (linkClock != null && (genClock == null || linkClock < genClock)) {
            unsettleLinkSpecificRequest(linkState);
            linkState.availCond.signal();
        } else if (genClock != null && (linkClock == null || genClock < linkClock)) {
            unsettleGeneralRequest(sid);
            genAvailCond.signal();
        }
    }

    private void signalWaitingThreads(SocketIdentifier sid, FlowControlState linkState, int maxSignals){
        // Calculates number of threads that should be signaled.
        // It should be the minimum between the amount of credits
        // replenished and the amount of new requests that can be satisfied.
        int nSignals = Math.min(maxSignals, linkState.countNewRequests() + countNewGeneralRequests());
        for(int i = 0 ; i < nSignals; i++)
            signalWaitingThread(sid, linkState);
    }

    private long calculateTimeoutTime(long timeout){
        // validates timeout and calculates timeout time
        if(timeout < 0L)
            throw new IllegalArgumentException("Timeout must not be a negative.");
        long timeoutTime = System.currentTimeMillis() + timeout;
        if(timeoutTime < 0L)
            timeoutTime = Long.MAX_VALUE;
        return timeoutTime;
    }
}
