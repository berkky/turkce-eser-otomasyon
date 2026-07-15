import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Adım 33 — Final teslim paketi ve gönderim hazırlığı doğrulamaları.
 */
public final class Adim33Dogrulama {
    private static final Path TESLIM_KLASOR = teslimKlasoru();
    private static final String ZIP_ADI = "turkce-eser-otomasyon-final.zip";

    private static Path teslimKlasoru() {
        String override = System.getenv("TESLIM_KLASOR");
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return Path.of(System.getenv().getOrDefault("USERPROFILE", System.getProperty("user.home")),
                "Desktop", "turkce-eser-final-teslim");
    }

    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        Path proje = Path.of(System.getProperty("user.dir"));

        teslimScriptleri(proje);
        teslimDokumanlari(proje);
        readmeTeslimBolumu(proje);
        gercekApiCagrisiYok();
        teslimKlasoruVeZip();
        zipGuvenlikKontrolleri();
        sha256Dosyasi();

        System.out.println("ADIM 33 DOĞRULAMA: BAŞARILI");
    }

    private static void teslimScriptleri(Path proje) throws Exception {
        for (String ad : new String[]{"teslim-paketi-olustur.ps1", "teslim-paketi-kontrol.ps1", "adim33-self-test.ps1"}) {
            Path p = proje.resolve(ad);
            if (!Files.isRegularFile(p)) {
                hata("Eksik script: " + ad);
            }
            String icerik = Files.readString(p, StandardCharsets.UTF_8);
            if (icerik.length() < 100) {
                hata("Script çok kısa: " + ad);
            }
        }
        System.out.println("OK: Teslim scriptleri mevcut");
    }

    private static void teslimDokumanlari(Path proje) throws Exception {
        String[] dosyalar = {
                "GONDERIM_MESAJLARI.md",
                "PATRON_KISA_OZET.md",
                "TEKNIK_KISIYE_NOT.md",
                "KURULUM_3_ADIM.md",
                "DEMO_5_DAKIKA.md",
                "GITHUB_LINKI.txt",
                "FINAL_RELEASE_DOGRULAMA.md"
        };
        for (String ad : dosyalar) {
            Path p = proje.resolve(ad);
            if (!Files.isRegularFile(p)) {
                hata("Eksik doküman: " + ad);
            }
            String icerik = Files.readString(p, StandardCharsets.UTF_8);
            if (icerik.length() < 80) {
                hata("Doküman çok kısa: " + ad);
            }
        }
        String gonderim = Files.readString(proje.resolve("GONDERIM_MESAJLARI.md"), StandardCharsets.UTF_8);
        if (!gonderim.contains("WhatsApp") || !gonderim.contains("github.com/berkky")) {
            hata("GONDERIM_MESAJLARI.md içerik eksik");
        }
        String patron = Files.readString(proje.resolve("PATRON_KISA_OZET.md"), StandardCharsets.UTF_8);
        if (!patron.contains("MVP") && !patron.contains("mvp")) {
            hata("PATRON_KISA_OZET.md MVP ifadesi eksik");
        }
        System.out.println("OK: Teslim dokümanları");
    }

    private static void readmeTeslimBolumu(Path proje) throws Exception {
        String readme = Files.readString(proje.resolve("README.md"), StandardCharsets.UTF_8);
        if (!readme.contains("Final Demo") && !readme.contains("Final demo")) {
            hata("README Final Demo bölümü eksik");
        }
        if (!readme.contains("teslim-paketi-olustur")) {
            hata("README teslim paketi bölümü eksik");
        }
        System.out.println("OK: README teslim bölümü");
    }

    private static void gercekApiCagrisiYok() {
        if (TamEserUretimKuyrukService.uretimBaslatildiMi()) {
            hata("Gerçek TTS üretimi başlatılmış olmamalı");
        }
        System.out.println("OK: Gerçek TTS/API üretim çağrısı yok");
    }

    private static void teslimKlasoruVeZip() throws Exception {
        Path zip = TESLIM_KLASOR.resolve(ZIP_ADI);
        if (!Files.isRegularFile(zip)) {
            hata("Teslim ZIP bulunamadı: " + zip);
        }
        if (Files.size(zip) < 10_000) {
            hata("Teslim ZIP çok küçük");
        }
        System.out.println("OK: Teslim ZIP mevcut");
    }

    private static void zipGuvenlikKontrolleri() throws Exception {
        Path zip = TESLIM_KLASOR.resolve(ZIP_ADI);
        boolean readmeVar = false;
        boolean finalKurulumVar = false;
        boolean gitVar = false;
        boolean targetVar = false;

        try (ZipFile zf = new ZipFile(zip.toFile())) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName().replace('\\', '/');
                if (name.equals("README.md") || name.endsWith("/README.md")) {
                    readmeVar = true;
                }
                if (name.contains("FINAL_KURULUM_REHBERI.md")) {
                    finalKurulumVar = true;
                }
                if (name.startsWith(".git/") || name.contains("/.git/")) {
                    gitVar = true;
                }
                if (name.startsWith("target/") || name.contains("/target/")) {
                    targetVar = true;
                }
                if (!e.isDirectory()) {
                    apiKeyTaramasi(zf, e, name);
                }
            }
        }

        if (gitVar) hata("ZIP içinde .git var");
        if (targetVar) hata("ZIP içinde target var");
        if (!readmeVar) hata("ZIP içinde README.md yok");
        if (!finalKurulumVar) hata("ZIP içinde FINAL_KURULUM_REHBERI.md yok");

        System.out.println("OK: ZIP güvenlik kontrolleri");
    }

    private static void apiKeyTaramasi(ZipFile zf, ZipEntry e, String name) throws Exception {
        if (!name.endsWith(".java") && !name.endsWith(".ps1") && !name.endsWith(".md")
                && !name.endsWith(".json") && !name.endsWith(".properties") && !name.endsWith(".env")) {
            return;
        }
        String icerik = new String(zf.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
        for (String line : icerik.split("\n")) {
            if (line.contains("dummy-test-api-key") || line.contains("test-key-adim")
                    || line.contains("sk-test") || line.contains("placeholder")
                    || line.contains("YOUR_API_KEY") || line.contains("API anahtar")) {
                continue;
            }
            if (line.matches(".*sk-[A-Za-z0-9]{20,}.*")) {
                hata("ZIP secret bulgusu: " + name);
            }
            if (line.matches(".*AIza[0-9A-Za-z_-]{30,}.*")) {
                hata("ZIP Google key bulgusu: " + name);
            }
        }
    }

    private static void sha256Dosyasi() throws Exception {
        Path sha = TESLIM_KLASOR.resolve("teslim-paketi-sha256.txt");
        if (!Files.isRegularFile(sha)) {
            hata("SHA-256 dosyası yok: teslim-paketi-sha256.txt");
        }
        String icerik = Files.readString(sha, StandardCharsets.UTF_8);
        if (!icerik.contains("SHA-256:") || icerik.length() < 40) {
            hata("SHA-256 dosyası geçersiz");
        }
        System.out.println("OK: SHA-256 dosyası");
    }

    private static void hata(String mesaj) {
        throw new AssertionError(mesaj);
    }
}
