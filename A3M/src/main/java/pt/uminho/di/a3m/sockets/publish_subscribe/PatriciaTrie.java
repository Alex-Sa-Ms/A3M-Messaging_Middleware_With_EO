package pt.uminho.di.a3m.sockets.publish_subscribe;

import java.util.*;
import java.util.function.Function;

/**
 * This is a simplification of the patricia trie use in <b>nanomsg</b> (<a href="https://250bpm.com/blog:19/index.html">ref</a>).
 * @param <V> value held by nodes.
 */
public class PatriciaTrie<V> {
    private static class Node<V>{
        String key;
        V value;
        final Map<Character, Node<V>> children = new HashMap<>();

        public Node(String key, V value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        void setKey(String key) {
            this.key = key;
        }

        char getChar(){
            return this.key.charAt(0);
        }

        public V getValue() {
            return value;
        }

        public void setValue(V value) {
            this.value = value;
        }

        void putChild(char c, Node<V> child){
            children.put(c, child);
        }

        void removeChild(char c){
            children.remove(c);
        }

        Node<V> getChild(char c){
            return children.get(c);
        }

        boolean hasChildren(){
            return !children.isEmpty();
        }

        public String toStringWithChildren(int length) {
            StringBuilder sb = new StringBuilder("Node{");
            sb.append("key='").append(key).append('\'');
            sb.append(", value=").append(value);
            sb.append(", children=\n");
            length += sb.length();
            for (Map.Entry<Character, Node<V>> entry : children.entrySet()){
                sb.repeat(' ', length);
                sb.append(entry.getKey()).append('=').append(entry.getValue().toStringWithChildren(length));
            }
            sb.repeat(' ', length).append('}');
            return sb + "\n";
        }

        public String toStringWithChildren(){
            return toStringWithChildren(0);
        }

        @Override
        public String toString() {
            return "Node{" +
                    "key='" + key + '\'' +
                    ", value=" + value +
                    '}';
        }
    }

    private final Node<V> root;

    public PatriciaTrie(V rootValue) {
        this.root = new Node<>("", rootValue);
    }

    public PatriciaTrie() {
        this.root = new Node<>("", null);
    }

    /**
     * Finds the node that represents the given key, or if such node
     * does not exist, returns the node that would be the parent
     * of a node with such key.
     * @param key key to be used for the search
     * @return map entry containing a key and a node.
     */
    private Map.Entry<String,Node<V>> selectEntry(String key){
        SelectIterator sIt = selectIterator(key);
        Map.Entry<String, Node<V>> entry = null;
        while (sIt.hasNext())
            entry = sIt.next();
        if(entry == null) entry = sIt.next();
        return entry;
    }

    /**
     * Finds entry associated with the given key, or if entry node
     * does not exist, returns the parent entry.
     * @param key key to be used for the search
     * @return entry containing a key and its associated value (which might be null).
     */
    public Map.Entry<String, V> select(String key){
        Map.Entry<String, Node<V>> entry = selectEntry(key);
        return new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().getValue());
    }

    /**
     * Retrieves key if it is present or the key of its parent.
     * @param key key to be used for the search
     * @return the key if a node associated with it exists or the
     * key of the parent node.
     */
    public String selectKey(String key){
        Map.Entry<String, Node<V>> entry = selectEntry(key);
        return entry.getKey();
    }

    private Map.Entry<String,Node<V>> selectParentEntry(String key){
        // root node does not have a parent
        if("".equals(key)) return null;

        SelectIterator sIt = selectIterator(key);
        Map.Entry<String, Node<V>> parentEntry = null, entry = null;
        while (sIt.hasNext()) {
            parentEntry = entry;
            entry = sIt.next();
        }
        // if parent entry is null, then the parent entry is the root
        if(parentEntry == null)
            parentEntry = sIt.next();
        // if entry is different from null, check if it is not a parent node
        if(entry != null && !entry.getKey().equals(key))
            parentEntry = entry;
        return parentEntry;
    }

    private SelectIterator selectIterator(String key){
        return new SelectIterator(key);
    }

    private class SelectIterator{
        String key, // key being searched
               selectKey = ""; // key of the current node
        int i = 0; // index of the key's character being analyzed
        StringBuilder sKeyBuilder = new StringBuilder(); // select key builder
        Node<V> selectNode = root, tmpNode;
        Boolean hasNext = null;

        public SelectIterator(String key) {
            if(key == null)
                throw new IllegalArgumentException("Key is null");
            this.key = key;
        }

        public boolean hasNext() {
            if(hasNext != null) return hasNext;

            hasNext = !selectKey.equals(key);
            if(hasNext){
                // get next char to be analyzed
                char c = key.charAt(i);
                // using the char get a node associated with the
                // character and that follows the current select node
                tmpNode = selectNode.getChild(c);
                // if such node exists, then test if the node's
                // "complete" key is still a prefix of the key being searched
                if(tmpNode != null){
                    sKeyBuilder.append(tmpNode.getKey());
                    if(key.startsWith(sKeyBuilder.toString())) {
                        selectKey = sKeyBuilder.toString();
                        selectNode = tmpNode;
                        i += tmpNode.getKey().length();
                        return hasNext;
                    }
                }
                // if the tmp node does not exist or if its "complete" key
                // is not a prefix of the key being searched, then
                // the search is completed and the node that would be the
                // parent of node with the key being searched can now be returned.
            }
            return false;
        }

        public Map.Entry<String, Node<V>> next() {
            // perform a hasNext() operation to update the
            // select key and select node variables
            if(hasNext == null) hasNext();
            // unlock a new hasNext() operation
            if(hasNext) hasNext = null;
            return new AbstractMap.SimpleEntry<>(selectKey, selectNode);
        }
    }

    /**
     * Searches node with given key and retrieves its value.
     * @param key key of the node to be searched
     * @return value associated with the given key (which may be null).
     * Or null, if there isn't a node associated with the key.
     * @throws IllegalArgumentException if the key is null
     */
    public V get(String key){
        if(key == null)
            throw new IllegalArgumentException("Key is null");
        Map.Entry<String,Node<V>> entry = selectEntry(key);
        V value = null;
        if(entry.getKey().equals(key))
            value = entry.getValue().getValue();
        return value;
    }

    private Map.Entry<String,Node<V>> getEntry(String key){
        Map.Entry<String,Node<V>> selectEntry = selectEntry(key);
        if(key.equals(selectEntry.getKey()))
            return selectEntry;
        return null;
    }

    /**
     * If the given key is not already associated with a value (or is mapped to null),
     * attempts to compute its value using the given mapping function and enters it
     * into this patricia trie unless the computed value is null.
     * @param key key with which the specified value is to be associated mappingFunction
     * @param mappingFunction the mapping function to compute a value
     * @return the current (existing or computed) value associated with the specified key,
     * or null if the computed value is null
     * @throws IllegalArgumentException if the key is null
     */
    public V computeIfAbsent(String key, Function<String, V> mappingFunction){
        if(key == null)
            throw new IllegalArgumentException("Key is null");

        V value = null;

        Map.Entry<String,Node<V>> selectEntry = selectEntry(key);
        String selectKey = selectEntry.getKey();
        Node<V> selectNode = selectEntry.getValue();
        // if node associated with the given key exists,
        // then check if a value should be computed
        if(key.equals(selectKey)) {
            value = selectNode.getValue();
            if(value == null) {
                value = mappingFunction.apply(key);
                selectNode.setValue(value);
            }
        }
        // Else, the returned node is a parent node,
        // so, a node must be created if the computed
        // value is not null.
        else {
            value = mappingFunction.apply(key);
            if(value != null)
                _put(key, value, selectKey, selectNode);
        }

        return value;
    }

    /**
     * Associates a value with a key, creating a node for the key
     * if one does not exist. If a node already exists, its associated
     * value is swapped by the value given as an argument.
     * @param key key to be searched
     * @param value value to associated with the key
     * @return if a node associated with the key was already present,
     * then returns the value previously associated with the key (which may be null).
     * Or null, if a node was created.
     * @throws IllegalArgumentException if the key is null
     */
    public V put(String key, V value){
        if(key == null)
            throw new IllegalArgumentException("Key is null.");

        V prevValue = null;

        Map.Entry<String,Node<V>> selectEntry = selectEntry(key);
        String selectKey = selectEntry.getKey();
        Node<V> selectNode = selectEntry.getValue();
        // if node associated with the given key exists,
        // then simply set a new value
        if(key.equals(selectKey)) {
            prevValue = selectNode.getValue();
            selectNode.setValue(value);
        }
        // Else, the returned node is a parent node,
        // so, a node must be created.
        else _put(key, value, selectKey, selectNode);

        return prevValue;
    }

    private static <V> void _put(String key, V value, String selectKey, Node<V> selectNode) {
        // get key's suffix (i.e. additional part in comparison with the parent node)
        String suffix = key.substring(selectKey.length());
        // check if the parent node already has a child associated with
        // the suffix's first character
        Node<V> next = selectNode.getChild(suffix.charAt(0));
        Node<V> node;
        // if a child does not exist, then simply create
        // one having the suffix as key
        if(next == null) {
            node = new Node<>(suffix, value);
            selectNode.putChild(suffix.charAt(0), node);
        }
        // else, if a child does exist, then an
        // intermediate node needs to be created.
        else{
            // get the longest common prefix between
            // the child node and the suffix.
            String commonPrefix = getLongestCommonPrefix(suffix, next.getKey());
            // If suffix equals the common prefix, then the suffix
            // is used to create an intermediate node which will be
            // the new parent of the "next" node
            if(commonPrefix.equals(suffix)) {
                // Create intermediate node and set it as a child
                // of the select node (parent node)
                node = new Node<>(suffix, value);
                selectNode.putChild(suffix.charAt(0), node);
                // remove the common prefix from the "next" node's
                // as it will be a child of a node holding such prefix
                next.setKey(next.getKey().substring(suffix.length()));
                // set "next" as the child of the new node
                node.putChild(next.key.charAt(0), next);
            }
            // If the common prefix does not equal to the suffix,
            // then an intermediate node needs to be created to
            // serve as parent of both nodes
            else{
                // create new parent node
                node = new Node<>(commonPrefix, null);
                selectNode.putChild(node.getChar(), node);
                // remove the common prefix from the "next" node's
                // key and the suffix, as they will be children
                // of a node having the common prefix as key
                next.setKey(next.getKey().substring(commonPrefix.length()));
                node.putChild(next.getKey().charAt(0), next);
                suffix = suffix.substring(commonPrefix.length());
                node.putChild(suffix.charAt(0), new Node<>(suffix, value));
            }
        }
    }

    /**
     * Removes and retrieves the value associated with a key.
     * The node associated with the key is only removed if it does not have children.
     * @param key key of the node to be removed (if possible)
     * @return value previously associated with the key (which may be null). Or,
     * null if a node was not found to be associated with the key.
     * @throws IllegalArgumentException if the key is null
     */
    public V remove(String key){
        if(key == null)
            throw new IllegalArgumentException("Key is null.");

        V prevValue = null;
        Map.Entry<String,Node<V>> parentEntry = selectParentEntry(key);
        if(parentEntry == null) {
            prevValue = root.getValue();
            root.setValue(null);
        }else{
            // get suffix of key in relation to the parent's complete key
            String suffix = key.substring(parentEntry.getKey().length());
            Node<V> parent = parentEntry.getValue(),
                    node = parent.getChild(suffix.charAt(0));
            // check if there is a child having the suffix as key,
            // if there isn't, then there isn't a node represented
            // by the key passed as argument, so, the removal of a node
            // with such key cannot happen
            if(node != null && node.getKey().equals(suffix)){
                // if such child exists, the node itself can only
                // be removed if it does not have children
                if(!node.hasChildren())
                    parent.removeChild(suffix.charAt(0));
                prevValue = node.getValue();
                node.setValue(null); // remove the value
                // if parent becomes empty and does not have a value,
                // then remove the parent too
                if(parent.getValue() == null && !parent.hasChildren())
                    remove(parentEntry.getKey());
            }
        }
        return prevValue;
    }

    /**
     * Retrieves a list of all keys (and their respective values) that
     * are prefixes of the key passed as argument and that do not have
     * a null value associated.
     * @param key key for which prefixes should be searched.
     * @return list of all keys (and their respective values) that
     * are prefixes of the key passed as argument.
     * @throws IllegalArgumentException if the key is null
     */
    public List<Map.Entry<String,V>> prefixesList(String key){
        if(key == null)
            throw new IllegalArgumentException("Key is null.");

        SelectIterator sIt = new SelectIterator(key);
        List<Map.Entry<String,V>> entryList = new ArrayList<>();

        // add root entry if the value is not null
        if(root.getValue() != null)
            entryList.add(new AbstractMap.SimpleEntry<>("",root.getValue()));
        while (sIt.hasNext()){
            Map.Entry<String,Node<V>> nodeEntry = sIt.next();
            V value = nodeEntry.getValue().getValue();
            if(value != null)
                entryList.add(new AbstractMap.SimpleEntry<>(
                        nodeEntry.getKey(),
                        value));
        }
        return entryList;
    }

    private static String getLongestCommonPrefix(String str1, String str2) {
        int minLength = Math.min(str1.length(), str2.length());
        int i = 0;
        while (i < minLength && str1.charAt(i) == str2.charAt(i))
            i++;
        return str1.substring(0,i);
    }

    @Override
    public String toString() {
        return "MyPatriciaTrie{\n" +
                 root.toStringWithChildren(0) +
                "\n}";
    }

    public static void main(String[] args) {
        PatriciaTrie<String> trie = new PatriciaTrie<String>();
        trie.put("Foto","Foto");
        trie.put("Fotografia","Fotografia");
        trie.put("FotoA","FotoA");
        trie.computeIfAbsent("FotoB", s -> "FotoB");
        trie.computeIfAbsent("FotoC", s -> null);
        trie.put("Foo","Foo");
        System.out.println(trie);
        System.out.println(trie.prefixesList("Fot"));
    }
}
