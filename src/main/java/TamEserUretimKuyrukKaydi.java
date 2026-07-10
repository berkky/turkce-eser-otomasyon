import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Onaylı tam eser üretim kuyruk kaydı — TTS başlatmaz.
 */
public record TamEserUretimKuyrukKaydi(
        String jobId,
        int eserId,
        String onayId,
        String provider,
        String model,
        KuyrukDurumu status,
        int parcaSayisi,
        int toplamKarakter,
        String outputSafeName,
        String createdAt,
        String updatedAt,
        String notlar
) {
    public enum KuyrukDurumu {
        WAITING_APPROVAL,
        APPROVED_NOT_STARTED,
        READY_FOR_MANUAL_RUN,
        BLOCKED,
        CANCELLED
    }

    ObjectNode json(ObjectMapper mapper) {
        ObjectNode n = mapper.createObjectNode();
        n.put("jobId", jobId);
        n.put("eserId", eserId);
        n.put("onayId", onayId);
        n.put("provider", provider);
        n.put("model", model);
        n.put("status", status.name());
        n.put("parcaSayisi", parcaSayisi);
        n.put("toplamKarakter", toplamKarakter);
        n.put("outputSafeName", outputSafeName);
        n.put("createdAt", createdAt);
        n.put("updatedAt", updatedAt);
        n.put("notlar", notlar);
        return n;
    }

    static TamEserUretimKuyrukKaydi fromJson(ObjectMapper mapper, ObjectNode n) {
        KuyrukDurumu durum;
        try {
            durum = KuyrukDurumu.valueOf(n.path("status").asText(KuyrukDurumu.WAITING_APPROVAL.name()));
        } catch (IllegalArgumentException e) {
            durum = KuyrukDurumu.WAITING_APPROVAL;
        }
        return new TamEserUretimKuyrukKaydi(
                n.path("jobId").asText(""),
                n.path("eserId").asInt(),
                n.path("onayId").asText(""),
                n.path("provider").asText(""),
                n.path("model").asText(""),
                durum,
                n.path("parcaSayisi").asInt(),
                n.path("toplamKarakter").asInt(),
                n.path("outputSafeName").asText(""),
                n.path("createdAt").asText(""),
                n.path("updatedAt").asText(""),
                n.path("notlar").asText("")
        );
    }
}
