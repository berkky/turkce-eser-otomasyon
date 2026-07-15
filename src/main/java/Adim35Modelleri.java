import java.math.BigDecimal;
import java.util.List;

enum PassageLicenseStatus {
    VERIFIED_PUBLIC_DOMAIN,
    VERIFIED_LICENSED,
    USER_CONFIRMED_RIGHTS,
    REVIEW_REQUIRED,
    UNKNOWN;

    boolean liveEligible() {
        return this == VERIFIED_PUBLIC_DOMAIN || this == VERIFIED_LICENSED
                || this == USER_CONFIRMED_RIGHTS;
    }
}

enum PassageApprovalStatus {
    CANDIDATE,
    SELECTED,
    PASSAGE_APPROVED_LIVE_LOCKED
}

enum UnderlyingWorkRightsStatus {
    PUBLIC_DOMAIN_BASIS_VERIFIED,
    COPYRIGHT_ACTIVE,
    RIGHTS_HOLDER_PERMISSION_REQUIRED,
    REVIEW_REQUIRED,
    UNKNOWN
}

enum SourceEditionLicenseStatus {
    CC_BY_SA_RECORDED,
    PUBLIC_DOMAIN_SOURCE,
    LICENSED_SOURCE,
    USER_PROVIDED_SOURCE,
    REVIEW_REQUIRED,
    UNKNOWN
}

record RightsEvidence(
        String evidenceType,
        String description,
        String reference,
        String recordedAt) {
}

record UnderlyingWorkRights(
        String underlyingWorkTitle,
        String underlyingWorkAuthor,
        int authorDeathYear,
        UnderlyingWorkRightsStatus underlyingWorkRightsStatus,
        String underlyingWorkRightsJurisdiction,
        String underlyingWorkRightsBasis,
        List<RightsEvidence> underlyingWorkRightsEvidence,
        String underlyingWorkReviewStatus,
        String underlyingWorkReviewedAt,
        String underlyingWorkReviewedBy,
        boolean legalAdvice) {
    UnderlyingWorkRights {
        underlyingWorkRightsEvidence = underlyingWorkRightsEvidence == null
                ? List.of() : List.copyOf(underlyingWorkRightsEvidence);
    }
}

record SourceEditionRights(
        String sourceEditionName,
        String sourceEditionUrl,
        String sourceEditionRevision,
        String sourceEditionRetrievedAt,
        SourceEditionLicenseStatus sourceEditionLicense,
        String sourceEditionLicenseUrl,
        boolean attributionRequired,
        String attributionText,
        boolean shareAlikeNoticeRequired,
        boolean additionalTermsMayApply,
        String sourceEditionReviewStatus,
        boolean commercialUseReady) {
}

record SourceProvenance(
        String eserId,
        String selectionId,
        String sourceType,
        String sourceFile,
        String sourceTextSha256,
        String sourceEditionRevision,
        String relationship,
        String recordedAt) {
}

record PassageApprovalConfirmations(
        boolean fullTextRead,
        boolean normalizationDiffReviewed,
        boolean sourceAttributionSeen,
        boolean rightsLayersUnderstood,
        boolean shortTtsUseApproved,
        boolean paidApiWarningUnderstood) {
    boolean allConfirmed() {
        return fullTextRead && normalizationDiffReviewed && sourceAttributionSeen
                && rightsLayersUnderstood && shortTtsUseApproved && paidApiWarningUnderstood;
    }
}

record PassageScores(
        int narrativeIntegrity,
        int emotionIntensity,
        int dialoguePresence,
        int pronunciationVariety,
        int listeningInterest,
        int naturalBoundaries,
        int textCleanliness,
        int commercialDemoFit,
        int copyrightClarity,
        int ttsSentenceFit,
        double totalScore) {
}

record PassageSourceSummary(
        int eserId,
        String eserBasligi,
        String yazar,
        String sourceType,
        String sourceName,
        String sourceUrl,
        String sourceArchiveId,
        PassageLicenseStatus sourceLicenseStatus,
        String sourceLicenseNote,
        String sourcePublicDomainStatus,
        String sourceAccessedAt,
        String sourceTextHash,
        int sourceCharacterCount,
        int ttsPartCount,
        String sourceFileSafeName) {
}

record PassageCandidate(
        String candidateId,
        int eserId,
        String eserBasligi,
        String yazar,
        String sourceType,
        String sourceName,
        String sourceUrl,
        String sourceArchiveId,
        PassageLicenseStatus sourceLicenseStatus,
        String sourceLicenseNote,
        String sourcePublicDomainStatus,
        String sourceAccessedAt,
        String originalText,
        String normalizedText,
        List<String> normalizationChanges,
        String originalTextHash,
        String normalizedTextHash,
        int originalCharacterCount,
        int normalizedCharacterCount,
        int wordCount,
        int estimatedSpeechSeconds,
        Integer sourceStartOffset,
        Integer sourceEndOffset,
        PassageScores scores,
        String selectedAt,
        String approvedAt,
        String approvedBy,
        PassageApprovalStatus approvalStatus) {
    PassageCandidate {
        normalizationChanges = normalizationChanges == null ? List.of() : List.copyOf(normalizationChanges);
    }
}

record PassageSelectionManifest(
        String selectionId,
        int eserId,
        String algorithmVersion,
        String createdAt,
        PassageSourceSummary source,
        List<PassageCandidate> candidates,
        String selectedCandidateId,
        String selectedAt,
        String status) {
    PassageSelectionManifest {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}

record PassageApprovalRequest(
        String selectionId,
        int eserId,
        String requiredSourceHash,
        List<String> requiredConfirmations,
        List<String> requiredAcknowledgements,
        List<String> legacyAcknowledgements,
        String schemaVersion,
        boolean liveApiCallPlanned,
        String status) {
    PassageApprovalRequest {
        requiredConfirmations = requiredConfirmations == null ? List.of() : List.copyOf(requiredConfirmations);
        requiredAcknowledgements = requiredAcknowledgements == null
                ? List.of() : List.copyOf(requiredAcknowledgements);
        legacyAcknowledgements = legacyAcknowledgements == null
                ? List.of() : List.copyOf(legacyAcknowledgements);
    }
}

record ApprovedPassage(
        String approvalId,
        String selectionId,
        int eserId,
        String candidateId,
        String recommendationType,
        UnderlyingWorkRightsStatus underlyingWorkRightsStatus,
        String underlyingWorkRightsBasis,
        SourceEditionLicenseStatus sourceEditionLicense,
        boolean attributionRequired,
        String attributionText,
        boolean additionalTermsMayApply,
        String sourceType,
        PassageLicenseStatus sourceLicenseStatus,
        String sourceLicenseNote,
        boolean sourceApproved,
        boolean rightsReviewed,
        boolean rightsAcknowledgedByUser,
        boolean commercialUseApprovedByUser,
        String approvedText,
        String approvedTextHash,
        int approvedCharacterCount,
        int approvedWordCount,
        int estimatedSpeechSeconds,
        String approvedAt,
        String approvedBy,
        boolean liveGenerationAllowed,
        boolean openAiAllowed,
        boolean xaiAllowed,
        BigDecimal budgetLimitUsd,
        int maxProviderCount,
        int approvalVersion,
        String status) {
}

record PreviewCostEstimate(
        int inputCharacters,
        int estimatedSpeechSeconds,
        BigDecimal estimatedCostUsd,
        String currency,
        String pricingProfileDate,
        String pricingSourceNote,
        String calculationType) {
}

record OpenAiPreviewProfile(
        String provider,
        String endpoint,
        String model,
        List<String> voiceCandidates,
        String selectedVoice,
        String language,
        String outputFormat,
        List<String> instructions,
        String inputTextHash,
        int inputCharacterCount,
        UnderlyingWorkRightsStatus underlyingWorkRightsStatus,
        SourceEditionLicenseStatus sourceEditionLicense,
        boolean attributionRequired,
        String attributionText,
        boolean rightsAcknowledgedByUser,
        boolean commercialUseApprovedByUser,
        PreviewCostEstimate costEstimate,
        BigDecimal estimatedCostUsd,
        boolean liveEnabled,
        boolean userApproved,
        String validationStatus) {
}

record XaiPreviewProfile(
        String provider,
        String endpoint,
        String model,
        List<String> voiceCandidates,
        String selectedVoice,
        String language,
        String outputFormat,
        double speed,
        List<String> speechTags,
        String inputTextHash,
        int inputCharacterCount,
        UnderlyingWorkRightsStatus underlyingWorkRightsStatus,
        SourceEditionLicenseStatus sourceEditionLicense,
        boolean attributionRequired,
        String attributionText,
        boolean rightsAcknowledgedByUser,
        boolean commercialUseApprovedByUser,
        PreviewCostEstimate costEstimate,
        BigDecimal estimatedCostUsd,
        boolean liveEnabled,
        boolean userApproved,
        String validationStatus) {
}

record LivePreviewDraft(
        String draftId,
        String approvalId,
        int eserId,
        String approvedTextHash,
        int approvedCharacterCount,
        List<String> providerOrder,
        List<String> providers,
        BigDecimal totalEstimatedCostUsd,
        BigDecimal maxAllowedCostUsd,
        boolean budgetApproved,
        boolean liveGenerationApproved,
        boolean providerSpecificApproval,
        String normalizationTarget,
        boolean blindTestPlanned,
        String createdAt,
        String expiresAt,
        String status) {
}
