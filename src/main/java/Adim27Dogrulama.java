import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Adım 27 patron demo akışı doğrulamaları.
 */
public final class Adim27Dogrulama {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();

        Path kok = Files.createTempDirectory("adim27-demo-");
        Path metin = kok.resolve("metin");
        Path ses = kok.resolve("ses");
        Path panel = kok.resolve("panel");
        Path paket = kok.resolve("demo-paket");

        try {
            hazirlaFixture(metin, ses, panel);
            System.setProperty("DEMO_PAKET_KLASORU", paket.toString());

            WebOrtam ortam = new WebOrtam(kok, null, kok.resolve("arsiv"), metin, ses, kok.resolve("kuyruk"), null, panel);
            YerelWebSunucu sunucu = new YerelWebSunucu(ortam);

            demoRouteTestleri(sunucu);
            apiTestleri(sunucu);
            senaryoRolTestleri();
            guvenlikTestleri(sunucu);

            sunucu.baslat(0);
            try {
                httpTestleri(sunucu.port());
            } finally {
                sunucu.durdur();
            }

            paketTestleri(paket, ortam);
            dokumanTestleri(Path.of(System.getProperty("user.dir")));

            System.out.println("ADIM 27 DOĞRULAMA: BAŞARILI");
        } finally {
            System.clearProperty("DEMO_PAKET_KLASORU");
            silRecursif(kok);
        }
    }

    private static void hazirlaFixture(Path metin, Path ses, Path panel) throws Exception {
        Path kasagi = metin.resolve("ESER-00005 - Kasagi - Vikikaynak");
        Path astronomi = metin.resolve("ESER-00006 - Astronomi Alfa Yayinlari");
        Files.createDirectories(kasagi.resolve("tts-parcalari"));
        Files.createDirectories(astronomi.resolve("tts-parcalari"));
        Files.writeString(astronomi.resolve("tts-parcalari").resolve("001-001.txt"),
                "Astronomi demo.", StandardCharsets.UTF_8);
        Files.writeString(kasagi.resolve("alim-manifest.json"), """
                {"eserAdi":"Kaşağı - Vikikaynak","metadataDurumu":"HAZIR","toplamKarakter":1200,"bolumSayisi":3,
                "metadata":{"yazar":"Test","yayinevi":"Vikikaynak","isbn":"978-0","guvenPuani":0.9}}
                """, StandardCharsets.UTF_8);
        Files.writeString(astronomi.resolve("alim-manifest.json"), """
                {"eserAdi":"Astronomi Alfa Yayınları","metadataDurumu":"KONTROL_GEREKIYOR","toplamKarakter":500000,"bolumSayisi":12,
                "metadata":{"yazar":"Test","yayinevi":"Alfa","isbn":"978-1","guvenPuani":0.7}}
                """, StandardCharsets.UTF_8);
        SesKalitePanelDemoService.demoVerisiOlustur(ses);
        Files.createDirectories(panel);
    }

    private static void demoRouteTestleri(YerelWebSunucu sunucu) throws Exception {
        WebResponse demo = sunucu.route("GET", "/demo", null, "", true);
        String html = new String(demo.body(), StandardCharsets.UTF_8);
        if (demo.status() != 200 || !html.contains("Türkçe Eser Otomasyonu")) {
            hata("/demo render edilmedi");
        }
        System.out.println("OK: UTF-8 Türkçe — demo sayfası");
        if (!html.contains("Demo İlerleme Akışı") || !html.contains("Önce / Sonra")) {
            hata("Demo sayfası bölümleri eksik");
        }
        if (!html.contains("github.com/berkky/turkce-eser-otomasyon")) {
            hata("GitHub linki eksik");
        }
        int adimSayisi = html.split("tl-step").length - 1;
        if (adimSayisi != 7) {
            hata("Timeline 7 adım içermiyor: " + adimSayisi);
        }
        System.out.println("OK: /demo render — 7 adımlı timeline");

        WebResponse paket = sunucu.route("GET", "/demo/paket", null, "", true);
        if (paket.status() != 200) {
            hata("/demo/paket çalışmadı");
        }
        System.out.println("OK: /demo/paket route");

        WebResponse eser5 = sunucu.route("GET", "/eser/5", null, "", true);
        String e5 = new String(eser5.body(), StandardCharsets.UTF_8);
        if (!e5.contains("Demo Eseri") || !e5.contains("ElevenLabs")) {
            hata("ESER-00005 demo bölümü eksik");
        }
        WebResponse eser6 = sunucu.route("GET", "/eser/6", null, "", true);
        String e6 = new String(eser6.body(), StandardCharsets.UTF_8);
        if (!e6.contains("Demo Eseri") || !e6.contains("engelli")) {
            hata("ESER-00006 demo bölümü eksik");
        }
        System.out.println("OK: Eser detay demo iyileştirmesi");
    }

    private static void apiTestleri(YerelWebSunucu sunucu) throws Exception {
        for (String path : List.of("/api/demo", "/api/demo/metrikler", "/api/demo/akis")) {
            WebResponse r = sunucu.route("GET", path, null, "", true);
            String body = new String(r.body(), StandardCharsets.UTF_8);
            if (r.status() != 200 || WebGuvenlikService.apiKeySizintisiVar(body)) {
                hata(path + " başarısız veya API key sızıntısı");
            }
        }
        String akis = new String(sunucu.route("GET", "/api/demo/akis", null, "", true).body(), StandardCharsets.UTF_8);
        ArrayNode dizi = (ArrayNode) JSON.readTree(akis);
        if (dizi.size() != 7) {
            hata("/api/demo/akis 7 adım değil");
        }
        System.out.println("OK: Demo API endpointleri");
    }

    private static void senaryoRolTestleri() {
        DemoSenaryo k = DemoSenaryo.kasagi();
        DemoSenaryo a = DemoSenaryo.astronomi();
        if (k.eserId() != 5 || k.buyukEser()) {
            hata("ESER-00005 demo rolü yanlış");
        }
        if (a.eserId() != 6 || !a.buyukEser()) {
            hata("ESER-00006 büyük eser demo rolü yanlış");
        }
        if (!k.demoRolu().contains("ElevenLabs") || !a.demoRolu().contains("Archive")) {
            hata("Demo rol açıklamaları eksik");
        }
        System.out.println("OK: ESER-00005 ve ESER-00006 demo rolleri");
    }

    private static void guvenlikTestleri(YerelWebSunucu sunucu) throws Exception {
        String demo = new String(sunucu.route("GET", "/demo", null, "", true).body(), StandardCharsets.UTF_8);
        if (!demo.contains("gerçek TTS üretimi başlatılmaz")) {
            hata("Demo güvenlik uyarısı eksik");
        }
        WebResponse post = sunucu.route("POST", "/api/demo", null, "", true);
        if (post.status() != 405 && post.status() != 404) {
            hata("Riskli POST /api/demo engellenmedi");
        }
        System.out.println("OK: Demo güvenlik uyarıları ve riskli endpoint yok");
    }

    private static void httpTestleri(int port) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> r = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/demo")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() != 200 || !r.body().contains("SUNUM MODU")) {
            hata("HTTP /demo başarısız");
        }
        System.out.println("OK: HTTP demo port " + port);
    }

    private static void paketTestleri(Path paket, WebOrtam ortam) throws Exception {
        System.setProperty("DEMO_PAKET_KLASORU", paket.toString());
        DemoRaporService rapor = new DemoRaporService(ortam);
        DemoRaporService.PaketSonucu sonuc = rapor.uret();

        for (String dosya : DemoRaporService.beklenenDosyalar()) {
            if (!Files.isRegularFile(sonuc.klasor().resolve(dosya))) {
                hata("Patron paketi eksik: " + dosya);
            }
        }
        String html = Files.readString(sonuc.klasor().resolve("patron-demo-ozeti.html"), StandardCharsets.UTF_8);
        if (!html.contains("Türkçe Eser Otomasyonu")) {
            hata("patron-demo-ozeti.html eksik");
        }
        String akis = Files.readString(sonuc.klasor().resolve("demo-akisi.md"), StandardCharsets.UTF_8);
        if (!akis.contains("Demo Akışı")) {
            hata("demo-akisi.md eksik");
        }
        System.out.println("OK: Patron demo paketi — " + sonuc.klasor());
    }

    private static void dokumanTestleri(Path proje) throws Exception {
        for (String ad : List.of("DEMO_SENARYOSU.md", "IS_MODELI_NOTU.md", "ADIM_27_MIMARI.md")) {
            Path dosya = proje.resolve(ad);
            if (!Files.isRegularFile(dosya)) {
                hata("Doküman eksik: " + ad);
            }
            String icerik = Files.readString(dosya, StandardCharsets.UTF_8);
            if (icerik.length() < 200) {
                hata("Doküman çok kısa: " + ad);
            }
        }
        System.out.println("OK: DEMO_SENARYOSU, IS_MODELI_NOTU, ADIM_27_MIMARI");
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
        throw new IllegalStateException("ADIM 27 TEST HATASI: " + mesaj);
    }
}
