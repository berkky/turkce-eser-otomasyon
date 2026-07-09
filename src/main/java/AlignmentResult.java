import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Forced alignment sonucu — segment ve kelime zamanlaması.
 */
public record AlignmentResult(
        int eserId,
        String previewId,
        String status,
        String provider,
        String language,
        String textHash,
        String audioHash,
        double durationSeconds,
        int wordCount,
        int segmentCount,
        boolean characterAlignmentAvailable,
        boolean wordAlignmentAvailable,
        List<AlignmentSegment> segments,
        List<String> warnings,
        String source,
        String alignmentStatus,
        String outputPathSafeName,
        String srtSafeName,
        String vttSafeName,
        boolean demoFixture,
        String createdAt
) {
    public static AlignmentResult notRequested() {
        return new AlignmentResult(0, "", AlignmentPlan.STATUS_NOT_REQUESTED, "LOCAL", "tr",
                "", "", 0, 0, 0, false, false, List.of(), List.of(),
                "LOCAL", AlignmentPlan.STATUS_NOT_REQUESTED, "", "", "", false,
                OffsetDateTime.now().toString());
    }

    public ObjectNode json(ObjectMapper mapper) {
        ObjectNode n = mapper.createObjectNode();
        if (eserId > 0) n.put("eserId", eserId);
        n.put("previewId", previewId);
        n.put("status", status);
        n.put("provider", provider);
        n.put("language", language);
        n.put("textHash", textHash);
        n.put("audioHash", audioHash);
        n.put("durationSeconds", durationSeconds);
        n.put("wordCount", wordCount);
        n.put("segmentCount", segmentCount);
        n.put("characterAlignmentAvailable", characterAlignmentAvailable);
        n.put("wordAlignmentAvailable", wordAlignmentAvailable);
        ArrayNode seg = n.putArray("segments");
        if (segments != null) {
            for (AlignmentSegment s : segments) {
                seg.add(s.json(mapper));
            }
        }
        ArrayNode uy = n.putArray("warnings");
        if (warnings != null) {
            warnings.forEach(uy::add);
        }
        n.put("source", source);
        n.put("alignmentStatus", alignmentStatus);
        n.put("outputPathSafeName", outputPathSafeName);
        n.put("srtSafeName", srtSafeName);
        n.put("vttSafeName", vttSafeName);
        n.put("demoFixture", demoFixture);
        n.put("createdAt", createdAt);
        return n;
    }
}
