import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class Adim35Dogrulama {
    private static int checks;

    private Adim35Dogrulama() {
    }

    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        forceOfflineProperties();
        Path temporary = Files.createTempDirectory("adim35-");
        try {
            WebOrtam actual = WebOrtam.varsayilan();
            sourceAndCandidates(actual);
            storageApprovalAndProfiles(actual, temporary);
            sourceMutationAndInvalidSources(actual, temporary);
            webFlow(actual, temporary);
            offlineAndDocumentation();
            System.out.println("ADIM 35 DOGRULAMA: BASARILI (" + checks + " kontrol)");
        } finally {
            deleteTree(temporary);
        }
    }

    private static void sourceAndCandidates(WebOrtam actual) throws Exception {
        KasagiPasajService service = new KasagiPasajService(actual);
        PassageSourceSummary source = service.resolveSource();
        check(source.eserId() == 5 && source.sourceType().equals("APPROVED_ARCHIVE_TEXT"),
                "Canonical ESER-00005 gerçek kaynak");
        check(source.sourceCharacterCount() > 7_000, "Kaynak karakter sayısı >7000");
        check(source.ttsPartCount() > 0, "Gerçek TTS parça sayısı");
        check(source.sourceLicenseStatus() == PassageLicenseStatus.REVIEW_REQUIRED
                        && source.sourcePublicDomainStatus().equals("NOT_VERIFIED"),
                "Lisans kendiliğinden public domain sayılmaz");

        List<PassageCandidate> first = service.analyzeCandidates();
        List<PassageCandidate> second = service.analyzeCandidates();
        check(first.size() == 3, "En iyi üç pasaj adayı");
        Set<String> hashes = new HashSet<>();
        String fullSource = Files.readString(findSourceFolder(actual).resolve("tam-metin.txt"),
                StandardCharsets.UTF_8);
        for (int i = 0; i < first.size(); i++) {
            PassageCandidate candidate = first.get(i);
            check(candidate.originalCharacterCount() >= 900 && candidate.originalCharacterCount() <= 1250
                            && candidate.normalizedCharacterCount() >= 900
                            && candidate.normalizedCharacterCount() <= 1250,
                    candidate.candidateId() + " karakter sınırı");
            check(hashes.add(candidate.normalizedTextHash()), candidate.candidateId() + " benzersiz hash");
            check(fullSource.contains(candidate.originalText()), candidate.candidateId() + " canonical kaynak sadakati");
            check(KasagiPasajService.normalize(candidate.originalText()).text().equals(candidate.normalizedText()),
                    candidate.candidateId() + " yalnız izinli normalizasyon");
            check(candidate.normalizedText().contains("Kaşağı") || candidate.normalizedText().contains("kaşağı"),
                    candidate.candidateId() + " eser kelimesi/telaffuz çeşitliliği");
            check(candidate.normalizedTextHash().equals(second.get(i).normalizedTextHash()),
                    candidate.candidateId() + " deterministik algoritma");
            check(candidate.estimatedSpeechSeconds() >= 60 && candidate.estimatedSpeechSeconds() <= 90,
                    candidate.candidateId() + " 60-90 saniye hedefi");
        }
        for (int i = 0; i < first.size(); i++) {
            for (int j = i + 1; j < first.size(); j++) {
                check(KasagiPasajService.overlapRatio(first.get(i), first.get(j)) <= 0.50,
                        "Aday overlap sınırı " + (i + 1) + "/" + (j + 1));
            }
        }
        check(KasagiPasajService.hasMergedWord("bakışınıgörünce hemen sustu."),
                "Birleşmiş kelime hatası tespiti");
        check(KasagiPasajService.hasOcrNoise("ve #8212; bozuk OCR"),
                "OCR gürültüsü tespiti");
        check(!KasagiPasajService.naturalStart("cümle ortasında başlar."),
                "Cümle ortası başlangıç reddi");
        check(!KasagiPasajService.naturalEnd("Doğal bitiş yok"),
                "Cümle ortası bitiş reddi");
        expect("FIXTURE_SOURCE_NOT_ALLOWED",
                () -> KasagiPasajService.approvalLicense("FIXTURE",
                        PassageLicenseStatus.USER_CONFIRMED_RIGHTS, true),
                "Fixture canlı hazırlık reddi");
        expect("SOURCE_LICENSE_REVIEW_REQUIRED",
                () -> KasagiPasajService.approvalLicense("APPROVED_ARCHIVE_TEXT",
                        PassageLicenseStatus.REVIEW_REQUIRED, false),
                "REVIEW_REQUIRED kullanıcı hak onayı olmadan reddi");
    }

    private static void storageApprovalAndProfiles(WebOrtam actual, Path temporary) throws Exception {
        WebOrtam environment = tempEnvironment(actual.metinArsivi(), temporary.resolve("storage"));
        KasagiPasajService service = new KasagiPasajService(environment);
        Path firstRoot = service.createSelection();
        String firstId = firstRoot.getFileName().toString();
        String firstHash = XaiTtsSaglayici.sha256(firstRoot.resolve("selection-manifest.json"));
        Thread.sleep(5);
        Path secondRoot = service.createSelection();
        check(!firstRoot.equals(secondRoot) && firstHash.equals(
                XaiTtsSaglayici.sha256(firstRoot.resolve("selection-manifest.json"))),
                "Eski seçim klasörünün üzerine yazılmaz");
        check(allFilesNonEmpty(firstRoot) && noTemporaryFiles(firstRoot), "Atomic ve 0-byte olmayan yazım");
        check(noAudioFiles(firstRoot), "Aday paketinde ses dosyası yok");
        check(Files.readString(firstRoot.resolve("candidates/candidate-1-normalized.txt"),
                StandardCharsets.UTF_8).contains("Kaşağı"), "UTF-8 Türkçe korunur");

        expect("PASSAGE_SELECTION_REQUIRED",
                () -> service.approve(firstId, submission("noselection"), true, true, "TEST"),
                "Seçim olmadan onay reddi");
        String duplicate = submission("duplicate");
        service.select(firstId, "PASSAGE-1", duplicate);
        expect("DUPLICATE_SUBMISSION",
                () -> service.select(firstId, "PASSAGE-1", duplicate),
                "Çift submission reddi");
        expect("SOURCE_ATTRIBUTION_ACKNOWLEDGEMENT_REQUIRED",
                () -> service.approve(firstId, submission("license"), true, false, "TEST"),
                "Kaynak ve atıf onayı olmadan approval reddi");
        ApprovedPassage approved = service.approve(firstId, submission("approve"), true, true, "TEST_USER");
        check(approved.status().equals("PASSAGE_APPROVED_LIVE_LOCKED"), "Onay status kilitli");
        check(approved.sourceApproved() && !approved.liveGenerationAllowed()
                        && !approved.openAiAllowed() && !approved.xaiAllowed(),
                "Canlı/OpenAI/xAI izinleri kapalı");
        check(approved.budgetLimitUsd().signum() == 0 && approved.maxProviderCount() == 0,
                "Bütçe ve sağlayıcı limiti sıfır");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode openAi = mapper.readTree(firstRoot.resolve("openai-preview-profile.json").toFile());
        JsonNode xai = mapper.readTree(firstRoot.resolve("xai-preview-profile.json").toFile());
        JsonNode draft = mapper.readTree(firstRoot.resolve("live-preview-draft.json").toFile());
        String profileText = openAi.toString() + xai;
        check(openAi.path("endpoint").asText().equals("https://api.openai.com/v1/audio/speech")
                        && !openAi.path("liveEnabled").asBoolean()
                        && openAi.path("costEstimate").path("calculationType").asText().equals("ESTIMATED_ONLY"),
                "OpenAI kilitli ESTIMATED_ONLY profili");
        check(xai.path("endpoint").asText().equals("https://api.x.ai/v1/tts")
                        && !xai.path("liveEnabled").asBoolean()
                        && xai.path("speed").asDouble() == 1.0,
                "xAI kilitli voice-doğrulanmamış profili");
        check(!profileText.toLowerCase().contains("apikey")
                        && !profileText.toLowerCase().contains("api_key")
                        && !profileText.toLowerCase().contains("authorization")
                        && !profileText.contains("sk-"),
                "Hazırlık profillerinde secret yok");
        check(!draft.path("liveGenerationApproved").asBoolean()
                        && !draft.path("budgetApproved").asBoolean()
                        && draft.path("maxAllowedCostUsd").decimalValue().signum() == 0
                        && draft.path("status").asText().equals("LIVE_GENERATION_LOCKED"),
                "Ortak canlı üretim taslağı kilitli");
        check(secondRoot.getFileName().toString().startsWith("PASSAGE-ESER-00005-"),
                "Canonical seçim klasörü güvenli ID");
    }

    private static void sourceMutationAndInvalidSources(WebOrtam actual, Path temporary) throws Exception {
        Path copiedTextRoot = temporary.resolve("copied-source/metin");
        copyTree(findSourceFolder(actual), copiedTextRoot.resolve("ESER-00005 - Kasagi - Vikikaynak"));
        WebOrtam copiedEnvironment = tempEnvironment(copiedTextRoot, temporary.resolve("mutated-output"));
        KasagiPasajService copiedService = new KasagiPasajService(copiedEnvironment);
        Path selection = copiedService.createSelection();
        String selectionId = selection.getFileName().toString();
        copiedService.select(selectionId, "PASSAGE-1", submission("mutation-select"));
        Path fullText = copiedTextRoot.resolve("ESER-00005 - Kasagi - Vikikaynak/tam-metin.txt");
        Files.writeString(fullText, Files.readString(fullText, StandardCharsets.UTF_8) + "\n",
                StandardCharsets.UTF_8);
        expect("SOURCE_HASH_MISMATCH",
                () -> copiedService.approve(selectionId, submission("mutation-approve"),
                        true, true, "TEST"),
                "Kaynak hash değişikliği onayı iptal eder");

        Path invalidRoot = temporary.resolve("invalid-source/metin/ESER-00005 - Invalid");
        Files.createDirectories(invalidRoot.resolve("tts-parcalari"));
        Files.write(invalidRoot.resolve("tam-metin.txt"), new byte[0]);
        Files.writeString(invalidRoot.resolve("alim-manifest.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(invalidRoot.resolve("tts-parcalari/001-001.txt"), "metin", StandardCharsets.UTF_8);
        KasagiPasajService invalid = new KasagiPasajService(
                tempEnvironment(invalidRoot.getParent(), temporary.resolve("invalid-output")));
        expect("SOURCE_TEXT_NOT_FOUND", invalid::resolveSource, "0-byte kaynak reddi");
    }

    private static void webFlow(WebOrtam actual, Path temporary) throws Exception {
        WebOrtam environment = tempEnvironment(actual.metinArsivi(), temporary.resolve("web"));
        KasagiPasajService service = new KasagiPasajService(environment);
        Path selection = service.createSelection();
        Path manifest = selection.resolve("selection-manifest.json");
        String beforeHash = XaiTtsSaglayici.sha256(manifest);
        var beforeTime = Files.getLastModifiedTime(manifest);
        YerelWebSunucu server = new YerelWebSunucu(environment);

        WebResponse pageResponse = server.route("GET", "/ab-test/pasajlar/ESER-00005", "", "", true);
        String page = new String(pageResponse.body(), StandardCharsets.UTF_8);
        check(pageResponse.status() == 200 && count(page, "PASSAGE-") >= 3
                        && page.contains("Bu işlem henüz ücretli API çağrısı veya ses üretimi yapmaz"),
                "Web sayfası üç adayı ve canlı uyarıyı gösterir");
        check(beforeHash.equals(XaiTtsSaglayici.sha256(manifest))
                        && beforeTime.equals(Files.getLastModifiedTime(manifest)),
                "GET endpoint dosya değiştirmez");
        check(server.route("GET", "/ab-test/pasajlar/ESER-00005", "", "", false).status() == 403,
                "Non-localhost engellenir");
        String token = csrfTokens(page).getFirst();
        String selectionId = selection.getFileName().toString();
        expect("CSRF_TOKEN_INVALID",
                () -> server.route("POST", "/ab-test/pasajlar/ESER-00005/sec", "",
                        form(MapBuilder.of("selectionId", selectionId, "csrfToken", "invalid",
                                "submissionId", submission("csrf-invalid"), "candidateId", "PASSAGE-1",
                                "candidateTextReviewed", "true")), true),
                "CSRF kontrolü");
        WebResponse selected = server.route("POST", "/ab-test/pasajlar/ESER-00005/sec", "",
                form(MapBuilder.of("selectionId", selectionId, "csrfToken", token,
                        "submissionId", submission("web-select"), "candidateId", "PASSAGE-1",
                        "candidateTextReviewed", "true")), true);
        check(selected.status() == 200, "Web pasaj seçimi");
        String selectedPage = new String(selected.body(), StandardCharsets.UTF_8);
        List<String> approvalTokens = csrfTokens(selectedPage);
        String approvalToken = approvalTokens.getLast();
        WebResponse approved = server.route("POST", "/ab-test/pasajlar/ESER-00005/onayla", "",
                form(MapBuilder.of("selectionId", selectionId, "csrfToken", approvalToken,
                        "submissionId", submission("web-approve"), "fullTextRead", "true",
                        "normalizationDiffReviewed", "true", "sourceAttributionSeen", "true",
                        "rightsLayersUnderstood", "true", "shortTtsUseApproved", "true",
                        "paidApiWarningUnderstood", "true")), true);
        check(approved.status() == 200 && new String(approved.body(), StandardCharsets.UTF_8)
                .contains("PASSAGE_APPROVED_LIVE_LOCKED"), "Web onay akışı canlı kilitli");
        expect("CSRF_TOKEN_INVALID",
                () -> server.route("POST", "/ab-test/pasajlar/ESER-00005/onayla", "",
                        form(MapBuilder.of("selectionId", selectionId, "csrfToken", approvalToken,
                                "submissionId", submission("csrf-reuse"), "fullTextRead", "true",
                                "normalizationDiffReviewed", "true", "sourceAttributionSeen", "true",
                                "rightsLayersUnderstood", "true", "shortTtsUseApproved", "true",
                                "paidApiWarningUnderstood", "true")), true),
                "Tek kullanımlık token");
        String api = new String(server.route("GET", "/api/ab-test/pasajlar/ESER-00005", "", "", true).body(),
                StandardCharsets.UTF_8);
        String status = new String(server.route("GET", "/api/ab-test/pasajlar/ESER-00005/durum",
                "", "", true).body(), StandardCharsets.UTF_8);
        check(!api.contains("C:\\Users\\") && !api.contains("C:/Users/"),
                "Public JSON tam kullanıcı yolu sızdırmaz");
        check(status.contains("\"liveGenerationAllowed\" : false")
                        && status.contains("\"externalApiCalled\" : false"),
                "Web durum API canlı üretim ve ağ çağrısını kilitler");
        try {
            server.baslat(0);
            HttpResponse<String> local = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port()
                                    + "/ab-test/pasajlar/ESER-00005"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            check(local.statusCode() == 200 && local.body().contains("Kaşağı Pasajları"),
                    "Web route gerçek localhost portunda");
        } finally {
            server.durdur();
        }
    }

    private static void offlineAndDocumentation() throws Exception {
        check(offline("ELEVENLABS_OFFLINE", true)
                        && offline("ELEVENLABS_LIVE_ENABLED", false)
                        && offline("XAI_TTS_LIVE_ENABLED", false)
                        && offline("OPENAI_TTS_LIVE_ENABLED", false)
                        && offline("GOOGLE_TTS_LIVE_ENABLED", false)
                        && offline("AZURE_TTS_LIVE_ENABLED", false)
                        && offline("CARTESIA_TTS_LIVE_ENABLED", false),
                "Tüm harici sağlayıcı live kapıları kapalı");
        check(ElevenLabsFabrika.durumOzeti().konsolOzeti().contains("OFFLINE"),
                "ElevenLabs abonelik API çağrısı yapılmaz");
        for (String file : List.of("KasagiPasajService.java", "KasagiPasajWebService.java",
                "Adim35PasajApp.java")) {
            String source = Files.readString(Path.of("src/main/java").resolve(file), StandardCharsets.UTF_8);
            check(!source.contains("HttpClient.newHttpClient") && !source.contains(".sendAsync(")
                            && !source.contains(".send(request"),
                    file + " dış ağ transportu içermez");
        }
        Path documentation = Path.of("docs/ADIM_35_PASAJ_SECIMI_VE_ONAY.md");
        check(Files.isRegularFile(documentation)
                        && Files.readString(documentation, StandardCharsets.UTF_8).contains("47d23dc"),
                "Git başlangıç beklentisi dokümante");
    }

    private static WebOrtam tempEnvironment(Path textRoot, Path root) {
        return new WebOrtam(Path.of("").toAbsolutePath(), root.resolve("gelen"), root.resolve("arsiv"),
                textRoot, root.resolve("ses"), root.resolve("kuyruk"), root.resolve("katalog.xlsx"),
                root.resolve("panel"));
    }

    private static Path findSourceFolder(WebOrtam environment) throws Exception {
        try (Stream<Path> paths = Files.list(environment.metinArsivi())) {
            return paths.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("ESER-00005"))
                    .findFirst().orElseThrow();
        }
    }

    private static boolean allFilesNonEmpty(Path root) throws Exception {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).allMatch(p -> {
                try { return Files.size(p) > 0; } catch (Exception e) { return false; }
            });
        }
    }

    private static boolean noTemporaryFiles(Path root) throws Exception {
        try (Stream<Path> files = Files.walk(root)) {
            return files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")
                    || p.getFileName().toString().endsWith(".partial"));
        }
    }

    private static boolean noAudioFiles(Path root) throws Exception {
        try (Stream<Path> files = Files.walk(root)) {
            return files.noneMatch(p -> p.getFileName().toString().matches("(?i).+\\.(mp3|wav|m4a|ogg)"));
        }
    }

    private static List<String> csrfTokens(String html) {
        Matcher matcher = Pattern.compile("name=\"csrfToken\" value=\"([^\"]+)\"").matcher(html);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) tokens.add(matcher.group(1));
        return tokens;
    }

    private static String form(java.util.Map<String, String> values) {
        return values.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private static int count(String text, String needle) {
        return text.split(Pattern.quote(needle), -1).length - 1;
    }

    private static String submission(String suffix) {
        return "submission-" + suffix + "-" + UUID.randomUUID();
    }

    private static boolean offline(String name, boolean expected) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = System.getProperty(name);
        return Boolean.parseBoolean(value) == expected;
    }

    private static void forceOfflineProperties() {
        System.setProperty("ELEVENLABS_OFFLINE", "true");
        for (String name : List.of("ELEVENLABS_LIVE_ENABLED", "XAI_TTS_LIVE_ENABLED",
                "OPENAI_TTS_LIVE_ENABLED", "GOOGLE_TTS_LIVE_ENABLED",
                "AZURE_TTS_LIVE_ENABLED", "CARTESIA_TTS_LIVE_ENABLED")) {
            System.setProperty(name, "false");
        }
    }

    private static void copyTree(Path source, Path target) throws Exception {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
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
            if (!exception.getMessage().contains(code)) throw exception;
            check(true, name);
        }
    }

    @FunctionalInterface
    private interface Throwing {
        void run() throws Exception;
    }

    private static final class MapBuilder {
        private MapBuilder() {
        }

        static java.util.Map<String, String> of(String... values) {
            java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
            for (int i = 0; i < values.length; i += 2) result.put(values[i], values[i + 1]);
            return result;
        }
    }
}
