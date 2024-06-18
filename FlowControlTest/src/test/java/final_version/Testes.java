package final_version;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Testes {

    private Receiver initReceiver(int capacity, float batchSizePercentage){
        Receiver receiver = new Receiver(capacity, batchSizePercentage);
        receiver.run();
        return receiver;
    }

    private Sender initSender(int capacity){
        Sender sender = new Sender(capacity);
        sender.run();
        return sender;
    }

    private void init(Receiver receiver, Sender sender){
        receiver.setSender(sender);
        sender.setReceiver(receiver);
    }

    private void waitUntilAllMessagesAreProcessed(Receiver receiver, Sender sender) throws InterruptedException {
        Thread.sleep(100);
        while(!receiver.isQueueEmpty() || !sender.isQueueEmpty()) {}
    }

    // Send N messages, with N equal to batchSize - 1. Sender must end up with credits equal to capacity - (batchSize - 1).
    @Test
    public void testConsumeUnderBatchSize() throws InterruptedException {
        // initialization
        int capacity = 10;
        float batchSizePercentage = 0.5f;
        Receiver receiver = initReceiver(capacity, batchSizePercentage);
        Sender sender = initSender(capacity);
        init(receiver,sender);

        // test logic
        for(int i = 0; i < capacity * batchSizePercentage - 1; i++) {
            boolean sent = sender.sendMsg();
            assert sent;
        }

        for(int i = 0; i < capacity * batchSizePercentage - 1; i++)
            receiver.deliverDataMsg();

        waitUntilAllMessagesAreProcessed(receiver, sender);

        assert sender.getCredits() == capacity - (capacity * batchSizePercentage - 1);

        // prints
        System.out.println(receiver.toString());
        System.out.println(sender.toString());
    }

    // Send N messages, with N equal to batchSize. Sender must end up with credits equal to capacity.
    @Test
    public void testConsumeBatchSize() throws InterruptedException {
        // initialization
        int capacity = 10;
        float batchSizePercentage = 0.5f;
        Receiver receiver = initReceiver(capacity, batchSizePercentage);
        Sender sender = initSender(capacity);
        init(receiver,sender);

        // test logic
        for(int i = 0; i < capacity * batchSizePercentage; i++)
            sender.sendMsg();

        for(int i = 0; i < capacity * batchSizePercentage; i++)
            receiver.deliverDataMsg();

        waitUntilAllMessagesAreProcessed(receiver, sender);

        assert sender.getCredits() == capacity;

        // prints
        System.out.println(receiver.toString());
        System.out.println(sender.toString());
    }

    // Send N messages, with N equal to capacity. Sender must end up with all available credits (credits == capacity)
    // Only works if (capacity * batchSizePercentage) is an integer.
    @Test
    public void testConsumeAllCredits() throws InterruptedException {
        // initialization
        int capacity = 10;
        float batchSizePercentage = 0.2f;
        Receiver receiver = initReceiver(capacity, batchSizePercentage);
        Sender sender = initSender(capacity);
        init(receiver,sender);

        // batch size must be a divisor of the capacity
        assert receiver.getCapacity() % receiver.getBatchSize() == 0;

        // test logic
        for(int i = 0; i < capacity; i++)
            sender.sendMsg();

        for(int i = 0; i < capacity; i++)
            receiver.deliverDataMsg();

        waitUntilAllMessagesAreProcessed(receiver, sender);

        assert sender.getCredits() == capacity;

        // prints
        System.out.println(receiver.toString());
        System.out.println(sender.toString());
    }

    // change capacity from 10 to 0. The message to alter the capacity
    // is sent after N data messages are sent by the sender and received by the receiver.
    public void auxTestMaxCreditsToZero(int N) throws InterruptedException {
        // initialization
        int capacity = 10;
        float batchSizePercentage = 0.2f;
        Receiver receiver = initReceiver(capacity, batchSizePercentage);
        Sender sender = initSender(capacity);
        init(receiver,sender);

        assert sender.getCredits() == capacity;

        // sends N data msgs
        for(int i = 0; i < N; i++)
            sender.sendMsg();

        // delivers N data msgs
        for(int i = 0; i < N; i++)
            receiver.deliverDataMsg();

        // changes capacity
        receiver.setCapacity(0);

        waitUntilAllMessagesAreProcessed(receiver, sender);

        assert sender.getCredits() == 0;

        // prints
        System.out.println(receiver.toString());
        System.out.println(sender.toString());
    }

    @TestFactory
    Collection<DynamicTest> testMaxCreditsToZero(){
        List<DynamicTest> list = new ArrayList<>();
        for(int N = 0 ; N <= 10; N++){
            int finalN = N;
            list.add(DynamicTest.dynamicTest("capacity to zero (N=" + N + ")", () -> auxTestMaxCreditsToZero(finalN)));
        }
        return list;
    }


    // change capacity from 10 to 0. The message to alter the capacity
    // is sent after N data messages are sent by the sender. The sender also tries to
    // send more messages before the message that removes credits is received. The receiver only
    // delivers the messages after the capacity is changed.
    // The amount of credits of the sender at the end must be symetric to the amount of messages
    // sent before changing the capacity (i.e. before receiving the message with negative credits).
    public void auxTestMaxCreditsToZero2(int N) throws InterruptedException {
        // initialization
        int capacity = 10;
        float batchSizePercentage = 0.2f;
        Receiver receiver = initReceiver(capacity, batchSizePercentage);
        Sender sender = initSender(capacity);
        init(receiver,sender);

        // sends N data msgs
        for(int i = 0; i < N; i++)
            sender.sendMsg();

        // changes capacity
        receiver.setCapacity(0);

        // attempts to send the rest of the data messages until the initial capacity are reached.
        int counter = 0; // counts the amount of messages that were sent before the credits message to
                         // change the capacity is received.
        for(int i = N; i < capacity; i++) {
            if(sender.sendMsg())
                counter++;
        }

        // delivers N data msgs
        for(int i = 0; i < N + counter; i++)
            receiver.deliverDataMsg();

        waitUntilAllMessagesAreProcessed(receiver, sender);

        assert sender.getCredits() == -(N + counter);

        // prints
        System.out.println(receiver.toString());
        System.out.println(sender.toString());
    }

    @TestFactory
    Collection<DynamicTest> testMaxCreditsToZero2(){
        List<DynamicTest> list = new ArrayList<>();
        for(int N = 0 ; N <= 10; N++){
            int finalN = N;
            list.add(DynamicTest.dynamicTest("capacity to zero (N=" + N + ")", () -> auxTestMaxCreditsToZero2(finalN)));
        }
        return list;
    }

    public void auxTestMaxCreditsToZeroToHalfInitialCredits(int N) throws InterruptedException {
        // initialization
        int capacity = 10;
        float batchSizePercentage = 0.2f;
        Receiver receiver = initReceiver(capacity, batchSizePercentage);
        Sender sender = initSender(capacity);
        init(receiver,sender);

        // sends N data msgs
        for(int i = 0; i < N; i++)
            sender.sendMsg();

        // changes capacity
        receiver.setCapacity(0);

        // attempts to send the rest of the data messages until the initial capacity are reached.
        int counter = 0; // counts the amount of messages that were sent before the credits message to
        // change the capacity is received.
        for(int i = N; i < capacity; i++) {
            if(sender.sendMsg())
                counter++;
        }

        receiver.setCapacity(5);

        // delivers N data msgs
        for(int i = 0; i < N + counter; i++)
            receiver.deliverDataMsg();

        waitUntilAllMessagesAreProcessed(receiver, sender);

        assert sender.getCredits() == 5;

        // prints
        System.out.println(receiver.toString());
        System.out.println(sender.toString());
    }

    @TestFactory
    Collection<DynamicTest> testMaxCreditsToZeroToHalfInitialCredits(){
        List<DynamicTest> list = new ArrayList<>();
        for(int N = 0 ; N <= 10; N++){
            int finalN = N;
            list.add(DynamicTest.dynamicTest("capacity to zero (N=" + N + ")", () -> auxTestMaxCreditsToZeroToHalfInitialCredits(finalN)));
        }
        return list;
    }

    public void auxTestMaxCreditsToZeroToHalfInitialCredits2(int N) throws InterruptedException {
        // initialization
        int capacity = 10;
        float batchSizePercentage = 0.2f;
        Receiver receiver = initReceiver(capacity, batchSizePercentage);
        Sender sender = initSender(capacity);
        init(receiver,sender);

        // sends N data msgs
        for(int i = 0; i < N; i++)
            sender.sendMsg();

        // changes capacity
        receiver.setCapacity(0);

        // attempts to send the rest of the data messages until the initial capacity are reached.
        int counter = 0; // counts the amount of messages that were sent before the credits message to
        // change the capacity is received.
        for(int i = N; i < capacity; i++) {
            if(sender.sendMsg())
                counter++;
        }

        // delivers N data msgs
        for(int i = 0; i < N + counter; i++)
            receiver.deliverDataMsg();

        receiver.setCapacity(5);

        waitUntilAllMessagesAreProcessed(receiver, sender);

        assert sender.getCredits() == 5;

        // prints
        System.out.println(receiver.toString());
        System.out.println(sender.toString());
    }

    @TestFactory
    Collection<DynamicTest> testMaxCreditsToZeroToHalfInitialCredits2(){
        List<DynamicTest> list = new ArrayList<>();
        for(int N = 0 ; N <= 10; N++){
            int finalN = N;
            list.add(DynamicTest.dynamicTest("capacity to zero (N=" + N + ")", () -> auxTestMaxCreditsToZeroToHalfInitialCredits2(finalN)));
        }
        return list;
    }
}
