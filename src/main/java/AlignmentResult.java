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
        boolean realApiUsed,
        String apiProvider,
        String apiRequestId,
        String generatedAt,
        String createdAt
) {
    public static final String SOURCE_ELEVENLABS = "ELEVENLABS";
    public static final String SOURCE_MOCK = "MOCK";
    public static final String SOURCE_DEMO_FIXTURE = "DEMO_FIXTURE";

    public static AlignmentResult notRequested() {
        String now = OffsetDateTime.now().toString();
        return new AlignmentResult(0, "", AlignmentPlan.STATUS_NOT_REQUESTED, "LOCAL", "tr",
                "", "", 0, 0, 0, false, false, List.of(), List.of(),
                "LOCAL", AlignmentPlan.STATUS_NOT_REQUESTED, "", "", "",
                false, false, "", "", now, now);
    }

    public static AlignmentResult failed(int eserId, String previewId, String textHash, String audioHash,
                                         String mesaj, AlignmentHata hata) {
        String now = OffsetDateTime.now().toString();
        List<String> uy = hata != null
                ? List.of(mesaj, hata.kullaniciMesaji())
                : List.of(mesaj);
        return new AlignmentResult(eserId, previewId, AlignmentPlan.STATUS_FAILED, "ELEVENLABS", "tr",
                textHash, audioHash, 0, 0, 0, false, false, List.of(), uy,
                SOURCE_ELEVENLABS, AlignmentPlan.STATUS_FAILED,
                AlignmentGuvenlikService.guvenliDosyaAdi(eserId, "alignment.json"), "", "",
                false, true, SOURCE_ELEVENLABS, "", now, now);
    }

    public String kaynakEtiketi() {
        if (demoFixture || SOURCE_DEMO_FIXTURE.equalsIgnoreCase(source)) {
            return "Demo fixture alignment";
        }
        if (realApiUsed || SOURCE_ELEVENLABS.equalsIgnoreCase(source)) {
            return "Gerçek ElevenLabs alignment";
        }
        return "Mock alignment";
    }

    public ObjectNode json(ObjectMapper mapper) {
        ObjectNode n = mapper.createObjectNode();
        if (eserId > 0) {
            n.put("eserId", eserId);
        }
        n.put("previewId", previewId);
        n.put("status", status);
        n.put("provider", provider);
        n.put("language", language);
        n.put("textHash", textHash);
        n.put("audioHash", audioHash);
        n.put("inputTextHash", textHash);
        n.put("inputAudioHash", audioHash);
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
        n.put("realApiUsed", realApiUsed);
        n.put("apiProvider", apiProvider == null ? "" : apiProvider);
        if (apiRequestId != null && !apiRequestId.isBlank()) {
            n.put("apiRequestId", apiRequestId);
        }
        String uretim = generatedAt != null && !generatedAt.isBlank() ? generatedAt : createdAt;
        n.put("generatedAt", uretim);
        n.put("createdAt", createdAt != null && !createdAt.isBlank() ? createdAt : uretim);
        return n;
    }
}
