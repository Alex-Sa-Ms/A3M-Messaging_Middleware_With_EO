package testWaitForAvailableLink;

import pt.uminho.di.a3m.SocketIdentifier;

import java.util.concurrent.locks.Condition;

public interface IFlowControlCoordinator {
    void newLinkState(SocketIdentifier sid, int credits, Condition availCond);
    void closeAndRemoveLinkState(SocketIdentifier sid);
    int getCredits(SocketIdentifier sid);
    int tryConsumeCredits(SocketIdentifier sid, int credits);
    int tryConsumeCredit(SocketIdentifier sid);
    int replenishCredits(SocketIdentifier sid, int credits);
    boolean isLinkAvailable(SocketIdentifier sid);
    SocketIdentifier getAvailableLink();
    boolean waitForLinkAvailability(SocketIdentifier sid, long timeout) throws InterruptedException, LinkClosedException;
    void waitForLinkAvailability(SocketIdentifier sid) throws InterruptedException, LinkClosedException;
    SocketIdentifier waitForAvailableLink(long timeout) throws InterruptedException;
    SocketIdentifier waitForAvailableLink() throws InterruptedException;
}
