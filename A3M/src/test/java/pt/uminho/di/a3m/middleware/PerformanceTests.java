package pt.uminho.di.a3m.middleware;

import haslab.eo.TransportAddress;
import pt.uminho.di.a3m.core.A3MMiddleware;
import pt.uminho.di.a3m.core.Socket;
import pt.uminho.di.a3m.sockets.push_pull.PullSocket;
import pt.uminho.di.a3m.sockets.push_pull.PushSocket;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.concurrent.Semaphore;

public class PerformanceTests {

    private static void receiveMsgs(Socket socket, final int nrMsgs) throws InterruptedException {
        int i = 0;
        byte[] msg;
        while (i < nrMsgs) {
            msg = socket.receive();
            if (msg != null) i++;
        }
    }

    private static void sendRandomMsgs(Socket socket, final int nrMsgs, final byte[] msgArr, final Random random) throws InterruptedException {
        for (int i = 0; i < nrMsgs; i++) {
            random.nextBytes(msgArr);
            socket.send(msgArr);
        }
    }

    public static void main(String[] args) throws SocketException, UnknownHostException, InterruptedException {
        A3MMiddleware senderMiddleware = A3MMiddleware.startMiddleware("SenderNode", 11111);
        A3MMiddleware receiverMiddleware = A3MMiddleware.startMiddleware("ReceiverNode", 22222);
        senderMiddleware.registerNode("ReceiverNode",new TransportAddress("localhost",22222));
        PushSocket senderSocket = PushSocket.startSocket(senderMiddleware, "Sender");
        PullSocket receiverSocket = PullSocket.startSocket(receiverMiddleware, "Receiver");
        receiverSocket.setOption("capacity",500);
        senderSocket.link(receiverSocket.getId());
        while (!senderSocket.isLinked(receiverSocket.getId()))
            Thread.onSpinWait();

        final int nrMsgs = 10000;
        final int nrTests = 100; // first test is warm up
        Semaphore semaphore = new Semaphore(0);

        Thread receiverT = new Thread(() -> {
            try {
                for (int i = 0; i < nrTests; i++) {
                    receiveMsgs(receiverSocket, nrMsgs);
                    semaphore.release();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, "ReceiverT");
        receiverT.start();

        Random random = new Random(2024);
        byte[] msg = new byte[1024];
        long[] times = new long[nrTests];
        for (int i = 0; i < nrTests; i++) {
            long time = System.currentTimeMillis();
            sendRandomMsgs(senderSocket, nrMsgs, msg, random);
            semaphore.acquire();
            time = System.currentTimeMillis() - time;
            times[i] = time;
            System.out.println("Test " + i + ": " + time + "ms");
        }

        double avg = 0;
        double warmedUpAvg = 0;
        for (int i = 0; i < nrTests; i++) {
            avg += (double) times[i];
            if(i > nrTests / 2)
                warmedUpAvg += (double) times[i];
        }

        avg /= (double) nrTests;
        warmedUpAvg /= (double) (nrTests / 2);

        System.out.println("Time average: " + avg);
        System.out.println("Time average (exclusing warm up rounds): " + warmedUpAvg);

        System.exit(0);
    }
}
