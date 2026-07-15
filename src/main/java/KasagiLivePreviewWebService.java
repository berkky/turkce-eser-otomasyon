import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Adım 36A localhost durum ve canlı onay hazırlık ekranı — TTS çağrısı yok. */
public final class KasagiLivePreviewWebService {
    private final KasagiLivePreviewService service;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Token> tokens = new HashMap<>();
    private final Map<String, SubmissionRecord> submissions = new HashMap<>();
    private final Set<String> usedSubmissionIds = new HashSet<>();

    public KasagiLivePreviewWebService(WebOrtam environment) {
        this.service = new KasagiLivePreviewService(environment);
    }

    public WebResponse route(String method, String path, String body) throws Exception {
        if (path.equals("/ab-test/live-preview") && "GET".equals(method)) {
            return WebResponse.htmlOk(index());
        }
        if (path.equals("/ab-test/live-preview/ESER-00005") && "GET".equals(method)) {
            return WebResponse.htmlOk(detail(null));
        }
        if (path.equals("/api/ab-test/live-preview/ESER-00005/status") && "GET".equals(method)) {
            return status();
        }
        if (path.equals("/ab-test/live-preview/ESER-00005/create-approval") && "POST".equals(method)) {
            return createApproval(body);
        }
        if (path.equals("/ab-test/live-preview/ESER-00005/revoke-approval") && "POST".equals(method)) {
            return revoke(body);
        }
        return WebResponse.text(405, "Method not allowed");
    }

    private String index() {
        return page("Canlı Önizleme", "<h1>Adım 36 Canlı Önizleme</h1>"
                + "<p><a href=\"/ab-test/live-preview/ESER-00005\">ESER-00005 canlı onay hazırlığı</a></p>"
                + "<p class=\"warning\">Web paneli gerçek TTS çağrısı başlatmaz. Üretim yalnız terminal scriptiyle yapılır.</p>");
    }

    private String detail(String message) throws Exception {
        ApprovedPassage approved;
        try {
            approved = service.resolveApprovedPassage();
        } catch (Exception e) {
            return page("Canlı önizleme", "<h1>Onaylı pasaj bulunamadı</h1><p>"
                    + escape(e.getMessage()) + "</p>");
        }
        String token = newToken("create");
        String revokeToken = newToken("revoke");
        String notice = message == null ? "" : "<p class=\"notice\">" + escape(message) + "</p>";
        return page("ESER-00005 Canlı Onay", notice
                + "<h1>ESER-00005 canlı üretim onayı</h1>"
                + "<p>Selection: <code>" + escape(approved.selectionId()) + "</code></p>"
                + "<p>Candidate: " + escape(approved.candidateId())
                + " · " + approved.approvedCharacterCount() + " karakter · hash "
                + "<code>" + escape(approved.approvedTextHash()) + "</code></p>"
                + "<p>Adım 35 status: <strong>" + escape(approved.status()) + "</strong> "
                + "(liveGenerationAllowed=" + approved.liveGenerationAllowed() + ")</p>"
                + "<p class=\"warning\">API anahtarları bu ekranda gösterilmez. Sağlayıcı dropdown ile seçilir; "
                + "xAI ve OpenAI ayrı kayıtlardır. Gerçek üretim <code>adim36-live-approve.ps1</code> → "
                + "<code>adim36-live-preflight.ps1</code> → <code>adim36-live-generate.ps1</code> sırasıyla yapılır.</p>"
                + "<form method=\"post\" action=\"/ab-test/live-preview/ESER-00005/create-approval\">"
                + hidden("csrfToken", token) + hidden("submissionId", "")
                + hidden("selectionId", approved.selectionId())
                + hidden("approvedTextHash", approved.approvedTextHash())
                + "<label>Sağlayıcı <select name=\"provider\"><option value=\"xai\">xAI</option>"
                + "<option value=\"openai\">OpenAI</option></select></label>"
                + "<label>Voice <input name=\"voice\" required placeholder=\"lumen veya marin\"></label>"
                + "<label>Max bütçe USD <input name=\"maxBudgetUsd\" required value=\"0.05\"></label>"
                + "<label>Confirmation phrase (tam yazın) <input name=\"confirmation\" required autocomplete=\"off\"></label>"
                + "<button type=\"submit\">Canlı onay kaydı oluştur (TTS çağrısı yok)</button></form>"
                + "<form method=\"post\" action=\"/ab-test/live-preview/ESER-00005/revoke-approval\">"
                + hidden("csrfToken", revokeToken) + hidden("submissionId", "")
                + "<button type=\"submit\">Onayı iptal et</button></form>"
                + submissionScript());
    }

    private WebResponse createApproval(String body) throws Exception {
        Map<String, String> form = parseForm(body);
        requireToken(form.get("csrfToken"), "create");
        LiveProvider provider = LiveProvider.valueOf(form.getOrDefault("provider", "xai"));
        consumeSubmissionId(form.get("submissionId"), "create", provider.name());
        LiveGenerationApproval approval = service.createLiveApproval(
                form.get("selectionId"), form.get("approvedTextHash"), provider,
                form.getOrDefault("voice", ""),
                new BigDecimal(form.getOrDefault("maxBudgetUsd", "0")),
                1, form.getOrDefault("confirmation", ""), "LOCAL_USER", false);
        return WebResponse.htmlOk(detail("Onay oluşturuldu: " + approval.status()
                + ". Gerçek TTS hâlâ kilitli; terminal scripti gerekir."));
    }

    private WebResponse revoke(String body) throws Exception {
        Map<String, String> form = parseForm(body);
        requireToken(form.get("csrfToken"), "revoke");
        consumeSubmissionId(form.get("submissionId"), "revoke", "any");
        service.revokeLiveApproval("web-revoke");
        return WebResponse.htmlOk(detail("Onay iptal edildi."));
    }

    private WebResponse status() throws Exception {
        ObjectNode node = json.createObjectNode();
        try {
            ApprovedPassage approved = service.resolveApprovedPassage();
            node.put("selectionId", approved.selectionId());
            node.put("candidateId", approved.candidateId());
            node.put("approvedTextHash", approved.approvedTextHash());
            node.put("status", approved.status());
            node.put("liveGenerationAllowed", false);
            node.put("webStartsTts", false);
            Path latest = service.latestExperiment();
            node.put("liveExperimentExists", latest != null);
            node.put("liveApprovalExists", latest != null
                    && Files.isRegularFile(latest.resolve("approvals/live-generation-approval.json")));
        } catch (Exception e) {
            node.put("error", e instanceof KasagiLivePreviewService.LivePreviewException live
                    ? live.code() : "STATUS_ERROR");
        }
        return WebResponse.jsonOk(WebGuvenlikService.guvenliJson(
                json.writerWithDefaultPrettyPrinter().writeValueAsString(node)));
    }

    private synchronized void consumeSubmissionId(String submissionId, String action, String provider) {
        pruneSubmissions();
        if (submissionId == null || submissionId.isBlank()) {
            throw new IllegalArgumentException("SUBMISSION_ID_INVALID");
        }
        if (!submissionId.matches("[A-Za-z0-9-]{12,80}")) {
            throw new IllegalArgumentException("SUBMISSION_ID_INVALID");
        }
        if (usedSubmissionIds.contains(submissionId)) {
            throw new IllegalArgumentException("SUBMISSION_ID_ALREADY_USED");
        }
        SubmissionRecord reserved = submissions.get(submissionId);
        if (reserved == null) {
            // client-generated UUID accepted once if format OK and unused
            reserved = new SubmissionRecord(action, provider.toLowerCase(Locale.ROOT),
                    Instant.now().plusSeconds(900));
            submissions.put(submissionId, reserved);
        }
        if (reserved.expiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("SUBMISSION_ID_INVALID");
        }
        if (!reserved.action().equals(action)) {
            throw new IllegalArgumentException("SUBMISSION_ID_INVALID");
        }
        if (!"any".equals(provider) && !"any".equals(reserved.provider())
                && !reserved.provider().equalsIgnoreCase(provider)) {
            throw new IllegalArgumentException("SUBMISSION_ID_INVALID");
        }
        usedSubmissionIds.add(submissionId);
        submissions.remove(submissionId);
    }

    private synchronized void pruneSubmissions() {
        Instant now = Instant.now();
        submissions.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    private synchronized String newToken(String action) {
        tokens.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(Instant.now()));
        String token = UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new Token(action, Instant.now().plusSeconds(900)));
        lastIssuedToken = token;
        lastIssuedAction = action;
        return token;
    }

    /** Test yardımı: son üretilen CSRF token. */
    synchronized String lastCsrfToken() {
        return lastIssuedToken;
    }

    synchronized String lastCsrfAction() {
        return lastIssuedAction;
    }

    private String lastIssuedToken;
    private String lastIssuedAction;

    private synchronized void requireToken(String token, String action) {
        Token stored = token == null ? null : tokens.remove(token);
        if (stored == null || stored.expiresAt().isBefore(Instant.now())
                || !stored.action().equals(action)) {
            throw new IllegalArgumentException("CSRF_TOKEN_INVALID");
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> result = new HashMap<>();
        if (body == null || body.isBlank()) return result;
        for (String part : body.split("&")) {
            int equals = part.indexOf('=');
            if (equals > 0) {
                result.put(URLDecoder.decode(part.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(part.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    private static String hidden(String name, String value) {
        return "<input type=\"hidden\" name=\"" + escape(name) + "\" value=\"" + escape(value) + "\">";
    }

    private static String submissionScript() {
        return "<script>document.querySelectorAll('form').forEach(function(f){"
                + "const i=f.querySelector('[name=submissionId]');if(i)i.value=crypto.randomUUID();});</script>";
    }

    private static String page(String title, String content) {
        return "<!doctype html><html lang=\"tr\"><head><meta charset=\"UTF-8\"><title>"
                + escape(title) + "</title><style>body{font-family:system-ui;max-width:960px;margin:2rem auto}"
                + ".warning{background:#fff3cd;padding:1rem}.notice{background:#d1e7dd;padding:1rem}"
                + "label{display:block;margin:.6rem 0}</style></head><body>" + content + "</body></html>";
    }

    private static String escape(String value) {
        return WebGuvenlikService.htmlKacis(value == null ? "" : value);
    }

    private record Token(String action, Instant expiresAt) {
    }

    private record SubmissionRecord(String action, String provider, Instant expiresAt) {
    }
}
