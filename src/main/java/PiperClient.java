import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Yerel Piper TTS komut satırı aracını Java'dan çalıştırır.
 * Ses üretimi tamamen yerel bilgisayarda yapılır; metin buluta gönderilmez.
 */
public final class PiperClient {
    private static final Duration VARSAYILAN_ZAMAN_ASIMI = Duration.ofMinutes(15);

    private final Path pythonYolu;
    private final Path sesModelKlasoru;
    private final String sesModeli;
    private final boolean cudaKullan;
    private volatile KontrolSonucu basariliKontrolOnbellegi;

    public PiperClient(Path projeKlasoru) {
        this(
                pythonYolunuBul(projeKlasoru),
                ortamVeyaVarsayilanYol(
                        "PIPER_DATA_DIR",
                        Path.of(System.getProperty("user.home"), "Desktop", "piper-voices")
                ),
                ortamVeyaVarsayilan("PIPER_VOICE", "tr_TR-dfki-medium"),
                ortamBoolean("PIPER_USE_CUDA", false)
        );
    }

    public PiperClient(Path pythonYolu,
                       Path sesModelKlasoru,
                       String sesModeli,
                       boolean cudaKullan) {
        this.pythonYolu = pythonYolu.toAbsolutePath().normalize();
        this.sesModelKlasoru = sesModelKlasoru.toAbsolutePath().normalize();
        this.sesModeli = sesModeli;
        this.cudaKullan = cudaKullan;
    }

    public KontrolSonucu kontrolEt() {
        KontrolSonucu onbellek = basariliKontrolOnbellegi;
        if (onbellek != null && onbellek.hazir()) {
            return onbellek;
        }

        List<String> sorunlar = new ArrayList<>();

        if (!Files.isRegularFile(pythonYolu)) {
            sorunlar.add("Piper Python ortamı bulunamadı: " + pythonYolu);
        }
        if (!Files.isDirectory(sesModelKlasoru)) {
            sorunlar.add("Piper ses modeli klasörü bulunamadı: " + sesModelKlasoru);
        }

        Path onnx = sesModelKlasoru.resolve(sesModeli + ".onnx");
        Path json = sesModelKlasoru.resolve(sesModeli + ".onnx.json");
        if (!Files.isRegularFile(onnx)) {
            sorunlar.add("Ses modeli bulunamadı: " + onnx);
        }
        if (!Files.isRegularFile(json)) {
            sorunlar.add("Ses modeli ayar dosyası bulunamadı: " + json);
        }

        if (!sorunlar.isEmpty()) {
            return new KontrolSonucu(false, String.join(System.lineSeparator(), sorunlar));
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(
                    pythonYolu.toString(), "-m", "piper", "--help"
            );
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String cikti = oku(process);
            boolean bitti = process.waitFor(45, TimeUnit.SECONDS);
            if (!bitti) {
                process.destroyForcibly();
                return new KontrolSonucu(false, "Piper kontrol komutu zaman aşımına uğradı.");
            }
            if (process.exitValue() != 0) {
                return new KontrolSonucu(false,
                        "Piper modülü çalıştırılamadı. Çıkış kodu: " + process.exitValue()
                                + System.lineSeparator() + kisalt(cikti, 800));
            }
            KontrolSonucu basarili = new KontrolSonucu(true, "Piper hazır");
            basariliKontrolOnbellegi = basarili;
            return basarili;
        } catch (Exception e) {
            return new KontrolSonucu(false, "Piper kontrolü başarısız: " + e.getMessage());
        }
    }

    public UretimSonucu wavUret(String metin, Path cikisWav) throws Exception {
        if (metin == null || metin.isBlank()) {
            throw new IllegalArgumentException("Seslendirilecek metin boş olamaz.");
        }

        KontrolSonucu kontrol = kontrolEt();
        if (!kontrol.hazir()) {
            throw new IllegalStateException(kontrol.mesaj());
        }

        Files.createDirectories(cikisWav.toAbsolutePath().getParent());
        Path geciciMetin = Files.createTempFile("piper-metin-", ".txt");
        Files.writeString(geciciMetin, metin.trim(), StandardCharsets.UTF_8);

        List<String> komut = new ArrayList<>();
        komut.add(pythonYolu.toString());
        komut.add("-m");
        komut.add("piper");
        komut.add("-m");
        komut.add(sesModeli);
        komut.add("--data-dir");
        komut.add(sesModelKlasoru.toString());
        komut.add("-f");
        komut.add(cikisWav.toAbsolutePath().toString());
        komut.add("--input-file");
        komut.add(geciciMetin.toAbsolutePath().toString());
        komut.add("--sentence-silence");
        komut.add("0.20");
        if (cudaKullan) {
            komut.add("--cuda");
        }

        long baslangic = System.nanoTime();
        try {
            ProcessBuilder builder = new ProcessBuilder(komut);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String cikti = oku(process);
            boolean bitti = process.waitFor(VARSAYILAN_ZAMAN_ASIMI.toSeconds(), TimeUnit.SECONDS);

            if (!bitti) {
                process.destroyForcibly();
                throw new IOException("Piper ses üretimi "
                        + VARSAYILAN_ZAMAN_ASIMI.toMinutes() + " dakikada tamamlanamadı.");
            }
            if (process.exitValue() != 0) {
                Files.deleteIfExists(cikisWav);
                throw new IOException("Piper ses üretimi başarısız. Çıkış kodu: "
                        + process.exitValue() + System.lineSeparator() + kisalt(cikti, 1500));
            }
            if (!Files.isRegularFile(cikisWav) || Files.size(cikisWav) < 1_000) {
                Files.deleteIfExists(cikisWav);
                throw new IOException("Piper çıktı WAV dosyasını oluşturamadı veya dosya boş.");
            }

            long sureMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - baslangic);
            return new UretimSonucu(
                    cikisWav,
                    Files.size(cikisWav),
                    metin.length(),
                    sureMs,
                    sesModeli,
                    cudaKullan,
                    cikti
            );
        } finally {
            Files.deleteIfExists(geciciMetin);
        }
    }

    public Path pythonYolu() {
        return pythonYolu;
    }

    public Path sesModelKlasoru() {
        return sesModelKlasoru;
    }

    public String sesModeli() {
        return sesModeli;
    }

    public boolean cudaKullan() {
        return cudaKullan;
    }

    private static Path pythonYolunuBul(Path projeKlasoru) {
        String ortam = System.getenv("PIPER_PYTHON");
        if (ortam != null && !ortam.isBlank()) {
            Path ortamYolu = Path.of(ortam.trim());
            if (Files.isRegularFile(ortamYolu)) {
                return ortamYolu;
            }
        }

        Path windowsVenv = projeKlasoru.resolve(".venv-piper").resolve("Scripts").resolve("python.exe");
        if (Files.isRegularFile(windowsVenv)) {
            return windowsVenv;
        }

        Path unixVenv = projeKlasoru.resolve(".venv-piper").resolve("bin").resolve("python");
        if (Files.isRegularFile(unixVenv)) {
            return unixVenv;
        }

        // Önceki adım klasöründe kurulmuş Piper ortamını da otomatik bul.
        Path masaustu = Path.of(System.getProperty("user.home"), "Desktop");
        if (Files.isDirectory(masaustu)) {
            try (var klasorler = Files.list(masaustu)) {
                Path bulunan = klasorler
                        .filter(Files::isDirectory)
                        .filter(path -> path.getFileName().toString().startsWith("eser-otomasyon"))
                        .map(path -> path.resolve(".venv-piper").resolve("Scripts").resolve("python.exe"))
                        .filter(Files::isRegularFile)
                        .findFirst()
                        .orElse(null);
                if (bulunan != null) {
                    return bulunan;
                }
            } catch (Exception ignored) {
                // Son çare olarak mevcut proje yolu döndürülür ve kontrolEt açıklayıcı hata verir.
            }
        }

        return windowsVenv;
    }

    private static String ortamVeyaVarsayilan(String ad, String varsayilan) {
        String deger = System.getenv(ad);
        return deger == null || deger.isBlank() ? varsayilan : deger.trim();
    }

    private static Path ortamVeyaVarsayilanYol(String ad, Path varsayilan) {
        String deger = System.getenv(ad);
        return deger == null || deger.isBlank() ? varsayilan : Path.of(deger.trim());
    }

    private static boolean ortamBoolean(String ad, boolean varsayilan) {
        String deger = System.getenv(ad);
        if (deger == null || deger.isBlank()) {
            return varsayilan;
        }
        String temiz = deger.trim().toLowerCase(Locale.ROOT);
        return temiz.equals("1") || temiz.equals("true") || temiz.equals("evet")
                || temiz.equals("e") || temiz.equals("yes") || temiz.equals("y");
    }

    private static String oku(Process process) throws IOException {
        StringBuilder cikti = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String satir;
            while ((satir = reader.readLine()) != null) {
                cikti.append(satir).append(System.lineSeparator());
            }
        }
        return cikti.toString();
    }

    private static String kisalt(String metin, int sinir) {
        if (metin == null) {
            return "";
        }
        String temiz = metin.trim();
        return temiz.length() <= sinir ? temiz : temiz.substring(0, sinir) + "...";
    }

    public record KontrolSonucu(boolean hazir, String mesaj) {
    }

    public record UretimSonucu(Path wavDosyasi,
                               long dosyaBoyutu,
                               int karakterSayisi,
                               long uretimSuresiMs,
                               String sesModeli,
                               boolean cudaKullanildi,
                               String motorCiktisi) {
    }
}
