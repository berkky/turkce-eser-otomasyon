import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kalite paneli web entegrasyonu.
 */
public final class WebKalitePanelService {
    private final WebOrtam ortam;
    private final ObjectMapper json = new ObjectMapper();
    private SesKalitePanelRaporu rapor;
    private Map<String, Path> mediaKayitlari = Map.of();

    public WebKalitePanelService(WebOrtam ortam) {
        this.ortam = ortam;
    }

    public SesKalitePanelRaporu rapor() throws Exception {
        if (rapor == null) {
            yenile();
        }
        return rapor;
    }

    public void yenile() throws Exception {
        Path panel = ortam.kalitePanel();
        if (!Files.isRegularFile(panel.resolve("kalite-panel.json"))) {
            SesKalitePanelService.uret(ortam.sesArsivi(), ortam.metinArsivi(), panel, ortam.projeKlasoru());
        } else {
            List<SesOnizlemeKaydi> oniz = new SesOnizlemeKesifService(ortam.projeKlasoru())
                    .kesfet(ortam.sesArsivi(), ortam.metinArsivi());
            SesKalitePanelService.PanelSonucu sonuc = SesKalitePanelService.uret(
                    ortam.sesArsivi(), ortam.metinArsivi(), panel, ortam.projeKlasoru());
            rapor = sonuc.rapor();
            mediaKayitlari = mediaHaritasi(oniz);
            return;
        }
        SesKalitePanelService.PanelSonucu sonuc = SesKalitePanelService.uret(
                ortam.sesArsivi(), ortam.metinArsivi(), panel, ortam.projeKlasoru());
        rapor = sonuc.rapor();
        mediaKayitlari = mediaHaritasi(rapor.onizlemeler());
    }

    public String kaliteJson() throws Exception {
        ObjectNode kok = rapor().jsonOzeti(json);
        return WebGuvenlikService.guvenliJson(json.writerWithDefaultPrettyPrinter().writeValueAsString(kok));
    }

    public Path mediaDosyasi(String safeId) {
        return mediaKayitlari.get(safeId);
    }

    public Map<String, Path> mediaKayitlari() {
        return mediaKayitlari;
    }

    public static Map<String, Path> mediaHaritasi(List<SesOnizlemeKaydi> kayitlar) {
        Map<String, Path> harita = new HashMap<>();
        for (SesOnizlemeKaydi k : kayitlar) {
            if (k.audioPath() == null || k.audioPath().isBlank()) {
                continue;
            }
            if (SesKaliteOlcutleri.DURUM_BEKLENIYOR.equals(k.status())) {
                continue;
            }
            String id = guvenliId(k);
            harita.put(id, Path.of(k.audioPath()));
        }
        return harita;
    }

    public static String guvenliId(SesOnizlemeKaydi k) {
        String hash = k.requestHash() == null || k.requestHash().isBlank()
                ? String.valueOf(k.audioPath().hashCode())
                : k.requestHash().substring(0, Math.min(12, k.requestHash().length()));
        return "e" + k.eserId() + "-" + k.provider().toLowerCase() + "-" + hash.replaceAll("[^a-zA-Z0-9]", "");
    }

    public String telaffuzJson() throws Exception {
        Path dosya = ortam.kalitePanel().resolve("telaffuz-notlari.json");
        if (!Files.isRegularFile(dosya)) {
            ObjectNode bos = json.createObjectNode();
            bos.put("notlar", "Dosya henüz oluşturulmadı");
            return json.writeValueAsString(bos);
        }
        String icerik = Files.readString(dosya, StandardCharsets.UTF_8);
        return WebGuvenlikService.guvenliJson(icerik);
    }
}
