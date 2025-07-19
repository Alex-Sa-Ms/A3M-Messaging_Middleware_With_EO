package pt.uminho.di.a3m.list;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public interface IListNode<T> {
    T getObject();
    IListNode<T> getPrev();
    IListNode<T> getNext();
    void setObject(T t);
    void setNext(IListNode<T> next);
    void setPrev(IListNode<T> prev);

    // Initializes a list. This corresponds to creating the head node.
    //static <T> IListNode<T> init() {
    //    return new IListNode<T>();
    //}

    static <T> void init(IListNode<T> node){
        node.setNext(node);
        node.setPrev(node);
    }

    //static <T> IListNode<T> create(T object) {
    //    IListNode<T> node = new IListNode<>(null, null, object);
    //    node.prev = node;
    //    node.next = node;
    //    return node;
    //}

    private static <T> void _add(IListNode<T> node, IListNode<T> prev, IListNode<T> next){
        node.setPrev(prev);
        prev.setNext(node);
        node.setNext(next);
        next.setPrev(node);
    }

    private static <T> boolean isAddValid(IListNode<T> node, IListNode<T> prev, IListNode<T> next){
        return next.getPrev() == prev && prev.getNext() == next && node != prev && node != next;
    }

    private static <T> boolean isRemoveValid(IListNode<T> node){
        return node.getPrev().getNext() == node && node.getNext().getPrev() == node;
    }

    // Deletes node from list by making prev and next point to each other
    private static <T> void _remove(IListNode<T> prev, IListNode<T> next){
        prev.setNext(next);
        next.setPrev(prev);
    }

    // Adds node at the head
    static <T> void addFirst(IListNode<T> node, IListNode<T> head){
        if(isAddValid(node, head, head.getNext()))
            _add(node, head, head.getNext());
    }

    // Adds node at the tail
    static <T> void addLast(IListNode<T> node, IListNode<T> head){
        _add(node, head.getPrev(), head);
    }

    // Removes node from list
    static <T> void remove(IListNode<T> node){
        if(isRemoveValid(node))
            _remove(node.getPrev(), node.getNext());
    }

    // Removes node from list and re-initializes it.
    static <T> void removeAndInit(IListNode<T> node){
        if(isRemoveValid(node)) {
            _remove(node.getPrev(), node.getNext());
            node.setPrev(node);
            node.setNext(node);
        }
    }

    // Deletes node - Removes from list and removes link to object
    static <T> void delete(IListNode<T> node){
        if(isRemoveValid(node)) {
            _remove(node.getPrev(), node.getNext());
            node.setPrev(null);
            node.setNext(null);
        }
    }

    // moves to head of list "head"
    static <T> void moveToFirst(IListNode<T> node, IListNode<T> head){
        remove(node);
        addFirst(node, head);
    }

    // moves to tail of list "head"
    static <T> void moveToLast(IListNode<T> node, IListNode<T> head){
        remove(node);
        addLast(node, head);
    }

    static <T> boolean isFirst(IListNode<T> node, IListNode<T> head){
        return node.getPrev() == head && head.getPrev() != head;
    }

    static <T> boolean isLast(IListNode<T> node, IListNode<T> head){
        return node.getNext() == head && head.getNext() != head;
    }

    static <T> boolean isHead(IListNode<T> node, IListNode<T> head){
        return node == head;
    }

    static <T> boolean isEmpty(IListNode<T> head){
        return head.getNext() == head;
    }

    static <T> boolean isQueued(IListNode<T> node){
        return node.getPrev() != null && node.getNext() != null && node.getNext() != node;
    }

    static <T> boolean isDeleted(IListNode<T> node){
        return node.getPrev() == null;
    }

    static <T> IListNode<T> getFirst(IListNode<T> head){
        return head.getNext();
        // return head.getNext() != head ? head.next : null;
    }

    static <T> IListNode<T> getLast(IListNode<T> head){
        return head.getPrev();
        // return head.getPrev() != head ? head.prev : null;
    }

    static <T> void forEach(IListNode<T> head, Consumer<T> action){
        for(IListNode<T> it = head.getNext(); it != head; it = it.getNext())
            action.accept(it.getObject());
    }

    static <T> void forEachReverse(IListNode<T> head, Consumer<T> action){
        for(IListNode<T> it = head.getPrev(); it != head; it = it.getPrev())
            action.accept(it.getObject());
    }

    static <T> int size(IListNode<T> head){
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
    static <T> int indexOf(IListNode<T> node, IListNode<T> head){
        int i = 0;
        IListNode<T> it = head.getNext();
        for(; it != head && it != node; it = it.getNext(), i++);
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
    static <T> T get(IListNode<T> head, int index){
        if(index < 0 || IListNode.isEmpty(head))
            throw new IndexOutOfBoundsException();
        IListNode<T> it = head.getNext();
        for(; it != head && index != 0; it = it.getNext(), index--);
        if(index != 0)
            throw new IndexOutOfBoundsException();
        return it.getObject();
    }

    static <T> void concat(IListNode<T> head1, IListNode<T> head2){
        if(!isEmpty(head2)) {
            head1.getPrev().setNext(head2.getNext());
            head2.getNext().setPrev(head1.getPrev());
            head1.setPrev(head2.getPrev());
            head2.getPrev().setNext(head1);
        }
    }

    class Iterator<T> {

        private final IListNode<T> head;
        private IListNode<T> current;

        // Dictates the direction of the last next()/previous() operation.
        // "true" when the last operation was next() or when the iterator has just been created.
        // "false" when the last operation was previous().
        private boolean notReverse = false;

        Iterator(IListNode<T> head) {
            if(head == null || head.getNext() == null || head.getPrev() == null)
                throw new IllegalArgumentException("Could not create iterator: Not a valid head.");
            this.head = head;
            this.current = head;
        }

        public boolean hasNext() {
            return current.getNext() != head;
        }

        public T next() {
            if(hasNext()) {
                current = current.getNext();
                notReverse = true;
                return current.getObject();
            }else throw new NoSuchElementException();
        }

        public boolean hasPrevious(){
            return current.getPrev() != head;
        }

        public T previous(){
            if(hasPrevious()) {
                current = current.getPrev();
                notReverse = false;
                return current.getObject();
            }else throw new NoSuchElementException();
        }

        public boolean isHead(){
            return current == head;
        }

        // If next() and previous() have never been invoked,
        // the added node will be the first node of the list.
        public void addAfter(IListNode<T> node){
            IListNode._add(node, current, current.getNext());
        }

        // If next() and previous() have never been invoked,
        // the added node will be the last node of the list.
        public void addBefore(IListNode<T> node){
            IListNode._add(node, current.getPrev(), current);
        }

        // Removes last list node returned by next() or previous().
        // Position is set based on the last next()/previous() operation. The position
        // is set so that the repetition of the last operation (next() or previous())
        // returns the same result as if this modifying operation was not executed.
        public IListNode<T> remove() {
            IListNode<T> toRmv = null;
            if(current != head){
                toRmv = current;
                setCurrentAfterModOp();
                IListNode.remove(toRmv);
            }
            return toRmv;
        }

        // Set current after modifying operation depending on the
        // direction of the last iteration.
        private void setCurrentAfterModOp(){
            current = notReverse ? current.getPrev() : current.getNext();
        }

        // Removes and inits last list node returned by next() or previous().
        // Position is set based on the last next()/previous() operation. The position
        // is set so that the repetition of the last operation (next() or previous())
        // returns the same result as if this modifying operation was not executed.
        public IListNode<T> removeAndInit() {
            IListNode<T> toRmv = null;
            if(current != head){
                toRmv = current;
                setCurrentAfterModOp();
                IListNode.removeAndInit(toRmv);
            }
            return toRmv;
        }

        // Deletes last list node returned by next() or previous().
        // Position is set based on the last next()/previous() operation. The position
        // is set so that the repetition of the last operation (next() or previous())
        // returns the same result as if this modifying operation was not executed.
        public void delete() {
            if(current != head){
                IListNode<T> toDlt = current;
                setCurrentAfterModOp();
                IListNode.delete(toDlt);
            }
        }

        // Moves to first the last list node returned by next() or previous().
        // Position is set based on the last next()/previous() operation. The position
        // is set so that the repetition of the last operation (next() or previous())
        // returns the same result as if this modifying operation was not executed.
        public void moveToFirst(){
            if(current != head){
                IListNode<T> toMv = current;
                setCurrentAfterModOp();
                IListNode.moveToFirst(toMv, head);
            }
        }

        // Moves to last the last list node returned by next() or previous().
        // Position is set based on the last next()/previous() operation. The position
        // is set so that the repetition of the last operation (next() or previous())
        // returns the same result as if this modifying operation was not executed.
        public void moveToLast(){
            if(current != head){
                IListNode<T> toMv = current;
                setCurrentAfterModOp();
                IListNode.moveToLast(toMv, head);
            }
        }
    }

    /**
     * @param head Head of the list. The head node is not considered by hasNext()/next().
     * @return list iterator
     * @param <T> type of the IListNode's object
     * @implNote This iterator allows concurrent modifications, however, it is the
     * user's responsability if it leads to undefined and unwanted behaviour.
     * Having that said, such operations are discouraged.
     */
    static <T> IListNode.Iterator<T> iterator(IListNode<T> head){
        return new IListNode.Iterator<>(head);
    }

    static <T> String toString(IListNode<T> node) {
        return "{" +
                "prev=" + (node.getPrev() != null ? node.getPrev().getObject() : "{null}") +
                ", next=" + (node.getNext() != null ? node.getNext().getObject() : "{null}") +
                ", object=" + node.getObject() +
                '}';
    }
}
