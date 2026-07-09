import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Demo metrikleri — gerçek arşiv verisinden salt okunur.
 */
public final class DemoMetrikService {
    private final WebOrtam ortam;
    private final ObjectMapper json = new ObjectMapper();

    public DemoMetrikService(WebOrtam ortam) {
        this.ortam = ortam;
    }

    public DemoMetrikler hesapla() throws Exception {
        List<WebEserService.WebEserOzeti> eserler = new WebEserService(ortam).eserleriListele();
        int ornek = 0, karakter = 0, parca = 0, bolum = 0, hazir = 0, kontrol = 0, buyuk = 0, oniz = 0;
        for (WebEserService.WebEserOzeti e : eserler) {
            karakter += e.karakter();
            parca += e.ttsParca();
            bolum += e.bolum();
            if ("HAZIR".equalsIgnoreCase(e.metadataDurumu())) hazir++;
            if (e.metadataDurumu() != null && e.metadataDurumu().toUpperCase(Locale.ROOT).contains("KONTROL")) kontrol++;
            if (e.buyukEser()) buyuk++;
            if (e.onizlemeVar()) oniz++;
            if (e.eserId() == SesKaliteOlcutleri.KASAGI_ESER_ID
                    || e.eserId() == SesKaliteOlcutleri.ASTRONOMI_ESER_ID) {
                ornek++;
            }
        }
        return new DemoMetrikler(
                eserler.size(), ornek, karakter, parca, bolum,
                hazir, kontrol, buyuk, oniz, gitCommitKisa(ortam.projeKlasoru())
        );
    }

    public String json() throws Exception {
        DemoMetrikler m = hesapla();
        ObjectNode n = json.createObjectNode();
        n.put("toplamEser", m.toplamEser());
        n.put("ornekEser", m.ornekEser());
        n.put("toplamKarakter", m.toplamKarakter());
        n.put("toplamTtsParca", m.toplamTtsParca());
        n.put("toplamBolum", m.toplamBolum());
        n.put("metadataHazir", m.metadataHazir());
        n.put("kontrolGerek", m.kontrolGerek());
        n.put("buyukEser", m.buyukEser());
        n.put("onizleme", m.onizleme());
        n.put("gitCommit", m.gitCommit());
        return WebGuvenlikService.guvenliJson(json.writerWithDefaultPrettyPrinter().writeValueAsString(n));
    }

    public static String gitCommitKisa(Path projeKlasoru) {
        try {
            Path gitDir = projeKlasoru.resolve(".git");
            if (!Files.isDirectory(gitDir)) {
                return "bilinmiyor";
            }
            String head = Files.readString(gitDir.resolve("HEAD"), StandardCharsets.UTF_8).trim();
            String hash;
            if (head.startsWith("ref:")) {
                Path ref = gitDir.resolve(head.substring(4).trim());
                if (!Files.isRegularFile(ref)) {
                    return "bilinmiyor";
                }
                hash = Files.readString(ref, StandardCharsets.UTF_8).trim();
            } else {
                hash = head;
            }
            return hash.length() >= 7 ? hash.substring(0, 7) : hash;
        } catch (Exception e) {
            return "bilinmiyor";
        }
    }

    public record DemoMetrikler(
            int toplamEser, int ornekEser, int toplamKarakter, int toplamTtsParca, int toplamBolum,
            int metadataHazir, int kontrolGerek, int buyukEser, int onizleme, String gitCommit
    ) {
    }
}
