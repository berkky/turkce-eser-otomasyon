import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Tesseract CLI ile çevrimdışı Türkçe OCR. API anahtarı gerektirmez. */
public final class YerelOcrService {
    private static final int DPI = ortamInt("ESER_OCR_DPI", 220, 150, 400);
    private static final int MAKSIMUM_SAYFA = ortamInt("ESER_OCR_MAKSIMUM_SAYFA", 600, 1, 5000);
    private static final int PSM = ortamInt("ESER_OCR_PSM", 3, 0, 13);

    private YerelOcrService() {}

    public static Durum durum() {
        String exe = tesseractKomutu();
        try {
            Process p = new ProcessBuilder(exe, "--version").redirectErrorStream(true).start();
            boolean bitti = p.waitFor(8, TimeUnit.SECONDS);
            if (!bitti) { p.destroyForcibly(); return new Durum(false, exe, "sürüm sorgusu zaman aşımı", ""); }
            String surum = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().findFirst().orElse("").trim();
            if (p.exitValue() != 0) return new Durum(false, exe, "çalıştırılamadı", surum);
            String dil = dilleriBul(exe);
            return new Durum(true, exe, dil.contains("tur") ? "Türkçe dil modeli hazır" : "Türkçe dil modeli yok; eng kullanılacak", surum);
        } catch (Exception e) {
            return new Durum(false, exe, e.getClass().getSimpleName() + ": " + e.getMessage(), "");
        }
    }

    public static boolean kullanilabilirMi() { return durum().hazir(); }

    public static OcrSonucu pdfOcr(Path pdf, int istenenSayfa) throws Exception {
        Durum d = durum();
        if (!d.hazir()) throw new IllegalStateException("Tesseract hazır değil: " + d.aciklama());
        int limit = Math.min(MAKSIMUM_SAYFA, istenenSayfa <= 0 ? MAKSIMUM_SAYFA : istenenSayfa);
        String diller = dilleriBul(d.komut());
        String dil = diller.contains("tur") ? (diller.contains("eng") ? "tur+eng" : "tur") : "eng";
        Path gecici = Files.createTempDirectory("eser-ocr-");
        List<SayfaMetni> sayfalar = new ArrayList<>();
        try (PDDocument belge = PDDocument.load(pdf.toFile())) {
            int toplam = belge.getNumberOfPages();
            int okunacak = Math.min(toplam, limit);
            PDFRenderer renderer = new PDFRenderer(belge);
            for (int i = 0; i < okunacak; i++) {
                BufferedImage goruntu = renderer.renderImageWithDPI(i, DPI, ImageType.RGB);
                Path png = gecici.resolve(String.format(Locale.ROOT, "sayfa-%05d.png", i + 1));
                ImageIO.write(goruntu, "png", png.toFile());
                String metin = resimOcr(d.komut(), png, dil);
                sayfalar.add(new SayfaMetni(i + 1, BelgeMetinCikarmaService.metniTemizle(metin)));
                Files.deleteIfExists(png);
                if ((i + 1) % 10 == 0 || i + 1 == okunacak)
                    System.out.println("Yerel OCR: " + (i + 1) + "/" + okunacak + " sayfa");
            }
            return new OcrSonucu(List.copyOf(sayfalar), toplam, okunacak, dil, DPI);
        } finally {
            sil(gecici);
        }
    }

    private static String resimOcr(String exe, Path png, String dil) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(exe, png.toString(), "stdout", "-l", dil, "--psm", Integer.toString(PSM));
        Process p = pb.start();
        CompletableFuture<byte[]> stdout = CompletableFuture.supplyAsync(() -> akisOku(p.getInputStream()));
        CompletableFuture<byte[]> stderr = CompletableFuture.supplyAsync(() -> akisOku(p.getErrorStream()));
        boolean bitti = p.waitFor(Duration.ofMinutes(5).toMillis(), TimeUnit.MILLISECONDS);
        if (!bitti) { p.destroyForcibly(); throw new IllegalStateException("Tesseract sayfa OCR zaman aşımı: " + png.getFileName()); }
        String metin = new String(stdout.join(), StandardCharsets.UTF_8);
        String hata = new String(stderr.join(), StandardCharsets.UTF_8);
        if (p.exitValue() != 0) throw new IllegalStateException("Tesseract hata kodu " + p.exitValue() + ": " + kisalt(hata, 500));
        return metin;
    }

    private static byte[] akisOku(java.io.InputStream in) {
        try { return in.readAllBytes(); } catch (Exception e) { return new byte[0]; }
    }

    private static String dilleriBul(String exe) {
        try {
            Process p = new ProcessBuilder(exe, "--list-langs").redirectErrorStream(true).start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) { p.destroyForcibly(); return ""; }
            return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        } catch (Exception e) { return ""; }
    }

    private static String tesseractKomutu() {
        String env = System.getenv("TESSERACT_PATH");
        if (env != null && !env.isBlank()) return env.trim();
        List<String> adaylar = new ArrayList<>();
        adaylar.add("C:\\Program Files\\Tesseract-OCR\\tesseract.exe");
        adaylar.add("C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe");
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            adaylar.add(Path.of(local, "Programs", "Tesseract-OCR", "tesseract.exe").toString());
            adaylar.add(Path.of(local, "Tesseract-OCR", "tesseract.exe").toString());
        }
        for (String a : adaylar) if (Files.isRegularFile(Path.of(a))) return a;
        return "tesseract";
    }

    private static int ortamInt(String ad, int varsayilan, int min, int max) {
        try {
            String x = System.getenv(ad);
            int v = x == null || x.isBlank() ? varsayilan : Integer.parseInt(x.trim());
            return Math.max(min, Math.min(max, v));
        } catch (Exception e) { return varsayilan; }
    }

    private static void sil(Path p) {
        try (Stream<Path> s = Files.walk(p)) {
            for (Path x : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(x);
        } catch (Exception ignored) {}
    }

    private static String kisalt(String s, int n) { return s == null || s.length() <= n ? s : s.substring(0, n) + "…"; }

    public record Durum(boolean hazir, String komut, String aciklama, String surum) {}
    public record SayfaMetni(int sayfa, String metin) {}
    public record OcrSonucu(List<SayfaMetni> sayfalar, int toplamSayfa, int okunanSayfa, String dil, int dpi) {
        public String tamMetin() {
            StringBuilder b = new StringBuilder();
            for (SayfaMetni s : sayfalar) {
                if (!b.isEmpty()) b.append("\n\n");
                b.append("Sayfa ").append(s.sayfa()).append("\n\n").append(s.metin());
            }
            return b.toString().trim();
        }
    }
}
