package pt.uminho.di.a3m.sockets.auxiliary;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.ToIntFunction;

public class OrderedQueue<E> extends PriorityQueue<E> {
    private final ToIntFunction<E> orderExtractor;
    private int next = 0;

    /**
     * Creates ordered queue.
     * @param orderExtractor function that allows extracting the order value (integer)
     *                       from the elements.
     * @param next order value of the first element that can be extracted from the queue.
     */
    public OrderedQueue(ToIntFunction<E> orderExtractor, int next) {
        super(orderComparator(orderExtractor));
        this.orderExtractor = orderExtractor;
        this.next = next;
    }

    public OrderedQueue(ToIntFunction<E> orderExtractor) {
        this(orderExtractor, 0);
    }

    private static <E> Comparator<E> orderComparator(ToIntFunction<E> orderExtractor) {
        if(orderExtractor == null)
            throw new IllegalArgumentException("Order extractor is null.");
        return (e1, e2) -> {
            if (e1 == null) return -1;
            if (e2 == null) return 1;
            return Integer.compare(
                    orderExtractor.applyAsInt(e1),
                    orderExtractor.applyAsInt(e2));
        };
    }

    @Override
    public E peek() {
        E e = super.peek();
        // make peek() return null if the first element
        // in the queue is not the next in order.
        if(e != null && orderExtractor.applyAsInt(e) != next)
            e = null;
        return e;
    }

    @Override
    public E poll() {
        // make poll() only extract the first element of the queue,
        // when it is the next one in order.
        E e = peek();
        if(e != null) {
            next++;
            return super.poll();
        }else
            return null;
    }
}
