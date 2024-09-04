package pt.uminho.di.a3m.list;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicInteger;

class ListNodeTest {

    // Class that matches the operations of the IListNode with an ArrayList to check if the
    // operations are consistent.
    private static class ListConsistencyChecker<T>{
        protected List<IListNode<T>> confirmList = new ArrayList<>();
        protected IListNode<T> head = ListNode.init();

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("ListConsistencyChecker{");
            if(!confirmList.isEmpty()) {
                sb.append('\n');
                for (IListNode<T> node : confirmList)
                    sb.append("\t").append(node.getObject().toString()).append(",\n");
                sb.deleteCharAt(sb.length() - 2);
            }
            sb.append('}');
            return sb.toString();
        }

        public void remove(IListNode<T> node) {
            confirmList.remove(node);
            IListNode.remove(node);
        }

        public void removeAndInit(IListNode<T> node) {
            confirmList.remove(node);
            IListNode.removeAndInit(node);
        }

        public void delete(IListNode<T> node) {
            confirmList.remove(node);
            IListNode.delete(node);
        }

        public void addHead(IListNode<T> node) {
            confirmList.addFirst(node);
            IListNode.addFirst(node, head);
        }

        public void addTail(IListNode<T> node) {
            confirmList.addLast(node);
            IListNode.addLast(node, head);
        }

        public void moveToFirst(IListNode<T> node) {
            confirmList.remove(node);
            confirmList.addFirst(node);
            IListNode.moveToFirst(node, head);
        }

        public void moveToLast(IListNode<T> node) {
            confirmList.remove(node);
            confirmList.addLast(node);
            IListNode.moveToLast(node, head);
        }

        public void confirmEquality() {
            // confirms that both lists agree on whether it is empty or not
            assert IListNode.isEmpty(head) == confirmList.isEmpty();

            // return if list is empty
            if(IListNode.isEmpty(head))
                return;

            // gets first element
            ListIterator<IListNode<T>> clIt = confirmList.listIterator(); // confirm list iterator
            IListNode<T> node = head, nodeConfirm;

            // checks links in the forward direction
            while(clIt.hasNext()) {
                node = node.getNext();
                nodeConfirm = clIt.next();
                assert node == nodeConfirm;
            }

            // assert that the head is the next entry
            assert node.getNext() == head;

            // reset the node to start at the head
            node = head;

            // checks links in the backward direction
            while(clIt.hasPrevious()) {
                node = node.getPrev();
                nodeConfirm = clIt.previous();
                assert node == nodeConfirm;
            }

            // assert that the head is the previous entry of node
            assert node.getPrev() == head;
        }
    }

    @Test
    void createEntryAndGetObject() {
        Object o1 = new Object(), o2 = new Object();
        IListNode<Object> listNode = ListNode.create(o1);
        Object obj = listNode.getObject();
        assert obj == o1;
        assert obj != o2;
    }

    @Test
    void initList() {
        Object o = new Object();
        IListNode<Object> head = ListNode.init();
        assert head.getObject() == null && head.getNext() == head && head.getPrev() == head;
    }

    @Test
    void addHead() {
        ListConsistencyChecker<String> lchecker = new ListConsistencyChecker<>();
        lchecker.addHead(ListNode.create("2"));
        lchecker.addHead(ListNode.create("1"));
        lchecker.confirmEquality();
        System.out.println(lchecker);
    }

    @Test
    void addTail() {
        ListConsistencyChecker<String> lchecker = new ListConsistencyChecker<>();
        lchecker.addTail(ListNode.create("1"));
        lchecker.addTail(ListNode.create("2"));
        lchecker.confirmEquality();
        System.out.println(lchecker);
    }

    @Test
    void remove() {
        ListConsistencyChecker<String> lchecker = new ListConsistencyChecker<>();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        lchecker.addHead(node3);
        lchecker.addHead(node2);
        lchecker.addHead(node1);
        lchecker.confirmEquality();

        lchecker.remove(node2);
        lchecker.confirmEquality();
        assert node2.getPrev() == node1 && node2.getNext() == node3;

        lchecker.remove(node1);
        lchecker.confirmEquality();
        assert node1.getPrev() == lchecker.head && node1.getNext() == node3;

        lchecker.remove(node3);
        lchecker.confirmEquality();
        assert node3.getPrev() == lchecker.head && node3.getNext() == lchecker.head;
        System.out.println(lchecker);
    }

    @Test
    void removeAndInit() {
        ListConsistencyChecker<String> lchecker = new ListConsistencyChecker<>();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        lchecker.addHead(node3);
        lchecker.addHead(node2);
        lchecker.addHead(node1);
        lchecker.confirmEquality();

        lchecker.removeAndInit(node2);
        lchecker.confirmEquality();
        assert node2.getPrev() == node2 && node2.getNext() == node2;

        lchecker.removeAndInit(node1);
        lchecker.confirmEquality();
        assert node1.getPrev() == node1 && node1.getNext() == node1;

        lchecker.removeAndInit(node3);
        lchecker.confirmEquality();
        assert node3.getPrev() == node3 && node3.getNext() == node3;
        System.out.println(lchecker);

        IListNode<String> head = lchecker.head;
        assert head.getPrev() == head && head.getNext() == head;
    }

    @Test
    void delete() {
        ListConsistencyChecker<String> lchecker = new ListConsistencyChecker<>();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        lchecker.addHead(node3);
        lchecker.addHead(node2);
        lchecker.addHead(node1);
        lchecker.confirmEquality();

        lchecker.delete(node2);
        lchecker.confirmEquality();
        assert node2.getPrev() == null && node2.getNext() == null;

        lchecker.delete(node1);
        lchecker.confirmEquality();
        assert node1.getPrev() == null && node1.getNext() == null;

        lchecker.delete(node3);
        lchecker.confirmEquality();
        assert node3.getPrev() == null && node3.getNext() == null;
        System.out.println(lchecker);

        IListNode<String> head = lchecker.head;
        assert head.getPrev() == head && head.getNext() == head;
    }

    @Test
    void moveToHead() {
        ListConsistencyChecker<String> lchecker = new ListConsistencyChecker<>();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        lchecker.addHead(node3);
        lchecker.addHead(node2);
        lchecker.addHead(node1);
        lchecker.confirmEquality();

        lchecker.moveToFirst(node3);
        lchecker.confirmEquality();

        lchecker.moveToFirst(node2);
        lchecker.confirmEquality();

        lchecker.moveToFirst(node1);
        lchecker.confirmEquality();

        System.out.println(lchecker);
    }

    @Test
    void moveToTail() {
        ListConsistencyChecker<String> lchecker = new ListConsistencyChecker<>();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        lchecker.addHead(node3);
        lchecker.addHead(node2);
        lchecker.addHead(node1);
        lchecker.confirmEquality();

        lchecker.moveToLast(node1);
        lchecker.confirmEquality();

        lchecker.moveToLast(node2);
        lchecker.confirmEquality();

        lchecker.moveToLast(node3);
        lchecker.confirmEquality();

        System.out.println(lchecker);
    }

    @Test
    void isFirst() {
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        assert !IListNode.isFirst(node1, head);
        assert !IListNode.isFirst(node2, head);
        assert !IListNode.isFirst(node3, head);
        assert !IListNode.isFirst(head, head);

        IListNode.addFirst(node3, head);
        assert IListNode.isFirst(node3, head);

        IListNode.addFirst(node2, head);
        assert IListNode.isFirst(node2, head);
        assert !IListNode.isFirst(node3, head);

        IListNode.addFirst(node1, head);
        assert IListNode.isFirst(node1, head);
        assert !IListNode.isFirst(node2, head);
        assert !IListNode.isFirst(node3, head);
    }

    @Test
    void isLast() {
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        assert !IListNode.isLast(node1, head);
        assert !IListNode.isLast(node2, head);
        assert !IListNode.isLast(node3, head);
        assert !IListNode.isLast(head, head);

        IListNode.addLast(node3, head);
        assert IListNode.isLast(node3, head);

        IListNode.addLast(node2, head);
        assert IListNode.isLast(node2, head);
        assert !IListNode.isLast(node3, head);

        IListNode.addLast(node1, head);
        assert IListNode.isLast(node1, head);
        assert !IListNode.isLast(node2, head);
        assert !IListNode.isLast(node3, head);
    }

    @Test
    void isHead() {
        IListNode<String> head = ListNode.init();
        assert IListNode.isHead(head, head);

        IListNode<String> node1 = ListNode.create("1");
        assert IListNode.isHead(head, head);
        assert !IListNode.isHead(node1, head);

        IListNode.addFirst(node1, head);
        assert IListNode.isHead(head, head);
        assert !IListNode.isHead(node1, head);
    }

    @Test
    void isEmpty() {
        IListNode<String> head = ListNode.init();
        assert IListNode.isEmpty(head);

        IListNode<String> node1 = ListNode.create("1");
        IListNode.addFirst(node1, head);
        assert !IListNode.isEmpty(head);

        IListNode.removeAndInit(node1);
        assert IListNode.isEmpty(head);

        IListNode.addLast(node1, head);
        assert !IListNode.isEmpty(head);
    }

    @Test
    void getFirst() {
        IListNode<String> head = ListNode.init();
        assert IListNode.getFirst(head) == head;

        IListNode<String> node1 = ListNode.create("1");
        IListNode.addFirst(node1, head);
        assert IListNode.getFirst(head) == node1;

        IListNode.removeAndInit(node1);
        assert IListNode.getFirst(head) == head;

        IListNode.addLast(node1, head);
        assert IListNode.getFirst(head) == node1;

        IListNode<String> node2 = ListNode.create("2");
        IListNode.addFirst(node2, head);
        assert IListNode.getFirst(head) == node2;
    }

    @Test
    void getLast() {
        IListNode<String> head = ListNode.init();
        assert IListNode.getLast(head) == head;

        IListNode<String> node1 = ListNode.create("1");
        IListNode.addFirst(node1, head);
        assert IListNode.getLast(head) == node1;

        IListNode.removeAndInit(node1);
        assert IListNode.getLast(head) == head;

        IListNode.addLast(node1, head);
        assert IListNode.getLast(head) == node1;

        IListNode<String> node2 = ListNode.create("2");
        IListNode.addLast(node2, head);
        assert IListNode.getLast(head) == node2;
    }

    @Test
    void forEach() {
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        IListNode.addFirst(node3, head);
        IListNode.addFirst(node2, head);
        IListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        IListNode.forEach(head, list::add);
        assert list.size() == 3;
        assert list.indexOf("1") == 0;
        assert list.indexOf("2") == 1;
        assert list.indexOf("3") == 2;
    }

    @Test
    void forEachReverse() {
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        IListNode.addFirst(node3, head);
        IListNode.addFirst(node2, head);
        IListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        IListNode.forEachReverse(head, list::add);
        assert list.size() == 3;
        assert list.indexOf("3") == 0;
        assert list.indexOf("2") == 1;
        assert list.indexOf("1") == 2;
    }

    @Test
    void forEachWithIterator() {
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        IListNode.addFirst(node3, head);
        IListNode.addFirst(node2, head);
        IListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        IListNode.Iterator<String> it = IListNode.iterator(head);
        while(it.hasNext())
            list.add(it.next());

        assert list.size() == 3;
        assert list.indexOf("1") == 0;
        assert list.indexOf("2") == 1;
        assert list.indexOf("3") == 2;
    }

    @Test
    void forEachReverseWithIterator() {
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        IListNode.addFirst(node3, head);
        IListNode.addFirst(node2, head);
        IListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        IListNode.Iterator<String> it = IListNode.iterator(head);
        while(it.hasPrevious())
            list.add(it.previous());

        assert list.size() == 3;
        assert list.indexOf("3") == 0;
        assert list.indexOf("2") == 1;
        assert list.indexOf("1") == 2;
    }

    @Test
    void forEachForwardPlusReverseWithIterator() {
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        IListNode.addFirst(node3, head);
        IListNode.addFirst(node2, head);
        IListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        IListNode.Iterator<String> it = IListNode.iterator(head);
        while(it.hasNext())
            list.add(it.next());

        assert list.size() == 3;
        assert list.indexOf("1") == 0;
        assert list.indexOf("2") == 1;
        assert list.indexOf("3") == 2;

        // current position is on the 3rd element,
        // therefore only 2 invocations of previous()
        // can be done.
        list.clear();
        while(it.hasPrevious())
            list.add(it.previous());

        assert list.size() == 2;
        assert list.indexOf("2") == 0;
        assert list.indexOf("1") == 1;
    }

    @Test
    void addAfterHeadWithIterator() {
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        IListNode.addFirst(node3, head);
        IListNode.addFirst(node2, head);
        IListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        IListNode.Iterator<String> it = IListNode.iterator(head);
        it.addAfter(ListNode.create("0"));
        while(it.hasNext())
            list.add(it.next());

        assert list.size() == 4;
        assert list.indexOf("0") == 0;
        assert list.indexOf("1") == 1;
        assert list.indexOf("2") == 2;
        assert list.indexOf("3") == 3;
    }

    @Test
    void addBeforeHeadWithIterator() {
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        IListNode.addFirst(node3, head);
        IListNode.addFirst(node2, head);
        IListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        IListNode.Iterator<String> it = IListNode.iterator(head);
        it.addBefore(ListNode.create("4"));
        while(it.hasNext())
            list.add(it.next());

        assert list.size() == 4;
        assert list.indexOf("1") == 0;
        assert list.indexOf("2") == 1;
        assert list.indexOf("3") == 2;
        assert list.indexOf("4") == 3;
    }

    @Test
    void addAfterNonHeadNodeWithIterator() {
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        IListNode.addFirst(node3, head);
        IListNode.addFirst(node2, head);
        IListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        IListNode.Iterator<String> it = IListNode.iterator(head);
        while(it.hasNext())
            list.add(it.next());

        // add node4 as last because the iterator stopped due to lack of a next node
        it.addAfter(ListNode.create("4"));

        // node4 is added after the current node, therefore it.hasNext() must be true
        assert it.hasNext();
        list.add(it.next());

        assert list.size() == 4;
        assert list.indexOf("1") == 0;
        assert list.indexOf("2") == 1;
        assert list.indexOf("3") == 2;
        assert list.indexOf("4") == 3;
    }

    @Test
    void addBeforeNonHeadNodeWithIterator() {
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        IListNode.addFirst(node3, head);
        IListNode.addFirst(node2, head);
        IListNode.addFirst(node1, head);

        // Makes iterator go to the last node
        IListNode.Iterator<String> it = IListNode.iterator(head);
        while(it.hasNext())
            it.next();

        // add node4 before last node
        it.addBefore(ListNode.create("4"));

        // Iterates back to the start and adds objects to the list.
        // The current is not added to the list.
        List<String> list = new ArrayList<>();

        while(it.hasPrevious())
            list.add(it.previous());

        assert list.size() == 3;
        assert list.indexOf("4") == 0;
        assert list.indexOf("2") == 1;
        assert list.indexOf("1") == 2;
    }

    @Test
    void removeWithIterator(){
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        IListNode.addFirst(node3, head);
        IListNode.addFirst(node2, head);
        IListNode.addFirst(node1, head);

        // checks that removing after initiating the iterator
        // does not do anything.
        IListNode.Iterator<String> it = IListNode.iterator(head);
        it.remove();
        assert head.getPrev() == node3 && head.getNext() == node1;

        IListNode<String> rmvd;
        // remove node1
        it.next();
        rmvd = it.remove();
        assert rmvd == node1;
        assert node1.getPrev() == head && node1.getNext() == node2;

        // remove node2
        it.next();
        rmvd = it.remove();
        assert rmvd == node2;
        assert node2.getPrev() == head && node2.getNext() == node3;

        // remove node3
        it.next();
        rmvd = it.remove();
        assert rmvd == node3;
        assert node3.getPrev() == head && node3.getNext() == head;

        // assert list is empty
        assert IListNode.isEmpty(head);
        // assert iterator cannot move
        assert !it.hasNext();
        assert !it.hasPrevious();
    }

    @Test
    void removeAndInitWithIterator(){
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        IListNode.addFirst(node3, head);
        IListNode.addFirst(node2, head);
        IListNode.addFirst(node1, head);

        // checks that removing after initiating the iterator
        // does not do anything.
        IListNode.Iterator<String> it = IListNode.iterator(head);
        it.removeAndInit();
        assert head.getPrev() == node3 && head.getNext() == node1;

        IListNode<String> rmvd;
        // remove and init node1
        it.next();
        rmvd = it.removeAndInit();
        assert rmvd == node1;
        assert node1.getPrev() == node1 && node1.getNext() == node1;

        // remove and init node2
        it.next();
        rmvd = it.removeAndInit();
        assert rmvd == node2;
        assert node2.getPrev() == node2 && node2.getNext() == node2;

        // remove and init node3
        it.next();
        rmvd = it.removeAndInit();
        assert rmvd == node3;
        assert node3.getPrev() == node3 && node3.getNext() == node3;

        // assert list is empty
        assert IListNode.isEmpty(head);
        // assert iterator cannot move
        assert !it.hasNext();
        assert !it.hasPrevious();
    }

    @Test
    void deleteWithIterator(){
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        IListNode.addFirst(node3, head);
        IListNode.addFirst(node2, head);
        IListNode.addFirst(node1, head);

        // checks that removing after initiating the iterator
        // does not do anything.
        IListNode.Iterator<String> it = IListNode.iterator(head);
        it.delete();
        assert head.getPrev() == node3 && head.getNext() == node1;

        String s;
        // delete node1
        s = it.next();
        it.delete();
        assert s.equals("1");
        assert node1.getPrev() == null && node1.getNext() == null;

        // delete node2
        s = it.next();
        it.delete();
        assert s.equals("2");
        assert node2.getPrev() == null && node2.getNext() == null;

        // delete node3
        s = it.next();
        it.delete();
        assert s.equals("3");
        assert node3.getPrev() == null && node3.getNext() == null;

        // assert list is empty
        assert IListNode.isEmpty(head);
        // assert iterator cannot move
        assert !it.hasNext();
        assert !it.hasPrevious();
    }

    @Test
    void moveToFirstWithIterator(){
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        IListNode.addFirst(node3, head);
        IListNode.addFirst(node2, head);
        IListNode.addFirst(node1, head);

        // checks that attempting to move the node after initiating the iterator
        // does not do anything.
        IListNode.Iterator<String> it = IListNode.iterator(head);
        it.moveToFirst();
        assert head.getPrev() == node3 && head.getNext() == node1;

        String s;
        // move node3 to first
        s = it.previous();
        assert s.equals("3");
        it.moveToFirst();
        assert node3.getPrev() == head && node3.getNext() == node1;

        // move node2 to first
        s = it.previous();
        assert s.equals("2");
        it.moveToFirst();
        assert node2.getPrev() == head && node2.getNext() == node3;

        // move node1 to first
        s = it.previous();
        assert s.equals("1");
        it.moveToFirst();
        assert node1.getPrev() == head && node1.getNext() == node2;

        // check that list remains correct
        List<String> list = new ArrayList<>();
        while(it.hasNext())
            list.add(it.next());

        assert list.size() == 3;
        assert list.indexOf("1") == 0;
        assert list.indexOf("2") == 1;
        assert list.indexOf("3") == 2;
    }

    @Test
    void moveToLastWithIterator(){
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");
        IListNode.addFirst(node3, head);
        IListNode.addFirst(node2, head);
        IListNode.addFirst(node1, head);

        // checks that attempting to move the node after initiating the iterator
        // does not do anything.
        IListNode.Iterator<String> it = IListNode.iterator(head);
        it.moveToLast();
        assert head.getPrev() == node3 && head.getNext() == node1;

        String s;
        // move node3 to last
        s = it.next();
        assert s.equals("1");
        it.moveToLast();
        assert node1.getPrev() == node3 && node1.getNext() == head;

        // move node2 to last
        s = it.next();
        assert s.equals("2");
        it.moveToLast();
        assert node2.getPrev() == node1 && node2.getNext() == head;

        // move node1 to last
        s = it.next();
        assert s.equals("3");
        it.moveToLast();
        assert node3.getPrev() == node2 && node3.getNext() == head;

        // check that list remains correct
        List<String> list = new ArrayList<>();
        while(it.hasNext())
            list.add(it.next());

        assert list.size() == 3;
        assert list.indexOf("1") == 0;
        assert list.indexOf("2") == 1;
        assert list.indexOf("3") == 2;
    }

    @Test
    public void size(){
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");

        assert IListNode.size(head) == 0;

        IListNode.addFirst(node3, head);
        assert IListNode.size(head) == 1;

        IListNode.addFirst(node2, head);
        assert IListNode.size(head) == 2;

        IListNode.addFirst(node1, head);
        assert IListNode.size(head) == 3;

        IListNode.delete(node1);
        assert IListNode.size(head) == 2;

        IListNode.delete(node2);
        assert IListNode.size(head) == 1;

        IListNode.delete(node3);
        assert IListNode.size(head) == 0;
    }

    @Test
    public void indexOf(){
        IListNode<String> head = ListNode.init();
        IListNode<String> node1 = ListNode.create("1");
        IListNode<String> node2 = ListNode.create("2");
        IListNode<String> node3 = ListNode.create("3");

        // assert that the nodes do not exist in the list
        assert IListNode.indexOf(node1, head) == -1;
        assert IListNode.indexOf(node2, head) == -1;
        assert IListNode.indexOf(node3, head) == -1;

        IListNode.addFirst(node3, head);
        assert IListNode.indexOf(node3, head) == 0;

        IListNode.addFirst(node2, head);
        assert IListNode.indexOf(node2, head) == 0;
        assert IListNode.indexOf(node3, head) == 1;

        IListNode.addFirst(node1, head);
        assert IListNode.indexOf(node1, head) == 0;
        assert IListNode.indexOf(node2, head) == 1;
        assert IListNode.indexOf(node3, head) == 2;

        IListNode.delete(node1);
        assert IListNode.indexOf(node1, head) == -1;
        assert IListNode.indexOf(node2, head) == 0;
        assert IListNode.indexOf(node3, head) == 1;

        IListNode.delete(node2);
        assert IListNode.indexOf(node2, head) == -1;
        assert IListNode.indexOf(node3, head) == 0;

        IListNode.delete(node3);
        assert IListNode.indexOf(node3, head) == -1;
    }

    private <T> void getAndCatchOutOfBoundsException(IListNode<T> head, int i){
        try {
            IListNode.get(head, i);
            // shouldn't get here
            assert false;
        } catch (IndexOutOfBoundsException exception) {
            assert true;
        }
    }

    @Test
    public void get(){
        IListNode<List<String>> head = ListNode.init();
        IListNode<List<String>> node1 = ListNode.create(List.of("1"));
        IListNode<List<String>> node2 = ListNode.create(List.of("2"));
        IListNode<List<String>> node3 = ListNode.create(List.of("3"));

        // assert that the nodes do not exist in the list
        for(int i = -1; i <= 1; i++)
            getAndCatchOutOfBoundsException(head, i);

        IListNode.addFirst(node3, head);
        assert IListNode.get(head, 0) == node3.getObject();

        IListNode.addFirst(node2, head);
        assert IListNode.get(head, 0) == node2.getObject();
        assert IListNode.get(head, 1) == node3.getObject();

        IListNode.addFirst(node1, head);
        assert IListNode.get(head, 0) == node1.getObject();
        assert IListNode.get(head, 1) == node2.getObject();
        assert IListNode.get(head, 2) == node3.getObject();

        IListNode.delete(node1);
        assert IListNode.get(head, 0) == node2.getObject();
        assert IListNode.get(head, 1) == node3.getObject();

        IListNode.delete(node2);
        assert IListNode.get(head, 0) == node3.getObject();

        IListNode.delete(node3);
        getAndCatchOutOfBoundsException(head, 0);
    }

    @Test
    public void concat(){
        IListNode<Integer> head1 = ListNode.init();
        IListNode<Integer> head2 = ListNode.init();
        IListNode<Integer> head3 = ListNode.init();

        // assert lists remain empty
        IListNode.concat(head1, head2);
        assert IListNode.isEmpty(head1);

        // add 1, 2 and 3 to list 1
        IListNode<Integer> node1 = ListNode.create(1);
        IListNode<Integer> node2 = ListNode.create(2);
        IListNode<Integer> node3 = ListNode.create(3);
        IListNode.addFirst(node3, head2);
        IListNode.addFirst(node2, head2);
        IListNode.addFirst(node1, head2);

        // joins the nodes in list 2 to
        // the nodes of list 1.
        IListNode.concat(head1, head2);

        // assert the elements were properly added
        AtomicInteger i = new AtomicInteger(1);
        IListNode.forEach(head1, integer -> {
            assert integer == i.getAndIncrement();
        });

        // add 4, 5 and 6 to list 2
        IListNode<Integer> node4 = ListNode.create(4);
        IListNode<Integer> node5 = ListNode.create(5);
        IListNode<Integer> node6 = ListNode.create(6);
        IListNode.addFirst(node6, head3);
        IListNode.addFirst(node5, head3);
        IListNode.addFirst(node4, head3);

        // joins the nodes in list 3 to
        // the nodes of list 1.
        IListNode.concat(head1, head3);

        // assert the elements were properly added
        IListNode.concat(head1, head3);
        i.set(1);
        IListNode.forEach(head1, integer -> {
            assert integer == i.getAndIncrement();
        });
    }

    @Test
    void moveToAnotherList() {
        // create 2 lists
        IListNode<Integer> head1 = ListNode.init();
        IListNode<Integer> head2 = ListNode.init();

        // add 3 integer nodes to list 1
        IListNode<Integer> node1 = ListNode.create(1);
        IListNode<Integer> node2 = ListNode.create(2);
        IListNode<Integer> node3 = ListNode.create(3);
        IListNode.addFirst(node3, head1);
        IListNode.addFirst(node2, head1);
        IListNode.addFirst(node1, head1);

        // move nodes from list 1 to list 2
        IListNode.moveToLast(node3, head2);
        IListNode.moveToLast(node2, head2);
        IListNode.moveToLast(node1, head2);

        assert IListNode.size(head1) == 0;
        assert IListNode.size(head2) == 3;
    }
}