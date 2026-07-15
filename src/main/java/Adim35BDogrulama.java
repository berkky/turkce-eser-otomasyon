import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class Adim35BDogrulama {
    private static int checks;
    private static final ObjectMapper JSON = new ObjectMapper();

    private Adim35BDogrulama() {
    }

    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        forceOffline();
        Path temporary = Files.createTempDirectory("adim35b-");
        try {
            WebOrtam actual = WebOrtam.varsayilan();
            canonicalNotApproved(actual);
            WebOrtam test = new WebOrtam(Path.of("").toAbsolutePath(), temporary.resolve("gelen"),
                    temporary.resolve("arsiv"), actual.metinArsivi(), temporary.resolve("ses"),
                    temporary.resolve("kuyruk"), temporary.resolve("katalog.xlsx"),
                    temporary.resolve("panel"));
            rightsAndRecommendations(test);
            approvalRequestSchemaUpgrade(test);
            approvalGuardsProfilesAndPackage(test);
            webAndOffline(test);
            documentation();
            System.out.println("ADIM 35B DOGRULAMA: BASARILI (" + checks + " kontrol)");
        } finally {
            deleteTree(temporary);
        }
    }

    private static void approvalRequestSchemaUpgrade(WebOrtam environment) throws Exception {
        KasagiPasajService service = new KasagiPasajService(environment);
        Path root = service.createSelection();
        String id = root.getFileName().toString();
        Path approval = root.resolve("approval-request.json");
        String requiredSourceHash = JSON.readTree(approval.toFile()).path("requiredSourceHash").asText();
        byte[] legacyBytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(
                Map.of(
                        "selectionId", id,
                        "eserId", 5,
                        "requiredSourceHash", requiredSourceHash,
                        "requiredConfirmations", List.of("SOURCE_AND_TEXT_CONFIRMED", "RIGHTS_CONFIRMED_BY_USER"),
                        "liveApiCallPlanned", false,
                        "status", "AWAITING_SELECTION"));
        TtsLaboratuvarYardimci.atomikYaz(approval, legacyBytes);
        String sourceHashBefore = Files.readString(root.resolve("source/source.sha256"),
                StandardCharsets.UTF_8).trim();
        String manifestHashBefore = XaiTtsSaglayici.sha256(root.resolve("selection-manifest.json"));
        String candidate1HashBefore = XaiTtsSaglayici.sha256(root.resolve("candidates/candidate-1.json"));
        check(service.upgradeApprovalRequestSchema(id), "Schema upgrade ilk çalıştırma dosyayı günceller");
        JsonNode upgraded = JSON.readTree(approval.toFile());
        check(KasagiPasajService.APPROVAL_REQUEST_SCHEMA_VERSION.equals(upgraded.path("schemaVersion").asText()),
                "schemaVersion 35B.1");
        for (String code : KasagiPasajService.REQUIRED_ACKNOWLEDGEMENTS) {
            check(upgraded.path("requiredAcknowledgements").toString().contains("\"" + code + "\""),
                    "requiredAcknowledgements içerir: " + code);
        }
        check(upgraded.path("legacyAcknowledgements").toString().contains("SOURCE_AND_TEXT_CONFIRMED")
                        && upgraded.path("legacyAcknowledgements").toString().contains("RIGHTS_CONFIRMED_BY_USER"),
                "legacyAcknowledgements eski iki kodu saklar");
        check("AWAITING_SELECTION".equals(upgraded.path("status").asText())
                        && !upgraded.path("liveApiCallPlanned").asBoolean(),
                "Upgrade selection/approval üretmez, AWAITING_SELECTION kalır");
        check(!service.upgradeApprovalRequestSchema(id), "Schema upgrade ikinci kez dosyayı değiştirmez");
        PassageSelectionManifest manifest = service.readManifest(id);
        check(manifest.selectedCandidateId() == null && "CANDIDATES_READY".equals(manifest.status()),
                "Upgrade sonrası selectedCandidateId null / CANDIDATES_READY");
        check(!Files.exists(root.resolve("approved-passage.json"))
                        && !Files.exists(root.resolve("openai-preview-profile.json"))
                        && !Files.exists(root.resolve("xai-preview-profile.json"))
                        && !Files.exists(root.resolve("ahmet-bey-package-draft")),
                "Upgrade onay veya canlı üretim dosyası oluşturmaz");
        check(sourceHashBefore.equals(Files.readString(root.resolve("source/source.sha256"),
                        StandardCharsets.UTF_8).trim())
                        && manifestHashBefore.equals(XaiTtsSaglayici.sha256(root.resolve("selection-manifest.json")))
                        && candidate1HashBefore.equals(XaiTtsSaglayici.sha256(
                        root.resolve("candidates/candidate-1.json"))),
                "Upgrade hash ve source pin değiştirmez");
        check(noAudio(root), "Upgrade MP3/WAV üretmez");
    }

    private static void canonicalNotApproved(WebOrtam actual) throws Exception {
        KasagiPasajService service = new KasagiPasajService(actual);
        PassageSelectionManifest latest = service.latestSelection();
        check(latest != null && latest.selectedCandidateId() == null
                        && "CANDIDATES_READY".equals(latest.status()),
                "Canonical selection kullanıcı adına seçilmedi");
        check(!Files.exists(service.root().resolve(latest.selectionId()).resolve("approved-passage.json")),
                "Canonical approved-passage kullanıcı onayı olmadan yok");
    }

    private static void rightsAndRecommendations(WebOrtam environment) throws Exception {
        KasagiPasajService service = new KasagiPasajService(environment);
        Path root = service.createSelection();
        Path rights = root.resolve("rights");
        for (String name : List.of("underlying-work-rights.json", "source-edition-license.json",
                "source-provenance.json", "SOURCE_ATTRIBUTION.txt", "SOURCE_ATTRIBUTION.json",
                "RIGHTS_REVIEW.md", "rights-evidence.sha256")) {
            check(Files.isRegularFile(rights.resolve(name)) && Files.size(rights.resolve(name)) > 0,
                    "Hak kanıt dosyası: " + name);
        }
        JsonNode underlying = JSON.readTree(rights.resolve("underlying-work-rights.json").toFile());
        JsonNode edition = JSON.readTree(rights.resolve("source-edition-license.json").toFile());
        check(underlying.path("underlyingWorkRightsStatus").asText()
                        .equals("PUBLIC_DOMAIN_BASIS_VERIFIED")
                        && underlying.path("authorDeathYear").asInt() == 1920,
                "Temel eser hakkı ve ölüm yılı kanıtı");
        check(underlying.path("underlyingWorkRightsEvidence").toString().contains("70 yıl")
                        && !underlying.path("legalAdvice").asBoolean(),
                "FSEK dayanağı kayıtlı, nihai hukuk görüşü yok");
        check(edition.path("sourceEditionLicense").asText().equals("CC_BY_SA_RECORDED")
                        && edition.path("attributionRequired").asBoolean()
                        && edition.path("additionalTermsMayApply").asBoolean()
                        && !edition.path("commercialUseReady").asBoolean(),
                "Kaynak sürümü lisansı ayrı ve ticari kullanım henüz hazır değil");
        String attribution = Files.readString(rights.resolve("SOURCE_ATTRIBUTION.txt"),
                StandardCharsets.UTF_8);
        check(attribution.contains("Ömer Seyfettin") && attribution.contains("oldid=172873")
                        && attribution.contains("76b90722") && attribution.contains("hukuk danışmanlığı değildir"),
                "İnsan okunabilir atıf ve provenance");
        check(!attribution.matches("(?is).*[A-Z]:\\\\Users\\\\.*")
                        && !attribution.contains("C:/Users/"),
                "Atıfta absolute kullanıcı yolu yok");
        PassageSelectionManifest manifest = service.readManifest(root.getFileName().toString());
        check(manifest.selectedCandidateId() == null
                        && KasagiPasajService.recommendationType("PASSAGE-1")
                        .equals(KasagiPasajService.PRIMARY_RECOMMENDATION),
                "PASSAGE-1 yalnız primary recommendation");
        check(KasagiPasajService.recommendationType("PASSAGE-3")
                        .equals(KasagiPasajService.EMOTIONAL_RECOMMENDATION),
                "PASSAGE-3 emotional followup recommendation");
    }

    private static void approvalGuardsProfilesAndPackage(WebOrtam environment) throws Exception {
        KasagiPasajService service = new KasagiPasajService(environment);
        PassageSelectionManifest latest = service.latestSelection();
        String id = latest.selectionId();
        expect("PASSAGE_SELECTION_REQUIRED",
                () -> service.approve(id, submission(), allConfirmed(), "LOCAL_USER"),
                "Kullanıcı seçimi olmadan onay reddi");
        service.select(id, "PASSAGE-2", submission());
        expectMissing(service, id, "FULL_TEXT_READ_REQUIRED",
                new PassageApprovalConfirmations(false, true, true, true, true, true));
        expectMissing(service, id, "NORMALIZATION_DIFF_REVIEW_REQUIRED",
                new PassageApprovalConfirmations(true, false, true, true, true, true));
        expectMissing(service, id, "SOURCE_ATTRIBUTION_ACKNOWLEDGEMENT_REQUIRED",
                new PassageApprovalConfirmations(true, true, false, true, true, true));
        expectMissing(service, id, "RIGHTS_LAYERS_ACKNOWLEDGEMENT_REQUIRED",
                new PassageApprovalConfirmations(true, true, true, false, true, true));
        expectMissing(service, id, "TTS_USE_APPROVAL_REQUIRED",
                new PassageApprovalConfirmations(true, true, true, true, false, true));
        expectMissing(service, id, "PAID_API_WARNING_ACKNOWLEDGEMENT_REQUIRED",
                new PassageApprovalConfirmations(true, true, true, true, true, false));
        ApprovedPassage approved = service.approve(id, submission(), allConfirmed(), "LOCAL_USER");
        check(approved.sourceApproved() && approved.rightsReviewed()
                        && approved.rightsAcknowledgedByUser()
                        && approved.commercialUseApprovedByUser(),
                "Hak ve ticari kullanım kullanıcı kabulleri kaydedildi");
        check(!approved.liveGenerationAllowed() && !approved.openAiAllowed() && !approved.xaiAllowed()
                        && approved.budgetLimitUsd().signum() == 0 && approved.maxProviderCount() == 0
                        && approved.status().equals("PASSAGE_APPROVED_LIVE_LOCKED"),
                "Onay sonrası canlı sağlayıcılar ve bütçe kilitli");
        Path root = service.root().resolve(id);
        for (String profileName : List.of("openai-preview-profile.json", "xai-preview-profile.json")) {
            JsonNode profile = JSON.readTree(root.resolve(profileName).toFile());
            check(profile.path("inputTextHash").asText().equals(approved.approvedTextHash())
                            && profile.path("underlyingWorkRightsStatus").asText()
                            .equals("PUBLIC_DOMAIN_BASIS_VERIFIED")
                            && profile.path("sourceEditionLicense").asText().equals("CC_BY_SA_RECORDED")
                            && profile.path("attributionRequired").asBoolean()
                            && profile.path("rightsAcknowledgedByUser").asBoolean()
                            && profile.path("commercialUseApprovedByUser").asBoolean()
                            && !profile.path("liveEnabled").asBoolean()
                            && !profile.path("userApproved").asBoolean(),
                    profileName + " hak/atıf kaydı ve canlı kilidi");
        }
        Path draft = root.resolve("ahmet-bey-package-draft");
        check(Files.isRegularFile(draft.resolve("KAYNAK_VE_ATIF.txt"))
                        && Files.isRegularFile(draft.resolve("SECILEN_PASAJ.txt"))
                        && Files.readString(draft.resolve("README.txt")).contains("henüz ses içermiyor"),
                "Ahmet Bey atıflı paket taslağı");
        check(noAudio(root), "Approval ve paket taslağında MP3/WAV yok");
    }

    private static void webAndOffline(WebOrtam environment) throws Exception {
        KasagiPasajService service = new KasagiPasajService(environment);
        Path root = service.createSelection();
        YerelWebSunucu server = new YerelWebSunucu(environment);
        String html = new String(server.route("GET", "/ab-test/pasajlar/ESER-00005",
                "", "", true).body(), StandardCharsets.UTF_8);
        PassageSelectionManifest manifest = service.readManifest(root.getFileName().toString());
        for (PassageCandidate candidate : manifest.candidates()) {
            check(html.contains(WebGuvenlikService.htmlKacis(candidate.originalText()))
                            && html.contains(WebGuvenlikService.htmlKacis(candidate.normalizedText())),
                    candidate.candidateId() + " tam original ve normalized web metni");
        }
        check(html.contains("originalTextHash") && html.contains("normalizedTextHash")
                        && html.contains("Kaynak offset") && html.contains("En yüksek overlap")
                        && html.contains("Satır/kelime fark özeti"),
                "Web panel tam inceleme ayrıntıları");
        check(server.route("GET", "/ab-test/pasajlar/ESER-00005/haklar",
                "", "", true).status() == 200
                        && server.route("GET", "/api/ab-test/pasajlar/ESER-00005/haklar",
                        "", "", true).status() == 200
                        && server.route("GET", "/ab-test/pasajlar/ESER-00005/atif",
                        "", "", true).status() == 200,
                "Hak ve atıf GET route'ları");
        check(server.route("GET", "/ab-test/pasajlar/ESER-00005/haklar",
                "", "", false).status() == 403, "Hak route localhost-only");
        for (String flag : List.of("ELEVENLABS_LIVE_ENABLED", "XAI_TTS_LIVE_ENABLED",
                "OPENAI_TTS_LIVE_ENABLED", "GOOGLE_TTS_LIVE_ENABLED",
                "AZURE_TTS_LIVE_ENABLED", "CARTESIA_TTS_LIVE_ENABLED")) {
            check(!Boolean.parseBoolean(System.getProperty(flag)), flag + " kapalı");
        }
        for (String file : List.of("KasagiPasajService.java", "KasagiPasajWebService.java",
                "Adim35PasajApp.java")) {
            String source = Files.readString(Path.of("src/main/java").resolve(file));
            check(!source.contains("HttpClient.newHttpClient") && !source.contains(".sendAsync("),
                    file + " harici ağ transportu içermez");
        }
    }

    private static void documentation() {
        for (String name : List.of("ADIM_35B_KAYNAK_HAKKI_VE_ATIF.md",
                "ADIM_35B_MANUEL_PASAJ_ONAYI.md", "AHMET_BEY_SES_PAKETI_ATIF_REHBERI.md")) {
            check(Files.isRegularFile(Path.of("docs").resolve(name)), "Doküman: " + name);
        }
    }

    private static void expectMissing(KasagiPasajService service, String id, String code,
                                      PassageApprovalConfirmations confirmations) throws Exception {
        expect(code, () -> service.approve(id, submission(), confirmations, "LOCAL_USER"),
                code + " kontrolü");
    }

    private static PassageApprovalConfirmations allConfirmed() {
        return new PassageApprovalConfirmations(true, true, true, true, true, true);
    }

    private static String submission() {
        return "submission-" + UUID.randomUUID();
    }

    private static boolean noAudio(Path root) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.noneMatch(p -> p.getFileName().toString().matches("(?i).+\\.(mp3|wav)$"));
        }
    }

    private static void forceOffline() {
        System.setProperty("ELEVENLABS_OFFLINE", "true");
        for (String name : List.of("ELEVENLABS_LIVE_ENABLED", "XAI_TTS_LIVE_ENABLED",
                "OPENAI_TTS_LIVE_ENABLED", "GOOGLE_TTS_LIVE_ENABLED",
                "AZURE_TTS_LIVE_ENABLED", "CARTESIA_TTS_LIVE_ENABLED")) {
            System.setProperty(name, "false");
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
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
        } catch (Exception exception) {
            if (exception.getMessage() == null || !exception.getMessage().contains(code)) throw exception;
            check(true, name);
        }
    }

    @FunctionalInterface
    private interface Throwing {
        void run() throws Exception;
    }
}
