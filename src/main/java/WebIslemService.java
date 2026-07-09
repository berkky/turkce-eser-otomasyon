import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Güvenli web işlemleri — yalnızca Java servis çağrıları, shell yok.
 */
public final class WebIslemService {
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
        return switch (aksiyon == null ? "" : aksiyon.trim().toLowerCase()) {
            case "kalite-yenile" -> {
                kalitePanel.yenile();
                yield WebIslemSonucu.basarili("Kalite paneli yeniden oluşturuldu.", "/kalite");
            }
            case "sistem-yenile" -> WebIslemSonucu.basarili("Sistem durumu yenilendi.", "/sistem");
            case "eser-tara" -> WebIslemSonucu.basarili("Eser listesi güncellendi.", "/eserler");
            default -> WebIslemSonucu.hatali("Bilinmeyen işlem: " + aksiyon);
        };
    }

    public void klasorleriHazirla() throws Exception {
        Files.createDirectories(ortam.metinArsivi());
        Files.createDirectories(ortam.sesArsivi());
        Files.createDirectories(ortam.kuyruk());
        Files.createDirectories(ortam.kalitePanel());
    }
}
