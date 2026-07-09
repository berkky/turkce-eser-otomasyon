import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Web paneli için eser listesi ve detay okuma (salt okunur).
 */
public final class WebEserService {
    private static final Pattern ESER_KLASOR = Pattern.compile("^ESER-(\\d{5})\\s*-\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARCA = Pattern.compile("^\\d{3}-\\d{3}\\.txt$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebOrtam ortam;
    private final List<SesOnizlemeKaydi> onizlemeler;

    public WebEserService(WebOrtam ortam) throws Exception {
        this.ortam = ortam;
        this.onizlemeler = new SesOnizlemeKesifService(ortam.projeKlasoru())
                .kesfet(ortam.sesArsivi(), ortam.metinArsivi());
    }

    public List<WebEserOzeti> eserleriListele() throws Exception {
        Map<Integer, WebEserOzeti> harita = new LinkedHashMap<>();
        taraMetinArsivi(harita);
        for (int id : List.of(SesKaliteOlcutleri.KASAGI_ESER_ID, SesKaliteOlcutleri.ASTRONOMI_ESER_ID)) {
            harita.computeIfAbsent(id, this::varsayilanEser);
        }
        return harita.values().stream()
                .sorted(Comparator.comparingInt(WebEserOzeti::eserId))
                .toList();
    }

    public WebEserDetay eserDetay(int eserId) throws Exception {
        for (WebEserOzeti o : eserleriListele()) {
            if (o.eserId() == eserId) {
                return detayGenislet(o);
            }
        }
        if (eserId == SesKaliteOlcutleri.KASAGI_ESER_ID || eserId == SesKaliteOlcutleri.ASTRONOMI_ESER_ID) {
            return detayGenislet(varsayilanEser(eserId));
        }
        return null;
    }

    public String eserlerJson() throws Exception {
        ArrayNode dizi = JSON.createArrayNode();
        for (WebEserOzeti o : eserleriListele()) {
            dizi.add(o.json(JSON));
        }
        return WebGuvenlikService.guvenliJson(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(dizi));
    }

    public String eserJson(int eserId) throws Exception {
        WebEserDetay d = eserDetay(eserId);
        if (d == null) {
            return null;
        }
        return WebGuvenlikService.guvenliJson(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(d.json(JSON)));
    }

    private void taraMetinArsivi(Map<Integer, WebEserOzeti> harita) throws Exception {
        Path ana = ortam.metinArsivi();
        if (!Files.isDirectory(ana)) {
            return;
        }
        try (Stream<Path> s = Files.list(ana)) {
            for (Path klasor : s.filter(Files::isDirectory).sorted().toList()) {
                WebEserOzeti ozet = klasordenOku(klasor);
                if (ozet != null) {
                    harita.put(ozet.eserId(), ozet);
                }
            }
        }
    }

    private WebEserOzeti klasordenOku(Path klasor) throws Exception {
        Matcher m = ESER_KLASOR.matcher(klasor.getFileName().toString());
        if (!m.matches()) {
            return null;
        }
        int id = Integer.parseInt(m.group(1));
        String ad = m.group(2).trim();
        String yazar = "Bilinmiyor";
        String yayinevi = "Bilinmiyor";
        String isbn = "Bilinmiyor";
        String metadataDurumu = "BILINMIYOR";
        double guven = 0;
        int karakter = 0;
        int bolum = 0;

        Path manifest = klasor.resolve("alim-manifest.json");
        if (Files.isRegularFile(manifest)) {
            JsonNode n = JSON.readTree(Files.readString(manifest, StandardCharsets.UTF_8));
            ad = n.path("eserAdi").asText(ad);
            karakter = n.path("toplamKarakter").asInt(0);
            bolum = n.path("bolumSayisi").asInt(0);
            metadataDurumu = n.path("metadataDurumu").asText(metadataDurumu);
            JsonNode meta = n.path("metadata");
            yazar = meta.path("yazar").asText(yazar);
            yayinevi = meta.path("yayinevi").asText(yayinevi);
            isbn = meta.path("isbn").asText(isbn);
            guven = meta.path("guvenPuani").asDouble(0);
        }

        int parca = parcaSayisi(klasor);
        if (karakter == 0) {
            karakter = (int) Math.min(Integer.MAX_VALUE, toplamKarakter(klasor));
        }
        boolean onizleme = onizlemeler.stream().anyMatch(o -> o.eserId() == id
                && !SesKaliteOlcutleri.DURUM_BEKLENIYOR.equals(o.status()));
        boolean buyuk = id == SesKaliteOlcutleri.ASTRONOMI_ESER_ID
                || parca >= SesKaliteOlcutleri.BUYUK_ESER_PARCA_ESIGI;

        return new WebEserOzeti(id, ad, yazar, yayinevi, isbn, metadataDurumu, guven,
                karakter, bolum, parca, buyuk, onizleme, WebGuvenlikService.yolMaskele(klasor));
    }

    private WebEserDetay detayGenislet(WebEserOzeti ozet) throws Exception {
        Path metinKlasor = eserKlasoruBul(ortam.metinArsivi(), ozet.eserId());
        Path sesKlasor = eserKlasoruBul(ortam.sesArsivi(), ozet.eserId());
        Path arsivKlasor = eserKlasoruBul(ortam.arsiv(), ozet.eserId());
        String kanit = "";
        String kaynakDosya = "";
        Path metadataJson = null;
        Path kaynakPdf = null;

        if (metinKlasor != null) {
            Path manifest = metinKlasor.resolve("alim-manifest.json");
            if (Files.isRegularFile(manifest)) {
                JsonNode n = JSON.readTree(Files.readString(manifest, StandardCharsets.UTF_8));
                kanit = n.path("metadata").path("kanit").asText("");
                String arsivYol = n.path("arsivDosyasi").asText("");
                if (!arsivYol.isBlank()) {
                    Path p = Path.of(arsivYol);
                    kaynakDosya = WebGuvenlikService.yolMaskele(p);
                    if (Files.isRegularFile(p) && p.toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                        kaynakPdf = p;
                    }
                }
            }
            Path arsivMeta = metinKlasor.resolve("metadata").resolve("eser-metadata.json");
            if (!Files.isRegularFile(arsivMeta) && arsivKlasor != null) {
                arsivMeta = arsivKlasor.resolve("metadata").resolve("eser-metadata.json");
            }
            if (Files.isRegularFile(arsivMeta)) {
                metadataJson = arsivMeta;
            }
        }

        List<SesOnizlemeKaydi> eserOnizleme = onizlemeler.stream()
                .filter(o -> o.eserId() == ozet.eserId())
                .toList();

        String metinOzet = "";
        if (metinKlasor != null) {
            Path tam = metinKlasor.resolve("tam-metin.txt");
            if (Files.isRegularFile(tam)) {
                String ham = Files.readString(tam, StandardCharsets.UTF_8);
                metinOzet = ham.length() > 1500 ? ham.substring(0, 1500) + "…" : ham;
            }
        }

        return new WebEserDetay(ozet, kanit, kaynakDosya,
                WebGuvenlikService.yolMaskele(metinKlasor),
                WebGuvenlikService.yolMaskele(sesKlasor),
                eserOnizleme, metinOzet, metadataJson, kaynakPdf);
    }

    private WebEserOzeti varsayilanEser(int id) {
        String ad = id == 5 ? "Kaşağı - Vikikaynak" : id == 6 ? "Astronomi Alfa Yayınları" : "Bilinmeyen";
        boolean buyuk = id == SesKaliteOlcutleri.ASTRONOMI_ESER_ID;
        boolean onizleme = onizlemeler.stream().anyMatch(o -> o.eserId() == id
                && !SesKaliteOlcutleri.DURUM_BEKLENIYOR.equals(o.status()));
        return new WebEserOzeti(id, ad, "Bilinmiyor", "Bilinmiyor", "Bilinmiyor",
                "BILINMIYOR", 0, 0, 0, buyuk ? 237 : 0, buyuk, onizleme, "");
    }

    private static Path eserKlasoruBul(Path ana, int eserId) throws Exception {
        if (!Files.isDirectory(ana)) {
            return null;
        }
        String onEk = String.format(Locale.ROOT, "ESER-%05d", eserId).toLowerCase(Locale.ROOT);
        try (Stream<Path> s = Files.list(ana)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(onEk))
                    .findFirst().orElse(null);
        }
    }

    private static int parcaSayisi(Path metinKlasor) throws Exception {
        Path tts = metinKlasor.resolve("tts-parcalari");
        if (!Files.isDirectory(tts)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(tts)) {
            return (int) s.filter(Files::isRegularFile)
                    .filter(p -> PARCA.matcher(p.getFileName().toString()).matches()).count();
        }
    }

    private static long toplamKarakter(Path metinKlasor) throws Exception {
        Path tts = metinKlasor.resolve("tts-parcalari");
        if (!Files.isDirectory(tts)) {
            return 0;
        }
        long t = 0;
        try (Stream<Path> s = Files.list(tts)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                if (PARCA.matcher(p.getFileName().toString()).matches()) {
                    t += Files.size(p);
                }
            }
        }
        return t;
    }

    public record WebEserOzeti(int eserId, String eserAdi, String yazar, String yayinevi, String isbn,
                               String metadataDurumu, double guvenPuani, int karakter, int bolum, int ttsParca,
                               boolean buyukEser, boolean onizlemeVar, String klasorMaskeli) {
        ObjectNode json(ObjectMapper mapper) {
            ObjectNode n = mapper.createObjectNode();
            n.put("eserId", eserId);
            n.put("eserAdi", eserAdi);
            n.put("yazar", yazar);
            n.put("yayinevi", yayinevi);
            n.put("isbn", isbn);
            n.put("metadataDurumu", metadataDurumu);
            n.put("guvenPuani", guvenPuani);
            n.put("karakter", karakter);
            n.put("bolum", bolum);
            n.put("ttsParca", ttsParca);
            n.put("buyukEser", buyukEser);
            n.put("onizlemeVar", onizlemeVar);
            n.put("klasorMaskeli", klasorMaskeli);
            return n;
        }
    }

    public record WebEserDetay(WebEserOzeti ozet, String kanit, String kaynakDosya, String metinKlasor,
                               String sesKlasor, List<SesOnizlemeKaydi> onizlemeler, String metinOzet,
                               Path metadataJson, Path kaynakPdf) {
        ObjectNode json(ObjectMapper mapper) {
            ObjectNode n = ozet.json(mapper);
            n.put("kanit", kanit);
            n.put("kaynakDosya", kaynakDosya);
            n.put("metinKlasor", metinKlasor);
            n.put("sesKlasor", sesKlasor);
            n.put("metinOzet", metinOzet);
            ArrayNode oniz = n.putArray("onizlemeler");
            for (SesOnizlemeKaydi k : onizlemeler) {
                oniz.add(k.jsonOzeti(mapper));
            }
            return n;
        }
    }
}
