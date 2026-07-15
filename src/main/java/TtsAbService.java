import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class TtsAbService {
    public static final String ESER_ID = "ESER-00005";
    public static final BigDecimal DEFAULT_BUDGET_USD = BigDecimal.ONE;
    private static final DateTimeFormatter ID_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern DIALOGUE = Pattern.compile("(?m)(^|\\s)[—–-]\\s*|[\"“”]");
    private static final Pattern EMOTION = Pattern.compile(
            "(?iu)\\b(kork|üzül|ağla|sevin|öfke|kız|heyecan|pişman|acı|mutlu|yalnız)\\p{L}*\\b");
    private final WebOrtam environment;
    private final ObjectMapper json = createMapper();

    public TtsAbService(WebOrtam environment) {
        this.environment = environment;
    }

    public Path root() {
        return environment.sesArsivi().resolve("ab-test");
    }

    public TtsAbSourceText selectSourceCandidate() throws Exception {
        Path folder = findEserFolder(environment.metinArsivi());
        Path fullText = folder.resolve("tam-metin.txt");
        String text;
        if (Files.isRegularFile(fullText)) {
            text = Files.readString(fullText, StandardCharsets.UTF_8);
        } else {
            Path chunks = folder.resolve("tts-parcalari");
            try (Stream<Path> files = Files.list(chunks)) {
                StringBuilder merged = new StringBuilder();
                for (Path path : files.filter(Files::isRegularFile).sorted().toList()) {
                    merged.append(Files.readString(path, StandardCharsets.UTF_8)).append("\n");
                    if (merged.length() > 20_000) break;
                }
                text = merged.toString();
            }
        }
        String normalized = text.replace("\r", "").replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n").trim();
        String candidate = bestCandidate(normalized);
        String license = readLicenseNote(folder);
        return new TtsAbSourceText(ESER_ID, TtsAbSourceType.APPROVED_ARCHIVE_TEXT,
                candidate, sha256(candidate), candidate.length(), wordCount(candidate), license, false);
    }

    public TtsAbSourceText fixtureSource(boolean approved) throws Exception {
        String text = """
                Çiftliğin arkasındaki küçük ahır, sabah güneşi yükselirken sessiz görünüyordu. Hasan, elinde eski tarağıyla Kaşağı'nın yanına geldi; hayvanın tedirgin bakışını görünce bir an durdu. İstanbul'dan gelen misafirlerin sözleri hâlâ aklındaydı ve iyi bir seyis gibi davranmak istiyordu.

                — Korkma, dedi usulca, sana zarar vermeyeceğim.

                Fakat kapının gıcırtısıyla at ürperdi. Hasan'ın heyecanı önce öfkeye, sonra derin bir pişmanlığa dönüştü. Kırılan tarağın sesi avluda duyulunca kardeşi koşarak geldi. İki çocuk bir süre konuşmadan birbirine baktı. Hasan gerçeği söylemek istiyor, ama babasının sert bakışını düşündükçe cesaretini kaybediyordu. Gözleri doldu; avucundaki kırık parçayı sakladı. O anda yaptığı küçük yanlışın büyüyebileceğini sezdi, yine de susmayı seçti. Akşam rüzgârı dut yapraklarını hışırdatırken ahırdaki sessizlik ona ağır bir suçlama gibi geldi. Gece yaklaşınca Kaşağı'nın başını okşadı ve ertesi sabah her şeyi anlatmaya karar verdi. Bu karar içini biraz rahatlatsa da uykuya dalarken kardeşinin şaşkın yüzünü unutamadı.
                """.strip();
        return new TtsAbSourceText(ESER_ID, TtsAbSourceType.FIXTURE,
                text, sha256(text), text.length(), wordCount(text),
                "Test fixture; ticari lisans kanıtı değildir.", approved);
    }

    public List<TtsAbCandidate> registry() {
        List<TtsAbCandidate> candidates = new ArrayList<>();
        candidates.add(candidate("piper", "mevcut-turkce", env("PIPER_MODEL", ""), "tr", false));
        String openAiSelected = env("OPENAI_TTS_VOICE", "marin");
        for (String voice : List.of("marin", "cedar")) {
            candidates.add(new TtsAbCandidate("openai", "gpt-4o-mini-tts", voice, "tr", "", List.of(),
                    has("OPENAI_API_KEY") && voice.equalsIgnoreCase(openAiSelected)
                            ? "CONFIGURED" : "CANDIDATE"));
        }
        candidates.add(candidate("google", "chirp-3-hd", env("GOOGLE_TTS_VOICE", ""), "tr-TR", has("GOOGLE_APPLICATION_CREDENTIALS")));
        candidates.add(candidate("elevenlabs", env("ELEVENLABS_MODEL_ID", "eleven_multilingual_v2"),
                env("ELEVENLABS_VOICE_ID", ""), "tr", has("ELEVENLABS_API_KEY")));
        String azureSelected = env("AZURE_TTS_VOICE", "tr-TR-Aydin:MAI-Voice-2");
        for (String voice : List.of("tr-TR-Aydin:MAI-Voice-2", "tr-TR-Elif:MAI-Voice-2")) {
            candidates.add(new TtsAbCandidate("azure", "MAI-Voice-2", voice, "tr-TR", "", List.of(),
                    has("AZURE_SPEECH_KEY") && voice.equalsIgnoreCase(azureSelected)
                            ? "CONFIGURED" : "CANDIDATE"));
        }
        candidates.add(candidate("cartesia", "sonic-3.5", env("CARTESIA_TTS_VOICE", ""), "tr", has("CARTESIA_API_KEY")));
        for (String voice : List.of("lumen", "ursa", "sal")) {
            boolean selected = voice.equalsIgnoreCase(env("XAI_TTS_VOICE", ""));
            candidates.add(new TtsAbCandidate("xai", "xai-tts", voice, "tr", "", List.of(),
                    selected ? "CONFIGURED" : "CANDIDATE"));
        }
        return List.copyOf(candidates);
    }

    public TtsAbExperiment newExperiment(TtsAbSourceText source, TtsAbExperimentMode mode,
                                         List<TtsAbCandidate> selected, long seed,
                                         BigDecimal budget, boolean dryRun) {
        if (!ESER_ID.equals(source.eserId())) throw new IllegalArgumentException("Yalnızca ESER-00005 kullanılabilir.");
        if (mode == TtsAbExperimentMode.EDITORIAL_ADAPTATION) {
            throw new IllegalArgumentException("EDITORIAL_ADAPTATION bu adımda çalıştırılamaz.");
        }
        String id = "AB-" + OffsetDateTime.now().format(ID_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        return new TtsAbExperiment(id, ESER_ID, seed, mode,
                budget == null ? DEFAULT_BUDGET_USD : budget, dryRun,
                OffsetDateTime.now(), source, selected);
    }

    public Path createMockExperiment(TtsAbSourceText source, long seed) throws Exception {
        List<TtsAbCandidate> selected = List.of(
                new TtsAbCandidate("xai", "xai-tts", "sal", "tr", "", List.of(), "MOCK"),
                new TtsAbCandidate("piper", "mevcut-turkce", "tr_TR-test", "tr", "", List.of(), "MOCK"));
        TtsAbExperiment experiment = newExperiment(source, TtsAbExperimentMode.RAW_BASELINE,
                selected, seed, DEFAULT_BUDGET_USD, true);
        return runMock(experiment);
    }

    public Path runMock(TtsAbExperiment experiment) throws Exception {
        validateSource(experiment);
        Path experimentRoot = createFolders(experiment.experimentId());
        try {
            writeSource(experimentRoot, experiment.sourceText());
            TtsAbAudioService audio = new TtsAbAudioService(environment.projeKlasoru());
            if (!audio.available()) {
                throw new IllegalStateException("FFmpeg/ffprobe olmadan doğrulanmış mock A/B paketi üretilemez.");
            }
            List<TtsAbGenerationResult> results = new ArrayList<>();
            Map<String, String> rawHashes = new HashMap<>();
            Map<String, String> normalizedHashes = new HashMap<>();
            int index = 0;
            for (TtsAbCandidate candidate : experiment.candidates()) {
                index++;
                OffsetDateTime started = OffsetDateTime.now();
                String requestHash = sha256(experiment.sourceText().text() + "|" + candidate.providerCode()
                        + "|" + candidate.voiceId() + "|" + experiment.mode());
                Path raw = experimentRoot.resolve("raw").resolve("candidate-" + index + ".wav");
                String mockIdentity = candidate.providerCode() + "|" + candidate.modelName() + "|"
                        + candidate.voiceId() + "|" + requestHash;
                byte[] wav = deterministicMockWav(mockIdentity);
                TtsLaboratuvarYardimci.atomikYaz(raw, wav);
                TtsAbAudioMetrics rawMetrics = audio.probe(raw);
                requireUniqueHash(rawHashes, rawMetrics.sha256(), mockIdentity, "raw");
                Path normalized = experimentRoot.resolve("normalized").resolve("candidate-" + index + ".mp3");
                TtsAbAudioMetrics normalizedMetrics = audio.normalize(raw, normalized);
                requireUniqueHash(normalizedHashes, normalizedMetrics.sha256(), mockIdentity, "normalized");
                results.add(new TtsAbGenerationResult(
                        experiment.experimentId(), experiment.eserId(), experiment.sourceText().sourceTextHash(),
                        experiment.sourceText().sourceCharacterCount(), experiment.sourceText().sourceWordCount(),
                        experiment.sourceText().sourceLicenseNote(), candidate.providerCode(), candidate.modelName(),
                        candidate.voiceId(), candidate.language(), requestHash, "MOCK",
                        candidate.instructionOrStyle(), candidate.speechTagsUsed(), started, OffsetDateTime.now(),
                        rawMetrics.durationMs(), rawMetrics.fileSizeBytes(), rawMetrics.sha256(), BigDecimal.ZERO, null,
                        "wav", rawMetrics.sampleRate(), rawMetrics.bitrate(), normalizedMetrics.loudnessLufs(),
                        normalizedMetrics.truePeakDbtp(), "normalized/" + normalized.getFileName(),
                        normalizedMetrics.sha256(), "VALID", "", 0,
                        Path.of("raw").resolve(raw.getFileName())));
            }
            writeExperimentFiles(experimentRoot, experiment, results);
            return experimentRoot;
        } catch (Exception e) {
            deleteTree(experimentRoot);
            throw e;
        }
    }

    public Path runSelectedXai(TtsAbExperiment experiment, boolean cliConfirmed) throws Exception {
        validateSource(experiment);
        requireLiveSource(experiment.sourceText());
        if (experiment.dryRun()) throw new IllegalArgumentException("Canlı deney dry-run=false olmalıdır.");
        if (experiment.candidates().size() != 1
                || !"xai".equalsIgnoreCase(experiment.candidates().getFirst().providerCode())) {
            throw new IllegalArgumentException("Canlı xAI çalıştırması tam olarak bir seçilmiş voice içermelidir.");
        }
        TtsAbCandidate candidate = experiment.candidates().getFirst();
        if (!XaiTtsSaglayici.CONFIGURED_VOICE_CANDIDATES.contains(candidate.voiceId().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Voice izinli aday listesinde değil.");
        }
        Path experimentRoot = createFolders(experiment.experimentId());
        try {
            writeSource(experimentRoot, experiment.sourceText());
            String currentFileHash = XaiTtsSaglayici.sha256(
                    experimentRoot.resolve("source").resolve("test-metni.txt"));
            var base = XaiTtsSaglayici.XaiConfig.fromEnvironment();
            var config = new XaiTtsSaglayici.XaiConfig(base.apiKey(), candidate.voiceId(), candidate.language(),
                    base.liveEnabled(), base.outputFormat(), base.endpoint(), base.timeout());
            var context = new XaiTtsSaglayici.XaiRequestContext(
                    experiment.eserId(), cliConfirmed, experiment.dryRun(),
                    experiment.sourceText().userApproved(), experiment.budgetUsd(),
                    experiment.sourceText().sourceType().name(),
                    experiment.sourceText().sourceLicenseNote(),
                    experiment.sourceText().sourceTextHash(), currentFileHash);
            XaiTtsSaglayici provider = new XaiTtsSaglayici(config, context,
                    new XaiTtsSaglayici.JavaHttpTransport(), Thread::sleep);
            OffsetDateTime started = OffsetDateTime.now();
            String cacheId = experiment.sourceText().sourceTextHash().substring(0, 16) + "-"
                    + TtsLaboratuvarYardimci.guvenliDosyaAdi(candidate.voiceId());
            var detail = provider.uretDetayli(new TtsUretimIstegi(cacheId, "Kaşağı A/B",
                    experiment.sourceText().text(), candidate.instructionOrStyle()),
                    root().resolve("_request-cache"));
            Path rawCopy = experimentRoot.resolve("raw").resolve("candidate-1." + detail.format());
            Files.copy(detail.file(), rawCopy, StandardCopyOption.REPLACE_EXISTING);
            TtsAbAudioService audio = new TtsAbAudioService(environment.projeKlasoru());
            TtsAbAudioMetrics rawMetrics = audio.probe(rawCopy);
            Path normalized = experimentRoot.resolve("normalized").resolve("candidate-1.mp3");
            TtsAbAudioMetrics normalizedMetrics = audio.normalize(rawCopy, normalized);
            TtsAbGenerationResult result = new TtsAbGenerationResult(
                    experiment.experimentId(), experiment.eserId(), experiment.sourceText().sourceTextHash(),
                    experiment.sourceText().sourceCharacterCount(), experiment.sourceText().sourceWordCount(),
                    experiment.sourceText().sourceLicenseNote(), candidate.providerCode(), candidate.modelName(),
                    candidate.voiceId(), candidate.language(), detail.requestHash(), detail.liveOrMock(),
                    candidate.instructionOrStyle(), candidate.speechTagsUsed(), started, OffsetDateTime.now(),
                    rawMetrics.durationMs(), rawMetrics.fileSizeBytes(), rawMetrics.sha256(),
                    detail.estimatedCostUsd(), null, detail.format(), rawMetrics.sampleRate(), rawMetrics.bitrate(),
                    normalizedMetrics.loudnessLufs(), normalizedMetrics.truePeakDbtp(),
                    "normalized/" + normalized.getFileName(), normalizedMetrics.sha256(),
                    "VALID", "", detail.retryCount(),
                    experimentRoot.relativize(rawCopy));
            writeExperimentFiles(experimentRoot, experiment, List.of(result));
            return experimentRoot;
        } catch (Exception e) {
            deleteTree(experimentRoot);
            throw e;
        }
    }

    public void writeExperimentFiles(Path experimentRoot, TtsAbExperiment experiment,
                                     List<TtsAbGenerationResult> results) throws Exception {
        validateUniqueGenerationAudio(results);
        BigDecimal estimated = results.stream().map(TtsAbGenerationResult::estimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        TtsAbExperimentManifest manifest = new TtsAbExperimentManifest(
                experiment.experimentId(), experiment.eserId(), experiment.mode(), experiment.seed(),
                experiment.budgetUsd(), estimated, experiment.createdAt(),
                experiment.sourceText().sourceType(),
                experiment.sourceText().sourceTextHash(), experiment.sourceText().sourceCharacterCount(),
                experiment.sourceText().sourceWordCount(), experiment.sourceText().sourceLicenseNote(), results);
        writeJson(experimentRoot.resolve("manifest.json"), manifest);
        blind(experimentRoot, experiment.seed(), results);
        writeEvaluationForm(experimentRoot, experiment.experimentId(), results.size());
        writeCostReport(experimentRoot, results);
        Files.writeString(experimentRoot.resolve("README.txt"),
                "Türkçe TTS A/B kör test paketi\n"
                        + "Dinleme dosyaları blind klasöründedir. Sağlayıcı eşlemesi private dosyadadır.\n"
                        + "Bu paket mock üretim olabilir; manifestte liveOrMock alanını kontrol edin.\n"
                        + (experiment.sourceText().sourceType() == TtsAbSourceType.FIXTURE
                        ? "UYARI: FIXTURE kaynak ve sentetik mock sesler kalite kanıtı değildir; "
                        + "ticari değerlendirmede kullanılamaz.\n" : ""),
                StandardCharsets.UTF_8);
    }

    public Path createPublicPackage(Path experimentRoot) throws Exception {
        Path publicRoot = experimentRoot.resolveSibling(experimentRoot.getFileName() + "-public");
        deleteTree(publicRoot);
        Files.createDirectories(publicRoot);
        copyTree(experimentRoot.resolve("blind"), publicRoot.resolve("blind"));
        copyTree(experimentRoot.resolve("evaluation"), publicRoot.resolve("evaluation"));
        Files.copy(experimentRoot.resolve("README.txt"), publicRoot.resolve("README.txt"),
                StandardCopyOption.REPLACE_EXISTING);
        try (Stream<Path> files = Files.walk(publicRoot)) {
            if (files.anyMatch(path -> path.getFileName().toString().equals("provider-mapping.private.json"))) {
                throw new IllegalStateException("Private mapping public pakete sızdı.");
            }
        }
        return publicRoot;
    }

    private void blind(Path root, long seed, List<TtsAbGenerationResult> results) throws Exception {
        List<TtsAbGenerationResult> ordered = new ArrayList<>(results);
        ordered.sort(Comparator.comparing(item -> deterministicRank(seed, item.requestHash())));
        ArrayNode mappings = json.createArrayNode();
        Path blindFolder = root.resolve("blind");
        Files.createDirectories(blindFolder);
        for (int i = 0; i < ordered.size(); i++) {
            String code = blindCode(i);
            TtsAbGenerationResult result = ordered.get(i);
            Path source = root.resolve(result.normalizedFile()).normalize();
            Path target = blindFolder.resolve("ornek-" + code + ".mp3");
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            ObjectNode mapping = mappings.addObject();
            mapping.put("blindCode", code);
            mapping.put("providerCode", result.providerCode());
            mapping.put("modelName", result.modelName());
            mapping.put("voiceId", result.voiceId());
            mapping.put("requestHash", result.requestHash());
            mapping.put("normalizedFile", result.normalizedFile());
            mapping.put("normalizedSha256", result.normalizedSha256());
            String blindHash = XaiTtsSaglayici.sha256(target);
            if (!blindHash.equalsIgnoreCase(result.normalizedSha256())) {
                throw new TtsAbExperimentException("BLIND_HASH_MISMATCH",
                        "Kör kopya normalize aday hash'ini korumadı.");
            }
            mapping.put("blindSha256", blindHash);
        }
        ObjectNode privateRoot = json.createObjectNode();
        privateRoot.put("experimentSeed", seed);
        privateRoot.set("mappings", mappings);
        writeJson(root.resolve("provider-mapping.private.json"), privateRoot);
    }

    private void writeSource(Path root, TtsAbSourceText source) throws Exception {
        Path folder = root.resolve("source");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("test-metni.txt"), source.text(), StandardCharsets.UTF_8);
        writeJson(folder.resolve("test-metni.json"), source);
        Files.writeString(folder.resolve("test-metni.sha256"),
                source.sourceTextHash() + "  test-metni.txt\n", StandardCharsets.UTF_8);
    }

    private void writeEvaluationForm(Path root, String experimentId, int sampleCount) throws Exception {
        StringBuilder options = new StringBuilder();
        for (int i = 0; i < sampleCount; i++) {
            String code = blindCode(i);
            options.append("<option value=\"").append(code).append("\">Örnek ").append(code).append("</option>");
        }
        String html = "<!doctype html><html lang=\"tr\"><head><meta charset=\"UTF-8\"><title>A/B Değerlendirme</title>"
                + "<style>body{font-family:system-ui;max-width:850px;margin:2rem auto;padding:1rem}"
                + "label{display:block;margin:.8rem 0}input,select,textarea{width:100%;padding:.5rem}</style></head><body>"
                + "<h1>Türkçe TTS Kör Değerlendirme</h1><p>Deney: " + escape(experimentId) + "</p>"
                + "<p>Sağlayıcı kimlikleri bu formda ve ses dosyalarında bulunmaz.</p>"
                + "<select name=\"blindCode\">" + options + "</select>"
                + "<p>Canlı form için yerel panelde /ab-test/" + escape(experimentId) + " adresini açın.</p>"
                + "</body></html>";
        Path evaluation = root.resolve("evaluation");
        Files.createDirectories(evaluation);
        Files.writeString(evaluation.resolve("degerlendirme-formu.html"), html, StandardCharsets.UTF_8);
        Files.write(evaluation.resolve("sonuclar.csv"), csvBom(
                "experimentId;submissionId;blindCode;dogallik;turkceTelaffuz;duygu;devaminiDinleme;"
                        + "ticariKalite;yoruculuk;genelTercih;hataliTelaffuz;20Dakika;tamKitap;yorum;submittedAt\r\n"));
    }

    private void writeCostReport(Path root, List<TtsAbGenerationResult> results) throws Exception {
        StringBuilder csv = new StringBuilder("providerCode;modelName;voiceId;estimatedCostUsd;actualCostUsd;retryCount\r\n");
        for (TtsAbGenerationResult result : results) {
            csv.append(csv(result.providerCode())).append(';').append(csv(result.modelName())).append(';')
                    .append(csv(result.voiceId())).append(';').append(result.estimatedCostUsd()).append(';')
                    .append(result.actualCostUsd() == null ? "" : result.actualCostUsd()).append(';')
                    .append(result.retryCount()).append("\r\n");
        }
        Files.write(root.resolve("maliyet-raporu.csv"), csvBom(csv.toString()));
    }

    private Path createFolders(String id) throws Exception {
        Path result = root().resolve(safeId(id));
        if (Files.exists(result)) throw new IllegalStateException("Deney zaten var: " + id);
        for (String child : List.of("source", "raw", "normalized", "blind", "evaluation")) {
            Files.createDirectories(result.resolve(child));
        }
        return result;
    }

    private void validateSource(TtsAbExperiment experiment) {
        TtsAbSourceText source = experiment.sourceText();
        if (!ESER_ID.equals(experiment.eserId()) || !ESER_ID.equals(source.eserId())) {
            throw new IllegalArgumentException("A/B laboratuvarı yalnızca ESER-00005 içindir.");
        }
        if (source.sourceCharacterCount() < 900 || source.sourceCharacterCount() > 1_200) {
            throw new IllegalArgumentException("Test metni 900-1.200 karakter olmalıdır.");
        }
        if (!sha256Unchecked(source.text()).equals(source.sourceTextHash())) {
            throw new IllegalArgumentException("Kaynak metin SHA-256 doğrulaması başarısız.");
        }
        if (source.sourceType() == null) {
            throw new IllegalArgumentException("Kaynak türü zorunludur.");
        }
    }

    private static void requireLiveSource(TtsAbSourceText source) {
        if (source.sourceType() != TtsAbSourceType.APPROVED_ARCHIVE_TEXT) {
            throw new TtsAbExperimentException("FIXTURE_SOURCE_NOT_ALLOWED",
                    "Fixture kaynak canlı API çağrısında kullanılamaz.");
        }
        if (!source.userApproved()) {
            throw new TtsAbExperimentException("SOURCE_NOT_APPROVED",
                    "Arşiv metni kullanıcı tarafından onaylanmadı.");
        }
        String license = source.sourceLicenseNote() == null
                ? "" : source.sourceLicenseNote().trim().toLowerCase(Locale.ROOT);
        if (license.isBlank() || license.contains("kontrol edilmedi")
                || license.contains("placeholder") || license.contains("fixture")
                || license.contains("kanıtı değildir")) {
            throw new TtsAbExperimentException("SOURCE_LICENSE_NOT_APPROVED",
                    "Kaynak lisans notu canlı kullanım için onaylı değil.");
        }
    }

    private static String bestCandidate(String text) {
        String[] paragraphs = text.split("\\n\\s*\\n");
        for (int start = 0; start < paragraphs.length; start++) {
            StringBuilder candidate = new StringBuilder();
            for (int i = start; i < paragraphs.length && candidate.length() < 1_250; i++) {
                if (!candidate.isEmpty()) candidate.append("\n\n");
                candidate.append(paragraphs[i].trim());
                int length = candidate.length();
                if (length >= 900 && length <= 1_200
                        && DIALOGUE.matcher(candidate).find() && EMOTION.matcher(candidate).find()) {
                    return candidate.toString();
                }
            }
        }
        int end = Math.min(1_150, text.length());
        int sentence = Math.max(text.lastIndexOf('.', end), text.lastIndexOf('!', end));
        if (sentence >= 900) end = sentence + 1;
        String candidate = text.substring(0, end).trim();
        if (candidate.length() < 900) {
            throw new IllegalStateException("900 karakterlik Kaşağı test adayı çıkarılamadı.");
        }
        return candidate;
    }

    private static Path findEserFolder(Path root) throws Exception {
        if (!Files.isDirectory(root)) throw new IllegalStateException("Metin arşivi bulunamadı: " + root);
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().toUpperCase(Locale.ROOT).startsWith(ESER_ID))
                    .findFirst().orElseThrow(() -> new IllegalStateException("ESER-00005 metin klasörü bulunamadı."));
        }
    }

    private String readLicenseNote(Path folder) {
        for (String name : List.of("alim-manifest.json", "manifest.json")) {
            Path path = folder.resolve(name);
            try {
                if (Files.isRegularFile(path)) {
                    var node = json.readTree(path.toFile());
                    for (String key : List.of("sourceLicenseNote", "lisans", "license", "rights")) {
                        String value = node.path(key).asText("");
                        if (!value.isBlank()) return value;
                    }
                }
            } catch (Exception ignored) { }
        }
        return "Kontrol edilmedi";
    }

    static byte[] deterministicMockWav(String identity) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(identity.getBytes(StandardCharsets.UTF_8));
        int sampleRate = 44_100;
        int sampleCount = sampleRate * 2;
        byte[] pcm = new byte[sampleCount * 2];
        double frequency1 = 170.0 + (digest[0] & 0xff) * 1.7;
        double frequency2 = 310.0 + (digest[1] & 0xff) * 2.1;
        double modulation = 2.0 + (digest[2] & 0xff) / 40.0;
        double phase = (digest[3] & 0xff) / 255.0 * Math.PI;
        double amplitude = 4_000.0 + (digest[4] & 0xff) * 18.0;
        for (int i = 0; i < sampleCount; i++) {
            double t = i / (double) sampleRate;
            double envelope = 0.55 + 0.45 * Math.pow(Math.sin(Math.PI * i / sampleCount), 2);
            double signal = Math.sin(2 * Math.PI * frequency1 * t + phase)
                    + 0.42 * Math.sin(2 * Math.PI * frequency2 * t)
                    + 0.18 * Math.sin(2 * Math.PI * (frequency1 + modulation * t) * t);
            short sample = (short) Math.max(Short.MIN_VALUE,
                    Math.min(Short.MAX_VALUE, Math.round(signal * amplitude * envelope / 1.6)));
            pcm[i * 2] = (byte) (sample & 0xff);
            pcm[i * 2 + 1] = (byte) ((sample >>> 8) & 0xff);
        }
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (ByteArrayInputStream input = new ByteArrayInputStream(pcm);
             AudioInputStream audio = new AudioInputStream(input, format, sampleCount);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, output);
            return output.toByteArray();
        }
    }

    private static void requireUniqueHash(Map<String, String> hashes, String hash,
                                          String identity, String stage) {
        String previous = hashes.putIfAbsent(hash, identity);
        if (previous != null && !previous.equals(identity)) {
            throw new TtsAbExperimentException("DUPLICATE_CANDIDATE_AUDIO",
                    "Farklı mock adayları aynı " + stage + " ses hash'ini üretti.");
        }
    }

    static void validateUniqueGenerationAudio(List<TtsAbGenerationResult> results) {
        Map<String, String> raw = new HashMap<>();
        Map<String, String> normalized = new HashMap<>();
        for (TtsAbGenerationResult result : results) {
            String identity = result.providerCode() + "|" + result.modelName() + "|"
                    + result.voiceId() + "|" + result.requestHash();
            requireUniqueHash(raw, result.sha256(), identity, "raw");
            requireUniqueHash(normalized, result.normalizedSha256(), identity, "normalized");
        }
    }

    private static String deterministicRank(long seed, String requestHash) {
        return sha256Unchecked(seed + "|" + requestHash);
    }

    private static String blindCode(int index) {
        StringBuilder code = new StringBuilder();
        int value = index;
        do {
            code.insert(0, (char) ('A' + value % 26));
            value = value / 26 - 1;
        } while (value >= 0);
        return code.toString();
    }

    static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sha256Unchecked(String text) {
        try { return sha256(text); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static int wordCount(String text) {
        return text.isBlank() ? 0 : text.trim().split("\\s+").length;
    }

    private TtsAbCandidate candidate(String provider, String model, String voice, String language, boolean configured) {
        return new TtsAbCandidate(provider, model, voice, language, "", List.of(),
                configured || ("piper".equals(provider) && !voice.isBlank()) ? "CONFIGURED" : "NOT_CONFIGURED");
    }

    private static String env(String key, String defaultValue) {
        return TtsLaboratuvarYardimci.ortam(key, defaultValue);
    }

    private static boolean has(String key) {
        return TtsLaboratuvarYardimci.ortamVar(key);
    }

    private void writeJson(Path path, Object value) throws Exception {
        TtsLaboratuvarYardimci.atomikYaz(path, json.writeValueAsBytes(value));
    }

    private static String safeId(String id) {
        if (id == null || !id.matches("AB-[0-9A-Z-]{6,40}")) throw new IllegalArgumentException("Geçersiz deney ID");
        return id;
    }

    private static byte[] csvBom(String text) {
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[content.length + 3];
        result[0] = (byte) 0xEF; result[1] = (byte) 0xBB; result[2] = (byte) 0xBF;
        System.arraycopy(content, 0, result, 3, content.length);
        return result;
    }

    static String csv(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private static String escape(String value) {
        return WebGuvenlikService.htmlKacis(value == null ? "" : value);
    }

    private static void copyTree(Path source, Path target) throws Exception {
        if (!Files.isDirectory(source)) return;
        try (Stream<Path> files = Files.walk(source)) {
            for (Path path : files.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        SimpleModule module = new SimpleModule("tts-ab-java-types");
        module.addSerializer(OffsetDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(OffsetDateTime value, JsonGenerator generator,
                                  SerializerProvider serializers) throws IOException {
                generator.writeString(value.toString());
            }
        });
        module.addSerializer(Path.class, new JsonSerializer<>() {
            @Override
            public void serialize(Path value, JsonGenerator generator,
                                  SerializerProvider serializers) throws IOException {
                generator.writeString(value.toString().replace('\\', '/'));
            }
        });
        mapper.registerModule(module);
        return mapper;
    }

    static final class TtsAbExperimentException extends RuntimeException {
        private final String code;

        TtsAbExperimentException(String code, String message) {
            super(message);
            this.code = code;
        }

        String code() {
            return code;
        }
    }
}
