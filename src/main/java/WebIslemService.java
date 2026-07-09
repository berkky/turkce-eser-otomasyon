import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Güvenli web işlemleri — yalnızca Java servis çağrıları, shell yok.
 */
public final class WebIslemService {
    private static final int IZINLI_ONIZLEME_ESER = SesKaliteOlcutleri.KASAGI_ESER_ID;

    private final WebOrtam ortam;
    private final WebKalitePanelService kalitePanel;
    private final ConcurrentHashMap<String, Long> nonceDeposu = new ConcurrentHashMap<>();

    public WebIslemService(WebOrtam ortam, WebKalitePanelService kalitePanel) {
        this.ortam = ortam;
        this.kalitePanel = kalitePanel;
    }

    public String yeniNonce() {
        String nonce = UUID.randomUUID().toString();
        nonceDeposu.put(nonce, System.currentTimeMillis());
        return nonce;
    }

    public boolean nonceGecerli(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }
        Long zaman = nonceDeposu.remove(nonce.trim());
        if (zaman == null) {
            return false;
        }
        return System.currentTimeMillis() - zaman < 600_000L;
    }

    public WebIslemSonucu islem(String aksiyon) throws Exception {
        return islem(aksiyon, Map.of());
    }

    public WebIslemSonucu islem(String aksiyon, Map<String, String> form) throws Exception {
        String ad = aksiyon == null ? "" : aksiyon.trim().toLowerCase(Locale.ROOT);
        return switch (ad) {
            case "kalite-yenile" -> {
                kalitePanel.yenile();
                yield WebIslemSonucu.basarili("Kalite paneli yeniden oluşturuldu.", "/kalite");
            }
            case "sistem-yenile" -> WebIslemSonucu.basarili("Sistem durumu yenilendi.", "/sistem");
            case "eser-tara" -> WebIslemSonucu.basarili("Eser listesi güncellendi.", "/eserler");
            case "elevenlabs-onizleme" -> elevenLabsOnizleme(form);
            case "alignment-mock" -> alignmentMock(form);
            default -> WebIslemSonucu.hatali("Bilinmeyen işlem: " + aksiyon);
        };
    }

    private WebIslemSonucu elevenLabsOnizleme(Map<String, String> form) throws Exception {
        int eserId = parseEserId(form.get("eserId"));
        if (eserId <= 0) {
            eserId = IZINLI_ONIZLEME_ESER;
        }
        if (eserId == SesKaliteOlcutleri.ASTRONOMI_ESER_ID) {
            return WebIslemSonucu.hatali(
                    "ESER-00006 büyük eser — önce manuel maliyet planı ve onay gerekir. Web önizlemesi engellendi.");
        }
        if (eserId != IZINLI_ONIZLEME_ESER) {
            return WebIslemSonucu.hatali("Web önizlemesi yalnızca ESER-00005 (Kaşağı) için açıktır.");
        }

        ElevenLabsOnizlemeService.OnizlemeSonucu sonuc = ElevenLabsOnizlemeService.uret(
                eserId, ortam.metinArsivi(), ortam.sesArsivi(), ortam.kalitePanel(), null, null);

        if (!sonuc.basarili()) {
            return WebIslemSonucu.hatali(sonuc.mesaj());
        }

        kalitePanel.yenile();
        String mesaj = sonuc.mevcutKullanildi()
                ? "Mevcut ElevenLabs önizlemesi kullanıldı; kalite paneli yenilendi."
                : "ElevenLabs önizlemesi üretildi; kalite paneli yenilendi.";
        return WebIslemSonucu.basarili(mesaj, "/eser/" + eserId);
    }

    private WebIslemSonucu alignmentMock(Map<String, String> form) throws Exception {
        int eserId = parseEserId(form.get("eserId"));
        if (eserId <= 0) {
            eserId = IZINLI_ONIZLEME_ESER;
        }
        if (eserId != IZINLI_ONIZLEME_ESER) {
            return WebIslemSonucu.hatali("Mock alignment yalnızca ESER-00005 için açıktır.");
        }
        AlignmentService svc = new AlignmentService(ortam.sesArsivi());
        AlignmentService.AlignmentSonucu sonuc = svc.uret(eserId, true, false);
        if (!sonuc.basarili()) {
            return WebIslemSonucu.hatali(sonuc.mesaj());
        }
        String mesaj = sonuc.mevcut()
                ? "Mevcut mock alignment kullanıldı."
                : "Mock alignment üretildi (" + sonuc.sonuc().segmentCount() + " segment).";
        return WebIslemSonucu.basarili(mesaj, "/eser/" + eserId + "/alignment");
    }

    private static int parseEserId(String ham) {
        if (ham == null || ham.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(ham.trim().replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void klasorleriHazirla() throws Exception {
        Files.createDirectories(ortam.metinArsivi());
        Files.createDirectories(ortam.sesArsivi());
        Files.createDirectories(ortam.kuyruk());
        Files.createDirectories(ortam.kalitePanel());
        Files.createDirectories(ortam.sesArsivi().resolve("_alignment"));
    }
}
