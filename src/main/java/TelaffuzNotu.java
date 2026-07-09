import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;

/**
 * Telaffuz sözlüğü kaydı — örnek/şablon veri; gerçek kullanıcı verisi değildir.
 */
public record TelaffuzNotu(
        String id,
        String ifade,
        String onerilenOkunus,
        String aciklama,
        int eserId,
        String dil,
        String kaynak,
        String durum,
        String uygulanmaModu,
        String createdAt,
        String updatedAt
) {
    public static final String DURUM_AKTIF = "AKTIF";
    public static final String DURUM_PASIF = "PASIF";
    public static final String DURUM_KONTROL = "KONTROL_GEREKIYOR";
    public static final String MOD_METIN = "METIN_NORMALIZE";
    public static final String MOD_DICTIONARY = "ELEVENLABS_DICTIONARY_ADAYI";
    public static final String MOD_NOT = "NOT";

    public boolean aktifMetinNormalize() {
        return DURUM_AKTIF.equalsIgnoreCase(durum) && MOD_METIN.equalsIgnoreCase(uygulanmaModu);
    }

    public ObjectNode json(ObjectMapper mapper) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", id);
        n.put("ifade", ifade);
        n.put("onerilenOkunus", onerilenOkunus);
        n.put("aciklama", aciklama);
        if (eserId > 0) n.put("eserId", eserId);
        n.put("dil", dil);
        n.put("kaynak", kaynak);
        n.put("durum", durum);
        n.put("uygulanmaModu", uygulanmaModu);
        n.put("createdAt", createdAt);
        n.put("updatedAt", updatedAt);
        return n;
    }

    public static TelaffuzNotu ornek(String id, String ifade, String okunus, String aciklama,
                                     int eserId, String mod, String durum) {
        String now = OffsetDateTime.now().toString();
        return new TelaffuzNotu(id, ifade, okunus, aciklama, eserId, "tr", "ORNEK_SABLON",
                durum, mod, now, now);
    }
}
