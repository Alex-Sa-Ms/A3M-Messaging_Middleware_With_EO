package testWaitForAvailableLink;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import pt.uminho.di.a3m.SocketIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TestsClass {
    @Test
    public void testSingleSendSpecific() throws InterruptedException, LinkClosedException {
        Socket socket = new Socket();
        SocketIdentifier dest = new SocketIdentifier("nodeA", "socketA");
        socket.newLink(dest, 1); // starts link with only 1 credit
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState linkState = fcManager.getLinkState(dest);

        // send message must result in the consumption of the credit
        assert linkState.credits == 1;
        assert socket.sendMsg(dest, 0L);
        assert linkState.credits == 0;
    }

    @Test
    public void testSendSpecificTimeout() throws InterruptedException, LinkClosedException {
        Socket socket = new Socket();
        SocketIdentifier dest = new SocketIdentifier("nodeA", "socketA");
        socket.newLink(dest, 0); // starts link with 0 credits
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState linkState = fcManager.getLinkState(dest);

        // should time out
        assert linkState.credits == 0;
        boolean sent = socket.sendMsg(dest, 0L);
        assert !sent
                && linkState.credits == 0
                && linkState.countTotalRequests() == 0;
    }

    @Test
    public void testSendSpecificReplenishCredits(){
        Socket socket = new Socket();
        SocketIdentifier dest = new SocketIdentifier("nodeA", "socketA");
        socket.newLink(dest, 0); // starts link with 0 credits
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState linkState = fcManager.getLinkState(dest);
        assert linkState.credits == 0;

        // starts thread that will wait until an availability request
        // for the linkState is created. Then replenishes 1 credit to allow
        // the message to be sent.
        new Thread(new WaitWithLockAcquiredAndDoSomething(socket) {
            @Override
            protected boolean waitCondition() {
                return !linkState.hasNewRequests();
            }

            @Override
            protected void customBehaviour() {
                // make some credits verifications and replenish 1 credit
                assert linkState.credits == 0;
                socket.replenishCredits(dest, 1);
                assert linkState.credits == 1;
            }
        }).start();

        try {
            // shows the intention of sending a message
            socket.sendMsg(dest, 500L);
            assert linkState.credits == 0;
        } catch (Exception e) {
            assert false;
        }
    }
    
    private void testRaceForCreditsBetweenSendSpecificRequests(int N, int credits) throws InterruptedException {
        Socket socket = new Socket();
        SocketIdentifier dest = new SocketIdentifier("nodeA", "socketA");
        socket.newLink(dest, 0); // starts link with 0 credits
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState linkState = fcManager.getLinkState(dest);

        Queue<Integer> sent = new LinkedBlockingQueue<>();

        // initiate threads
        Thread[] threads = new Thread[N];
        for(int i = 0; i < N; i++){
            int finalI = i;
            threads[i] = new Thread(() -> {
                try {
                    if(socket.sendMsg(dest, 200L))
                        sent.add(finalI);
                } catch (Exception ignored) {}
            });
            threads[i].start();
        }

        // replenish credit(s)
        socket.replenishCredits(dest, credits);

        // wait for threads
        for(Thread t : threads)
            t.join();

        assert sent.size() == Math.min(N, credits)
                && linkState.credits == Math.max(0, credits - N)
                && !linkState.hasNewRequests()
                && linkState.countUnsettledRequests() == 0;
    }

    @TestFactory
    public List<DynamicTest> testRaceForCreditsBetweenSendSpecificRequests(){
        List<Integer> NList = List.of(2,2,5,10,20,20),
                creditsList = List.of(0,1,3,5,10,40);
        List<DynamicTest> tests = new ArrayList<>();
        int i = 0;
        for(int N : NList){
            int credits = creditsList.get(i);
            tests.add(
                    DynamicTest.dynamicTest(
                            "Test race for credits between specific request (N=" +
                                    N + " , credits=" + credits + ")" ,
                            () -> testRaceForCreditsBetweenSendSpecificRequests(N, credits)));
            i++;
        }
        return tests;
    }

    @Test
    public void testSendSpecificLinkClosedException() throws InterruptedException {
        Socket socket = new Socket();
        SocketIdentifier dest = new SocketIdentifier("nodeA", "socketA");
        socket.newLink(dest, 0); // starts link with 0 credits to make the thread wait
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState linkState = fcManager.getLinkState(dest);

        // starts thread that will wait until an availability request
        // for the linkState is created. Then closes the link
        new Thread(new WaitWithLockAcquiredAndDoSomething(socket) {
            @Override
            protected boolean waitCondition() {
                return !linkState.hasNewRequests();
            }

            @Override
            protected void customBehaviour() {
                socket.closeLink(dest);
            }
        }).start();

        try {
            socket.sendMsg(dest);
            assert false; // should not get here due to the LinkClosedException
        } catch (LinkClosedException e) {
            assert true;
        }
    }

    @Test
    public void testSingleSendAny() throws InterruptedException, TimeoutException {
        Socket socket = new Socket();
        SocketIdentifier dest = new SocketIdentifier("nodeA", "socketA");
        socket.newLink(dest, 1); // starts link with only 1 credit
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState linkState = fcManager.getLinkState(dest);

        // send message must result in the consumption of the credit
        assert linkState.credits == 1;
        socket.sendMsg(0L);
        assert linkState.credits == 0
                && !linkState.hasNewRequests()
                && linkState.countUnsettledRequests() == 0
                && fcManager.countNewGeneralRequests() == 0
                && fcManager.countUnsettledGeneralRequests() == 0;
    }

    @Test
    public void testSendAnyTimeout() throws InterruptedException {
        Socket socket = new Socket();
        SocketIdentifier dest = new SocketIdentifier("nodeA", "socketA");
        socket.newLink(dest, 0); // starts link with 0 credits
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState linkState = fcManager.getLinkState(dest);

        // should time out
        assert socket.sendMsg(0L) == null
                && linkState.credits == 0
                && !linkState.hasNewRequests()
                && linkState.countUnsettledRequests() == 0
                && fcManager.countNewGeneralRequests() == 0
                && fcManager.countUnsettledGeneralRequests() == 0;
    }

    @Test
    public void testSendAnyReplenishCredits(){
        Socket socket = new Socket();
        SocketIdentifier dest = new SocketIdentifier("nodeA", "socketA");
        socket.newLink(dest, 0); // starts link with 0 credits
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState linkState = fcManager.getLinkState(dest);
        assert linkState.credits == 0;

        // starts thread that will wait until an availability request
        // for the linkState is created. Then replenishes 1 credit to allow
        // the message to be sent.
        new Thread(new WaitWithLockAcquiredAndDoSomething(socket) {
            @Override
            protected boolean waitCondition() {
                return !fcManager.hasNewGeneralRequests();
            }

            @Override
            protected void customBehaviour() {
                // make some credits verifications and replenish 1 credit
                assert linkState.credits == 0;
                socket.replenishCredits(dest, 1);
                assert linkState.credits == 1;
            }
        }).start();

        try {
            // shows the intention of sending a message
            socket.sendMsg(500L);
            assert linkState.credits == 0
                    && !linkState.hasNewRequests()
                    && linkState.countUnsettledRequests() == 0
                    && fcManager.countNewGeneralRequests() == 0
                    && fcManager.countUnsettledGeneralRequests() == 0;
        } catch (Exception e) {
            assert false;
        }
    }

    private void testRaceForCreditsBetweenSendAnyRequests(int N, int creditsA, int creditsB) throws InterruptedException {
        Socket socket = new Socket();
        SocketIdentifier destA = new SocketIdentifier("nodeA", "socketA");
        SocketIdentifier destB = new SocketIdentifier("nodeB", "socketB");
        socket.newLink(destA, 0); // starts link with 0 credits
        socket.newLink(destB, 0); // starts link with 0 credits
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState linkA = fcManager.getLinkState(destA);
        FlowControlState linkB = fcManager.getLinkState(destB);

        Queue<Integer> sentA = new LinkedBlockingQueue<>();
        Queue<Integer> sentB = new LinkedBlockingQueue<>();

        // initiate threads
        Thread[] threads = new Thread[N];
        for(int i = 0; i < N; i++){
            int finalI = i;
            threads[i] = new Thread(() -> {
                try {
                    SocketIdentifier d = socket.sendMsg(200L);
                    if(d.equals(destA))
                        sentA.add(finalI);
                    else
                        sentB.add(finalI);
                } catch (Exception ignored) {}
            });
            threads[i].start();
        }

        // replenish credit(s)
        socket.replenishCredits(destA, creditsA);
        socket.replenishCredits(destB, creditsB);

        // wait for threads
        for(Thread t : threads)
            t.join();

        assert sentA.size() + sentB.size() == Math.min(N, creditsA + creditsB);
                assert sentA.size() <= creditsA && sentA.size() <= N;
                assert sentB.size() <= creditsB && sentB.size() <= N;
                assert linkA.credits == creditsA - sentA.size();
                assert !linkA.hasNewRequests();
                assert linkA.countUnsettledRequests() == 0;
                assert linkB.credits == creditsB - sentB.size();
                assert !linkB.hasNewRequests();
                assert linkB.countUnsettledRequests() == 0;
    }

    @TestFactory
    public List<DynamicTest> testRaceForCreditsBetweenSendAnyRequests(){
        List<Integer> NList = List.of(2,2,2,2,5,10,20,20),
                creditsAList = List.of(0,1,0,1,3,5,10,40),
                creditsBList = List.of(0,0,1,1,3,2,5,40);
        List<DynamicTest> tests = new ArrayList<>();
        int i = 0;
        for(int N : NList){
            int creditsA = creditsAList.get(i);
            int creditsB = creditsBList.get(i);
            tests.add(
                    DynamicTest.dynamicTest(
                            "Test race for credits between send any requests (N=" +
                                    N + ", creditsA=" + creditsA + ", creditsB=" + creditsB + ")" ,
                            () -> testRaceForCreditsBetweenSendAnyRequests(N, creditsA, creditsB)));
            i++;
        }
        return tests;
    }

    @Test
    public void testSendGeneralLinkClosedResultInTimeout() throws InterruptedException {
        Socket socket = new Socket();
        SocketIdentifier dest = new SocketIdentifier("nodeA", "socketA");
        socket.newLink(dest, 0); // starts link with 0 credits
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState link = fcManager.getLinkState(dest);

        new Thread(new WaitWithLockAcquiredAndDoSomething(socket) {
            @Override
            protected boolean waitCondition() {
                return !fcManager.hasGeneralRequests();
            }

            @Override
            protected void customBehaviour() {
                // closes the link
                socket.closeLink(dest);
            }
        }).start();

        assert socket.sendMsg(200L) == null;
    }

    // makes a general request that reached the state of "unsettled" become "new" again.
    // Allows introducing an amount of time which the lock should be released, allowing
    // the awakened interested thread to reacquire the lock and get back to sleep
    // after verifying that the waiting conditions have not been fulfilled.
    public void testSendGeneralLinkClosedUnsettledToNew(int sleep) throws InterruptedException {
        Socket socket = new Socket();
        SocketIdentifier destA = new SocketIdentifier("nodeA", "socketA");
        SocketIdentifier destB = new SocketIdentifier("nodeB", "socketB");
        socket.newLink(destA, 0); // starts link with 0 credits
        socket.newLink(destB, 0); // starts link with 0 credits
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;

        new Thread(new WaitWithLockAcquiredAndDoSomething(socket) {
            @Override
            protected boolean waitCondition() {
                return !fcManager.hasGeneralRequests();
            }

            @Override
            protected void customBehaviour() {
                // Check that there aren't unsettled general request
                assert !fcManager.hasUnsettledGeneralRequests();

                // Add credits to linkA
                socket.replenishCredits(destA, 1);

                // Check that a general request became unsettled
                assert !fcManager.hasNewGeneralRequests()
                        && fcManager.countUnsettledGeneralRequests() == 1;

                // closes the link
                socket.closeLink(destA);

                // Check that the unsettled general request became a new general request again
                assert fcManager.countNewGeneralRequests() == 1
                        && !fcManager.hasUnsettledGeneralRequests();

                // sleep to allow the awakened interested thread to reacquire the lock
                if(sleep > 0) {
                    try {
                        boolean ignored = socket.lock.newCondition().await(sleep, TimeUnit.MILLISECONDS);
                    }catch (InterruptedException ignored) {}
                }

                // add credit to link B so that the request can be fulfilled
                socket.replenishCredits(destB, 1);
            }
        }).start();

        // sets a confortable value for the waiting timeout
        assert socket.sendMsg(sleep < 50L ? 200L : sleep * 4L).equals(destB)
                && !fcManager.hasGeneralRequests();
    }

    @TestFactory
    public List<DynamicTest> testFactorySendGeneralLinkClosedUnsettledToNew(){
        List<Integer> sleepTimes = List.of(0, 25, 50, 100);
        List<DynamicTest> tests = new ArrayList<>();
        for(int sleep : sleepTimes)
            tests.add(DynamicTest.dynamicTest("Test general send with link being closed " +
                    "and resulting in an unsettled request become new again. " +
                    "(sleep= " + sleep + ")",
                    () -> testSendGeneralLinkClosedUnsettledToNew(sleep)));
        return tests;
    }

    // Same as the test above, but a new link is added after the first link is removed.
    public void testSendGeneralLinkClosedUnsettledToNew2(int sleep) throws InterruptedException {
        Socket socket = new Socket();
        SocketIdentifier destA = new SocketIdentifier("nodeA", "socketA");
        SocketIdentifier destB = new SocketIdentifier("nodeB", "socketB");
        socket.newLink(destA, 0); // starts link with 0 credits
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;

        new Thread(new WaitWithLockAcquiredAndDoSomething(socket) {
            @Override
            protected boolean waitCondition() {
                return !fcManager.hasGeneralRequests();
            }

            @Override
            protected void customBehaviour() {
                // Check that there aren't unsettled general request
                assert !fcManager.hasUnsettledGeneralRequests();

                // Add credits to linkA
                socket.replenishCredits(destA, 1);

                // Check that a general request became unsettled
                assert !fcManager.hasNewGeneralRequests()
                        && fcManager.countUnsettledGeneralRequests() == 1;

                // closes the link
                socket.closeLink(destA);

                // Check that the unsettled general request became a new general request again
                assert fcManager.countNewGeneralRequests() == 1
                        && !fcManager.hasUnsettledGeneralRequests();

                // sleep to allow the awakened interested thread to reacquire the lock
                if(sleep > 0) {
                    try {
                        boolean ignored = socket.lock.newCondition().await(sleep, TimeUnit.MILLISECONDS);
                    }catch (InterruptedException ignored) {}
                }

                // establishes link B with 1 credit so that the request can be fulfilled
                socket.newLink(destB, 1);
            }
        }).start();

        // sets a confortable value for the waiting timeout
        assert socket.sendMsg(sleep < 50L ? 200L : sleep * 4L).equals(destB)
                && !fcManager.hasGeneralRequests();
    }

    @TestFactory
    public List<DynamicTest> testFactorySendGeneralLinkClosedUnsettledToNew2(){
        List<Integer> sleepTimes = List.of(0, 25, 50, 100);
        List<DynamicTest> tests = new ArrayList<>();
        for(int sleep : sleepTimes)
            tests.add(DynamicTest.dynamicTest("Test (Version 2) general send with link being closed " +
                            "and resulting in an unsettled request become new again. " +
                            "(sleep= " + sleep + ")",
                    () -> testSendGeneralLinkClosedUnsettledToNew2(sleep)));
        return tests;
    }

    private void testRaceForCredits(int nGeneral, int nSpecificA, int nSpecificB, int creditsA, int creditsB) throws InterruptedException {
        Socket socket = new Socket();
        SocketIdentifier destA = new SocketIdentifier("nodeA", "socketA");
        SocketIdentifier destB = new SocketIdentifier("nodeB", "socketB");
        socket.newLink(destA, 0); // starts link with 0 credits
        socket.newLink(destB, 0); // starts link with 0 credits
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState linkA = fcManager.getLinkState(destA);
        FlowControlState linkB = fcManager.getLinkState(destB);

        Queue<Integer> sentA = new LinkedBlockingQueue<>();
        Queue<Integer> sentB = new LinkedBlockingQueue<>();

        // initiate threads
        Thread[] threads = new Thread[nGeneral + nSpecificA + nSpecificB];
        int i = 0;

        // initiate threads that make general requests
        for(i = 0; i < nGeneral; i++){
            int finalI = i;
            threads[i] = new Thread(() -> {
                try {
                    SocketIdentifier d = socket.sendMsg(500L);
                    if(d.equals(destA))
                        sentA.add(finalI);
                    else
                        sentB.add(finalI);
                } catch (Exception ignored) {}
            });
            threads[i].start();
        }

        // initiate threads that make requests to link A
        for(;i < nGeneral + nSpecificA; i++){
            int finalI = i;
            threads[i] = new Thread(() -> {
                try {
                    if(socket.sendMsg(destA, 500L))
                        sentA.add(finalI);
                } catch (Exception ignored) {}
            });
            threads[i].start();
        }

        // initiate threads that make requests to link B
        for(;i < threads.length; i++){
            int finalI = i;
            threads[i] = new Thread(() -> {
                try {
                    if(socket.sendMsg(destB, 500L))
                        sentB.add(finalI);
                } catch (Exception ignored) {}
            });
            threads[i].start();
        }

        // replenish credit(s)
        socket.replenishCredits(destA, creditsA);
        socket.replenishCredits(destB, creditsB);

        // wait for threads
        for(Thread t : threads)
            t.join();

        int N = threads.length;
        //System.out.println("sentA: " + sentA.size() + " | sentB: " + sentB.size()
        //        + " | sentAll: " + (sentA.size() + sentB.size())
        //        + " | N: " + N
        //        + " | creditsA: " + creditsA
        //        + " | creditsB: " + creditsB
        //        + " | totalCredits: " + (creditsA + creditsB));

        if(sentA.size() + sentB.size() == N)
            assert sentA.size() + sentB.size() == Math.min(N, creditsA + creditsB);
        else
            assert (sentA.size() + sentB.size()) == (creditsA + creditsB) - (linkA.credits + linkB.credits);
        assert sentA.size() <= creditsA && sentA.size() <= N;
        assert sentB.size() <= creditsB && sentB.size() <= N;
        assert linkA.credits == creditsA - sentA.size();
        assert !linkA.hasNewRequests();
        assert linkA.countUnsettledRequests() == 0;
        assert linkB.credits == creditsB - sentB.size();
        assert !linkB.hasNewRequests();
        assert linkB.countUnsettledRequests() == 0;
    }

    @TestFactory
    public List<DynamicTest> testRaceForCredits(){
        List<Integer> nGeneralList = List.of(2,2,2,2,5,10,20,20),
                    nSpecificAList = List.of(2,2,2,2,5,10,20,20),
                    nSpecificBList = List.of(2,2,2,2,5,10,20,20),
                      creditsAList = List.of(0,1,0,1,3,5,10,40),
                      creditsBList = List.of(0,0,1,1,3,2,5,40);
        List<DynamicTest> tests = new ArrayList<>();
        int i = 0;
        for(int nGeneral : nGeneralList){
            int nSpecificA = nSpecificAList.get(i);
            int nSpecificB = nSpecificBList.get(i);
            int creditsA = creditsAList.get(i);
            int creditsB = creditsBList.get(i);
            tests.add(
                    DynamicTest.dynamicTest(
                            "Test race for credits (nGeneral=" +
                                    nGeneral + ", nSpecificA=" + nSpecificA +
                                    ", nSpecificB=" + nSpecificB + ", creditsA=" + creditsA +
                                    ", creditsB=" + creditsB + ")" ,
                            () -> testRaceForCredits(nGeneral, nSpecificA, nSpecificB, creditsA, creditsB)));
            i++;
        }
        return tests;
    }

    @TestFactory
    public List<DynamicTest> testRaceForCreditsRandom(){
        List<Integer> nGeneralList = new ArrayList<>(),
                nSpecificAList = new ArrayList<>(),
                nSpecificBList = new ArrayList<>(),
                creditsAList = new ArrayList<>(),
                creditsBList = new ArrayList<>();

        Random random = new Random();
        for(int n = 0; n < 20; n++){
            nGeneralList.add(random.nextInt(50)); // random non-negative value for nGeneral
            nSpecificAList.add(random.nextInt(50)); // random non-negative value for nSpecificA
            nSpecificBList.add(random.nextInt(50)); // random non-negative value for nSpecificB
            creditsAList.add(random.nextInt(500)); // random non-negative value for creditsA
            creditsBList.add(random.nextInt(500)); // random non-negative value for creditsB
        }

        List<DynamicTest> tests = new ArrayList<>();
        int i = 0;
        for(int nGeneral : nGeneralList){
            int nSpecificA = nSpecificAList.get(i);
            int nSpecificB = nSpecificBList.get(i);
            int creditsA = creditsAList.get(i);
            int creditsB = creditsBList.get(i);
            tests.add(
                    DynamicTest.dynamicTest(
                            "Random test race for credits (nGeneral=" +
                                    nGeneral + ", nSpecificA=" + nSpecificA +
                                    ", nSpecificB=" + nSpecificB + ", creditsA=" + creditsA +
                                    ", creditsB=" + creditsB + ")" ,
                            () -> testRaceForCredits(nGeneral, nSpecificA, nSpecificB, creditsA, creditsB)));
            i++;
        }
        return tests;
    }

    public void testTrySendUntilNoCredits(int nGeneral, int nSpecificA, int nSpecificB, int creditsA, int creditsB) throws InterruptedException {
        Socket socket = new Socket();
        SocketIdentifier destA = new SocketIdentifier("nodeA", "socketA");
        SocketIdentifier destB = new SocketIdentifier("nodeB", "socketB");
        socket.newLink(destA, 0); // starts link with 0 credits
        socket.newLink(destB, 0); // starts link with 0 credits
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState linkA = fcManager.getLinkState(destA);
        FlowControlState linkB = fcManager.getLinkState(destB);

        Lock startLock = new ReentrantLock();
        Condition startCond = startLock.newCondition();
        var ref = new Object() {
            boolean canStart = false;
        };

        // initiate threads
        Thread[] threads = new Thread[nGeneral + nSpecificA + nSpecificB];
        int i = 0;

        // initiate threads that make general requests
        for(i = 0; i < nGeneral; i++){
            threads[i] = new Thread(() -> {
                try {
                    try{
                        startLock.lock();
                        while (!ref.canStart)
                            startCond.await();
                    }finally {
                        startLock.unlock();
                    }
                    while(socket.trySendMsg());
                } catch (Exception ignored) {}
            });
            threads[i].start();
        }

        // initiate threads that make requests to link A
        for(;i < nGeneral + nSpecificA; i++){
            threads[i] = new Thread(() -> {
                try {
                    try{
                        startLock.lock();
                        while (!ref.canStart)
                            startCond.await();
                    }finally {
                        startLock.unlock();
                    }
                    while(socket.trySendMsg(destA));
                } catch (Exception ignored) {}
            });
            threads[i].start();
        }

        // initiate threads that make requests to link B
        for(;i < threads.length; i++){
            threads[i] = new Thread(() -> {
                try {
                    try{
                        startLock.lock();
                        while (!ref.canStart)
                            startCond.await();
                    }finally {
                        startLock.unlock();
                    }
                    while(socket.trySendMsg(destB));
                } catch (Exception ignored) {}
            });
            threads[i].start();
        }

        long start;
        try {
            startLock.lock();
            ref.canStart = true;
            start = System.currentTimeMillis();
            startCond.signalAll();
        }finally {
            startLock.unlock();
        }

        // replenish credit(s)
        socket.replenishCredits(destA, creditsA);
        socket.replenishCredits(destB, creditsB);

        // wait for threads
        for(Thread t : threads)
            t.join();

        long end = System.currentTimeMillis() - start;
        System.out.println("Time(ms): " + end);

        assert linkA.credits == 0;
        assert !linkA.hasNewRequests();
        assert linkA.countUnsettledRequests() == 0;
        assert linkB.credits == 0;
        assert !linkB.hasNewRequests();
        assert linkB.countUnsettledRequests() == 0;
    }

    @TestFactory
    public List<DynamicTest> testTrySendUntilNoCredits(){
        List<Integer> nGeneralList = new ArrayList<>(),
                nSpecificAList = new ArrayList<>(),
                nSpecificBList = new ArrayList<>(),
                creditsAList = new ArrayList<>(),
                creditsBList = new ArrayList<>();

        Random random = new Random();
        for(int n = 0; n < 20; n++){
            nGeneralList.add(random.nextInt(50)); // random non-negative value for nGeneral
            nSpecificAList.add(random.nextInt(50)); // random non-negative value for nSpecificA
            nSpecificBList.add(random.nextInt(50)); // random non-negative value for nSpecificB
            creditsAList.add(random.nextInt(500)); // random non-negative value for creditsA
            creditsBList.add(random.nextInt(500)); // random non-negative value for creditsB
        }

        List<DynamicTest> tests = new ArrayList<>();
        int i = 0;
        for(int nGeneral : nGeneralList){
            int nSpecificA = nSpecificAList.get(i);
            int nSpecificB = nSpecificBList.get(i);
            int creditsA = creditsAList.get(i);
            int creditsB = creditsBList.get(i);
            tests.add(
                    DynamicTest.dynamicTest(
                            "Random test try send until no credits (nGeneral=" +
                                    nGeneral + ", nSpecificA=" + nSpecificA +
                                    ", nSpecificB=" + nSpecificB + ", creditsA=" + creditsA +
                                    ", creditsB=" + creditsB + ")" ,
                            () -> testTrySendUntilNoCredits(nGeneral, nSpecificA, nSpecificB, creditsA, creditsB)));
            i++;
        }
        return tests;
    }

    // if the socket lock is set to be fair, and all interested threads are waiting,
    // the requests are handled in the request order. This means, that to achieve total order
    // the only requirement is to not allow immediate returns when the link is available.
    // However, as long as threads do not starve and some sense of order exists, the total
    // order should not be required.
    @Test
    public void testFairness() throws InterruptedException {
        Socket socket = new Socket(true);
        SocketIdentifier dest = new SocketIdentifier("nodeA", "socketA");
        socket.newLink(dest, 0); // starts link with 0 credits
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState link = fcManager.getLinkState(dest);

        int nGeneral = 50;
        int nSpecific = 50;

        AtomicInteger ticketCounter = new AtomicInteger(0),
                receivedCounter = new AtomicInteger(0);

        // initiate threads
        Thread[] threads = new Thread[nGeneral + nSpecific];
        int i = 0;

        // initiate threads that make general requests
        for(i = 0; i < nGeneral; i++){
            threads[i] = new Thread(() -> {
                try {
                    socket.lock.lock();
                    int ticket = ticketCounter.getAndIncrement();
                    socket.sendMsg();
                    assert receivedCounter.getAndIncrement() == ticket;
                }catch (Exception ignored) {
                }finally {
                    socket.lock.unlock();
                }
            });
            threads[i].start();
        }

        // initiate threads that make requests to link
        for(;i < threads.length ; i++){
            threads[i] = new Thread(() -> {
                try {
                    socket.lock.lock();
                    int ticket = ticketCounter.getAndIncrement();
                    socket.sendMsg(dest);
                    assert receivedCounter.getAndIncrement() == ticket;
                }catch (Exception ignored) {
                }finally {
                    socket.lock.unlock();
                }
            });
            threads[i].start();
        }

        // waits until all threads are initialized
        Thread.sleep(100);

        // replenish credit(s)
        socket.replenishCredits(dest, threads.length);

        // wait for threads
        for(Thread t : threads)
            t.join();

        assert link.credits == 0;
        assert !link.hasNewRequests();
        assert link.countUnsettledRequests() == 0;
    }

    /*
    // TODO - to test total fairness if it is implemented
    @Test
    public void testTotalFairness() throws InterruptedException {
        Socket socket = new Socket(true);
        SocketIdentifier dest = new SocketIdentifier("nodeA", "socketA");
        socket.newLink(dest, 0); // starts link with 0 credits
        FlowControlState link = fcManager.getLinkState(dest);

        int nGeneral = 5;
        int nSpecific = 5;

        AtomicInteger ticketCounter = new AtomicInteger(0),
                      receivedCounter = new AtomicInteger(0);

        // initiate threads
        Thread[] threads = new Thread[nGeneral + nSpecific];
        int i = 0;

        // initiate threads that make general requests
        for(i = 0; i < nGeneral; i++){
            threads[i] = new Thread(() -> {
                try {
                    socket.lock.lock();
                    int ticket = ticketCounter.getAndIncrement();
                    while(socket.sendMsg(500L) != null)
                        assert receivedCounter.getAndIncrement() == ticket;
                }catch (Exception ignored) {
                }finally {
                    socket.lock.unlock();
                }
            });
            threads[i].start();
        }

        // initiate threads that make requests to link
        for(;i < threads.length ; i++){
            threads[i] = new Thread(() -> {
                try {
                    socket.lock.lock();
                    int ticket = ticketCounter.getAndIncrement();
                    while(socket.sendMsg(dest, 200L))
                        assert receivedCounter.getAndIncrement() == ticket;
                }catch (Exception ignored) {
                }finally {
                    socket.lock.unlock();
                }
            });
            threads[i].start();
        }

        // waits until all threads are initialized
        Thread.sleep(100);

        // replenish credit(s)
        socket.replenishCredits(dest, 100);

        // wait for threads
        for(Thread t : threads)
            t.join();

        assert link.credits == 0;
        assert !link.hasNewRequests();
        assert link.countUnsettledRequests() == 0;
    }

     */

    // When a socket waits for the permission to send, but does not send,
    // waiting threads that could send messages will starve.
    @Test
    public void testWaitButDontConsumeCredit() throws InterruptedException, LinkClosedException {
        Socket socket = new Socket(true);
        SocketIdentifier dest = new SocketIdentifier("nodeA", "socketA");
        socket.newLink(dest, 0); // starts link with only 1 credit
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState linkState = fcManager.getLinkState(dest);
        AtomicBoolean sent = new AtomicBoolean(false);

        Thread t1 = new Thread(() -> {
            try {
                Thread.sleep(100L);
                socket.replenishCredits(dest, 1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        t1.start();

        Thread t2 = new Thread(() ->{
            try {
                Thread.sleep(50L);
                sent.set(socket.sendMsg(dest, 200L));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (LinkClosedException e) {
                throw new RuntimeException(e);
            }
        });
        t2.start();

        try {
            socket.lock.lock();
            boolean wait = fcManager.waitForLinkAvailability(dest, 200L);
            assert wait;
            fcManager.creditNotConsumed(dest);
        }finally {
            socket.lock.unlock();
        }

        t1.join();
        t2.join();

        assert sent.get();
    }

    @Test
    public void testWaitButDontConsumeCredit2() throws InterruptedException, LinkClosedException {
        Socket socket = new Socket(true);
        SocketIdentifier dest = new SocketIdentifier("nodeA", "socketA");
        socket.newLink(dest, 0); // starts link with only 1 credit
        FlowControlCoordinator fcManager = (FlowControlCoordinator) socket.fcManager;
        FlowControlState linkState = fcManager.getLinkState(dest);
        AtomicBoolean sent1 = new AtomicBoolean(false), sent2 = new AtomicBoolean(false);

        Thread t1 = new Thread(() -> {
            try {
                Thread.sleep(100L);
                socket.replenishCredits(dest, 1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        t1.start();

        Thread t2 = new Thread(() ->{
            try {
                Thread.sleep(50L);
                sent1.set(socket.sendMsg(dest, 200L));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (LinkClosedException e) {
                throw new RuntimeException(e);
            }
        });
        t2.start();

        Thread t3 = new Thread(() ->{
            try {
                Thread.sleep(50L);
                sent2.set(socket.sendMsg(dest, 200L));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (LinkClosedException e) {
                throw new RuntimeException(e);
            }
        });
        t3.start();

        try {
            socket.lock.lock();
            boolean wait = fcManager.waitForLinkAvailability(dest, 200L);
            assert wait;
            fcManager.creditNotConsumed(dest);
        }finally {
            socket.lock.unlock();
        }

        socket.replenishCredits(dest, 1);

        t1.join();
        t2.join();
        t3.join();

        assert sent1.get();
        assert sent2.get();
    }

    /* ***** Auxiliary Stuff ***** */

    private static abstract class WaitWithLockAcquiredAndDoSomething implements Runnable{
        private final Socket socket;
        private Exception e = null;
        public WaitWithLockAcquiredAndDoSomething(Socket socket) {
            this.socket = socket;
        }

        @Override
        public final void run() {
            new Thread(() -> {
                try {
                    socket.lock.lock();
                    // create socket condition for this test to wait with the lock acquired
                    Condition cond = socket.lock.newCondition();
                    // wait until there is an availability request
                    while(waitCondition())
                        cond.await(10, TimeUnit.MILLISECONDS);
                    // perform custom behaviour
                    customBehaviour();
                } catch (Exception e) {
                    this.e = e;
                    throw new RuntimeException(e);
                } finally {
                    socket.lock.unlock();
                }
            }).start();
        }

        protected abstract boolean waitCondition();
        protected abstract void customBehaviour();
    }

}
