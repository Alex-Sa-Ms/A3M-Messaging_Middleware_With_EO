package pt.uminho.di.a3m.list;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Implementation of circular list.
 * Lists are identified by a head node obtained through ListNode.init().
 * Although it is a node, it's main purpose is identifying the start of the list,
 * therefore it should not be considered as "effective" member of the list.
 * @param <T>
 */
public class ListNode<T> implements IListNode<T>{
    IListNode<T> prev;
    IListNode<T> next;
    private T object;

    public T getObject() {
        return object;
    }

    public IListNode<T> getPrev() {
        return prev;
    }

    public IListNode<T> getNext() {
        return next;
    }

    @Override
    public void setObject(T t) {
        this.object = t;
    }

    @Override
    public void setNext(IListNode<T> next) {
        this.next = next;
    }

    @Override
    public void setPrev(IListNode<T> prev) {
        this.prev = prev;
    }

    private ListNode(ListNode<T> prev, ListNode<T> next, T object) {
        this.prev = prev;
        this.next = next;
        this.object = object;
    }

    private ListNode() {
        this.prev = this;
        this.next = this;
        this.object = null;
    }

    // Initializes a list. This corresponds to creating the head node.
    public static <T> ListNode<T> init() {
        return new ListNode<>();
    }


    public static <T> ListNode<T> create(T object) {
        ListNode<T> node = new ListNode<>(null, null, object);
        node.prev = node;
        node.next = node;
        return node;
    }
    @Override
    public String toString() {
        return "ListNode" + IListNode.toString(this);
    }
}
