import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Tam eser üretim onay kaydı — üretim başlatmaz.
 */
public record TamEserUretimOnayi(
        String onayId,
        int eserId,
        String provider,
        String model,
        int toplamKarakter,
        int parcaSayisi,
        long tahminiKredi,
        OnayDurumu onayDurumu,
        String onayMetni,
        String createdAt,
        String approvedAt,
        String approvedByLocalUser,
        boolean tamUretimBaslatildiMi
) {
    public enum OnayDurumu {
        TASLAK,
        ONAY_BEKLIYOR,
        ONAYLANDI,
        REDDEDILDI,
        IPTAL
    }

    ObjectNode json(ObjectMapper mapper) {
        ObjectNode n = mapper.createObjectNode();
        n.put("onayId", onayId);
        n.put("eserId", eserId);
        n.put("provider", provider);
        n.put("model", model);
        n.put("toplamKarakter", toplamKarakter);
        n.put("parcaSayisi", parcaSayisi);
        n.put("tahminiKredi", tahminiKredi);
        n.put("onayDurumu", onayDurumu.name());
        n.put("onayMetni", onayMetni);
        n.put("createdAt", createdAt);
        n.put("approvedAt", approvedAt == null ? "" : approvedAt);
        n.put("approvedByLocalUser", approvedByLocalUser == null ? "" : approvedByLocalUser);
        n.put("tamUretimBaslatildiMi", tamUretimBaslatildiMi);
        return n;
    }

    static TamEserUretimOnayi fromJson(ObjectMapper mapper, ObjectNode n) {
        OnayDurumu durum;
        try {
            durum = OnayDurumu.valueOf(n.path("onayDurumu").asText(OnayDurumu.TASLAK.name()));
        } catch (IllegalArgumentException e) {
            durum = OnayDurumu.TASLAK;
        }
        return new TamEserUretimOnayi(
                n.path("onayId").asText(""),
                n.path("eserId").asInt(),
                n.path("provider").asText(""),
                n.path("model").asText(""),
                n.path("toplamKarakter").asInt(),
                n.path("parcaSayisi").asInt(),
                n.path("tahminiKredi").asLong(),
                durum,
                n.path("onayMetni").asText(""),
                n.path("createdAt").asText(""),
                n.path("approvedAt").asText(null),
                n.path("approvedByLocalUser").asText(null),
                n.path("tamUretimBaslatildiMi").asBoolean(false)
        );
    }
}
