import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Adım 35 için localhost-only minimal pasaj seçimi ve onay ekranı. */
public final class KasagiPasajWebService {
    private final KasagiPasajService service;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Token> tokens = new HashMap<>();

    public KasagiPasajWebService(WebOrtam environment) {
        this.service = new KasagiPasajService(environment);
    }

    public WebResponse route(String method, String path, String body) throws Exception {
        if (path.equals("/ab-test/pasajlar") && "GET".equals(method)) {
            return WebResponse.htmlOk(index());
        }
        if (path.equals("/ab-test/pasajlar/ESER-00005") && "GET".equals(method)) {
            return WebResponse.htmlOk(detail(null));
        }
        if (path.equals("/ab-test/pasajlar/ESER-00005/sec") && "POST".equals(method)) {
            return select(body);
        }
        if (path.equals("/ab-test/pasajlar/ESER-00005/onayla") && "POST".equals(method)) {
            return approve(body);
        }
        if (path.equals("/ab-test/pasajlar/ESER-00005/haklar") && "GET".equals(method)) {
            return rightsPage();
        }
        if (path.equals("/ab-test/pasajlar/ESER-00005/atif") && "GET".equals(method)) {
            return attributionPage();
        }
        if (path.equals("/api/ab-test/pasajlar/ESER-00005/haklar") && "GET".equals(method)) {
            PassageSelectionManifest manifest = service.latestSelection();
            return manifest == null ? WebResponse.json(404, "{\"hata\":\"PASSAGE_SELECTION_NOT_FOUND\"}")
                    : WebResponse.jsonOk(service.rightsJson(manifest.selectionId()));
        }
        if (path.equals("/api/ab-test/pasajlar/ESER-00005") && "GET".equals(method)) {
            PassageSelectionManifest manifest = service.latestSelection();
            return manifest == null ? WebResponse.json(404, "{\"hata\":\"PASSAGE_SELECTION_NOT_FOUND\"}")
                    : WebResponse.jsonOk(service.manifestJson(manifest));
        }
        if (path.equals("/api/ab-test/pasajlar/ESER-00005/durum") && "GET".equals(method)) {
            return status();
        }
        return WebResponse.text(405, "Method not allowed");
    }

    private String index() throws Exception {
        PassageSelectionManifest latest = service.latestSelection();
        String status = latest == null ? "Henüz aday paketi üretilmedi."
                : "Son seçim: " + escape(latest.selectionId()) + " — " + escape(latest.status());
        return page("Kaşağı Pasaj Seçimi", "<h1>Kaşağı Pasaj Seçimi</h1><p>" + status
                + "</p><p><a href=\"/ab-test/pasajlar/ESER-00005\">Üç pasaj adayını aç</a></p>"
                + warning());
    }

    private String detail(String message) throws Exception {
        PassageSelectionManifest manifest = service.latestSelection();
        if (manifest == null) {
            return page("Pasaj bulunamadı", "<h1>Pasaj adayı bulunamadı</h1>"
                    + "<p>Önce offline Adım 35 pasaj adayları komutunu çalıştırın.</p>" + warning());
        }
        String selectToken = newToken(manifest.selectionId(), "select");
        String approveToken = newToken(manifest.selectionId(), "approve");
        StringBuilder cards = new StringBuilder();
        for (PassageCandidate candidate : manifest.candidates()) {
            PassageScores score = candidate.scores();
            double overlap = manifest.candidates().stream().filter(other -> other != candidate)
                    .mapToDouble(other -> KasagiPasajService.overlapRatio(candidate, other)).max().orElse(0);
            cards.append("<article><h2>").append(escape(candidate.candidateId())).append("</h2>")
                    .append("<p><strong>Öneri: ")
                    .append(escape(KasagiPasajService.recommendationType(candidate.candidateId())))
                    .append("</strong> — öneri otomatik seçim değildir.</p>")
                    .append("<p><strong>").append(candidate.normalizedCharacterCount()).append(" karakter · ")
                    .append(candidate.wordCount()).append(" kelime · ")
                    .append(candidate.estimatedSpeechSeconds()).append(" saniye · skor ")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", score.totalScore())).append("</strong></p>")
                    .append("<details open><summary>Tam originalText</summary><pre>")
                    .append(escape(candidate.originalText())).append("</pre></details>")
                    .append("<details open><summary>Tam normalizedText</summary><pre>")
                    .append(escape(candidate.normalizedText())).append("</pre></details>")
                    .append("<p><strong>Satır/kelime fark özeti:</strong> ")
                    .append(escape(diffSummary(candidate))).append("</p><p>normalizationChanges: ")
                    .append(escape(String.join(", ", candidate.normalizationChanges()))).append("</p>")
                    .append("<p>originalTextHash: <code>").append(candidate.originalTextHash())
                    .append("</code><br>normalizedTextHash: <code>").append(candidate.normalizedTextHash())
                    .append("</code></p><p>Original karakter: ").append(candidate.originalCharacterCount())
                    .append(" · Normalized karakter: ").append(candidate.normalizedCharacterCount())
                    .append(" · Kaynak offset: ").append(candidate.sourceStartOffset()).append("–")
                    .append(candidate.sourceEndOffset()).append(" · En yüksek overlap: ")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", overlap)).append("</p>")
                    .append("<p>Başlangıç cümlesi: ").append(escape(firstSentence(candidate.normalizedText())))
                    .append("<br>Bitiş cümlesi: ").append(escape(lastSentence(candidate.normalizedText())))
                    .append("</p><p>Kaynak/lisans durumu: ").append(candidate.sourceLicenseStatus())
                    .append(" · Kullanım uyarısı: atıf ve hak kaydı kullanıcı tarafından incelenmelidir.</p>")
                    .append("<p>Puanlar: anlatı ").append(score.narrativeIntegrity())
                    .append(", duygu ").append(score.emotionIntensity())
                    .append(", diyalog ").append(score.dialoguePresence())
                    .append(", telaffuz ").append(score.pronunciationVariety())
                    .append(", dinleme ").append(score.listeningInterest())
                    .append(", temizlik ").append(score.textCleanliness())
                    .append(", TTS ").append(score.ttsSentenceFit()).append("</p>")
                    .append("<label><input required type=\"radio\" name=\"candidateId\" value=\"")
                    .append(escape(candidate.candidateId())).append("\"> Bu pasajı seç</label></article>");
        }
        String selected = manifest.selectedCandidateId() == null ? "Henüz seçim yok"
                : "Seçilen: " + escape(manifest.selectedCandidateId());
        String approval = manifest.selectedCandidateId() == null ? ""
                : "<form method=\"post\" action=\"/ab-test/pasajlar/ESER-00005/onayla\">"
                + hidden("selectionId", manifest.selectionId()) + hidden("csrfToken", approveToken)
                + hidden("submissionId", "")
                + confirmation("fullTextRead", "Seçtiğim pasajın tam metnini okudum.")
                + confirmation("normalizationDiffReviewed",
                "Original ve normalized metin farklarını kontrol ettim.")
                + confirmation("sourceAttributionSeen", "Kaynak ve atıf bilgisini gördüm.")
                + confirmation("rightsLayersUnderstood",
                "Temel eser hakkı ile kaynak sayfası lisansının ayrı olduğunu anladım.")
                + confirmation("shortTtsUseApproved",
                "Bu pasajın kısa TTS karşılaştırmasında kullanılmasını onaylıyorum.")
                + confirmation("paidApiWarningUnderstood",
                "Bu onayın henüz ücretli API veya ses üretimini başlatmadığını anladım.")
                + hidden("approvedBy", "LOCAL_USER")
                + "<button type=\"submit\">Pasaj onayı oluştur (canlı üretim kilitli)</button></form>";
        String notice = message == null ? "" : "<p class=\"notice\">" + escape(message) + "</p>";
        String content = "<h1>Kaşağı - Vikikaynak</h1>" + notice
                + "<p>Kaynak: " + escape(manifest.source().sourceName())
                + " · Lisans: <strong>" + manifest.source().sourceLicenseStatus() + "</strong>"
                + " · " + manifest.source().sourceCharacterCount() + " karakter · "
                + manifest.source().ttsPartCount() + " TTS parçası</p>"
                + "<p>" + escape(manifest.source().sourceLicenseNote()) + "</p>"
                + "<p><a href=\"/ab-test/pasajlar/ESER-00005/haklar\">Hak katmanlarını incele</a> · "
                + "<a href=\"/ab-test/pasajlar/ESER-00005/atif\">Atıf metnini incele</a></p>"
                + "<form method=\"post\" action=\"/ab-test/pasajlar/ESER-00005/sec\">"
                + hidden("selectionId", manifest.selectionId()) + hidden("csrfToken", selectToken)
                + hidden("submissionId", "") + cards
                + confirmation("candidateTextReviewed",
                "Üç adayın tam original ve normalized metinlerini inceledim.")
                + "<button type=\"submit\">Pasajı seç</button></form><h2>" + selected + "</h2>"
                + approval + warning() + submissionScript();
        return page("Kaşağı Pasajları", content);
    }

    private WebResponse select(String body) throws Exception {
        Map<String, String> form = parseForm(body);
        String selection = form.getOrDefault("selectionId", "");
        requireToken(form.get("csrfToken"), selection, "select");
        if (!Boolean.parseBoolean(form.getOrDefault("candidateTextReviewed", "false"))) {
            throw new IllegalStateException("FULL_CANDIDATE_TEXT_REVIEW_REQUIRED");
        }
        service.select(selection, form.getOrDefault("candidateId", ""),
                form.getOrDefault("submissionId", ""));
        return WebResponse.htmlOk(detail("Pasaj seçildi. Kaynak/lisans onayı henüz oluşturulmadı."));
    }

    private WebResponse approve(String body) throws Exception {
        Map<String, String> form = parseForm(body);
        String selection = form.getOrDefault("selectionId", "");
        requireToken(form.get("csrfToken"), selection, "approve");
        PassageApprovalConfirmations confirmations = new PassageApprovalConfirmations(
                checked(form, "fullTextRead"), checked(form, "normalizationDiffReviewed"),
                checked(form, "sourceAttributionSeen"), checked(form, "rightsLayersUnderstood"),
                checked(form, "shortTtsUseApproved"), checked(form, "paidApiWarningUnderstood"));
        ApprovedPassage approved = service.approve(selection, form.getOrDefault("submissionId", ""),
                confirmations, "LOCAL_USER");
        return WebResponse.htmlOk(detail("Onay oluşturuldu: " + approved.status()
                + ". Canlı üretim kilitli kalmaya devam ediyor."));
    }

    private WebResponse rightsPage() throws Exception {
        PassageSelectionManifest manifest = service.latestSelection();
        if (manifest == null) return WebResponse.text(404, "Pasaj seçimi bulunamadı");
        String rights = service.rightsJson(manifest.selectionId());
        return WebResponse.htmlOk(page("Kaşağı Hak Katmanları",
                "<h1>Temel eser hakkı ve kaynak sürümü lisansı</h1>"
                        + "<p>Bu iki kayıt birbirinden ayrıdır ve nihai hukuk görüşü değildir.</p><pre>"
                        + escape(rights) + "</pre><p><a href=\"/ab-test/pasajlar/ESER-00005\">Pasajlara dön</a></p>"));
    }

    private WebResponse attributionPage() throws Exception {
        PassageSelectionManifest manifest = service.latestSelection();
        if (manifest == null) return WebResponse.text(404, "Pasaj seçimi bulunamadı");
        return WebResponse.htmlOk(page("Kaşağı Kaynak Atfı",
                "<h1>Kaynak ve atıf kaydı</h1><pre>"
                        + escape(service.attributionText(manifest.selectionId()))
                        + "</pre><p><a href=\"/ab-test/pasajlar/ESER-00005\">Pasajlara dön</a></p>"));
    }

    private WebResponse status() throws Exception {
        PassageSelectionManifest manifest = service.latestSelection();
        if (manifest == null) return WebResponse.json(404, "{\"hata\":\"PASSAGE_SELECTION_NOT_FOUND\"}");
        Path root = service.root().resolve(manifest.selectionId());
        ObjectNode node = json.createObjectNode();
        node.put("eserId", ESER_CODE());
        node.put("selectionId", manifest.selectionId());
        node.put("status", manifest.status());
        node.put("selectedCandidateId", manifest.selectedCandidateId());
        node.put("approvalExists", Files.isRegularFile(root.resolve("approved-passage.json")));
        node.put("openAiProfileExists", Files.isRegularFile(root.resolve("openai-preview-profile.json")));
        node.put("xaiProfileExists", Files.isRegularFile(root.resolve("xai-preview-profile.json")));
        node.put("liveDraftExists", Files.isRegularFile(root.resolve("live-preview-draft.json")));
        node.put("liveGenerationAllowed", false);
        node.put("externalApiCalled", false);
        return WebResponse.jsonOk(WebGuvenlikService.guvenliJson(
                json.writerWithDefaultPrettyPrinter().writeValueAsString(node)));
    }

    private synchronized String newToken(String selectionId, String action) {
        tokens.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(Instant.now()));
        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new Token(selectionId, action, Instant.now().plusSeconds(900)));
        return token;
    }

    private synchronized void requireToken(String token, String selectionId, String action) {
        Token stored = token == null ? null : tokens.remove(token);
        if (stored == null || stored.expiresAt().isBefore(Instant.now())
                || !stored.selectionId().equals(selectionId) || !stored.action().equals(action)) {
            throw new IllegalArgumentException("CSRF_TOKEN_INVALID");
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> result = new HashMap<>();
        if (body == null || body.isBlank()) return result;
        for (String part : body.split("&")) {
            int equals = part.indexOf('=');
            if (equals > 0) {
                result.put(java.net.URLDecoder.decode(part.substring(0, equals), StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(part.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    private static String hidden(String name, String value) {
        return "<input type=\"hidden\" name=\"" + escape(name) + "\" value=\"" + escape(value) + "\">";
    }

    private static String confirmation(String name, String text) {
        return "<label><input required type=\"checkbox\" name=\"" + escape(name)
                + "\" value=\"true\"> " + escape(text) + "</label>";
    }

    private static boolean checked(Map<String, String> form, String name) {
        return Boolean.parseBoolean(form.getOrDefault(name, "false"));
    }

    private static String diffSummary(PassageCandidate candidate) {
        int characterDelta = candidate.originalCharacterCount() - candidate.normalizedCharacterCount();
        return candidate.normalizationChanges().size() + " normalizasyon türü; "
                + characterDelta + " karakterlik fark. Original hash ve normalized hash ayrı doğrulanır.";
    }

    private static String firstSentence(String text) {
        int end = sentenceBoundary(text, true);
        return text.substring(0, Math.min(end, text.length())).strip();
    }

    private static String lastSentence(String text) {
        int start = Math.max(text.lastIndexOf('.', Math.max(0, text.length() - 2)),
                Math.max(text.lastIndexOf('!', Math.max(0, text.length() - 2)),
                        text.lastIndexOf('?', Math.max(0, text.length() - 2))));
        return text.substring(start < 0 ? 0 : start + 1).strip();
    }

    private static int sentenceBoundary(String text, boolean ignored) {
        int result = text.length();
        for (char mark : new char[]{'.', '!', '?'}) {
            int index = text.indexOf(mark);
            if (index >= 0) result = Math.min(result, index + 1);
        }
        return result;
    }

    private static String submissionScript() {
        return "<script>document.querySelectorAll('form').forEach(function(f){"
                + "const i=f.querySelector('[name=submissionId]');if(i)i.value=crypto.randomUUID();});</script>";
    }

    private static String warning() {
        return "<p class=\"warning\"><strong>Uyarı:</strong> Bu işlem henüz ücretli API çağrısı veya "
                + "ses üretimi yapmaz. Onaydan sonra da canlı üretim kilitli kalır.</p>";
    }

    private static String page(String title, String content) {
        return "<!doctype html><html lang=\"tr\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width\"><title>" + escape(title) + "</title>"
                + "<style>body{font-family:system-ui;max-width:1100px;margin:2rem auto;padding:1rem}"
                + "article{border:1px solid #bbb;border-radius:8px;padding:1rem;margin:1rem 0}"
                + "pre{white-space:pre-wrap;font:inherit}.warning{background:#fff3cd;padding:1rem}"
                + ".notice{background:#d1e7dd;padding:1rem}label{display:block;margin:.8rem 0}"
                + "button,input{padding:.55rem}</style></head><body>" + content + "</body></html>";
    }

    private static String escape(String value) {
        return WebGuvenlikService.htmlKacis(value == null ? "" : value);
    }

    private static String ESER_CODE() {
        return "ESER-00005";
    }

    private record Token(String selectionId, String action, Instant expiresAt) {
    }
}
