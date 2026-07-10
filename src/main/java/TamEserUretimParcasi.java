import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Tam eser üretim planı parça özeti (yol içermez).
 */
public record TamEserUretimParcasi(int sira, String safeName, int karakterSayisi, int tahminiSureSaniye) {
    ObjectNode json(ObjectMapper mapper) {
        ObjectNode n = mapper.createObjectNode();
        n.put("sira", sira);
        n.put("safeName", safeName);
        n.put("karakterSayisi", karakterSayisi);
        n.put("tahminiSureSaniye", tahminiSureSaniye);
        return n;
    }
}
