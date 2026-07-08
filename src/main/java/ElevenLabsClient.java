import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * ElevenLabs Text-to-Speech API istemcisi.
 * API anahtarını yalnızca ELEVENLABS_API_KEY ortam değişkeninden okur.
 */
public final class ElevenLabsClient implements ElevenLabsIstemci {
    private static final String API_KOKU = "https://api.elevenlabs.io";
    private static final int EN_AZ_GECERLI_AUDIO_BOYUTU = 128;
    private static final int MAKSIMUM_RETRY = 3;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ElevenLabsClient(String apiKey) {
        this(apiKey, ortamModeliVeyaVarsayilan());
    }

    public ElevenLabsClient(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("ELEVENLABS_API_KEY bulunamadı.");
        }
        this.apiKey = apiKey.trim();
        this.model = model == null || model.isBlank()
                ? "eleven_multilingual_v2"
                : model.trim();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    private static String ortamModeliVeyaVarsayilan() {
        return ElevenLabsModelPolitikasi.ortamModeliVeyaVarsayilan();
    }

    public static String voiceIdMaskele(String voiceId) {
        if (voiceId == null || voiceId.isBlank()) {
            return "YOK";
        }
        String temiz = voiceId.trim();
        if (temiz.length() <= 8) {
            return "****";
        }
        return temiz.substring(0, 4) + "..." + temiz.substring(temiz.length() - 4);
    }

    @Override
    public String getModel() {
        return model;
    }

    /**
     * Kullanıcının gerçek abonelik/kredi durumunu ElevenLabs'tan okur.
     * Bu çağrı için API anahtarında user_read izni bulunmalıdır.
     */
    public AbonelikBilgisi abonelikBilgisiniGetir() throws Exception {
        URI uri = URI.create(API_KOKU + "/v1/user/subscription");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(60))
                .header("xi-api-key", apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw apiHatasi("Abonelik/kredi bilgisi alınamadı", response.statusCode(), response.body());
        }

        JsonNode kok = objectMapper.readTree(response.body());
        String tier = kok.path("tier").asText("bilinmiyor").trim();
        String durum = kok.path("status").asText("bilinmiyor").trim();
        long kullanilan = kok.path("character_count").asLong(0L);
        long limit = kok.path("character_limit").asLong(0L);
        long sonrakiSifirlamaUnix = kok.path("next_character_count_reset_unix").asLong(0L);
        boolean asimKullanilabilir = kok.path("can_extend_character_limit").asBoolean(false);
        boolean acikFaturaVar = kok.path("has_open_invoices").asBoolean(false);

        JsonNode asimNode = kok.path("max_credit_limit_extension");
        boolean sinirsizAsim = asimNode.isTextual()
                && "unlimited".equalsIgnoreCase(asimNode.asText(""));
        long azamiAsim = sinirsizAsim ? Long.MAX_VALUE : Math.max(0L, asimNode.asLong(0L));

        return new AbonelikBilgisi(
                tier,
                durum,
                Math.max(0L, kullanilan),
                Math.max(0L, limit),
                azamiAsim,
                sinirsizAsim,
                asimKullanilabilir,
                acikFaturaVar,
                sonrakiSifirlamaUnix
        );
    }

    /**
     * Modelin API tarafından bildirilen kredi çarpanını okumayı dener.
     * Bu çağrı için models_read izni gerekebilir. Çağrı başarısız olursa
     * üst katman güvenli varsayım olarak 1.0 kullanabilir.
     */
    public ModelKrediBilgisi modelKrediBilgisiniGetir() throws Exception {
        URI uri = URI.create(API_KOKU + "/v1/models");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(60))
                .header("xi-api-key", apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw apiHatasi("Model kredi bilgisi alınamadı", response.statusCode(), response.body());
        }

        JsonNode kok = objectMapper.readTree(response.body());
        if (kok.isArray()) {
            for (JsonNode node : kok) {
                if (!model.equals(node.path("model_id").asText(""))) {
                    continue;
                }

                double carpan = node.path("model_rates")
                        .path("character_cost_multiplier")
                        .asDouble(Double.NaN);
                if (!Double.isFinite(carpan) || carpan <= 0.0d) {
                    carpan = node.path("token_cost_factor").asDouble(Double.NaN);
                }
                boolean apiCarpaniBulundu = Double.isFinite(carpan) && carpan > 0.0d;
                if (!apiCarpaniBulundu) {
                    carpan = bilinenModelKrediCarpani(model);
                }

                return new ModelKrediBilgisi(
                        model,
                        node.path("name").asText(model).trim(),
                        carpan,
                        node.path("maximum_text_length_per_request").asInt(0),
                        apiCarpaniBulundu
                );
            }
        }

        return new ModelKrediBilgisi(model, model, bilinenModelKrediCarpani(model), 0, false);
    }

    public static double bilinenModelKrediCarpani(String modelId) {
        if (modelId == null) {
            return 1.0d;
        }
        String temiz = modelId.trim().toLowerCase(Locale.ROOT);
        if ("eleven_flash_v2_5".equals(temiz) || "eleven_turbo_v2_5".equals(temiz)) {
            return 0.5d;
        }
        return 1.0d;
    }

    public List<Ses> sesleriListele() throws Exception {
        URI uri = URI.create(API_KOKU + "/v2/voices?page_size=100&include_total_count=true&sort=name&sort_direction=asc");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(60))
                .header("xi-api-key", apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw apiHatasi("Ses listesi alınamadı", response.statusCode(), response.body());
        }

        JsonNode kok = objectMapper.readTree(response.body());
        List<Ses> sesler = new ArrayList<>();
        for (JsonNode node : kok.path("voices")) {
            String id = node.path("voice_id").asText("").trim();
            String ad = node.path("name").asText("İsimsiz ses").trim();
            String kategori = node.path("category").asText("bilinmiyor").trim();
            String aciklama = node.path("description").asText("").trim();
            JsonNode etiketler = node.path("labels");
            String cinsiyet = etiketler.path("gender").asText("").trim();
            String aksan = etiketler.path("accent").asText("").trim();
            boolean turkceDogrulanmis = false;
            for (JsonNode dil : node.path("verified_languages")) {
                String kod = dil.path("language").asText("");
                String locale = dil.path("locale").asText("");
                if ("tr".equalsIgnoreCase(kod)
                        || "tur".equalsIgnoreCase(kod)
                        || locale.toLowerCase(Locale.ROOT).startsWith("tr")) {
                    turkceDogrulanmis = true;
                    break;
                }
            }
            if (!id.isBlank()) {
                sesler.add(new Ses(id, ad, kategori, cinsiyet, aksan, aciklama, turkceDogrulanmis));
            }
        }

        sesler.sort(Comparator
                .comparing(Ses::turkceDogrulanmis).reversed()
                .thenComparing(Ses::ad, String.CASE_INSENSITIVE_ORDER));
        return sesler;
    }

    @Override
    public boolean sesIdDogrula(String voiceId) throws Exception {
        if (voiceId == null || voiceId.isBlank()) {
            return false;
        }
        String kodlanmis = URLEncoder.encode(voiceId.trim(), StandardCharsets.UTF_8);
        URI uri = URI.create(API_KOKU + "/v1/voices/" + kodlanmis);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("xi-api-key", apiKey)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() == 404) {
            return false;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw apiHatasi("Ses doğrulaması başarısız", response.statusCode(), response.body());
        }
        JsonNode kok = objectMapper.readTree(response.body());
        return voiceId.trim().equals(kok.path("voice_id").asText("").trim());
    }

    @Override
    public boolean modelDestekleniyorMu(String modelId) throws Exception {
        String hedef = modelId == null || modelId.isBlank() ? model : modelId.trim();
        if (!ElevenLabsModelPolitikasi.desteklenenModelMi(hedef)) {
            return false;
        }
        URI uri = URI.create(API_KOKU + "/v1/models");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(60))
                .header("xi-api-key", apiKey)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw apiHatasi("Model listesi alınamadı", response.statusCode(), response.body());
        }
        JsonNode kok = objectMapper.readTree(response.body());
        if (!kok.isArray()) {
            return ElevenLabsModelPolitikasi.desteklenenModelMi(hedef);
        }
        for (JsonNode node : kok) {
            if (hedef.equals(node.path("model_id").asText(""))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public SesUretimSonucu sesUret(String metin, Ses ses, Path ciktiDosyasi) throws Exception {
        return sesUret(metin, null, null, ses, ciktiDosyasi);
    }

    /**
     * Bir TTS parçasını önceki ve sonraki metin bağlamıyla üretir. Bağlam alanları,
     * çok parçalı uzun metinlerde ton ve akış tutarlılığını iyileştirmek için kullanılır.
     */
    @Override
    public SesUretimSonucu sesUret(String metin,
                                   String oncekiMetin,
                                   String sonrakiMetin,
                                   Ses ses,
                                   Path ciktiDosyasi) throws Exception {
        if (metin == null || metin.isBlank()) {
            throw new IllegalArgumentException("Seslendirilecek metin boş olamaz.");
        }
        if (ses == null || ses.id() == null || ses.id().isBlank()) {
            throw new IllegalArgumentException("Geçerli bir ElevenLabs sesi seçilmedi.");
        }

        int deneme = 0;
        while (true) {
            deneme++;
            try {
                return sesUretTekDeneme(metin, oncekiMetin, sonrakiMetin, ses, ciktiDosyasi);
            } catch (ElevenLabsApiException e) {
                boolean tekrar = (e.getDurumKodu() == 429 || e.getDurumKodu() >= 500) && deneme < MAKSIMUM_RETRY;
                if (!tekrar) {
                    throw e;
                }
                long beklemeMs = (long) Math.min(12_000L, Math.pow(2, deneme) * 750L);
                Thread.sleep(beklemeMs);
            }
        }
    }

    private SesUretimSonucu sesUretTekDeneme(String metin,
                                             String oncekiMetin,
                                             String sonrakiMetin,
                                             Ses ses,
                                             Path ciktiDosyasi) throws Exception {
        Files.createDirectories(ciktiDosyasi.getParent());

        ObjectNode govde = objectMapper.createObjectNode();
        govde.put("text", metin.trim());
        govde.put("model_id", model);
        govde.put("language_code", "tr");
        govde.put("apply_text_normalization", "auto");

        if (oncekiMetin != null && !oncekiMetin.isBlank()) {
            govde.put("previous_text", oncekiMetin.trim());
        }
        if (sonrakiMetin != null && !sonrakiMetin.isBlank()) {
            govde.put("next_text", sonrakiMetin.trim());
        }

        ObjectNode ayarlar = govde.putObject("voice_settings");
        ayarlar.put("stability", 0.45);
        ayarlar.put("similarity_boost", 0.75);
        ayarlar.put("style", 0.15);
        ayarlar.put("use_speaker_boost", true);
        ayarlar.put("speed", 0.95);

        String voiceId = URLEncoder.encode(ses.id(), StandardCharsets.UTF_8);
        URI uri = URI.create(API_KOKU + "/v1/text-to-speech/" + voiceId
                + "?output_format=mp3_44100_128");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMinutes(4))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(govde), StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        int kod = response.statusCode();
        if (kod < 200 || kod >= 300) {
            String body = new String(response.body(), StandardCharsets.UTF_8);
            throw apiHatasi("Ses üretilemedi", kod, body);
        }

        byte[] audio = response.body();
        if (audio == null || audio.length < EN_AZ_GECERLI_AUDIO_BOYUTU) {
            throw new IllegalStateException("Geçersiz veya boş ses dosyası alındı.");
        }

        Path partial = ciktiDosyasi.resolveSibling(ciktiDosyasi.getFileName() + ".partial");
        Files.deleteIfExists(partial);
        try {
            Files.write(partial, audio);
            try {
                Files.move(partial, ciktiDosyasi,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(partial, ciktiDosyasi, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(partial);
        }

        return new SesUretimSonucu(
                ciktiDosyasi,
                audio.length,
                metin.length(),
                ses,
                model,
                "mp3_44100_128"
        );
    }

    private ElevenLabsApiException apiHatasi(String onEk, int durumKodu, String body) {
        String mesaj = hataMesajiniCikar(body);
        String kucukMesaj = mesaj.toLowerCase(Locale.ROOT);
        String aciklama;
        if (durumKodu == 401 && kucukMesaj.contains("user_read")) {
            aciklama = "API anahtarında user_read izni yok.";
        } else if (durumKodu == 401 && kucukMesaj.contains("voices_read")) {
            aciklama = "API anahtarında voices_read izni yok.";
        } else if (durumKodu == 401 && kucukMesaj.contains("models_read")) {
            aciklama = "API anahtarında models_read izni yok.";
        } else if ((durumKodu == 401 || durumKodu == 402 || durumKodu == 429)
                && (kucukMesaj.contains("quota")
                || kucukMesaj.contains("credit")
                || kucukMesaj.contains("remaining"))) {
            aciklama = "ElevenLabs kredisi yetersiz.";
        } else if (durumKodu == 401) {
            aciklama = "API anahtarı geçersiz, iptal edilmiş veya gerekli izin eksik.";
        } else if (durumKodu == 403) {
            aciklama = "API anahtarı veya hesap izni bu işlem için yeterli değil.";
        } else if (durumKodu == 402) {
            aciklama = "Hesap planı ya da bakiye bu işlem için yeterli değil.";
        } else if (durumKodu == 429) {
            aciklama = "Kota veya hız sınırı aşıldı.";
        } else {
            aciklama = "ElevenLabs API hatası.";
        }
        return new ElevenLabsApiException(
                durumKodu,
                onEk + " (HTTP " + durumKodu + "): " + aciklama
                        + (mesaj.isBlank() ? "" : " " + mesaj)
        );
    }

    private String hataMesajiniCikar(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String[] yollar = {
                    node.path("detail").path("message").asText(""),
                    node.path("detail").path("status").asText(""),
                    node.path("message").asText(""),
                    node.path("error").path("message").asText("")
            };
            for (String deger : yollar) {
                if (deger != null && !deger.isBlank()) {
                    return deger.trim();
                }
            }
        } catch (Exception ignored) {
        }
        String temiz = body.replaceAll("\\s+", " ").trim();
        return temiz.length() > 300 ? temiz.substring(0, 300) + "..." : temiz;
    }

    public static final class ElevenLabsApiException extends IllegalStateException {
        private final int durumKodu;

        public ElevenLabsApiException(int durumKodu, String mesaj) {
            super(mesaj);
            this.durumKodu = durumKodu;
        }

        public int getDurumKodu() {
            return durumKodu;
        }
    }

    public record AbonelikBilgisi(String plan,
                                  String durum,
                                  long kullanilanKredi,
                                  long donemKrediLimiti,
                                  long azamiUcretliAsimKredisi,
                                  boolean sinirsizUcretliAsim,
                                  boolean ucretliAsimKullanilabilir,
                                  boolean acikFaturaVar,
                                  long sonrakiSifirlamaUnix) {

        public long kalanPlanKredisi() {
            return Math.max(0L, donemKrediLimiti - kullanilanKredi);
        }

        public long kullanilabilirKredi(boolean ucretliAsmaIzinVer) {
            if (!ucretliAsmaIzinVer || !ucretliAsimKullanilabilir) {
                return kalanPlanKredisi();
            }
            if (sinirsizUcretliAsim) {
                return Long.MAX_VALUE;
            }
            long toplamTavan;
            try {
                toplamTavan = Math.addExact(donemKrediLimiti, azamiUcretliAsimKredisi);
            } catch (ArithmeticException e) {
                toplamTavan = Long.MAX_VALUE;
            }
            return Math.max(0L, toplamTavan - kullanilanKredi);
        }

        public boolean aktifMi() {
            if (durum == null || durum.isBlank() || "bilinmiyor".equalsIgnoreCase(durum)) {
                return true;
            }
            String temiz = durum.trim().toLowerCase(Locale.ROOT);
            return !(temiz.equals("canceled")
                    || temiz.equals("cancelled")
                    || temiz.equals("expired")
                    || temiz.equals("inactive")
                    || temiz.equals("past_due")
                    || temiz.equals("unpaid"));
        }
    }

    public record ModelKrediBilgisi(String modelId,
                                    String modelAdi,
                                    double karakterKrediCarpani,
                                    int tekIstekAzamiKarakter,
                                    boolean apiTarafindanDogrulandi) {

        public long tahminiKredi(long karakterSayisi) {
            if (karakterSayisi <= 0L) {
                return 0L;
            }
            return Math.max(1L, (long) Math.ceil(karakterSayisi * karakterKrediCarpani));
        }
    }

    public record Ses(String id,
                      String ad,
                      String kategori,
                      String cinsiyet,
                      String aksan,
                      String aciklama,
                      boolean turkceDogrulanmis) {

        public String kisaBilgi() {
            List<String> ayrintilar = new ArrayList<>();
            if (turkceDogrulanmis) {
                ayrintilar.add("Türkçe doğrulanmış");
            }
            if (cinsiyet != null && !cinsiyet.isBlank()) {
                ayrintilar.add(cinsiyet);
            }
            if (aksan != null && !aksan.isBlank()) {
                ayrintilar.add(aksan);
            }
            if (kategori != null && !kategori.isBlank()) {
                ayrintilar.add(kategori);
            }
            return ayrintilar.isEmpty() ? ad : ad + " | " + String.join(" | ", ayrintilar);
        }
    }

    public record SesUretimSonucu(Path dosya,
                                  long bayt,
                                  int karakter,
                                  Ses ses,
                                  String model,
                                  String format) {
    }
}
