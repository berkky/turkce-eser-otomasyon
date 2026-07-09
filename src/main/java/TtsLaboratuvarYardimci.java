import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;

public final class TtsLaboratuvarYardimci {
    private TtsLaboratuvarYardimci() {
    }

    public static String ortam(String ad, String varsayilan) {
        String deger = System.getenv(ad);
        if (deger == null || deger.isBlank()) {
            deger = System.getProperty(ad);
        }
        return deger == null || deger.isBlank() ? varsayilan : deger.trim();
    }

    public static boolean ortamVar(String ad) {
        return !ortam(ad, "").isBlank();
    }

    public static String guvenliDosyaAdi(String metin) {
        String n = Normalizer.normalize(metin == null ? "" : metin, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('ı', 'i').replace('İ', 'I')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return n.isBlank() ? "ses" : n;
    }

    public static String xmlKacis(String metin) {
        return metin.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public static void atomikYaz(Path hedef, byte[] veri) throws IOException {
        Files.createDirectories(hedef.toAbsolutePath().getParent());
        Path gecici = Files.createTempFile(hedef.toAbsolutePath().getParent(), "tts-", ".tmp");
        try {
            Files.write(gecici, veri);
            Files.move(gecici, hedef, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(gecici);
        }
    }

    public static String kisalt(String metin, int limit) {
        if (metin == null) return "";
        String temiz = metin.replaceAll("\\s+", " ").trim();
        return temiz.length() <= limit ? temiz : temiz.substring(0, limit) + "...";
    }

    public static void metinYaz(Path hedef, String metin) throws IOException {
        Files.createDirectories(hedef.toAbsolutePath().getParent());
        Files.writeString(hedef, metin, StandardCharsets.UTF_8);
    }
}
