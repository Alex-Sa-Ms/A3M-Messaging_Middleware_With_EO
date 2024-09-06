package pt.uminho.di.a3m.list;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Volatile list node for atomic operations.
 */
public class VListNode<T> implements IListNode<T>{
    T object;
    volatile VListNode<T> next;
    volatile VListNode<T> prev;

    private VListNode(VListNode<T> prev, VListNode<T> next, T object) {
        this.prev = prev;
        this.next = next;
        this.object = object;
    }

    private VListNode() {
        this.prev = this;
        this.next = this;
        this.object = null;
    }

    // Initializes a list. This corresponds to creating the head node.
    public static <T> VListNode<T> init() {
        return new VListNode<>();
    }


    public static <T> VListNode<T> create(T object) {
        VListNode<T> node = new VListNode<>(null, null, object);
        node.prev = node;
        node.next = node;
        return node;
    }
    @Override
    public String toString() {
        return "VolListNode" + IListNode.toString(this);
    }

    @Override
    public T getObject() {
        return object;
    }

    @Override
    public void setObject(T object) {
        this.object = object;
    }

    @Override
    public VListNode<T> getNext() {
        return next;
    }

    @Override
    public void setNext(IListNode<T> next) {
        if(next instanceof VListNode<T> vNext)
            this.next = vNext;
    }

    @Override
    public VListNode<T> getPrev() {
        return prev;
    }

    @Override
    public void setPrev(IListNode<T> prev) {
        if(prev instanceof VListNode<T> vPrev)
            this.prev = vPrev;
    }

    // ******* Atomic reference field updaters ******* //
    private static final AtomicReferenceFieldUpdater<VListNode, VListNode> nextUpdater =
            AtomicReferenceFieldUpdater.newUpdater(VListNode.class, VListNode.class, "next");
    private static final AtomicReferenceFieldUpdater<VListNode, VListNode> prevUpdater =
            AtomicReferenceFieldUpdater.newUpdater(VListNode.class, VListNode.class, "prev");

    public VListNode<T> getNextAtomic() {
        return (VListNode<T>) nextUpdater.get(this);
    }

    public VListNode<T> getPrevAtomic() {
        return (VListNode<T>) prevUpdater.get(this);
    }

    public void setNextAtomic(VListNode<T> newValue) {
        nextUpdater.set(this, newValue);
    }

    public void setPrevAtomic(VListNode<T> newValue) {
        prevUpdater.set(this, newValue);
    }

    public VListNode<T> getAndSetNextAtomic(VListNode<T> newValue) {
        return (VListNode<T>) nextUpdater.getAndSet(this, newValue);
    }

    public VListNode<T> getAndSetPrevAtomic(VListNode<T> newValue) {
        return (VListNode<T>) prevUpdater.getAndSet(this, newValue);
    }

    public boolean compareAndSetNextAtomic(VListNode<T> expected, VListNode<T> newValue) {
        return nextUpdater.compareAndSet(this, expected, newValue);
    }

    public boolean compareAndSetPrevAtomic(VListNode<T> expected, VListNode<T> newValue) {
        return prevUpdater.compareAndSet(this, expected, newValue);
    }
}

