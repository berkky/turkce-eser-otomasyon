import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Kelime düzeyinde hizalama zamanlaması.
 */
public record AlignmentWord(
        int index,
        String text,
        double startSeconds,
        double endSeconds
) {
    public ObjectNode json(ObjectMapper mapper) {
        ObjectNode n = mapper.createObjectNode();
        n.put("index", index);
        n.put("text", text);
        n.put("startSeconds", startSeconds);
        n.put("endSeconds", endSeconds);
        return n;
    }
}
