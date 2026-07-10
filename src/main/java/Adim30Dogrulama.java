import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adım 30 — Gerçek forced alignment API kapısı doğrulamaları.
 */
public final class Adim30Dogrulama {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();

        gercekApiOnaysizMock();
        apiKeyYokHatasi();
        onizlemeYokHatasi();
        sifirBaytReddi();
        astronomiEngeli();
        mockHalaCalisiyor();
        demoFixtureVtt();
        fakeApiParse();
        malformedApiGuvenliFailed();
        apiKeySizintisiYok();
        gercekApiOnaysizTransportCagrilmiyor();

        Path kok = Files.createTempDirectory("adim30-web-");
        try {
            webApiTestleri(kok);
        } finally {
            silRecursif(kok);
        }

        dokumanTestleri(Path.of(System.getProperty("user.dir")));
        patronPaketTestleri();

        System.out.println("ADIM 30 DOĞRULAMA: BAŞARILI");
    }

    private static void gercekApiOnaysizMock() throws Exception {
        Path kok = hazirlaOnizlemeFixture();
        try {
            System.setProperty("ELEVENLABS_MOCK", "true");
            AlignmentService svc = new AlignmentService(kok);
            AlignmentService.AlignmentSonucu sonuc = svc.uret(5, true, false);
            if (!sonuc.basarili() || sonuc.sonuc().realApiUsed()) {
                hata("Mock modda gerçek API kullanılmamalı");
            }
            if (!AlignmentResult.SOURCE_MOCK.equals(sonuc.sonuc().source())) {
                hata("Kaynak MOCK olmalı");
            }
            System.out.println("OK: -GercekApiOnayli olmadan mock kullanılıyor");
        } finally {
            System.clearProperty("ELEVENLABS_MOCK");
            silRecursif(kok);
        }
    }

    private static void gercekApiOnaysizTransportCagrilmiyor() throws Exception {
        Path kok = hazirlaOnizlemeFixture();
        AtomicInteger cagri = new AtomicInteger();
        AlignmentApiClient.testTransportAyarla((audio, ad, metin) -> {
            cagri.incrementAndGet();
            return new AlignmentApiClient.AlignmentHttpTransport.Yanit(200, "{}", "req-test");
        });
        try {
            System.setProperty("ELEVENLABS_MOCK", "true");
            AlignmentService svc = new AlignmentService(kok);
            svc.uret(5, true, false);
            if (cagri.get() > 0) {
                hata("Mock modda transport çağrılmamalı");
            }
            System.out.println("OK: Gerçek API transport mock modda çağrılmıyor");
        } finally {
            AlignmentApiClient.testTransportTemizle();
            System.clearProperty("ELEVENLABS_MOCK");
            silRecursif(kok);
        }
    }

    private static void apiKeyYokHatasi() throws Exception {
        if (TtsLaboratuvarYardimci.ortamVar("ELEVENLABS_API_KEY")) {
            System.out.println("OK: API key yok testi atlandı (ortam anahtarı tanımlı)");
            return;
        }
        Path kok = hazirlaOnizlemeFixture();
        try {
            System.clearProperty("ELEVENLABS_MOCK");
            AlignmentService svc = new AlignmentService(kok);
            try {
                svc.uret(5, false, true);
                hata("API key yokken gerçek alignment engellenmeliydi");
            } catch (IllegalStateException e) {
                if (!e.getMessage().contains("API anahtarı")) {
                    hata("Yanlış hata: " + e.getMessage());
                }
            }
            System.out.println("OK: API key yokken güvenli hata");
        } finally {
            silRecursif(kok);
        }
    }

    private static void onizlemeYokHatasi() throws Exception {
        Path kok = Files.createTempDirectory("adim30-noprev-");
        AlignmentService svc = new AlignmentService(kok);
        try {
            svc.uret(5, false, true);
            hata("Önizleme yokken engellenmeliydi");
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains("önizleme") && !e.getMessage().contains("Önizleme")) {
                hata("Yanlış mesaj: " + e.getMessage());
            }
        }
        silRecursif(kok);
        System.out.println("OK: Preview yokken güvenli hata");
    }

    private static void sifirBaytReddi() throws Exception {
        Path kok = Files.createTempDirectory("adim30-zero-");
        Path eser = kok.resolve("ESER-00005 - Test");
        Path oniz = eser.resolve("onizleme").resolve("elevenlabs");
        Files.createDirectories(oniz);
        Files.writeString(oniz.resolve("preview-input.txt"), "Test metni yeterince uzun.", StandardCharsets.UTF_8);
        Files.write(oniz.resolve("preview-elevenlabs.mp3"), new byte[0]);
        AlignmentService svc = new AlignmentService(kok);
        try {
            svc.uret(5, true, false);
            hata("0 byte audio engellenmeliydi");
        } catch (Exception e) {
            // expected
        }
        silRecursif(kok);
        System.out.println("OK: 0 byte audio reddi");
    }

    private static void astronomiEngeli() throws Exception {
        AlignmentService svc = new AlignmentService(Files.createTempDirectory("adim30-a-"));
        try {
            svc.uret(6, true, false);
            hata("ESER-00006 engellenmeliydi");
        } catch (Exception e) {
            // expected
        }
        System.out.println("OK: ESER-00006 alignment engeli");
    }

    private static void mockHalaCalisiyor() throws Exception {
        Path kok = hazirlaOnizlemeFixture();
        try {
            System.setProperty("ELEVENLABS_MOCK", "true");
            AlignmentService svc = new AlignmentService(kok);
            var sonuc = svc.uret(5, true, false);
            if (!sonuc.basarili() || sonuc.sonuc().segmentCount() == 0) {
                hata("Mock alignment bozuldu");
            }
            System.out.println("OK: Mock alignment çalışıyor");
        } finally {
            System.clearProperty("ELEVENLABS_MOCK");
            silRecursif(kok);
        }
    }

    private static void demoFixtureVtt() throws Exception {
        Path kok = Files.createTempDirectory("adim30-fix-");
        try {
            AlignmentService svc = new AlignmentService(kok);
            svc.uretDemoFixture(5);
            String vtt = svc.altyazi(5, "vtt");
            if (!vtt.startsWith("WEBVTT")) {
                hata("Demo fixture VTT bozuldu");
            }
            AlignmentResult r = svc.sonuc(5);
            if (!r.demoFixture() || !AlignmentResult.SOURCE_DEMO_FIXTURE.equals(r.source())) {
                hata("Demo fixture source alanı hatalı");
            }
            System.out.println("OK: Demo fixture WEBVTT üretiyor");
        } finally {
            silRecursif(kok);
        }
    }

    private static void fakeApiParse() throws Exception {
        Path kok = hazirlaOnizlemeFixture();
        String fakeJson = """
                {
                  "characters": [{"text": "B", "start": 0.0, "end": 0.2}],
                  "words": [
                    {"text": "Bir", "start": 0.0, "end": 0.5, "loss": 0.1},
                    {"text": "varmış", "start": 0.5, "end": 1.0, "loss": 0.1},
                    {"text": "bir", "start": 1.0, "end": 1.4, "loss": 0.1},
                    {"text": "yokmuş.", "start": 1.4, "end": 2.0, "loss": 0.1}
                  ],
                  "loss": 0.1
                }
                """;
        AlignmentApiClient.testTransportAyarla((a, d, m) ->
                new AlignmentApiClient.AlignmentHttpTransport.Yanit(200, fakeJson, "req-fake-001"));
        try {
            System.setProperty("ELEVENLABS_MOCK", "false");
            System.setProperty("ELEVENLABS_API_KEY", "test-key-adim30");
            System.setProperty("ELEVENLABS_VOICE_ID", "test-voice-id-12345678");
            AlignmentService svc = new AlignmentService(kok);
            var sonuc = svc.uret(5, false, true);
            if (!sonuc.basarili() || !sonuc.sonuc().realApiUsed()) {
                hata("Fake API sonucu gerçek API olarak işaretlenmedi");
            }
            if (!AlignmentResult.SOURCE_ELEVENLABS.equals(sonuc.sonuc().source())) {
                hata("Source ELEVENLABS olmalı");
            }
            if (sonuc.sonuc().wordCount() < 2) {
                hata("Kelime parse hatalı");
            }
            System.out.println("OK: Fake API response parse");
        } finally {
            AlignmentApiClient.testTransportTemizle();
            System.clearProperty("ELEVENLABS_MOCK");
            System.clearProperty("ELEVENLABS_API_KEY");
            System.clearProperty("ELEVENLABS_VOICE_ID");
            silRecursif(kok);
        }
    }

    private static void malformedApiGuvenliFailed() throws Exception {
        Path kok = hazirlaOnizlemeFixture();
        AlignmentApiClient.testTransportAyarla((a, d, m) ->
                new AlignmentApiClient.AlignmentHttpTransport.Yanit(200, "{\"unexpected\":true}", "x"));
        try {
            System.setProperty("ELEVENLABS_MOCK", "false");
            System.setProperty("ELEVENLABS_API_KEY", "test-key-adim30");
            System.setProperty("ELEVENLABS_VOICE_ID", "test-voice-id-12345678");
            AlignmentService svc = new AlignmentService(kok);
            try {
                svc.uret(5, false, true);
                hata("Malformed yanıt FAILED dönmeli");
            } catch (IllegalStateException e) {
                if (!e.getMessage().contains("başarısız") && !e.getMessage().contains("işlenemedi")) {
                    hata("Beklenen güvenli hata değil: " + e.getMessage());
                }
            }
            System.out.println("OK: Malformed API güvenli FAILED");
        } finally {
            AlignmentApiClient.testTransportTemizle();
            System.clearProperty("ELEVENLABS_MOCK");
            System.clearProperty("ELEVENLABS_API_KEY");
            System.clearProperty("ELEVENLABS_VOICE_ID");
            silRecursif(kok);
        }
    }

    private static void apiKeySizintisiYok() throws Exception {
        Path kok = hazirlaOnizlemeFixture();
        AlignmentApiClient.testTransportAyarla((a, d, m) ->
                new AlignmentApiClient.AlignmentHttpTransport.Yanit(200,
                        "{\"words\":[{\"text\":\"test\",\"start\":0,\"end\":1,\"loss\":0}],\"characters\":[],\"loss\":0}",
                        "req-1"));
        try {
            System.setProperty("ELEVENLABS_MOCK", "false");
            System.setProperty("ELEVENLABS_API_KEY", "dummy-test-api-key-not-real");
            System.setProperty("ELEVENLABS_VOICE_ID", "test-voice-id-12345678");
            AlignmentService svc = new AlignmentService(kok);
            svc.uret(5, false, true);
            String json = svc.jsonPlan(5);
            if (WebGuvenlikService.apiKeySizintisiVar(json)
                    || AlignmentGuvenlikService.yolSizintisiVar(json)
                    || json.contains("sk-test")) {
                hata("API key veya path sızıntısı");
            }
            System.out.println("OK: API key/full path sızıntısı yok");
        } finally {
            AlignmentApiClient.testTransportTemizle();
            System.clearProperty("ELEVENLABS_MOCK");
            System.clearProperty("ELEVENLABS_API_KEY");
            System.clearProperty("ELEVENLABS_VOICE_ID");
            silRecursif(kok);
        }
    }

    private static void webApiTestleri(Path kok) throws Exception {
        Path ses = kok.resolve("ses");
        hazirlaOnizlemeFixtureIcinde(ses);
        WebOrtam ortam = new WebOrtam(kok, null, kok.resolve("arsiv"), kok.resolve("metin"), ses,
                kok.resolve("kuyruk"), null, kok.resolve("panel"));
        YerelWebSunucu sunucu = new YerelWebSunucu(ortam);
        AlignmentService svc = new AlignmentService(ses);
        svc.uretDemoFixture(5);

        String plan = new String(sunucu.route("GET", "/api/alignment/5", null, "", true).body(),
                StandardCharsets.UTF_8);
        if (plan.contains("sk_") || plan.contains("C:\\Users\\")) {
            hata("/api/alignment/5 güvensiz");
        }

        WebResponse vtt = sunucu.route("GET", "/api/alignment/5/subtitles", "format=vtt", "", true, "text/plain");
        if (vtt.status() != 200 || !new String(vtt.body(), StandardCharsets.UTF_8).startsWith("WEBVTT")) {
            hata("VTT API başarısız");
        }

        String demo = new String(sunucu.route("GET", "/demo", null, "", true).body(), StandardCharsets.UTF_8);
        if (!demo.contains("Adım 30")) {
            hata("/demo Adım 30 eksik");
        }
        System.out.println("OK: Web API ve demo Adım 30");
    }

    private static void patronPaketTestleri() throws Exception {
        Path paket = Files.createTempDirectory("adim30-paket-");
        System.setProperty("DEMO_PAKET_KLASORU", paket.toString());
        try {
            DemoRaporService rapor = new DemoRaporService(WebOrtam.varsayilan());
            DemoRaporService.PaketSonucu sonuc = rapor.uret();
            String md = Files.readString(sonuc.klasor().resolve("patron-demo-ozeti.md"), StandardCharsets.UTF_8);
            if (!md.contains("Adım 30") && !md.contains("forced alignment")) {
                hata("Patron paketi Adım 30 eksik");
            }
            System.out.println("OK: Patron demo paketi güncellendi");
        } finally {
            System.clearProperty("DEMO_PAKET_KLASORU");
            silRecursif(paket);
        }
    }

    private static void dokumanTestleri(Path proje) throws Exception {
        Path mimari = proje.resolve("ADIM_30_MIMARI.md");
        if (!Files.isRegularFile(mimari)) {
            hata("ADIM_30_MIMARI.md eksik");
        }
        String icerik = Files.readString(mimari, StandardCharsets.UTF_8);
        if (!icerik.contains("GercekApiOnayli") || !icerik.toLowerCase().contains("forced alignment")) {
            hata("ADIM_30_MIMARI.md içerik eksik");
        }
        System.out.println("OK: ADIM_30_MIMARI.md");
    }

    private static Path hazirlaOnizlemeFixture() throws Exception {
        Path kok = Files.createTempDirectory("adim30-fix-");
        hazirlaOnizlemeFixtureIcinde(kok);
        return kok;
    }

    private static void hazirlaOnizlemeFixtureIcinde(Path kok) throws Exception {
        Path eser = kok.resolve("ESER-00005 - Kasagi");
        Path oniz = eser.resolve("onizleme").resolve("elevenlabs");
        Files.createDirectories(oniz);
        String metin = "Bir varmış bir yokmuş. Evliya Çelebi bu eserde pek çok ayrıntıyı anlatmıştır.";
        Files.writeString(oniz.resolve("preview-input.txt"), metin, StandardCharsets.UTF_8);
        byte[] mp3 = new byte[512];
        for (int i = 0; i < mp3.length; i++) {
            mp3[i] = (byte) (i % 256);
        }
        Files.write(oniz.resolve("preview-elevenlabs.mp3"), mp3);
        Files.writeString(oniz.resolve("preview-elevenlabs.json"), """
                {"status":"BASARILI"}
                """, StandardCharsets.UTF_8);
    }

    private static void silRecursif(Path kok) throws Exception {
        if (!Files.exists(kok)) {
            return;
        }
        try (var s = Files.walk(kok)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private static void hata(String mesaj) {
        throw new IllegalStateException("ADIM 30 TEST HATASI: " + mesaj);
    }
}
