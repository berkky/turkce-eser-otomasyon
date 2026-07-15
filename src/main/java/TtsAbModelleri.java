import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

enum TtsAbExperimentMode {
    RAW_BASELINE,
    DIRECTED_COMMERCIAL,
    EDITORIAL_ADAPTATION
}

enum TtsAbSourceType {
    FIXTURE,
    APPROVED_ARCHIVE_TEXT
}

record TtsAbSourceText(
        String eserId,
        TtsAbSourceType sourceType,
        String text,
        String sourceTextHash,
        int sourceCharacterCount,
        int sourceWordCount,
        String sourceLicenseNote,
        boolean userApproved) {
}

record TtsAbCandidate(
        String providerCode,
        String modelName,
        String voiceId,
        String language,
        String instructionOrStyle,
        List<String> speechTagsUsed,
        String configurationStatus) {
    TtsAbCandidate {
        speechTagsUsed = speechTagsUsed == null ? List.of() : List.copyOf(speechTagsUsed);
        instructionOrStyle = instructionOrStyle == null ? "" : instructionOrStyle;
        configurationStatus = configurationStatus == null ? "NOT_CONFIGURED" : configurationStatus;
    }
}

record TtsAbExperiment(
        String experimentId,
        String eserId,
        long seed,
        TtsAbExperimentMode mode,
        BigDecimal budgetUsd,
        boolean dryRun,
        OffsetDateTime createdAt,
        TtsAbSourceText sourceText,
        List<TtsAbCandidate> candidates) {
    TtsAbExperiment {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}

record TtsAbAudioMetrics(
        long durationMs,
        long fileSizeBytes,
        String codec,
        int sampleRate,
        int channels,
        long bitrate,
        Double loudnessLufs,
        Double truePeakDbtp,
        String sha256) {
}

record TtsAbGenerationResult(
        String experimentId,
        String eserId,
        String sourceTextHash,
        int sourceCharacterCount,
        int sourceWordCount,
        String sourceLicenseNote,
        String providerCode,
        String modelName,
        String voiceId,
        String language,
        String requestHash,
        String liveOrMock,
        String instructionOrStyle,
        List<String> speechTagsUsed,
        OffsetDateTime generationStartedAt,
        OffsetDateTime generationCompletedAt,
        long durationMs,
        long fileSizeBytes,
        String sha256,
        BigDecimal estimatedCostUsd,
        BigDecimal actualCostUsd,
        String outputFormat,
        Integer sampleRate,
        Long bitrate,
        Double loudnessLufs,
        Double truePeakDbtp,
        String normalizedFile,
        String normalizedSha256,
        String validationStatus,
        String errorCode,
        int retryCount,
        Path rawFile) {
    TtsAbGenerationResult {
        speechTagsUsed = speechTagsUsed == null ? List.of() : List.copyOf(speechTagsUsed);
    }
}

record TtsAbEvaluation(
        String experimentId,
        String submissionId,
        String blindCode,
        int naturalness,
        int turkishPronunciation,
        int emotion,
        int continueListening,
        int commercialQuality,
        int fatigue,
        String overallPreference,
        String mispronouncedWords,
        boolean listen20Minutes,
        boolean listenFullBook,
        String comment,
        OffsetDateTime submittedAt) {
}

record TtsAbExperimentManifest(
        String experimentId,
        String eserId,
        TtsAbExperimentMode mode,
        long seed,
        BigDecimal budgetUsd,
        BigDecimal estimatedTotalCostUsd,
        OffsetDateTime createdAt,
        TtsAbSourceType sourceType,
        String sourceTextHash,
        int sourceCharacterCount,
        int sourceWordCount,
        String sourceLicenseNote,
        List<TtsAbGenerationResult> generations) {
    TtsAbExperimentManifest {
        generations = generations == null ? List.of() : List.copyOf(generations);
    }
}
