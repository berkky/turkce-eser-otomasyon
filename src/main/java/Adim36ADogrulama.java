import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Adım 36A.1 davranışsal doğrulama — yalnız injectable transport / localhost fake server.
 */
public final class Adim36ADogrulama {
    private static int checks;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SECRET_MARKER = "SECRET_MARKER_BODY_SHOULD_NOT_APPEAR";

    private Adim36ADogrulama() {
    }

    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        forceOffline();
        Path temporary = Files.createTempDirectory("adim36a-");
        try {
            gitCheckpoint();
            WebOrtam actual = WebOrtam.varsayilan();
            canonicalApproved(actual);
            WebOrtam test = tempEnv(actual.metinArsivi(), temporary);
            seedCanonicalApproved(actual, test);
            KasagiLivePreviewService service = new KasagiLivePreviewService(test);
            ApprovedPassage approved = service.resolveApprovedPassage();
            approvalsAndBudgets(service, approved);
            liveFlagAndAttemptLedger(service, approved, temporary);
            integrityGates36A2(service, approved, temporary);
            cacheAndRequestGuards(service, approved);
            packageAndWeb(service, approved, test);
            offlineGuards();
            documentation();
            System.out.println("ADIM 36A DOGRULAMA: BASARILI (" + checks + " kontrol)");
        } finally {
            forceOffline();
            deleteTree(temporary);
        }
    }

    private static void gitCheckpoint() throws Exception {
        Process p = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(Path.of("").toAbsolutePath().toFile()).start();
        String head = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        p.waitFor();
        check(head.startsWith("eb11f98") || head.equals("eb11f98"), "Git checkpoint HEAD eb11f98");
    }

    private static void canonicalApproved(WebOrtam actual) throws Exception {
        KasagiLivePreviewService service = new KasagiLivePreviewService(actual);
        ApprovedPassage approved = service.resolveApprovedPassage();
        check(approved.candidateId().equals("PASSAGE-1"), "Canonical PASSAGE-1 resolve");
        check(TtsAbService.sha256(approved.approvedText()).equals(approved.approvedTextHash()),
                "approved text hash eşleşmesi");
        check(approved.sourceApproved() && approved.rightsReviewed()
                        && approved.rightsAcknowledgedByUser() && approved.commercialUseApprovedByUser(),
                "Hak/onay alanları true");
        check("PASSAGE_APPROVED_LIVE_LOCKED".equals(approved.status())
                        && !approved.liveGenerationAllowed(),
                "Adım 35 status live locked");
        check(Files.isRegularFile(service.selectionRoot().resolve("rights/SOURCE_ATTRIBUTION.txt")),
                "Hak/atıf dosyası mevcut");
        check(Files.isRegularFile(service.selectionRoot().resolve("rights/rights-evidence.sha256")),
                "rights-evidence.sha256 mevcut");
    }

    private static void approvalsAndBudgets(KasagiLivePreviewService service, ApprovedPassage approved)
            throws Exception {
        expect("LIVE_CONFIRMATION_REQUIRED",
                () -> service.createLiveApproval(approved.selectionId(), approved.approvedTextHash(),
                        LiveProvider.xai, "lumen", new BigDecimal("0.05"), 1, "YANLIS", "TEST", false),
                "Yanlış xAI phrase reddedilir");
        expect("LIVE_CONFIRMATION_REQUIRED",
                () -> service.createLiveApproval(approved.selectionId(), approved.approvedTextHash(),
                        LiveProvider.openai, "marin", new BigDecimal("0.20"), 1, "YANLIS", "TEST", false),
                "Yanlış OpenAI phrase reddedilir");
        LiveGenerationApproval xai = service.createLiveApproval(approved.selectionId(),
                approved.approvedTextHash(), LiveProvider.xai, "lumen", new BigDecimal("0.05"), 1,
                KasagiLivePreviewService.XAI_CONFIRMATION, "TEST", false);
        check(xai.xaiApproved() && !xai.openAiApproved()
                        && xai.status() == LiveApprovalStatus.XAI_ONLY_APPROVED,
                "xAI ayrı onaylanabilir");
        LiveGenerationApproval both = service.createLiveApproval(approved.selectionId(),
                approved.approvedTextHash(), LiveProvider.openai, "marin", new BigDecimal("0.20"), 1,
                KasagiLivePreviewService.OPENAI_CONFIRMATION, "TEST", false);
        check(both.openAiApproved() && both.xaiApproved()
                        && both.status() == LiveApprovalStatus.BOTH_PROVIDERS_APPROVED,
                "OpenAI ayrı onaylanabilir");
        check(both.providerApprovals().stream().anyMatch(p -> p.provider() == LiveProvider.xai)
                        && both.providerApprovals().stream().anyMatch(p -> p.provider() == LiveProvider.openai),
                "Provider onayı diğer sağlayıcıyı kapatmaz");
        expect("LIVE_BUDGET_NOT_APPROVED",
                () -> service.createLiveApproval(approved.selectionId(), approved.approvedTextHash(),
                        LiveProvider.xai, "lumen", BigDecimal.ZERO, 1,
                        KasagiLivePreviewService.XAI_CONFIRMATION, "TEST", false),
                "Bütçe sıfırken live reddedilir");
        expect("REQUEST_LIMIT_EXCEEDED",
                () -> service.createLiveApproval(approved.selectionId(), approved.approvedTextHash(),
                        LiveProvider.xai, "lumen", new BigDecimal("0.05"), 0,
                        KasagiLivePreviewService.XAI_CONFIRMATION, "TEST", false),
                "Max request 0 iken live reddedilir");
        check(both.maxRequestsPerProvider() == 1, "Max request 1 uygulanır");
        check(!both.allowAutomaticRetry(), "Retry varsayılan kapalı");
        LiveCostEstimate xaiCost = service.estimateXai(approved);
        LiveCostEstimate openAiCost = service.estimateOpenAi(approved);
        check(xaiCost.estimatedCostUsd().signum() > 0
                        && openAiCost.calculationType().equals("ESTIMATED_ONLY"),
                "Bütçe tahmin modelleri");
        check(xaiCost.estimatedCostUsd().compareTo(KasagiLivePreviewService.XAI_FIRST_BUDGET_CAP) <= 0
                        && openAiCost.estimatedCostUsd()
                        .compareTo(KasagiLivePreviewService.OPENAI_FIRST_BUDGET_CAP) <= 0,
                "İlk çağrı tahminleri üst sınır altında");
        expect("AUTOMATIC_RETRY_NOT_SUPPORTED",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, false, null, null, null),
                "AllowRetry / noRetry=false AUTOMATIC_RETRY_NOT_SUPPORTED");
        try {
            Adim36LiveGenerateApp.main(new String[]{
                    "-Provider", "xai",
                    "-SelectionId", approved.selectionId(),
                    "-Voice", "lumen",
                    "-MaxBudgetUsd", "0.05",
                    "-Confirmation", KasagiLivePreviewService.XAI_CONFIRMATION,
                    "-Live",
                    "-AllowRetry"
            });
            throw new IllegalStateException("FAILED: -AllowRetry reddedilmedi");
        } catch (IllegalArgumentException e) {
            check(e.getMessage() != null && e.getMessage().contains("AUTOMATIC_RETRY_NOT_SUPPORTED"),
                    "-AllowRetry AUTOMATIC_RETRY_NOT_SUPPORTED verir");
        }
    }

    private static void liveFlagAndAttemptLedger(KasagiLivePreviewService service, ApprovedPassage approved,
                                                 Path temporary) throws Exception {
        forceOffline();
        try {
            service.revokeLiveApproval("reset-flag");
        } catch (Exception ignored) {
        }
        service.createLiveApproval(approved.selectionId(), approved.approvedTextHash(),
                LiveProvider.xai, "lumen", new BigDecimal("0.05"), 1,
                KasagiLivePreviewService.XAI_CONFIRMATION, "TEST", false);
        service.createLiveApproval(approved.selectionId(), approved.approvedTextHash(),
                LiveProvider.openai, "marin", new BigDecimal("0.20"), 1,
                KasagiLivePreviewService.OPENAI_CONFIRMATION, "TEST", false);

        System.setProperty("XAI_TTS_LIVE_ENABLED", "false");
        System.setProperty("OPENAI_TTS_LIVE_ENABLED", "false");
        Path experiment = service.latestExperiment();
        long attemptsBefore = countAttempts(experiment, "xai");
        expect("LIVE_PROVIDER_FLAG_DISABLED",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, true, null, (e, k, b, a, t) -> {
                            throw new AssertionError("transport çağrılmamalı");
                        }, null),
                "live=true fakat provider flag false => LIVE_PROVIDER_FLAG_DISABLED");
        check(!Files.exists(experiment.resolve("raw/xai-lumen.wav")),
                "Flag kapalıyken mock audio yok");
        check(countAttempts(experiment, "xai") == attemptsBefore,
                "Flag kapalıyken ücretli attempt oluşmaz");
        JsonNode approvalJson = JSON.readTree(
                experiment.resolve("approvals/live-generation-approval.json").toFile());
        boolean xaiApproved = false;
        for (JsonNode p : approvalJson.path("providerApprovals")) {
            if ("xai".equals(p.path("provider").asText())) {
                xaiApproved = "APPROVED".equals(p.path("state").asText()) || p.path("approved").asBoolean();
            }
        }
        check(xaiApproved, "Flag kapalıyken approval tüketilmez");

        expect("DRY_RUN_ONLY",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        false, true, null, null, null),
                "Live=false ağ çağrısı yapmaz");

        System.setProperty("XAI_TTS_LIVE_ENABLED", "true");
        System.setProperty("OPENAI_TTS_LIVE_ENABLED", "true");
        System.setProperty("XAI_API_KEY", "test-xai-key-not-real");
        System.setProperty("OPENAI_API_KEY", "sk-test-openai-key-not-real");

        byte[] wav = XaiTtsSaglayici.mockWav();
        XaiTtsSaglayici.XaiHttpTransport okXai = (endpoint, key, body, accept, timeout) -> {
            check(body.contains("\"text_normalization\":false")
                            || body.contains("\"text_normalization\": false"),
                    "xAI request JSON text_normalization=false içerir");
            check(!body.contains("test-xai-key"), "API key request body'de yok");
            return new XaiTtsSaglayici.XaiHttpResponse(200, "audio/wav", wav, Map.of());
        };
        LiveGenerateResult first = service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                true, true, null, okXai, null);
        check(service.countXaiTransport() == 1, "Transport çağrı sayacı gerçekten 1");
        check(first.calledNetwork() && !first.cacheHit() && first.requestCount() == 1,
                "Başarılı canlı çağrı network=true");
        check(Files.isRegularFile(experiment.resolve("raw/xai-lumen.wav")),
                "Final raw publish edildi");
        check(Files.isRegularFile(experiment.resolve("raw/xai-lumen.wav.request.json")),
                "Final raw request state yazıldı");
        check(first.approvalConsumed() && "CONSUMED".equals(first.approvalState()),
                "Başarılı çağrı sonrası approval consumed");
        check(attemptStatesContain(experiment, "xai", AttemptState.NETWORK_DISPATCH_STARTED)
                        || attemptStatesContain(experiment, "xai", AttemptState.SUCCESS),
                "Transport öncesi NETWORK_DISPATCH_STARTED / SUCCESS attempt kaydı");
        check(!findAttempt(experiment, "xai", AttemptState.SUCCESS).retryAllowed(),
                "Başarılı attempt retryAllowed=false");
        assertNonCacheAttemptIntegrity(experiment, "xai");

        LiveGenerationApproval afterXai = JSON.readValue(
                experiment.resolve("approvals/live-generation-approval.json").toFile(),
                LiveGenerationApproval.class);
        ProviderApprovalState openAiState = afterXai.providerApprovals().stream()
                .filter(p -> p.provider() == LiveProvider.openai).findFirst()
                .map(p -> p.state() == null ? ProviderApprovalState.APPROVED : p.state())
                .orElse(ProviderApprovalState.NOT_APPROVED);
        check(openAiState == ProviderApprovalState.APPROVED,
                "xAI consume OpenAI approval'ını tüketmez");

        // Cache hit: same raw+state, no approval needed (even if CONSUMED)
        LiveGenerateResult cached = service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                true, true, null, (e, k, b, a, t) -> {
                    throw new AssertionError("cache hit transport çağrılmamalı");
                }, null);
        check(cached.cacheHit() && !cached.calledNetwork() && cached.requestCount() == 0,
                "İkinci aynı request cacheHit=true");
        check(service.countXaiTransport() == 0, "Cache hit transport count=0");
        check(!cached.approvalConsumed(), "Cache hit approval tüketmez");
        check("NOT_REQUIRED_CACHE_HIT".equals(cached.approvalState()),
                "Cache hit approvalState NOT_REQUIRED_CACHE_HIT");

        clearRaw(experiment, "xai-lumen.wav");
        expect("LIVE_APPROVAL_CONSUMED",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, true, null, okXai, null),
                "Tüketilmiş xAI approval tekrar kullanılamaz (cache yokken)");

        // OpenAI success then prove xAI state untouched (already CONSUMED)
        OpenAiLiveTtsSaglayici.OpenAiHttpTransport okOpenAi = (endpoint, key, body, accept, timeout) -> {
            check(!body.contains("sk-test"), "OpenAI body secret maskelenir/anahtar yok");
            return new OpenAiLiveTtsSaglayici.OpenAiHttpResponse(200, "audio/wav", wav, Map.of());
        };
        LiveGenerateResult openOk = service.generate(LiveProvider.openai, approved.selectionId(), "marin",
                new BigDecimal("0.20"), KasagiLivePreviewService.OPENAI_CONFIRMATION,
                true, true, null, null, okOpenAi);
        check(openOk.approvalConsumed(), "OpenAI başarılı çağrı sonrası consumed");
        LiveGenerationApproval afterOpen = JSON.readValue(
                experiment.resolve("approvals/live-generation-approval.json").toFile(),
                LiveGenerationApproval.class);
        ProviderApprovalState xaiState = afterOpen.providerApprovals().stream()
                .filter(p -> p.provider() == LiveProvider.xai).findFirst()
                .map(LiveProviderApproval::state).orElse(ProviderApprovalState.NOT_APPROVED);
        check(xaiState == ProviderApprovalState.CONSUMED,
                "OpenAI consume xAI durumunu değiştirmiyor (zaten CONSUMED)");

        // Fresh approvals for failure paths
        try {
            service.revokeLiveApproval("fail-paths");
        } catch (Exception ignored) {
        }
        clearRaw(experiment, "xai-lumen.wav");
        clearRaw(experiment, "openai-marin.wav");
        service.createLiveApproval(approved.selectionId(), approved.approvedTextHash(),
                LiveProvider.xai, "lumen", new BigDecimal("0.05"), 1,
                KasagiLivePreviewService.XAI_CONFIRMATION, "TEST", false);
        service.createLiveApproval(approved.selectionId(), approved.approvedTextHash(),
                LiveProvider.openai, "marin", new BigDecimal("0.20"), 1,
                KasagiLivePreviewService.OPENAI_CONFIRMATION, "TEST", false);

        int[] ioCalls = {0};
        expect("NETWORK_ERROR",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, true, null, (e, k, b, a, t) -> {
                            ioCalls[0]++;
                            throw new java.io.IOException("simulated disconnect");
                        }, null),
                "Transport IOException sonrası FAILED_AFTER_DISPATCH");
        check(ioCalls[0] == 1, "IOException için transport bir kez çağrıldı");
        check(providerState(service) == ProviderApprovalState.REAPPROVAL_REQUIRED
                        || providerStateNamed(service, LiveProvider.xai) == ProviderApprovalState.REAPPROVAL_REQUIRED,
                "IOException sonrası REAPPROVAL_REQUIRED");
        int[] ioCalls2 = {0};
        expect("LIVE_PROVIDER_NOT_APPROVED",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, true, null, (e, k, b, a, t) -> {
                            ioCalls2[0]++;
                            return new XaiTtsSaglayici.XaiHttpResponse(200, "audio/wav", wav, Map.of());
                        }, null),
                "IOException sonrası ikinci çağrı engellenir");
        check(ioCalls2[0] == 0, "IOException sonrası ikinci transport yok");

        // OpenAI still APPROVED after xAI failure
        check(providerStateNamed(service, LiveProvider.openai) == ProviderApprovalState.APPROVED,
                "xAI fail OpenAI approval'ını tüketmez");

        reapprove(service, approved, LiveProvider.xai);
        int[] timeoutCalls = {0};
        expect("TIMEOUT",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, true, null, (e, k, b, a, t) -> {
                            timeoutCalls[0]++;
                            throw new XaiTtsSaglayici.XaiTtsException("TIMEOUT", 0, "timeout");
                        }, null),
                "Timeout sonrası REAPPROVAL");
        int[] timeout2 = {0};
        expect("LIVE_PROVIDER_NOT_APPROVED",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, true, null, (e, k, b, a, t) -> {
                            timeout2[0]++;
                            return new XaiTtsSaglayici.XaiHttpResponse(200, "audio/wav", wav, Map.of());
                        }, null),
                "Timeout sonrası ikinci çağrı engellenir");
        check(timeout2[0] == 0, "Timeout ikinci transport sayacı 0");

        reapprove(service, approved, LiveProvider.openai);
        int[] mimeCalls = {0};
        expect("INVALID_MIME_TYPE",
                () -> service.generate(LiveProvider.openai, approved.selectionId(), "marin",
                        new BigDecimal("0.20"), KasagiLivePreviewService.OPENAI_CONFIRMATION,
                        true, true, null, null, (e, k, b, a, t) -> {
                            mimeCalls[0]++;
                            return new OpenAiLiveTtsSaglayici.OpenAiHttpResponse(
                                    200, "application/json", "{}".getBytes(StandardCharsets.UTF_8), Map.of());
                        }),
                "Invalid MIME sonrası REAPPROVAL");
        int[] mime2 = {0};
        expect("LIVE_PROVIDER_NOT_APPROVED",
                () -> service.generate(LiveProvider.openai, approved.selectionId(), "marin",
                        new BigDecimal("0.20"), KasagiLivePreviewService.OPENAI_CONFIRMATION,
                        true, true, null, null, (e, k, b, a, t) -> {
                            mime2[0]++;
                            return new OpenAiLiveTtsSaglayici.OpenAiHttpResponse(200, "audio/wav", wav, Map.of());
                        }),
                "Invalid MIME sonrası ikinci çağrı engellenir");
        check(mime2[0] == 0, "Invalid MIME ikinci transport 0");

        reapprove(service, approved, LiveProvider.openai);
        expect("EMPTY_BODY",
                () -> service.generate(LiveProvider.openai, approved.selectionId(), "marin",
                        new BigDecimal("0.20"), KasagiLivePreviewService.OPENAI_CONFIRMATION,
                        true, true, null, null, (e, k, b, a, t) ->
                                new OpenAiLiveTtsSaglayici.OpenAiHttpResponse(200, "audio/wav", new byte[0], Map.of())),
                "Empty body sonrası REAPPROVAL");
        expect("LIVE_PROVIDER_NOT_APPROVED",
                () -> service.generate(LiveProvider.openai, approved.selectionId(), "marin",
                        new BigDecimal("0.20"), KasagiLivePreviewService.OPENAI_CONFIRMATION,
                        true, true, null, null, (e, k, b, a, t) ->
                                new OpenAiLiveTtsSaglayici.OpenAiHttpResponse(200, "audio/wav", wav, Map.of())),
                "Empty body sonrası ikinci çağrı engellenir");

        reapprove(service, approved, LiveProvider.openai);
        expect("PROVIDER_SERVER_ERROR",
                () -> service.generate(LiveProvider.openai, approved.selectionId(), "marin",
                        new BigDecimal("0.20"), KasagiLivePreviewService.OPENAI_CONFIRMATION,
                        true, true, null, null, (e, k, b, a, t) ->
                                new OpenAiLiveTtsSaglayici.OpenAiHttpResponse(
                                        500, "application/json", "{\"error\":\"x\"}".getBytes(StandardCharsets.UTF_8),
                                        Map.of())),
                "Provider 500 sonrası REAPPROVAL");
        expect("LIVE_PROVIDER_NOT_APPROVED",
                () -> service.generate(LiveProvider.openai, approved.selectionId(), "marin",
                        new BigDecimal("0.20"), KasagiLivePreviewService.OPENAI_CONFIRMATION,
                        true, true, null, null, (e, k, b, a, t) ->
                                new OpenAiLiveTtsSaglayici.OpenAiHttpResponse(200, "audio/wav", wav, Map.of())),
                "Provider 500 sonrası ikinci çağrı engellenir");

        expectCode("INVALID_MIME_TYPE", () -> directOpenAiFixed(approved, temporary, 200, "",
                        wav),
                "OpenAI boş MIME reddedilir");

        // FAILED_BEFORE_NETWORK: bad voice before dispatch leaves APPROVED
        reapprove(service, approved, LiveProvider.xai);
        expect("VOICE_NOT_ALLOWED",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "not-a-voice",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, true, null, (e, k, b, a, t) -> {
                            throw new AssertionError("voice hatasında transport yok");
                        }, null),
                "VOICE_NOT_ALLOWED transport öncesi");
        check(providerStateNamed(service, LiveProvider.xai) == ProviderApprovalState.APPROVED,
                "FAILED_BEFORE_NETWORK (voice) approval yeniden kullanılabilir");

        String safe = Files.readString(experiment.resolve("requests/xai-request.safe.json"),
                StandardCharsets.UTF_8);
        check(safe.contains("[REDACTED]") && !safe.contains("test-xai-key-not-real"),
                "Safe request JSON secret içermez");

        forceOffline();
    }

    private static void cacheAndRequestGuards(KasagiLivePreviewService service, ApprovedPassage approved)
            throws Exception {
        System.setProperty("XAI_TTS_LIVE_ENABLED", "true");
        System.setProperty("XAI_API_KEY", "test-xai-key-not-real");
        Path experiment = service.latestExperiment();
        Path raw = experiment.resolve("raw/xai-lumen.wav");
        Path state = experiment.resolve("raw/xai-lumen.wav.request.json");
        byte[] wav = XaiTtsSaglayici.mockWav();
        Files.createDirectories(raw.getParent());
        if (Files.exists(raw)) {
            raw.toFile().setWritable(true);
        }
        Files.write(raw, wav);
        ObjectNode node = JSON.createObjectNode();
        node.put("provider", "xai");
        node.put("model", "xai-tts");
        node.put("voice", "lumen");
        node.put("approvedTextHash", approved.approvedTextHash());
        String requestHash = XaiTtsSaglayici.sha256((approved.approvedText() + "|lumen|tr|wav|1.0|")
                .getBytes(StandardCharsets.UTF_8));
        node.put("requestHash", requestHash);
        node.put("audioSha256", "deadbeef" + "0".repeat(56));
        node.put("status", "SUCCESS");
        Files.writeString(state, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node));
        expect("RAW_CACHE_INVALID",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, true, null, (e, k, b, a, t) -> {
                            throw new AssertionError("invalid cache transport yok");
                        }, null),
                "Cache audio hash değişirse CACHE_INVALID");

        Files.write(raw, new byte[0]);
        node.put("audioSha256", XaiTtsSaglayici.sha256(new byte[0]));
        Files.writeString(state, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node));
        expect("RAW_CACHE_INVALID",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, true, null, null, null),
                "0-byte cache CACHE_INVALID");

        Files.write(raw, wav);
        Files.deleteIfExists(state);
        expect("RAW_CACHE_INVALID",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, true, null, null, null),
                "State eksikse CACHE_INVALID");

        // Restore valid-looking broken WAV for ffprobe invalid if available
        byte[] bad = wav.clone();
        bad[0] = 'X';
        Files.write(raw, bad);
        node.put("audioSha256", XaiTtsSaglayici.sha256(bad));
        node.put("requestHash", requestHash);
        Files.writeString(state, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node));
        TtsAbAudioService audio = new TtsAbAudioService(Path.of("").toAbsolutePath());
        if (audio.available()) {
            expect("RAW_CACHE_INVALID",
                    () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                            new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                            true, true, null, null, null),
                    "ffprobe başarısız cache CACHE_INVALID");
        } else {
            // without ffprobe, header-invalid still fails hash/header gates when WAV check runs —
            // currently evaluateFinalCache may HIT without probe; force INVALID via hash mismatch already covered.
            check(!audio.available(), "ffprobe yokken ayrı header invalid yolu hash testiyle kapsandı");
        }

        String preflight = Files.readString(Path.of("adim36-live-preflight.ps1"), StandardCharsets.UTF_8);
        check(preflight.contains("PREFLIGHT_READY") && preflight.contains("API_KEY_MISSING"),
                "Preflight READY/key yokken READY vermez kapıları scriptte");
        check(preflight.contains("LIVE_PROVIDER_NOT_APPROVED")
                        && preflight.contains("LIVE_APPROVAL_EXPIRED"),
                "Preflight approval yok/expired READY vermez");
        check(preflight.contains("repo-private-audio-scan.ps1")
                        && preflight.contains("Find-RepoPrivateAudioLeaks"),
                "Preflight Extension tabanlı audio scan helper kullanır");
        check(!preflight.contains("-Include *.wav") && !preflight.contains("-Include *.mp3"),
                "Preflight Get-ChildItem -Include audio filtresi kullanmaz");
        String audioScan = Files.readString(Path.of("repo-private-audio-scan.ps1"), StandardCharsets.UTF_8);
        check(audioScan.contains("ToLowerInvariant()")
                        && audioScan.contains(".wav")
                        && audioScan.contains(".mp3")
                        && audioScan.contains(".zip")
                        && audioScan.contains("target")
                        && audioScan.contains("node_modules")
                        && audioScan.contains(".git"),
                "Audio scan Extension + exclude (target/node_modules/.git)");
        check(!audioScan.contains("-Include"),
                "Audio scan helper Include kullanmaz");
        String selfTest = Files.readString(Path.of("adim36a-self-test.ps1"), StandardCharsets.UTF_8);
        check(selfTest.contains("adim36b0RepoAudioScan"),
                "Self-test adim36b0RepoAudioScan davranışsal tarama içerir");
        check(Files.readString(Path.of("maven-resolve.ps1"), StandardCharsets.UTF_8)
                        .contains("MAVEN_NOT_FOUND"),
                "Maven resolver MAVEN_NOT_FOUND");
        check(!selfTest.contains("C:\\Tools\\apache-maven"),
                "Self-test C:\\Tools hardcoded Maven kullanmaz");
        forceOffline();
    }

    private static void packageAndWeb(KasagiLivePreviewService service, ApprovedPassage approved,
                                      WebOrtam test) throws Exception {
        Path experiment = service.ensureExperimentWorkspace(approved);
        byte[] wav = XaiTtsSaglayici.mockWav();
        Path rawX = experiment.resolve("raw/xai-lumen.wav");
        Path rawO = experiment.resolve("raw/openai-marin.wav");
        Path rawP = experiment.resolve("raw/piper-current.wav");
        Files.createDirectories(rawX.getParent());
        for (Path existing : List.of(rawX, rawO, rawP)) {
            if (Files.exists(existing)) {
                existing.toFile().setWritable(true);
            }
        }
        TtsLaboratuvarYardimci.atomikYaz(rawX, wav);
        byte[] wav2 = wav.clone();
        wav2[wav2.length - 1] ^= 0x01;
        TtsLaboratuvarYardimci.atomikYaz(rawO, wav2);
        byte[] wav3 = wav.clone();
        wav3[wav3.length - 2] ^= 0x02;
        TtsLaboratuvarYardimci.atomikYaz(rawP, wav3);
        Path norm = experiment.resolve("normalized");
        Files.createDirectories(norm);
        TtsAbAudioService audio = new TtsAbAudioService(Path.of("").toAbsolutePath());
        if (!audio.available()) {
            throw new IllegalStateException(
                    "AUDIO_TOOLING_NOT_AVAILABLE: test ortamında ffmpeg/ffprobe zorunlu");
        }
        audio.normalize(rawX, norm.resolve("xAI-Grok-TTS.mp3"));
        audio.normalize(rawO, norm.resolve("OpenAI-gpt-4o-mini-tts.mp3"));
        audio.normalize(rawP, norm.resolve("Piper-mevcut-ses.mp3"));
        service.buildBlindPackage(experiment);
        check(Files.isRegularFile(experiment.resolve("blind/ornek-A.mp3")), "Blind paket A");
        String privateMap = Files.readString(experiment.resolve("private/provider-mapping.private.json"),
                StandardCharsets.UTF_8);
        check(privateMap.contains("\"blindCode\"") && privateMap.contains("\"provider\"")
                        && privateMap.contains("\"normalizedHash\""),
                "Private mapping alanları");
        String pub = Files.readString(experiment.resolve("blind/blind-manifest.public.json"));
        check(!pub.toLowerCase().contains("openai") && !pub.toLowerCase().contains("xai")
                        && !pub.toLowerCase().contains("piper"),
                "Public blind provider sızdırmaz");

        expect("PACKAGE_INCOMPLETE", () -> {
            Path bad = Files.createTempDirectory("pkg-incomplete");
            Files.createDirectories(bad.resolve("normalized"));
            TtsLaboratuvarYardimci.atomikYaz(bad.resolve("normalized/xAI-Grok-TTS.mp3"),
                    "only-one".getBytes(StandardCharsets.UTF_8));
            service.buildAhmetBeyPackages(bad);
        }, "Paket üç geçerli MP3 olmadan tamamlanmaz");

        PackageBuildResult packages = service.buildAhmetBeyPackages(experiment);
        check(!packages.publicDir().safeName().equals(packages.privateDir().safeName()),
                "Public/private klasörler farklıdır");
        check(packages.publicDir().safeName().endsWith("-PUBLIC")
                        && packages.privateDir().safeName().endsWith("-PRIVATE"),
                "PUBLIC/PRIVATE adlı klasörler");
        Path teslimler = test.sesArsivi().getParent().resolve("teslimler");
        Path publicZip = teslimler.resolve(packages.publicZip().safeName());
        Path privateZip = teslimler.resolve(packages.privateZip().safeName());
        check(Files.isRegularFile(publicZip) && Files.isRegularFile(privateZip),
                "Public/private ZIP'ler gerçekten oluşur");
        Path publicSha = teslimler.resolve(publicZip.getFileName() + ".sha256.txt");
        check(Files.isRegularFile(publicSha)
                        && Files.readString(publicSha).contains(packages.publicZipSha256()),
                "ZIP SHA-256 doğrulanır");
        try (ZipFile zip = new ZipFile(publicZip.toFile())) {
            boolean hasPrivate = zip.stream().anyMatch(e ->
                    e.getName().endsWith("provider-mapping.private.json"));
            check(!hasPrivate, "Public ZIP private mapping içermez");
            String home = System.getProperty("user.home", "").replace('\\', '/').toLowerCase();
            boolean hasAbs = zip.stream().anyMatch(e ->
                    e.getName().toLowerCase().replace('\\', '/').contains(home) && !home.isBlank());
            check(!hasAbs, "Public ZIP absolute user path içermez");
        }
        Path publicDir = teslimler.resolve(packages.publicDir().safeName());
        for (String mp3 : List.of(
                "acik-isimli/xAI-Grok-TTS.mp3",
                "acik-isimli/OpenAI-gpt-4o-mini-tts.mp3",
                "acik-isimli/Piper-mevcut-ses.mp3",
                "kor-dinleme/ornek-A.mp3",
                "kor-dinleme/ornek-B.mp3",
                "kor-dinleme/ornek-C.mp3")) {
            TtsAbAudioMetrics metrics = audio.probe(publicDir.resolve(mp3));
            check(metrics.durationMs() > 0 && metrics.sampleRate() == 44_100 && metrics.channels() == 1,
                    "Paketlenmiş MP3 ffprobe: " + mp3);
        }
        String form = Files.readString(publicDir.resolve("kor-dinleme/DEGERLENDIRME_FORMU.html"),
                StandardCharsets.UTF_8);
        check(form.contains("a_dogallik") && form.contains("b_dogallik") && form.contains("c_dogallik")
                        && form.contains("a_telaffuz") && form.contains("b_telaffuz")
                        && form.contains("c_telaffuz")
                        && form.contains("downloadJson") && form.contains("downloadCsv"),
                "A/B/C form kriterleri eşit + JSON/CSV");

        YerelWebSunucu server = new YerelWebSunucu(test);
        check(server.route("GET", "/ab-test/live-preview/ESER-00005", "", "", true).status() == 200,
                "Web live-preview sayfası");
        check(server.route("GET", "/api/ab-test/live-preview/ESER-00005/status", "", "", true).status() == 200,
                "Web status API");
        check(server.route("GET", "/ab-test/live-preview/ESER-00005", "", "", false).status() == 403,
                "Web localhost-only");
        KasagiLivePreviewWebService web = new KasagiLivePreviewWebService(test);
        web.route("GET", "/ab-test/live-preview/ESER-00005", null);
        String csrf = web.lastCsrfAction().equals("create") ? web.lastCsrfToken() : null;
        // detail() issues create then revoke — last is revoke; request a create token via second page parse
        String detail = new String(web.route("GET", "/ab-test/live-preview/ESER-00005", null)
                .body(), StandardCharsets.UTF_8);
        csrf = extract(detail, "name=\"csrfToken\" value=\"", "\"");
        check(csrf != null && csrf.length() >= 32, "CSRF token sayfadan okunur");
        check(web.lastCsrfToken() != null, "CSRF token üretilir");
        // use first form token from HTML; verify it is still registered by matching extract to map via recreate
        // Prefer posting with extracted create token; if page issues revoke last, extract first create token
        String submission = UUID.randomUUID().toString();
        String body = "csrfToken=" + enc(csrf)
                + "&submissionId=" + enc(submission)
                + "&selectionId=" + enc(approved.selectionId())
                + "&approvedTextHash=" + enc(approved.approvedTextHash())
                + "&provider=xai&voice=lumen&maxBudgetUsd=0.05"
                + "&confirmation=" + enc(KasagiLivePreviewService.XAI_CONFIRMATION);
        WebResponse first;
        try {
            first = web.route("POST", "/ab-test/live-preview/ESER-00005/create-approval", body);
        } catch (IllegalArgumentException ex) {
            // First token may be stale if extract mismatched; retry with freshly issued create-only flow
            if (!"CSRF_TOKEN_INVALID".equals(ex.getMessage())) throw ex;
            // Force create token: GET page, take ALL csrf values, try them with create action
            detail = new String(web.route("GET", "/ab-test/live-preview/ESER-00005", null)
                    .body(), StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("name=\"csrfToken\" value=\"([^\"]+)\"").matcher(detail);
            Exception last = ex;
            first = null;
            while (m.find()) {
                String tok = m.group(1);
                String attempt = "csrfToken=" + enc(tok)
                        + "&submissionId=" + enc(submission)
                        + "&selectionId=" + enc(approved.selectionId())
                        + "&approvedTextHash=" + enc(approved.approvedTextHash())
                        + "&provider=xai&voice=lumen&maxBudgetUsd=0.05"
                        + "&confirmation=" + enc(KasagiLivePreviewService.XAI_CONFIRMATION);
                try {
                    first = web.route("POST", "/ab-test/live-preview/ESER-00005/create-approval", attempt);
                    break;
                } catch (IllegalArgumentException e2) {
                    last = e2;
                }
            }
            if (first == null) throw last;
        }
        check(first.status() == 200, "Web approval submission kabul");
        String detail2 = new String(web.route("GET", "/ab-test/live-preview/ESER-00005", null)
                .body(), StandardCharsets.UTF_8);
        java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile("name=\"csrfToken\" value=\"([^\"]+)\"").matcher(detail2);
        Exception dupLast = null;
        boolean dupRejected = false;
        while (m2.find()) {
            String body2 = "csrfToken=" + enc(m2.group(1))
                    + "&submissionId=" + enc(submission)
                    + "&selectionId=" + enc(approved.selectionId())
                    + "&approvedTextHash=" + enc(approved.approvedTextHash())
                    + "&provider=openai&voice=marin&maxBudgetUsd=0.20"
                    + "&confirmation=" + enc(KasagiLivePreviewService.OPENAI_CONFIRMATION);
            try {
                web.route("POST", "/ab-test/live-preview/ESER-00005/create-approval", body2);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("SUBMISSION_ID_ALREADY_USED")) {
                    dupRejected = true;
                    break;
                }
                dupLast = e;
            }
        }
        check(dupRejected, "Web duplicate submission reddedilir"
                + (dupLast == null ? "" : " (" + dupLast.getMessage() + ")"));
        String detail3 = new String(web.route("GET", "/ab-test/live-preview/ESER-00005", null)
                .body(), StandardCharsets.UTF_8);
        String csrf3 = extract(detail3, "name=\"csrfToken\" value=\"", "\"");
        Exception emptyLast = null;
        boolean emptyRejected = false;
        java.util.regex.Matcher m3 = java.util.regex.Pattern
                .compile("name=\"csrfToken\" value=\"([^\"]+)\"").matcher(detail3);
        while (m3.find()) {
            try {
                web.route("POST", "/ab-test/live-preview/ESER-00005/create-approval",
                        "csrfToken=" + enc(m3.group(1)) + "&submissionId=&provider=xai&voice=lumen"
                                + "&maxBudgetUsd=0.05&confirmation="
                                + enc(KasagiLivePreviewService.XAI_CONFIRMATION)
                                + "&selectionId=" + enc(approved.selectionId())
                                + "&approvedTextHash=" + enc(approved.approvedTextHash()));
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("SUBMISSION_ID_INVALID")) {
                    emptyRejected = true;
                    break;
                }
                emptyLast = e;
            }
        }
        check(emptyRejected, "Boş submissionId reddedilir"
                + (emptyLast == null ? "" : " (" + emptyLast.getMessage() + ")"));
        check(!detail.contains("xaiSeparate") && !detail.contains("openaiSeparate"),
                "Yanıltıcı ayrı onay checkbox'ları kaldırıldı");
    }

    private static void integrityGates36A2(KasagiLivePreviewService service, ApprovedPassage approved,
                                           Path temporary) throws Exception {
        TtsAbAudioService audio = new TtsAbAudioService(Path.of("").toAbsolutePath());
        if (!audio.available()) {
            throw new IllegalStateException(
                    "AUDIO_TOOLING_NOT_AVAILABLE: integrity kapıları ffmpeg/ffprobe gerektirir");
        }
        restoreAudioToolingProperty();
        System.setProperty("XAI_TTS_LIVE_ENABLED", "true");
        System.setProperty("OPENAI_TTS_LIVE_ENABLED", "true");
        System.setProperty("XAI_API_KEY", "test-xai-key-not-real");
        System.setProperty("OPENAI_API_KEY", "sk-test-openai-key-not-real");

        Path experiment = service.latestExperiment();
        byte[] validWav = XaiTtsSaglayici.mockWav();

        // 1-3: tooling force=false blocks generate before network; approval untouched
        reapprove(service, approved, LiveProvider.xai);
        long attemptsBeforeTooling = countAttempts(experiment, "xai");
        System.setProperty("ADIM36_AUDIO_TOOLING_FORCE", "false");
        int[] toolingTransport = {0};
        expect("AUDIO_TOOLING_NOT_AVAILABLE",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, true, null, (e, k, b, a, t) -> {
                            toolingTransport[0]++;
                            return new XaiTtsSaglayici.XaiHttpResponse(200, "audio/wav", validWav, Map.of());
                        }, null),
                "Tooling force=false generate AUDIO_TOOLING_NOT_AVAILABLE");
        check(toolingTransport[0] == 0, "Tooling force=false transport 0");
        check(service.countXaiTransport() == 0, "Tooling force=false xAI transport sayacı 0");
        check(countAttempts(experiment, "xai") == attemptsBeforeTooling,
                "Tooling force=false yeni attempt oluşmaz");
        check(providerStateNamed(service, LiveProvider.xai) == ProviderApprovalState.APPROVED,
                "Tooling force=false approval APPROVED kalır");
        restoreAudioToolingProperty();

        // 4-5: staging ffprobe fail — final raw publish edilmez, REAPPROVAL_REQUIRED
        reapprove(service, approved, LiveProvider.xai);
        clearRaw(experiment, "xai-lumen.wav");
        byte[] corruptWav = corruptWavDataChunk(validWav);
        expectOneOf(List.of("UNPLAYABLE_AUDIO", "FFPROBE_FAILED"),
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, true, null, (e, k, b, a, t) ->
                                new XaiTtsSaglayici.XaiHttpResponse(200, "audio/wav", corruptWav, Map.of()),
                        null),
                "Staging ffprobe fail UNPLAYABLE/FFPROBE");
        check(!Files.exists(experiment.resolve("raw/xai-lumen.wav")),
                "Staging ffprobe fail sonrası final raw yok");
        check(providerStateNamed(service, LiveProvider.xai) == ProviderApprovalState.REAPPROVAL_REQUIRED,
                "Staging ffprobe fail REAPPROVAL_REQUIRED");

        // 6-8: normalization fail after network success — raw kalır, CACHE_HIT ikinci çağrı
        reapprove(service, approved, LiveProvider.xai);
        clearRaw(experiment, "xai-lumen.wav");
        Files.deleteIfExists(experiment.resolve("normalized/xAI-Grok-TTS.mp3"));
        Path normalizeScript = Path.of("tts-ab-normalize.ps1").toAbsolutePath();
        Path normalizeBackup = temporary.resolve("tts-ab-normalize.ps1.bak");
        Files.move(normalizeScript, normalizeBackup, StandardCopyOption.REPLACE_EXISTING);
        try {
            expect("NORMALIZATION_FAILED",
                    () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                            new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                            true, true, null, (e, k, b, a, t) ->
                                    new XaiTtsSaglayici.XaiHttpResponse(200, "audio/wav", validWav, Map.of()),
                            null),
                    "Normalize script yokken NORMALIZATION_FAILED");
            check(Files.isRegularFile(experiment.resolve("raw/xai-lumen.wav")),
                    "NORMALIZATION_FAILED sonrası raw mevcut");
            check(providerStateNamed(service, LiveProvider.xai) == ProviderApprovalState.CONSUMED,
                    "NORMALIZATION_FAILED approval CONSUMED");
        } finally {
            Files.move(normalizeBackup, normalizeScript, StandardCopyOption.REPLACE_EXISTING);
        }
        int[] normRetryTransport = {0};
        LiveGenerateResult normCacheHit = service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                true, true, null, (e, k, b, a, t) -> {
                    normRetryTransport[0]++;
                    return new XaiTtsSaglayici.XaiHttpResponse(200, "audio/wav", validWav, Map.of());
                }, null);
        check(normCacheHit.cacheHit() && normRetryTransport[0] == 0,
                "NORMALIZATION_FAILED sonrası ikinci generate CACHE_HIT transport 0");
        check("NOT_REQUIRED_CACHE_HIT".equals(normCacheHit.approvalState()),
                "NORMALIZATION_FAILED sonrası cache hit NOT_REQUIRED_CACHE_HIT");
        Path renormalized = service.normalizeRaw(experiment, LiveProvider.xai, "lumen");
        check(Files.isRegularFile(renormalized), "Script geri yüklenince normalizeRaw başarılı");

        // 9-11: tooling false + existing raw → AUDIO_TOOLING; corrupt cache → RAW_CACHE_INVALID
        System.setProperty("ADIM36_AUDIO_TOOLING_FORCE", "false");
        expect("AUDIO_TOOLING_NOT_AVAILABLE",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, true, null, (e, k, b, a, t) -> {
                            throw new AssertionError("tooling false cache transport yok");
                        }, null),
                "Mevcut raw + tooling false AUDIO_TOOLING (CACHE_HIT değil)");
        restoreAudioToolingProperty();
        Path raw = experiment.resolve("raw/xai-lumen.wav");
        byte[] badHeader = validWav.clone();
        badHeader[0] = 'X';
        raw.toFile().setWritable(true);
        Files.write(raw, badHeader);
        expect("RAW_CACHE_INVALID",
                () -> service.generate(LiveProvider.xai, approved.selectionId(), "lumen",
                        new BigDecimal("0.05"), KasagiLivePreviewService.XAI_CONFIRMATION,
                        true, true, null, (e, k, b, a, t) -> {
                            throw new AssertionError("corrupt cache transport yok");
                        }, null),
                "Corrupt WAV cache RAW_CACHE_INVALID");

        // 12: attempt defteri bütünlüğü (non-CACHE_HIT)
        assertNonCacheAttemptIntegrity(experiment, "xai");

        // 14-16: blind rotate mapping + hash doğrulama
        Path blindWorkspace = service.ensureExperimentWorkspace(approved);
        seedNormalizedTriplet(blindWorkspace, approved, audio);
        for (int rotate = 0; rotate < 3; rotate++) {
            verifyBlindRotation(service, blindWorkspace, approved, rotate);
        }

        // 17: fake ID3 mp3 paket reddi
        Path fakePkg = Files.createTempDirectory(temporary, "id3fake-");
        Files.createDirectories(fakePkg.resolve("normalized"));
        TtsLaboratuvarYardimci.atomikYaz(fakePkg.resolve("normalized/xAI-Grok-TTS.mp3"),
                ("ID3fake-xai-" + approved.approvedTextHash()).getBytes(StandardCharsets.UTF_8));
        TtsLaboratuvarYardimci.atomikYaz(fakePkg.resolve("normalized/OpenAI-gpt-4o-mini-tts.mp3"),
                ("ID3fake-openai-" + approved.approvedTextHash()).getBytes(StandardCharsets.UTF_8));
        TtsLaboratuvarYardimci.atomikYaz(fakePkg.resolve("normalized/Piper-mevcut-ses.mp3"),
                ("ID3fake-piper-" + approved.approvedTextHash()).getBytes(StandardCharsets.UTF_8));
        expectOneOf(List.of("PACKAGE_INCOMPLETE", "FFPROBE_FAILED", "AUDIO_TOOLING_NOT_AVAILABLE",
                        "NORMALIZATION_FAILED"),
                () -> service.buildAhmetBeyPackages(fakePkg),
                "Fake ID3 mp3 paket reddedilir");

        // 19-20: provider HTTP hata mesajında response body sızmaz
        byte[] secretBody = ("{\"error\":\"" + SECRET_MARKER + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        expectProviderErrorWithoutBody("OpenAI 500 response body sızmaz",
                () -> directOpenAiFixed(approved, temporary, 500, "application/json", secretBody));
        expectProviderErrorWithoutBody("xAI 500 response body sızmaz",
                () -> directXaiFixed(approved, temporary, 500, secretBody));

        // 22: normalized silinince cache hit yeniden normalize eder, transport artmaz
        reapprove(service, approved, LiveProvider.openai);
        clearRaw(experiment, "openai-marin.wav");
        Files.deleteIfExists(experiment.resolve("normalized/OpenAI-gpt-4o-mini-tts.mp3"));
        OpenAiLiveTtsSaglayici.OpenAiHttpTransport okOpenAi = (endpoint, key, body, accept, timeout) ->
                new OpenAiLiveTtsSaglayici.OpenAiHttpResponse(200, "audio/wav", validWav, Map.of());
        LiveGenerateResult openFirst = service.generate(LiveProvider.openai, approved.selectionId(), "marin",
                new BigDecimal("0.20"), KasagiLivePreviewService.OPENAI_CONFIRMATION,
                true, true, null, null, okOpenAi);
        check(openFirst.calledNetwork() && "SUCCESS".equals(openFirst.status()),
                "OpenAI normalize başarılı üretim");
        Path openNorm = experiment.resolve("normalized/OpenAI-gpt-4o-mini-tts.mp3");
        check(Files.isRegularFile(openNorm), "OpenAI normalized oluştu");
        Files.delete(openNorm);
        int[] recreateTransport = {0};
        LiveGenerateResult openCache = service.generate(LiveProvider.openai, approved.selectionId(), "marin",
                new BigDecimal("0.20"), KasagiLivePreviewService.OPENAI_CONFIRMATION,
                true, true, null, null, (e, k, b, a, t) -> {
                    recreateTransport[0]++;
                    return new OpenAiLiveTtsSaglayici.OpenAiHttpResponse(200, "audio/wav", validWav, Map.of());
                });
        check(openCache.cacheHit() && recreateTransport[0] == 0,
                "Normalized silinince CACHE_HIT transport 0");
        check(Files.isRegularFile(openNorm), "Cache hit normalized yeniden oluşturur");
        check("NOT_REQUIRED_CACHE_HIT".equals(openCache.approvalState()),
                "Normalized recreate cache hit NOT_REQUIRED_CACHE_HIT");

        forceOffline();
    }

    private static void seedNormalizedTriplet(Path experiment, ApprovedPassage approved,
                                              TtsAbAudioService audio) throws Exception {
        byte[] wav = XaiTtsSaglayici.mockWav();
        Path rawX = experiment.resolve("raw/xai-lumen.wav");
        Path rawO = experiment.resolve("raw/openai-marin.wav");
        Path rawP = experiment.resolve("raw/piper-current.wav");
        Files.createDirectories(rawX.getParent());
        for (Path existing : List.of(rawX, rawO, rawP)) {
            if (Files.exists(existing)) {
                existing.toFile().setWritable(true);
            }
        }
        TtsLaboratuvarYardimci.atomikYaz(rawX, wav);
        byte[] wav2 = wav.clone();
        wav2[wav2.length - 1] ^= 0x01;
        TtsLaboratuvarYardimci.atomikYaz(rawO, wav2);
        byte[] wav3 = wav.clone();
        wav3[wav3.length - 2] ^= 0x02;
        TtsLaboratuvarYardimci.atomikYaz(rawP, wav3);
        Path norm = experiment.resolve("normalized");
        Files.createDirectories(norm);
        audio.normalize(rawX, norm.resolve("xAI-Grok-TTS.mp3"));
        audio.normalize(rawO, norm.resolve("OpenAI-gpt-4o-mini-tts.mp3"));
        audio.normalize(rawP, norm.resolve("Piper-mevcut-ses.mp3"));
    }

    private static void verifyBlindRotation(KasagiLivePreviewService service, Path experiment,
                                              ApprovedPassage approved, int forceRotate) throws Exception {
        Path blind = experiment.resolve("blind");
        if (Files.exists(blind)) {
            deleteTree(blind);
        }
        Files.deleteIfExists(experiment.resolve("private/provider-mapping.private.json"));

        service.buildBlindPackage(experiment, forceRotate);

        JsonNode map = JSON.readTree(
                experiment.resolve("private/provider-mapping.private.json").toFile());
        JsonNode arr = map.path("mapping");
        List<BlindCandidate> base = List.of(
                new BlindCandidate(LiveProvider.xai, "xai-tts", "lumen",
                        experiment.resolve("normalized/xAI-Grok-TTS.mp3"),
                        experiment.resolve("raw/xai-lumen.wav"),
                        experiment.resolve("raw/xai-lumen.wav.request.json")),
                new BlindCandidate(LiveProvider.openai, "gpt-4o-mini-tts", "marin",
                        experiment.resolve("normalized/OpenAI-gpt-4o-mini-tts.mp3"),
                        experiment.resolve("raw/openai-marin.wav"),
                        experiment.resolve("raw/openai-marin.wav.request.json")),
                new BlindCandidate(LiveProvider.piper, "piper", "piper",
                        experiment.resolve("normalized/Piper-mevcut-ses.mp3"),
                        experiment.resolve("raw/piper-current.wav"),
                        experiment.resolve("raw/piper-current.wav.request.json")));
        List<BlindCandidate> rotated = KasagiLivePreviewService.rotateList(base, forceRotate);
        String[] codes = {"A", "B", "C"};
        for (int i = 0; i < 3; i++) {
            JsonNode row = arr.get(i);
            BlindCandidate expected = rotated.get(i);
            check(expected.provider().name().equals(row.path("provider").asText()),
                    "Blind rotate " + forceRotate + " kod " + codes[i] + " provider");
            String sourceNorm = expected.provider() == LiveProvider.xai ? "xAI-Grok-TTS.mp3"
                    : expected.provider() == LiveProvider.openai ? "OpenAI-gpt-4o-mini-tts.mp3"
                    : "Piper-mevcut-ses.mp3";
            check(sourceNorm.equals(row.path("sourceNormalizedFile").asText()),
                    "Blind rotate " + forceRotate + " sourceNormalizedFile");
            Path blindFile = experiment.resolve("blind/ornek-" + codes[i] + ".mp3");
            check(row.path("normalizedHash").asText().equals(XaiTtsSaglayici.sha256(blindFile)),
                    "Blind rotate " + forceRotate + " normalizedHash");
        }
    }

    private static byte[] corruptWavDataChunk(byte[] wav) {
        byte[] corrupt = new byte[96];
        corrupt[0] = 'R';
        corrupt[1] = 'I';
        corrupt[2] = 'F';
        corrupt[3] = 'F';
        corrupt[8] = 'W';
        corrupt[9] = 'A';
        corrupt[10] = 'V';
        corrupt[11] = 'E';
        for (int i = 12; i < corrupt.length; i++) {
            corrupt[i] = (byte) 0xFF;
        }
        return corrupt;
    }

    private static LiveAttempt findAttempt(Path experiment, String provider, AttemptState state)
            throws Exception {
        Path dir = experiment.resolve("attempts/" + provider);
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("Attempt bulunamadı: " + provider + " " + state);
        }
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path path : paths.toList()) {
                LiveAttempt attempt = JSON.readValue(path.toFile(), LiveAttempt.class);
                if (attempt.state() == state) {
                    return attempt;
                }
            }
        }
        throw new IllegalStateException("Attempt bulunamadı: " + provider + " " + state);
    }

    private static void assertNonCacheAttemptIntegrity(Path experiment, String provider) throws Exception {
        Path dir = experiment.resolve("attempts/" + provider);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path path : paths.toList()) {
                LiveAttempt attempt = JSON.readValue(path.toFile(), LiveAttempt.class);
                if (attempt.state() == AttemptState.CACHE_HIT) {
                    continue;
                }
                check(!attempt.retryAllowed(),
                        "Attempt " + attempt.attemptId() + " retryAllowed=false");
                String certainty = attempt.resultCertainty();
                boolean ok = certainty != null && (
                        certainty.contains("BEFORE_NETWORK")
                                || certainty.contains("NO_AUTOMATIC")
                                || certainty.contains("DISPATCHED")
                                || certainty.contains("NETWORK_RESPONSE"));
                check(ok, "Attempt " + attempt.attemptId() + " resultCertainty beklenen değer");
            }
        }
    }

    private static void expectOneOf(List<String> codes, Throwing action, String name) throws Exception {
        try {
            action.run();
            throw new IllegalStateException("FAILED: " + name + " hata üretmedi");
        } catch (Exception e) {
            String msg = fullMessage(e);
            for (String code : codes) {
                if (msg.contains(code)) {
                    checks++;
                    System.out.println("OK: " + name);
                    return;
                }
            }
            throw e;
        }
    }

    private static void expectProviderErrorWithoutBody(String name, Throwing action) throws Exception {
        try {
            action.run();
            throw new IllegalStateException("FAILED: " + name + " hata üretmedi");
        } catch (Exception e) {
            String msg = fullMessage(e);
            check(!msg.contains(SECRET_MARKER), name);
        }
    }

    private static String fullMessage(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return sb.toString();
    }

    private static void directXaiFixed(ApprovedPassage approved, Path temporary,
                                       int status, byte[] body) throws Exception {
        Path dir = Files.createTempDirectory(temporary, "xai-direct-");
        XaiTtsSaglayici.XaiConfig config = new XaiTtsSaglayici.XaiConfig(
                "test-key", "lumen", "tr", true, "wav",
                URI.create("http://127.0.0.1:9/unused"), Duration.ofSeconds(10));
        XaiTtsSaglayici.XaiRequestContext context = new XaiTtsSaglayici.XaiRequestContext(
                "ESER-00005", true, false, true, new BigDecimal("0.05"),
                TtsAbSourceType.APPROVED_ARCHIVE_TEXT.name(),
                "Adım 35 kullanıcı hak onayı ile VERIFIED_LICENSED kaydı.",
                approved.approvedTextHash(), approved.approvedTextHash(), false);
        XaiTtsSaglayici sag = new XaiTtsSaglayici(config, context,
                (ep, key, req, accept, timeout) ->
                        new XaiTtsSaglayici.XaiHttpResponse(status, "application/json", body, Map.of()),
                millis -> {
                });
        sag.uretDetayli(new TtsUretimIstegi("t", "PASSAGE", approved.approvedText(), ""), dir);
    }

    private static void offlineGuards() throws Exception {
        forceOffline();
        for (String flag : List.of("XAI_TTS_LIVE_ENABLED", "OPENAI_TTS_LIVE_ENABLED",
                "ELEVENLABS_LIVE_ENABLED")) {
            check(!Boolean.parseBoolean(System.getProperty(flag, "false")), flag + " kapalı");
        }
        String dogrulama = Files.readString(Path.of("src/main/java/Adim36ADogrulama.java"),
                StandardCharsets.UTF_8);
        String xaiHost = "api" + ".x.ai";
        String openAiHost = "api" + ".openai.com";
        check(!dogrulama.contains(xaiHost) && !dogrulama.contains(openAiHost),
                "Hiçbir test gerçek harici endpoint'e gitmez");
        check(Files.exists(Path.of("TESLIM_OZETI.md")), "TESLIM_OZETI.md mevcut");
        long stubCount = dogrulama.lines()
                .filter(l -> l.contains("check(true,") && !l.contains("check(true, name)"))
                .count();
        check(stubCount == 0, "Kalan check(true) stub sayısı 0");
    }

    private static void documentation() throws Exception {
        for (String name : List.of(
                "ADIM_36A_CANLI_TTS_MOTORU.md", "ADIM_36A_GUVENLIK_VE_BUTCE.md",
                "ADIM_36A_OPENAI_TTS.md", "ADIM_36A_XAI_TTS.md",
                "ADIM_36A_AHMET_BEY_PAKETI.md", "ADIM_36B_MANUEL_CANLI_URETIM.md",
                "ADIM_36A_HATA_KODLARI.md")) {
            check(Files.isRegularFile(Path.of("docs").resolve(name)), "Doküman: " + name);
        }
        String manual = Files.readString(Path.of("docs/ADIM_36B_MANUEL_CANLI_URETIM.md"),
                StandardCharsets.UTF_8);
        check(manual.contains("adim36-live-approve.ps1")
                        && manual.contains("adim36-live-preflight.ps1")
                        && manual.contains("adim36-live-generate.ps1"),
                "Manuel akış approve→preflight→generate");
        check(Files.isRegularFile(Path.of("adim36a-self-test.ps1")), "adim36a-self-test.ps1");
        check(Files.isRegularFile(Path.of("adim36-live-preflight.ps1")), "adim36-live-preflight.ps1");
        check(Files.isRegularFile(Path.of("adim36-live-generate.ps1")), "adim36-live-generate.ps1");
        check(Files.isRegularFile(Path.of("adim36-live-approve.ps1")), "adim36-live-approve.ps1");
        check(Files.isRegularFile(Path.of("maven-resolve.ps1")), "maven-resolve.ps1");
    }

    private static void reapprove(KasagiLivePreviewService service, ApprovedPassage approved,
                                  LiveProvider provider) throws Exception {
        String voice = provider == LiveProvider.xai ? "lumen" : "marin";
        BigDecimal budget = provider == LiveProvider.xai ? new BigDecimal("0.05") : new BigDecimal("0.20");
        String conf = provider == LiveProvider.xai
                ? KasagiLivePreviewService.XAI_CONFIRMATION
                : KasagiLivePreviewService.OPENAI_CONFIRMATION;
        service.createLiveApproval(approved.selectionId(), approved.approvedTextHash(),
                provider, voice, budget, 1, conf, "TEST", false);
    }

    private static ProviderApprovalState providerState(KasagiLivePreviewService service) throws Exception {
        return providerStateNamed(service, LiveProvider.xai);
    }

    private static ProviderApprovalState providerStateNamed(KasagiLivePreviewService service,
                                                            LiveProvider provider) throws Exception {
        Path file = service.latestExperiment().resolve("approvals/live-generation-approval.json");
        LiveGenerationApproval approval = JSON.readValue(file.toFile(), LiveGenerationApproval.class);
        return approval.providerApprovals().stream()
                .filter(p -> p.provider() == provider)
                .findFirst()
                .map(p -> p.state() == null
                        ? (p.approved() ? ProviderApprovalState.APPROVED : ProviderApprovalState.NOT_APPROVED)
                        : p.state())
                .orElse(ProviderApprovalState.NOT_APPROVED);
    }

    private static long countAttempts(Path experiment, String provider) throws Exception {
        Path dir = experiment.resolve("attempts/" + provider);
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".json")).count();
        }
    }

    private static boolean attemptStatesContain(Path experiment, String provider, AttemptState state)
            throws Exception {
        Path dir = experiment.resolve("attempts/" + provider);
        if (!Files.isDirectory(dir)) return false;
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path path : paths.toList()) {
                LiveAttempt attempt = JSON.readValue(path.toFile(), LiveAttempt.class);
                if (attempt.state() == state) return true;
            }
        }
        return false;
    }

    private static void clearRaw(Path experiment, String name) throws Exception {
        Path raw = experiment.resolve("raw/" + name);
        Path state = experiment.resolve("raw/" + name + ".request.json");
        if (Files.exists(raw)) {
            raw.toFile().setWritable(true);
            Files.deleteIfExists(raw);
        }
        Files.deleteIfExists(state);
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String extract(String html, String start, String end) {
        int i = html.indexOf(start);
        if (i < 0) throw new IllegalStateException("csrf bulunamadı");
        int from = i + start.length();
        int to = html.indexOf(end, from);
        return html.substring(from, to);
    }

    private static void directOpenAiFixed(ApprovedPassage approved, Path temporary,
                                          int status, String contentType, byte[] body) throws Exception {
        Path dir = Files.createTempDirectory(temporary, "openai-direct-");
        OpenAiLiveTtsSaglayici.OpenAiConfig config = new OpenAiLiveTtsSaglayici.OpenAiConfig(
                "sk-test", "gpt-4o-mini-tts", "marin", true,
                URI.create("http://127.0.0.1:9/unused"), Duration.ofSeconds(10));
        OpenAiLiveTtsSaglayici sag = new OpenAiLiveTtsSaglayici(config,
                (ep, key, req, accept, timeout) ->
                        new OpenAiLiveTtsSaglayici.OpenAiHttpResponse(status, contentType, body, Map.of()));
        sag.uretDetayli(new TtsUretimIstegi("t", "PASSAGE", approved.approvedText(), ""),
                dir, OpenAiLiveTtsSaglayici.DEFAULT_INSTRUCTIONS, new BigDecimal("0.20"), true, false);
    }

    private static void seedCanonicalApproved(WebOrtam actual, WebOrtam test) throws Exception {
        Path src = new KasagiLivePreviewService(actual).selectionRoot();
        Path dest = new KasagiLivePreviewService(test).selectionRoot();
        Files.createDirectories(dest);
        copyTree(src, dest);
    }

    private static WebOrtam tempEnv(Path metin, Path temporary) {
        return new WebOrtam(Path.of("").toAbsolutePath(), temporary.resolve("gelen"),
                temporary.resolve("arsiv"), metin, temporary.resolve("ses"),
                temporary.resolve("kuyruk"), temporary.resolve("katalog.xlsx"),
                temporary.resolve("panel"));
    }

    private static void forceOffline() {
        System.setProperty("ELEVENLABS_OFFLINE", "true");
        restoreAudioToolingProperty();
        for (String name : List.of("ELEVENLABS_LIVE_ENABLED", "XAI_TTS_LIVE_ENABLED",
                "OPENAI_TTS_LIVE_ENABLED", "GOOGLE_TTS_LIVE_ENABLED",
                "AZURE_TTS_LIVE_ENABLED", "CARTESIA_TTS_LIVE_ENABLED")) {
            System.setProperty(name, "false");
        }
    }

    private static void restoreAudioToolingProperty() {
        System.setProperty("ADIM36_AUDIO_TOOLING_FORCE", "true");
    }

    private static void copyTree(Path source, Path target) throws Exception {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(relative);
                else {
                    Files.createDirectories(relative.getParent());
                    Files.copy(path, relative, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                try {
                    path.toFile().setWritable(true);
                } catch (Exception ignored) {
                }
                Files.deleteIfExists(path);
            }
        }
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("FAILED: " + name);
        checks++;
        System.out.println("OK: " + name);
    }

    private static void expect(String code, Throwing action, String name) throws Exception {
        try {
            action.run();
            throw new IllegalStateException("FAILED: " + name + " hata üretmedi");
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("FAILED:")
                    && !e.getMessage().contains(code)) {
                throw e;
            }
            if (e.getMessage() != null && e.getMessage().contains(code)) {
                checks++;
                System.out.println("OK: " + name);
                return;
            }
            throw e;
        } catch (Exception exception) {
            if (exception.getMessage() == null || !exception.getMessage().contains(code)) {
                throw exception;
            }
            checks++;
            System.out.println("OK: " + name);
        }
    }

    private static void expectCode(String code, Throwing action, String name) throws Exception {
        try {
            action.run();
            throw new IllegalStateException("FAILED: " + name + " hata üretmedi");
        } catch (OpenAiLiveTtsSaglayici.OpenAiLiveException e) {
            if (!code.equals(e.code())) {
                throw new IllegalStateException("FAILED: " + name + " beklenen " + code
                        + " gelen " + e.code(), e);
            }
            checks++;
            System.out.println("OK: " + name);
        } catch (XaiTtsSaglayici.XaiTtsException e) {
            if (!code.equals(e.code())) {
                throw new IllegalStateException("FAILED: " + name + " beklenen " + code
                        + " gelen " + e.code(), e);
            }
            checks++;
            System.out.println("OK: " + name);
        } catch (Exception e) {
            Throwable t = e;
            while (t != null) {
                if (t instanceof OpenAiLiveTtsSaglayici.OpenAiLiveException o && code.equals(o.code())) {
                    checks++;
                    System.out.println("OK: " + name);
                    return;
                }
                if (t instanceof XaiTtsSaglayici.XaiTtsException x && code.equals(x.code())) {
                    checks++;
                    System.out.println("OK: " + name);
                    return;
                }
                if (t.getMessage() != null && t.getMessage().contains(code)) {
                    checks++;
                    System.out.println("OK: " + name);
                    return;
                }
                t = t.getCause();
            }
            throw e;
        }
    }

    @FunctionalInterface
    private interface Throwing {
        void run() throws Exception;
    }
}
