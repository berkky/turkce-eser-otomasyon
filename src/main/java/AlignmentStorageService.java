import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Alignment dosya okuma/yazma — ses-arsivi/_alignment/
 */
public final class AlignmentStorageService {
    private static final ObjectMapper JSON = new ObjectMapper();
    static final int EN_AZ_MP3 = 128;

    private final Path sesArsivi;

    public AlignmentStorageService(Path sesArsivi) {
        this.sesArsivi = sesArsivi;
    }

    public Path alignmentKlasoru() {
        return sesArsivi.resolve("_alignment");
    }

    public Path alignmentJson(int eserId) {
        return alignmentKlasoru().resolve(AlignmentGuvenlikService.guvenliDosyaAdi(eserId, "alignment.json"));
    }

    public Path summaryJson(int eserId) {
        return alignmentKlasoru().resolve(AlignmentGuvenlikService.guvenliDosyaAdi(eserId, "alignment.summary.json"));
    }

    public Path srtDosya(int eserId) {
        return alignmentKlasoru().resolve(AlignmentGuvenlikService.guvenliDosyaAdi(eserId, "subtitles.srt"));
    }

    public Path vttDosya(int eserId) {
        return alignmentKlasoru().resolve(AlignmentGuvenlikService.guvenliDosyaAdi(eserId, "subtitles.vtt"));
    }

    public PreviewKaynaklari previewKaynaklari(int eserId) throws Exception {
        Path eser = eserKlasoruBul(eserId);
        if (eser == null) {
            return null;
        }
        Path oniz = eser.resolve("onizleme").resolve("elevenlabs");
        Path mp3 = oniz.resolve("preview-elevenlabs.mp3");
        Path metin = oniz.resolve("preview-input.txt");
        Path meta = oniz.resolve("preview-elevenlabs.json");
        if (!Files.isRegularFile(mp3) || !Files.isRegularFile(metin)) {
            return null;
        }
        if (Files.size(mp3) < EN_AZ_MP3) {
            throw new IllegalStateException("Geçersiz veya boş önizleme MP3.");
        }
        String previewMetin = Files.readString(metin, StandardCharsets.UTF_8).trim();
        if (previewMetin.isBlank()) {
            throw new IllegalStateException("Önizleme metni yok.");
        }
        String previewId = "preview-elevenlabs";
        String audioSafeId = "e" + eserId + "-elevenlabs-preview";
        return new PreviewKaynaklari(eserId, previewId, mp3, metin, meta, previewMetin, audioSafeId);
    }

    public String metinHash(String metin) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(d.digest(metin.getBytes(StandardCharsets.UTF_8)));
    }

    public String audioHash(Path mp3) throws Exception {
        byte[] ilk = Files.readAllBytes(mp3);
        int len = Math.min(ilk.length, 4096);
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        d.update(ilk, 0, len);
        d.update(String.valueOf(Files.size(mp3)).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(d.digest());
    }

    public boolean mevcutKullanilabilir(int eserId, String textHash, String audioHash) throws Exception {
        Path json = alignmentJson(eserId);
        if (!Files.isRegularFile(json) || Files.size(json) < 32) {
            return false;
        }
        JsonNode kok = JSON.readTree(Files.readString(json, StandardCharsets.UTF_8));
        if (!AlignmentPlan.STATUS_COMPLETED.equalsIgnoreCase(kok.path("status").asText(""))) {
            return false;
        }
        return textHash.equals(kok.path("textHash").asText(""))
                && audioHash.equals(kok.path("audioHash").asText(""));
    }

    public AlignmentResult yukle(int eserId) throws Exception {
        Path json = alignmentJson(eserId);
        if (!Files.isRegularFile(json)) {
            return AlignmentResult.notRequested();
        }
        JsonNode kok = JSON.readTree(Files.readString(json, StandardCharsets.UTF_8));
        List<AlignmentSegment> segmentler = new ArrayList<>();
        for (JsonNode s : kok.path("segments")) {
            List<AlignmentWord> kelimeler = new ArrayList<>();
            for (JsonNode w : s.path("words")) {
                kelimeler.add(new AlignmentWord(
                        w.path("index").asInt(),
                        w.path("text").asText(""),
                        w.path("startSeconds").asDouble(),
                        w.path("endSeconds").asDouble()));
            }
            segmentler.add(new AlignmentSegment(
                    s.path("index").asInt(),
                    s.path("text").asText(""),
                    s.path("startSeconds").asDouble(),
                    s.path("endSeconds").asDouble(),
                    kelimeler));
        }
        List<String> uyarilar = new ArrayList<>();
        kok.path("warnings").forEach(n -> uyarilar.add(n.asText()));
        return new AlignmentResult(
                kok.path("eserId").asInt(eserId),
                kok.path("previewId").asText(""),
                kok.path("status").asText(""),
                kok.path("provider").asText(""),
                kok.path("language").asText("tr"),
                kok.path("textHash").asText(""),
                kok.path("audioHash").asText(""),
                kok.path("durationSeconds").asDouble(),
                kok.path("wordCount").asInt(),
                kok.path("segmentCount").asInt(),
                kok.path("characterAlignmentAvailable").asBoolean(),
                kok.path("wordAlignmentAvailable").asBoolean(),
                segmentler, uyarilar,
                kok.path("source").asText(""),
                kok.path("alignmentStatus").asText(""),
                kok.path("outputPathSafeName").asText(""),
                kok.path("srtSafeName").asText(""),
                kok.path("vttSafeName").asText(""),
                kok.path("demoFixture").asBoolean(false),
                kok.path("createdAt").asText(""));
    }

    public void kaydet(AlignmentResult sonuc, String srt, String vtt) throws Exception {
        Path klasor = alignmentKlasoru();
        Files.createDirectories(klasor);
        Path json = alignmentJson(sonuc.eserId());
        Path partial = klasor.resolve(json.getFileName().toString() + ".partial");
        Path summary = summaryJson(sonuc.eserId());
        Path srtPath = srtDosya(sonuc.eserId());
        Path vttPath = vttDosya(sonuc.eserId());

        String icerik = JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsString(sonuc.json(JSON));
        Files.writeString(partial, icerik, StandardCharsets.UTF_8);
        Files.move(partial, json, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        ObjectMapper mapper = JSON;
        var ozet = mapper.createObjectNode();
        ozet.put("eserId", sonuc.eserId());
        ozet.put("status", sonuc.status());
        ozet.put("segmentCount", sonuc.segmentCount());
        ozet.put("wordCount", sonuc.wordCount());
        ozet.put("durationSeconds", sonuc.durationSeconds());
        ozet.put("source", sonuc.source());
        ozet.put("demoFixture", sonuc.demoFixture());
        Files.writeString(summary, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ozet),
                StandardCharsets.UTF_8);

        if (srt != null && !srt.isBlank()) {
            Files.writeString(srtPath, srt, StandardCharsets.UTF_8);
        }
        if (vtt != null && !vtt.isBlank()) {
            Files.writeString(vttPath, vtt, StandardCharsets.UTF_8);
        }

        AlignmentGuvenlikService.dosyaGuvenligi(sesArsivi, json);
        onizlemeJsonGuncelle(sonuc);
    }

    private void onizlemeJsonGuncelle(AlignmentResult sonuc) throws Exception {
        PreviewKaynaklari k = previewKaynaklari(sonuc.eserId());
        if (k == null || !Files.isRegularFile(k.metaJson())) {
            return;
        }
        JsonNode kok = JSON.readTree(Files.readString(k.metaJson(), StandardCharsets.UTF_8));
        if (!(kok instanceof com.fasterxml.jackson.databind.node.ObjectNode node)) {
            return;
        }
        node.put("alignmentStatus", sonuc.alignmentStatus());
        node.put("alignmentOutputPath", sonuc.outputPathSafeName());
        Files.writeString(k.metaJson(),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node),
                StandardCharsets.UTF_8);
    }

    private Path eserKlasoruBul(int eserId) throws Exception {
        String onEk = String.format(Locale.ROOT, "ESER-%05d", eserId).toLowerCase(Locale.ROOT);
        if (!Files.isDirectory(sesArsivi)) {
            return null;
        }
        try (var stream = Files.list(sesArsivi)) {
            return stream.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(onEk))
                    .findFirst().orElse(null);
        }
    }

    public record PreviewKaynaklari(
            int eserId, String previewId, Path mp3, Path inputTxt, Path metaJson,
            String metin, String audioSafeId
    ) {
    }
}
