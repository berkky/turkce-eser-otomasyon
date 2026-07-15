import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class Adim34Dogrulama {
    private static int passed;

    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        Path temporary = Files.createTempDirectory("adim34-");
        try {
            xaiHttpCases(temporary);
            liveGates(temporary);
            idempotencyAndSecrets(temporary);
            blindPackageAndWeb(temporary);
            mockAudioDeterminism(temporary);
            canonicalPathPolicy(temporary);
            productionSourceRegression(temporary);
            scriptAndModels();
            System.out.println("ADIM 34 DOGRULAMA: BASARILI (" + passed + " kontrol)");
        } finally {
            deleteTree(temporary);
        }
    }

    private static void xaiHttpCases(Path root) throws Exception {
        try (FakeTtsServer server = new FakeTtsServer()) {
            server.enqueue(200, "audio/mpeg", mp3());
            var mp3 = provider(server.uri(), context(), "mp3").uretDetayli(request("mp3", "Kısa deneme metni."), root);
            check("mp3".equals(mp3.format()) && Files.size(mp3.file()) > 0, "xAI başarılı MP3");

            server.enqueue(200, "audio/wav", XaiTtsSaglayici.mockWav());
            var wav = provider(server.uri(), context(), "wav").uretDetayli(request("wav", "Başka kısa deneme."), root);
            check("wav".equals(wav.format()) && Files.size(wav.file()) > 44, "xAI başarılı WAV");

            assertError(providerFor(server, 401, "application/json", "yetkisiz"), root, "UNAUTHORIZED");
            assertError(providerFor(server, 404, "application/json", "voice"), root, "UNKNOWN_VOICE");

            server.enqueue(429, "application/json", "limit".getBytes(StandardCharsets.UTF_8));
            server.enqueue(200, "audio/mpeg", mp3());
            var retried = provider(server.uri(), context(), "mp3").uretDetayli(
                    request("retry429", "Retry için farklı kısa metin."), root);
            check(retried.retryCount() == 1, "429 exponential retry");

            server.enqueue(500, "application/json", "hata".getBytes(StandardCharsets.UTF_8));
            server.enqueue(503, "application/json", "bakım".getBytes(StandardCharsets.UTF_8));
            server.enqueue(200, "audio/mpeg", mp3());
            var serverRetry = provider(server.uri(), context(), "mp3").uretDetayli(
                    request("retry503", "Sunucu retry için farklı kısa metin."), root);
            check(serverRetry.retryCount() == 2, "500/503 retry");

            assertError(providerFor(server, 200, "text/plain", "not audio"), root, "INVALID_MIME_TYPE");
            assertError(providerFor(server, 200, "audio/mpeg", new byte[0]), root, "EMPTY_BODY");
            assertError(providerFor(server, 200, "audio/mpeg", "bad".getBytes(StandardCharsets.UTF_8)),
                    root, "UNPLAYABLE_AUDIO");
        }

        XaiTtsSaglayici timeout = new XaiTtsSaglayici(config(URI.create("http://127.0.0.1"), "mp3", true),
                context(), (a, b, c, d, e) -> { throw new HttpTimeoutException("test"); }, millis -> { });
        assertError(timeout, root, "TIMEOUT");
    }

    private static void liveGates(Path root) throws Exception {
        String longUnary = "a".repeat(15_001);
        expectCode(() -> provider(URI.create("http://127.0.0.1"), context(), "mp3")
                .uretDetayli(request("long", longUnary), root), "UNARY_TEXT_TOO_LONG");
        expectCode(() -> provider(URI.create("http://127.0.0.1"),
                new XaiTtsSaglayici.XaiRequestContext("ESER-00006", true, false, true,
                        java.math.BigDecimal.ONE), "mp3").uretDetayli(request("eser", "test"), root),
                "ESER_NOT_ALLOWED");
        expectCode(() -> provider(URI.create("http://127.0.0.1"),
                new XaiTtsSaglayici.XaiRequestContext("ESER-00005", false, false, true,
                        java.math.BigDecimal.ONE), "mp3").uretDetayli(request("confirm", "test"), root),
                "CLI_CONFIRMATION_REQUIRED");
        expectCode(() -> provider(URI.create("http://127.0.0.1"),
                sourceContext(true, TtsAbSourceType.FIXTURE, "Onaylı lisans", "hash", "hash"),
                "mp3").uretDetayli(request("fixture", "Fixture kaynak testi"), root),
                "FIXTURE_SOURCE_NOT_ALLOWED");
        expectCode(() -> provider(URI.create("http://127.0.0.1"),
                sourceContext(false, TtsAbSourceType.APPROVED_ARCHIVE_TEXT,
                        "Onaylı lisans", "hash", "hash"),
                "mp3").uretDetayli(request("notApproved", "Onaysız kaynak testi"), root),
                "SOURCE_NOT_APPROVED");
        expectCode(() -> provider(URI.create("http://127.0.0.1"),
                sourceContext(true, TtsAbSourceType.APPROVED_ARCHIVE_TEXT,
                        "Kontrol edilmedi", "hash", "hash"),
                "mp3").uretDetayli(request("license", "Lisans kaynak testi"), root),
                "SOURCE_LICENSE_NOT_APPROVED");
        expectCode(() -> provider(URI.create("http://127.0.0.1"),
                sourceContext(true, TtsAbSourceType.APPROVED_ARCHIVE_TEXT,
                        "Onaylı lisans", "approved", "different"),
                "mp3").uretDetayli(request("hashMismatch", "Hash kaynak testi"), root),
                "SOURCE_HASH_MISMATCH");
        expectCode(() -> provider(URI.create("http://127.0.0.1"), context(), "mp3")
                .uretDetayli(request("experimentLimit", "a".repeat(1_500)), root), "EXPERIMENT_TEXT_TOO_LONG");
        expectCode(() -> provider(URI.create("http://127.0.0.1"),
                new XaiTtsSaglayici.XaiRequestContext("ESER-00005", true, false, true,
                        new java.math.BigDecimal("0.000001")), "mp3")
                .uretDetayli(request("budget", "Bütçe sınırı metni"), root), "BUDGET_EXCEEDED");

        var disabled = config(URI.create("http://127.0.0.1"), "wav", false);
        check(!disabled.liveEnabled(), "Live gate varsayılan kapalı modeli");
    }

    private static void idempotencyAndSecrets(Path root) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        XaiTtsSaglayici.XaiHttpTransport transport = (endpoint, key, body, accept, timeout) -> {
            calls.incrementAndGet();
            return new XaiTtsSaglayici.XaiHttpResponse(200, "audio/mpeg", mp3(), Map.of());
        };
        XaiTtsSaglayici provider = new XaiTtsSaglayici(
                config(URI.create("http://127.0.0.1"), "mp3", true), context(), transport, millis -> { });
        TtsUretimIstegi request = request("samehash", "Aynı request hash tekrar çağrılmayacak.");
        provider.uretDetayli(request, root);
        var reused = provider.uretDetayli(request, root);
        check(calls.get() == 1 && reused.reused(), "Request hash idempotency");

        XaiTtsSaglayici leaking = new XaiTtsSaglayici(
                config(URI.create("http://127.0.0.1"), "mp3", true), context(),
                (a, b, c, d, e) -> new XaiTtsSaglayici.XaiHttpResponse(401, "application/json",
                        "Bearer dummy-test-api-key-not-real".getBytes(StandardCharsets.UTF_8), Map.of()),
                millis -> { });
        try {
            leaking.uretDetayli(request("secret", "Secret log testi"), root);
            throw new AssertionError("401 bekleniyordu");
        } catch (XaiTtsSaglayici.XaiTtsException e) {
            check(!e.getMessage().contains("dummy-test-api-key-not-real")
                    && e.getMessage().contains("[REDACTED]"), "API anahtarı loglanmaz");
        }
    }

    private static void blindPackageAndWeb(Path temporary) throws Exception {
        Path project = Path.of("").toAbsolutePath();
        Path ses = temporary.resolve("ses-arsivi");
        Path metin = temporary.resolve("metin-arsivi");
        Files.createDirectories(ses);
        Files.createDirectories(metin);
        WebOrtam env = new WebOrtam(project, temporary.resolve("gelen"), temporary.resolve("arsiv"),
                metin, ses, temporary.resolve("kuyruk"), temporary.resolve("katalog.xlsx"),
                temporary.resolve("kalite"));
        TtsAbService service = new TtsAbService(env);
        TtsAbSourceText source = service.fixtureSource(true);
        TtsAbExperiment experiment = new TtsAbExperiment("AB-20260715-TEST01", TtsAbService.ESER_ID,
                340034L, TtsAbExperimentMode.RAW_BASELINE, java.math.BigDecimal.ONE, true,
                OffsetDateTime.now(), source, List.of());
        List<TtsAbGenerationResult> results = new ArrayList<>();
        byte[] firstAudio = mp3(1);
        byte[] secondAudio = mp3(2);
        Path first = createExperimentSkeleton(
                service.root().resolve(experiment.experimentId()), "one.mp3", firstAudio);
        Path second = first.getParent().resolve("two.mp3");
        Files.write(second, secondAudio);
        String firstNormalizedHash = XaiTtsSaglayici.sha256(first);
        String secondNormalizedHash = XaiTtsSaglayici.sha256(second);
        results.add(result(experiment, "xai", "sal", "normalized/one.mp3",
                "raw-hash-one", firstNormalizedHash, "request-hash-one"));
        results.add(result(experiment, "piper", "tr", "normalized/two.mp3",
                "raw-hash-two", secondNormalizedHash, "request-hash-two"));
        service.writeExperimentFiles(first.getParent().getParent(), experiment, results);
        String mappingOne = Files.readString(first.getParent().getParent()
                .resolve("provider-mapping.private.json"), StandardCharsets.UTF_8);

        Path secondRoot = service.root().resolve("AB-20260715-TEST02");
        createExperimentSkeleton(secondRoot, "one.mp3", firstAudio);
        Files.write(secondRoot.resolve("normalized/two.mp3"), secondAudio);
        TtsAbExperiment experiment2 = new TtsAbExperiment("AB-20260715-TEST02", TtsAbService.ESER_ID,
                340034L, TtsAbExperimentMode.RAW_BASELINE, java.math.BigDecimal.ONE, true,
                experiment.createdAt(), source, List.of());
        service.writeExperimentFiles(secondRoot, experiment2, List.of(
                result(experiment2, "xai", "sal", "normalized/one.mp3",
                        "raw-hash-one", firstNormalizedHash, "request-hash-one"),
                result(experiment2, "piper", "tr", "normalized/two.mp3",
                        "raw-hash-two", secondNormalizedHash, "request-hash-two")));
        String mappingTwo = Files.readString(secondRoot.resolve("provider-mapping.private.json"),
                StandardCharsets.UTF_8).replace("TEST02", "TEST01");
        check(mappingOne.equals(mappingTwo), "Blind mapping deterministik seed");

        byte[] csv = Files.readAllBytes(first.getParent().getParent().resolve("evaluation/sonuclar.csv"));
        check(csv.length > 3 && (csv[0] & 0xff) == 0xef && (csv[1] & 0xff) == 0xbb,
                "CSV UTF-8 BOM");
        Path publicPackage = service.createPublicPackage(first.getParent().getParent());
        check(!Files.exists(publicPackage.resolve("provider-mapping.private.json"))
                && !Files.exists(publicPackage.resolve("manifest.json")), "Private mapping public pakette yok");

        TtsAbWebService web = new TtsAbWebService(env);
        String html = new String(web.route("GET", "/ab-test/" + experiment.experimentId(), "").body(),
                StandardCharsets.UTF_8).toLowerCase();
        check(!html.contains("xai") && !html.contains("piper") && !html.contains("provider"),
                "Web ekranında sağlayıcı sızıntısı yok");
        String submission = "submissionId=12345678-1234-1234-1234-123456789012&blindCode=A"
                + "&naturalness=8&turkishPronunciation=9&emotion=7&continueListening=8"
                + "&commercialQuality=8&fatigue=3&overallPreference=A&mispronouncedWords="
                + "&listen20Minutes=true&listenFullBook=true&comment=G%C3%BCzel";
        WebResponse firstSubmit = web.route("POST",
                "/ab-test/" + experiment.experimentId() + "/sonuclar", submission);
        WebResponse duplicate = web.route("POST",
                "/ab-test/" + experiment.experimentId() + "/sonuclar", submission);
        check(firstSubmit.status() == 200
                        && new String(duplicate.body(), StandardCharsets.UTF_8).contains("daha önce"),
                "Submission ID çift gönderim koruması");
    }

    private static void mockAudioDeterminism(Path temporary) throws Exception {
        Path root = temporary.resolve("mock-determinism");
        WebOrtam env = new WebOrtam(Path.of("").toAbsolutePath(), root.resolve("gelen"),
                root.resolve("arsiv"), root.resolve("metin"), root.resolve("ses"),
                root.resolve("kuyruk"), root.resolve("katalog.xlsx"), root.resolve("kalite"));
        TtsAbService service = new TtsAbService(env);
        TtsAbSourceText source = service.fixtureSource(true);
        Path first = service.createMockExperiment(source, 340034L);
        Path second = service.createMockExperiment(source, 340034L);

        String firstRawA = XaiTtsSaglayici.sha256(first.resolve("raw/candidate-1.wav"));
        String firstRawB = XaiTtsSaglayici.sha256(first.resolve("raw/candidate-2.wav"));
        String firstNormalizedA = XaiTtsSaglayici.sha256(first.resolve("normalized/candidate-1.mp3"));
        String firstNormalizedB = XaiTtsSaglayici.sha256(first.resolve("normalized/candidate-2.mp3"));
        String firstBlindA = XaiTtsSaglayici.sha256(first.resolve("blind/ornek-A.mp3"));
        String firstBlindB = XaiTtsSaglayici.sha256(first.resolve("blind/ornek-B.mp3"));
        check(!firstRawA.equals(firstRawB), "Farklı mock adayların raw hashleri farklı");
        check(!firstNormalizedA.equals(firstNormalizedB), "Farklı mock adayların normalized hashleri farklı");
        check(!firstBlindA.equals(firstBlindB), "Farklı mock adayların blind hashleri farklı");
        check(firstRawA.equals(XaiTtsSaglayici.sha256(second.resolve("raw/candidate-1.wav")))
                        && firstNormalizedA.equals(
                        XaiTtsSaglayici.sha256(second.resolve("normalized/candidate-1.mp3"))),
                "Aynı aday/requestHash deterministik ses üretir");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode mapping = mapper.readTree(first.resolve("provider-mapping.private.json").toFile());
        for (JsonNode item : mapping.path("mappings")) {
            Path normalized = first.resolve(item.path("normalizedFile").asText()).normalize();
            Path blind = first.resolve("blind/ornek-" + item.path("blindCode").asText() + ".mp3");
            check(XaiTtsSaglayici.sha256(normalized).equals(item.path("normalizedSha256").asText())
                            && XaiTtsSaglayici.sha256(blind).equals(item.path("blindSha256").asText()),
                    "Blind mapping normalized aday hashini korur");
        }
        String readme = Files.readString(first.resolve("README.txt"), StandardCharsets.UTF_8);
        JsonNode sourceJson = mapper.readTree(first.resolve("source/test-metni.json").toFile());
        check(readme.contains("kalite kanıtı değildir")
                        && "FIXTURE".equals(sourceJson.path("sourceType").asText()),
                "Fixture kalite kanıtı değildir uyarısı");

        TtsAbExperiment duplicateExperiment = new TtsAbExperiment(
                "AB-20260715-DUP001", TtsAbService.ESER_ID, 1L,
                TtsAbExperimentMode.RAW_BASELINE, java.math.BigDecimal.ONE, true,
                OffsetDateTime.now(), source, List.of());
        try {
            TtsAbService.validateUniqueGenerationAudio(List.of(
                    result(duplicateExperiment, "xai", "sal", "normalized/a.mp3",
                            "same-raw", "same-normalized", "request-a"),
                    result(duplicateExperiment, "piper", "tr", "normalized/b.mp3",
                            "same-raw", "same-normalized", "request-b")));
            throw new AssertionError("Duplicate audio reddedilmeliydi");
        } catch (TtsAbService.TtsAbExperimentException e) {
            check("DUPLICATE_CANDIDATE_AUDIO".equals(e.code()),
                    "Duplicate candidate audio açık hata ile reddedilir");
        }
    }

    private static void canonicalPathPolicy(Path temporary) throws Exception {
        Path home = temporary.resolve("path-policy-home");
        Path desktop = home.resolve("Desktop");
        Path legacyQueue = desktop.resolve("eser-otomasyon-kuyruk");
        Path legacyText = desktop.resolve("metin-arsivi");
        Files.createDirectories(legacyQueue.resolve("production-approvals"));
        Files.createDirectories(legacyText);
        Path legacySentinel = legacyText.resolve("legacy-sentinel.txt");
        Files.writeString(legacySentinel, "legacy-veri-korunacak", StandardCharsets.UTF_8);
        String sentinelHash = XaiTtsSaglayici.sha256(legacySentinel);

        EserVeriYollari onlyLegacy = EserVeriYollari.cozumle(home, Map.of());
        check(onlyLegacy.metin().equals(desktop.resolve("ESER/metin-arsivi"))
                        && onlyLegacy.legacyDetected()
                        && !Files.exists(onlyLegacy.canonicalRoot()),
                "Legacy mevcutken canonical yoksa fallback yapılmaz");
        check(sentinelHash.equals(XaiTtsSaglayici.sha256(legacySentinel)),
                "Legacy veri resolver tarafından değiştirilmez");

        Files.createDirectories(onlyLegacy.metin());
        Files.createDirectories(onlyLegacy.ses());
        Files.createDirectories(onlyLegacy.kuyruk());
        EserVeriYollari both = EserVeriYollari.cozumle(home, Map.of());
        check(both.metin().startsWith(both.canonicalRoot())
                        && !both.metin().startsWith(both.legacyRoot().resolve("metin-arsivi")),
                "Canonical ve legacy birlikteyken canonical seçilir");

        Path override = temporary.resolve("valid-override");
        Files.createDirectories(override);
        EserVeriYollari overridden = EserVeriYollari.cozumle(home,
                Map.of("ESER_METIN_ARSIVI", override.toAbsolutePath().toString()));
        check(overridden.metin().equals(override.toAbsolutePath().normalize()),
                "Geçerli environment override seçilir");

        Path eser = both.metin().resolve("ESER-00005 - Canonical Test");
        Path chunks = eser.resolve("tts-parcalari");
        Files.createDirectories(chunks);
        Files.writeString(chunks.resolve("001-001.txt"),
                "Canonical kaynak metni. ".repeat(50), StandardCharsets.UTF_8);
        Path legacyPlan = legacyQueue.resolve("production-approvals")
                .resolve("ESER-00005-production-plan.json");
        Files.writeString(legacyPlan,
                "{\"eserId\":5,\"toplamKarakter\":0,\"ttsParcaSayisi\":0}",
                StandardCharsets.UTF_8);
        String legacyPlanHash = XaiTtsSaglayici.sha256(legacyPlan);
        Path canonicalPlan = both.productionApprovals().resolve("ESER-00005-production-plan.json");
        Files.createDirectories(canonicalPlan.getParent());
        Files.writeString(canonicalPlan,
                "{\"eserId\":5,\"toplamKarakter\":0,\"ttsParcaSayisi\":0}",
                StandardCharsets.UTF_8);

        String previousMock = System.getProperty("ELEVENLABS_MOCK");
        System.setProperty("ELEVENLABS_MOCK", "true");
        try {
            WebOrtam environment = new WebOrtam(Path.of("").toAbsolutePath(), both.gelen(), both.arsiv(),
                    both.metin(), both.ses(), both.kuyruk(), both.katalog(), both.kalitePanel());
            TamEserUretimPlani refreshed = new TamEserUretimPlanService(environment).planGetir(5);
            check(refreshed.toplamKarakter() > 0 && refreshed.ttsParcaSayisi() > 0
                            && Files.isRegularFile(canonicalPlan),
                    "Sıfır canonical cache gerçek kaynaktan yenilenir");
            check(XaiTtsSaglayici.sha256(legacyPlan).equals(legacyPlanHash),
                    "Legacy sıfır plan cache olarak kullanılmaz veya değiştirilmez");
            check(canonicalPlan.startsWith(both.kuyruk())
                            && !canonicalPlan.startsWith(legacyQueue),
                    "Plan yalnız canonical production-approvals yoluna yazılır");
        } finally {
            if (previousMock == null) System.clearProperty("ELEVENLABS_MOCK");
            else System.setProperty("ELEVENLABS_MOCK", previousMock);
        }
    }

    private static void productionSourceRegression(Path temporary) throws Exception {
        String previousMock = System.getProperty("ELEVENLABS_MOCK");
        System.setProperty("ELEVENLABS_MOCK", "true");
        try {
            WebOrtam actual = WebOrtam.varsayilan();
            EserVeriYollari actualPaths = EserVeriYollari.varsayilan();
            Path legacyPlan5 = actualPaths.legacyRoot().resolve(
                    "eser-otomasyon-kuyruk/production-approvals/ESER-00005-production-plan.json");
            String legacyPlan5Hash = Files.isRegularFile(legacyPlan5)
                    ? XaiTtsSaglayici.sha256(legacyPlan5) : "";
            var legacyPlan5Modified = Files.isRegularFile(legacyPlan5)
                    ? Files.getLastModifiedTime(legacyPlan5) : null;
            Path eser5 = actual.metinArsivi().resolve("ESER-00005 - Kasagi - Vikikaynak");
            check(Files.isDirectory(eser5) && Files.isRegularFile(eser5.resolve("tam-metin.txt")),
                    "ESER-00005 gerçek kaynak resolver");
            TamEserUretimPlani plan5 = new TamEserUretimPlanService(actual).planGetir(5);
            TamEserUretimPlani plan6 = new TamEserUretimPlanService(actual).planGetir(6);
            long diskParca6;
            Path eser6;
            try (var folders = Files.list(actual.metinArsivi())) {
                eser6 = folders.filter(Files::isDirectory)
                        .filter(path -> path.getFileName().toString().startsWith("ESER-00006"))
                        .findFirst().orElseThrow();
            }
            try (var files = Files.list(eser6.resolve("tts-parcalari"))) {
                diskParca6 = files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().matches("\\d{3}-\\d{3}\\.txt"))
                        .count();
            }
            check(plan5.toplamKarakter() > 7_000 && plan5.ttsParcaSayisi() > 0,
                    "ESER-00005 gerçek karakter/parça eşikleri");
            TamEserUretimParcasi firstPart = plan5.parcalar().getFirst();
            Path firstPartFile = eser5.resolve("tts-parcalari").resolve(firstPart.safeName());
            check(Files.isRegularFile(firstPartFile)
                            && Files.readString(firstPartFile, StandardCharsets.UTF_8).length()
                            == firstPart.karakterSayisi(),
                    "Çözümlenen TTS parça dosyası UTF-8 karakter sayısı");
            check(plan6.toplamKarakter() > 700_000 && plan6.ttsParcaSayisi() == diskParca6,
                    "ESER-00006 gerçek karakter ve disk parça sayısı");
            Path canonicalPlan5 = actualPaths.productionApprovals()
                    .resolve("ESER-00005-production-plan.json");
            check(Files.isRegularFile(canonicalPlan5)
                            && canonicalPlan5.startsWith(actualPaths.canonicalRoot())
                            && actualPaths.legacyDetected(),
                    "Gerçek plan canonical kuyrukta; çift kök açıkça raporlanır");
            check(legacyPlan5Hash.isEmpty()
                            || (legacyPlan5Hash.equals(XaiTtsSaglayici.sha256(legacyPlan5))
                            && legacyPlan5Modified.equals(Files.getLastModifiedTime(legacyPlan5))),
                    "Gerçek legacy plan doğrulama sırasında korunur");
            String publicJson = new TamEserUretimPlanService(actual).json(5);
            check(!publicJson.contains("C:\\Users\\") && !publicJson.contains("C:/Users/"),
                    "Üretim planı public JSON tam yol sızdırmaz");
            String statusJson = new WebSistemDurumService(actual).durumJson();
            check(statusJson.contains(EserVeriYollari.LEGACY_WARNING)
                            && !statusJson.contains("C:\\Users\\")
                            && !statusJson.contains("C:/Users/"),
                    "Çift kök public status içinde pathsiz raporlanır");

            Path missing = temporary.resolve("missing-source");
            Files.createDirectories(missing.resolve("metin"));
            Files.createDirectories(missing.resolve("ses"));
            Files.createDirectories(missing.resolve("kuyruk"));
            WebOrtam empty = new WebOrtam(Path.of("").toAbsolutePath(), missing.resolve("gelen"),
                    missing.resolve("arsiv"), missing.resolve("metin"), missing.resolve("ses"),
                    missing.resolve("kuyruk"), missing.resolve("katalog.xlsx"), missing.resolve("kalite"));
            TamEserUretimPlani blocked = new TamEserUretimPlanService(empty).planUret(5);
            check(blocked.riskSeviyesi() == TamEserUretimRisk.ENGELLI
                            && blocked.toplamKarakter() == 0 && blocked.tahminiDakika() == 0
                            && blocked.onerilenAksiyon().startsWith("SOURCE_"),
                    "Kaynak yoksa sıfır karakterli plan güvenli engellenir");
        } finally {
            if (previousMock == null) System.clearProperty("ELEVENLABS_MOCK");
            else System.setProperty("ELEVENLABS_MOCK", previousMock);
        }
    }

    private static void scriptAndModels() throws Exception {
        String script = Files.readString(Path.of("tts-ab-normalize.ps1"), StandardCharsets.UTF_8);
        check(script.contains("-map_metadata") && script.contains("\"-1\"")
                && script.contains("-id3v2_version") && script.contains("loudnorm"),
                "ID3 temizliği ve loudness normalizasyonu");
        check(XaiTtsSaglayici.CONFIGURED_VOICE_CANDIDATES.equals(java.util.Set.of("lumen", "ursa", "sal")),
                "İstenen xAI voice adayları");
        TtsAbSourceText fixture = new TtsAbService(WebOrtam.varsayilan()).fixtureSource(true);
        check(fixture.sourceType() == TtsAbSourceType.FIXTURE
                        && !fixture.text().contains("bakışınıgörünce")
                        && !fixture.text().contains("koşarakgeldi"),
                "Fixture türü ve birleşik kelime düzeltmeleri");
        for (String file : List.of("App.java", "KaynakAlimOrkestratoruApp.java",
                "UretimOrkestratoruApp.java", "SesKalitePanelApp.java",
                "ElevenLabsOnizlemeApp.java", "ElevenLabsAlignmentApp.java",
                "SesKalitePanelDemoApp.java", "PiperYerelTest.java")) {
            String source = Files.readString(Path.of("src/main/java").resolve(file), StandardCharsets.UTF_8);
            check(!source.matches("(?s).*Desktop.{0,120}(metin-arsivi|ses-arsivi|"
                            + "eser-otomasyon-kuyruk|\\\\arsiv).*"),
                    file + " legacy Desktop fallback içermez");
        }
        String powerShellResolver = Files.readString(Path.of("canonical-paths.ps1"), StandardCharsets.UTF_8);
        check(powerShellResolver.contains("Desktop") && powerShellResolver.contains("'ESER'")
                        && powerShellResolver.contains(EserVeriYollari.LEGACY_WARNING),
                "PowerShell canonical resolver mevcut");
        System.setProperty("ELEVENLABS_OFFLINE", "true");
        try {
            check(ElevenLabsFabrika.offlineModAktif()
                            && (ElevenLabsFabrika.mockModAktif()
                            || ElevenLabsFabrika.durumOzeti().mesaj().contains("OFFLINE")),
                    "Regression harici ElevenLabs API çağrısı yapmaz");
        } finally {
            System.clearProperty("ELEVENLABS_OFFLINE");
        }
        TtsAbCostService costs = new TtsAbCostService();
        check(new java.math.BigDecimal("0.01500000").equals(
                        costs.estimate("xai", "a".repeat(1_000)).estimatedUsd())
                        && new java.math.BigDecimal("1000").equals(
                        costs.estimate("cartesia", "a".repeat(1_000)).estimatedCredits()),
                "Sağlayıcı fiyat profilleri");
    }

    private static XaiTtsSaglayici providerFor(FakeTtsServer server, int status,
                                                String contentType, String body) {
        server.enqueue(status, contentType, body.getBytes(StandardCharsets.UTF_8));
        return provider(server.uri(), context(), "mp3");
    }

    private static XaiTtsSaglayici providerFor(FakeTtsServer server, int status,
                                                String contentType, byte[] body) {
        server.enqueue(status, contentType, body);
        return provider(server.uri(), context(), "mp3");
    }

    private static void assertError(XaiTtsSaglayici provider, Path root, String code) throws Exception {
        expectCode(() -> provider.uretDetayli(request("err-" + code, "Hata senaryosu " + code), root), code);
    }

    private static XaiTtsSaglayici provider(URI endpoint, XaiTtsSaglayici.XaiRequestContext context,
                                            String format) {
        return new XaiTtsSaglayici(config(endpoint, format, true), context,
                new XaiTtsSaglayici.JavaHttpTransport(), millis -> { });
    }

    private static XaiTtsSaglayici.XaiConfig config(URI endpoint, String format, boolean live) {
        return new XaiTtsSaglayici.XaiConfig("dummy-test-api-key-not-real", "sal", "tr",
                live, format, endpoint, Duration.ofSeconds(2));
    }

    private static XaiTtsSaglayici.XaiRequestContext context() {
        return new XaiTtsSaglayici.XaiRequestContext("ESER-00005", true, false, true,
                java.math.BigDecimal.ONE);
    }

    private static XaiTtsSaglayici.XaiRequestContext sourceContext(
            boolean approved, TtsAbSourceType sourceType, String license,
            String approvedHash, String currentHash) {
        return new XaiTtsSaglayici.XaiRequestContext(
                "ESER-00005", true, false, approved, java.math.BigDecimal.ONE,
                sourceType.name(), license, approvedHash, currentHash);
    }

    private static TtsUretimIstegi request(String id, String text) {
        return new TtsUretimIstegi(id, "test", text, "");
    }

    private static TtsAbGenerationResult result(TtsAbExperiment experiment, String provider,
                                                String voice, String normalized, String rawHash,
                                                String normalizedHash, String requestHash) {
        return new TtsAbGenerationResult(experiment.experimentId(), experiment.eserId(),
                experiment.sourceText().sourceTextHash(), experiment.sourceText().sourceCharacterCount(),
                experiment.sourceText().sourceWordCount(), experiment.sourceText().sourceLicenseNote(),
                provider, "model", voice, "tr", requestHash, "MOCK", "", List.of(),
                OffsetDateTime.now(), OffsetDateTime.now(), 2_000, 4, rawHash,
                java.math.BigDecimal.ZERO, null, "mp3", 44_100, 192_000L,
                -16.0, -1.2, normalized, normalizedHash,
                "VALID", "", 0, Path.of("raw/test.wav"));
    }

    private static Path createExperimentSkeleton(Path root, String name, byte[] data) throws Exception {
        Files.createDirectories(root.resolve("normalized"));
        Files.createDirectories(root.resolve("blind"));
        Files.createDirectories(root.resolve("evaluation"));
        Path file = root.resolve("normalized").resolve(name);
        Files.write(file, data);
        return file;
    }

    private static byte[] mp3() {
        return mp3(0);
    }

    private static byte[] mp3(int variant) {
        byte[] bytes = new byte[512];
        bytes[0] = 'I'; bytes[1] = 'D'; bytes[2] = '3';
        bytes[20] = (byte) variant;
        return bytes;
    }

    private static void expectCode(ThrowingRunnable runnable, String expected) throws Exception {
        try {
            runnable.run();
            throw new AssertionError("Beklenen hata oluşmadı: " + expected);
        } catch (XaiTtsSaglayici.XaiTtsException e) {
            check(expected.equals(e.code()), expected + " hata kodu");
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError("BAŞARISIZ: " + label);
        passed++;
        System.out.println("OK: " + label);
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }

    private static final class FakeTtsServer implements AutoCloseable {
        private final HttpServer server;
        private final Queue<Response> responses = new ArrayDeque<>();

        FakeTtsServer() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/tts", exchange -> {
                exchange.getRequestBody().readAllBytes();
                Response response = responses.remove();
                exchange.getResponseHeaders().set("Content-Type", response.contentType());
                exchange.sendResponseHeaders(response.status(), response.body().length);
                exchange.getResponseBody().write(response.body());
                exchange.close();
            });
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
        }

        void enqueue(int status, String contentType, byte[] body) {
            responses.add(new Response(status, contentType, body));
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/tts");
        }

        @Override public void close() { server.stop(0); }
        private record Response(int status, String contentType, byte[] body) { }
    }
}
