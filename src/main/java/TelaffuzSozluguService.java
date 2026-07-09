import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Telaffuz notları JSON okuma/yazma — kalite panel yolu.
 */
public final class TelaffuzSozluguService {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path dosya;

    public TelaffuzSozluguService(Path kalitePanelKlasoru) {
        this.dosya = kalitePanelKlasoru.resolve("telaffuz-notlari.json");
    }

    public Path dosya() {
        return dosya;
    }

    public List<TelaffuzNotu> yukle() throws Exception {
        if (!Files.isRegularFile(dosya)) {
            ornekSablonuYaz();
        }
        JsonNode kok = JSON.readTree(Files.readString(dosya, StandardCharsets.UTF_8));
        ArrayNode dizi = kok.path("notlar").isArray() ? (ArrayNode) kok.path("notlar") : JSON.createArrayNode();
        List<TelaffuzNotu> liste = new ArrayList<>();
        for (JsonNode n : dizi) {
            TelaffuzNotu not = jsonOku(n);
            if (not != null) {
                liste.add(not);
            }
        }
        if (liste.isEmpty()) {
            liste = ornekNotlar();
            kaydet(liste);
        } else if (eskiSemaMi(liste)) {
            liste = ornekNotlar();
            kaydet(liste);
        }
        return liste;
    }

    private static boolean eskiSemaMi(List<TelaffuzNotu> liste) {
        return liste.stream().noneMatch(n ->
                TelaffuzNotu.MOD_DICTIONARY.equalsIgnoreCase(n.uygulanmaModu())
                        || TelaffuzNotu.MOD_METIN.equalsIgnoreCase(n.uygulanmaModu()));
    }

    public String jsonGuvenli() throws Exception {
        ObjectNode kok = JSON.createObjectNode();
        kok.put("aciklama", "Örnek telaffuz notları — gerçek kullanıcı verisi değildir. "
                + "İleride ElevenLabs pronunciation dictionary veya metin normalizasyonuna aktarılabilir.");
        ArrayNode dizi = kok.putArray("notlar");
        for (TelaffuzNotu n : yukle()) {
            dizi.add(n.json(JSON));
        }
        return WebGuvenlikService.guvenliJson(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(kok));
    }

    public void kaydet(List<TelaffuzNotu> notlar) throws Exception {
        Files.createDirectories(dosya.getParent());
        ObjectNode kok = JSON.createObjectNode();
        kok.put("aciklama", "Telaffuz sözlüğü — örnek/şablon veri.");
        ArrayNode dizi = kok.putArray("notlar");
        for (TelaffuzNotu n : notlar) {
            dizi.add(n.json(JSON));
        }
        Files.writeString(dosya, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(kok),
                StandardCharsets.UTF_8);
    }

    private void ornekSablonuYaz() throws Exception {
        kaydet(ornekNotlar());
    }

    public static List<TelaffuzNotu> ornekNotlar() {
        return List.of(
                TelaffuzNotu.ornek("t1", "DK", "di key", "Kısaltma — di key veya de ka tercihi notu (örnek).",
                        0, TelaffuzNotu.MOD_METIN, TelaffuzNotu.DURUM_AKTIF),
                TelaffuzNotu.ornek("t2", "ISBN", "ay es bi en", "ISBN kısaltması — Türkçe harf okunuşu (örnek).",
                        0, TelaffuzNotu.MOD_METIN, TelaffuzNotu.DURUM_AKTIF),
                TelaffuzNotu.ornek("t3", "Alfa Yayınları", "Alfa Yayınları",
                        "Yayınevi adı — net telaffuz (örnek).", 6, TelaffuzNotu.MOD_METIN, TelaffuzNotu.DURUM_AKTIF),
                TelaffuzNotu.ornek("t4", "Kaşağı", "Kaşağı", "Eser adı — şapkalı ğ (örnek).",
                        5, TelaffuzNotu.MOD_METIN, TelaffuzNotu.DURUM_AKTIF),
                TelaffuzNotu.ornek("t5", "Ahmet Fethi", "Ahmet Fethi", "Yazar adı (örnek).",
                        5, TelaffuzNotu.MOD_NOT, TelaffuzNotu.DURUM_AKTIF),
                TelaffuzNotu.ornek("t6", "Archive.org", "arşiv dot org", "Kaynak sitesi (örnek).",
                        6, TelaffuzNotu.MOD_DICTIONARY, TelaffuzNotu.DURUM_KONTROL),
                TelaffuzNotu.ornek("t7", "ElevenLabs", "ilıven labs", "Sağlayıcı adı — sözlük adayı (örnek).",
                        0, TelaffuzNotu.MOD_DICTIONARY, TelaffuzNotu.DURUM_PASIF)
        );
    }

    private static TelaffuzNotu jsonOku(JsonNode n) {
        String ifade = n.path("ifade").asText(n.path("kelime").asText(""));
        if (ifade.isBlank()) {
            return null;
        }
        String okunus = n.path("onerilenOkunus").asText(n.path("telaffuz").asText(""));
        return new TelaffuzNotu(
                n.path("id").asText("t-" + ifade.hashCode()),
                ifade,
                okunus,
                n.path("aciklama").asText(n.path("not").asText("")),
                n.path("eserId").asInt(0),
                n.path("dil").asText("tr"),
                n.path("kaynak").asText("DOSYA"),
                n.path("durum").asText(TelaffuzNotu.DURUM_AKTIF),
                n.path("uygulanmaModu").asText(TelaffuzNotu.MOD_NOT),
                n.path("createdAt").asText(OffsetDateTime.now().toString()),
                n.path("updatedAt").asText(OffsetDateTime.now().toString())
        );
    }
}
