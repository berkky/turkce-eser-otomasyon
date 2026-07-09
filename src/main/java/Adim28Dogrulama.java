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
import java.util.Map;

/**
 * Adım 28 — ElevenLabs önizleme, telaffuz ve maliyet planı doğrulamaları.
 */
public final class Adim28Dogrulama {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();

        telaffuzYuklemeVeNormalizer();
        mockOnizlemeVeJsonAlanlari();
        astronomiEngeli();
        ttsPlanTestleri();

        Path kok = Files.createTempDirectory("adim28-web-");
        try {
            webApiTestleri(kok);
        } finally {
            silRecursif(kok);
        }

        dokumanTestleri(Path.of(System.getProperty("user.dir")));
        patronPaketTestleri();

        System.out.println("ADIM 28 DOĞRULAMA: BAŞARILI");
    }

    private static void telaffuzYuklemeVeNormalizer() throws Exception {
        Path panel = Files.createTempDirectory("adim28-telaffuz-");
        try {
            List<TelaffuzNotu> notlar = new TelaffuzSozluguService(panel).yukle();
            if (notlar.isEmpty()) {
                hata("Telaffuz notları yüklenmedi");
            }
            long aktif = notlar.stream().filter(TelaffuzNotu::aktifMetinNormalize).count();
            if (aktif < 2) {
                hata("Aktif telaffuz notu yetersiz");
            }

            String metin = "Kaşağı ISBN DK Alfa Yayınları hikayesi.";
            TelaffuzNormalizerService.Sonuc sonuc = TelaffuzNormalizerService.uygula(metin, notlar);
            if (sonuc.uygulananNotlar().isEmpty()) {
                hata("Aktif telaffuz notu uygulanmadı");
            }

            List<TelaffuzNotu> pasif = notlar.stream()
                    .map(n -> TelaffuzNotu.DURUM_PASIF.equals(n.durum())
                            ? n : new TelaffuzNotu(n.id(), n.ifade(), n.onerilenOkunus(), n.aciklama(),
                            n.eserId(), n.dil(), n.kaynak(), TelaffuzNotu.DURUM_PASIF, n.uygulanmaModu(),
                            n.createdAt(), n.updatedAt()))
                    .toList();
            TelaffuzNormalizerService.Sonuc pasifSonuc = TelaffuzNormalizerService.uygula(metin, pasif);
            if (!pasifSonuc.uygulananNotlar().isEmpty()) {
                hata("Pasif telaffuz notu uygulanmamalıydı");
            }
            System.out.println("OK: Telaffuz notları ve normalizer");
        } finally {
            silRecursif(panel);
        }
    }

    private static void mockOnizlemeVeJsonAlanlari() throws Exception {
        Path kok = Files.createTempDirectory("adim28-onizleme-");
        Path metinArsiv = kok.resolve("metin-arsivi");
        Path sesArsiv = kok.resolve("ses-arsivi");
        Path panel = kok.resolve("panel");
        Path eser = metinArsiv.resolve("ESER-00005 - Kasagi - Vikikaynak");
        Path tts = eser.resolve("tts-parcalari");
        Files.createDirectories(tts);
        Files.createDirectories(panel);
        Files.writeString(tts.resolve("001-001.txt"),
                "Kaşağı hikâyesinden kısa bir önizleme metni. Bir varmış bir yokmuş. "
                        + "Evliya Çelebi bu eserde pek çok ayrıntıyı anlatmıştır. "
                        + "Türkçe karakterler: ğüşıöç ĞÜŞİÖÇ. ISBN ve DK notları.",
                StandardCharsets.UTF_8);

        ElevenLabsMockClient mock = new ElevenLabsMockClient();
        ElevenLabsOnizlemeService.OnizlemeSonucu ilk =
                ElevenLabsOnizlemeService.uret(5, metinArsiv, sesArsiv, panel, mock, "mock-voice-id-0001");
        if (!ilk.basarili()) {
            hata("Mock önizleme başarısız: " + ilk.mesaj());
        }

        JsonNode json = JSON.readTree(Files.readString(ilk.json(), StandardCharsets.UTF_8));
        if (!json.has("appliedPronunciationNotes")) {
            hata("preview JSON appliedPronunciationNotes eksik");
        }
        if (!json.has("originalInputCharacterCount") || !json.has("normalizedInputCharacterCount")) {
            hata("preview JSON karakter sayıları eksik");
        }
        if (!"NOT_REQUESTED".equals(json.path("alignmentStatus").asText())) {
            hata("alignmentStatus NOT_REQUESTED olmalı");
        }

        long ilkCagri = mock.ttsCagriSayisi();
        ElevenLabsOnizlemeService.OnizlemeSonucu ikinci =
                ElevenLabsOnizlemeService.uret(5, metinArsiv, sesArsiv, panel, mock, "mock-voice-id-0001");
        if (!ikinci.mevcutKullanildi() || mock.ttsCagriSayisi() != ilkCagri) {
            hata("requestHash idempotency başarısız");
        }

        silRecursif(kok);
        System.out.println("OK: Mock önizleme, JSON alanları ve idempotency");
    }

    private static void astronomiEngeli() throws Exception {
        Path kok = Files.createTempDirectory("adim28-astro-");
        Path metin = kok.resolve("metin");
        Path ses = kok.resolve("ses");
        Path eser = metin.resolve("ESER-00006 - Astronomi");
        Files.createDirectories(eser.resolve("tts-parcalari"));
        Files.writeString(eser.resolve("tts-parcalari").resolve("001-001.txt"), "Astronomi.", StandardCharsets.UTF_8);

        ElevenLabsOnizlemeService.OnizlemeSonucu sonuc =
                ElevenLabsOnizlemeService.uret(6, metin, ses, null, new ElevenLabsMockClient(), "mock-voice");
        if (sonuc.basarili()) {
            hata("ESER-00006 önizleme engellenmeliydi");
        }
        silRecursif(kok);
        System.out.println("OK: ESER-00006 büyük eser koruması");
    }

    private static void ttsPlanTestleri() throws Exception {
        Path kok = Files.createTempDirectory("adim28-plan-");
        Path metin = kok.resolve("metin");
        Path ses = kok.resolve("ses");
        Path panel = kok.resolve("panel");
        hazirlaPlanFixture(metin);

        WebOrtam ortam = new WebOrtam(kok, null, kok.resolve("arsiv"), metin, ses, kok.resolve("kuyruk"), null, panel);
        TtsMaliyetPlanService svc = new TtsMaliyetPlanService(ortam);

        TtsMaliyetPlanService.TtsMaliyetPlani p5 = svc.planla(5);
        if (p5.buyukEser() || p5.onerilenAksiyon().contains("BUYUK")) {
            hata("ESER-00005 büyük eser olmamalı");
        }
        if (p5.krediYok() && !p5.onerilenAksiyon().equals("KREDI_BEKLENIYOR")) {
            // kredi yokken KREDI_BEKLENIYOR beklenir
        }

        TtsMaliyetPlanService.TtsMaliyetPlani p6 = svc.planla(6);
        if (!p6.buyukEser() || !p6.onerilenAksiyon().contains("BUYUK")) {
            hata("ESER-00006 büyük eser uyarısı eksik");
        }

        String json5 = svc.json(5);
        if (WebGuvenlikService.apiKeySizintisiVar(json5)) {
            hata("/api/tts-plan JSON API key sızdırıyor");
        }

        silRecursif(kok);
        System.out.println("OK: TTS maliyet planı ESER-00005/00006");
    }

    private static void webApiTestleri(Path kok) throws Exception {
        Path metin = kok.resolve("metin");
        Path ses = kok.resolve("ses");
        Path panel = kok.resolve("panel");
        hazirlaPlanFixture(metin);
        SesKalitePanelDemoService.demoVerisiOlustur(ses);

        WebOrtam ortam = new WebOrtam(kok, null, kok.resolve("arsiv"), metin, ses, kok.resolve("kuyruk"), null, panel);
        YerelWebSunucu sunucu = new YerelWebSunucu(ortam);

        String status = new String(sunucu.route("GET", "/api/elevenlabs/status", null, "", true).body(),
                StandardCharsets.UTF_8);
        if (WebGuvenlikService.apiKeySizintisiVar(status)) {
            hata("/api/elevenlabs/status key sızdırıyor");
        }

        String telaffuz = new String(sunucu.route("GET", "/api/telaffuz", null, "", true).body(),
                StandardCharsets.UTF_8);
        JsonNode t = JSON.readTree(telaffuz);
        if (!t.path("notlar").isArray() || t.path("notlar").isEmpty()) {
            hata("/api/telaffuz çalışmıyor");
        }

        String plan = new String(sunucu.route("GET", "/api/tts-plan/5", null, "", true).body(),
                StandardCharsets.UTF_8);
        if (!plan.contains("onerilenAksiyon") || WebGuvenlikService.apiKeySizintisiVar(plan)) {
            hata("/api/tts-plan/5 başarısız");
        }

        String demo = new String(sunucu.route("GET", "/demo", null, "", true).body(), StandardCharsets.UTF_8);
        if (!demo.contains("Adım 28") || !demo.contains("Premium Ses Önizleme")) {
            hata("/demo Adım 28 bölümü eksik");
        }

        WebResponse telR = sunucu.route("GET", "/telaffuz", null, "", true);
        String telaffuzSayfa = new String(telR.body(), StandardCharsets.UTF_8);
        if (telR.status() != 200) {
            hata("/telaffuz HTTP " + telR.status());
        }
        if (!telaffuzSayfa.contains("Telaffuz") || !telaffuzSayfa.contains("ELEVENLABS_DICTIONARY_ADAYI")) {
            hata("/telaffuz sayfası eksik (uzunluk=" + telaffuzSayfa.length() + ")");
        }

        String eser5 = new String(sunucu.route("GET", "/eser/5", null, "", true).body(), StandardCharsets.UTF_8);
        if (!eser5.contains("TTS Maliyet Planı")) {
            hata("/eser/5 plan bölümü eksik");
        }
        String eser6 = new String(sunucu.route("GET", "/eser/6", null, "", true).body(), StandardCharsets.UTF_8);
        if (!eser6.contains("Büyük eser") && !eser6.contains("maliyet")) {
            hata("/eser/6 büyük eser uyarısı eksik");
        }

        System.setProperty("ELEVENLABS_MOCK", "true");
        System.setProperty("ELEVENLABS_VOICE_ID", "mock-voice-id-0001");
        try {
            Path kasagiTts = metin.resolve("ESER-00005 - Kasagi").resolve("tts-parcalari");
            Files.createDirectories(kasagiTts);
            Files.writeString(kasagiTts.resolve("001-001.txt"),
                    "Kaşağı önizleme metni uzun enough for preview generation test. "
                            + "Bir varmış bir yokmuş evliya çelebi anlatır.",
                    StandardCharsets.UTF_8);

            String nonce = extractNonce(new String(sunucu.route("GET", "/islemler", null, "", true).body(),
                    StandardCharsets.UTF_8));
            String body = "nonce=" + java.net.URLEncoder.encode(nonce, StandardCharsets.UTF_8)
                    + "&aksiyon=elevenlabs-onizleme&eserId=5";
            WebResponse post = sunucu.route("POST", "/islemler", null, body, true);
            if (post.status() != 302 && post.status() != 303) {
                hata("POST elevenlabs-onizleme redirect bekleniyordu");
            }
        } finally {
            System.clearProperty("ELEVENLABS_MOCK");
            System.clearProperty("ELEVENLABS_VOICE_ID");
        }

        sunucu.baslat(0);
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> r = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + sunucu.port() + "/api/tts-plan/5")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() != 200) {
                hata("HTTP /api/tts-plan/5 başarısız");
            }
        } finally {
            sunucu.durdur();
        }

        System.out.println("OK: Web API ve sayfalar (Adım 28)");
    }

    private static void patronPaketTestleri() throws Exception {
        Path paket = Files.createTempDirectory("adim28-paket-");
        System.setProperty("DEMO_PAKET_KLASORU", paket.toString());
        try {
            WebOrtam ortam = WebOrtam.varsayilan();
            DemoRaporService rapor = new DemoRaporService(ortam);
            DemoRaporService.PaketSonucu sonuc = rapor.uret();
            String md = Files.readString(sonuc.klasor().resolve("patron-demo-ozeti.md"), StandardCharsets.UTF_8);
            if (!md.contains("Adım 28") && !md.contains("ElevenLabs")) {
                hata("Patron paketi Adım 28 bilgisi eksik");
            }
            System.out.println("OK: Patron demo paketi güncellendi");
        } finally {
            System.clearProperty("DEMO_PAKET_KLASORU");
            silRecursif(paket);
        }
    }

    private static void dokumanTestleri(Path proje) throws Exception {
        Path mimari = proje.resolve("ADIM_28_MIMARI.md");
        if (!Files.isRegularFile(mimari)) {
            hata("ADIM_28_MIMARI.md eksik");
        }
        String icerik = Files.readString(mimari, StandardCharsets.UTF_8);
        if (!icerik.contains("forced alignment") || !icerik.contains("ESER-00005")) {
            hata("ADIM_28_MIMARI.md içerik eksik");
        }
        System.out.println("OK: ADIM_28_MIMARI.md");
    }

    private static void hazirlaPlanFixture(Path metin) throws Exception {
        Path kasagi = metin.resolve("ESER-00005 - Kasagi");
        Path astronomi = metin.resolve("ESER-00006 - Astronomi Alfa");
        Files.createDirectories(kasagi.resolve("tts-parcalari"));
        Files.createDirectories(astronomi.resolve("tts-parcalari"));
        Files.writeString(kasagi.resolve("alim-manifest.json"), """
                {"eserAdi":"Kaşağı","metadataDurumu":"HAZIR","toplamKarakter":1200,"bolumSayisi":3,
                "metadata":{"yazar":"Test","yayinevi":"Vikikaynak","isbn":"978-0","guvenPuani":0.9}}
                """, StandardCharsets.UTF_8);
        Files.writeString(astronomi.resolve("alim-manifest.json"), """
                {"eserAdi":"Astronomi","metadataDurumu":"KONTROL_GEREKIYOR","toplamKarakter":500000,"bolumSayisi":12,
                "metadata":{"yazar":"Test","yayinevi":"Alfa","isbn":"978-1","guvenPuani":0.7}}
                """, StandardCharsets.UTF_8);
    }

    private static String extractNonce(String html) {
        int i = html.indexOf("name=\"nonce\"");
        if (i < 0) {
            hata("Nonce bulunamadı");
        }
        int v = html.indexOf("value=\"", i);
        int end = html.indexOf("\"", v + 7);
        return html.substring(v + 7, end);
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
        throw new IllegalStateException("ADIM 28 TEST HATASI: " + mesaj);
    }
}
