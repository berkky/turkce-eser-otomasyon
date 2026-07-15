import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Adım 36 OpenAI gpt-4o-mini-tts adaptörü.
 * Varsayılan canlı kapalı; otomatik retry yok; injection ile offline test edilebilir.
 */
public final class OpenAiLiveTtsSaglayici implements TtsSaglayici {
    static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/audio/speech";
    static final String DEFAULT_INSTRUCTIONS = "Türkçe bir edebî hikâye anlatıcısı gibi oku. Sıcak, doğal, sakin ve "
            + "dinlemesi rahat bir ton kullan. Nostaljik atmosferi koru ancak tiyatral aşırılıktan kaçın. "
            + "Diyaloglarda hafif karakter ayrımı yap fakat karakterleri karikatürleştirme. Kelimeleri değiştirme, "
            + "ekleme veya çıkarma. Kaşağı, Dadaruh ve özel adları açık ve doğal telaffuz et. Cümle sonlarında "
            + "doğal duraklamalar kullan. Orta-yavaş, akıcı bir tempo koru.";
    static final Set<String> ALLOWED_VOICES = Set.of("marin", "cedar");
    static final Set<String> ALLOWED_MODELS = Set.of("gpt-4o-mini-tts");
    private static final long MIN_BYTES = 44;
    private static final long MAX_BYTES = 25L * 1024 * 1024;

    private final OpenAiConfig config;
    private final OpenAiHttpTransport transport;
    private final ObjectMapper json = new ObjectMapper();

    public OpenAiLiveTtsSaglayici() {
        this(OpenAiConfig.fromEnvironment(), new JavaHttpTransport());
    }

    OpenAiLiveTtsSaglayici(OpenAiConfig config, OpenAiHttpTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    @Override public String kimlik() { return "openai"; }
    @Override public String ad() { return "OpenAI gpt-4o-mini-tts"; }
    @Override public String model() { return config.model(); }
    @Override public String ses() { return config.voice(); }

    @Override
    public Hazirlik hazirlik() {
        if (!ALLOWED_MODELS.contains(config.model())) return Hazirlik.degil("MODEL_NOT_ALLOWED");
        if (!ALLOWED_VOICES.contains(config.voice())) return Hazirlik.degil("VOICE_NOT_ALLOWED");
        if (!config.liveEnabled()) return Hazirlik.hazir("Mock/locked; OPENAI_TTS_LIVE_ENABLED=false");
        return config.apiKey().isBlank()
                ? Hazirlik.degil("OPENAI_API_KEY_MISSING")
                : Hazirlik.hazir("Canlı yapılandırma hazır; CLI confirmation ayrıca zorunlu");
    }

    @Override
    public TtsUretimSonucu uret(TtsUretimIstegi istek, Path ciktiKlasoru) throws Exception {
        OpenAiUretimDetayi detay = uretDetayli(istek, ciktiKlasoru, "", BigDecimal.ZERO, false, false);
        return new TtsUretimSonucu(kimlik(), ad(), model(), ses(), istek.ornekId(), istek.metinTuru(),
                detay.file(), istek.metin().length(), Files.size(detay.file()), detay.elapsedMs());
    }

    OpenAiUretimDetayi uretDetayli(TtsUretimIstegi istek, Path ciktiKlasoru, String instructions,
                                   BigDecimal budgetUsd, boolean liveConfirmed, boolean dryRun)
            throws Exception {
        if (istek.metin() == null || istek.metin().isBlank()) {
            throw live("INVALID_REQUEST", 0, "Boş metin");
        }
        if (!ALLOWED_MODELS.contains(config.model())) throw live("MODEL_NOT_ALLOWED", 0, "Model izinli değil");
        if (!ALLOWED_VOICES.contains(config.voice())) throw live("VOICE_NOT_ALLOWED", 0, "Voice izinli değil");
        if (config.liveEnabled()) {
            if (dryRun) throw live("DRY_RUN_ONLY", 0, "Dry-run aktif");
            if (!liveConfirmed) throw live("LIVE_CONFIRMATION_REQUIRED", 0, "Confirmation eksik");
            if (config.apiKey().isBlank()) throw live("OPENAI_API_KEY_MISSING", 0, "API key yok");
            BigDecimal estimate = estimatedCost(istek.metin().length(), 72);
            if (budgetUsd == null || budgetUsd.signum() <= 0 || estimate.compareTo(budgetUsd) > 0) {
                throw live("BUDGET_EXCEEDED", 0, "Bütçe yetersiz");
            }
        }

        String instructionText = instructions == null || instructions.isBlank()
                ? DEFAULT_INSTRUCTIONS : instructions;
        Path target = ciktiKlasoru.resolve("openai-" + config.voice() + ".wav");
        Path state = target.resolveSibling(target.getFileName() + ".request.json");
        String requestHash = requestHash(istek.metin(), instructionText);
        if (sameSuccessfulRequest(state, target, requestHash)) {
            return new OpenAiUretimDetayi(target, requestHash, XaiTtsSaglayici.sha256(target),
                    instructionHash(instructionText), Files.size(target), 0L, 0,
                    estimatedCost(istek.metin().length(), 72), true,
                    config.liveEnabled() ? "LIVE" : "MOCK", "CACHE_HIT");
        }
        if (Files.exists(target) && Files.size(target) > 0) {
            throw live("RAW_FILE_ALREADY_EXISTS", 0, "Raw dosya mevcut");
        }

        OffsetDateTime started = OffsetDateTime.now();
        long start = System.nanoTime();
        byte[] body;
        String contentType;
        if (!config.liveEnabled()) {
            body = XaiTtsSaglayici.mockWav();
            contentType = "audio/wav";
        } else {
            OpenAiHttpResponse response;
            try {
                response = transport.send(config.endpoint(), config.apiKey(),
                        requestJson(istek.metin(), instructionText), "audio/wav", config.timeout());
            } catch (HttpTimeoutException e) {
                throw live("TIMEOUT", 408, "OpenAI TTS zaman aşımı");
            } catch (IOException e) {
                throw live("PROVIDER_SERVER_ERROR", 0, "OpenAI ağ hatası");
            }
            // Adım 36: otomatik retry yok — her hata kodunda dur.
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String rid = maskedRequestId(response.headers());
                throw live(errorCode(response.statusCode()), response.statusCode(),
                        "OpenAI TTS HTTP " + response.statusCode()
                                + (rid.isBlank() ? "" : " requestId=" + rid)
                                + " — yeniden onay gerekir; response body loglanmaz");
            }
            body = response.body();
            contentType = response.contentType();
        }
        validateAudio(body, contentType);
        TtsLaboratuvarYardimci.atomikYaz(target, body);
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        String hash = XaiTtsSaglayici.sha256(target);
        writeState(state, requestHash, hash, instructionHash(instructionText), started, OffsetDateTime.now());
        return new OpenAiUretimDetayi(target, requestHash, hash, instructionHash(instructionText),
                body.length, elapsed, 1, estimatedCost(istek.metin().length(), 72), false,
                config.liveEnabled() ? "LIVE" : "MOCK", "SUCCESS");
    }

    static BigDecimal estimatedCost(int characters, int estimatedSpeechSeconds) {
        // ESTIMATED_ONLY — süre tabanlı belirsiz planlama; kesin maliyet değildir.
        BigDecimal perMinute = decimalEnv("OPENAI_TTS_PLANNING_USD_PER_MINUTE", new BigDecimal("0.015"));
        return BigDecimal.valueOf(Math.max(1, estimatedSpeechSeconds))
                .divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP)
                .multiply(perMinute)
                .setScale(8, RoundingMode.HALF_UP);
    }

    static void validateAudio(byte[] body, String contentType) {
        if (body == null || body.length == 0) throw live("EMPTY_BODY", 0, "Boş body");
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (mime.isBlank() || (!mime.equals("audio/wav") && !mime.equals("audio/x-wav")
                && !mime.equals("audio/wave"))) {
            throw live("INVALID_MIME_TYPE", 0, "Beklenmeyen veya boş MIME");
        }
        if (body.length < MIN_BYTES) throw live("UNPLAYABLE_AUDIO", 0, "Ses çok küçük");
        if (body.length > MAX_BYTES) throw live("UNPLAYABLE_AUDIO", 0, "Ses çok büyük");
        boolean wav = body.length >= 12 && body[0] == 'R' && body[1] == 'I' && body[2] == 'F' && body[3] == 'F'
                && body[8] == 'W' && body[9] == 'A' && body[10] == 'V' && body[11] == 'E';
        if (!wav) throw live("UNPLAYABLE_AUDIO", 0, "RIFF/WAVE başlığı yok");
    }

    private String requestJson(String text, String instructions) throws Exception {
        ObjectNode root = json.createObjectNode();
        root.put("model", config.model());
        root.put("voice", config.voice());
        root.put("input", text);
        root.put("instructions", instructions);
        root.put("response_format", "wav");
        return json.writeValueAsString(root);
    }

    private String requestHash(String text, String instructions) throws Exception {
        return XaiTtsSaglayici.sha256((config.model() + "|" + config.voice() + "|" + text + "|"
                + instructions + "|wav").getBytes(StandardCharsets.UTF_8));
    }

    static String instructionHash(String instructions) throws Exception {
        return XaiTtsSaglayici.sha256(instructions.getBytes(StandardCharsets.UTF_8));
    }

    private boolean sameSuccessfulRequest(Path state, Path audio, String hash) {
        try {
            if (!Files.isRegularFile(state) || !Files.isRegularFile(audio) || Files.size(audio) == 0) {
                return false;
            }
            return hash.equals(json.readTree(state.toFile()).path("requestHash").asText())
                    && "SUCCESS".equals(json.readTree(state.toFile()).path("status").asText());
        } catch (Exception ignored) {
            return false;
        }
    }

    private void writeState(Path state, String requestHash, String audioHash, String instructionHash,
                            OffsetDateTime started, OffsetDateTime completed) throws Exception {
        ObjectNode node = json.createObjectNode();
        node.put("provider", "openai");
        node.put("model", config.model());
        node.put("voice", config.voice());
        node.put("requestHash", requestHash);
        node.put("instructionHash", instructionHash);
        node.put("audioSha256", audioHash);
        node.put("status", "SUCCESS");
        node.put("requestCount", 1);
        node.put("startedAt", started.toString());
        node.put("completedAt", completed.toString());
        TtsLaboratuvarYardimci.atomikYaz(state, json.writerWithDefaultPrettyPrinter().writeValueAsBytes(node));
    }

    private static String errorCode(int status) {
        return switch (status) {
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "UNKNOWN_VOICE";
            case 408 -> "TIMEOUT";
            case 429 -> "RATE_LIMITED";
            case 500, 502, 503 -> "PROVIDER_SERVER_ERROR";
            default -> "HTTP_" + status;
        };
    }

    private static OpenAiLiveException live(String code, int http, String message) {
        return new OpenAiLiveException(code, http, message);
    }

    /** Response body asla exception/log'a eklenmez; yalnız header request-id maskelenir. */
    static String maskedRequestId(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return "";
        String raw = null;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            String k = e.getKey() == null ? "" : e.getKey().toLowerCase(Locale.ROOT);
            if ("x-request-id".equals(k) || "request-id".equals(k)) {
                raw = e.getValue();
                break;
            }
        }
        if (raw == null || raw.isBlank()) return "";
        String v = raw.trim();
        if (v.length() <= 8) return "****";
        return v.substring(0, 4) + "…" + v.substring(v.length() - 4);
    }

    private static BigDecimal decimalEnv(String name, BigDecimal fallback) {
        String value = TtsLaboratuvarYardimci.ortam(name, "");
        if (value == null || value.isBlank()) return fallback;
        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            return parsed.signum() >= 0 ? parsed : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    record OpenAiConfig(String apiKey, String model, String voice, boolean liveEnabled,
                        URI endpoint, Duration timeout) {
        static OpenAiConfig fromEnvironment() {
            String model = TtsLaboratuvarYardimci.ortam("OPENAI_TTS_MODEL", "gpt-4o-mini-tts");
            String voice = TtsLaboratuvarYardimci.ortam("OPENAI_TTS_VOICE", "marin");
            return new OpenAiConfig(
                    TtsLaboratuvarYardimci.ortam("OPENAI_API_KEY", ""),
                    model, voice,
                    Boolean.parseBoolean(TtsLaboratuvarYardimci.ortam("OPENAI_TTS_LIVE_ENABLED", "false")),
                    URI.create(DEFAULT_ENDPOINT), Duration.ofMinutes(5));
        }
    }

    record OpenAiUretimDetayi(Path file, String requestHash, String sha256, String instructionHash,
                              long fileSize, long elapsedMs, int requestCount,
                              BigDecimal estimatedCostUsd, boolean reused, String liveOrMock,
                              String status) {
    }

    record OpenAiHttpResponse(int statusCode, String contentType, byte[] body, Map<String, String> headers) {
    }

    @FunctionalInterface
    interface OpenAiHttpTransport {
        OpenAiHttpResponse send(URI endpoint, String apiKey, String jsonBody,
                                String accept, Duration timeout) throws Exception;
    }

    static final class JavaHttpTransport implements OpenAiHttpTransport {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30)).build();

        @Override
        public OpenAiHttpResponse send(URI endpoint, String apiKey, String jsonBody,
                                       String accept, Duration timeout) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", accept)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return new OpenAiHttpResponse(response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(""),
                    response.body(), Map.of());
        }
    }

    static final class OpenAiLiveException extends RuntimeException {
        private final String code;
        private final int httpStatus;

        OpenAiLiveException(String code, int httpStatus, String message) {
            super(message);
            this.code = code;
            this.httpStatus = httpStatus;
        }

        String code() { return code; }
        int httpStatus() { return httpStatus; }
    }
}
