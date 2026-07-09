import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Segment (cümle/parça) düzeyinde hizalama.
 */
public record AlignmentSegment(
        int index,
        String text,
        double startSeconds,
        double endSeconds,
        List<AlignmentWord> words
) {
    public ObjectNode json(ObjectMapper mapper) {
        ObjectNode n = mapper.createObjectNode();
        n.put("index", index);
        n.put("text", text);
        n.put("startSeconds", startSeconds);
        n.put("endSeconds", endSeconds);
        ArrayNode kelimeler = n.putArray("words");
        if (words != null) {
            for (AlignmentWord w : words) {
                kelimeler.add(w.json(mapper));
            }
        }
        return n;
    }
}
