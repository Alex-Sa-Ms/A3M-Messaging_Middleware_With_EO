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
public class ListNode<T> {
    private ListNode<T> prev;
    private ListNode<T> next;
    private T object;

    public T getObject() {
        return object;
    }

    public ListNode<T> getPrev() {
        return prev;
    }

    public ListNode<T> getNext() {
        return next;
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

    private static <T> void _add(ListNode<T> node, ListNode<T> prev, ListNode<T> next){
        node.prev = prev;
        prev.next = node;
        node.next = next;
        next.prev = node;
    }

    private static <T> boolean isAddValid(ListNode<T> node, ListNode<T> prev, ListNode<T> next){
        return next.prev == prev && prev.next == next && node != prev && node != next;
    }

    private static <T> boolean isRemoveValid(ListNode<T> node){
        return node.prev.next == node && node.next.prev == node;
    }

    // Deletes node from list by making prev and next point to each other
    private static <T> void _remove(ListNode<T> prev, ListNode<T> next){
        prev.next = next;
        next.prev = prev;
    }

    // Adds node at the head
    public static <T> void addFirst(ListNode<T> node, ListNode<T> head){
        if(isAddValid(node, head, head.next))
            _add(node, head, head.next);
    }

    // Adds node at the tail
    public static <T> void addLast(ListNode<T> node, ListNode<T> head){
        _add(node, head.prev, head);
    }

    // Removes node from list
    public static <T> void remove(ListNode<T> node){
        if(isRemoveValid(node))
            _remove(node.prev, node.next);
    }

    // Removes node from list and re-initializes it.
    public static <T> void removeAndInit(ListNode<T> node){
        if(isRemoveValid(node)) {
            _remove(node.prev, node.next);
            node.prev = node;
            node.next = node;
        }
    }

    // Deletes node - Removes from list and removes link to object
    public static <T> void delete(ListNode<T> node){
        if(isRemoveValid(node)) {
            _remove(node.prev, node.next);
            node.prev = null;
            node.next = null;
        }
    }

    // moves to head of list "head"
    public static <T> void moveToFirst(ListNode<T> node, ListNode<T> head){
        remove(node);
        addFirst(node, head);
    }

    // moves to tail of list "head"
    public static <T> void moveToLast(ListNode<T> node, ListNode<T> head){
        remove(node);
        addLast(node, head);
    }

    public static <T> boolean isFirst(ListNode<T> node, ListNode<T> head){
        return node.prev == head && head.prev != head;
    }

    public static <T> boolean isLast(ListNode<T> node, ListNode<T> head){
        return node.next == head && head.next != head;
    }

    public static <T> boolean isHead(ListNode<T> node, ListNode<T> head){
        return node == head;
    }

    public static <T> boolean isEmpty(ListNode<T> head){
        return head.next == head;
    }

    public static <T> boolean isQueued(ListNode<T> node){
        return node.prev != null && node.next != null && node.next != node;
    }

    public static <T> boolean isDeleted(ListNode<T> node){
        return node.prev == null;
    }

    public static <T> ListNode<T> getFirst(ListNode<T> head){
        return head.next;
        // return head.next != head ? head.next : null;
    }

    public static <T> ListNode<T> getLast(ListNode<T> head){
        return head.prev;
        // return head.prev != head ? head.prev : null;
    }

    public static <T> void forEach(ListNode<T> head, Consumer<T> action){
        for(ListNode<T> it = head.next; it != head; it = it.next)
            action.accept(it.getObject());
    }

    public static <T> void forEachReverse(ListNode<T> head, Consumer<T> action){
        for(ListNode<T> it = head.prev; it != head; it = it.prev)
            action.accept(it.getObject());
    }

    public static <T> int size(ListNode<T> head){
        AtomicInteger i = new AtomicInteger();
        forEach(head, t -> i.addAndGet(1));
        return i.get();
    }

    /**
     * @param node node to be found in the list
     * @param head head of the list
     * @return index of the node in the list or -1 if it doesn't belong to the list.
     * @param <T> t
     */
    public static <T> int indexOf(ListNode<T> node, ListNode<T> head){
        int i = 0;
        ListNode<T> it = head.next;
        for(; it != head && it != node; it = it.next, i++);
        if(it == head)
            return -1;
        return i;
    }

    /**
     * Gets object at given index.
     * @param head head of the list
     * @param index position on the list
     * @return object at the given index
     * @param <T> t
     * @throws IndexOutOfBoundsException If the index is not inside the list.
     */
    public static <T> T get(ListNode<T> head, int index){
        if(index < 0 || ListNode.isEmpty(head))
            throw new IndexOutOfBoundsException();
        ListNode<T> it = head.next;
        for(; it != head && index != 0; it = it.next, index--);
        if(index != 0)
            throw new IndexOutOfBoundsException();
        return it.getObject();
    }

    public static <T> void concat(ListNode<T> head1, ListNode<T> head2){
        if(!isEmpty(head2)) {
            head1.prev.next = head2.next;
            head2.next.prev = head1.prev;
            head1.prev = head2.prev;
            head2.prev.next = head1;
        }
    }

    public static class Iterator<T> {

        private final ListNode<T> head;
        private ListNode<T> current;

        // Dictates the direction of the last next()/previous() operation.
        // "true" when the last operation was next() or when the iterator has just been created.
        // "false" when the last operation was previous().
        private boolean notReverse = false;

        Iterator(ListNode<T> head) {
            if(head == null || head.next == null || head.prev == null)
                throw new IllegalArgumentException("Could not create iterator: Not a valid head.");
            this.head = head;
            this.current = head;
        }

        public boolean hasNext() {
            return current.next != head;
        }

        public T next() {
            if(hasNext()) {
                current = current.next;
                notReverse = true;
                return current.getObject();
            }else throw new NoSuchElementException();
        }

        public boolean hasPrevious(){
            return current.prev != head;
        }

        public T previous(){
            if(hasPrevious()) {
                current = current.prev;
                notReverse = false;
                return current.getObject();
            }else throw new NoSuchElementException();
        }

        public boolean isHead(){
            return current == head;
        }

        // If next() and previous() have never been invoked,
        // the added node will be the first node of the list.
        public ListNode<T> addAfter(T t){
            ListNode<T> node = ListNode.create(t);
            ListNode._add(node, current, current.next);
            return node;
        }

        // If next() and previous() have never been invoked,
        // the added node will be the last node of the list.
        public ListNode<T> addBefore(T t){
            ListNode<T> node = ListNode.create(t);
            ListNode._add(node, current.prev, current);
            return node;
        }

        // Removes last list node returned by next() or previous().
        // Position is set based on the last next()/previous() operation. The position
        // is set so that the repetition of the last operation (next() or previous())
        // returns the same result as if this modifying operation was not executed.
        public ListNode<T> remove() {
            ListNode<T> toRmv = null;
            if(current != head){
                toRmv = current;
                setCurrentAfterModOp();
                ListNode.remove(toRmv);
            }
            return toRmv;
        }

        // Set current after modifying operation depending on the
        // direction of the last iteration.
        private void setCurrentAfterModOp(){
            current = notReverse ? current.prev : current.next;
        }

        // Removes and inits last list node returned by next() or previous().
        // Position is set based on the last next()/previous() operation. The position
        // is set so that the repetition of the last operation (next() or previous())
        // returns the same result as if this modifying operation was not executed.
        public ListNode<T> removeAndInit() {
            ListNode<T> toRmv = null;
            if(current != head){
                toRmv = current;
                setCurrentAfterModOp();
                ListNode.removeAndInit(toRmv);
            }
            return toRmv;
        }

        // Deletes last list node returned by next() or previous().
        // Position is set based on the last next()/previous() operation. The position
        // is set so that the repetition of the last operation (next() or previous())
        // returns the same result as if this modifying operation was not executed.
        public void delete() {
            if(current != head){
                ListNode<T> toDlt = current;
                setCurrentAfterModOp();
                ListNode.delete(toDlt);
            }
        }

        // Moves to first the last list node returned by next() or previous().
        // Position is set based on the last next()/previous() operation. The position
        // is set so that the repetition of the last operation (next() or previous())
        // returns the same result as if this modifying operation was not executed.
        public void moveToFirst(){
            if(current != head){
                ListNode<T> toMv = current;
                setCurrentAfterModOp();
                ListNode.moveToFirst(toMv, head);
            }
        }

        // Moves to last the last list node returned by next() or previous().
        // Position is set based on the last next()/previous() operation. The position
        // is set so that the repetition of the last operation (next() or previous())
        // returns the same result as if this modifying operation was not executed.
        public void moveToLast(){
            if(current != head){
                ListNode<T> toMv = current;
                setCurrentAfterModOp();
                ListNode.moveToLast(toMv, head);
            }
        }
    }

    /**
     * @param head Head of the list. The head node is not considered by hasNext()/next().
     * @return list iterator
     * @param <T> type of the ListNode's object
     * @implNote This iterator allows concurrent modifications, however, it is the
     * user's responsability if it leads to undefined and unwanted behaviour.
     * Having that said, such operations are discouraged.
     */
    public static <T> Iterator<T> iterator(ListNode<T> head){
        return new Iterator<>(head);
    }

    @Override
    public String toString() {
        return "ListNode{" +
                "prev=" + (prev != null ? prev.object : "{null}") +
                ", next=" + (next != null ? next.object : "{null}") +
                ", object=" + object +
                '}';
    }
}
