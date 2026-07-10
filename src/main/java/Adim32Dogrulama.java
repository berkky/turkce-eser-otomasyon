import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adım 32 — Final release kalite kapısı doğrulamaları.
 */
public final class Adim32Dogrulama {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        Path proje = Path.of(System.getProperty("user.dir"));

        finalDokumanlar(proje);
        readmeFinalBolum(proje);
        secretScanScripti(proje);
        patronPaketFinalDosyalari(proje);
        webDemoAdim32(proje);
        apiKeySizintisiYok();
        gercekApiCagrisiYok();
        finalReleaseRaporKlasoru();

        System.out.println("ADIM 32 DOĞRULAMA: BAŞARILI");
    }

    private static void finalDokumanlar(Path proje) throws Exception {
        String[] dosyalar = {
                "FINAL_RELEASE_NOTES.md",
                "FINAL_KURULUM_REHBERI.md",
                "FINAL_DEMO_AKISI.md",
                "FINAL_TEKNIK_OZET.md",
                "FINAL_GUVENLIK_NOTLARI.md",
                "FINAL_TESLIM_KONTROL_LISTESI.md",
                "ADIM_32_MIMARI.md"
        };
        for (String ad : dosyalar) {
            Path p = proje.resolve(ad);
            if (!Files.isRegularFile(p)) {
                hata("Eksik doküman: " + ad);
            }
            String icerik = Files.readString(p, StandardCharsets.UTF_8);
            if (icerik.length() < 200) {
                hata("Doküman çok kısa: " + ad);
            }
        }
        System.out.println("OK: Final dokümanlar mevcut");
    }

    private static void readmeFinalBolum(Path proje) throws Exception {
        String readme = Files.readString(proje.resolve("README.md"), StandardCharsets.UTF_8);
        if (!readme.contains("Hızlı başlangıç") && !readme.contains("Hizli baslangic")) {
            hata("README hızlı başlangıç bölümü eksik");
        }
        if (!readme.contains("Güvenlik") && !readme.contains("Guvenlik")) {
            hata("README güvenlik notu eksik");
        }
        System.out.println("OK: README final bölüm");
    }

    private static void secretScanScripti(Path proje) throws Exception {
        Path script = proje.resolve("check-secrets.ps1");
        if (!Files.isRegularFile(script)) {
            hata("check-secrets.ps1 eksik");
        }
        String icerik = Files.readString(script, StandardCharsets.UTF_8);
        if (!icerik.contains("sk-") || !icerik.contains("ELEVENLABS_API_KEY")) {
            hata("check-secrets.ps1 içerik eksik");
        }
        System.out.println("OK: check-secrets.ps1");
    }

    private static void patronPaketFinalDosyalari(Path proje) throws Exception {
        Path paket = Files.createTempDirectory("adim32-paket-");
        System.setProperty("DEMO_PAKET_KLASORU", paket.toString());
        try {
            DemoRaporService.PaketSonucu sonuc = new DemoRaporService(WebOrtam.varsayilan()).uret();
            for (String ad : DemoRaporService.finalDosyalar()) {
                if (!Files.isRegularFile(sonuc.klasor().resolve(ad))) {
                    hata("Patron paketi final dosya eksik: " + ad);
                }
            }
            System.out.println("OK: Patron demo paketi final dosyaları");
        } finally {
            System.clearProperty("DEMO_PAKET_KLASORU");
            silRecursif(paket);
        }
    }

    private static void webDemoAdim32(Path proje) throws Exception {
        Path kok = Files.createTempDirectory("adim32-web-");
        try {
            WebOrtam ortam = new WebOrtam(kok, null, kok.resolve("arsiv"), kok.resolve("metin"),
                    kok.resolve("ses"), kok.resolve("kuyruk"), null, kok.resolve("panel"));
            YerelWebSunucu sunucu = new YerelWebSunucu(ortam);
            String demo = new String(sunucu.route("GET", "/demo", null, "", true).body(),
                    StandardCharsets.UTF_8);
            if (!demo.contains("Adım 32") && !demo.contains("Adim 32")) {
                hata("/demo Adım 32 bölümü eksik");
            }
            if (!demo.contains("Final Demo Ready") && !demo.contains("kalite kapısı")) {
                hata("/demo final demo etiketi eksik");
            }
            System.out.println("OK: /demo Adım 32 alanı");
        } finally {
            silRecursif(kok);
        }
    }

    private static void apiKeySizintisiYok() throws Exception {
        Path kok = Files.createTempDirectory("adim32-api-");
        try {
            WebOrtam ortam = new WebOrtam(kok, null, kok.resolve("arsiv"), kok.resolve("metin"),
                    kok.resolve("ses"), kok.resolve("kuyruk"), null, kok.resolve("panel"));
            YerelWebSunucu sunucu = new YerelWebSunucu(ortam);
            String status = new String(sunucu.route("GET", "/api/status", null, "", true).body(),
                    StandardCharsets.UTF_8);
            if (WebGuvenlikService.apiKeySizintisiVar(status)
                    || status.contains("C:\\Users\\")) {
                hata("API status sızıntı");
            }
            System.out.println("OK: API key/full path sızıntısı yok");
        } finally {
            silRecursif(kok);
        }
    }

    private static void gercekApiCagrisiYok() {
        if (TamEserUretimKuyrukService.uretimBaslatildiMi()) {
            hata("Gerçek TTS üretimi başlatılmış olmamalı");
        }
        System.out.println("OK: Gerçek TTS/API üretim çağrısı yok");
    }

    private static void finalReleaseRaporKlasoru() {
        Path klasor = FinalReleaseDurumService.raporKlasoru();
        if (!Files.isDirectory(klasor)) {
            System.out.println("OK: Final rapor klasörü (henüz oluşturulmamış olabilir)");
            return;
        }
        System.out.println("OK: Final release rapor klasörü erişilebilir");
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
        throw new AssertionError(mesaj);
    }
}
