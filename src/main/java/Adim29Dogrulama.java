import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Adım 29 — Forced alignment, altyazı ve okuma takibi doğrulamaları.
 */
public final class Adim29Dogrulama {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();

        mockAlignmentUretimi();
        onizlemeYokReddi();
        sifirBaytReddi();
        astronomiEngeli();
        srtVttExport();
        idempotentHash();
        altyaziYokGuvenli404();
        demoFixtureUretimi();

        Path kok = Files.createTempDirectory("adim29-web-");
        try {
            webApiTestleri(kok);
        } finally {
            silRecursif(kok);
        }

        dokumanTestleri(Path.of(System.getProperty("user.dir")));
        patronPaketTestleri();
        demoFixtureGercekArsivApi();

        System.out.println("ADIM 29 DOĞRULAMA: BAŞARILI");
    }

    private static void altyaziYokGuvenli404() throws Exception {
        Path kok = Files.createTempDirectory("adim29-notitle-");
        try {
            WebOrtam ortam = new WebOrtam(Path.of("."), null, null, null, kok, null, null, null);
            YerelWebSunucu sunucu = new YerelWebSunucu(ortam);
            WebResponse html = sunucu.route("GET", "/api/alignment/5/subtitles", "format=vtt", "", true,
                    "text/html,application/xhtml+xml");
            String govde = new String(html.body(), StandardCharsets.UTF_8);
            if (html.status() != 404 || govde.contains("<h1>Hata</h1>") || govde.contains("<h1>404</h1>")) {
                hata("Altyazı yokken HTML hata sayfası dönmemeli");
            }
            if (!govde.contains("Altyazı henüz üretilmedi")) {
                hata("Altyazı yok mesajı eksik");
            }
            WebResponse json = sunucu.route("GET", "/api/alignment/5/subtitles", "format=srt", "", true,
                    "application/json");
            String j = new String(json.body(), StandardCharsets.UTF_8);
            if (json.status() != 404 || !j.contains("altyazi_yok")) {
                hata("JSON 404 altyazı yanıtı eksik");
            }
            if (WebGuvenlikService.apiKeySizintisiVar(j) || AlignmentGuvenlikService.yolSizintisiVar(j)) {
                hata("Altyazı 404 yanıtında sızıntı");
            }
            System.out.println("OK: Altyazı yokken güvenli 404 yanıtı");
        } finally {
            silRecursif(kok);
        }
    }

    private static void demoFixtureUretimi() throws Exception {
        Path kok = Files.createTempDirectory("adim29-fixture-");
        try {
            AlignmentService svc = new AlignmentService(kok);
            AlignmentService.AlignmentSonucu sonuc = svc.uretDemoFixture(5);
            if (!sonuc.basarili() || !sonuc.sonuc().demoFixture()) {
                hata("Demo fixture üretilemedi");
            }
            if (!Files.isRegularFile(kok.resolve("_alignment")
                    .resolve("ESER-00005-preview-alignment.json"))) {
                hata("Demo fixture JSON yazılmadı");
            }
            String vtt = svc.altyazi(5, "vtt");
            if (!vtt.startsWith("WEBVTT")) {
                hata("Demo fixture VTT hatalı");
            }
            String json = svc.jsonPlan(5);
            if (!json.contains("demoFixture") || !json.contains("true")) {
                hata("demoFixture JSON alanı eksik");
            }
            Path fixtureDir = kok.resolve("_alignment").resolve("_fixture");
            if (!Files.isDirectory(fixtureDir)) {
                hata("Fixture klasörü oluşmadı");
            }
            System.out.println("OK: Demo fixture üretimi");
        } finally {
            silRecursif(kok);
        }
    }

    private static void demoFixtureGercekArsivApi() throws Exception {
        WebOrtam ortam = WebOrtam.varsayilan();
        AlignmentService svc = new AlignmentService(ortam.sesArsivi());
        svc.uretDemoFixture(5);
        YerelWebSunucu sunucu = new YerelWebSunucu(ortam);
        WebResponse vtt = sunucu.route("GET", "/api/alignment/5/subtitles", "format=vtt", "", true, "text/plain");
        String govde = new String(vtt.body(), StandardCharsets.UTF_8);
        if (vtt.status() != 200 || !govde.startsWith("WEBVTT")) {
            hata("Gerçek arşivde VTT API başarısız");
        }
        WebResponse srt = sunucu.route("GET", "/api/alignment/5/subtitles", "format=srt", "", true, "text/plain");
        if (srt.status() != 200 || !new String(srt.body(), StandardCharsets.UTF_8).contains("-->")) {
            hata("Gerçek arşivde SRT API başarısız");
        }
        String plan = new String(sunucu.route("GET", "/api/alignment/5", null, "", true).body(),
                StandardCharsets.UTF_8);
        if (!plan.contains("demoFixture") || WebGuvenlikService.apiKeySizintisiVar(plan)) {
            hata("Gerçek arşiv alignment planı güvensiz veya eksik");
        }
        String sayfa = new String(sunucu.route("GET", "/eser/5/alignment", null, "", true).body(),
                StandardCharsets.UTF_8);
        if (!sayfa.contains("Demo fixture")) {
            hata("/eser/5/alignment demo fixture bilgisi eksik");
        }
        System.out.println("OK: Demo fixture gerçek arşiv API");
    }

    private static void mockAlignmentUretimi() throws Exception {
        Path kok = hazirlaOnizlemeFixture();
        try {
            AlignmentService svc = new AlignmentService(kok);
            AlignmentService.AlignmentSonucu sonuc = svc.uret(5, true, false);
            if (!sonuc.basarili() || sonuc.sonuc().segmentCount() == 0) {
                hata("Mock alignment üretilemedi");
            }
            if (!"MOCK".equals(sonuc.sonuc().source())) {
                hata("Kaynak MOCK olmalı");
            }
            Path alignDir = kok.resolve("_alignment");
            if (!Files.isRegularFile(alignDir.resolve("ESER-00005-preview-alignment.json"))) {
                hata("Alignment JSON yazılmadı");
            }
            System.out.println("OK: Mock alignment üretimi");
        } finally {
            silRecursif(kok);
        }
    }

    private static void onizlemeYokReddi() throws Exception {
        Path kok = Files.createTempDirectory("adim29-noprev-");
        Files.createDirectories(kok);
        AlignmentService svc = new AlignmentService(kok);
        try {
            svc.uret(5, true, false);
            hata("Önizleme yokken alignment engellenmeliydi");
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains("Önizleme")) {
                hata("Yanlış hata mesajı: " + e.getMessage());
            }
        }
        silRecursif(kok);
        System.out.println("OK: Önizleme yokken alignment reddi");
    }

    private static void sifirBaytReddi() throws Exception {
        Path kok = Files.createTempDirectory("adim29-zero-");
        Path eser = kok.resolve("ESER-00005 - Test");
        Path oniz = eser.resolve("onizleme").resolve("elevenlabs");
        Files.createDirectories(oniz);
        Files.writeString(oniz.resolve("preview-input.txt"), "Test metni yeterince uzun.", StandardCharsets.UTF_8);
        Files.write(kok.resolve("dummy.mp3"), new byte[0]);
        Files.write(oniz.resolve("preview-elevenlabs.mp3"), new byte[0]);
        AlignmentService svc = new AlignmentService(kok);
        try {
            svc.uret(5, true, false);
            hata("0 byte audio engellenmeliydi");
        } catch (IllegalStateException e) {
            // expected
        }
        silRecursif(kok);
        System.out.println("OK: 0 byte audio reddi");
    }

    private static void astronomiEngeli() throws Exception {
        AlignmentService svc = new AlignmentService(Files.createTempDirectory("adim29-a-"));
        AlignmentPlan plan = svc.planla(6);
        if (!AlignmentPlan.STATUS_BLOCKED.equals(plan.status())) {
            hata("ESER-00006 plan BLOCKED olmalı");
        }
        try {
            svc.uret(6, true, false);
            hata("ESER-00006 alignment engellenmeliydi");
        } catch (IllegalStateException e) {
            // expected
        }
        System.out.println("OK: ESER-00006 alignment engeli");
    }

    private static void srtVttExport() throws Exception {
        Path kok = hazirlaOnizlemeFixture();
        try {
            AlignmentService svc = new AlignmentService(kok);
            svc.uret(5, true, false);
            String srt = svc.altyazi(5, "srt");
            String vtt = svc.altyazi(5, "vtt");
            if (!srt.contains("-->") || !srt.matches("(?s).*\\d{2}:\\d{2}:\\d{2},\\d{3}.*")) {
                hata("SRT formatı hatalı");
            }
            if (!vtt.startsWith("WEBVTT")) {
                hata("VTT WEBVTT başlığı eksik");
            }
            AlignmentResult r = svc.sonuc(5);
            SubtitleExportService.dogrula(r);
            System.out.println("OK: SRT/VTT export ve segment doğrulama");
        } finally {
            silRecursif(kok);
        }
    }

    private static void idempotentHash() throws Exception {
        Path kok = hazirlaOnizlemeFixture();
        try {
            AlignmentService svc = new AlignmentService(kok);
            AlignmentService.AlignmentSonucu ilk = svc.uret(5, true, false);
            AlignmentService.AlignmentSonucu ikinci = svc.uret(5, true, false);
            if (!ikinci.mevcut()) {
                hata("İkinci alignment idempotent değil");
            }
            if (ilk.sonuc().segmentCount() != ikinci.sonuc().segmentCount()) {
                hata("Idempotent sonuç farklı");
            }
            System.out.println("OK: requestHash/idempotent alignment");
        } finally {
            silRecursif(kok);
        }
    }

    private static void webApiTestleri(Path kok) throws Exception {
        Path metin = kok.resolve("metin");
        Path ses = kok.resolve("ses");
        Path panel = kok.resolve("panel");
        hazirlaWebOnizleme(ses);
        Files.createDirectories(metin);
        Files.createDirectories(panel);

        WebOrtam ortam = new WebOrtam(kok, null, kok.resolve("arsiv"), metin, ses, kok.resolve("kuyruk"), null, panel);
        YerelWebSunucu sunucu = new YerelWebSunucu(ortam);
        AlignmentService svc = new AlignmentService(ses);
        svc.uret(5, true, false);

        for (String path : List.of("/api/alignment", "/api/alignment/5", "/api/alignment/5/segments")) {
            WebResponse r = sunucu.route("GET", path, null, "", true);
            String body = new String(r.body(), StandardCharsets.UTF_8);
            if (r.status() != 200 || WebGuvenlikService.apiKeySizintisiVar(body)
                    || AlignmentGuvenlikService.yolSizintisiVar(body)) {
                hata(path + " başarısız veya güvensiz");
            }
        }

        WebResponse srt = sunucu.route("GET", "/api/alignment/5/subtitles", "format=srt", "", true);
        WebResponse vtt = sunucu.route("GET", "/api/alignment/5/subtitles", "format=vtt", "", true);
        if (srt.status() != 200 || vtt.status() != 200) {
            hata("Subtitle API başarısız");
        }
        if (!new String(vtt.body(), StandardCharsets.UTF_8).startsWith("WEBVTT")) {
            hata("VTT API içeriği hatalı");
        }

        String demo = new String(sunucu.route("GET", "/demo", null, "", true).body(), StandardCharsets.UTF_8);
        if (!demo.contains("Adım 29") || !demo.contains("Ses-Metin Hizalama")) {
            hata("/demo Adım 29 eksik");
        }

        WebResponse alignPage = sunucu.route("GET", "/alignment", null, "", true);
        if (alignPage.status() != 200) {
            hata("/alignment sayfası render edilmedi");
        }

        WebResponse eserAlign = sunucu.route("GET", "/eser/5/alignment", null, "", true);
        String e5 = new String(eserAlign.body(), StandardCharsets.UTF_8);
        if (!e5.contains("Okuma Takibi") || !e5.contains("alignment-segments")) {
            hata("/eser/5/alignment eksik");
        }

        sunucu.baslat(0);
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> hr = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + sunucu.port()
                            + "/api/alignment/5/subtitles?format=vtt")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (hr.statusCode() != 200 || !hr.body().startsWith("WEBVTT")) {
                hata("HTTP VTT endpoint başarısız");
            }
        } finally {
            sunucu.durdur();
        }

        System.out.println("OK: Web API ve alignment sayfaları");
    }

    private static void patronPaketTestleri() throws Exception {
        Path paket = Files.createTempDirectory("adim29-paket-");
        System.setProperty("DEMO_PAKET_KLASORU", paket.toString());
        try {
            DemoRaporService rapor = new DemoRaporService(WebOrtam.varsayilan());
            DemoRaporService.PaketSonucu sonuc = rapor.uret();
            String md = Files.readString(sonuc.klasor().resolve("patron-demo-ozeti.md"), StandardCharsets.UTF_8);
            if (!md.contains("Adım 29") && !md.contains("alignment")) {
                hata("Patron paketi Adım 29 eksik");
            }
            System.out.println("OK: Patron demo paketi güncellendi");
        } finally {
            System.clearProperty("DEMO_PAKET_KLASORU");
            silRecursif(paket);
        }
    }

    private static void dokumanTestleri(Path proje) throws Exception {
        Path mimari = proje.resolve("ADIM_29_MIMARI.md");
        if (!Files.isRegularFile(mimari)) {
            hata("ADIM_29_MIMARI.md eksik");
        }
        String icerik = Files.readString(mimari, StandardCharsets.UTF_8);
        if (!icerik.contains("forced alignment") || !icerik.contains("SRT")) {
            hata("ADIM_29_MIMARI.md içerik eksik");
        }
        System.out.println("OK: ADIM_29_MIMARI.md");
    }

    private static Path hazirlaOnizlemeFixture() throws Exception {
        Path kok = Files.createTempDirectory("adim29-fix-");
        Path eser = kok.resolve("ESER-00005 - Kasagi");
        Path oniz = eser.resolve("onizleme").resolve("elevenlabs");
        Files.createDirectories(oniz);
        String metin = "Kaşağı hikâyesinden bir parça. Bir varmış bir yokmuş. "
                + "Evliya Çelebi bu eserde pek çok ayrıntıyı anlatmıştır. Türkçe karakterler: ğüşıöç.";
        Files.writeString(oniz.resolve("preview-input.txt"), metin, StandardCharsets.UTF_8);
        byte[] mp3 = new byte[512];
        for (int i = 0; i < mp3.length; i++) {
            mp3[i] = (byte) (i % 256);
        }
        Files.write(oniz.resolve("preview-elevenlabs.mp3"), mp3);
        Files.writeString(oniz.resolve("preview-elevenlabs.json"), """
                {"status":"BASARILI","alignmentStatus":"NOT_REQUESTED"}
                """, StandardCharsets.UTF_8);
        return kok;
    }

    private static void hazirlaWebOnizleme(Path ses) throws Exception {
        Path eser = ses.resolve("ESER-00005 - Kasagi");
        Path oniz = eser.resolve("onizleme").resolve("elevenlabs");
        Files.createDirectories(oniz);
        Files.writeString(oniz.resolve("preview-input.txt"),
                "Web test alignment metni. Bir varmış bir yokmuş.", StandardCharsets.UTF_8);
        Files.write(oniz.resolve("preview-elevenlabs.mp3"), new byte[256]);
        SesKalitePanelDemoService.demoVerisiOlustur(ses);
    }

    private static void silRecursif(Path kok) throws Exception {
        if (!Files.exists(kok)) return;
        try (var s = Files.walk(kok)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) { }
            });
        }
    }

    private static void hata(String mesaj) {
        throw new IllegalStateException("ADIM 29 TEST HATASI: " + mesaj);
    }
}
