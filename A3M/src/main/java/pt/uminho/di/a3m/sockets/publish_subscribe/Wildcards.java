package pt.uminho.di.a3m.sockets.publish_subscribe;


import java.util.Arrays;
import java.util.regex.Pattern;

public class Wildcards {

    /*public static void main(String[] args) {
        PatriciaTrie<String> trie = new PatriciaTrie<>();
        trie.put("bear","bear");
        trie.put("bell","bell");
        trie.put("bird","bird");
        trie.put("bull","bull");
        trie.put("buy","buy");
        trie.put("sell","sell");
        trie.put("stock","stock");
        trie.put("stop","stop");

        String select = trie.selectKey("bean");
        System.out.println("bean->select: " + select);
        System.out.println("bean->select->prev: " + trie.previousKey(select));
        System.out.println("bean->select->next: " + trie.nextKey(select));

        select = trie.selectKey("beat");
        System.out.println("beat->select: " + select);
        System.out.println("beat->select->prev: " + trie.previousKey(select));
        System.out.println("beat->select->next: " + trie.nextKey(select));

        System.out.println("---------");
        while (select != null) {
            System.out.println(select);
            select = trie.nextKey(select);
        }



    }

     */

    private static void getSingleLevelWildcardTopics(String[] subtopics, String[] wildcardTopics){
        StringBuilder sb;
        for (int i = 0; i < subtopics.length; i++) {
            sb = new StringBuilder();
            for (int j = 0; j < subtopics.length; j++) {
                sb.append(i != j ? subtopics[j] : '+')
                        .append("/");
            }
            sb.deleteCharAt(sb.length() - 1);
            wildcardTopics[i] = sb.toString();
        }
    }

    private static void getMultiLevelWildcardTopics(String[] subtopics, String[] wildcardTopics, int offset){
        StringBuilder sb;
        for (int i = 0; i < subtopics.length; i++) {
            sb = new StringBuilder();
            for (int j = 0; j < i; j++) {
                sb.append(subtopics[j])
                        .append("/");
            }
            sb.append("#");
            wildcardTopics[offset + i] = sb.toString();
        }
    }

    private static String[] getWildcardTopics(String topic, boolean single, boolean multiple){
        System.out.println("topic: " + topic);
        String[] subtopics = topic.split("/");
        System.out.println("subtopics: " + Arrays.toString(subtopics));

        int length = 0, offset = 0;
        if(single) {
            length += subtopics.length;
            offset = length;
        }
        if(multiple) length += subtopics.length;
        String[] wildcardTopics = new String[length];

        if(single) getSingleLevelWildcardTopics(subtopics, wildcardTopics);
        if(multiple) getMultiLevelWildcardTopics(subtopics, wildcardTopics, offset);

        System.out.println(Arrays.toString(wildcardTopics));
        return wildcardTopics;
    }

    private static String[] getSingleLevelWildcardTopics(String topic){
        return getWildcardTopics(topic, true, false);
    }

    private static String[] getMultiLevelWildcardTopics(String topic){
        return getWildcardTopics(topic, false, true);
    }

    public static void wildcards(){
        PatriciaTrie<String> trie = new PatriciaTrie<>();

        trie.put("Europe/Portugal/news","Europe/Portugal/news");
        trie.put("Europe/news","Europe/news");
        trie.put("Europe/Germany/weather","Europe/Germany/weather");
        trie.put("Europe/France","Europe/France");
        trie.put("Europe/France/news","Europe/France/news");
        trie.put("Europe/France/news/politics","Europe/France/news/politics");
        trie.put("Asia/Japan/news","Asia/Japan/news");
        trie.put("Europe/+/news", "Europe/+/news");

        String topic = "Europe/Germany/news";

        String[] wildcardTopics = getWildcardTopics(topic, true, true);

        System.out.println(Arrays.stream(wildcardTopics).filter(s -> s.equals(trie.selectKey(s))).toList());
    }

    /**
     * Trie nodes attributes:
     *  - list of subscribers
     *  -
     */

    public static void main(String[] args) {
        wildcards();
    }
}
