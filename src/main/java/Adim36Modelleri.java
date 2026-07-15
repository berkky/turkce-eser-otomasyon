import java.math.BigDecimal;
import java.util.List;

enum LiveApprovalStatus {
    LIVE_APPROVAL_NOT_CREATED,
    XAI_ONLY_APPROVED,
    OPENAI_ONLY_APPROVED,
    BOTH_PROVIDERS_APPROVED,
    PARTIALLY_CONSUMED,
    EXPIRED,
    CONSUMED,
    REVOKED
}

enum ProviderApprovalState {
    NOT_APPROVED,
    APPROVED,
    IN_FLIGHT,
    CONSUMED,
    REAPPROVAL_REQUIRED,
    REVOKED,
    EXPIRED
}

enum AttemptState {
    CREATED,
    DISPATCH_RESERVED,
    NETWORK_DISPATCH_STARTED,
    SUCCESS,
    FAILED_BEFORE_NETWORK,
    FAILED_AFTER_DISPATCH,
    RESPONSE_UNKNOWN,
    CACHE_HIT,
    NETWORK_SUCCESS_POSTPROCESS_FAILED
}

enum ResultCertainty {
    BEFORE_NETWORK_CERTAIN,
    DISPATCHED_RESULT_UNKNOWN,
    NETWORK_RESPONSE_RECEIVED,
    NO_AUTOMATIC_RETRY
}

/** Kör dinleme adayı — rotate bu liste üzerinde yapılır; provider alanları birbirinden ayrılmaz. */
record BlindCandidate(
        LiveProvider provider,
        String model,
        String voice,
        java.nio.file.Path normalizedPath,
        java.nio.file.Path rawPath,
        java.nio.file.Path requestStatePath) {
}

enum LiveProvider {
    xai,
    openai,
    piper
}

record LiveProviderApproval(
        LiveProvider provider,
        boolean approved,
        ProviderApprovalState state,
        String voice,
        BigDecimal budgetUsd,
        int maxRequestCount,
        boolean allowAutomaticRetry,
        String confirmationPhraseHash) {
    LiveProviderApproval {
        if (state == null) {
            state = approved ? ProviderApprovalState.APPROVED : ProviderApprovalState.NOT_APPROVED;
        }
    }

    static LiveProviderApproval legacy(LiveProvider provider, boolean approved, String voice,
                                       BigDecimal budgetUsd, int maxRequestCount,
                                       boolean allowAutomaticRetry, String confirmationPhraseHash) {
        return new LiveProviderApproval(provider, approved,
                approved ? ProviderApprovalState.APPROVED : ProviderApprovalState.NOT_APPROVED,
                voice, budgetUsd, maxRequestCount, allowAutomaticRetry, confirmationPhraseHash);
    }
}

record LiveGenerationApproval(
        String liveApprovalId,
        String approvalId,
        String selectionId,
        String approvedTextHash,
        List<String> requestedProviders,
        List<LiveProviderApproval> providerApprovals,
        boolean xaiApproved,
        boolean openAiApproved,
        BigDecimal xaiBudgetUsd,
        BigDecimal openAiBudgetUsd,
        BigDecimal totalBudgetUsd,
        int maxRequestsPerProvider,
        boolean allowAutomaticRetry,
        String approvedAt,
        String approvedBy,
        String expiresAt,
        int confirmationVersion,
        LiveApprovalStatus status) {
    LiveGenerationApproval {
        requestedProviders = requestedProviders == null ? List.of() : List.copyOf(requestedProviders);
        providerApprovals = providerApprovals == null ? List.of() : List.copyOf(providerApprovals);
    }
}

record LiveAttempt(
        String attemptId,
        String liveApprovalId,
        LiveProvider provider,
        String model,
        String voice,
        String selectionId,
        String approvedTextHash,
        String requestHash,
        BigDecimal estimatedCostUsd,
        BigDecimal maxBudgetUsd,
        String startedAt,
        AttemptState state,
        String networkDispatchStartedAt,
        String completedAt,
        Integer responseStatus,
        String outputHash,
        String errorCode,
        String resultCertainty,
        boolean retryAllowed,
        String previousAttemptId) {
}

record LiveApprovalConsumption(
        String liveApprovalId,
        LiveProvider provider,
        String requestHash,
        String consumedAt,
        int requestCount,
        boolean cacheHit,
        String rawAudioSha256,
        String attemptId,
        String status) {
}

record LiveCostEstimate(
        LiveProvider provider,
        int inputCharacters,
        int estimatedSpeechSeconds,
        BigDecimal estimatedCostUsd,
        String currency,
        String calculationType,
        String note) {
}

record LiveGenerateResult(
        LiveProvider provider,
        boolean calledNetwork,
        boolean cacheHit,
        int requestCount,
        PathLike outputPath,
        String rawPathSafe,
        String requestHash,
        String rawHash,
        String normalizedPathSafe,
        String normalizedHash,
        Double durationSeconds,
        String codec,
        Integer sampleRate,
        Integer channels,
        Long bitrate,
        Double integratedLufs,
        Double truePeakDbtp,
        String format,
        BigDecimal estimatedCostUsd,
        BigDecimal actualCostUsd,
        boolean approvalConsumed,
        String attemptId,
        String approvalState,
        String status) {
}

/** Path as string for JSON-safe reporting without absolute user home leakage. */
record PathLike(String safeName) {
}

record PackageBuildResult(
        PathLike publicDir,
        PathLike privateDir,
        PathLike publicZip,
        PathLike privateZip,
        String publicZipSha256,
        String privateZipSha256) {
}
