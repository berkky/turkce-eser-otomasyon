import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class XaiTtsSaglayici implements TtsSaglayici {
    static final String DEFAULT_ENDPOINT = "https://api.x.ai/v1/tts";
    static final int UNARY_CHARACTER_LIMIT = 15_000;
    static final int EXPERIMENT_CHARACTER_LIMIT = 1_500;
    static final Set<String> CONFIGURED_VOICE_CANDIDATES = Set.of("lumen", "ursa", "sal");
    private static final int MAX_RETRIES = 3;
    private static final BigDecimal PRICE_PER_MILLION = new BigDecimal("15.00");

    private final XaiConfig config;
    private final XaiRequestContext context;
    private final XaiHttpTransport transport;
    private final XaiSleeper sleeper;
    private final ObjectMapper json = new ObjectMapper();

    public XaiTtsSaglayici() {
        this(XaiConfig.fromEnvironment(), XaiRequestContext.mock(), new JavaHttpTransport(), Thread::sleep);
    }

    XaiTtsSaglayici(XaiConfig config, XaiRequestContext context,
                    XaiHttpTransport transport, XaiSleeper sleeper) {
        this.config = config;
        this.context = context;
        this.transport = transport;
        this.sleeper = sleeper;
    }

    @Override public String kimlik() { return "xai"; }
    @Override public String ad() { return "xAI Text to Speech"; }
    @Override public String model() { return "xai-tts"; }
    @Override public String ses() { return config.voice(); }

    @Override
    public Hazirlik hazirlik() {
        if (config.voice().isBlank()) {
            return Hazirlik.degil("XAI_TTS_VOICE yok");
        }
        if (!config.liveEnabled()) {
            return Hazirlik.hazir("Mock hazır; canlı mod varsayılan kapalı");
        }
        return config.apiKey().isBlank()
                ? Hazirlik.degil("XAI_API_KEY yok")
                : Hazirlik.hazir("Canlı yapılandırma hazır; CLI onayı ayrıca zorunlu");
    }

    @Override
    public TtsUretimSonucu uret(TtsUretimIstegi istek, Path ciktiKlasoru) throws Exception {
        XaiUretimDetayi detay = uretDetayli(istek, ciktiKlasoru);
        return new TtsUretimSonucu(kimlik(), ad(), model(), ses(), istek.ornekId(), istek.metinTuru(),
                detay.file(), istek.metin().length(), Files.size(detay.file()), detay.elapsedMs());
    }

    XaiUretimDetayi uretDetayli(TtsUretimIstegi istek, Path ciktiKlasoru) throws Exception {
        validateUnaryText(istek.metin());
        validateLiveGate(istek.metin());
        String format = config.outputFormat();
        String extension = "wav".equals(format) ? ".wav" : ".mp3";
        Path target = ciktiKlasoru.resolve(kimlik()).resolve(istek.ornekId() + extension);
        Path state = target.resolveSibling(target.getFileName() + ".request.json");
        String requestHash = requestHash(istek);
        if (sameSuccessfulRequest(state, target, requestHash)) {
            return new XaiUretimDetayi(target, requestHash, sha256(target), format,
                    Files.size(target), 0L, 0, estimatedCost(istek.metin()), true,
                    config.liveEnabled() ? "LIVE" : "MOCK");
        }

        OffsetDateTime started = OffsetDateTime.now();
        long startNanos = System.nanoTime();
        int retryCount = 0;
        BigDecimal reserved = BigDecimal.ZERO;
        byte[] body;
        String contentType;
        if (!config.liveEnabled()) {
            body = mockWav();
            contentType = "audio/wav";
            format = "wav";
        } else {
            XaiHttpResponse response = null;
            int maxAttempts = context.allowAutomaticRetry() ? MAX_RETRIES : 0;
            for (int attempt = 0; attempt <= maxAttempts; attempt++) {
                BigDecimal attemptCost = estimatedCost(istek.metin());
                if (reserved.add(attemptCost).compareTo(context.budgetUsd()) > 0) {
                    throw new XaiTtsException("BUDGET_EXCEEDED", 0,
                            "Deney bütçesi retry dahil aşılıyor.");
                }
                reserved = reserved.add(attemptCost);
                try {
                    response = transport.send(config.endpoint(), config.apiKey(), requestJson(istek, format),
                            expectedMime(format), config.timeout());
                } catch (HttpTimeoutException e) {
                    throw new XaiTtsException("TIMEOUT", 408, "xAI TTS zaman aşımı.");
                } catch (IOException e) {
                    throw new XaiTtsException("NETWORK_ERROR", 0, "xAI TTS ağ hatası: " + safe(e.getMessage()));
                }
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    break;
                }
                String code = errorCode(response.statusCode());
                if (attempt < maxAttempts && retryable(response.statusCode())) {
                    retryCount++;
                    sleeper.sleep(Math.min(8_000L, 500L * (1L << attempt)));
                    continue;
                }
                String rid = maskedRequestId(response.headers());
                throw new XaiTtsException(code, response.statusCode(),
                        "xAI TTS HTTP " + response.statusCode()
                                + (rid.isBlank() ? "" : " requestId=" + rid)
                                + " — yeniden onay gerekir; response body loglanmaz");
            }
            if (response == null) {
                throw new XaiTtsException("NO_RESPONSE", 0, "xAI TTS yanıtı alınamadı.");
            }
            body = response.body();
            contentType = response.contentType();
        }

        validateAudio(body, contentType, format);
        TtsLaboratuvarYardimci.atomikYaz(target, body);
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        String hash = sha256(target);
        writeState(state, requestHash, hash, started, OffsetDateTime.now(), retryCount);
        return new XaiUretimDetayi(target, requestHash, hash, format, body.length, elapsed,
                retryCount, estimatedCost(istek.metin()).multiply(BigDecimal.valueOf(retryCount + 1L)),
                false, config.liveEnabled() ? "LIVE" : "MOCK");
    }

    private void validateLiveGate(String text) {
        if (!config.liveEnabled()) {
            return;
        }
        if (!context.cliConfirmed()) throw gate("CLI_CONFIRMATION_REQUIRED");
        if (context.dryRun()) throw gate("DRY_RUN_ACTIVE");
        if (!"ESER-00005".equals(context.eserId())) throw gate("ESER_NOT_ALLOWED");
        if (text.length() >= EXPERIMENT_CHARACTER_LIMIT) throw gate("EXPERIMENT_TEXT_TOO_LONG");
        if (!TtsAbSourceType.APPROVED_ARCHIVE_TEXT.name().equals(context.sourceType())) {
            throw gate("FIXTURE_SOURCE_NOT_ALLOWED");
        }
        if (!context.sourceApproved()) throw gate("SOURCE_NOT_APPROVED");
        if (licensePlaceholder(context.sourceLicenseNote())) throw gate("SOURCE_LICENSE_NOT_APPROVED");
        if (context.approvedSourceHash().isBlank()
                || !context.approvedSourceHash().equalsIgnoreCase(context.currentSourceHash())) {
            throw gate("SOURCE_HASH_MISMATCH");
        }
        if (config.apiKey().isBlank()) throw gate("API_KEY_MISSING");
    }

    private static boolean licensePlaceholder(String note) {
        String value = note == null ? "" : note.trim().toLowerCase(Locale.ROOT);
        return value.isBlank() || value.contains("kontrol edilmedi")
                || value.contains("placeholder") || value.contains("fixture")
                || value.contains("kanıtı değildir");
    }

    static void validateUnaryText(String text) {
        if (text == null || text.isBlank()) throw gate("EMPTY_TEXT");
        if (text.length() > UNARY_CHARACTER_LIMIT) throw gate("UNARY_TEXT_TOO_LONG");
    }

    private static XaiTtsException gate(String code) {
        return new XaiTtsException(code, 0, "xAI canlı güvenlik kapısı: " + code);
    }

    private String requestJson(TtsUretimIstegi istek, String format) throws Exception {
        ObjectNode root = json.createObjectNode();
        root.put("text", istek.metin());
        root.put("voice_id", config.voice());
        root.put("language", config.language());
        root.put("speed", 1.0);
        root.put("text_normalization", false);
        ObjectNode output = root.putObject("output_format");
        output.put("codec", format);
        output.put("sample_rate", 44_100);
        if ("mp3".equals(format)) output.put("bit_rate", 192_000);
        return json.writeValueAsString(root);
    }

    private String requestHash(TtsUretimIstegi request) throws Exception {
        return sha256((request.metin() + "|" + config.voice() + "|" + config.language() + "|"
                + config.outputFormat() + "|1.0|" + request.yonerge()).getBytes(StandardCharsets.UTF_8));
    }

    private boolean sameSuccessfulRequest(Path state, Path audio, String hash) {
        try {
            if (!Files.isRegularFile(state) || !Files.isRegularFile(audio) || Files.size(audio) == 0) return false;
            return hash.equals(json.readTree(state.toFile()).path("requestHash").asText())
                    && "SUCCESS".equals(json.readTree(state.toFile()).path("status").asText());
        } catch (Exception ignored) {
            return false;
        }
    }

    private void writeState(Path state, String requestHash, String audioHash, OffsetDateTime started,
                            OffsetDateTime completed, int retries) throws Exception {
        ObjectNode node = json.createObjectNode();
        node.put("requestHash", requestHash);
        node.put("audioSha256", audioHash);
        node.put("status", "SUCCESS");
        node.put("startedAt", started.toString());
        node.put("completedAt", completed.toString());
        node.put("retryCount", retries);
        TtsLaboratuvarYardimci.atomikYaz(state,
                json.writerWithDefaultPrettyPrinter().writeValueAsBytes(node));
    }

    static BigDecimal estimatedCost(String text) {
        int chars = text == null ? 0 : text.length();
        return PRICE_PER_MILLION.multiply(BigDecimal.valueOf(chars))
                .divide(BigDecimal.valueOf(1_000_000L), 8, RoundingMode.HALF_UP);
    }

    static void validateAudio(byte[] body, String contentType, String format) {
        if (body == null || body.length == 0) throw new XaiTtsException("EMPTY_BODY", 0, "Boş ses yanıtı.");
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (!expectedMime(format).equals(mime)) {
            throw new XaiTtsException("INVALID_MIME_TYPE", 0, "Beklenmeyen ses MIME türü: " + mime);
        }
        boolean wav = body.length >= 12 && body[0] == 'R' && body[1] == 'I' && body[2] == 'F' && body[3] == 'F'
                && body[8] == 'W' && body[9] == 'A' && body[10] == 'V' && body[11] == 'E';
        boolean mp3 = body.length >= 3 && body[0] == 'I' && body[1] == 'D' && body[2] == '3'
                || body.length >= 2 && (body[0] & 0xff) == 0xff && (body[1] & 0xe0) == 0xe0;
        if (("wav".equals(format) && !wav) || ("mp3".equals(format) && !mp3)) {
            throw new XaiTtsException("UNPLAYABLE_AUDIO", 0, "Ses başlığı beklenen formatta değil.");
        }
    }

    private static String expectedMime(String format) {
        return "wav".equals(format) ? "audio/wav" : "audio/mpeg";
    }

    private static boolean retryable(int status) {
        return status == 429 || status == 500 || status == 503;
    }

    private static String errorCode(int status) {
        return switch (status) {
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "UNKNOWN_VOICE";
            case 408 -> "TIMEOUT";
            case 429 -> "RATE_LIMITED";
            case 500 -> "PROVIDER_INTERNAL_ERROR";
            case 503 -> "PROVIDER_UNAVAILABLE";
            default -> "HTTP_" + status;
        };
    }

    static byte[] mockWav() throws Exception {
        int sampleRate = 44_100;
        int seconds = 2;
        byte[] pcm = new byte[sampleRate * seconds * 2];
        for (int i = 0; i < sampleRate * seconds; i++) {
            short sample = (short) (Math.sin(2 * Math.PI * 220 * i / sampleRate) * 3_000);
            pcm[i * 2] = (byte) (sample & 0xff);
            pcm[i * 2 + 1] = (byte) ((sample >>> 8) & 0xff);
        }
        AudioFormat audioFormat = new AudioFormat(sampleRate, 16, 1, true, false);
        try (ByteArrayInputStream in = new ByteArrayInputStream(pcm);
             AudioInputStream audio = new AudioInputStream(in, audioFormat, sampleRate * seconds);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, out);
            return out.toByteArray();
        }
    }

    static String sha256(Path path) throws Exception {
        return sha256(Files.readAllBytes(path));
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String safe(String value) {
        if (value == null) return "";
        return TtsLaboratuvarYardimci.kisalt(value
                .replaceAll("(?i)bearer\\s+[A-Za-z0-9._-]+", "Bearer [REDACTED]")
                .replaceAll("(?i)(api[_-]?key[\"']?\\s*[:=]\\s*[\"']?)[^\\s,\"']+", "$1[REDACTED]"), 500);
    }

    /** Response body asla exception/log'a eklenmez. */
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

    record XaiConfig(String apiKey, String voice, String language, boolean liveEnabled,
                     String outputFormat, URI endpoint, Duration timeout) {
        static XaiConfig fromEnvironment() {
            String format = TtsLaboratuvarYardimci.ortam("XAI_TTS_OUTPUT_FORMAT", "wav")
                    .toLowerCase(Locale.ROOT);
            if (!Set.of("wav", "mp3").contains(format)) format = "wav";
            String normalization = TtsLaboratuvarYardimci
                    .ortam("XAI_TTS_TEXT_NORMALIZATION", "none").toLowerCase(Locale.ROOT);
            if (!"none".equals(normalization)) {
                throw new IllegalArgumentException(
                        "XAI_TTS_TEXT_NORMALIZATION Adım 34 için yalnızca 'none' olabilir.");
            }
            return new XaiConfig(
                    TtsLaboratuvarYardimci.ortam("XAI_API_KEY", ""),
                    TtsLaboratuvarYardimci.ortam("XAI_TTS_VOICE", ""),
                    TtsLaboratuvarYardimci.ortam("XAI_TTS_LANGUAGE", "tr"),
                    Boolean.parseBoolean(TtsLaboratuvarYardimci.ortam("XAI_TTS_LIVE_ENABLED", "false")),
                    format, URI.create(DEFAULT_ENDPOINT), Duration.ofMinutes(5));
        }
    }

    record XaiRequestContext(String eserId, boolean cliConfirmed, boolean dryRun,
                             boolean sourceApproved, BigDecimal budgetUsd,
                             String sourceType, String sourceLicenseNote,
                             String approvedSourceHash, String currentSourceHash,
                             boolean allowAutomaticRetry) {
        XaiRequestContext(String eserId, boolean cliConfirmed, boolean dryRun,
                          boolean sourceApproved, BigDecimal budgetUsd) {
            this(eserId, cliConfirmed, dryRun, sourceApproved, budgetUsd,
                    TtsAbSourceType.APPROVED_ARCHIVE_TEXT.name(),
                    "Ticari kullanım lisansı kullanıcı tarafından doğrulandı.",
                    "approved-test-hash", "approved-test-hash", true);
        }

        XaiRequestContext(String eserId, boolean cliConfirmed, boolean dryRun,
                          boolean sourceApproved, BigDecimal budgetUsd,
                          String sourceType, String sourceLicenseNote,
                          String approvedSourceHash, String currentSourceHash) {
            this(eserId, cliConfirmed, dryRun, sourceApproved, budgetUsd,
                    sourceType, sourceLicenseNote, approvedSourceHash, currentSourceHash, true);
        }

        static XaiRequestContext mock() {
            return new XaiRequestContext("ESER-00005", false, true, false, BigDecimal.ONE,
                    TtsAbSourceType.FIXTURE.name(), "Test fixture; ticari lisans kanıtı değildir.",
                    "", "", false);
        }
    }

    record XaiUretimDetayi(Path file, String requestHash, String sha256, String format,
                           long fileSize, long elapsedMs, int retryCount,
                           BigDecimal estimatedCostUsd, boolean reused, String liveOrMock) {
    }

    record XaiHttpResponse(int statusCode, String contentType, byte[] body, Map<String, String> headers) {
    }

    @FunctionalInterface
    interface XaiHttpTransport {
        XaiHttpResponse send(URI endpoint, String apiKey, String jsonBody,
                             String accept, Duration timeout) throws Exception;
    }

    @FunctionalInterface
    interface XaiSleeper {
        void sleep(long millis) throws InterruptedException;
    }

    static final class JavaHttpTransport implements XaiHttpTransport {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30)).build();

        @Override
        public XaiHttpResponse send(URI endpoint, String apiKey, String jsonBody,
                                    String accept, Duration timeout) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", accept)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return new XaiHttpResponse(response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(""),
                    response.body(), Map.of());
        }
    }

    static final class XaiTtsException extends RuntimeException {
        private final String code;
        private final int httpStatus;

        XaiTtsException(String code, int httpStatus, String message) {
            super(message);
            this.code = code;
            this.httpStatus = httpStatus;
        }

        String code() { return code; }
        int httpStatus() { return httpStatus; }
    }
}
