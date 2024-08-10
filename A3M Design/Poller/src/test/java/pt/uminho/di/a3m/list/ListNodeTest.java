package pt.uminho.di.a3m.list;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

class ListNodeTest {

    // Class that matches the operations of the ListNode with an ArrayList to check if the
    // operations are consistent.
    private static class ListConsistencyChecker<T>{
        protected List<ListNode<T>> confirmList = new ArrayList<>();
        protected ListNode<T> head = ListNode.init();

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("ListConsistencyChecker{");
            if(!confirmList.isEmpty()) {
                sb.append('\n');
                for (ListNode<T> node : confirmList)
                    sb.append("\t").append(node.getObject().toString()).append(",\n");
                sb.deleteCharAt(sb.length() - 2);
            }
            sb.append('}');
            return sb.toString();
        }

        public void remove(ListNode<T> node) {
            confirmList.remove(node);
            ListNode.remove(node);
        }

        public void removeAndInit(ListNode<T> node) {
            confirmList.remove(node);
            ListNode.removeAndInit(node);
        }

        public void delete(ListNode<T> node) {
            confirmList.remove(node);
            ListNode.delete(node);
        }

        public void addHead(ListNode<T> node) {
            confirmList.addFirst(node);
            ListNode.addFirst(node, head);
        }

        public void addTail(ListNode<T> node) {
            confirmList.addLast(node);
            ListNode.addLast(node, head);
        }

        public void moveToFirst(ListNode<T> node) {
            confirmList.remove(node);
            confirmList.addFirst(node);
            ListNode.moveToHead(node, head);
        }

        public void moveToLast(ListNode<T> node) {
            confirmList.remove(node);
            confirmList.addLast(node);
            ListNode.moveToTail(node, head);
        }

        public void confirmEquality() {
            // confirms that both lists agree on whether it is empty or not
            assert ListNode.isEmpty(head) == confirmList.isEmpty();

            // return if list is empty
            if(ListNode.isEmpty(head))
                return;

            // gets first element
            ListIterator<ListNode<T>> clIt = confirmList.listIterator(); // confirm list iterator
            ListNode<T> node = head, nodeConfirm;

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
        ListNode<Object> listNode = ListNode.create(o1);
        Object obj = listNode.getObject();
        assert obj == o1;
        assert obj != o2;
    }

    @Test
    void initList() {
        Object o = new Object();
        ListNode<Object> head = ListNode.init();
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
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
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
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
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

        ListNode<String> head = lchecker.head;
        assert head.getPrev() == head && head.getNext() == head;
    }

    @Test
    void delete() {
        ListConsistencyChecker<String> lchecker = new ListConsistencyChecker<>();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
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

        ListNode<String> head = lchecker.head;
        assert head.getPrev() == head && head.getNext() == head;
    }

    @Test
    void moveToHead() {
        ListConsistencyChecker<String> lchecker = new ListConsistencyChecker<>();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
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
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
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
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        assert !ListNode.isFirst(node1, head);
        assert !ListNode.isFirst(node2, head);
        assert !ListNode.isFirst(node3, head);
        assert !ListNode.isFirst(head, head);

        ListNode.addFirst(node3, head);
        assert ListNode.isFirst(node3, head);

        ListNode.addFirst(node2, head);
        assert ListNode.isFirst(node2, head);
        assert !ListNode.isFirst(node3, head);

        ListNode.addFirst(node1, head);
        assert ListNode.isFirst(node1, head);
        assert !ListNode.isFirst(node2, head);
        assert !ListNode.isFirst(node3, head);
    }

    @Test
    void isLast() {
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        assert !ListNode.isLast(node1, head);
        assert !ListNode.isLast(node2, head);
        assert !ListNode.isLast(node3, head);
        assert !ListNode.isLast(head, head);

        ListNode.addLast(node3, head);
        assert ListNode.isLast(node3, head);

        ListNode.addLast(node2, head);
        assert ListNode.isLast(node2, head);
        assert !ListNode.isLast(node3, head);

        ListNode.addLast(node1, head);
        assert ListNode.isLast(node1, head);
        assert !ListNode.isLast(node2, head);
        assert !ListNode.isLast(node3, head);
    }

    @Test
    void isHead() {
        ListNode<String> head = ListNode.init();
        assert ListNode.isHead(head, head);

        ListNode<String> node1 = ListNode.create("1");
        assert ListNode.isHead(head, head);
        assert !ListNode.isHead(node1, head);

        ListNode.addFirst(node1, head);
        assert ListNode.isHead(head, head);
        assert !ListNode.isHead(node1, head);
    }

    @Test
    void isEmpty() {
        ListNode<String> head = ListNode.init();
        assert ListNode.isEmpty(head);

        ListNode<String> node1 = ListNode.create("1");
        ListNode.addFirst(node1, head);
        assert !ListNode.isEmpty(head);

        ListNode.removeAndInit(node1);
        assert ListNode.isEmpty(head);

        ListNode.addLast(node1, head);
        assert !ListNode.isEmpty(head);
    }

    @Test
    void getFirst() {
        ListNode<String> head = ListNode.init();
        assert ListNode.getFirst(head) == head;

        ListNode<String> node1 = ListNode.create("1");
        ListNode.addFirst(node1, head);
        assert ListNode.getFirst(head) == node1;

        ListNode.removeAndInit(node1);
        assert ListNode.getFirst(head) == head;

        ListNode.addLast(node1, head);
        assert ListNode.getFirst(head) == node1;

        ListNode<String> node2 = ListNode.create("2");
        ListNode.addFirst(node2, head);
        assert ListNode.getFirst(head) == node2;
    }

    @Test
    void getLast() {
        ListNode<String> head = ListNode.init();
        assert ListNode.getLast(head) == head;

        ListNode<String> node1 = ListNode.create("1");
        ListNode.addFirst(node1, head);
        assert ListNode.getLast(head) == node1;

        ListNode.removeAndInit(node1);
        assert ListNode.getLast(head) == head;

        ListNode.addLast(node1, head);
        assert ListNode.getLast(head) == node1;

        ListNode<String> node2 = ListNode.create("2");
        ListNode.addLast(node2, head);
        assert ListNode.getLast(head) == node2;
    }

    @Test
    void forEach() {
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        ListNode.addFirst(node3, head);
        ListNode.addFirst(node2, head);
        ListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        ListNode.forEach(head, list::add);
        assert list.size() == 3;
        assert list.indexOf("1") == 0;
        assert list.indexOf("2") == 1;
        assert list.indexOf("3") == 2;
    }

    @Test
    void forEachReverse() {
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        ListNode.addFirst(node3, head);
        ListNode.addFirst(node2, head);
        ListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        ListNode.forEachReverse(head, list::add);
        assert list.size() == 3;
        assert list.indexOf("3") == 0;
        assert list.indexOf("2") == 1;
        assert list.indexOf("1") == 2;
    }

    @Test
    void forEachWithIterator() {
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        ListNode.addFirst(node3, head);
        ListNode.addFirst(node2, head);
        ListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        ListNode.Iterator<String> it = ListNode.iterator(head);
        while(it.hasNext())
            list.add(it.next());

        assert list.size() == 3;
        assert list.indexOf("1") == 0;
        assert list.indexOf("2") == 1;
        assert list.indexOf("3") == 2;
    }

    @Test
    void forEachReverseWithIterator() {
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        ListNode.addFirst(node3, head);
        ListNode.addFirst(node2, head);
        ListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        ListNode.Iterator<String> it = ListNode.iterator(head);
        while(it.hasPrevious())
            list.add(it.previous());

        assert list.size() == 3;
        assert list.indexOf("3") == 0;
        assert list.indexOf("2") == 1;
        assert list.indexOf("1") == 2;
    }

    @Test
    void forEachForwardPlusReverseWithIterator() {
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        ListNode.addFirst(node3, head);
        ListNode.addFirst(node2, head);
        ListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        ListNode.Iterator<String> it = ListNode.iterator(head);
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
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        ListNode.addFirst(node3, head);
        ListNode.addFirst(node2, head);
        ListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        ListNode.Iterator<String> it = ListNode.iterator(head);
        ListNode<String> node0 = it.addAfter("0");
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
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        ListNode.addFirst(node3, head);
        ListNode.addFirst(node2, head);
        ListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        ListNode.Iterator<String> it = ListNode.iterator(head);
        ListNode<String> node4 = it.addBefore("4");
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
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        ListNode.addFirst(node3, head);
        ListNode.addFirst(node2, head);
        ListNode.addFirst(node1, head);

        List<String> list = new ArrayList<>();
        ListNode.Iterator<String> it = ListNode.iterator(head);
        while(it.hasNext())
            list.add(it.next());

        // add node4 as last because the iterator stopped due to lack of a next node
        ListNode<String> node4 = it.addAfter("4");

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
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        ListNode.addFirst(node3, head);
        ListNode.addFirst(node2, head);
        ListNode.addFirst(node1, head);

        // Makes iterator go to the last node
        ListNode.Iterator<String> it = ListNode.iterator(head);
        while(it.hasNext())
            it.next();

        // add node4 before last node
        ListNode<String> node4 = it.addBefore("4");

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
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        ListNode.addFirst(node3, head);
        ListNode.addFirst(node2, head);
        ListNode.addFirst(node1, head);

        // checks that removing after initiating the iterator
        // does not do anything.
        ListNode.Iterator<String> it = ListNode.iterator(head);
        it.remove();
        assert head.getPrev() == node3 && head.getNext() == node1;

        ListNode<String> rmvd;
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
        assert ListNode.isEmpty(head);
        // assert iterator cannot move
        assert !it.hasNext();
        assert !it.hasPrevious();
    }

    @Test
    void removeAndInitWithIterator(){
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        ListNode.addFirst(node3, head);
        ListNode.addFirst(node2, head);
        ListNode.addFirst(node1, head);

        // checks that removing after initiating the iterator
        // does not do anything.
        ListNode.Iterator<String> it = ListNode.iterator(head);
        it.removeAndInit();
        assert head.getPrev() == node3 && head.getNext() == node1;

        ListNode<String> rmvd;
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
        assert ListNode.isEmpty(head);
        // assert iterator cannot move
        assert !it.hasNext();
        assert !it.hasPrevious();
    }

    @Test
    void deleteWithIterator(){
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        ListNode.addFirst(node3, head);
        ListNode.addFirst(node2, head);
        ListNode.addFirst(node1, head);

        // checks that removing after initiating the iterator
        // does not do anything.
        ListNode.Iterator<String> it = ListNode.iterator(head);
        it.delete();
        assert head.getPrev() == node3 && head.getNext() == node1;

        String s;
        // delete node1
        s = it.next();
        it.delete();
        assert s.equals("1");
        assert node1.getPrev() == null && node1.getNext() == null && node1.getObject() == null;

        // delete node2
        s = it.next();
        it.delete();
        assert s.equals("2");
        assert node2.getPrev() == null && node2.getNext() == null && node1.getObject() == null;

        // delete node3
        s = it.next();
        it.delete();
        assert s.equals("3");
        assert node3.getPrev() == null && node3.getNext() == null && node1.getObject() == null;

        // assert list is empty
        assert ListNode.isEmpty(head);
        // assert iterator cannot move
        assert !it.hasNext();
        assert !it.hasPrevious();
    }

    @Test
    void moveToFirstWithIterator(){
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        ListNode.addFirst(node3, head);
        ListNode.addFirst(node2, head);
        ListNode.addFirst(node1, head);

        // checks that attempting to move the node after initiating the iterator
        // does not do anything.
        ListNode.Iterator<String> it = ListNode.iterator(head);
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
        ListNode<String> head = ListNode.init();
        ListNode<String> node1 = ListNode.create("1");
        ListNode<String> node2 = ListNode.create("2");
        ListNode<String> node3 = ListNode.create("3");
        ListNode.addFirst(node3, head);
        ListNode.addFirst(node2, head);
        ListNode.addFirst(node1, head);

        // checks that attempting to move the node after initiating the iterator
        // does not do anything.
        ListNode.Iterator<String> it = ListNode.iterator(head);
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
}