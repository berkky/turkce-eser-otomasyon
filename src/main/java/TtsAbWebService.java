import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class TtsAbWebService {
    private final TtsAbService service;

    public TtsAbWebService(WebOrtam environment) {
        this.service = new TtsAbService(environment);
    }

    public WebResponse route(String method, String path, String body) throws Exception {
        String rest = path.equals("/ab-test") ? "" : path.substring("/ab-test/".length());
        if (rest.isBlank() && "GET".equals(method)) return WebResponse.htmlOk(index());
        String[] parts = rest.split("/");
        String experimentId = parts[0];
        Path root = experimentRoot(experimentId);
        if (parts.length == 3 && "media".equals(parts[1]) && "GET".equals(method)) {
            return media(root, parts[2]);
        }
        if (parts.length == 2 && "sonuclar".equals(parts[1])) {
            if ("POST".equals(method)) return submit(root, experimentId, body);
            if ("GET".equals(method)) return results(root);
        }
        if (parts.length == 1 && "GET".equals(method)) return WebResponse.htmlOk(detail(root, experimentId));
        return WebResponse.text(405, "Method not allowed");
    }

    private String index() throws Exception {
        StringBuilder items = new StringBuilder();
        Files.createDirectories(service.root());
        try (Stream<Path> paths = Files.list(service.root())) {
            for (Path path : paths.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("AB-"))
                    .sorted(Comparator.reverseOrder()).toList()) {
                String id = path.getFileName().toString();
                if (Files.isRegularFile(path.resolve("manifest.json"))) {
                    items.append("<li><a href=\"/ab-test/").append(escape(id)).append("\">")
                            .append(escape(id)).append("</a></li>");
                }
            }
        }
        return page("A/B Testleri", "<h1>Türkçe TTS Kör Testleri</h1><ul>" + items + "</ul>");
    }

    private String detail(Path root, String id) throws Exception {
        List<String> codes = blindCodes(root);
        String sourceType = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(root.resolve("manifest.json").toFile()).path("sourceType").asText("");
        String fixtureWarning = "FIXTURE".equals(sourceType)
                ? "<p><strong>Uyarı:</strong> Fixture kaynak ve sentetik mock sesler kalite kanıtı değildir; "
                + "ticari değerlendirmede kullanılamaz.</p>" : "";
        StringBuilder audio = new StringBuilder();
        StringBuilder options = new StringBuilder();
        for (String code : codes) {
            audio.append("<section><h2>Örnek ").append(code).append("</h2><audio controls preload=\"metadata\" src=\"/ab-test/")
                    .append(escape(id)).append("/media/").append(code).append("\"></audio></section>");
            options.append("<option value=\"").append(code).append("\">Örnek ").append(code).append("</option>");
        }
        String form = "<form method=\"post\" action=\"/ab-test/" + escape(id) + "/sonuclar\" id=\"eval\">"
                + "<input type=\"hidden\" name=\"submissionId\" id=\"submissionId\">"
                + select("blindCode", "Değerlendirilen örnek", options.toString())
                + score("naturalness", "Doğallık") + score("turkishPronunciation", "Türkçe telaffuz")
                + score("emotion", "Duygu") + score("continueListening", "Devamını dinleme isteği")
                + score("commercialQuality", "Ticari kalite") + score("fatigue", "Yoruculuk (10 çok yorucu)")
                + text("overallPreference", "Genel tercih") + text("mispronouncedWords", "Hatalı telaffuz edilen kelimeler")
                + yesNo("listen20Minutes", "20 dakika dinler miydin?")
                + yesNo("listenFullBook", "Tam kitap dinler miydin?")
                + "<label>Serbest yorum<textarea name=\"comment\" maxlength=\"2000\"></textarea></label>"
                + "<button type=\"submit\">Değerlendirmeyi gönder</button></form>"
                + "<script>(function(){const k='tts-ab-" + escape(id)
                + "-submission';let v=localStorage.getItem(k);if(!v){v=crypto.randomUUID();localStorage.setItem(k,v)}"
                + "document.getElementById('submissionId').value=v;})();</script>";
        return page("Kör Test " + id, "<h1>Türkçe TTS Kör Test</h1><p>Deney: " + escape(id)
                + "</p><p>Sağlayıcı isimleri değerlendirme tamamlanana kadar gizlidir.</p>"
                + fixtureWarning + audio + form);
    }

    private synchronized WebResponse submit(Path root, String id, String body) throws Exception {
        Map<String, String> form = parseForm(body);
        String submissionId = form.getOrDefault("submissionId", "");
        String code = form.getOrDefault("blindCode", "");
        if (!submissionId.matches("[A-Za-z0-9-]{12,80}") || !blindCodes(root).contains(code)) {
            return WebResponse.forbidden("Geçersiz submission veya örnek kodu");
        }
        Path csv = root.resolve("evaluation").resolve("sonuclar.csv");
        String existing = Files.isRegularFile(csv) ? Files.readString(csv, StandardCharsets.UTF_8) : "";
        if (existing.contains("\"" + submissionId.replace("\"", "\"\"") + "\"")) {
            return WebResponse.htmlOk(page("Zaten kaydedildi", "<p>Bu tarayıcı gönderimi daha önce kaydedildi.</p>"));
        }
        int naturalness = score(form, "naturalness");
        int pronunciation = score(form, "turkishPronunciation");
        int emotion = score(form, "emotion");
        int continueListening = score(form, "continueListening");
        int commercial = score(form, "commercialQuality");
        int fatigue = score(form, "fatigue");
        String row = String.join(";",
                TtsAbService.csv(id), TtsAbService.csv(submissionId), TtsAbService.csv(code),
                Integer.toString(naturalness), Integer.toString(pronunciation), Integer.toString(emotion),
                Integer.toString(continueListening), Integer.toString(commercial), Integer.toString(fatigue),
                TtsAbService.csv(form.get("overallPreference")),
                TtsAbService.csv(form.get("mispronouncedWords")),
                Boolean.toString(Boolean.parseBoolean(form.getOrDefault("listen20Minutes", "false"))),
                Boolean.toString(Boolean.parseBoolean(form.getOrDefault("listenFullBook", "false"))),
                TtsAbService.csv(form.get("comment")), TtsAbService.csv(OffsetDateTime.now().toString())) + "\r\n";
        Path temporary = Files.createTempFile(csv.getParent(), "results-", ".csv");
        try {
            Files.writeString(temporary, existing + row, StandardCharsets.UTF_8);
            Files.move(temporary, csv, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return WebResponse.htmlOk(page("Kaydedildi", "<h1>Teşekkürler</h1><p>Değerlendirme kaydedildi.</p>"));
    }

    private WebResponse results(Path root) throws Exception {
        Path csv = root.resolve("evaluation").resolve("sonuclar.csv");
        if (!Files.isRegularFile(csv)) return WebResponse.notFound("Sonuç bulunamadı");
        return WebResponse.binary(200, "text/csv; charset=UTF-8", Files.readAllBytes(csv))
                .withHeader("Content-Disposition", "attachment; filename=\"sonuclar.csv\"");
    }

    private WebResponse media(Path root, String code) throws Exception {
        if (!code.matches("[A-Z]{1,3}")) return WebResponse.notFound("Örnek bulunamadı");
        Path file = root.resolve("blind").resolve("ornek-" + code + ".mp3").normalize();
        if (!WebGuvenlikService.guvenliAltDosya(root.resolve("blind"), file)
                || !Files.isRegularFile(file) || Files.size(file) == 0) {
            return WebResponse.notFound("Örnek bulunamadı");
        }
        return WebResponse.binary(200, "audio/mpeg", Files.readAllBytes(file))
                .withHeader("Cache-Control", "no-store");
    }

    private Path experimentRoot(String id) throws Exception {
        if (id == null || !id.matches("AB-[0-9A-Z-]{6,40}")) throw new IllegalArgumentException("Geçersiz deney ID");
        Path root = service.root().resolve(id).normalize();
        if (!WebGuvenlikService.guvenliAltDosya(service.root(), root)
                || !Files.isRegularFile(root.resolve("manifest.json"))) {
            throw new IllegalArgumentException("Deney bulunamadı");
        }
        return root;
    }

    private static List<String> blindCodes(Path root) throws Exception {
        List<String> result = new ArrayList<>();
        try (Stream<Path> paths = Files.list(root.resolve("blind"))) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                String name = path.getFileName().toString();
                if (name.matches("ornek-[A-Z]{1,3}\\.mp3")) {
                    result.add(name.substring(6, name.length() - 4));
                }
            }
        }
        return result;
    }

    private static int score(Map<String, String> form, String name) {
        int value;
        try { value = Integer.parseInt(form.getOrDefault(name, "0")); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(name + " sayı olmalıdır."); }
        if (value < 1 || value > 10) throw new IllegalArgumentException(name + " 1-10 aralığında olmalıdır.");
        return value;
    }

    private static String score(String name, String label) {
        StringBuilder options = new StringBuilder();
        for (int i = 1; i <= 10; i++) options.append("<option>").append(i).append("</option>");
        return select(name, label, options.toString());
    }

    private static String select(String name, String label, String options) {
        return "<label>" + escape(label) + "<select required name=\"" + name + "\">" + options + "</select></label>";
    }

    private static String text(String name, String label) {
        return "<label>" + escape(label) + "<input name=\"" + name + "\" maxlength=\"500\"></label>";
    }

    private static String yesNo(String name, String label) {
        return select(name, label, "<option value=\"true\">Evet</option><option value=\"false\">Hayır</option>");
    }

    private static String page(String title, String content) {
        return "<!doctype html><html lang=\"tr\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width\">"
                + "<title>" + escape(title) + "</title><style>body{font-family:system-ui;max-width:900px;margin:2rem auto;padding:1rem}"
                + "label{display:block;margin:1rem 0}input,select,textarea{display:block;width:100%;padding:.55rem}"
                + "audio{width:100%}button{padding:.7rem 1rem}</style></head><body>" + content + "</body></html>";
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

    private static String escape(String value) {
        return WebGuvenlikService.htmlKacis(value == null ? "" : value);
    }
}
