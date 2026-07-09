import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * ElevenLabs JSON API yanıtları — API anahtarı sızdırmaz.
 */
public final class ElevenLabsApiService {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ElevenLabsApiService() {
    }

    public static String durumJson() throws Exception {
        ElevenLabsFabrika.DurumOzeti o = ElevenLabsFabrika.durumOzeti();
        ObjectNode n = JSON.createObjectNode();
        n.put("hazir", o.hazir());
        n.put("ttsDurumu", o.ttsDurumu());
        n.put("apiAnahtariDurumu", o.apiKeyDurumu());
        n.put("voiceDurumu", o.voiceDurumu());
        n.put("voiceIdMasked", o.voiceIdMasked());
        n.put("mockMod", o.mockMod());
        n.put("varsayilanModel", o.varsayilanModel());
        n.put("kullanilanKredi", o.kullanilanKredi());
        n.put("limitKredi", o.limitKredi());
        n.put("kalanKredi", o.kalanKredi());
        n.put("mesaj", o.mesaj());
        n.put("onizlemeModu", "KISA_ONIZLEME");
        n.put("tamUretimKapali", true);
        String ham = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(n);
        return WebGuvenlikService.guvenliJson(ham);
    }
}
