import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * ses-arsivi altındaki önizleme klasörlerini keşfeder. Var olan dosyalara yazmaz.
 */
public final class SesOnizlemeKesifService {
    private static final Pattern ESER_KLASOR = Pattern.compile("^ESER-(\\d{5})\\s*-\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARCA_ADI = Pattern.compile("^\\d{3}-\\d{3}\\.txt$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final FfmpegClient ffmpeg;

    public SesOnizlemeKesifService(Path projeKlasoru) {
        this.ffmpeg = new FfmpegClient(projeKlasoru);
    }

    public List<SesOnizlemeKaydi> kesfet(Path sesArsivKlasoru, Path metinArsivKlasoru) throws Exception {
        Map<Integer, EserBilgi> eserler = eserHaritasiniOlustur(sesArsivKlasoru, metinArsivKlasoru);
        List<SesOnizlemeKaydi> kayitlar = new ArrayList<>();

        for (EserBilgi eser : eserler.values()) {
            Path sesEserKlasoru = eser.sesKlasoru();
            if (sesEserKlasoru != null && Files.isDirectory(sesEserKlasoru)) {
                kesfetOnizlemeKlasorleri(eser, sesEserKlasoru, kayitlar);
            }
            if (kayitSayisi(eser.id(), kayitlar) == 0) {
                kayitlar.add(bekleniyorKaydi(eser));
            }
        }

        kayitlar.sort(Comparator
                .comparingInt(SesOnizlemeKaydi::eserId)
                .thenComparing(SesOnizlemeKaydi::provider, String.CASE_INSENSITIVE_ORDER));
        return kayitlar;
    }

    private void kesfetOnizlemeKlasorleri(EserBilgi eser, Path sesEserKlasoru, List<SesOnizlemeKaydi> kayitlar)
            throws Exception {
        Path onizlemeKok = sesEserKlasoru.resolve("onizleme");
        if (!Files.isDirectory(onizlemeKok)) {
            return;
        }
        try (Stream<Path> stream = Files.list(onizlemeKok)) {
            for (Path saglayiciKlasoru : stream.filter(Files::isDirectory).sorted().toList()) {
                String provider = saglayiciKlasoru.getFileName().toString().toLowerCase(Locale.ROOT);
                kesfetSaglayiciKlasoru(eser, provider, saglayiciKlasoru, kayitlar);
            }
        }
        kesfetOrneklerKlasoru(eser, sesEserKlasoru.resolve("ornekler"), kayitlar);
    }

    private void kesfetSaglayiciKlasoru(EserBilgi eser,
                                       String provider,
                                       Path klasor,
                                       List<SesOnizlemeKaydi> kayitlar) throws Exception {
        Path json = oncelikliJson(klasor, provider);
        Path mp3 = oncelikliMp3(klasor, provider);
        Path input = klasor.resolve("preview-input.txt");
        if (!Files.isRegularFile(input)) {
            input = ilkDosya(klasor, "preview-input", ".txt");
        }

        if (json != null || mp3 != null) {
            kayitlar.add(kayitOlustur(eser, provider, klasor, json, mp3, input));
            return;
        }

        try (Stream<Path> stream = Files.list(klasor)) {
            for (Path dosya : stream.filter(Files::isRegularFile).sorted().toList()) {
                String ad = dosya.getFileName().toString().toLowerCase(Locale.ROOT);
                if (ad.endsWith(".mp3")) {
                    Path yanJson = dosya.resolveSibling(uzantisiz(dosya.getFileName().toString()) + ".json");
                    kayitlar.add(kayitOlustur(eser, provider, klasor,
                            Files.isRegularFile(yanJson) ? yanJson : null, dosya, input));
                }
            }
        }
    }

    private void kesfetOrneklerKlasoru(EserBilgi eser, Path ornekler, List<SesOnizlemeKaydi> kayitlar)
            throws Exception {
        if (!Files.isDirectory(ornekler)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(ornekler, 2)) {
            for (Path mp3 : stream.filter(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3")).toList()) {
                String ad = mp3.getFileName().toString().toLowerCase(Locale.ROOT);
                String provider = ad.contains("elevenlabs") ? "elevenlabs"
                        : ad.contains("piper") ? "piper"
                        : ad.contains("google") ? "google" : "ornek";
                Path json = mp3.resolveSibling(uzantisiz(mp3.getFileName().toString()) + ".json");
                kayitlar.add(kayitOlustur(eser, provider, mp3.getParent(),
                        Files.isRegularFile(json) ? json : null, mp3, null));
            }
        }
    }

    private SesOnizlemeKaydi kayitOlustur(EserBilgi eser,
                                          String provider,
                                          Path klasor,
                                          Path json,
                                          Path mp3,
                                          Path input) throws Exception {
        JsonNode meta = json != null && Files.isRegularFile(json)
                ? JSON.readTree(Files.readString(json, StandardCharsets.UTF_8)) : null;

        boolean mock = meta != null && meta.path("mockMode").asBoolean(false);
        if (!mock && meta != null && meta.path("mock").asBoolean(false)) {
            mock = true;
        }
        if (!mock && mp3 != null) {
            String ad = mp3.getFileName().toString().toLowerCase(Locale.ROOT);
            mock = ad.contains("mock") || (meta != null && "mp3_mock_fixture".equals(meta.path("format").asText("")));
        }

        long boyut = mp3 != null && Files.isRegularFile(mp3) ? Files.size(mp3) : 0L;
        String status = durumBelirle(mock, mp3, boyut, meta);

        String previewMetin = "";
        if (input != null && Files.isRegularFile(input)) {
            previewMetin = Files.readString(input, StandardCharsets.UTF_8).trim();
        } else if (meta != null) {
            previewMetin = meta.path("ornekMetin").asText(meta.path("previewMetin").asText("")).trim();
        }

        int karakter = meta != null && meta.has("inputCharacterCount")
                ? meta.path("inputCharacterCount").asInt(previewMetin.length())
                : previewMetin.length();

        Double sure = null;
        if (mp3 != null && Files.isRegularFile(mp3) && boyut >= SesKaliteOlcutleri.EN_AZ_GECERLI_MP3_BOYUTU) {
            try {
                if (ffmpeg.kontrolEt().hazir()) {
                    sure = ffmpeg.sureSaniye(mp3);
                }
            } catch (Exception ignored) {
            }
        }

        String modelId = meta != null ? meta.path("modelId").asText(meta.path("model").asText("")) : "";
        String voiceMasked = meta != null
                ? meta.path("voiceIdMasked").asText(ElevenLabsClient.voiceIdMaskele(meta.path("voiceId").asText("")))
                : "";
        String voiceName = meta != null
                ? meta.path("voiceName").asText(meta.path("ses").asText(""))
                : "";
        String requestHash = meta != null ? meta.path("requestHash").asText("") : "";
        String generatedAt = meta != null
                ? meta.path("generatedAt").asText(meta.path("olusturulmaZamani").asText(""))
                : "";
        String error = meta != null ? meta.path("errorMessage").asText("") : "";
        if (SesKaliteOlcutleri.DURUM_GECERSIZ.equals(status) && error.isBlank()) {
            error = "Geçersiz veya boş ses dosyası";
        }

        return new SesOnizlemeKaydi(
                eser.id(),
                eser.adi(),
                provider.toUpperCase(Locale.ROOT),
                modelId,
                voiceMasked,
                voiceName,
                input != null ? input.toAbsolutePath().toString() : "",
                mp3 != null ? mp3.toAbsolutePath().toString() : "",
                json != null ? json.toAbsolutePath().toString() : "",
                karakter,
                sure,
                boyut,
                generatedAt,
                requestHash,
                mock,
                status,
                error,
                eser.tahminiTamEserKarakter(),
                SesKaliteOlcutleri.krediRiski(eser.tahminiTamEserKarakter(), eser.parcaSayisi(), eser.id()),
                eser.parcaSayisi(),
                eser.buyukEser(),
                kisalt(previewMetin, 2_000),
                mock ? "MOCK — gerçek TTS değildir; test amaçlıdır" : ""
        );
    }

    private static SesOnizlemeKaydi bekleniyorKaydi(EserBilgi eser) {
        String mesaj = eser.buyukEser()
                ? "Büyük eser — önizleme önerilir; tam üretim için kredi onayı gerekir"
                : "Önizleme bekleniyor";
        return new SesOnizlemeKaydi(
                eser.id(), eser.adi(), "—", "", "", "",
                "", "", "",
                0, null, 0L, "", "", false,
                SesKaliteOlcutleri.DURUM_BEKLENIYOR,
                mesaj,
                eser.tahminiTamEserKarakter(),
                SesKaliteOlcutleri.krediRiski(eser.tahminiTamEserKarakter(), eser.parcaSayisi(), eser.id()),
                eser.parcaSayisi(),
                eser.buyukEser(),
                "",
                ""
        );
    }

    private static String durumBelirle(boolean mock, Path mp3, long boyut, JsonNode meta) {
        if (mp3 == null || !Files.isRegularFile(mp3) || boyut == 0) {
            return SesKaliteOlcutleri.DURUM_GECERSIZ;
        }
        if (boyut < SesKaliteOlcutleri.EN_AZ_GECERLI_MP3_BOYUTU) {
            return SesKaliteOlcutleri.DURUM_GECERSIZ;
        }
        if (mock) {
            return SesKaliteOlcutleri.DURUM_MOCK;
        }
        if (meta != null) {
            String s = meta.path("status").asText("").toUpperCase(Locale.ROOT);
            if (s.contains("BASARISIZ") || s.contains("HATA")) {
                return SesKaliteOlcutleri.DURUM_GECERSIZ;
            }
        }
        return SesKaliteOlcutleri.DURUM_GECERLI;
    }

    private Map<Integer, EserBilgi> eserHaritasiniOlustur(Path sesArsiv, Path metinArsiv) throws Exception {
        Map<Integer, EserBilgi> harita = new LinkedHashMap<>();
        taraKlasor(sesArsiv, harita, true);
        taraKlasor(metinArsiv, harita, false);

        for (int zorunlu : List.of(SesKaliteOlcutleri.KASAGI_ESER_ID, SesKaliteOlcutleri.ASTRONOMI_ESER_ID)) {
            harita.computeIfAbsent(zorunlu, id -> new EserBilgi(id, varsayilanEserAdi(id), null, null, 0, 0L));
        }
        return harita;
    }

    private void taraKlasor(Path ana, Map<Integer, EserBilgi> harita, boolean sesKlasoru) throws Exception {
        if (!Files.isDirectory(ana)) {
            return;
        }
        try (Stream<Path> stream = Files.list(ana)) {
            for (Path klasor : stream.filter(Files::isDirectory).toList()) {
                Matcher m = ESER_KLASOR.matcher(klasor.getFileName().toString());
                if (!m.matches()) {
                    continue;
                }
                int id = Integer.parseInt(m.group(1));
                String ad = m.group(2).trim();
                EserBilgi mevcut = harita.get(id);
                Path ses = sesKlasoru ? klasor : mevcut == null ? null : mevcut.sesKlasoru();
                Path metin = sesKlasoru ? (mevcut == null ? null : mevcut.metinKlasoru()) : klasor;
                if (!sesKlasoru && mevcut != null && mevcut.sesKlasoru() != null) {
                    ses = mevcut.sesKlasoru();
                }
                int parca = parcaSayisiOku(metin);
                long karakter = toplamKarakterOku(metin);
                harita.put(id, new EserBilgi(id, ad, ses, metin, parca, karakter));
            }
        }
    }

    private static int parcaSayisiOku(Path metinEserKlasoru) throws Exception {
        if (metinEserKlasoru == null) {
            return 0;
        }
        Path tts = metinEserKlasoru.resolve("tts-parcalari");
        if (!Files.isDirectory(tts)) {
            return metinEserKlasoru.getFileName().toString().toLowerCase(Locale.ROOT).contains("astronomi")
                    ? 237 : 0;
        }
        try (Stream<Path> s = Files.list(tts)) {
            return (int) s.filter(Files::isRegularFile)
                    .filter(p -> PARCA_ADI.matcher(p.getFileName().toString()).matches())
                    .count();
        }
    }

    private static long toplamKarakterOku(Path metinEserKlasoru) throws Exception {
        if (metinEserKlasoru == null) {
            return 0L;
        }
        Path manifest = metinEserKlasoru.resolve("tts-parcalari").resolve("tts-manifest.json");
        if (Files.isRegularFile(manifest)) {
            JsonNode n = JSON.readTree(Files.readString(manifest, StandardCharsets.UTF_8));
            if (n.has("toplamKarakter")) {
                return n.path("toplamKarakter").asLong(0L);
            }
        }
        Path tts = metinEserKlasoru.resolve("tts-parcalari");
        if (!Files.isDirectory(tts)) {
            return 0L;
        }
        long toplam = 0L;
        try (Stream<Path> s = Files.list(tts)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                if (PARCA_ADI.matcher(p.getFileName().toString()).matches()) {
                    toplam += Files.readString(p, StandardCharsets.UTF_8).length();
                }
            }
        }
        return toplam;
    }

    private static int kayitSayisi(int eserId, List<SesOnizlemeKaydi> kayitlar) {
        int sayi = 0;
        for (SesOnizlemeKaydi k : kayitlar) {
            if (k.eserId() == eserId && !SesKaliteOlcutleri.DURUM_BEKLENIYOR.equals(k.status())) {
                sayi++;
            }
        }
        return sayi;
    }

    private static Path oncelikliJson(Path klasor, String provider) {
        Path[] adaylar = {
                klasor.resolve("preview-" + provider + ".json"),
                klasor.resolve("preview-elevenlabs.json"),
                klasor.resolve("preview-piper.json"),
                klasor.resolve("preview-google.json")
        };
        for (Path p : adaylar) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    private static Path oncelikliMp3(Path klasor, String provider) {
        Path[] adaylar = {
                klasor.resolve("preview-" + provider + ".mp3"),
                klasor.resolve("preview-elevenlabs.mp3"),
                klasor.resolve("preview-piper.mp3"),
                klasor.resolve("preview-google.mp3")
        };
        for (Path p : adaylar) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    private static Path ilkDosya(Path klasor, String onEk, String uzanti) throws Exception {
        try (Stream<Path> s = Files.list(klasor)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(onEk))
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(uzanti))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static String uzantisiz(String ad) {
        int n = ad.lastIndexOf('.');
        return n > 0 ? ad.substring(0, n) : ad;
    }

    private static String kisalt(String metin, int limit) {
        if (metin == null || metin.length() <= limit) {
            return metin == null ? "" : metin;
        }
        return metin.substring(0, limit) + "...";
    }

    private static String varsayilanEserAdi(int id) {
        return switch (id) {
            case 5 -> "Kaşağı - Vikikaynak";
            case 6 -> "Astronomi Alfa Yayınları";
            default -> "Bilinmeyen eser";
        };
    }

    private record EserBilgi(int id, String adi, Path sesKlasoru, Path metinKlasoru, int parcaSayisi, long karakter) {
        boolean buyukEser() {
            return id == SesKaliteOlcutleri.ASTRONOMI_ESER_ID
                    || parcaSayisi >= SesKaliteOlcutleri.BUYUK_ESER_PARCA_ESIGI;
        }

        long tahminiTamEserKarakter() {
            return karakter > 0 ? karakter : (buyukEser() ? 800_000L : 50_000L);
        }
    }
}
