import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;

/**
 * Forced alignment plan — önizleme ses/metin eşlemesi.
 */
public record AlignmentPlan(
        int eserId,
        String previewId,
        String audioSafeId,
        String textHash,
        String audioHash,
        String provider,
        String status,
        String reason,
        int estimatedTextCharacters,
        String audioFileSafeName,
        String outputJsonSafeName,
        String createdAt,
        String updatedAt
) {
    public static final String STATUS_NOT_REQUESTED = "NOT_REQUESTED";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_BLOCKED = "BLOCKED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    public static AlignmentPlan notRequested() {
        return new AlignmentPlan(0, "", "", "", "", "LOCAL",
                STATUS_NOT_REQUESTED, "Henüz alignment istenmedi", 0,
                "", "", OffsetDateTime.now().toString(), OffsetDateTime.now().toString());
    }

    public ObjectNode json(ObjectMapper mapper) {
        ObjectNode n = mapper.createObjectNode();
        if (eserId > 0) n.put("eserId", eserId);
        n.put("previewId", previewId);
        n.put("audioSafeId", audioSafeId);
        n.put("textHash", textHash);
        n.put("audioHash", audioHash);
        n.put("provider", provider);
        n.put("status", status);
        n.put("reason", reason);
        n.put("estimatedTextCharacters", estimatedTextCharacters);
        n.put("audioFileSafeName", audioFileSafeName);
        n.put("outputJsonSafeName", outputJsonSafeName);
        n.put("createdAt", createdAt);
        n.put("updatedAt", updatedAt);
        return n;
    }
}
