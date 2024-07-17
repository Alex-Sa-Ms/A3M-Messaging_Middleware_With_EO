package testWaitForAvailableLink.UnfairVersion;

import pt.uminho.di.a3m.SocketIdentifier;
import testWaitForAvailableLink.LinkClosedException;


import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * Unfair version, which allows consuming credits at any time as long as there are credits to be consumed.
 */
public class UnfairFlowControlCoordinator {

    Map<SocketIdentifier, UnfairFlowControlState> linkStates = new HashMap<>();
    Set<SocketIdentifier> availLinks = new HashSet<>();
    int genRequests = 0; // number of general requests
    Deque<SocketIdentifier> genSigs = new LinkedList<>(); // signals to general threads
    Condition genAvailCond; // general requests availability condition

    
    public void newLinkState(SocketIdentifier sid, int credits, Condition availCond) {
        if (linkStates.containsKey(sid))
            throw new IllegalStateException("A link state already exists associated with the given socket identifier.");
        else {
            UnfairFlowControlState linkState = new UnfairFlowControlState(sid, credits, availCond);
            linkStates.put(sid, linkState);
            if (credits > 0) {
                availLinks.add(sid);
                signalWaitingThreads(sid, linkState, credits);
            }
        }
    }

    
    public void closeAndRemoveLinkState(SocketIdentifier sid) {
        UnfairFlowControlState linkState = linkStates.remove(sid);
        linkState.closed = true;
        availLinks.remove(sid);
        genSigs.removeIf(aux -> aux.equals(sid));
        linkState.availCond.signalAll();
    }

    
    public int getCredits(SocketIdentifier sid) {
        UnfairFlowControlState linkState = getLinkState(sid);
        return linkState.credits;
    }

    
    public void consumeCredits(SocketIdentifier sid, int credits) {
        // If the link has credits, then return immediatelly
        UnfairFlowControlState linkState = getLinkState(sid);
        if(internalTryConsumeCredits(sid, linkState, credits) >= 0){
            // if the credits of another thread were consumed,
            // decreases the number of signals, to allow the
            // threads to be signaled again.
            if(linkState.credits < linkState.specSignaled + linkState.genSignaled) {
                boolean sigSpecific = true;
                while (credits > 0){
                    if(linkState.sigSpecific){
                        if(linkState.specSignaled > 0) {
                            linkState.specSignaled--;
                            linkState.sigSpecific = false;
                        }else{
                            while (credits > 0) {
                                credits--;
                                linkState.genSignaled--;
                            }
                        }
                    }
                    else{
                        if(linkState.genSignaled > 0) {
                            credits--;
                            linkState.genSignaled--;
                            linkState.sigSpecific = true;
                        }else{
                            while (credits > 0) {
                                credits--;
                                linkState.specSignaled--;
                            }
                        }
                    }
                }
            }
            return;
        }
    }

    
    public void consumeCredit(SocketIdentifier sid) {
        consumeCredits(sid, 1);
    }
    
    private int internalTryConsumeCredits(SocketIdentifier sid, UnfairFlowControlState linkState, int credits){
        int r = linkState.credits - credits;
        if(r < 0)
            return r;
        else if(r == 0) {
            availLinks.remove(sid);
        }
        linkState.credits = r;
        return r;
    }

    private boolean internalTryConsumeCredit(SocketIdentifier sid, UnfairFlowControlState linkState){
        return internalTryConsumeCredits(sid, linkState, 1) >= 0;
    }

    
    public int replenishCredits(SocketIdentifier sid, int credits) {
        UnfairFlowControlState linkState = getLinkState(sid);
        // if balance becomes positive, marks link as available
        if(linkState.credits <= 0 && credits > 0)
            availLinks.add(sid);
        // adds credits
        linkState.credits += credits;
        if(credits > 0)
            signalWaitingThreads(sid, linkState, credits);
        // returns the current amount of credits held by the link
        return linkState.credits;
    }

    
    public boolean isLinkAvailable(SocketIdentifier sid) {
        return availLinks.contains(sid);
    }

    
    public SocketIdentifier getAvailableLink() {
        return !availLinks.isEmpty() ? availLinks.iterator().next() : null;
    }

    
    public boolean waitForLinkAvailability(SocketIdentifier sid, Long timeout) throws InterruptedException, LinkClosedException {
        long timeoutTime = 0L;
        if(timeout != null)
            timeoutTime = calculateTimeoutTime(timeout);

        // If the link has credits, then return immediatelly
        UnfairFlowControlState linkState = getLinkState(sid);
        if(internalTryConsumeCredit(sid, linkState)){
            // if the credit of another thread was consumed,
            // decreases the number of signals of that type of thread,
            // to allow the thread to be signaled again.
            if(linkState.credits < linkState.specSignaled + linkState.genSignaled) {
                // First, attempts to decrease the signals of its own type.
                // If not possible, removes from general threads.
                if(linkState.specSignaled > 0)
                    linkState.specSignaled--;
                else if (linkState.genSignaled > 0)
                    linkState.genSignaled--;
            }
            return true;
        }
        
        try {
            // creates availability request
            createLinkSpecificRequest(linkState);

            boolean proceed = false,
                    timedOut = false;
            while (!proceed && !timedOut) {
                if(timeout != null) {
                    timeout = Math.max(0L, timeoutTime - System.currentTimeMillis());
                    timedOut = !linkState.availCond.await(timeout, TimeUnit.MILLISECONDS);
                }else {
                    linkState.availCond.await();
                }
                if(linkState.isClosed())
                    throw new LinkClosedException("Link was closed.");
                proceed = linkState.specSignaled > 0
                        && internalTryConsumeCredit(sid, linkState);
            }

            if(!proceed){
                // aborts request
                abortLinkSpecificRequest(linkState);
                return false;
            }

            finishLinkSpecificRequest(linkState);

            return proceed;
        }catch (InterruptedException | LinkClosedException e){
            abortLinkSpecificRequest(linkState);
            throw e;
        }
    }


    public void waitForLinkAvailability(SocketIdentifier sid) throws InterruptedException, LinkClosedException {
        waitForLinkAvailability(sid, null);
    }

    public SocketIdentifier waitForAvailableLink(Long timeout) throws InterruptedException {
        long timeoutTime = 0L;
        if(timeout != null)
            calculateTimeoutTime(timeout);

        // Checks if there is any available link that can return immediatelly
        SocketIdentifier sid = getAvailableLink();
        UnfairFlowControlState linkState = linkStates.get(sid);
        if(linkState != null && internalTryConsumeCredit(sid, linkState)){
            // if the credit of another thread was consumed,
            // decreases the number of signals of that type of thread,
            // to allow the thread to be signaled again.
            if(linkState.credits < linkState.specSignaled + linkState.genSignaled) {
                // First, attempts to decrease the signals of its own type.
                // If not possible, removes from link-specific threads.
                if(linkState.genSignaled > 0)
                    linkState.genSignaled--;
                else if (linkState.specSignaled > 0)
                    linkState.specSignaled--;
            }
            return sid;
        }

        try {
            // creates availability request
            createGeneralRequest();

            boolean proceed = false,
                    timedOut = false;
            while (!proceed && !timedOut) {
                if(timeout != null) {
                    timeout = Math.max(0L, timeoutTime - System.currentTimeMillis());
                    timedOut = !genAvailCond.await(timeout, TimeUnit.MILLISECONDS);
                }else {
                    genAvailCond.await();
                }
                sid = genSigs.poll();
                linkState = linkStates.get(sid);
                if(linkState != null) {
                    proceed = linkState.genSignaled > 0
                            && internalTryConsumeCredit(sid, linkState);
                }
            }

            if(!proceed){
                // aborts request
                abortGeneralRequest();
                return null;
            }

            finishGeneralRequest(linkState);
            return sid;
        }catch (InterruptedException ie){
            abortGeneralRequest();
            throw ie;
        }
    }

    
    public SocketIdentifier waitForAvailableLink() throws InterruptedException {
        return waitForAvailableLink(null);
    }

    UnfairFlowControlState getLinkState(SocketIdentifier sid) {
        UnfairFlowControlState linkState = linkStates.get(sid);
        if (linkState == null)
            throw new IllegalArgumentException("No state found for the given socket identifier.");
        else
            return linkState;
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

    // Creates request for a specific socket availability
    private void createLinkSpecificRequest(UnfairFlowControlState linkState){
        linkState.requests++;
    }

    // Create request for any socket availability
    private void createGeneralRequest(){
        genRequests++;
    }
    
    // Aborts a link specific request.
    private void abortLinkSpecificRequest(UnfairFlowControlState linkState){
        // A request can only be aborted if there are requests
        assert linkState.requests > 0;
        linkState.requests--;
    }

    // Aborts a general request
    private void abortGeneralRequest(){
        // A request can only be aborted if there are requests
        assert genRequests > 0;
        genRequests--;
    }

    private void finishLinkSpecificRequest(UnfairFlowControlState linkState){
        assert linkState.requests > 0 && linkState.specSignaled > 0;
        linkState.requests--;
        linkState.specSignaled--;
    }

    private void finishGeneralRequest(UnfairFlowControlState linkState){
        assert genRequests > 0 && linkState.genSignaled > 0;
        genRequests--;
        linkState.genSignaled--;
    }

    private void signalWaitingThreads(SocketIdentifier sid, UnfairFlowControlState linkState, int credits) {
        int nGenSigs = genRequests - genSigs.size();
        int nSpecSigs = linkState.requests - linkState.specSignaled;
        int nSignals = Math.min(credits,nGenSigs + nSpecSigs);

        while (nSignals > 0){
            if(linkState.sigSpecific){
                if(nSpecSigs > 0) {
                    nSignals--;
                    nSpecSigs--;
                    linkState.specSignaled++;
                    linkState.sigSpecific = false;
                    linkState.availCond.signal();
                }else{
                    while (nSignals > 0) {
                        nSignals--;
                        nGenSigs--;
                        genSigs.add(sid);
                        linkState.genSignaled++;
                        genAvailCond.signal();
                    }
                }
            }
            else{
                if(nGenSigs > 0) {
                    nSignals--;
                    nGenSigs--;
                    genSigs.add(sid);
                    linkState.genSignaled++;
                    linkState.sigSpecific = true;
                    genAvailCond.signal();
                }else{
                    while (nSignals > 0) {
                        nSignals--;
                        nSpecSigs--;
                        linkState.specSignaled++;
                        genAvailCond.signal();
                    }
                }
            }
        }

    }
}