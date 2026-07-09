import java.util.Locale;

/**
 * ElevenLabs ortam değişkenleri, fabrika ve güvenli durum özeti.
 */
public final class ElevenLabsFabrika {
    private ElevenLabsFabrika() {
    }

    public static ElevenLabsIstemci olustur() {
        if (mockModAktif()) {
            return new ElevenLabsMockClient();
        }
        String apiKey = TtsLaboratuvarYardimci.ortam("ELEVENLABS_API_KEY", "");
        if (apiKey.isBlank()) {
            throw new IllegalStateException("ELEVENLABS_API_KEY bulunamadı.");
        }
        return new ElevenLabsClient(apiKey, ElevenLabsModelPolitikasi.ortamModeliVeyaVarsayilan());
    }

    public static ElevenLabsIstemci olusturGuvenli() {
        try {
            return olustur();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean mockModAktif() {
        String deger = System.getenv("ELEVENLABS_MOCK");
        if (deger == null || deger.isBlank()) {
            deger = System.getProperty("ELEVENLABS_MOCK");
        }
        if (deger == null || deger.isBlank()) {
            return false;
        }
        String temiz = deger.trim().toLowerCase(Locale.ROOT);
        return temiz.equals("true") || temiz.equals("1") || temiz.equals("yes") || temiz.equals("evet");
    }

    public static DurumOzeti durumOzeti() {
        boolean mock = mockModAktif();
        boolean apiKeyVar = mock || TtsLaboratuvarYardimci.ortamVar("ELEVENLABS_API_KEY");
        boolean voiceVar = TtsLaboratuvarYardimci.ortamVar("ELEVENLABS_VOICE_ID");
        String voiceId = TtsLaboratuvarYardimci.ortam("ELEVENLABS_VOICE_ID", "");
        String model = ElevenLabsModelPolitikasi.ortamModeliVeyaVarsayilan();

        if (!apiKeyVar) {
            return new DurumOzeti(
                    false,
                    "KAPALI",
                    "YOK",
                    voiceVar ? "TANIMLI" : "YOK",
                    ElevenLabsClient.voiceIdMaskele(voiceId),
                    false,
                    -1L,
                    -1L,
                    -1L,
                    model,
                    mock,
                    "ELEVENLABS_API_KEY yok"
            );
        }

        if (!voiceVar) {
            return new DurumOzeti(
                    false,
                    "KAPALI",
                    "TANIMLI",
                    "YOK",
                    "YOK",
                    false,
                    -1L,
                    -1L,
                    -1L,
                    model,
                    mock,
                    "ELEVENLABS_VOICE_ID yok"
            );
        }

        try {
            ElevenLabsIstemci client = olustur();
            ElevenLabsClient.AbonelikBilgisi abonelik = client.abonelikBilgisiniGetir();
            long kalan = abonelik.kalanPlanKredisi();
            boolean abonelikOk = abonelik.aktifMi();
            boolean voiceOk = client.sesIdDogrula(voiceId);
            boolean hazir = abonelikOk && kalan > 0L && voiceOk;
            String mesaj;
            if (!abonelikOk) {
                mesaj = "abonelik aktif değil";
            } else if (kalan <= 0L) {
                mesaj = "kredi yok: " + abonelik.kullanilanKredi() + "/" + abonelik.donemKrediLimiti() + " kullanıldı";
            } else if (!voiceOk) {
                mesaj = "voice id doğrulanamadı";
            } else {
                mesaj = "kalan kredi: " + kalan;
            }
            return new DurumOzeti(
                    hazir,
                    hazir ? "HAZIR" : "KAPALI",
                    "TANIMLI",
                    "TANIMLI",
                    ElevenLabsClient.voiceIdMaskele(voiceId),
                    abonelikOk,
                    abonelik.kullanilanKredi(),
                    abonelik.donemKrediLimiti(),
                    kalan,
                    model,
                    mock,
                    mesaj
            );
        } catch (Exception e) {
            return new DurumOzeti(
                    false,
                    "KAPALI",
                    "TANIMLI",
                    "TANIMLI",
                    ElevenLabsClient.voiceIdMaskele(voiceId),
                    false,
                    -1L,
                    -1L,
                    -1L,
                    model,
                    mock,
                    TtsLaboratuvarYardimci.kisalt(e.getMessage(), 200)
            );
        }
    }

    public record DurumOzeti(boolean hazir,
                             String ttsDurumu,
                             String apiKeyDurumu,
                             String voiceDurumu,
                             String voiceIdMasked,
                             boolean abonelikOkunabiliyor,
                             long kullanilanKredi,
                             long limitKredi,
                             long kalanKredi,
                             String varsayilanModel,
                             boolean mockMod,
                             String mesaj) {

        public String konsolOzeti() {
            StringBuilder sb = new StringBuilder();
            sb.append("ElevenLabs TTS: ").append(ttsDurumu);
            if (mockMod) {
                sb.append(" | MOCK mod");
            }
            sb.append(" | ").append(mesaj);
            sb.append(System.lineSeparator());
            sb.append("API anahtarı       : ").append(apiKeyDurumu).append(System.lineSeparator());
            sb.append("Voice ID           : ").append(voiceDurumu);
            if (!"YOK".equals(voiceDurumu)) {
                sb.append(" (").append(voiceIdMasked).append(")");
            }
            sb.append(System.lineSeparator());
            sb.append("Varsayılan model   : ").append(varsayilanModel).append(System.lineSeparator());
            if (abonelikOkunabiliyor) {
                sb.append("Kullanılan/Limit   : ")
                        .append(kullanilanKredi).append("/").append(limitKredi).append(System.lineSeparator());
                sb.append("Kalan kredi        : ").append(kalanKredi);
            } else {
                sb.append("Abonelik           : okunamadı");
            }
            return sb.toString();
        }
    }
}
