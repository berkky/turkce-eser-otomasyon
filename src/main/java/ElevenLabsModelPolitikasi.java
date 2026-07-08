import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Türkçe eser türüne göre ElevenLabs model önerisi.
 * Bu adımda otomatik tam üretim uygulanmaz; yalnızca politika ve log çıktısı sağlanır.
 */
public final class ElevenLabsModelPolitikasi {
    public static final String VARSAYILAN_MODEL = "eleven_multilingual_v2";
    public static final String V3_MODEL = "eleven_v3";
    public static final String FLASH_MODEL = "eleven_flash_v2_5";

    private static final Set<String> DESTEKLENEN_MODELLER = Set.of(
            VARSAYILAN_MODEL,
            V3_MODEL,
            FLASH_MODEL,
            "eleven_turbo_v2_5"
    );

    private ElevenLabsModelPolitikasi() {
    }

    public enum EserTuru {
        KITAP,
        UZUN_ANLATI,
        HIKAYE,
        SIIR,
        DIYALOG,
        HABER,
        BLOG,
        TWEET,
        KISA_ICERIK
    }

    public static String onerilenModel(EserTuru tur) {
        if (tur == null) {
            return ortamModeliVeyaVarsayilan();
        }
        return switch (tur) {
            case SIIR, DIYALOG, HIKAYE -> V3_MODEL;
            case HABER, BLOG, TWEET, KISA_ICERIK -> FLASH_MODEL;
            case KITAP, UZUN_ANLATI -> VARSAYILAN_MODEL;
        };
    }

    public static String ortamModeliVeyaVarsayilan() {
        String ortam = TtsLaboratuvarYardimci.ortam("ELEVENLABS_MODEL", VARSAYILAN_MODEL);
        return modelIdTemizle(ortam);
    }

    public static String modelIdTemizle(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return VARSAYILAN_MODEL;
        }
        String temiz = modelId.trim().toLowerCase(Locale.ROOT);
        if (DESTEKLENEN_MODELLER.contains(temiz)) {
            return temiz;
        }
        return VARSAYILAN_MODEL;
    }

    public static boolean desteklenenModelMi(String modelId) {
        return modelId != null && DESTEKLENEN_MODELLER.contains(modelId.trim().toLowerCase(Locale.ROOT));
    }

    public static List<String> bilinenModeller() {
        return List.copyOf(DESTEKLENEN_MODELLER);
    }

    public static String politikaOzeti() {
        StringBuilder sb = new StringBuilder();
        sb.append("ElevenLabs model politikası:").append(System.lineSeparator());
        for (Map.Entry<EserTuru, String> giris : politikaHaritasi().entrySet()) {
            sb.append("  - ")
                    .append(giris.getKey().name())
                    .append(" -> ")
                    .append(giris.getValue())
                    .append(System.lineSeparator());
        }
        sb.append("Varsayılan ortam modeli: ").append(ortamModeliVeyaVarsayilan());
        return sb.toString().trim();
    }

    public static Map<EserTuru, String> politikaHaritasi() {
        Map<EserTuru, String> harita = new LinkedHashMap<>();
        for (EserTuru tur : EserTuru.values()) {
            harita.put(tur, onerilenModel(tur));
        }
        return harita;
    }
}
