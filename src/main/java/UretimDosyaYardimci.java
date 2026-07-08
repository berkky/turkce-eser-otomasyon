import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;

public final class UretimDosyaYardimci {
    private UretimDosyaYardimci() {}

    public static void propertiesAtomikYaz(Path hedef, Properties p, String baslik) throws IOException {
        Files.createDirectories(hedef.toAbsolutePath().getParent());
        Path gecici = Files.createTempFile(hedef.toAbsolutePath().getParent(), hedef.getFileName().toString(), ".tmp");
        try (var out = Files.newOutputStream(gecici)) {
            p.store(out, baslik);
        }
        atomikTasi(gecici, hedef);
    }

    public static Properties propertiesOku(Path yol) throws IOException {
        Properties p = new Properties();
        try (var in = Files.newInputStream(yol)) { p.load(in); }
        return p;
    }

    public static void metinAtomikYaz(Path hedef, String metin) throws IOException {
        Files.createDirectories(hedef.toAbsolutePath().getParent());
        Path gecici = Files.createTempFile(hedef.toAbsolutePath().getParent(), hedef.getFileName().toString(), ".tmp");
        Files.writeString(gecici, metin, StandardCharsets.UTF_8);
        atomikTasi(gecici, hedef);
    }

    public static void atomikTasi(Path kaynak, Path hedef) throws IOException {
        try {
            Files.move(kaynak, hedef, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(kaynak, hedef, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String sha256(Path dosya) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(dosya)) {
            byte[] tampon = new byte[64 * 1024];
            int n;
            while ((n = in.read(tampon)) >= 0) if (n > 0) md.update(tampon, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    public static String jsonKacir(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
