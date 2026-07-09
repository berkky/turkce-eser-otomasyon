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

/**
 * Adım 26 yerel web MVP doğrulamaları — gerçek ElevenLabs API gerektirmez.
 */
public final class Adim26Dogrulama {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        utf8BaslikTesti();

        Path kok = Files.createTempDirectory("adim26-web-");
        Path metin = kok.resolve("metin-arsivi");
        Path ses = kok.resolve("ses-arsivi");
        Path panel = kok.resolve("kalite-panel");
        Path kuyruk = kok.resolve("kuyruk");
        Path arsiv = kok.resolve("arsiv");

        try {
            hazirlaFixture(metin, ses, panel);
            WebOrtam ortam = new WebOrtam(kok, null, arsiv, metin, ses, kuyruk, null, panel);
            YerelWebSunucu sunucu = new YerelWebSunucu(ortam);

            servisRouteTestleri(sunucu, ortam);
            guvenlikTestleri(sunucu);

            sunucu.baslat(0);
            try {
                httpTestleri(sunucu.port());
            } finally {
                sunucu.durdur();
            }
            if (sunucu.calisiyor()) {
                hata("HttpServer test sonrası kapanmadı");
            }
            System.out.println("OK: HttpServer ve executor kapatıldı");

            gercekArsivTestleri();

            System.out.println("ADIM 26 DOĞRULAMA: BAŞARILI");
        } finally {
            silRecursif(kok);
        }
    }

    private static void hazirlaFixture(Path metin, Path ses, Path panel) throws Exception {
        Path kasagi = metin.resolve("ESER-00005 - Kasagi - Vikikaynak");
        Path astronomi = metin.resolve("ESER-00006 - Astronomi Alfa Yayinlari");
        Files.createDirectories(kasagi.resolve("tts-parcalari"));
        Files.createDirectories(astronomi.resolve("tts-parcalari"));
        Files.writeString(astronomi.resolve("tts-parcalari").resolve("001-001.txt"),
                "Astronomi demo parça.", StandardCharsets.UTF_8);
        Files.writeString(kasagi.resolve("alim-manifest.json"), """
                {"eserAdi":"Kaşağı - Vikikaynak","metadataDurumu":"HAZIR","toplamKarakter":1200,"bolumSayisi":3,
                "metadata":{"yazar":"Test Yazar","yayinevi":"Test","isbn":"978-0","guvenPuani":0.9}}
                """, StandardCharsets.UTF_8);
        Files.writeString(astronomi.resolve("alim-manifest.json"), """
                {"eserAdi":"Astronomi Alfa Yayınları","metadataDurumu":"KONTROL_GEREKIYOR","toplamKarakter":500000,"bolumSayisi":12,
                "metadata":{"yazar":"Astronom","yayinevi":"Alfa","isbn":"978-1","guvenPuani":0.7}}
                """, StandardCharsets.UTF_8);
        SesKalitePanelDemoService.demoVerisiOlustur(ses);
        Files.createDirectories(panel);
    }

    private static void servisRouteTestleri(YerelWebSunucu sunucu, WebOrtam ortam) throws Exception {
        WebResponse ana = sunucu.route("GET", "/", null, "", true);
        if (ana.status() != 200 || !new String(ana.body(), StandardCharsets.UTF_8).contains("Kontrol Paneli")) {
            hata("Dashboard render edilmedi");
        }
        System.out.println("OK: Dashboard render");

        WebResponse eserler = sunucu.route("GET", "/eserler", null, "", true);
        String eserHtml = new String(eserler.body(), StandardCharsets.UTF_8);
        if (!eserHtml.contains("ESER-00005") || !eserHtml.contains("ESER-00006")) {
            hata("Eser listesinde ESER-00005/00006 yok");
        }
        System.out.println("OK: Eser listesi ESER-00005 ve ESER-00006");

        WebResponse apiEserler = sunucu.route("GET", "/api/eserler", null, "", true);
        String eserJson = new String(apiEserler.body(), StandardCharsets.UTF_8);
        ArrayNode dizi = (ArrayNode) JSON.readTree(eserJson);
        boolean kasagi = false, astronomi = false, astronomiBuyuk = false;
        for (JsonNode n : dizi) {
            int id = n.path("eserId").asInt();
            if (id == 5) kasagi = true;
            if (id == 6) {
                astronomi = true;
                astronomiBuyuk = n.path("buyukEser").asBoolean(false);
            }
        }
        if (!kasagi || !astronomi) {
            hata("/api/eserler içinde ESER-00005 veya ESER-00006 yok");
        }
        if (!astronomiBuyuk) {
            hata("ESER-00006 büyük eser olarak işaretlenmedi");
        }
        System.out.println("OK: /api/eserler — ESER-00006 büyük eser");

        WebResponse status = sunucu.route("GET", "/api/status", null, "", true);
        String statusJson = new String(status.body(), StandardCharsets.UTF_8);
        if (WebGuvenlikService.apiKeySizintisiVar(statusJson)) {
            hata("/api/status API key sızıntısı");
        }
        System.out.println("OK: /api/status güvenli JSON");

        WebResponse kalite = sunucu.route("GET", "/kalite", null, "", true);
        if (kalite.status() != 200) {
            hata("Kalite paneli route çalışmadı");
        }
        System.out.println("OK: Kalite panel route");

        WebResponse detay = sunucu.route("GET", "/eser/6", null, "", true);
        String detayHtml = new String(detay.body(), StandardCharsets.UTF_8);
        if (!detayHtml.contains("Büyük eser")) {
            hata("Eser detay büyük eser uyarısı eksik");
        }
        System.out.println("OK: Eser detay sayfası");

        WebResponse css = sunucu.route("GET", "/assets/app.css", null, "", true);
        if (css.status() != 200) {
            hata("Statik CSS serve edilmedi");
        }
        System.out.println("OK: Statik varlıklar");
    }

    private static void guvenlikTestleri(YerelWebSunucu sunucu) throws Exception {
        WebResponse traversal = sunucu.route("GET", "/assets/../../pom.xml", null, "", true);
        if (traversal.status() == 200) {
            hata("Path traversal engellenmedi");
        }
        System.out.println("OK: Path traversal reddedildi");

        WebResponse sifirByte = sunucu.route("GET", "/media/preview/olmayan-id-000", null, "", true);
        if (sifirByte.status() == 200) {
            hata("Geçersiz media id serve edildi");
        }
        Path sifirMp3 = Files.createTempFile("adim26-sifir", ".mp3");
        try {
            WebResponse dosya = WebStaticAssetService.dosyaSun(sifirMp3.getParent(), sifirMp3);
            if (dosya.status() == 200) {
                hata("0 byte media serve edildi");
            }
        } finally {
            Files.deleteIfExists(sifirMp3);
        }
        System.out.println("OK: 0 byte media reddedildi");

        WebResponse disErisim = sunucu.route("GET", "/", null, "", false);
        if (disErisim.status() != 403) {
            hata("Non-localhost erişim engellenmedi");
        }
        System.out.println("OK: Non-localhost reddedildi");

        try {
            WebGuvenlikService.guvenliJson("{\"key\":\"sk-abcdefghijklmnop\"}");
            hata("API key pattern filtresi çalışmadı");
        } catch (WebGuvenlikService.GuvenlikIstisnasi ignored) {
        }
        System.out.println("OK: API key sızıntı filtresi");
    }

    private static void httpTestleri(int port) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI base = URI.create("http://127.0.0.1:" + port);

        HttpResponse<String> ana = client.send(
                HttpRequest.newBuilder(base).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (ana.statusCode() != 200 || !ana.body().contains("Türkçe Eser Otomasyonu")) {
            hata("HTTP dashboard başarısız");
        }

        HttpResponse<String> api = client.send(
                HttpRequest.newBuilder(base.resolve("/api/kalite")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (api.statusCode() != 200 || WebGuvenlikService.apiKeySizintisiVar(api.body())) {
            hata("/api/kalite HTTP testi başarısız");
        }

        System.out.println("OK: HTTP sunucu port " + port);
    }

    private static void gercekArsivTestleri() throws Exception {
        WebOrtam ortam = WebOrtam.varsayilan();
        if (!Files.isDirectory(ortam.metinArsivi())) {
            System.out.println("NOT: Gerçek metin arşivi yok — atlandı");
            return;
        }
        WebEserService svc = new WebEserService(ortam);
        boolean kasagi = false, astronomi = false, buyuk = false;
        for (WebEserService.WebEserOzeti e : svc.eserleriListele()) {
            if (e.eserId() == 5) kasagi = true;
            if (e.eserId() == 6) {
                astronomi = true;
                buyuk = e.buyukEser();
            }
        }
        if (kasagi && astronomi) {
            if (!buyuk) {
                hata("Gerçek arşivde ESER-00006 büyük eser değil");
            }
            System.out.println("OK: Gerçek arşiv ESER-00005 ve ESER-00006");
        } else {
            System.out.println("NOT: Gerçek arşivde ESER klasörleri eksik — fixture testleri yeterli");
        }
    }

    private static void utf8BaslikTesti() {
        String baslik = "YEREL WEB MVP KONTROL PANELİ — GÜVENLİ İŞLEMLER";
        if (!baslik.contains("İ") || !baslik.contains("Ü") || !baslik.contains("Ş")) {
            hata("UTF-8 başlık testi başarısız");
        }
        System.out.println("OK: UTF-8 Türkçe başlık — " + baslik);
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
        throw new IllegalStateException("ADIM 26 TEST HATASI: " + mesaj);
    }
}
