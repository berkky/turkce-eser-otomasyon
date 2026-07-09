import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * ElevenLabs için 60–90 saniyelik güvenli önizleme üretimi.
 * Aynı requestHash ile mevcut önizleme varsa API çağrısı yapılmaz.
 */
public final class ElevenLabsOnizlemeService {
    private static final int MIN_KARAKTER = 450;
    private static final int MAKS_KARAKTER = 1_100;
    private static final int EN_AZ_MP3_BOYUTU = 128;
    private static final Pattern PARCA_ADI = Pattern.compile("^(\\d{3})-(\\d{3})\\.txt$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private ElevenLabsOnizlemeService() {
    }

    public static OnizlemeSonucu uret(int eserId,
                                      Path metinArsivKlasoru,
                                      Path sesArsivKlasoru,
                                      ElevenLabsIstemci istemciOverride) {
        return uret(eserId, metinArsivKlasoru, sesArsivKlasoru, null, istemciOverride, null);
    }

    public static OnizlemeSonucu uret(int eserId,
                                      Path metinArsivKlasoru,
                                      Path sesArsivKlasoru,
                                      ElevenLabsIstemci istemciOverride,
                                      String voiceIdOverride) {
        return uret(eserId, metinArsivKlasoru, sesArsivKlasoru, null, istemciOverride, voiceIdOverride);
    }

    public static OnizlemeSonucu uret(int eserId,
                                      Path metinArsivKlasoru,
                                      Path sesArsivKlasoru,
                                      Path kalitePanelKlasoru,
                                      ElevenLabsIstemci istemciOverride,
                                      String voiceIdOverride) {
        Path logDosyasi = null;
        try {
            if (eserId <= 0) {
                throw new IllegalArgumentException("Geçerli bir eser ID gerekli.");
            }
            if (eserId == SesKaliteOlcutleri.ASTRONOMI_ESER_ID) {
                throw new IllegalStateException(
                        "ESER-00006 büyük eser — gerçek önizleme/üretim bu adımda engelli. Önce maliyet planı ve onay gerekir.");
            }

            Path kalitePanel = kalitePanelKlasoru != null ? kalitePanelKlasoru
                    : Path.of(System.getProperty("user.home"), "Desktop", "ses-arsivi_kalite-panel");
            String panelEnv = System.getenv("SES_KALITE_PANEL");
            if (panelEnv != null && !panelEnv.isBlank()) {
                kalitePanel = Path.of(panelEnv.trim());
            }
            List<TelaffuzNotu> telaffuzNotlari = new TelaffuzSozluguService(kalitePanel).yukle();
            String telaffuzRevizyon = TelaffuzNormalizerService.revisionHash(telaffuzNotlari);

            Path metinEserKlasoru = eserKlasoruBul(metinArsivKlasoru, eserId);
            if (metinEserKlasoru == null) {
                throw new IllegalStateException(String.format(Locale.ROOT,
                        "ESER-%05d metin arşivinde bulunamadı.", eserId));
            }

            Path ttsKlasoru = metinEserKlasoru.resolve("tts-parcalari");
            List<Path> parcalar = metinParcalariniListele(ttsKlasoru);
            if (parcalar.isEmpty()) {
                throw new IllegalStateException("TTS parçası bulunamadı.");
            }

            String eserAdi = eserAdiCikar(metinEserKlasoru.getFileName().toString(), eserId);
            Path onizlemeKlasoru = sesArsivKlasoru
                    .resolve(metinEserKlasoru.getFileName().toString())
                    .resolve("onizleme")
                    .resolve("elevenlabs");
            Files.createDirectories(onizlemeKlasoru);

            Path mp3 = onizlemeKlasoru.resolve("preview-elevenlabs.mp3");
            Path json = onizlemeKlasoru.resolve("preview-elevenlabs.json");
            Path inputTxt = onizlemeKlasoru.resolve("preview-input.txt");
            logDosyasi = onizlemeKlasoru.resolve("preview-run.log");

            String modelId = ElevenLabsModelPolitikasi.ortamModeliVeyaVarsayilan();
            String voiceId = voiceIdOverride != null && !voiceIdOverride.isBlank()
                    ? voiceIdOverride.trim()
                    : TtsLaboratuvarYardimci.ortam("ELEVENLABS_VOICE_ID", "");
            String voiceName = TtsLaboratuvarYardimci.ortam("ELEVENLABS_VOICE_NAME", "Seçili ElevenLabs sesi");

            logYaz(logDosyasi, "Önizleme başlatıldı | ESER-" + String.format(Locale.ROOT, "%05d", eserId));
            logYaz(logDosyasi, ElevenLabsModelPolitikasi.politikaOzeti());

            if (istemciOverride == null
                    && !ElevenLabsFabrika.mockModAktif()
                    && !TtsLaboratuvarYardimci.ortamVar("ELEVENLABS_API_KEY")) {
                return sonucYaz(json, logDosyasi, basarisizJson(eserId, eserAdi, modelId, voiceId,
                        null, 0, mp3, List.of(), "ELEVENLABS_API_KEY yok", telaffuzRevizyon));
            }
            if (voiceId.isBlank()) {
                return sonucYaz(json, logDosyasi, basarisizJson(eserId, eserAdi, modelId, voiceId,
                        null, 0, mp3, List.of(), "ELEVENLABS_VOICE_ID yok", telaffuzRevizyon));
            }

            OnizlemeMetni metin = onizlemeMetniHazirla(parcalar, telaffuzNotlari);
            Files.writeString(inputTxt, metin.metin(), StandardCharsets.UTF_8);

            long tahminiKredi = tahminiKredi(metin.metin().length(), modelId);
            String requestHash = requestHash(metin.metin(), modelId, voiceId, telaffuzRevizyon);

            if (mevcutOnizlemeKullanilabilir(json, mp3, requestHash)) {
                logYaz(logDosyasi, "Mevcut önizleme kullanıldı | requestHash=" + requestHash);
                ObjectNode mevcut = (ObjectNode) JSON.readTree(Files.readString(json, StandardCharsets.UTF_8));
                mevcut.put("status", "MEVCUT_KULLANILDI");
                mevcut.put("generatedAt", OffsetDateTime.now().toString());
                Files.writeString(json, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(mevcut),
                        StandardCharsets.UTF_8);
                return OnizlemeSonucu.basarili(mp3, json, true, "Mevcut önizleme kullanıldı");
            }

            ElevenLabsIstemci client = istemciOverride != null ? istemciOverride : ElevenLabsFabrika.olustur();
            ElevenLabsClient.AbonelikBilgisi abonelik = client.abonelikBilgisiniGetir();
            long kalan = abonelik.kalanPlanKredisi();
            if (!abonelik.aktifMi() || kalan <= 0L) {
                String mesaj = "ElevenLabs kredisi yok: "
                        + abonelik.kullanilanKredi() + "/" + abonelik.donemKrediLimiti() + " kullanıldı";
                logYaz(logDosyasi, "Üretim yapılmadı | " + mesaj);
                return sonucYaz(json, logDosyasi, basarisizJson(eserId, eserAdi, modelId, voiceId,
                        metin, tahminiKredi, mp3, metin.kaynakChunkIds(), mesaj, telaffuzRevizyon));
            }
            if (kalan < tahminiKredi) {
                String mesaj = "Önizleme için yeterli kredi yok. Gerekli: " + tahminiKredi + " | Kalan: " + kalan;
                logYaz(logDosyasi, mesaj);
                return sonucYaz(json, logDosyasi, basarisizJson(eserId, eserAdi, modelId, voiceId,
                        metin, tahminiKredi, mp3, metin.kaynakChunkIds(), mesaj, telaffuzRevizyon));
            }

            if (!client.sesIdDogrula(voiceId)) {
                String mesaj = "ELEVENLABS_VOICE_ID doğrulanamadı";
                logYaz(logDosyasi, mesaj);
                return sonucYaz(json, logDosyasi, basarisizJson(eserId, eserAdi, modelId, voiceId,
                        metin, tahminiKredi, mp3, metin.kaynakChunkIds(), mesaj, telaffuzRevizyon));
            }

            ElevenLabsClient.Ses ses = new ElevenLabsClient.Ses(
                    voiceId, voiceName, "onizleme", "", "tr", "", true
            );

            logYaz(logDosyasi, "API TTS çağrısı başlıyor | karakter=" + metin.metin().length());
            ElevenLabsClient.SesUretimSonucu uretim = client.sesUret(metin.metin(), ses, mp3);
            if (!Files.isRegularFile(mp3) || Files.size(mp3) < EN_AZ_MP3_BOYUTU) {
                Files.deleteIfExists(mp3);
                throw new IllegalStateException("Geçersiz veya boş önizleme MP3 üretildi.");
            }

            ObjectNode sonuc = basariliJson(eserId, eserAdi, modelId, voiceId,
                    metin, tahminiKredi, mp3, metin.kaynakChunkIds(), requestHash, uretim, telaffuzRevizyon);
            Files.writeString(json, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(sonuc),
                    StandardCharsets.UTF_8);
            logYaz(logDosyasi, "Önizleme başarılı | " + mp3.toAbsolutePath());
            return OnizlemeSonucu.basarili(mp3, json, false, "Önizleme üretildi");

        } catch (Exception e) {
            if (logDosyasi != null) {
                logYaz(logDosyasi, "HATA: " + e.getMessage());
            }
            return OnizlemeSonucu.hatali(e.getMessage());
        }
    }

    private static OnizlemeSonucu sonucYaz(Path json, Path log, ObjectNode icerik) throws Exception {
        Files.createDirectories(json.getParent());
        Files.writeString(json, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(icerik),
                StandardCharsets.UTF_8);
        logYaz(log, "Durum: " + icerik.path("status").asText(""));
        return OnizlemeSonucu.hatali(icerik.path("errorMessage").asText("Bilinmeyen hata"));
    }

    private static ObjectNode basariliJson(int eserId,
                                           String eserAdi,
                                           String modelId,
                                           String voiceId,
                                           OnizlemeMetni metin,
                                           long tahminiKredi,
                                           Path mp3,
                                           List<String> chunkIds,
                                           String requestHash,
                                           ElevenLabsClient.SesUretimSonucu uretim,
                                           String telaffuzRevizyon) {
        ObjectNode json = ortakJson(eserId, eserAdi, modelId, voiceId, metin, tahminiKredi, mp3, chunkIds,
                requestHash, telaffuzRevizyon);
        json.put("status", "BASARILI");
        json.put("provider", "ELEVENLABS");
        json.put("outputFile", mp3.toAbsolutePath().toString());
        json.put("audioBytes", uretim.bayt());
        json.put("mockMode", ElevenLabsFabrika.mockModAktif());
        return json;
    }

    private static ObjectNode basarisizJson(int eserId,
                                            String eserAdi,
                                            String modelId,
                                            String voiceId,
                                            OnizlemeMetni metin,
                                            long tahminiKredi,
                                            Path mp3,
                                            List<String> chunkIds,
                                            String hata,
                                            String telaffuzRevizyon) throws Exception {
        OnizlemeMetni guvenli = metin != null ? metin
                : new OnizlemeMetni("", 0, 0, chunkIds != null ? chunkIds : List.of(), List.of(), List.of());
        String hash = requestHash(guvenli.metin(), modelId, voiceId, telaffuzRevizyon != null ? telaffuzRevizyon : "");
        ObjectNode json = ortakJson(eserId, eserAdi, modelId, voiceId, guvenli, tahminiKredi, mp3,
                guvenli.kaynakChunkIds(), hash, telaffuzRevizyon != null ? telaffuzRevizyon : "");
        json.put("status", "BASARISIZ");
        json.put("provider", "ELEVENLABS");
        json.put("outputFile", mp3.toAbsolutePath().toString());
        json.put("errorMessage", hata);
        return json;
    }

    private static ObjectNode ortakJson(int eserId,
                                        String eserAdi,
                                        String modelId,
                                        String voiceId,
                                        OnizlemeMetni metin,
                                        long tahminiKredi,
                                        Path mp3,
                                        List<String> chunkIds,
                                        String requestHash,
                                        String telaffuzRevizyon) {
        ObjectNode json = JSON.createObjectNode();
        json.put("eserId", eserId);
        json.put("eserAdi", eserAdi);
        json.put("provider", "ELEVENLABS");
        json.put("previewMode", "KISA_ONIZLEME");
        json.put("modelId", modelId);
        json.put("voiceIdMasked", ElevenLabsClient.voiceIdMaskele(voiceId));
        json.put("inputCharacterCount", metin.metin().length());
        json.put("originalInputCharacterCount", metin.orijinalKarakter());
        json.put("normalizedInputCharacterCount", metin.normalizeKarakter());
        json.put("estimatedCharacterCost", tahminiKredi);
        json.put("generatedAt", OffsetDateTime.now().toString());
        json.put("outputFile", mp3.toAbsolutePath().toString());
        json.put("requestHash", requestHash);
        json.put("telaffuzRevision", telaffuzRevizyon);
        json.put("alignmentStatus", AlignmentPlan.notRequested().status());
        json.put("alignmentOutputPath", "");
        ArrayNode kaynaklar = json.putArray("sourceChunkIds");
        for (String id : chunkIds) {
            kaynaklar.add(id);
        }
        ArrayNode uygulanan = json.putArray("appliedPronunciationNotes");
        for (String not : metin.uygulananNotlar()) {
            uygulanan.add(not);
        }
        ArrayNode uyarilar = json.putArray("normalizationWarnings");
        for (String uyari : metin.uyarilar()) {
            uyarilar.add(uyari);
        }
        return json;
    }

    private static boolean mevcutOnizlemeKullanilabilir(Path json, Path mp3, String requestHash) throws Exception {
        if (!Files.isRegularFile(json) || !Files.isRegularFile(mp3)) {
            return false;
        }
        if (Files.size(mp3) < EN_AZ_MP3_BOYUTU) {
            Files.deleteIfExists(mp3);
            return false;
        }
        JsonNode kok = JSON.readTree(Files.readString(json, StandardCharsets.UTF_8));
        String oncekiHash = kok.path("requestHash").asText("");
        String oncekiDurum = kok.path("status").asText("");
        return requestHash.equals(oncekiHash)
                && ("BASARILI".equalsIgnoreCase(oncekiDurum) || "MEVCUT_KULLANILDI".equalsIgnoreCase(oncekiDurum));
    }

    private static OnizlemeMetni onizlemeMetniHazirla(List<Path> parcalar, List<TelaffuzNotu> telaffuzNotlari) throws Exception {
        StringBuilder birlestir = new StringBuilder();
        List<String> kaynaklar = new ArrayList<>();
        for (Path parca : parcalar) {
            String ham = Files.readString(parca, StandardCharsets.UTF_8);
            String temiz = TurkishSpeechTextNormalizer.normalize(ham);
            if (temiz.isBlank()) {
                continue;
            }
            if (!birlestir.isEmpty()) {
                birlestir.append(' ');
            }
            birlestir.append(temiz);
            kaynaklar.add(parca.getFileName().toString().replace(".txt", ""));
            if (birlestir.length() >= MAKS_KARAKTER) {
                break;
            }
        }

        String orijinal = onizlemeUzunluguAyarla(birlestir.toString());
        if (orijinal.length() < MIN_KARAKTER / 3) {
            throw new IllegalStateException("Önizleme için yeterli metin bulunamadı.");
        }
        TelaffuzNormalizerService.Sonuc telaffuz = TelaffuzNormalizerService.uygula(orijinal, telaffuzNotlari);
        String metin = telaffuz.metin();
        return new OnizlemeMetni(metin, orijinal.length(), metin.length(), kaynaklar,
                telaffuz.uygulananNotlar(), telaffuz.uyarilar());
    }

    private static String onizlemeUzunluguAyarla(String metin) {
        String temiz = metin == null ? "" : metin.replaceAll("\\s+", " ").trim();
        if (temiz.length() <= MAKS_KARAKTER) {
            return temiz;
        }
        int hedef = Math.min(MAKS_KARAKTER, Math.max(MIN_KARAKTER, 900));
        for (int i = hedef; i < Math.min(temiz.length(), hedef + 120); i++) {
            char c = temiz.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                return temiz.substring(0, i + 1).trim();
            }
        }
        int bosluk = temiz.lastIndexOf(' ', hedef);
        return temiz.substring(0, bosluk > 300 ? bosluk : hedef).trim();
    }

    private static long tahminiKredi(int karakter, String modelId) {
        double carpan = ElevenLabsClient.bilinenModelKrediCarpani(modelId);
        return Math.max(1L, (long) Math.ceil(karakter * carpan));
    }

    private static String requestHash(String metin, String modelId, String voiceId, String telaffuzRevizyon) throws Exception {
        String ham = (metin == null ? "" : metin.trim()) + "|" + modelId + "|" + voiceId.trim()
                + "|" + (telaffuzRevizyon == null ? "" : telaffuzRevizyon);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(ham.getBytes(StandardCharsets.UTF_8)));
    }

    private static Path eserKlasoruBul(Path ana, int eserId) throws Exception {
        String onEk = String.format(Locale.ROOT, "ESER-%05d", eserId).toLowerCase(Locale.ROOT);
        try (Stream<Path> stream = Files.list(ana)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(onEk))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static List<Path> metinParcalariniListele(Path ttsKlasoru) throws Exception {
        if (!Files.isDirectory(ttsKlasoru)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(ttsKlasoru)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> PARCA_ADI.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    private static String eserAdiCikar(String klasorAdi, int eserId) {
        String onEk = "ESER-" + String.format(Locale.ROOT, "%05d", eserId) + " - ";
        if (klasorAdi.startsWith(onEk)) {
            return klasorAdi.substring(onEk.length()).trim();
        }
        Matcher matcher = Pattern.compile("ESER-\\d{5}\\s*-\\s*(.+)").matcher(klasorAdi);
        return matcher.find() ? matcher.group(1).trim() : klasorAdi;
    }

    private static void logYaz(Path log, String satir) {
        try {
            Files.createDirectories(log.getParent());
            String zamanli = OffsetDateTime.now() + " | " + satir + System.lineSeparator();
            Files.writeString(log, zamanli, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private record OnizlemeMetni(String metin,
                                 int orijinalKarakter,
                                 int normalizeKarakter,
                                 List<String> kaynakChunkIds,
                                 List<String> uygulananNotlar,
                                 List<String> uyarilar) {
    }

    public record OnizlemeSonucu(boolean basarili,
                                  boolean mevcutKullanildi,
                                  String mesaj,
                                  Path mp3,
                                  Path json) {
        public static OnizlemeSonucu basarili(Path mp3, Path json, boolean mevcut, String mesaj) {
            return new OnizlemeSonucu(true, mevcut, mesaj, mp3, json);
        }

        public static OnizlemeSonucu hatali(String mesaj) {
            return new OnizlemeSonucu(false, false, mesaj, null, null);
        }
    }
}
