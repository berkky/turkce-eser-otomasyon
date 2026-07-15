import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Adım 36A.1: canlı TTS üretim motoru — sıkı güvenlik, deneme defteri, önbellek kapıları. */
public final class KasagiLivePreviewService {
    static final String CANONICAL_SELECTION_ID = "PASSAGE-ESER-00005-20260715-183836-DF7B8C";
    static final String CANONICAL_CANDIDATE_ID = "PASSAGE-1";
    static final int ESER_ID = 5;
    static final String ESER_CODE = "ESER-00005";
    static final String XAI_CONFIRMATION = "CANLI_XAI_TTS_ONAYLI";
    static final String OPENAI_CONFIRMATION = "CANLI_OPENAI_TTS_ONAYLI";
    static final String RETRY_CONFIRMATION = "CANLI_TTS_RETRY_ONAYLI";
    static final BigDecimal XAI_FIRST_BUDGET_CAP = new BigDecimal("0.05");
    static final BigDecimal OPENAI_FIRST_BUDGET_CAP = new BigDecimal("0.20");
    static final BigDecimal TOTAL_FIRST_CAP = new BigDecimal("0.25");
    static final BigDecimal XAI_USD_PER_MILLION = new BigDecimal("15");
    static final long MAX_RAW_BYTES = 25L * 1024 * 1024;
    static final long MIN_RAW_BYTES = 44;

    private static final String[] SCORE_CRITERIA = {
            "dogallik", "telaffuz", "duygu", "vurgu", "diyalog",
            "tempo", "dinleme", "kitap", "yapay", "genel"
    };

    private final WebOrtam environment;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final TtsAbAudioService audio;
    private int lastXaiTransportCalls;
    private int lastOpenAiTransportCalls;

    public KasagiLivePreviewService(WebOrtam environment) {
        this.environment = environment;
        this.audio = new TtsAbAudioService(environment.projeKlasoru());
    }

    /** Son {@link #generate} çağrısında enjekte edilen xAI transport çağrı sayısı (test). */
    public int countXaiTransport() {
        return lastXaiTransportCalls;
    }

    /** Son {@link #generate} çağrısında enjekte edilen OpenAI transport çağrı sayısı (test). */
    public int countOpenAiTransport() {
        return lastOpenAiTransportCalls;
    }

    public Path selectionRoot() {
        return environment.sesArsivi().resolve("ab-test").resolve("passage-selection")
                .resolve(ESER_CODE).resolve(CANONICAL_SELECTION_ID);
    }

    public Path livePreviewRoot() {
        return environment.sesArsivi().resolve("ab-test").resolve("live-preview").resolve(ESER_CODE);
    }

    public ApprovedPassage resolveApprovedPassage() throws Exception {
        Path file = selectionRoot().resolve("approved-passage.json");
        if (!Files.isRegularFile(file) || Files.size(file) == 0) {
            throw error("APPROVED_PASSAGE_INVALID", "approved-passage.json yok");
        }
        ApprovedPassage approved = json.readValue(file.toFile(), ApprovedPassage.class);
        validateApproved(approved);
        return approved;
    }

    void validateApproved(ApprovedPassage approved) throws Exception {
        if (approved == null) throw error("APPROVED_PASSAGE_INVALID", "null");
        if (!CANONICAL_SELECTION_ID.equals(approved.selectionId())) {
            throw error("APPROVED_PASSAGE_INVALID", "selectionId eşleşmiyor");
        }
        if (approved.eserId() != ESER_ID) throw error("APPROVED_PASSAGE_INVALID", "eserId");
        if (!CANONICAL_CANDIDATE_ID.equals(approved.candidateId())) {
            throw error("APPROVED_PASSAGE_INVALID", "candidateId");
        }
        if (approved.approvedText() == null || approved.approvedText().isBlank()) {
            throw error("APPROVED_PASSAGE_INVALID", "approvedText boş");
        }
        String recomputed = TtsAbService.sha256(approved.approvedText());
        if (!recomputed.equalsIgnoreCase(approved.approvedTextHash())) {
            throw error("APPROVED_TEXT_HASH_MISMATCH", "hash uyuşmuyor");
        }
        if (Math.abs(approved.approvedCharacterCount() - approved.approvedText().length()) > 2) {
            throw error("APPROVED_PASSAGE_INVALID", "karakter sayısı tutarsız");
        }
        if (!approved.sourceApproved() || !approved.rightsReviewed()
                || !approved.rightsAcknowledgedByUser() || !approved.commercialUseApprovedByUser()) {
            throw error("SOURCE_RIGHTS_NOT_APPROVED", "hak/onay alanları eksik");
        }
        if (!"PASSAGE_APPROVED_LIVE_LOCKED".equals(approved.status())) {
            throw error("PASSAGE_STATUS_INVALID", approved.status());
        }
    }

    public LiveCostEstimate estimateXai(ApprovedPassage approved) {
        BigDecimal cost = XAI_USD_PER_MILLION
                .multiply(BigDecimal.valueOf(approved.approvedCharacterCount()))
                .divide(BigDecimal.valueOf(1_000_000L), 8, RoundingMode.HALF_UP);
        return new LiveCostEstimate(LiveProvider.xai, approved.approvedCharacterCount(),
                approved.estimatedSpeechSeconds(), cost, "USD", "CHARACTER_ESTIMATE",
                "15 USD / 1.000.000 karakter planlama profili");
    }

    public LiveCostEstimate estimateOpenAi(ApprovedPassage approved) {
        BigDecimal cost = OpenAiLiveTtsSaglayici.estimatedCost(
                approved.approvedCharacterCount(), approved.estimatedSpeechSeconds());
        return new LiveCostEstimate(LiveProvider.openai, approved.approvedCharacterCount(),
                approved.estimatedSpeechSeconds(), cost, "USD", "ESTIMATED_ONLY",
                "Süre tabanlı belirsiz planlama; kesin OpenAI maliyeti değildir");
    }

    public synchronized LiveGenerationApproval createLiveApproval(
            String selectionId, String approvedTextHash, LiveProvider provider, String voice,
            BigDecimal maxBudgetUsd, int maxRequestCount, String confirmation, String approvedBy,
            boolean budgetReviewSecondConfirm) throws Exception {
        ApprovedPassage approved = resolveApprovedPassage();
        if (!CANONICAL_SELECTION_ID.equals(selectionId)) {
            throw error("APPROVED_PASSAGE_INVALID", "selectionId");
        }
        if (!approved.approvedTextHash().equalsIgnoreCase(approvedTextHash)) {
            throw error("APPROVED_TEXT_HASH_MISMATCH", "text hash");
        }
        requireConfirmation(provider, confirmation);
        if (maxRequestCount != 1) throw error("REQUEST_LIMIT_EXCEEDED", "maxRequestCount yalnız 1");
        if (maxBudgetUsd == null || maxBudgetUsd.signum() <= 0) {
            throw error("LIVE_BUDGET_NOT_APPROVED", "bütçe <= 0");
        }
        LiveCostEstimate estimate = provider == LiveProvider.xai ? estimateXai(approved) : estimateOpenAi(approved);
        BigDecimal cap = provider == LiveProvider.xai ? XAI_FIRST_BUDGET_CAP : OPENAI_FIRST_BUDGET_CAP;
        if (estimate.estimatedCostUsd().compareTo(maxBudgetUsd) > 0) {
            throw error("BUDGET_EXCEEDED", "tahmini maliyet bütçeyi aşıyor");
        }
        if (maxBudgetUsd.compareTo(cap) > 0 && !budgetReviewSecondConfirm) {
            throw error("BUDGET_REVIEW_REQUIRED", "üst sınır aşımı için ikinci onay gerekir");
        }
        if (!voiceAllowed(provider, voice)) throw error("VOICE_NOT_ALLOWED", voice);

        Path experiment = ensureExperimentWorkspace(approved);
        Path approvalFile = experiment.resolve("approvals/live-generation-approval.json");
        LiveGenerationApproval current = Files.isRegularFile(approvalFile)
                ? json.readValue(approvalFile.toFile(), LiveGenerationApproval.class) : null;
        if (current != null && (current.status() == LiveApprovalStatus.CONSUMED
                || current.status() == LiveApprovalStatus.REVOKED
                || current.status() == LiveApprovalStatus.EXPIRED
                || OffsetDateTime.parse(current.expiresAt()).isBefore(OffsetDateTime.now()))) {
            current = null;
        }

        boolean xai = current != null && current.xaiApproved();
        boolean openai = current != null && current.openAiApproved();
        if (provider == LiveProvider.xai) xai = true;
        if (provider == LiveProvider.openai) openai = true;

        List<LiveProviderApproval> approvals = new ArrayList<>();
        if (current != null) {
            for (LiveProviderApproval existing : current.providerApprovals()) {
                if (existing.provider() != provider) approvals.add(existing);
            }
        }
        approvals.add(new LiveProviderApproval(provider, true, ProviderApprovalState.APPROVED, voice,
                maxBudgetUsd, 1, false,
                XaiTtsSaglayici.sha256(confirmation.getBytes(StandardCharsets.UTF_8))));

        BigDecimal xaiBudget = provider == LiveProvider.xai ? maxBudgetUsd
                : (current == null ? BigDecimal.ZERO : current.xaiBudgetUsd());
        BigDecimal openAiBudget = provider == LiveProvider.openai ? maxBudgetUsd
                : (current == null ? BigDecimal.ZERO : current.openAiBudgetUsd());
        BigDecimal total = xaiBudget.add(openAiBudget);
        LiveApprovalStatus status = deriveLiveApprovalStatus(approvals);

        LiveGenerationApproval created = new LiveGenerationApproval(
                current == null ? "LIVE-APPROVAL-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT)
                        : current.liveApprovalId(),
                approved.approvalId(), selectionId, approved.approvedTextHash(),
                approvals.stream().map(a -> a.provider().name()).toList(),
                approvals, xai, openai, xaiBudget, openAiBudget, total, 1, false,
                OffsetDateTime.now().toString(),
                approvedBy == null || approvedBy.isBlank() ? "LOCAL_USER" : approvedBy.trim(),
                OffsetDateTime.now().plusHours(12).toString(), 1, status);
        writeJson(approvalFile, created);
        return created;
    }

    public synchronized LiveGenerationApproval revokeLiveApproval(String reason) throws Exception {
        Path experiment = latestExperiment();
        if (experiment == null) throw error("LIVE_APPROVAL_NOT_CREATED", "experiment yok");
        Path approvalFile = experiment.resolve("approvals/live-generation-approval.json");
        if (!Files.isRegularFile(approvalFile)) throw error("LIVE_APPROVAL_NOT_CREATED", "dosya yok");
        LiveGenerationApproval current = json.readValue(approvalFile.toFile(), LiveGenerationApproval.class);
        List<LiveProviderApproval> revokedProviders = current.providerApprovals().stream()
                .map(p -> new LiveProviderApproval(p.provider(), false, ProviderApprovalState.REVOKED,
                        p.voice(), BigDecimal.ZERO, 0, false, p.confirmationPhraseHash()))
                .toList();
        LiveGenerationApproval revoked = new LiveGenerationApproval(
                current.liveApprovalId(), current.approvalId(), current.selectionId(),
                current.approvedTextHash(), List.of(), revokedProviders,
                false, false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, false,
                current.approvedAt(), current.approvedBy(), current.expiresAt(),
                current.confirmationVersion(), LiveApprovalStatus.REVOKED);
        writeJson(approvalFile, revoked);
        writeJson(experiment.resolve("approvals/revoke-note.json"),
                Mapish("reason", reason == null ? "" : reason, "at", OffsetDateTime.now().toString()));
        return revoked;
    }

    /**
     * Canlı üretim. {@code live=false} ağ çağrısı yapmaz; {@code live=true} ortam bayrağı ve onay gerekir.
     * Testler transport enjekte edebilir; çağrı sayısı {@link #countXaiTransport()} / {@link #countOpenAiTransport()} ile okunur.
     */
    public synchronized LiveGenerateResult generate(LiveProvider provider, String selectionId,
                                                    String voice, BigDecimal maxBudgetUsd,
                                                    String confirmation, boolean live, boolean noRetry,
                                                    String retryConfirmation,
                                                    XaiTtsSaglayici.XaiHttpTransport xaiTransport,
                                                    OpenAiLiveTtsSaglayici.OpenAiHttpTransport openAiTransport)
            throws Exception {
        lastXaiTransportCalls = 0;
        lastOpenAiTransportCalls = 0;
        if (provider != LiveProvider.xai && provider != LiveProvider.openai) {
            throw error("LIVE_PROVIDER_NOT_APPROVED", "yalnız xai|openai");
        }
        ApprovedPassage approved = resolveApprovedPassage();
        if (!CANONICAL_SELECTION_ID.equals(selectionId)) {
            throw error("APPROVED_PASSAGE_INVALID", "selectionId");
        }
        if (!live) {
            throw error("DRY_RUN_ONLY", "Live bayrağı yok; ağ çağrısı yapılmadı");
        }
        requireConfirmation(provider, confirmation);
        if (!noRetry) {
            throw error("AUTOMATIC_RETRY_NOT_SUPPORTED", "otomatik retry kapalı; noRetry=true gerekir");
        }
        if (!liveProviderFlagEnabled(provider)) {
            throw error("LIVE_PROVIDER_FLAG_DISABLED", provider.name() + " ortam bayrağı kapalı");
        }
        requireAudioTooling();

        Path experiment = resolveExperimentForGenerate(approved);
        LiveCostEstimate estimate = provider == LiveProvider.xai ? estimateXai(approved) : estimateOpenAi(approved);
        if (estimate.estimatedCostUsd().compareTo(maxBudgetUsd) > 0) {
            throw error("BUDGET_EXCEEDED", "tahmini maliyet");
        }
        if (!voiceAllowed(provider, voice)) {
            throw error("VOICE_NOT_ALLOWED", voice);
        }

        String model = provider == LiveProvider.xai ? "xai-tts"
                : TtsLaboratuvarYardimci.ortam("OPENAI_TTS_MODEL", "gpt-4o-mini-tts");
        String requestHash = computeRequestHash(provider, approved, voice, model);
        Path rawFinal = finalRawPath(experiment, provider, voice);
        Path rawState = rawFinal.resolveSibling(rawFinal.getFileName() + ".request.json");

        // Cache BEFORE approval consume / network — geçerli cache approval gerektirmez
        CacheDecision cache = evaluateFinalCache(experiment, provider, voice, model,
                approved.approvedTextHash(), requestHash, rawFinal, rawState);
        if (cache == CacheDecision.HIT) {
            String rawHash = XaiTtsSaglayici.sha256(rawFinal);
            LiveGenerationApproval approvalForCache = readApprovalIfPresent(experiment);
            writeCacheHitAttempt(experiment, approvalForCache, provider, model, voice, selectionId,
                    approved.approvedTextHash(), requestHash, estimate.estimatedCostUsd(), maxBudgetUsd, rawHash);
            String outName = provider == LiveProvider.xai ? "xAI-Grok-TTS.mp3" : "OpenAI-gpt-4o-mini-tts.mp3";
            Path expectedNorm = experiment.resolve("normalized/" + outName);
            if (!Files.isRegularFile(expectedNorm)) {
                normalizeRaw(experiment, provider, voice);
            }
            String normalizedHash = null;
            TtsAbAudioMetrics metrics = null;
            if (Files.isRegularFile(expectedNorm)) {
                metrics = audio.probe(expectedNorm);
                requireNormalizedProbe(metrics);
                normalizedHash = metrics.sha256();
            }
            return cacheHitResult(provider, rawFinal, requestHash, rawHash, estimate.estimatedCostUsd(),
                    expectedNorm, normalizedHash, metrics);
        }
        if (cache == CacheDecision.INVALID) {
            throw error("RAW_CACHE_INVALID", rawFinal.getFileName().toString());
        }
        if (cache == CacheDecision.HASH_MISMATCH) {
            throw error("RAW_FILE_ALREADY_EXISTS", rawFinal.getFileName().toString());
        }

        LiveGenerationApproval approval = requireActiveApproval(experiment, provider, voice, maxBudgetUsd);

        Path requests = experiment.resolve("requests");
        Files.createDirectories(requests);
        writeSafeRequest(requests.resolve(provider.name() + "-request.safe.json"),
                provider, voice, approved, estimate);

        String attemptId = "ATTEMPT-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        LiveAttempt attempt = newAttempt(attemptId, approval, provider, model, voice, selectionId,
                approved.approvedTextHash(), requestHash, estimate.estimatedCostUsd(), maxBudgetUsd,
                AttemptState.CREATED, null, null, null, null, null, false, null);
        writeAttempt(experiment, attempt);
        attempt = updateAttempt(experiment, attempt, AttemptState.DISPATCH_RESERVED, null, null, null, null, null);

        approval = markProviderInFlight(experiment, approval, provider);
        attempt = updateAttempt(experiment, attempt, AttemptState.NETWORK_DISPATCH_STARTED,
                OffsetDateTime.now().toString(), null, null, null, null);

        int[] xaiCounter = new int[1];
        int[] openAiCounter = new int[1];
        XaiTtsSaglayici.XaiHttpTransport wrappedXai = countingXaiTransport(
                xaiTransport == null ? new XaiTtsSaglayici.JavaHttpTransport() : xaiTransport, xaiCounter);
        OpenAiLiveTtsSaglayici.OpenAiHttpTransport wrappedOpenAi = countingOpenAiTransport(
                openAiTransport == null ? new OpenAiLiveTtsSaglayici.JavaHttpTransport() : openAiTransport,
                openAiCounter);

        Path stagingRoot = experiment.resolve("raw-staging-" + provider.name() + "/" + attemptId);
        Files.createDirectories(stagingRoot);
        try {
            NetworkOutcome outcome = provider == LiveProvider.xai
                    ? dispatchXai(approved, voice, maxBudgetUsd, wrappedXai, stagingRoot, attemptId)
                    : dispatchOpenAi(approved, voice, maxBudgetUsd, wrappedOpenAi, stagingRoot, experiment);
            lastXaiTransportCalls = xaiCounter[0];
            lastOpenAiTransportCalls = openAiCounter[0];

            validateStagingWav(outcome.stagingFile(), outcome.rawHash());
            publishFinalRaw(rawFinal, rawState, outcome.stagingFile(), provider, model, voice,
                    approved.approvedTextHash(), requestHash, outcome.rawHash());
            attempt = updateAttempt(experiment, attempt, AttemptState.SUCCESS,
                    attempt.networkDispatchStartedAt(), OffsetDateTime.now().toString(),
                    200, outcome.rawHash(), null);

            Path normalized;
            String normalizedHash;
            TtsAbAudioMetrics metrics;
            try {
                normalized = normalizeRaw(experiment, provider, voice);
                metrics = audio.probe(normalized);
                requireNormalizedProbe(metrics);
                normalizedHash = metrics.sha256();
            } catch (Exception postProcess) {
                attempt = updateAttempt(experiment, attempt, AttemptState.NETWORK_SUCCESS_POSTPROCESS_FAILED,
                        attempt.networkDispatchStartedAt(), OffsetDateTime.now().toString(),
                        200, outcome.rawHash(), "NORMALIZATION_FAILED");
                approval = consumeProviderApproval(experiment, approval, provider, requestHash,
                        outcome.rawHash(), attemptId, false);
                throw error("NORMALIZATION_FAILED", postProcess.getMessage());
            }

            approval = consumeProviderApproval(experiment, approval, provider, requestHash,
                    outcome.rawHash(), attemptId, false);
            ProviderApprovalState providerState = providerState(approval, provider);
            return new LiveGenerateResult(provider, true, false, 1,
                    new PathLike(rawFinal.getFileName().toString()),
                    rawFinal.getFileName().toString(), requestHash, outcome.rawHash(),
                    normalized.getFileName().toString(),
                    normalizedHash,
                    metrics.durationMs() / 1000.0,
                    metrics.codec(),
                    metrics.sampleRate(),
                    metrics.channels(),
                    metrics.bitrate(),
                    metrics.loudnessLufs(),
                    metrics.truePeakDbtp(),
                    metrics.codec(),
                    outcome.estimatedCostUsd(), null, true, attemptId,
                    providerState.name(), "SUCCESS");
        } catch (LivePreviewException e) {
            lastXaiTransportCalls = xaiCounter[0];
            lastOpenAiTransportCalls = openAiCounter[0];
            if ((provider == LiveProvider.xai ? xaiCounter[0] : openAiCounter[0]) > 0
                    && !"NORMALIZATION_FAILED".equals(e.code())
                    && !"PACKAGE_INCOMPLETE".equals(e.code())) {
                handleDispatchFailure(experiment, approval, provider, attempt, e.code(), 0, true);
            }
            throw e;
        } catch (XaiTtsSaglayici.XaiTtsException e) {
            lastXaiTransportCalls = xaiCounter[0];
            lastOpenAiTransportCalls = openAiCounter[0];
            handleDispatchFailure(experiment, approval, provider, attempt, e.code(), e.httpStatus(), true);
            throw error(e.code(), e.getMessage());
        } catch (OpenAiLiveTtsSaglayici.OpenAiLiveException e) {
            lastXaiTransportCalls = xaiCounter[0];
            lastOpenAiTransportCalls = openAiCounter[0];
            handleDispatchFailure(experiment, approval, provider, attempt, e.code(), e.httpStatus(), true);
            throw error(e.code(), e.getMessage());
        } catch (Exception e) {
            lastXaiTransportCalls = xaiCounter[0];
            lastOpenAiTransportCalls = openAiCounter[0];
            boolean unknown = e instanceof IOException;
            handleDispatchFailure(experiment, approval, provider, attempt,
                    unknown ? "NETWORK_ERROR" : "PROVIDER_SERVER_ERROR", 0, unknown);
            throw error(unknown ? "NETWORK_ERROR" : "PROVIDER_SERVER_ERROR", e.getMessage());
        }
    }

    public Path normalizeRaw(Path experiment, LiveProvider provider, String voice) throws Exception {
        requireAudioTooling();
        Path raw = finalRawPath(experiment, provider, voice);
        if (!Files.isRegularFile(raw) || Files.size(raw) == 0) {
            throw error("PACKAGE_INCOMPLETE", "raw yok");
        }
        String outName = provider == LiveProvider.xai ? "xAI-Grok-TTS.mp3"
                : "OpenAI-gpt-4o-mini-tts.mp3";
        Path normalized = experiment.resolve("normalized/" + outName);
        Files.createDirectories(normalized.getParent());
        try {
            audio.normalize(raw, normalized);
            TtsAbAudioMetrics metrics = audio.probe(normalized);
            requireNormalizedProbe(metrics);
        } catch (LivePreviewException e) {
            throw e;
        } catch (Exception e) {
            throw error("NORMALIZATION_FAILED", e.getMessage());
        }
        return normalized;
    }

    public Path producePiperBaseline(Path experiment, ApprovedPassage approved) throws Exception {
        requireAudioTooling();
        Path raw = experiment.resolve("raw/piper-current.wav");
        Files.createDirectories(raw.getParent());
        try {
            PiperClient piper = new PiperClient(environment.projeKlasoru());
            PiperClient.KontrolSonucu kontrol = piper.kontrolEt();
            if (!kontrol.hazir()) throw error("PIPER_BASELINE_MISSING", kontrol.mesaj());
            Path staging = experiment.resolve("raw-staging-piper");
            Files.createDirectories(staging);
            Path wavTarget = staging.resolve("piper.wav");
            piper.wavUret(approved.approvedText(), wavTarget);
            atomicMoveIfAbsent(wavTarget, raw);
            Path mp3 = experiment.resolve("normalized/Piper-mevcut-ses.mp3");
            Files.createDirectories(mp3.getParent());
            audio.normalize(raw, mp3);
            return mp3;
        } catch (LivePreviewException e) {
            throw e;
        } catch (Exception e) {
            throw error("PIPER_BASELINE_MISSING", e.getMessage());
        }
    }

    public void buildBlindPackage(Path experiment) throws Exception {
        buildBlindPackage(experiment, null);
    }

    void buildBlindPackage(Path experiment, Integer forceRotate) throws Exception {
        requireAudioTooling();
        Path normalized = experiment.resolve("normalized");
        Path a = normalized.resolve("xAI-Grok-TTS.mp3");
        Path b = normalized.resolve("OpenAI-gpt-4o-mini-tts.mp3");
        Path c = normalized.resolve("Piper-mevcut-ses.mp3");
        requireDistinctNormalized(a, b, c);

        ApprovedPassage approved = resolveApprovedPassage();
        String seed = experiment.getFileName() + "|" + approved.approvedTextHash();
        Path rawXai = experiment.resolve("raw/xai-lumen.wav");
        Path rawOpenai = experiment.resolve("raw/openai-marin.wav");
        Path rawPiper = experiment.resolve("raw/piper-current.wav");
        Path stateXai = rawXai.resolveSibling(rawXai.getFileName() + ".request.json");
        Path stateOpenai = rawOpenai.resolveSibling(rawOpenai.getFileName() + ".request.json");
        Path statePiper = rawPiper.resolveSibling(rawPiper.getFileName() + ".request.json");
        List<BlindCandidate> candidates = List.of(
                new BlindCandidate(LiveProvider.xai, "xai-tts", "lumen", a, rawXai, stateXai),
                new BlindCandidate(LiveProvider.openai, "gpt-4o-mini-tts", "marin", b, rawOpenai, stateOpenai),
                new BlindCandidate(LiveProvider.piper, "piper", "piper", c, rawPiper,
                        Files.isRegularFile(statePiper) ? statePiper : null));
        int rotate = forceRotate != null ? Math.floorMod(forceRotate, 3)
                : Math.floorMod(seed.hashCode(), 3);
        candidates = rotateList(candidates, rotate);

        Path blind = experiment.resolve("blind");
        Files.createDirectories(blind);
        String[] codes = {"A", "B", "C"};
        ObjectNode privateMap = json.createObjectNode();
        privateMap.put("seed", XaiTtsSaglayici.sha256(seed.getBytes(StandardCharsets.UTF_8)));
        ArrayNode arr = privateMap.putArray("mapping");

        for (int i = 0; i < 3; i++) {
            BlindCandidate candidate = candidates.get(i);
            Path target = blind.resolve("ornek-" + codes[i] + ".mp3");
            Files.copy(candidate.normalizedPath(), target, StandardCopyOption.REPLACE_EXISTING);
            ObjectNode row = arr.addObject();
            row.put("blindCode", "ornek-" + codes[i]);
            row.put("provider", candidate.provider().name());
            row.put("sourceFile", candidate.normalizedPath().getFileName().toString());
            row.put("normalizedHash", XaiTtsSaglayici.sha256(target));
            enrichMappingRow(candidate, row);
        }

        ObjectNode publicManifest = json.createObjectNode();
        publicManifest.put("samples", 3);
        publicManifest.put("providerNamesIncluded", false);
        publicManifest.putArray("files").add("ornek-A.mp3").add("ornek-B.mp3").add("ornek-C.mp3");
        writeJson(blind.resolve("blind-manifest.public.json"), publicManifest);
        Files.createDirectories(experiment.resolve("private"));
        writeJson(experiment.resolve("private/provider-mapping.private.json"), privateMap);
    }

    public PackageBuildResult buildAhmetBeyPackages(Path experiment) throws Exception {
        requireAudioTooling();
        ApprovedPassage approved = resolveApprovedPassage();
        requireDistinctNormalized(
                experiment.resolve("normalized/xAI-Grok-TTS.mp3"),
                experiment.resolve("normalized/OpenAI-gpt-4o-mini-tts.mp3"),
                experiment.resolve("normalized/Piper-mevcut-ses.mp3"));

        String stamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path teslimler = environment.sesArsivi().getParent().resolve("teslimler");
        Path publicDir = teslimler.resolve("AHMET-BEY-SES-KARSILASTIRMASI-" + stamp + "-PUBLIC");
        Path privateDir = teslimler.resolve("AHMET-BEY-SES-KARSILASTIRMASI-" + stamp + "-PRIVATE");
        populateAhmetBeyDirectory(publicDir, experiment, approved, false);
        populateAhmetBeyDirectory(privateDir, experiment, approved, true);

        Path publicZip = teslimler.resolve(publicDir.getFileName() + ".zip");
        Path privateZip = teslimler.resolve(privateDir.getFileName() + ".zip");
        zipDirectory(publicDir, publicZip, false);
        zipDirectory(privateDir, privateZip, true);
        verifyZipPrivacy(publicZip, false);
        verifyZipPrivacy(privateZip, true);

        String publicSha = XaiTtsSaglayici.sha256(publicZip);
        String privateSha = XaiTtsSaglayici.sha256(privateZip);
        atomicText(teslimler.resolve(publicZip.getFileName() + ".sha256.txt"),
                publicSha + "  " + publicZip.getFileName() + "\n");
        atomicText(teslimler.resolve(privateZip.getFileName() + ".sha256.txt"),
                privateSha + "  " + privateZip.getFileName() + "\n");

        return new PackageBuildResult(
                new PathLike(publicDir.getFileName().toString()),
                new PathLike(privateDir.getFileName().toString()),
                new PathLike(publicZip.getFileName().toString()),
                new PathLike(privateZip.getFileName().toString()),
                publicSha,
                privateSha);
    }

    /** Geriye dönük test uyumluluğu: public veya private dizin yolunu döner. */
    public Path buildAhmetBeyPackage(Path experiment, boolean includePrivateMapping) throws Exception {
        PackageBuildResult result = buildAhmetBeyPackages(experiment);
        Path teslimler = environment.sesArsivi().getParent().resolve("teslimler");
        return includePrivateMapping
                ? teslimler.resolve(result.privateDir().safeName())
                : teslimler.resolve(result.publicDir().safeName());
    }

    public String preflightReport(LiveProvider provider, String voice) throws Exception {
        ApprovedPassage approved = resolveApprovedPassage();
        LiveCostEstimate estimate = provider == LiveProvider.xai ? estimateXai(approved) : estimateOpenAi(approved);
        ObjectNode node = json.createObjectNode();
        node.put("selectionId", approved.selectionId());
        node.put("candidateId", approved.candidateId());
        node.put("approvedTextHash", approved.approvedTextHash());
        node.put("provider", provider.name());
        node.put("voice", voice);
        node.put("estimatedCostUsd", estimate.estimatedCostUsd());
        node.put("calculationType", estimate.calculationType());
        node.put("openAiKeyPresent", !TtsLaboratuvarYardimci.ortam("OPENAI_API_KEY", "").isBlank());
        node.put("xaiKeyPresent", !TtsLaboratuvarYardimci.ortam("XAI_API_KEY", "").isBlank());
        node.put("liveProviderFlagEnabled", liveProviderFlagEnabled(provider));
        node.put("xaiLiveFlag", liveProviderFlagEnabled(LiveProvider.xai));
        node.put("openAiLiveFlag", liveProviderFlagEnabled(LiveProvider.openai));
        Path experiment = latestExperiment();
        node.put("liveApprovalExists", experiment != null && Files.isRegularFile(
                experiment.resolve("approvals/live-generation-approval.json")));
        if (experiment != null && Files.isRegularFile(experiment.resolve("approvals/live-generation-approval.json"))) {
            LiveGenerationApproval approval = json.readValue(
                    experiment.resolve("approvals/live-generation-approval.json").toFile(),
                    LiveGenerationApproval.class);
            node.put("approvalStatus", approval.status().name());
            approval.providerApprovals().stream()
                    .filter(p -> p.provider() == provider)
                    .findFirst()
                    .ifPresent(p -> node.put("providerApprovalState",
                            (p.state() == null ? ProviderApprovalState.NOT_APPROVED : p.state()).name()));
        }
        node.put("networkCalls", false);
        node.put("webStartsTts", false);
        node.put("status", "PREFLIGHT_READY");
        return json.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    public Path latestExperiment() throws Exception {
        if (!Files.isDirectory(livePreviewRoot())) return null;
        try (Stream<Path> paths = Files.list(livePreviewRoot())) {
            return paths.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("LIVE-ESER-00005-"))
                    .max(Comparator.comparing(p -> p.getFileName().toString()))
                    .orElse(null);
        }
    }

    Path ensureExperimentWorkspace(ApprovedPassage approved) throws Exception {
        Path latest = latestExperiment();
        if (latest != null && Files.isRegularFile(latest.resolve("source/approved-text.sha256"))) {
            String hash = Files.readString(latest.resolve("source/approved-text.sha256"),
                    StandardCharsets.UTF_8).trim();
            if (hash.equalsIgnoreCase(approved.approvedTextHash())) return latest;
        }
        String id = "LIVE-ESER-00005-" + OffsetDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        Path root = livePreviewRoot().resolve(id);
        Files.createDirectories(root.resolve("source"));
        Files.createDirectories(root.resolve("approvals"));
        Files.createDirectories(root.resolve("requests"));
        Files.createDirectories(root.resolve("raw"));
        Files.createDirectories(root.resolve("normalized"));
        Files.createDirectories(root.resolve("technical"));
        Files.createDirectories(root.resolve("blind"));
        Files.createDirectories(root.resolve("private"));
        Files.createDirectories(root.resolve("attempts"));
        Files.copy(selectionRoot().resolve("approved-passage.json"),
                root.resolve("source/approved-passage.json"), StandardCopyOption.REPLACE_EXISTING);
        atomicText(root.resolve("source/approved-text.txt"), approved.approvedText());
        atomicText(root.resolve("source/approved-text.sha256"), approved.approvedTextHash() + "\n");
        Path attribution = selectionRoot().resolve("rights/SOURCE_ATTRIBUTION.txt");
        if (Files.isRegularFile(attribution)) {
            Files.copy(attribution, root.resolve("source/SOURCE_ATTRIBUTION.txt"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        Path evidence = selectionRoot().resolve("rights/rights-evidence.sha256");
        if (Files.isRegularFile(evidence)) {
            Files.copy(evidence, root.resolve("source/rights-evidence.sha256"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        atomicText(root.resolve("README.txt"),
                "Adım 36 canlı önizleme alanı. Varsayılan olarak kilitlidir.\n"
                        + "Gerçek üretim yalnız CLI confirmation ile yapılır.\n");
        return root;
    }

    static XaiTtsSaglayici.XaiHttpTransport countingXaiTransport(
            XaiTtsSaglayici.XaiHttpTransport delegate, int[] counter) {
        return (endpoint, key, body, accept, timeout) -> {
            counter[0]++;
            return delegate.send(endpoint, key, body, accept, timeout);
        };
    }

    static OpenAiLiveTtsSaglayici.OpenAiHttpTransport countingOpenAiTransport(
            OpenAiLiveTtsSaglayici.OpenAiHttpTransport delegate, int[] counter) {
        return (endpoint, key, body, accept, timeout) -> {
            counter[0]++;
            return delegate.send(endpoint, key, body, accept, timeout);
        };
    }

    private Path resolveExperimentForGenerate(ApprovedPassage approved) throws Exception {
        Path latest = latestExperiment();
        if (latest == null) throw error("LIVE_APPROVAL_NOT_CREATED", "experiment yok");
        if (!Files.isRegularFile(latest.resolve("source/approved-text.sha256"))) {
            throw error("APPROVED_PASSAGE_INVALID", "experiment hash dosyası yok");
        }
        String hash = Files.readString(latest.resolve("source/approved-text.sha256"),
                StandardCharsets.UTF_8).trim();
        if (!hash.equalsIgnoreCase(approved.approvedTextHash())) {
            throw error("APPROVED_TEXT_HASH_MISMATCH", "experiment hash uyuşmuyor");
        }
        return latest;
    }

    private enum CacheDecision { MISS, HIT, INVALID, HASH_MISMATCH }

    private CacheDecision evaluateFinalCache(Path experiment, LiveProvider provider, String voice,
                                             String model, String approvedTextHash, String requestHash,
                                             Path rawFinal, Path rawState) throws Exception {
        requireAudioTooling();
        if (!Files.exists(rawFinal)) return CacheDecision.MISS;
        if (!Files.isRegularFile(rawFinal) || Files.size(rawFinal) <= MIN_RAW_BYTES) {
            return CacheDecision.INVALID;
        }
        if (!Files.isRegularFile(rawState)) {
            return CacheDecision.INVALID;
        }
        JsonNode state;
        try {
            state = json.readTree(rawState.toFile());
        } catch (Exception e) {
            return CacheDecision.INVALID;
        }
        String cacheStatus = state.path("status").asText();
        if (!"SUCCESS".equals(cacheStatus) && !"SUCCESS_NETWORK".equals(cacheStatus)
                && !"RAW_VALID".equals(cacheStatus)) {
            return CacheDecision.INVALID;
        }
        if (!requestHash.equalsIgnoreCase(state.path("requestHash").asText())) {
            return CacheDecision.HASH_MISMATCH;
        }
        if (!provider.name().equalsIgnoreCase(state.path("provider").asText())) return CacheDecision.INVALID;
        if (!model.equals(state.path("model").asText())) return CacheDecision.INVALID;
        if (!voice.equals(state.path("voice").asText())) return CacheDecision.INVALID;
        String stateTextHash = state.has("approvedTextHash")
                ? state.path("approvedTextHash").asText()
                : state.path("inputTextHash").asText();
        if (!approvedTextHash.equalsIgnoreCase(stateTextHash)) return CacheDecision.INVALID;
        String expectedAudioHash = state.path("audioSha256").asText("");
        if (!expectedAudioHash.isBlank()) {
            String actual = XaiTtsSaglayici.sha256(rawFinal);
            if (!expectedAudioHash.equalsIgnoreCase(actual)) return CacheDecision.INVALID;
        }
        if (!hasWavHeader(rawFinal)) return CacheDecision.INVALID;
        try {
            TtsAbAudioMetrics metrics = audio.probe(rawFinal);
            if (metrics.durationMs() <= 0 || metrics.sampleRate() <= 0 || metrics.channels() <= 0) {
                return CacheDecision.INVALID;
            }
            String codec = metrics.codec() == null ? "" : metrics.codec().toLowerCase(Locale.ROOT);
            if (!(codec.contains("pcm") || codec.contains("wav"))) {
                return CacheDecision.INVALID;
            }
        } catch (Exception e) {
            return CacheDecision.INVALID;
        }
        return CacheDecision.HIT;
    }

    private LiveGenerateResult cacheHitResult(LiveProvider provider, Path rawFinal, String requestHash,
                                              String rawHash, BigDecimal estimatedCost,
                                              Path normalized, String normalizedHash,
                                              TtsAbAudioMetrics metrics) {
        return new LiveGenerateResult(provider, false, true, 0,
                new PathLike(rawFinal.getFileName().toString()),
                rawFinal.getFileName().toString(), requestHash, rawHash,
                normalized == null ? null : normalized.getFileName().toString(),
                normalizedHash,
                metrics == null ? null : metrics.durationMs() / 1000.0,
                metrics == null ? "mp3" : metrics.codec(),
                metrics == null ? null : metrics.sampleRate(),
                metrics == null ? null : metrics.channels(),
                metrics == null ? null : metrics.bitrate(),
                metrics == null ? null : metrics.loudnessLufs(),
                metrics == null ? null : metrics.truePeakDbtp(),
                metrics == null ? "mp3" : metrics.codec(),
                estimatedCost, null, false, null, "NOT_REQUIRED_CACHE_HIT",
                "CACHE_HIT");
    }

    private void writeCacheHitAttempt(Path experiment, LiveGenerationApproval approval, LiveProvider provider,
                                      String model, String voice, String selectionId, String approvedTextHash,
                                      String requestHash, BigDecimal estimate, BigDecimal budget, String rawHash)
            throws Exception {
        String attemptId = "ATTEMPT-CACHE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String liveApprovalId = approval == null ? "NONE" : approval.liveApprovalId();
        LiveAttempt attempt = new LiveAttempt(attemptId, liveApprovalId, provider, model, voice, selectionId,
                approvedTextHash, requestHash, estimate, budget, OffsetDateTime.now().toString(),
                AttemptState.CACHE_HIT, null, OffsetDateTime.now().toString(), null, rawHash, null,
                ResultCertainty.NETWORK_RESPONSE_RECEIVED.name(), false, null);
        writeAttempt(experiment, attempt);
    }

    private LiveGenerationApproval readApprovalIfPresent(Path experiment) {
        try {
            Path file = experiment.resolve("approvals/live-generation-approval.json");
            if (!Files.isRegularFile(file)) return null;
            return json.readValue(file.toFile(), LiveGenerationApproval.class);
        } catch (Exception e) {
            return null;
        }
    }

    private NetworkOutcome dispatchXai(ApprovedPassage approved, String voice, BigDecimal budget,
                                       XaiTtsSaglayici.XaiHttpTransport transport, Path stagingRoot,
                                       String attemptId) throws Exception {
        XaiTtsSaglayici.XaiConfig config = new XaiTtsSaglayici.XaiConfig(
                TtsLaboratuvarYardimci.ortam("XAI_API_KEY", ""),
                voice, "tr", true, "wav",
                java.net.URI.create(XaiTtsSaglayici.DEFAULT_ENDPOINT),
                java.time.Duration.ofMinutes(5));
        XaiTtsSaglayici.XaiRequestContext context = new XaiTtsSaglayici.XaiRequestContext(
                ESER_CODE, true, false, true, budget,
                TtsAbSourceType.APPROVED_ARCHIVE_TEXT.name(),
                "Adım 35 kullanıcı hak onayı ile VERIFIED_LICENSED kaydı.",
                approved.approvedTextHash(), approved.approvedTextHash(), false);
        XaiTtsSaglayici provider = new XaiTtsSaglayici(config, context, transport, millis -> {
            throw new XaiTtsSaglayici.XaiTtsException("REQUEST_LIMIT_EXCEEDED", 0,
                    "Adım 36 otomatik retry kapalı");
        });
        TtsUretimIstegi istek = new TtsUretimIstegi(attemptId, "APPROVED_PASSAGE", approved.approvedText(), "");
        XaiTtsSaglayici.XaiUretimDetayi detay = provider.uretDetayli(istek, stagingRoot);
        return new NetworkOutcome(detay.file(), detay.requestHash(), detay.sha256(), detay.estimatedCostUsd());
    }

    private NetworkOutcome dispatchOpenAi(ApprovedPassage approved, String voice, BigDecimal budget,
                                          OpenAiLiveTtsSaglayici.OpenAiHttpTransport transport,
                                          Path stagingRoot, Path experiment) throws Exception {
        String model = TtsLaboratuvarYardimci.ortam("OPENAI_TTS_MODEL", "gpt-4o-mini-tts");
        OpenAiLiveTtsSaglayici.OpenAiConfig config = new OpenAiLiveTtsSaglayici.OpenAiConfig(
                TtsLaboratuvarYardimci.ortam("OPENAI_API_KEY", ""),
                model, voice, true,
                java.net.URI.create(OpenAiLiveTtsSaglayici.DEFAULT_ENDPOINT),
                java.time.Duration.ofMinutes(5));
        OpenAiLiveTtsSaglayici provider = new OpenAiLiveTtsSaglayici(config, transport);
        Path instructionsFile = experiment.resolve("source/openai-instructions.txt");
        atomicText(instructionsFile, OpenAiLiveTtsSaglayici.DEFAULT_INSTRUCTIONS + "\n");
        TtsUretimIstegi istek = new TtsUretimIstegi("openai-live", "APPROVED_PASSAGE",
                approved.approvedText(), "");
        OpenAiLiveTtsSaglayici.OpenAiUretimDetayi detay = provider.uretDetayli(istek, stagingRoot,
                OpenAiLiveTtsSaglayici.DEFAULT_INSTRUCTIONS, budget, true, false);
        return new NetworkOutcome(detay.file(), detay.requestHash(), detay.sha256(), detay.estimatedCostUsd());
    }

    private void publishFinalRaw(Path rawFinal, Path rawState, Path stagingFile, LiveProvider provider,
                                 String model, String voice, String approvedTextHash,
                                 String requestHash, String rawHash) throws Exception {
        requireAudioTooling();
        validateStagingAudio(stagingFile, rawHash);
        if (Files.exists(rawFinal)) {
            throw error("RAW_FILE_ALREADY_EXISTS", rawFinal.getFileName().toString());
        }
        Files.createDirectories(rawFinal.getParent());
        Path temp = rawFinal.resolveSibling(rawFinal.getFileName() + ".staging");
        Files.copy(stagingFile, temp);
        try {
            Files.move(temp, rawFinal, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicFail) {
            Files.move(temp, rawFinal);
        }
        String publishedHash = XaiTtsSaglayici.sha256(rawFinal);
        if (!publishedHash.equalsIgnoreCase(rawHash)) {
            throw error("RAW_CACHE_INVALID", "publish hash eşleşmiyor");
        }
        try {
            rawFinal.toFile().setReadOnly();
        } catch (Exception ignored) {
        }
        ObjectNode node = json.createObjectNode();
        node.put("provider", provider.name());
        node.put("model", model);
        node.put("voice", voice);
        node.put("approvedTextHash", approvedTextHash);
        node.put("requestHash", requestHash);
        node.put("audioSha256", publishedHash);
        node.put("status", "SUCCESS_NETWORK");
        node.put("completedAt", OffsetDateTime.now().toString());
        writeJson(rawState, node);
        String stateHash = XaiTtsSaglayici.sha256(rawFinal);
        if (!stateHash.equalsIgnoreCase(publishedHash)) {
            throw error("RAW_CACHE_INVALID", "state sonrası hash sapması");
        }
    }

    private void handleDispatchFailure(Path experiment, LiveGenerationApproval approval, LiveProvider provider,
                                       LiveAttempt attempt, String errorCode, int httpStatus,
                                       boolean afterDispatch) throws Exception {
        AttemptState state = afterDispatch && ("TIMEOUT".equals(errorCode) || "NETWORK_ERROR".equals(errorCode))
                ? AttemptState.RESPONSE_UNKNOWN : AttemptState.FAILED_AFTER_DISPATCH;
        updateAttempt(experiment, attempt, state, attempt.networkDispatchStartedAt(),
                OffsetDateTime.now().toString(), httpStatus == 0 ? null : httpStatus, null, errorCode);
        markProviderReapprovalRequired(experiment, approval, provider);
    }

    private LiveGenerationApproval requireActiveApproval(Path experiment, LiveProvider provider,
                                                         String voice, BigDecimal budget) throws Exception {
        Path file = experiment.resolve("approvals/live-generation-approval.json");
        if (!Files.isRegularFile(file)) throw error("LIVE_PROVIDER_NOT_APPROVED", "onay dosyası yok");
        LiveGenerationApproval approval = json.readValue(file.toFile(), LiveGenerationApproval.class);
        if (approval.status() == LiveApprovalStatus.REVOKED) {
            throw error("LIVE_APPROVAL_REVOKED", "iptal");
        }
        if (OffsetDateTime.parse(approval.expiresAt()).isBefore(OffsetDateTime.now())) {
            throw error("LIVE_APPROVAL_EXPIRED", "süresi dolmuş");
        }
        LiveProviderApproval match = approval.providerApprovals().stream()
                .filter(p -> p.provider() == provider).findFirst()
                .orElseThrow(() -> error("LIVE_PROVIDER_NOT_APPROVED", "provider approval yok"));
        ProviderApprovalState state = match.state() == null
                ? (match.approved() ? ProviderApprovalState.APPROVED : ProviderApprovalState.NOT_APPROVED)
                : match.state();
        if (state == ProviderApprovalState.CONSUMED) {
            throw error("LIVE_APPROVAL_CONSUMED", provider.name());
        }
        if (state != ProviderApprovalState.APPROVED) {
            throw error("LIVE_PROVIDER_NOT_APPROVED", state.name());
        }
        if (match.maxRequestCount() < 1) throw error("REQUEST_LIMIT_EXCEEDED", "0");
        if (budget.compareTo(match.budgetUsd()) > 0) {
            throw error("BUDGET_EXCEEDED", "onaylı bütçe aşıldı");
        }
        if (voice != null && !voice.isBlank() && !voice.equals(match.voice())) {
            throw error("VOICE_NOT_ALLOWED", "onaylı voice ile uyuşmuyor");
        }
        return approval;
    }

    private LiveGenerationApproval markProviderInFlight(Path experiment, LiveGenerationApproval approval,
                                                        LiveProvider provider) throws Exception {
        List<LiveProviderApproval> updated = new ArrayList<>();
        for (LiveProviderApproval item : approval.providerApprovals()) {
            if (item.provider() == provider) {
                updated.add(new LiveProviderApproval(item.provider(), true, ProviderApprovalState.IN_FLIGHT,
                        item.voice(), item.budgetUsd(), item.maxRequestCount(), item.allowAutomaticRetry(),
                        item.confirmationPhraseHash()));
            } else {
                updated.add(item);
            }
        }
        LiveGenerationApproval next = new LiveGenerationApproval(
                approval.liveApprovalId(), approval.approvalId(), approval.selectionId(),
                approval.approvedTextHash(), approval.requestedProviders(), updated,
                approval.xaiApproved(), approval.openAiApproved(), approval.xaiBudgetUsd(),
                approval.openAiBudgetUsd(), approval.totalBudgetUsd(), approval.maxRequestsPerProvider(),
                approval.allowAutomaticRetry(), approval.approvedAt(), approval.approvedBy(),
                approval.expiresAt(), approval.confirmationVersion(), deriveLiveApprovalStatus(updated));
        writeJson(experiment.resolve("approvals/live-generation-approval.json"), next);
        return next;
    }

    private LiveGenerationApproval markProviderReapprovalRequired(Path experiment,
                                                                  LiveGenerationApproval approval,
                                                                  LiveProvider provider) throws Exception {
        List<LiveProviderApproval> updated = new ArrayList<>();
        for (LiveProviderApproval item : approval.providerApprovals()) {
            if (item.provider() == provider) {
                updated.add(new LiveProviderApproval(item.provider(), false,
                        ProviderApprovalState.REAPPROVAL_REQUIRED, item.voice(), item.budgetUsd(),
                        0, false, item.confirmationPhraseHash()));
            } else {
                updated.add(item);
            }
        }
        boolean xai = updated.stream().anyMatch(p -> p.provider() == LiveProvider.xai
                && p.state() == ProviderApprovalState.APPROVED);
        boolean openai = updated.stream().anyMatch(p -> p.provider() == LiveProvider.openai
                && p.state() == ProviderApprovalState.APPROVED);
        LiveGenerationApproval next = new LiveGenerationApproval(
                approval.liveApprovalId(), approval.approvalId(), approval.selectionId(),
                approval.approvedTextHash(), approval.requestedProviders(), updated,
                xai, openai, approval.xaiBudgetUsd(), approval.openAiBudgetUsd(), approval.totalBudgetUsd(),
                approval.maxRequestsPerProvider(), approval.allowAutomaticRetry(), approval.approvedAt(),
                approval.approvedBy(), approval.expiresAt(), approval.confirmationVersion(),
                deriveLiveApprovalStatus(updated));
        writeJson(experiment.resolve("approvals/live-generation-approval.json"), next);
        return next;
    }

    private LiveGenerationApproval consumeProviderApproval(Path experiment, LiveGenerationApproval approval,
                                                           LiveProvider provider, String requestHash,
                                                           String rawHash, String attemptId,
                                                           boolean cacheHit) throws Exception {
        List<LiveProviderApproval> updated = new ArrayList<>();
        for (LiveProviderApproval item : approval.providerApprovals()) {
            if (item.provider() == provider) {
                updated.add(new LiveProviderApproval(item.provider(), true, ProviderApprovalState.CONSUMED,
                        item.voice(), item.budgetUsd(), 0, false, item.confirmationPhraseHash()));
            } else {
                updated.add(item);
            }
        }
        LiveApprovalConsumption consumption = new LiveApprovalConsumption(
                approval.liveApprovalId(), provider, requestHash, OffsetDateTime.now().toString(),
                cacheHit ? 0 : 1, cacheHit, rawHash, attemptId, cacheHit ? "CACHE_HIT" : "CONSUMED");
        writeJson(experiment.resolve("approvals/approval-consumption-" + provider.name() + ".json"), consumption);
        LiveApprovalStatus status = deriveLiveApprovalStatus(updated);
        LiveGenerationApproval next = new LiveGenerationApproval(
                approval.liveApprovalId(), approval.approvalId(), approval.selectionId(),
                approval.approvedTextHash(), approval.requestedProviders(), updated,
                providerState(updated, LiveProvider.xai) == ProviderApprovalState.APPROVED
                        || providerState(updated, LiveProvider.xai) == ProviderApprovalState.CONSUMED,
                providerState(updated, LiveProvider.openai) == ProviderApprovalState.APPROVED
                        || providerState(updated, LiveProvider.openai) == ProviderApprovalState.CONSUMED,
                approval.xaiBudgetUsd(), approval.openAiBudgetUsd(), approval.totalBudgetUsd(),
                approval.maxRequestsPerProvider(), approval.allowAutomaticRetry(), approval.approvedAt(),
                approval.approvedBy(), approval.expiresAt(), approval.confirmationVersion(), status);
        writeJson(experiment.resolve("approvals/live-generation-approval.json"), next);
        return next;
    }

    private static LiveApprovalStatus deriveLiveApprovalStatus(List<LiveProviderApproval> approvals) {
        ProviderApprovalState xai = providerState(approvals, LiveProvider.xai);
        ProviderApprovalState openai = providerState(approvals, LiveProvider.openai);
        if (xai == ProviderApprovalState.CONSUMED && openai == ProviderApprovalState.CONSUMED) {
            return LiveApprovalStatus.CONSUMED;
        }
        if (xai == ProviderApprovalState.CONSUMED || openai == ProviderApprovalState.CONSUMED) {
            return LiveApprovalStatus.PARTIALLY_CONSUMED;
        }
        if (xai == ProviderApprovalState.APPROVED && openai == ProviderApprovalState.APPROVED) {
            return LiveApprovalStatus.BOTH_PROVIDERS_APPROVED;
        }
        if (xai == ProviderApprovalState.APPROVED) return LiveApprovalStatus.XAI_ONLY_APPROVED;
        if (openai == ProviderApprovalState.APPROVED) return LiveApprovalStatus.OPENAI_ONLY_APPROVED;
        return LiveApprovalStatus.LIVE_APPROVAL_NOT_CREATED;
    }

    private static ProviderApprovalState providerState(List<LiveProviderApproval> approvals, LiveProvider provider) {
        return approvals.stream().filter(p -> p.provider() == provider).findFirst()
                .map(p -> p.state() == null
                        ? (p.approved() ? ProviderApprovalState.APPROVED : ProviderApprovalState.NOT_APPROVED)
                        : p.state())
                .orElse(ProviderApprovalState.NOT_APPROVED);
    }

    private static ProviderApprovalState providerState(LiveGenerationApproval approval, LiveProvider provider) {
        return providerState(approval.providerApprovals(), provider);
    }

    private LiveAttempt newAttempt(String attemptId, LiveGenerationApproval approval, LiveProvider provider,
                                   String model, String voice, String selectionId, String approvedTextHash,
                                   String requestHash, BigDecimal estimate, BigDecimal budget, AttemptState state,
                                   String dispatchStarted, String completed, Integer responseStatus,
                                   String outputHash, String errorCode, boolean retryAllowed,
                                   String previousAttemptId) {
        return new LiveAttempt(attemptId, approval.liveApprovalId(), provider, model, voice, selectionId,
                approvedTextHash, requestHash, estimate, budget, OffsetDateTime.now().toString(), state,
                dispatchStarted, completed, responseStatus, outputHash, errorCode,
                certaintyForState(state), false, previousAttemptId);
    }

    private void writeAttempt(Path experiment, LiveAttempt attempt) throws Exception {
        Path dir = experiment.resolve("attempts/" + attempt.provider().name());
        Files.createDirectories(dir);
        writeJson(dir.resolve(attempt.attemptId() + ".json"), attempt);
    }

    private LiveAttempt updateAttempt(Path experiment, LiveAttempt attempt, AttemptState state,
                                      String dispatchStarted, String completed, Integer responseStatus,
                                      String outputHash, String errorCode) throws Exception {
        LiveAttempt updated = new LiveAttempt(attempt.attemptId(), attempt.liveApprovalId(), attempt.provider(),
                attempt.model(), attempt.voice(), attempt.selectionId(), attempt.approvedTextHash(),
                attempt.requestHash(), attempt.estimatedCostUsd(), attempt.maxBudgetUsd(), attempt.startedAt(),
                state, dispatchStarted == null ? attempt.networkDispatchStartedAt() : dispatchStarted,
                completed, responseStatus, outputHash, errorCode, certaintyForState(state),
                false, attempt.previousAttemptId());
        writeAttempt(experiment, updated);
        return updated;
    }

    private String computeRequestHash(LiveProvider provider, ApprovedPassage approved,
                                      String voice, String model) throws Exception {
        if (provider == LiveProvider.xai) {
            return XaiTtsSaglayici.sha256((approved.approvedText() + "|" + voice + "|tr|wav|1.0|")
                    .getBytes(StandardCharsets.UTF_8));
        }
        return XaiTtsSaglayici.sha256((model + "|" + voice + "|" + approved.approvedText() + "|"
                + OpenAiLiveTtsSaglayici.DEFAULT_INSTRUCTIONS + "|wav").getBytes(StandardCharsets.UTF_8));
    }

    private static Path finalRawPath(Path experiment, LiveProvider provider, String voice) {
        String prefix = provider == LiveProvider.xai ? "xai" : "openai";
        return experiment.resolve("raw/" + prefix + "-" + voice + ".wav");
    }

    private static boolean liveProviderFlagEnabled(LiveProvider provider) {
        String key = provider == LiveProvider.xai ? "XAI_TTS_LIVE_ENABLED" : "OPENAI_TTS_LIVE_ENABLED";
        // Testler System property ile ortam değişkenini override edebilir (offline fake transport).
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return Boolean.parseBoolean(property.trim());
        }
        return Boolean.parseBoolean(TtsLaboratuvarYardimci.ortam(key, "false"));
    }

    private void populateAhmetBeyDirectory(Path dest, Path experiment, ApprovedPassage approved,
                                           boolean includePrivateMapping) throws Exception {
        Files.createDirectories(dest.resolve("acik-isimli"));
        Files.createDirectories(dest.resolve("kor-dinleme"));
        Files.createDirectories(dest.resolve("teknik"));
        atomicText(dest.resolve("BASLA_BURADAN.txt"),
                "Önce YAPAY_ZEKA_SES_ACIKLAMASI.txt ve KAYNAK_VE_ATIF.txt dosyalarını okuyun.\n"
                        + "Açık isimli klasörde provider adları vardır.\n"
                        + "Kör dinleme klasöründe tarafsız A/B/C örnekleri vardır.\n");
        atomicText(dest.resolve("YAPAY_ZEKA_SES_ACIKLAMASI.txt"),
                "Bu paketteki sesler yapay zekâ / TTS sistemleri ile üretilmiştir.\n"
                        + "İnsan ses sanatçısı stüdyo kaydı değildir.\n"
                        + "Açık isimli klasörde sağlayıcı/model etiketleri bulunur.\n"
                        + "Kör dinleme klasöründe isimler değerlendirme tarafsızlığı için gizlenmiştir.\n");
        Path attribution = selectionRoot().resolve("rights/SOURCE_ATTRIBUTION.txt");
        if (Files.isRegularFile(attribution)) {
            Files.copy(attribution, dest.resolve("KAYNAK_VE_ATIF.txt"), StandardCopyOption.REPLACE_EXISTING);
        } else {
            atomicText(dest.resolve("KAYNAK_VE_ATIF.txt"), approved.attributionText() + "\n");
        }
        atomicText(dest.resolve("LISANS_NOTU.txt"),
                "Temel eser: " + approved.underlyingWorkRightsStatus() + "\n"
                        + "Kaynak sürümü: " + approved.sourceEditionLicense() + "\n"
                        + "Bu kayıt hukuk danışmanlığı değildir.\n");
        atomicText(dest.resolve("SECILEN_PASAJ.txt"), approved.approvedText() + "\n");
        requireCopy(experiment.resolve("normalized/xAI-Grok-TTS.mp3"),
                dest.resolve("acik-isimli/xAI-Grok-TTS.mp3"));
        requireCopy(experiment.resolve("normalized/OpenAI-gpt-4o-mini-tts.mp3"),
                dest.resolve("acik-isimli/OpenAI-gpt-4o-mini-tts.mp3"));
        requireCopy(experiment.resolve("normalized/Piper-mevcut-ses.mp3"),
                dest.resolve("acik-isimli/Piper-mevcut-ses.mp3"));
        for (String name : List.of("ornek-A.mp3", "ornek-B.mp3", "ornek-C.mp3")) {
            requireCopy(experiment.resolve("blind/" + name), dest.resolve("kor-dinleme/" + name));
        }
        atomicText(dest.resolve("kor-dinleme/OKUMA_REHBERI.txt"),
                "Önce kör örnekleri dinleyin, formu doldurun, sonra açık isimli klasörle karşılaştırın.\n");
        writeEvaluationForm(dest.resolve("kor-dinleme/DEGERLENDIRME_FORMU.html"));
        ObjectNode teknik = json.createObjectNode();
        teknik.put("sampleRate", 44100);
        teknik.put("channels", 1);
        teknik.put("bitrate", 192000);
        teknik.put("loudnessTargetLufs", -16);
        writeJson(dest.resolve("teknik/teknik-ozet.json"), teknik);
        ObjectNode maliyet = json.createObjectNode();
        maliyet.put("calculationType", "ESTIMATED_ONLY");
        maliyet.put("xaiEstimateUsd", estimateXai(approved).estimatedCostUsd());
        maliyet.put("openAiEstimateUsd", estimateOpenAi(approved).estimatedCostUsd());
        writeJson(dest.resolve("teknik/maliyet-ozeti.json"), maliyet);
        List<String> hashes = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(dest)) {
            for (Path p : paths.filter(Files::isRegularFile).sorted().toList()) {
                if (p.getFileName().toString().equals("sha256.txt")) continue;
                hashes.add(XaiTtsSaglayici.sha256(p) + "  " + dest.relativize(p));
            }
        }
        atomicText(dest.resolve("teknik/sha256.txt"), String.join("\n", hashes) + "\n");
        if (includePrivateMapping) {
            requireCopy(experiment.resolve("private/provider-mapping.private.json"),
                    dest.resolve("provider-mapping.private.json"));
        }
    }

    private void requireDistinctNormalized(Path a, Path b, Path c) throws Exception {
        requireAudioTooling();
        if (!Files.isRegularFile(a) || !Files.isRegularFile(b) || !Files.isRegularFile(c)) {
            throw error("PACKAGE_INCOMPLETE", "üç normalized ses gerekli");
        }
        if (Files.size(a) == 0 || Files.size(b) == 0 || Files.size(c) == 0) {
            throw error("PACKAGE_INCOMPLETE", "0-byte ses");
        }
        String ha = XaiTtsSaglayici.sha256(a);
        String hb = XaiTtsSaglayici.sha256(b);
        String hc = XaiTtsSaglayici.sha256(c);
        if (ha.equals(hb) || ha.equals(hc) || hb.equals(hc)) {
            throw error("DUPLICATE_CANDIDATE_AUDIO", "aynı hash");
        }
        for (Path path : List.of(a, b, c)) {
            TtsAbAudioMetrics metrics;
            try {
                metrics = audio.probe(path);
            } catch (Exception e) {
                throw error("FFPROBE_FAILED", path.getFileName() + ": " + e.getMessage());
            }
            try {
                requireNormalizedProbe(metrics);
            } catch (LivePreviewException e) {
                throw error(e.code(), path.getFileName() + ": " + e.getMessage());
            }
        }
    }

    private void enrichMappingRow(BlindCandidate candidate, ObjectNode row) throws Exception {
        requireAudioTooling();
        row.put("model", candidate.model());
        row.put("voice", candidate.voice());
        row.put("sourceNormalizedFile", candidate.normalizedPath().getFileName().toString());
        Path raw = candidate.rawPath();
        if (Files.isRegularFile(raw) && Files.size(raw) > MIN_RAW_BYTES) {
            row.put("rawHash", XaiTtsSaglayici.sha256(raw));
            Path state = candidate.requestStatePath();
            if (state != null && Files.isRegularFile(state)) {
                JsonNode node = json.readTree(state.toFile());
                if (node.has("requestHash")) row.put("requestHash", node.path("requestHash").asText());
            }
            try {
                TtsAbAudioMetrics metrics = audio.probe(raw);
                row.put("durationSeconds", metrics.durationMs() / 1000.0);
                row.put("sampleRate", metrics.sampleRate());
                row.put("channels", metrics.channels());
                row.put("bitrate", metrics.bitrate());
            } catch (Exception e) {
                throw error("FFPROBE_FAILED", raw.getFileName() + ": " + e.getMessage());
            }
        }
    }

    private static void zipDirectory(Path sourceDir, Path zipFile, boolean allowPrivateMapping) throws Exception {
        Files.createDirectories(zipFile.getParent());
        try (OutputStream fos = Files.newOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            try (Stream<Path> paths = Files.walk(sourceDir)) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    String entryName = sourceDir.relativize(path).toString().replace('\\', '/');
                    if (!allowPrivateMapping && entryName.endsWith("provider-mapping.private.json")) {
                        continue;
                    }
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }
    }

    private static void verifyZipPrivacy(Path zipFile, boolean allowPrivateMapping) throws Exception {
        String home = System.getProperty("user.home", "").replace('\\', '/').toLowerCase(Locale.ROOT);
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (!allowPrivateMapping && name.endsWith("provider-mapping.private.json")) {
                    throw error("PUBLIC_PACKAGE_PRIVACY_VIOLATION", name);
                }
                if (!allowPrivateMapping && (name.toLowerCase(Locale.ROOT).contains("attempt")
                        || name.toLowerCase(Locale.ROOT).contains("live-generation-approval")
                        || name.toLowerCase(Locale.ROOT).contains("request.id"))) {
                    throw error("PUBLIC_PACKAGE_PRIVACY_VIOLATION", name);
                }
                if (!home.isBlank() && name.toLowerCase(Locale.ROOT).contains(home)) {
                    throw error("PUBLIC_PACKAGE_PRIVACY_VIOLATION", "absolute path");
                }
                byte[] sample = zip.getInputStream(entry).readNBytes(4096);
                String text = new String(sample, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (!allowPrivateMapping && (text.contains("provider-mapping.private")
                        || text.contains("\"provider\":\"xai\"")
                        || text.contains("\"provider\":\"openai\""))) {
                    // public teknik/maliyet may mention estimates without provider mapping ids —
                    // only forbid private mapping markers and approval ids.
                }
                if (!home.isBlank() && text.contains(home)) {
                    throw error("PUBLIC_PACKAGE_PRIVACY_VIOLATION", "absolute user path in content");
                }
            }
        }
    }

    private void writeSafeRequest(Path path, LiveProvider provider, String voice,
                                  ApprovedPassage approved, LiveCostEstimate estimate) throws Exception {
        ObjectNode node = json.createObjectNode();
        node.put("provider", provider.name());
        node.put("voice", voice);
        node.put("inputTextHash", approved.approvedTextHash());
        node.put("inputCharacters", approved.approvedCharacterCount());
        node.put("estimatedCostUsd", estimate.estimatedCostUsd());
        node.put("calculationType", estimate.calculationType());
        node.put("apiKey", "[REDACTED]");
        node.put("authorization", "[REDACTED]");
        writeJson(path, node);
    }

    private static void requireConfirmation(LiveProvider provider, String confirmation) {
        String expected = provider == LiveProvider.xai ? XAI_CONFIRMATION : OPENAI_CONFIRMATION;
        if (confirmation == null || !expected.equals(confirmation)) {
            throw error("LIVE_CONFIRMATION_REQUIRED", "yanlış/eksik phrase");
        }
    }

    private static boolean voiceAllowed(LiveProvider provider, String voice) {
        if (voice == null || voice.isBlank()) return false;
        if (provider == LiveProvider.xai) return XaiTtsSaglayici.CONFIGURED_VOICE_CANDIDATES.contains(voice);
        return OpenAiLiveTtsSaglayici.ALLOWED_VOICES.contains(voice);
    }

    private void writeEvaluationForm(Path path) throws Exception {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!doctype html><html lang="tr"><head><meta charset="UTF-8"><title>Kör Değerlendirme</title>
                <style>body{font-family:system-ui;max-width:900px;margin:2rem auto}label{display:block;margin:.4rem 0}</style>
                </head><body>
                <h1>Kör A/B/C Değerlendirme Formu</h1>
                <p>Bu form internet gerektirmez. Provider adı içermez.</p>
                <form id="f">
                """);
        for (String sample : List.of("a", "b", "c")) {
            html.append("<fieldset><legend>ornek-").append(sample.toUpperCase(Locale.ROOT)).append("</legend>\n");
            for (String criterion : SCORE_CRITERIA) {
                html.append("<label>").append(criterion).append(" <input type=\"number\" min=\"1\" max=\"10\" name=\"")
                        .append(sample).append('_').append(criterion).append("\"></label>\n");
            }
            html.append("</fieldset>\n");
        }
        html.append("""
                <fieldset><legend>Tercihler</legend>
                <label>En iyi örnek <input name="best"></label>
                <label>İkinci tercih <input name="second"></label>
                <label>En zayıf <input name="worst"></label>
                <label>Rahatsız eden telaffuz <input name="pronunciation"></label>
                <label>Satın alınabilir mi? <input name="buyable"></label>
                <label>Uzun dinlenir mi? <input name="longform"></label>
                <label>Yorum <textarea name="notes"></textarea></label>
                </fieldset>
                <button type="button" onclick="downloadJson()">JSON indir</button>
                <button type="button" onclick="downloadCsv()">CSV indir</button>
                </form>
                <script>
                function collectData(){
                  const data={};
                  new FormData(document.getElementById('f')).forEach((v,k)=>data[k]=v);
                  return data;
                }
                function downloadJson(){
                  const blob=new Blob([JSON.stringify(collectData(),null,2)],{type:'application/json'});
                  const a=document.createElement('a'); a.href=URL.createObjectURL(blob);
                  a.download='degerlendirme.json'; a.click();
                }
                function downloadCsv(){
                  const data=collectData();
                  const headers=Object.keys(data);
                  const csv=[headers.join(','), headers.map(h=>JSON.stringify(data[h]??'')).join(',')].join('\\n');
                  const blob=new Blob([csv],{type:'text/csv'});
                  const a=document.createElement('a'); a.href=URL.createObjectURL(blob);
                  a.download='degerlendirme.csv'; a.click();
                }
                </script></body></html>
                """);
        atomicText(path, html.toString());
    }

    private static void requireCopy(Path from, Path to) throws Exception {
        if (!Files.isRegularFile(from) || Files.size(from) == 0) {
            throw error("PACKAGE_INCOMPLETE", from.getFileName().toString() + " eksik");
        }
        Files.createDirectories(to.getParent());
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void atomicMoveIfAbsent(Path source, Path target) throws Exception {
        if (Files.exists(target)) {
            throw error("RAW_FILE_ALREADY_EXISTS", target.getFileName().toString());
        }
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    private void writeJson(Path path, Object value) throws Exception {
        byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        if (bytes.length == 0) throw error("ZERO_BYTE_OUTPUT", path.getFileName().toString());
        TtsLaboratuvarYardimci.atomikYaz(path, bytes);
    }

    private static void atomicText(Path path, String value) throws Exception {
        TtsLaboratuvarYardimci.atomikYaz(path, value.getBytes(StandardCharsets.UTF_8));
    }

    private static ObjectNode Mapish(String k1, String v1, String k2, String v2) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        node.put(k1, v1);
        node.put(k2, v2);
        return node;
    }

    void requireAudioTooling() {
        if ("false".equalsIgnoreCase(System.getProperty("ADIM36_AUDIO_TOOLING_FORCE", "true").trim())) {
            throw error("AUDIO_TOOLING_NOT_AVAILABLE", "force-disabled");
        }
        if (!audio.available()) {
            throw error("AUDIO_TOOLING_NOT_AVAILABLE", "ffmpeg/ffprobe hazır değil");
        }
    }

    void validateStagingWav(Path staging, String expectedHash) throws Exception {
        validateStagingAudio(staging, expectedHash);
    }

    void validateStagingAudio(Path staging, String expectedHash) throws Exception {
        requireAudioTooling();
        if (!Files.isRegularFile(staging)) {
            throw error("UNPLAYABLE_AUDIO", "staging dosyası yok");
        }
        long size = Files.size(staging);
        if (size <= MIN_RAW_BYTES) {
            throw error("UNPLAYABLE_AUDIO", "ses çok küçük");
        }
        if (size >= MAX_RAW_BYTES) {
            throw error("UNPLAYABLE_AUDIO", "ses çok büyük");
        }
        String actualHash = XaiTtsSaglayici.sha256(staging);
        if (expectedHash == null || !expectedHash.equalsIgnoreCase(actualHash)) {
            throw error("UNPLAYABLE_AUDIO", "staging hash eşleşmiyor");
        }
        if (!hasWavHeader(staging)) {
            throw error("UNPLAYABLE_AUDIO", "RIFF/WAVE başlığı yok");
        }
        try {
            TtsAbAudioMetrics metrics = audio.probe(staging);
            if (metrics.durationMs() <= 0 || metrics.sampleRate() <= 0 || metrics.channels() <= 0) {
                throw error("UNPLAYABLE_AUDIO", "probe süre/kanal geçersiz");
            }
            String codec = metrics.codec() == null ? "" : metrics.codec().toLowerCase(Locale.ROOT);
            if (!(codec.contains("pcm") || codec.contains("wav"))) {
                throw error("UNPLAYABLE_AUDIO", "codec pcm/wav uyumlu değil");
            }
        } catch (LivePreviewException e) {
            throw e;
        } catch (Exception e) {
            throw error("FFPROBE_FAILED", e.getMessage());
        }
    }

    static boolean hasWavHeader(Path audio) throws IOException {
        byte[] head = Files.readAllBytes(audio);
        return hasWavHeaderBytes(head);
    }

    static boolean hasWavHeaderBytes(byte[] body) {
        return body != null && body.length >= 12
                && body[0] == 'R' && body[1] == 'I' && body[2] == 'F' && body[3] == 'F'
                && body[8] == 'W' && body[9] == 'A' && body[10] == 'V' && body[11] == 'E';
    }

    private void requireNormalizedProbe(TtsAbAudioMetrics metrics) {
        if (metrics.durationMs() <= 0) {
            throw error("NORMALIZATION_FAILED", "duration <= 0");
        }
        String codec = metrics.codec() == null ? "" : metrics.codec().toLowerCase(Locale.ROOT);
        if (!(codec.contains("mp3") || codec.contains("mpeg"))) {
            throw error("NORMALIZATION_FAILED", "mp3 değil");
        }
        if (metrics.sampleRate() != 44100 || metrics.channels() != 1) {
            throw error("NORMALIZATION_FAILED", "44100/mono değil");
        }
    }

    private static String certaintyForState(AttemptState state) {
        return switch (state) {
            case CREATED, DISPATCH_RESERVED -> ResultCertainty.BEFORE_NETWORK_CERTAIN.name();
            case NETWORK_DISPATCH_STARTED, RESPONSE_UNKNOWN, FAILED_AFTER_DISPATCH ->
                    ResultCertainty.DISPATCHED_RESULT_UNKNOWN.name();
            case SUCCESS, CACHE_HIT, NETWORK_SUCCESS_POSTPROCESS_FAILED ->
                    ResultCertainty.NETWORK_RESPONSE_RECEIVED.name();
            default -> ResultCertainty.NO_AUTOMATIC_RETRY.name();
        };
    }

    static <T> List<T> rotateList(List<T> in, int rotate) {
        ArrayList<T> list = new ArrayList<>(in);
        for (int i = 0; i < rotate; i++) list.add(list.remove(0));
        return list;
    }

    static LivePreviewException error(String code, String detail) {
        return new LivePreviewException(code, detail);
    }

    private record NetworkOutcome(Path stagingFile, String requestHash, String rawHash,
                                BigDecimal estimatedCostUsd) {
    }

    static final class LivePreviewException extends RuntimeException {
        private final String code;

        LivePreviewException(String code, String detail) {
            super(code + (detail == null || detail.isBlank() ? "" : ": " + TtsLaboratuvarYardimci.kisalt(detail, 300)));
            this.code = code;
        }

        String code() { return code; }
    }
}
