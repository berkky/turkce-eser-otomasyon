import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.BreakIterator;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Adım 35: ESER-00005 için tamamen yerel ve deterministik pasaj seçimi. */
public final class KasagiPasajService {
    static final String ALGORITHM_VERSION = "KASAGI-PASSAGE-1";
    static final int ESER_ID = 5;
    static final String ESER_CODE = "ESER-00005";
    static final int MIN_CHARS = 900;
    static final int MAX_CHARS = 1250;
    static final String PRIMARY_RECOMMENDATION = "RECOMMENDED_FOR_PRIMARY_BLIND_TEST";
    static final String EMOTIONAL_RECOMMENDATION = "RECOMMENDED_FOR_EMOTIONAL_FOLLOWUP";
    static final String APPROVAL_REQUEST_SCHEMA_VERSION = "35B.1";
    static final List<String> REQUIRED_ACKNOWLEDGEMENTS = List.of(
            "FULL_TEXT_READ",
            "NORMALIZATION_DIFF_REVIEWED",
            "SOURCE_ATTRIBUTION_SEEN",
            "RIGHTS_LAYERS_UNDERSTOOD",
            "SHORT_TTS_USE_APPROVED",
            "PAID_API_WARNING_UNDERSTOOD");
    static final List<String> LEGACY_ACKNOWLEDGEMENTS = List.of(
            "SOURCE_AND_TEXT_CONFIRMED",
            "RIGHTS_CONFIRMED_BY_USER");
    static final String ATTRIBUTION = "Kaşağı — Ömer Seyfettin; kaynak: Türkçe Vikikaynak, "
            + "Kaşağı (oldid=172873); kaynak sürümü CC BY-SA olarak kaydedilmiştir.";
    private static final Pattern EMOTION = Pattern.compile(
            "(?iu)\\b(kork|ağla|hıçkır|öl|öfke|kız|hiddet|pişman|vicdan|zehir|"
                    + "zavallı|masum|yalancı|sevin|acı|hazin)\\p{L}*\\b");
    private static final Pattern DIALOGUE = Pattern.compile("(?m)^\\s*(?:—|&#8212;|ve #8212;|-)");
    private static final Pattern URL = Pattern.compile("(?iu)https?://|www\\.");
    private static final Pattern HTML = Pattern.compile("<[^>]+>");
    private static final Pattern OCR_NOISE = Pattern.compile("(?iu)�|#\\d{3,5}|\\bve\\s+#\\d+|\\b[a-zçğıöşü]+\\d+[a-zçğıöşü]+\\b");
    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF]");
    private static final Pattern MERGED_WORD = Pattern.compile(
            "(?iu)\\b\\p{Ll}{7,}(?:görünce|geldi|koştu|dedi|olunca|başladı|kaldı)\\b");
    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

    private final WebOrtam environment;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public KasagiPasajService(WebOrtam environment) {
        this.environment = environment;
    }

    public Path root() {
        return environment.sesArsivi().resolve("ab-test").resolve("passage-selection").resolve(ESER_CODE);
    }

    public PassageSourceSummary resolveSource() throws Exception {
        Path folder = sourceFolder();
        Path fullText = folder.resolve("tam-metin.txt");
        if (!Files.isRegularFile(fullText) || Files.size(fullText) == 0) {
            throw new IllegalStateException("SOURCE_TEXT_NOT_FOUND");
        }
        String source = Files.readString(fullText, StandardCharsets.UTF_8);
        if (source.isBlank() || source.indexOf('\uFFFD') >= 0) {
            throw new IllegalStateException("SOURCE_TEXT_INVALID");
        }
        if (source.length() <= 7_000) {
            throw new IllegalStateException("SOURCE_CHARACTER_COUNT_INVALID");
        }
        Path chunks = folder.resolve("tts-parcalari");
        int partCount;
        try (Stream<Path> paths = Files.list(chunks)) {
            partCount = (int) paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("\\d{3}-\\d{3}\\.txt")).count();
        }
        if (partCount <= 0) throw new IllegalStateException("SOURCE_TEXT_INVALID");

        JsonNode manifest = readJson(folder.resolve("alim-manifest.json"));
        String license = manifest.path("lisans").asText("Kontrol edilmedi");
        PassageLicenseStatus licenseStatus = verifiedLicenseStatus(license);
        String note = "Metadata lisans kaydı: " + license
                + ". Kaynak URL tek başına ticari kullanım hakkı kanıtı değildir.";
        return new PassageSourceSummary(
                ESER_ID,
                manifest.path("eserAdi").asText("Kaşağı - Vikikaynak"),
                manifest.path("yazar").asText("Bilinmiyor"),
                TtsAbSourceType.APPROVED_ARCHIVE_TEXT.name(),
                "Vikikaynak",
                manifest.path("kaynakUrl").asText(""),
                archiveId(manifest.path("kaynakUrl").asText("")),
                licenseStatus,
                note,
                "NOT_VERIFIED",
                OffsetDateTime.now().toString(),
                TtsAbService.sha256(source),
                source.length(),
                partCount,
                "tam-metin.txt");
    }

    public List<PassageCandidate> analyzeCandidates() throws Exception {
        PassageSourceSummary sourceSummary = resolveSource();
        String source = Files.readString(sourceFolder().resolve("tam-metin.txt"), StandardCharsets.UTF_8);
        Range body = storyRange(source);
        List<Range> sentences = sentenceRanges(source, body);
        List<PassageCandidate> analyzed = new ArrayList<>();
        int stride = Math.max(1, sentences.size() / 10);
        Set<String> seen = new HashSet<>();
        for (int start = 0; start < sentences.size() && analyzed.size() < 10; start += stride) {
            PassageCandidate candidate = candidateFrom(source, sentences, start, sourceSummary,
                    "candidate-analysis-" + (analyzed.size() + 1));
            if (candidate != null && seen.add(candidate.normalizedTextHash())) analyzed.add(candidate);
        }
        for (int start = 0; start < sentences.size() && analyzed.size() < 10; start++) {
            PassageCandidate candidate = candidateFrom(source, sentences, start, sourceSummary,
                    "candidate-analysis-" + (analyzed.size() + 1));
            if (candidate != null && seen.add(candidate.normalizedTextHash())) analyzed.add(candidate);
        }
        analyzed.sort(Comparator.comparingDouble((PassageCandidate p) -> p.scores().totalScore()).reversed()
                .thenComparingInt(PassageCandidate::sourceStartOffset));

        List<PassageCandidate> selected = selectDistinct(analyzed, 0.35);
        if (selected.size() < 3) selected = selectDistinct(analyzed, 0.50);
        if (selected.size() < 3) throw new IllegalStateException("PASSAGE_CANDIDATE_COUNT_INVALID");

        List<PassageCandidate> result = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            PassageCandidate p = selected.get(i);
            result.add(copyWithId(p, "PASSAGE-" + (i + 1)));
        }
        validateCandidates(source, result);
        return List.copyOf(result);
    }

    public Path createSelection() throws Exception {
        PassageSourceSummary source = resolveSource();
        List<PassageCandidate> candidates = analyzeCandidates();
        String id = "PASSAGE-" + ESER_CODE + "-" + OffsetDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS"))
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        Path selection = root().resolve(id).normalize();
        if (!WebGuvenlikService.guvenliAltDosya(root(), selection) || Files.exists(selection)) {
            throw new IllegalStateException("SELECTION_ALREADY_EXISTS");
        }
        Files.createDirectories(selection);
        try {
            Files.createDirectories(selection.resolve("source"));
            Files.createDirectories(selection.resolve("candidates"));
            writeJson(selection.resolve("source/eser-kaynak-ozeti.json"), source);
            atomicText(selection.resolve("source/source.sha256"), source.sourceTextHash() + "\n");
            atomicText(selection.resolve("source/source-preview.txt"),
                    candidates.getFirst().originalText().substring(0,
                            Math.min(candidates.getFirst().originalText().length(), 1200)) + "\n");
            for (int i = 0; i < candidates.size(); i++) {
                PassageCandidate candidate = candidates.get(i);
                int number = i + 1;
                atomicText(selection.resolve("candidates/candidate-" + number + "-original.txt"),
                        candidate.originalText());
                atomicText(selection.resolve("candidates/candidate-" + number + "-normalized.txt"),
                        candidate.normalizedText());
                writeJson(selection.resolve("candidates/candidate-" + number + ".json"), candidate);
            }
            PassageSelectionManifest manifest = new PassageSelectionManifest(
                    id, ESER_ID, ALGORITHM_VERSION, OffsetDateTime.now().toString(),
                    source, candidates, null, null, "CANDIDATES_READY");
            writeJson(selection.resolve("selection-manifest.json"), manifest);
            writeJson(selection.resolve("approval-request.json"), newApprovalRequest(
                    id, source.sourceTextHash(), List.of()));
            atomicText(selection.resolve("README.txt"),
                    "Bu klasör ESER-00005 gerçek canonical kaynağından yerel ve deterministik olarak üretildi.\n"
                            + "Henüz ücretli API çağrısı veya ses üretimi yapılmadı.\n"
                            + "Lisans durumu kullanıcı tarafından açıkça doğrulanmadan onay oluşturulamaz.\n");
            prepareRights(id);
            assertNonEmpty(selection);
            return selection;
        } catch (Exception exception) {
            deleteTree(selection);
            throw exception;
        }
    }

    public PassageSelectionManifest latestSelection() throws Exception {
        if (!Files.isDirectory(root())) return null;
        try (Stream<Path> paths = Files.list(root())) {
            Path latest = paths.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().matches("PASSAGE-ESER-00005-[0-9A-Z-]{10,40}"))
                    .filter(p -> Files.isRegularFile(p.resolve("selection-manifest.json")))
                    .max(Comparator.comparing(p -> p.getFileName().toString())).orElse(null);
            return latest == null ? null : readManifest(latest);
        }
    }

    public PassageSelectionManifest readManifest(String selectionId) throws Exception {
        return readManifest(selectionRoot(selectionId));
    }

    public synchronized PassageSelectionManifest select(String selectionId, String candidateId,
                                                        String submissionId) throws Exception {
        Path root = selectionRoot(selectionId);
        useSubmission(root, submissionId);
        PassageSelectionManifest manifest = readManifest(root);
        PassageCandidate selected = manifest.candidates().stream()
                .filter(c -> c.candidateId().equals(candidateId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("PASSAGE_NOT_FOUND"));
        String now = OffsetDateTime.now().toString();
        List<PassageCandidate> updated = manifest.candidates().stream()
                .map(c -> c.candidateId().equals(selected.candidateId())
                        ? new PassageCandidate(c.candidateId(), c.eserId(), c.eserBasligi(), c.yazar(),
                        c.sourceType(), c.sourceName(), c.sourceUrl(), c.sourceArchiveId(),
                        c.sourceLicenseStatus(), c.sourceLicenseNote(), c.sourcePublicDomainStatus(),
                        c.sourceAccessedAt(), c.originalText(), c.normalizedText(), c.normalizationChanges(),
                        c.originalTextHash(), c.normalizedTextHash(), c.originalCharacterCount(),
                        c.normalizedCharacterCount(), c.wordCount(), c.estimatedSpeechSeconds(),
                        c.sourceStartOffset(), c.sourceEndOffset(), c.scores(), now, null, null,
                        PassageApprovalStatus.SELECTED)
                        : c).toList();
        PassageSelectionManifest result = new PassageSelectionManifest(
                manifest.selectionId(), manifest.eserId(), manifest.algorithmVersion(), manifest.createdAt(),
                manifest.source(), updated, candidateId, now, "PASSAGE_SELECTED");
        writeJson(root.resolve("selection-manifest.json"), result);
        return result;
    }

    public synchronized ApprovedPassage approve(String selectionId, String submissionId,
                                                PassageApprovalConfirmations confirmations,
                                                String approvedBy) throws Exception {
        Path root = selectionRoot(selectionId);
        useSubmission(root, submissionId);
        requireConfirmations(confirmations);
        PassageSelectionManifest manifest = readManifest(root);
        if (manifest.selectedCandidateId() == null) throw new IllegalStateException("PASSAGE_SELECTION_REQUIRED");
        if (Files.exists(root.resolve("approved-passage.json"))) throw new IllegalStateException("ALREADY_APPROVED");
        PassageCandidate candidate = manifest.candidates().stream()
                .filter(c -> c.candidateId().equals(manifest.selectedCandidateId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("PASSAGE_SELECTION_REQUIRED"));

        UnderlyingWorkRights underlying = readUnderlyingRights(root);
        SourceEditionRights edition = readSourceEditionRights(root);
        if (underlying.underlyingWorkRightsStatus() != UnderlyingWorkRightsStatus.PUBLIC_DOMAIN_BASIS_VERIFIED
                || edition.sourceEditionLicense() == SourceEditionLicenseStatus.UNKNOWN
                || edition.sourceEditionLicense() == SourceEditionLicenseStatus.REVIEW_REQUIRED
                || !edition.attributionRequired()) {
            throw new IllegalStateException("RIGHTS_REVIEW_REQUIRED");
        }
        PassageLicenseStatus effectiveLicense = PassageLicenseStatus.VERIFIED_LICENSED;
        verifyCurrentHashes(root, manifest, candidate);
        String approver = approvedBy == null || approvedBy.isBlank() ? "LOCAL_USER" : approvedBy.trim();
        String now = OffsetDateTime.now().toString();
        ApprovedPassage approved = new ApprovedPassage(
                "APPROVAL-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT),
                selectionId, ESER_ID, candidate.candidateId(), recommendationType(candidate.candidateId()),
                underlying.underlyingWorkRightsStatus(), underlying.underlyingWorkRightsBasis(),
                edition.sourceEditionLicense(), edition.attributionRequired(), edition.attributionText(),
                edition.additionalTermsMayApply(), candidate.sourceType(), effectiveLicense,
                candidate.sourceLicenseNote(), true, true, true, true,
                candidate.normalizedText(), candidate.normalizedTextHash(),
                candidate.normalizedCharacterCount(), candidate.wordCount(), candidate.estimatedSpeechSeconds(),
                now, approver, false, false, false, BigDecimal.ZERO, 0, 2,
                PassageApprovalStatus.PASSAGE_APPROVED_LIVE_LOCKED.name());
        writeJson(root.resolve("approved-passage.json"), approved);
        writePreparationProfiles(root, approved);
        writeAhmetBeyPackageDraft(root, approved);

        PassageLicenseStatus approvedLicense = effectiveLicense;
        List<PassageCandidate> updated = manifest.candidates().stream()
                .map(c -> c.candidateId().equals(candidate.candidateId())
                        ? new PassageCandidate(c.candidateId(), c.eserId(), c.eserBasligi(), c.yazar(),
                        c.sourceType(), c.sourceName(), c.sourceUrl(), c.sourceArchiveId(), approvedLicense,
                        c.sourceLicenseNote(), c.sourcePublicDomainStatus(), c.sourceAccessedAt(), c.originalText(),
                        c.normalizedText(), c.normalizationChanges(), c.originalTextHash(), c.normalizedTextHash(),
                        c.originalCharacterCount(), c.normalizedCharacterCount(), c.wordCount(),
                        c.estimatedSpeechSeconds(), c.sourceStartOffset(), c.sourceEndOffset(), c.scores(),
                        c.selectedAt(), now, approver, PassageApprovalStatus.PASSAGE_APPROVED_LIVE_LOCKED)
                        : c).toList();
        writeJson(root.resolve("selection-manifest.json"), new PassageSelectionManifest(
                manifest.selectionId(), manifest.eserId(), manifest.algorithmVersion(), manifest.createdAt(),
                manifest.source(), updated, manifest.selectedCandidateId(), manifest.selectedAt(),
                PassageApprovalStatus.PASSAGE_APPROVED_LIVE_LOCKED.name()));
        return approved;
    }

    public synchronized ApprovedPassage approve(String selectionId, String submissionId,
                                                boolean sourceAndTextConfirmed, boolean rightsConfirmed,
                                                String approvedBy) throws Exception {
        return approve(selectionId, submissionId, new PassageApprovalConfirmations(
                sourceAndTextConfirmed, sourceAndTextConfirmed, rightsConfirmed,
                rightsConfirmed, sourceAndTextConfirmed, sourceAndTextConfirmed), approvedBy);
    }

    /**
     * Idempotent schema upgrade for approval-request.json only.
     * Does not select, approve, or rewrite rights/source/candidates.
     */
    public synchronized boolean upgradeApprovalRequestSchema(String selectionId) throws Exception {
        Path selection = selectionRoot(selectionId);
        Path approvalRequest = selection.resolve("approval-request.json");
        if (!Files.isRegularFile(approvalRequest)) {
            throw new IllegalStateException("APPROVAL_REQUEST_NOT_FOUND");
        }
        PassageSelectionManifest manifest = readManifest(selection);
        if (manifest.selectedCandidateId() != null
                || !"CANDIDATES_READY".equals(manifest.status())) {
            throw new IllegalStateException("APPROVAL_REQUEST_UPGRADE_REQUIRES_CANDIDATES_READY");
        }
        if (Files.exists(selection.resolve("approved-passage.json"))) {
            throw new IllegalStateException("ALREADY_APPROVED");
        }
        JsonNode existing = readJson(approvalRequest);
        String sourceHash = existing.path("requiredSourceHash").asText(manifest.source().sourceTextHash());
        if (!sourceHash.equals(manifest.source().sourceTextHash())) {
            throw new IllegalStateException("SOURCE_HASH_MISMATCH");
        }
        List<String> legacy = extractLegacyAcknowledgements(existing);
        PassageApprovalRequest upgraded = newApprovalRequest(selectionId, sourceHash, legacy);
        byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(upgraded);
        if (bytes.length == 0) throw new IllegalStateException("ZERO_BYTE_OUTPUT");
        byte[] current = Files.readAllBytes(approvalRequest);
        if (java.util.Arrays.equals(current, bytes)) {
            return false;
        }
        TtsLaboratuvarYardimci.atomikYaz(approvalRequest, bytes);
        PassageSelectionManifest after = readManifest(selection);
        if (after.selectedCandidateId() != null
                || !"CANDIDATES_READY".equals(after.status())
                || Files.exists(selection.resolve("approved-passage.json"))) {
            throw new IllegalStateException("APPROVAL_REQUEST_UPGRADE_SIDE_EFFECT");
        }
        return true;
    }

    public static PassageApprovalRequest newApprovalRequest(String selectionId, String sourceHash,
                                                            List<String> legacy) {
        List<String> legacyCopy = legacy == null || legacy.isEmpty()
                ? List.copyOf(LEGACY_ACKNOWLEDGEMENTS)
                : List.copyOf(legacy);
        return new PassageApprovalRequest(
                selectionId, ESER_ID, sourceHash,
                List.copyOf(REQUIRED_ACKNOWLEDGEMENTS),
                List.copyOf(REQUIRED_ACKNOWLEDGEMENTS),
                legacyCopy,
                APPROVAL_REQUEST_SCHEMA_VERSION,
                false, "AWAITING_SELECTION");
    }

    private static List<String> extractLegacyAcknowledgements(JsonNode existing) {
        LinkedHashSet<String> legacy = new LinkedHashSet<>();
        for (JsonNode node : existing.path("legacyAcknowledgements")) {
            if (node.isTextual()) legacy.add(node.asText());
        }
        for (JsonNode node : existing.path("requiredConfirmations")) {
            if (node.isTextual()) {
                String code = node.asText();
                if (LEGACY_ACKNOWLEDGEMENTS.contains(code) || !REQUIRED_ACKNOWLEDGEMENTS.contains(code)) {
                    legacy.add(code);
                }
            }
        }
        if (legacy.isEmpty()) legacy.addAll(LEGACY_ACKNOWLEDGEMENTS);
        return List.copyOf(legacy);
    }

    public synchronized Path prepareRights(String selectionId) throws Exception {
        Path selection = selectionRoot(selectionId);
        PassageSelectionManifest manifest = readManifest(selection);
        Path rights = selection.resolve("rights");
        Files.createDirectories(rights);
        String revision = sourceRevision();
        String recordedAt = manifest.source().sourceAccessedAt();
        List<RightsEvidence> evidence = List.of(
                new RightsEvidence("AUTHOR_IDENTITY",
                        "Yazar Ömer Seyfettin; doğum ve ölüm bilgisi bibliyografik kaynak kaydı.",
                        "https://tr.wikipedia.org/wiki/%C3%96mer_Seyfettin", recordedAt),
                new RightsEvidence("AUTHOR_DEATH_YEAR", "Ömer Seyfettin ölüm yılı: 1920.",
                        "BIBLIOGRAPHIC_RECORD", recordedAt),
                new RightsEvidence("LEGAL_DURATION_BASIS",
                        "Türkiye FSEK genel kuralı: eser sahibinin yaşamı ve ölümünden sonra 70 yıl.",
                        "5846_SAYILI_FSEK_GENEL_KORUMA_SURESI", recordedAt),
                new RightsEvidence("WORK_TITLE", "Eser adı: Kaşağı.",
                        "SOURCE_EDITION_TITLE", recordedAt));
        UnderlyingWorkRights underlying = new UnderlyingWorkRights(
                "Kaşağı", "Ömer Seyfettin", 1920,
                UnderlyingWorkRightsStatus.PUBLIC_DOMAIN_BASIS_VERIFIED, "TR",
                "Yazarın 1920 ölüm yılı ile 5846 sayılı FSEK'teki yaşam + 70 yıl genel koruma "
                        + "süresi dayanağı kaydedildi; bu kayıt nihai hukuk görüşü değildir.",
                evidence, "EVIDENCE_RECORDED_NOT_LEGAL_OPINION", recordedAt,
                "LOCAL_REVIEW_RECORD", false);
        SourceEditionRights edition = new SourceEditionRights(
                "Türkçe Vikikaynak — Kaşağı",
                manifest.source().sourceUrl(), revision, sourceRetrievedAt(),
                SourceEditionLicenseStatus.CC_BY_SA_RECORDED,
                "https://creativecommons.org/licenses/by-sa/4.0/deed.tr",
                true, ATTRIBUTION, true, true,
                "LICENSE_NOTICE_RECORDED_USER_ACKNOWLEDGEMENT_REQUIRED", false);
        SourceProvenance provenance = new SourceProvenance(
                ESER_CODE, selectionId, manifest.source().sourceType(), "tam-metin.txt",
                manifest.source().sourceTextHash(), revision,
                "Canonical local tam-metin.txt, kayıtlı Türkçe Vikikaynak sürümünden çıkarılmış "
                        + "metin olarak ilişkilendirilmiştir; ağ üzerinden yeniden doğrulama yapılmamıştır.",
                recordedAt);
        writeJson(rights.resolve("underlying-work-rights.json"), underlying);
        writeJson(rights.resolve("source-edition-license.json"), edition);
        writeJson(rights.resolve("source-provenance.json"), provenance);
        writeJson(rights.resolve("SOURCE_ATTRIBUTION.json"), edition);
        atomicText(rights.resolve("SOURCE_ATTRIBUTION.txt"), attributionText(underlying, edition, provenance));
        atomicText(rights.resolve("RIGHTS_REVIEW.md"),
                "# Kaşağı kaynak hakkı inceleme kaydı\n\n"
                        + "- Temel edebî eser hakkı: `PUBLIC_DOMAIN_BASIS_VERIFIED`\n"
                        + "- Kullanılan dijital kaynak sürümü: `CC_BY_SA_RECORDED`\n"
                        + "- Atıf zorunluluğu: evet\n- Benzer paylaşım bildirimi: evet\n"
                        + "- Ek koşullar uygulanabilir: evet\n- Ticari kullanıma hazır: hayır\n\n"
                        + "Bu otomatik hukuk kararı veya hukuk danışmanlığı değildir. "
                        + "Kanıt ve lisans bildirimi kullanıcı incelemesine sunulmuştur.\n");
        List<String> evidenceHashes = new ArrayList<>();
        for (String name : List.of("underlying-work-rights.json", "source-edition-license.json",
                "source-provenance.json", "SOURCE_ATTRIBUTION.txt", "SOURCE_ATTRIBUTION.json",
                "RIGHTS_REVIEW.md")) {
            evidenceHashes.add(XaiTtsSaglayici.sha256(rights.resolve(name)) + "  " + name);
        }
        atomicText(rights.resolve("rights-evidence.sha256"), String.join("\n", evidenceHashes) + "\n");
        return rights;
    }

    public String rightsJson(String selectionId) throws Exception {
        Path root = selectionRoot(selectionId);
        com.fasterxml.jackson.databind.node.ObjectNode node = json.createObjectNode();
        node.set("underlyingWork", readJson(root.resolve("rights/underlying-work-rights.json")));
        node.set("sourceEdition", readJson(root.resolve("rights/source-edition-license.json")));
        node.set("provenance", readJson(root.resolve("rights/source-provenance.json")));
        return WebGuvenlikService.guvenliJson(json.writerWithDefaultPrettyPrinter().writeValueAsString(node));
    }

    public String attributionText(String selectionId) throws Exception {
        return Files.readString(selectionRoot(selectionId).resolve("rights/SOURCE_ATTRIBUTION.txt"),
                StandardCharsets.UTF_8);
    }

    static String recommendationType(String candidateId) {
        if ("PASSAGE-1".equals(candidateId)) return PRIMARY_RECOMMENDATION;
        if ("PASSAGE-3".equals(candidateId)) return EMOTIONAL_RECOMMENDATION;
        return "NO_AUTOMATIC_RECOMMENDATION";
    }

    public String manifestJson(PassageSelectionManifest manifest) throws Exception {
        return WebGuvenlikService.guvenliJson(json.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
    }

    static NormalizationResult normalize(String original) {
        String value = original;
        List<String> changes = new ArrayList<>();
        String next = value.replace("\r\n", "\n").replace('\r', '\n');
        if (!next.equals(value)) changes.add("LINE_ENDINGS_NORMALIZED");
        value = next;
        next = INVISIBLE.matcher(value).replaceAll("");
        if (!next.equals(value)) changes.add("INVISIBLE_UNICODE_REMOVED");
        value = next;
        next = value.replace("&#8212;", "—").replace("ve #8212;", "—")
                .replace("&#039;", "'").replace("#039;", "'");
        if (!next.equals(value)) changes.add("HTML_ENTITY_OR_OCR_DASH_NORMALIZED");
        value = next;
        next = value.replaceAll("(?m)^\\s*-\\s+", "— ");
        if (!next.equals(value)) changes.add("DIALOGUE_DASH_STANDARDIZED");
        value = next;
        next = value.replace('“', '"').replace('”', '"');
        if (!next.equals(value)) changes.add("QUOTATION_MARKS_STANDARDIZED");
        value = next;
        next = value.replaceAll("[\\t ]{2,}", " ")
                .replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n").strip();
        if (!next.equals(value)) changes.add("WHITESPACE_NORMALIZED");
        return new NormalizationResult(next, List.copyOf(changes));
    }

    static boolean hasMergedWord(String text) {
        return text != null && MERGED_WORD.matcher(text).find();
    }

    static boolean hasOcrNoise(String text) {
        return text != null && OCR_NOISE.matcher(text).find();
    }

    static double overlapRatio(PassageCandidate a, PassageCandidate b) {
        int overlap = Math.max(0, Math.min(a.sourceEndOffset(), b.sourceEndOffset())
                - Math.max(a.sourceStartOffset(), b.sourceStartOffset()));
        return overlap / (double) Math.min(a.sourceEndOffset() - a.sourceStartOffset(),
                b.sourceEndOffset() - b.sourceStartOffset());
    }

    private PassageCandidate candidateFrom(String source, List<Range> sentences, int start,
                                           PassageSourceSummary summary, String id) throws Exception {
        int end = start;
        PassageCandidate candidate = null;
        while (end < sentences.size()) {
            String original = source.substring(sentences.get(start).start(), sentences.get(end).end()).strip();
            NormalizationResult normalized = normalize(original);
            if (original.length() > MAX_CHARS || normalized.text().length() > MAX_CHARS) break;
            if (original.length() >= MIN_CHARS && normalized.text().length() >= MIN_CHARS) {
                int sourceStart = source.indexOf(original, sentences.get(start).start());
                candidate = buildCandidate(id, original, normalized, sourceStart,
                        sourceStart + original.length(), summary);
            }
            end++;
        }
        return candidate != null && qualityValid(candidate) ? candidate : null;
    }

    private PassageCandidate buildCandidate(String id, String original, NormalizationResult normalized,
                                            int start, int end, PassageSourceSummary source) throws Exception {
        int words = wordCount(normalized.text());
        int seconds = Math.max(1, (int) Math.ceil(words / 2.35));
        PassageScores scores = score(normalized.text(), source.sourceLicenseStatus());
        return new PassageCandidate(id, ESER_ID, source.eserBasligi(), source.yazar(), source.sourceType(),
                source.sourceName(), source.sourceUrl(), source.sourceArchiveId(), source.sourceLicenseStatus(),
                source.sourceLicenseNote(), source.sourcePublicDomainStatus(), source.sourceAccessedAt(),
                original, normalized.text(), normalized.changes(), TtsAbService.sha256(original),
                TtsAbService.sha256(normalized.text()), original.length(), normalized.text().length(), words,
                seconds, start, end, scores, null, null, null, PassageApprovalStatus.CANDIDATE);
    }

    private static PassageScores score(String text, PassageLicenseStatus license) {
        int sentences = Math.max(1, sentenceCount(text));
        int dialogueCount = matches(DIALOGUE, text);
        int emotionCount = matches(EMOTION, text);
        int narrative = clamp(6 + Math.min(4, sentences / 3));
        int emotion = clamp(3 + emotionCount * 2);
        int dialogue = clamp(dialogueCount == 0 ? 2 : 5 + dialogueCount);
        int pronunciation = clamp(5 + properNameVariety(text));
        int interest = clamp(4 + Math.min(3, emotionCount) + Math.min(3, dialogueCount));
        int boundaries = naturalStart(text) && naturalEnd(text) ? 10 : 3;
        int cleanliness = clamp(10 - (hasOcrNoise(text) ? 5 : 0) - (hasMergedWord(text) ? 4 : 0)
                - (URL.matcher(text).find() ? 5 : 0));
        int commercial = clamp((narrative + emotion + interest + cleanliness) / 4);
        int copyright = license.liveEligible() ? 10 : license == PassageLicenseStatus.REVIEW_REQUIRED ? 4 : 1;
        double averageSentence = text.length() / (double) sentences;
        int ttsFit = averageSentence >= 35 && averageSentence <= 190 ? 9 : averageSentence <= 240 ? 7 : 4;
        double total = narrative * .20 + emotion * .15 + dialogue * .10 + pronunciation * .10
                + interest * .20 + cleanliness * .15 + ttsFit * .10;
        return new PassageScores(narrative, emotion, dialogue, pronunciation, interest, boundaries,
                cleanliness, commercial, copyright, ttsFit,
                BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP).doubleValue());
    }

    private void writePreparationProfiles(Path root, ApprovedPassage approved) throws Exception {
        String date = LocalDate.now().toString();
        BigDecimal xaiCost = BigDecimal.valueOf(approved.approvedCharacterCount())
                .multiply(BigDecimal.valueOf(15)).divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
        BigDecimal openAiPlanningRate = decimalEnv("OPENAI_TTS_PLANNING_USD_PER_MINUTE",
                new BigDecimal("0.015"));
        BigDecimal openAiCost = BigDecimal.valueOf(approved.estimatedSpeechSeconds())
                .divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP)
                .multiply(openAiPlanningRate).setScale(8, RoundingMode.HALF_UP);
        PreviewCostEstimate xaiEstimate = new PreviewCostEstimate(approved.approvedCharacterCount(),
                approved.estimatedSpeechSeconds(), xaiCost, "USD", date,
                "15 USD / 1.000.000 karakter planlama profili; canlı kullanım öncesi yeniden doğrulanmalıdır.",
                "CHARACTER_ESTIMATE");
        PreviewCostEstimate openAiEstimate = new PreviewCostEstimate(approved.approvedCharacterCount(),
                approved.estimatedSpeechSeconds(), openAiCost, "USD", date,
                "Süre tabanlı, yapılandırılabilir belirsizlikli planlama varsayımıdır; "
                        + "OpenAI gerçek token/ses kullanımı değildir.",
                "ESTIMATED_ONLY");
        String openAiVoice = env("OPENAI_TTS_VOICE");
        if (openAiVoice != null && !List.of("marin", "cedar").contains(openAiVoice)) openAiVoice = null;
        OpenAiPreviewProfile openAi = new OpenAiPreviewProfile(
                "openai", "https://api.openai.com/v1/audio/speech", "gpt-4o-mini-tts",
                List.of("marin", "cedar"), openAiVoice, "tr", "wav",
                List.of("Sıcak ve doğal Türkçe hikâye anlatımı",
                        "Nostaljik ancak abartısız ton",
                        "Diyaloglarda hafif karakter ayrımı",
                        "Sakin, dinlemesi yorucu olmayan tempo",
                        "Kelimeleri değiştirmeden oku",
                        "Kaşağı ve özel adları belirgin ama doğal telaffuz et",
                        "Tiyatral aşırılıktan kaçın",
                        "Bölüm sonlarında doğal duraklama kullan"),
                approved.approvedTextHash(), approved.approvedCharacterCount(),
                approved.underlyingWorkRightsStatus(), approved.sourceEditionLicense(),
                approved.attributionRequired(), approved.attributionText(),
                approved.rightsAcknowledgedByUser(), approved.commercialUseApprovedByUser(),
                openAiEstimate, openAiCost,
                false, false, "DRAFT_LIVE_LOCKED_ESTIMATED_ONLY");
        List<String> xaiVoices = new ArrayList<>(List.of("sal", "lumen", "ursa"));
        String xaiVoice = env("XAI_TTS_VOICE");
        if (xaiVoice != null && !xaiVoices.contains(xaiVoice)) xaiVoices.addFirst(xaiVoice);
        XaiPreviewProfile xai = new XaiPreviewProfile(
                "xai", "https://api.x.ai/v1/tts", "xai-tts", List.copyOf(xaiVoices), xaiVoice,
                "tr", "wav", 1.0, List.of("doğal kısa duraklama", "yumuşak anlatım",
                "hafif yavaşlatma", "kontrollü vurgu"), approved.approvedTextHash(),
                approved.approvedCharacterCount(), approved.underlyingWorkRightsStatus(),
                approved.sourceEditionLicense(), approved.attributionRequired(), approved.attributionText(),
                approved.rightsAcknowledgedByUser(), approved.commercialUseApprovedByUser(),
                xaiEstimate, xaiCost, false, false,
                "DRAFT_LIVE_LOCKED_VOICE_UNVERIFIED");
        BigDecimal total = openAiCost.add(xaiCost).setScale(8, RoundingMode.HALF_UP);
        String status = total.compareTo(BigDecimal.ONE) > 0 ? "BUDGET_REVIEW_REQUIRED" : "LIVE_GENERATION_LOCKED";
        LivePreviewDraft draft = new LivePreviewDraft(
                "LIVE-DRAFT-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT), approved.approvalId(),
                ESER_ID, approved.approvedTextHash(), approved.approvedCharacterCount(),
                List.of("xai", "openai"), List.of("xai", "openai"), total, BigDecimal.ZERO,
                false, false, false, "mono/44.1kHz/-16LUFS/-1dBTP", true,
                OffsetDateTime.now().toString(), OffsetDateTime.now().plusDays(7).toString(), status);
        writeJson(root.resolve("openai-preview-profile.json"), openAi);
        writeJson(root.resolve("xai-preview-profile.json"), xai);
        writeJson(root.resolve("live-preview-draft.json"), draft);
    }

    private void writeAhmetBeyPackageDraft(Path root, ApprovedPassage approved) throws Exception {
        Path draft = root.resolve("ahmet-bey-package-draft");
        Files.createDirectories(draft.resolve("blind"));
        atomicText(draft.resolve("README.txt"),
                "Bu paket henüz ses içermiyor.\n"
                        + "OpenAI ve xAI örnekleri Adım 36'da, ayrıca açık canlı üretim onayıyla üretilecektir.\n"
                        + ATTRIBUTION + "\n"
                        + "Temel edebî eser hakkı ile kullanılan dijital kaynak lisansı ayrı kayıtlardır.\n"
                        + "Planlanan sesler yapay zekâ ile üretildi açıklamasını taşıyacaktır.\n");
        atomicText(draft.resolve("KAYNAK_VE_ATIF.txt"), approved.attributionText() + "\n");
        atomicText(draft.resolve("LISANS_NOTU.txt"),
                "Temel eser: " + approved.underlyingWorkRightsStatus() + "\n"
                        + "Kaynak sürümü: " + approved.sourceEditionLicense() + "\n"
                        + "Ek koşullar uygulanabilir: " + approved.additionalTermsMayApply() + "\n"
                        + "Bu kayıt hukuk danışmanlığı değildir.\n");
        atomicText(draft.resolve("SECILEN_PASAJ.txt"), approved.approvedText() + "\n");
        com.fasterxml.jackson.databind.node.ObjectNode plan = json.createObjectNode();
        plan.put("audioIncluded", false);
        plan.put("liveGenerationAllowed", false);
        var files = plan.putArray("plannedFiles");
        for (String file : List.of("xAI-Grok-TTS.mp3", "OpenAI-gpt-4o-mini-tts.mp3",
                "Piper-mevcut-ses.mp3", "blind/ornek-A.mp3", "blind/ornek-B.mp3",
                "blind/ornek-C.mp3")) files.add(file);
        writeJson(draft.resolve("provider-files-planned.json"), plan);
        com.fasterxml.jackson.databind.node.ObjectNode packageManifest = json.createObjectNode();
        packageManifest.put("selectionId", approved.selectionId());
        packageManifest.put("approvalId", approved.approvalId());
        packageManifest.put("approvedTextHash", approved.approvedTextHash());
        packageManifest.put("sourceAttributionIncluded", true);
        packageManifest.put("audioIncluded", false);
        packageManifest.put("status", "DRAFT_NO_AUDIO_LIVE_LOCKED");
        writeJson(draft.resolve("package-manifest.json"), packageManifest);
    }

    private static void requireConfirmations(PassageApprovalConfirmations confirmations) {
        if (confirmations == null) throw new IllegalStateException("APPROVAL_CONFIRMATIONS_REQUIRED");
        if (!confirmations.fullTextRead()) throw new IllegalStateException("FULL_TEXT_READ_REQUIRED");
        if (!confirmations.normalizationDiffReviewed()) {
            throw new IllegalStateException("NORMALIZATION_DIFF_REVIEW_REQUIRED");
        }
        if (!confirmations.sourceAttributionSeen()) {
            throw new IllegalStateException("SOURCE_ATTRIBUTION_ACKNOWLEDGEMENT_REQUIRED");
        }
        if (!confirmations.rightsLayersUnderstood()) {
            throw new IllegalStateException("RIGHTS_LAYERS_ACKNOWLEDGEMENT_REQUIRED");
        }
        if (!confirmations.shortTtsUseApproved()) throw new IllegalStateException("TTS_USE_APPROVAL_REQUIRED");
        if (!confirmations.paidApiWarningUnderstood()) {
            throw new IllegalStateException("PAID_API_WARNING_ACKNOWLEDGEMENT_REQUIRED");
        }
    }

    private UnderlyingWorkRights readUnderlyingRights(Path root) throws Exception {
        return json.readValue(root.resolve("rights/underlying-work-rights.json").toFile(),
                UnderlyingWorkRights.class);
    }

    private SourceEditionRights readSourceEditionRights(Path root) throws Exception {
        return json.readValue(root.resolve("rights/source-edition-license.json").toFile(),
                SourceEditionRights.class);
    }

    private String sourceRevision() throws Exception {
        String source = Files.readString(sourceFolder().resolve("tam-metin.txt"), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("(?i)oldid=(\\d+)").matcher(source);
        return matcher.find() ? "oldid=" + matcher.group(1) : "REVISION_NOT_RECORDED";
    }

    private String sourceRetrievedAt() throws Exception {
        return readJson(sourceFolder().resolve("alim-manifest.json")).path("alimZamani").asText("");
    }

    private static String attributionText(UnderlyingWorkRights underlying, SourceEditionRights edition,
                                          SourceProvenance provenance) {
        return "Eser: " + underlying.underlyingWorkTitle() + "\n"
                + "Yazar: " + underlying.underlyingWorkAuthor() + "\n"
                + "Kaynak: " + edition.sourceEditionName() + "\n"
                + "Kaynak sayfası: " + edition.sourceEditionUrl() + "\n"
                + "Kaynak revision: " + edition.sourceEditionRevision() + "\n"
                + "Erişim tarihi: " + edition.sourceEditionRetrievedAt() + "\n"
                + "Kullanılan metin SHA-256: " + provenance.sourceTextSha256() + "\n"
                + "Kaynak lisansı: CC BY-SA (CC_BY_SA_RECORDED)\n"
                + "Atıf: " + edition.attributionText() + "\n"
                + "Temel eser hakkı: " + underlying.underlyingWorkRightsStatus()
                + " (kaynak sürümü lisansından ayrı değerlendirilmiştir)\n"
                + "Ek koşullar uygulanabilir: evet\n"
                + "Bu kayıt hukuk danışmanlığı değildir.\n";
    }

    private void verifyCurrentHashes(Path selection, PassageSelectionManifest manifest,
                                     PassageCandidate candidate) throws Exception {
        String currentSource = Files.readString(sourceFolder().resolve("tam-metin.txt"), StandardCharsets.UTF_8);
        if (!TtsAbService.sha256(currentSource).equals(manifest.source().sourceTextHash())) {
            throw new IllegalStateException("SOURCE_HASH_MISMATCH");
        }
        int number = Integer.parseInt(candidate.candidateId().replace("PASSAGE-", ""));
        String original = Files.readString(selection.resolve("candidates/candidate-" + number + "-original.txt"),
                StandardCharsets.UTF_8);
        String normalized = Files.readString(selection.resolve("candidates/candidate-" + number + "-normalized.txt"),
                StandardCharsets.UTF_8);
        if (!TtsAbService.sha256(original).equals(candidate.originalTextHash())
                || !TtsAbService.sha256(normalized).equals(candidate.normalizedTextHash())
                || !normalize(original).text().equals(normalized)) {
            throw new IllegalStateException("PASSAGE_HASH_MISMATCH");
        }
    }

    private static boolean qualityValid(PassageCandidate p) {
        String text = p.normalizedText();
        return p.originalCharacterCount() >= MIN_CHARS && p.originalCharacterCount() <= MAX_CHARS
                && p.normalizedCharacterCount() >= MIN_CHARS && p.normalizedCharacterCount() <= MAX_CHARS
                && !text.isBlank() && naturalStart(text) && naturalEnd(text)
                && !URL.matcher(text).find() && !HTML.matcher(text).find()
                && !hasOcrNoise(text) && !hasMergedWord(text)
                && !text.matches("(?s).* {3,}.*") && !INVISIBLE.matcher(text).find()
                && p.originalTextHash().matches("[0-9a-f]{64}")
                && p.normalizedTextHash().matches("[0-9a-f]{64}");
    }

    private static void validateCandidates(String source, List<PassageCandidate> candidates) {
        if (candidates.size() != 3) throw new IllegalStateException("PASSAGE_CANDIDATE_COUNT_INVALID");
        Set<String> hashes = new HashSet<>();
        for (PassageCandidate candidate : candidates) {
            if (!qualityValid(candidate)) throw new IllegalStateException("PASSAGE_QUALITY_INVALID");
            if (!source.contains(candidate.originalText())) throw new IllegalStateException("SOURCE_FIDELITY_INVALID");
            if (!normalize(candidate.originalText()).text().equals(candidate.normalizedText())) {
                throw new IllegalStateException("NORMALIZATION_NOT_ALLOWED");
            }
            if (!hashes.add(candidate.normalizedTextHash())) throw new IllegalStateException("DUPLICATE_PASSAGE");
        }
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                if (overlapRatio(candidates.get(i), candidates.get(j)) > 0.50) {
                    throw new IllegalStateException("PASSAGE_OVERLAP_TOO_HIGH");
                }
            }
        }
    }

    private static List<PassageCandidate> selectDistinct(List<PassageCandidate> candidates, double maxOverlap) {
        List<PassageCandidate> selected = new ArrayList<>();
        for (PassageCandidate candidate : candidates) {
            if (selected.stream().allMatch(existing -> overlapRatio(existing, candidate) <= maxOverlap)) {
                selected.add(candidate);
                if (selected.size() == 3) break;
            }
        }
        return selected;
    }

    private static PassageCandidate copyWithId(PassageCandidate p, String id) {
        return new PassageCandidate(id, p.eserId(), p.eserBasligi(), p.yazar(), p.sourceType(), p.sourceName(),
                p.sourceUrl(), p.sourceArchiveId(), p.sourceLicenseStatus(), p.sourceLicenseNote(),
                p.sourcePublicDomainStatus(), p.sourceAccessedAt(), p.originalText(), p.normalizedText(),
                p.normalizationChanges(), p.originalTextHash(), p.normalizedTextHash(), p.originalCharacterCount(),
                p.normalizedCharacterCount(), p.wordCount(), p.estimatedSpeechSeconds(), p.sourceStartOffset(),
                p.sourceEndOffset(), p.scores(), null, null, null, PassageApprovalStatus.CANDIDATE);
    }

    private Range storyRange(String source) {
        Matcher paragraphs = Pattern.compile("(?s)(?:^|\\R\\s*\\R)(.{400,}?)(?=\\R\\s*\\R|$)").matcher(source);
        int start = -1;
        while (paragraphs.find()) {
            String paragraph = paragraphs.group(1);
            if (paragraph.toLowerCase(TURKISH).contains("kaşağı") && sentenceCount(paragraph) >= 4) {
                start = paragraphs.start(1);
                break;
            }
        }
        if (start < 0) throw new IllegalStateException("SOURCE_TEXT_INVALID");
        int end = source.length();
        Matcher footer = Pattern.compile("(?iu)[\"“]?\\s*https?://tr\\.wikisource\\.org|Kategori\\s*:").matcher(source);
        if (footer.find(start)) end = footer.start();
        if (end - start < 4_000) throw new IllegalStateException("SOURCE_TEXT_INVALID");
        return new Range(start, end);
    }

    private static List<Range> sentenceRanges(String source, Range body) {
        String text = source.substring(body.start(), body.end());
        BreakIterator iterator = BreakIterator.getSentenceInstance(TURKISH);
        iterator.setText(text);
        List<Range> ranges = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            int absoluteStart = body.start() + start;
            int absoluteEnd = body.start() + end;
            String sentence = source.substring(absoluteStart, absoluteEnd).strip();
            if (sentence.length() < 2) continue;
            int trimmedStart = source.indexOf(sentence, absoluteStart);
            ranges.add(new Range(trimmedStart, trimmedStart + sentence.length()));
        }
        return ranges;
    }

    private Path sourceFolder() throws Exception {
        if (!Files.isDirectory(environment.metinArsivi())) throw new IllegalStateException("SOURCE_TEXT_NOT_FOUND");
        try (Stream<Path> paths = Files.list(environment.metinArsivi())) {
            return paths.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().matches("(?i)^ESER-00005(?:\\s*-.*)?$"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("SOURCE_TEXT_NOT_FOUND"));
        }
    }

    private Path selectionRoot(String id) throws Exception {
        if (id == null || !id.matches("PASSAGE-ESER-00005-[0-9A-Z-]{10,40}")) {
            throw new IllegalArgumentException("INVALID_SELECTION_ID");
        }
        Path path = root().resolve(id).normalize();
        if (!WebGuvenlikService.guvenliAltDosya(root(), path)
                || !Files.isRegularFile(path.resolve("selection-manifest.json"))) {
            throw new IllegalArgumentException("SELECTION_NOT_FOUND");
        }
        return path;
    }

    private PassageSelectionManifest readManifest(Path root) throws Exception {
        return json.readValue(root.resolve("selection-manifest.json").toFile(), PassageSelectionManifest.class);
    }

    private synchronized void useSubmission(Path root, String submissionId) throws Exception {
        if (submissionId == null || !submissionId.matches("[A-Za-z0-9-]{12,80}")) {
            throw new IllegalArgumentException("INVALID_SUBMISSION_ID");
        }
        Path file = root.resolve("submission-ids.txt");
        Set<String> used = new LinkedHashSet<>();
        if (Files.isRegularFile(file)) used.addAll(Files.readAllLines(file, StandardCharsets.UTF_8));
        if (!used.add(submissionId)) throw new IllegalStateException("DUPLICATE_SUBMISSION");
        atomicText(file, String.join("\n", used) + "\n");
    }

    private void writeJson(Path path, Object value) throws Exception {
        byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        if (bytes.length == 0) throw new IllegalStateException("ZERO_BYTE_OUTPUT");
        TtsLaboratuvarYardimci.atomikYaz(path, bytes);
    }

    private static void atomicText(Path path, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) throw new IllegalStateException("ZERO_BYTE_OUTPUT");
        TtsLaboratuvarYardimci.atomikYaz(path, bytes);
    }

    private static void assertNonEmpty(Path root) throws Exception {
        try (Stream<Path> files = Files.walk(root)) {
            if (files.filter(Files::isRegularFile).anyMatch(p -> {
                try { return Files.size(p) == 0; } catch (Exception e) { return true; }
            })) throw new IllegalStateException("ZERO_BYTE_OUTPUT");
        }
    }

    private JsonNode readJson(Path path) throws Exception {
        if (!Files.isRegularFile(path) || Files.size(path) == 0) {
            throw new IllegalStateException("SOURCE_TEXT_INVALID");
        }
        return json.readTree(path.toFile());
    }

    private static PassageLicenseStatus verifiedLicenseStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("PUBLIC DOMAIN") || normalized.contains("KAMU MALI")) {
            return PassageLicenseStatus.VERIFIED_PUBLIC_DOMAIN;
        }
        if (normalized.startsWith("VERIFIED") || normalized.startsWith("DOĞRULANMIŞ")) {
            return PassageLicenseStatus.VERIFIED_LICENSED;
        }
        return normalized.isBlank() ? PassageLicenseStatus.UNKNOWN : PassageLicenseStatus.REVIEW_REQUIRED;
    }

    private static String archiveId(String sourceUrl) {
        if (sourceUrl == null || !sourceUrl.contains("archive.org")) return "";
        Matcher matcher = Pattern.compile("archive\\.org/(?:details|download)/([^/?#]+)").matcher(sourceUrl);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static int sentenceCount(String text) {
        BreakIterator iterator = BreakIterator.getSentenceInstance(TURKISH);
        iterator.setText(text);
        int count = 0;
        for (int end = iterator.first(); (end = iterator.next()) != BreakIterator.DONE; ) count++;
        return count;
    }

    private static int wordCount(String text) {
        String stripped = text == null ? "" : text.strip();
        return stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
    }

    private static int matches(Pattern pattern, String text) {
        int count = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) count++;
        return count;
    }

    private static int properNameVariety(String text) {
        int count = 0;
        for (String name : List.of("Kaşağı", "Hasan", "Dadaruh", "Tosun", "Pervin", "İstanbul")) {
            if (text.contains(name)) count++;
        }
        return count;
    }

    static PassageLicenseStatus approvalLicense(String sourceType, PassageLicenseStatus license,
                                                boolean rightsConfirmed) {
        if (!TtsAbSourceType.APPROVED_ARCHIVE_TEXT.name().equals(sourceType)) {
            throw new IllegalStateException("FIXTURE_SOURCE_NOT_ALLOWED");
        }
        if (license.liveEligible()) return license;
        if (!rightsConfirmed) throw new IllegalStateException("SOURCE_LICENSE_REVIEW_REQUIRED");
        return PassageLicenseStatus.USER_CONFIRMED_RIGHTS;
    }

    static boolean naturalStart(String text) {
        if (text == null || text.isBlank()) return false;
        int first = text.codePointAt(0);
        return Character.isUpperCase(first) || first == '—' || first == '"' || first == '“';
    }

    static boolean naturalEnd(String text) {
        if (text == null || text.isBlank()) return false;
        String stripped = text.strip();
        char last = stripped.charAt(stripped.length() - 1);
        return ".!?…\"”".indexOf(last) >= 0;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(10, value));
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = System.getProperty(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal decimalEnv(String name, BigDecimal fallback) {
        String value = env(name);
        if (value == null) return fallback;
        try {
            BigDecimal parsed = new BigDecimal(value);
            return parsed.signum() >= 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    record NormalizationResult(String text, List<String> changes) {
    }

    private record Range(int start, int end) {
    }
}
