package version2;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import version2.Receiver;
import version2.Sender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Testes {

    private Receiver initReceiver(int windowSize, float batchSizePercentage){
        Receiver receiver = new Receiver(windowSize, batchSizePercentage);
        receiver.run();
        return receiver;
    }

    private Sender initSender(int windowSize){
        Sender sender = new Sender(windowSize);
        sender.run();
        return sender;
    }

    private void init(Receiver receiver, Sender sender){
        receiver.setSender(sender);
        sender.setReceiver(receiver);
    }

    private void waitUntilAllMessagesAreProcessed(Receiver receiver, Sender sender) throws InterruptedException {
        Thread.sleep(1000);
        while(!receiver.isQueueEmpty() || !sender.isQueueEmpty()) {
        }
    }

    // Initialization only
    @Test
    public void testInitialization() throws InterruptedException {
        // initialization
        int windowSize = 10;
        float batchSizePercentage = 0.2f;
        Receiver receiver = initReceiver(windowSize, batchSizePercentage);
        Sender sender = initSender(windowSize);
        init(receiver,sender);

        // test logic
        Thread.sleep(1000);
        assert sender.getUnacked() == 0;

        // prints
        System.out.println(receiver.toString());
        System.out.println(sender.toString());
    }

    // Send N messages, with N equal to batchSize - 1. Sender must end up with credits equal to windowSize - (batchSize - 1).
    @Test
    public void testConsumeUnderBatchSize() throws InterruptedException {
        // initialization
        int windowSize = 10;
        float batchSizePercentage = 0.5f;
        Receiver receiver = initReceiver(windowSize, batchSizePercentage);
        Sender sender = initSender(windowSize);
        init(receiver,sender);

        // test logic
        for(int i = 0; i < windowSize * batchSizePercentage - 1; i++)
            sender.sendMsg();
        waitUntilAllMessagesAreProcessed(receiver, sender);
        assert sender.getUnacked() == windowSize * batchSizePercentage - 1;

        // prints
        System.out.println(receiver.toString());
        System.out.println(sender.toString());
    }

    // Send N messages, with N equal to batchSize. Sender must end up with credits equal to windowSize.
    @Test
    public void testConsumeBatchSize() throws InterruptedException {
        // initialization
        int windowSize = 10;
        float batchSizePercentage = 0.5f;
        Receiver receiver = initReceiver(windowSize, batchSizePercentage);
        Sender sender = initSender(windowSize);
        init(receiver,sender);

        // test logic
        for(int i = 0; i < windowSize * batchSizePercentage; i++)
            sender.sendMsg();
        waitUntilAllMessagesAreProcessed(receiver, sender);
        assert sender.getUnacked() == 0;

        // prints
        System.out.println(receiver.toString());
        System.out.println(sender.toString());
    }

    // Send N messages, with N equal to windowSize. Sender must end up with all available credits (credits == windowSize)
    // Only works if (windowSize * batchSizePercentage) is an integer.
    @Test
    public void testConsumeAllCredits() throws InterruptedException {
        // initialization
        int windowSize = 10;
        float batchSizePercentage = 0.2f;
        Receiver receiver = initReceiver(windowSize, batchSizePercentage);
        Sender sender = initSender(windowSize);
        init(receiver,sender);

        // test logic
        for(int i = 0; i < windowSize; i++)
            sender.sendMsg();
        waitUntilAllMessagesAreProcessed(receiver, sender);
        assert sender.getUnacked() == 0;

        // prints
        System.out.println(receiver.toString());
        System.out.println(sender.toString());
    }

    // change windowSize from 10 to 0. The message to alter the max credits
    // is sent after N data messages are sent by the sender.
    public void auxTestMaxCreditsToZero(int N) throws InterruptedException {
        // initialization
        int windowSize = 10;
        float batchSizePercentage = 0.2f;
        Receiver receiver = initReceiver(windowSize, batchSizePercentage);
        Sender sender = initSender(windowSize);
        init(receiver,sender);

        // test logic

        // sends N data msgs
        for(int i = 0; i < N; i++)
            sender.sendMsg();

        // changes max credits
        receiver.setWindowSize(0);

        // attempts to send the rest of the data messages until the initial max credits are reached.
        for(int i = N; i < windowSize; i++)
            sender.sendMsg();

        waitUntilAllMessagesAreProcessed(receiver, sender);
        assert sender.getWindowSize() == 0 && sender.getUnacked() == 0;

        // prints
        System.out.println(receiver.toString());
        System.out.println(sender.toString());
    }

    @TestFactory
    Collection<DynamicTest> testMaxCreditsToZero(){
        List<DynamicTest> list = new ArrayList<>();
        for(int N = 0 ; N <= 10; N++){
            int finalN = N;
            list.add(DynamicTest.dynamicTest("Max credits to zero (N=" + N + ")", () -> auxTestMaxCreditsToZero(finalN)));
        }
        return list;
    }
}
