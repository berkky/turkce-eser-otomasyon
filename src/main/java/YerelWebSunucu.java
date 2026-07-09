import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Localhost-only HTTP sunucusu.
 */
public final class YerelWebSunucu {
    private final WebOrtam ortam;
    private final WebKalitePanelService kalitePanel;
    private final WebIslemService islemService;
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer sunucu;

    public YerelWebSunucu(WebOrtam ortam) throws Exception {
        this.ortam = ortam;
        this.kalitePanel = new WebKalitePanelService(ortam);
        this.islemService = new WebIslemService(ortam, kalitePanel);
        islemService.klasorleriHazirla();
        kalitePanel.yenile();
    }

    public void baslat(int port) throws IOException {
        sunucu = HttpServer.create(new InetSocketAddress(WebOrtam.HOST, port), 0);
        sunucu.createContext("/", this::handle);
        sunucu.setExecutor(Executors.newFixedThreadPool(4));
        sunucu.start();
    }

    public void durdur() {
        if (sunucu != null) {
            sunucu.stop(0);
        }
    }

    public int port() {
        return sunucu.getAddress().getPort();
    }

    WebResponse route(String method, String path, String query, String body, boolean localhost) throws Exception {
        if (!localhost) {
            return WebResponse.forbidden("Yalnızca localhost");
        }
        if (path.startsWith("/assets/")) {
            return WebStaticAssetService.classpathAsset(path.substring("/assets/".length()));
        }
        if (path.startsWith("/media/preview/")) {
            return mediaPreview(path.substring("/media/preview/".length()));
        }
        if (path.equals("/media/file") && "GET".equals(method)) {
            return mediaFile(query);
        }
        if (path.equals("/api/status") && "GET".equals(method)) {
            return WebResponse.jsonOk(statusJson());
        }
        if (path.equals("/api/eserler") && "GET".equals(method)) {
            return WebResponse.jsonOk(new WebEserService(ortam).eserlerJson());
        }
        if (path.startsWith("/api/eser/") && "GET".equals(method)) {
            int id = parseId(path.substring("/api/eser/".length()));
            String veri = new WebEserService(ortam).eserJson(id);
            return veri == null ? WebResponse.json(404, "{\"hata\":\"bulunamadi\"}") : WebResponse.jsonOk(veri);
        }
        if (path.equals("/api/kalite") && "GET".equals(method)) {
            return WebResponse.jsonOk(kalitePanel.kaliteJson());
        }
        if (path.equals("/api/kuyruk") && "GET".equals(method)) {
            return WebResponse.jsonOk(kuyrukJson());
        }
        if ("POST".equals(method) && path.equals("/islemler")) {
            return postIslem(body);
        }
        if (!"GET".equals(method)) {
            return WebResponse.text(405, "Method not allowed");
        }
        if (path.equals("/")) {
            return WebResponse.htmlOk(WebTemplateService.dashboard(dashboard()));
        }
        if (path.equals("/eserler")) {
            return WebResponse.htmlOk(WebTemplateService.eserler(new WebEserService(ortam).eserleriListele()));
        }
        if (path.startsWith("/eser/")) {
            int id = parseId(path.substring("/eser/".length()));
            WebEserService.WebEserDetay d = new WebEserService(ortam).eserDetay(id);
            return d == null ? WebResponse.notFound("Eser bulunamadı")
                    : WebResponse.htmlOk(WebTemplateService.eserDetay(d, kalitePanel));
        }
        if (path.equals("/kalite")) {
            Map<String, String> media = tersMediaHaritasi();
            return WebResponse.htmlOk(WebTemplateService.kalite(kalitePanel.rapor(), media));
        }
        if (path.equals("/sistem")) {
            return WebResponse.htmlOk(WebTemplateService.sistem(new WebSistemDurumService(ortam).durum()));
        }
        if (path.equals("/kuyruk")) {
            UretimKuyruguService kuyruk = new UretimKuyruguService(ortam.kuyruk(), ortam.sesArsivi());
            kuyruk.senkronizeEt(ortam.metinArsivi());
            return WebResponse.htmlOk(WebTemplateService.kuyruk(kuyruk.listele()));
        }
        if (path.equals("/islemler")) {
            return WebResponse.htmlOk(WebTemplateService.islemler(islemService.yeniNonce(), queryMesaj(query)));
        }
        if (path.equals("/docs")) {
            return WebResponse.htmlOk(WebTemplateService.docs(List.of(
                    "README.md", "PROJE_DURUMU.md", "ADIM_25_MIMARI.md", "ADIM_26_MIMARI.md",
                    "TTS_ARASTIRMA_VE_YOL_HARITASI.md")));
        }
        if (path.startsWith("/docs/")) {
            String ad = path.substring("/docs/".length());
            return docSayfa(ad);
        }
        if (path.equals("/telaffuz")) {
            return WebResponse.htmlOk(WebTemplateService.telaffuz(kalitePanel.telaffuzJson()));
        }
        return WebResponse.notFound("Sayfa bulunamadı");
    }

    private void handle(HttpExchange exchange) {
        try {
            WebGuvenlikService.localhostZorunlu(exchange);
            String method = exchange.getRequestMethod();
            String rawPath = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getRawQuery();
            String body = "";
            if ("POST".equals(method)) {
                body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            }
            WebResponse yanit = route(method, rawPath, query, body, true);
            yaz(exchange, yanit);
        } catch (WebGuvenlikService.GuvenlikIstisnasi e) {
            yaz(exchange, WebResponse.forbidden(e.getMessage()));
        } catch (Exception e) {
            yaz(exchange, WebResponse.html(500, "<h1>Hata</h1><p>" + WebGuvenlikService.htmlKacis(e.getMessage()) + "</p>"));
        }
    }

    private WebResponse mediaPreview(String safeId) throws Exception {
        safeId = safeId.replaceAll("[^a-zA-Z0-9\\-]", "");
        Path dosya = kalitePanel.mediaDosyasi(safeId);
        if (dosya == null || !WebGuvenlikService.guvenliAltDosya(ortam.sesArsivi(), dosya)) {
            return WebResponse.notFound("Önizleme bulunamadı");
        }
        if (Files.size(dosya) < SesKaliteOlcutleri.EN_AZ_GECERLI_MP3_BOYUTU) {
            return WebResponse.notFound("Geçersiz ses dosyası");
        }
        return WebStaticAssetService.dosyaSun(ortam.sesArsivi(), dosya);
    }

    private WebResponse mediaFile(String query) throws Exception {
        Map<String, String> p = parseQuery(query);
        int eserId = Integer.parseInt(p.getOrDefault("eser", "0"));
        String tip = p.getOrDefault("tip", "");
        WebEserService.WebEserDetay d = new WebEserService(ortam).eserDetay(eserId);
        if (d == null) {
            return WebResponse.notFound("Eser yok");
        }
        Path dosya = "pdf".equals(tip) ? d.kaynakPdf() : d.metadataJson();
        if (dosya == null) {
            return WebResponse.notFound("Dosya yok");
        }
        Path kok = uygunKok(dosya);
        if (kok == null) {
            return WebResponse.forbidden("Dosya erişimi reddedildi");
        }
        return WebStaticAssetService.dosyaSun(kok, dosya);
    }

    private WebResponse postIslem(String body) throws Exception {
        Map<String, String> form = parseForm(body);
        if (!islemService.nonceGecerli(form.get("nonce"))) {
            return WebResponse.forbidden("Geçersiz veya süresi dolmuş nonce");
        }
        WebIslemSonucu sonuc = islemService.islem(form.get("aksiyon"));
        String q = "mesaj=" + java.net.URLEncoder.encode(sonuc.mesaj(), StandardCharsets.UTF_8);
        return WebResponse.redirect(sonuc.yonlendirme() + "?" + q);
    }

    private WebResponse docSayfa(String ad) throws Exception {
        if (ad.contains("..") || ad.contains("/") || ad.contains("\\")) {
            return WebResponse.forbidden("Geçersiz dosya");
        }
        Path dosya = ortam.projeKlasoru().resolve(ad);
        if (!WebGuvenlikService.guvenliAltDosya(ortam.projeKlasoru(), dosya) || !Files.isRegularFile(dosya)) {
            return WebResponse.notFound("Doküman bulunamadı");
        }
        String icerik = Files.readString(dosya, StandardCharsets.UTF_8);
        return WebResponse.htmlOk(WebTemplateService.docIcerik(ad, icerik));
    }

    private WebTemplateService.DashboardVeri dashboard() throws Exception {
        List<WebEserService.WebEserOzeti> eserler = new WebEserService(ortam).eserleriListele();
        int hazir = 0, kontrol = 0, oniz = 0, buyuk = 0;
        for (WebEserService.WebEserOzeti e : eserler) {
            if ("HAZIR".equalsIgnoreCase(e.metadataDurumu())) hazir++;
            if (e.metadataDurumu() != null && e.metadataDurumu().toUpperCase(Locale.ROOT).contains("KONTROL")) kontrol++;
            if (e.onizlemeVar()) oniz++;
            if (e.buyukEser()) buyuk++;
        }
        UretimKuyruguService k = new UretimKuyruguService(ortam.kuyruk(), ortam.sesArsivi());
        k.senkronizeEt(ortam.metinArsivi());
        int ttsHazir = 0, ttsTamam = 0;
        for (UretimIsi i : k.listele()) {
            if (i.durum() == UretimDurumu.HAZIR) ttsHazir++;
            if (i.durum() == UretimDurumu.TAMAMLANDI) ttsTamam++;
        }
        var s = new WebSistemDurumService(ortam).durum();
        var el = ElevenLabsFabrika.durumOzeti();
        return new WebTemplateService.DashboardVeri(
                OffsetDateTime.now().toString(), eserler.size(), hazir, kontrol,
                ttsHazir, ttsTamam, oniz, buyuk, el.ttsDurumu(),
                s.piper() ? "HAZIR" : "KAPALI", s.ffmpeg(), s.excelVar());
    }

    private String statusJson() throws Exception {
        WebTemplateService.DashboardVeri d = dashboard();
        ObjectNode n = json.createObjectNode();
        n.put("guncelleme", d.guncelleme());
        n.put("toplamEser", d.toplamEser());
        n.put("metadataHazir", d.metadataHazir());
        n.put("kontrolGerek", d.kontrolGerek());
        n.put("onizleme", d.onizleme());
        n.put("buyukEser", d.buyukEser());
        n.put("elevenLabs", d.elevenLabs());
        n.put("piper", d.piper());
        n.put("ffmpeg", d.ffmpeg() ? "HAZIR" : "KAPALI");
        return WebGuvenlikService.guvenliJson(json.writerWithDefaultPrettyPrinter().writeValueAsString(n));
    }

    private String kuyrukJson() throws Exception {
        UretimKuyruguService k = new UretimKuyruguService(ortam.kuyruk(), ortam.sesArsivi());
        k.senkronizeEt(ortam.metinArsivi());
        return json.writeValueAsString(k.listele().stream().map(i -> Map.of(
                "id", i.id(), "eserAdi", i.eserAdi(), "durum", i.durum().name(),
                "hazir", i.hazirParca(), "toplam", i.toplamParca())).toList());
    }

    private Path uygunKok(Path dosya) {
        if (WebGuvenlikService.guvenliAltDosya(ortam.metinArsivi(), dosya)) {
            return ortam.metinArsivi();
        }
        if (WebGuvenlikService.guvenliAltDosya(ortam.arsiv(), dosya)) {
            return ortam.arsiv();
        }
        if (WebGuvenlikService.guvenliAltDosya(ortam.sesArsivi(), dosya)) {
            return ortam.sesArsivi();
        }
        if (WebGuvenlikService.guvenliAltDosya(ortam.projeKlasoru(), dosya)) {
            return ortam.projeKlasoru();
        }
        return null;
    }

    private Map<String, String> tersMediaHaritasi() {
        Map<String, String> ters = new HashMap<>();
        kalitePanel.mediaKayitlari().forEach((id, path) -> ters.put(path.toString(), id));
        return ters;
    }

    private static int parseId(String s) {
        return Integer.parseInt(s.replaceAll("[^0-9]", ""));
    }

    private static String queryMesaj(String query) {
        if (query == null) return "";
        for (String parca : query.split("&")) {
            if (parca.startsWith("mesaj=")) {
                return java.net.URLDecoder.decode(parca.substring(6), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static Map<String, String> parseQuery(String query) {
        return parseForm(query == null ? "" : query);
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> m = new HashMap<>();
        if (body == null || body.isBlank()) return m;
        for (String parca : body.split("&")) {
            int eq = parca.indexOf('=');
            if (eq > 0) {
                m.put(java.net.URLDecoder.decode(parca.substring(0, eq), StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(parca.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return m;
    }

    private static void yaz(HttpExchange exchange, WebResponse yanit) {
        try {
            exchange.getResponseHeaders().set("Content-Type", yanit.contentType());
            for (Map.Entry<String, String> h : yanit.headers().entrySet()) {
                exchange.getResponseHeaders().set(h.getKey(), h.getValue());
            }
            exchange.sendResponseHeaders(yanit.status(), yanit.body().length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(yanit.body());
            }
        } catch (IOException ignored) {
        }
    }
}
