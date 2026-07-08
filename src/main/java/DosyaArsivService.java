import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DosyaArsivService {
    private DosyaArsivService() {
    }

    public static void klasorHazirla(Path klasor) throws Exception {
        Files.createDirectories(klasor);
    }

    public static List<Path> eserDosyalariniListele(Path klasor) throws Exception {
        try (var akis = Files.list(klasor)) {
            return akis
                    .filter(Files::isRegularFile)
                    .filter(DosyaArsivService::desteklenenEserMi)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    public static List<Path> pdfleriListele(Path klasor) throws Exception {
        try (var akis = Files.list(klasor)) {
            return akis
                    .filter(Files::isRegularFile)
                    .filter(DosyaArsivService::pdfMi)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    public static boolean desteklenenEserMi(Path path) {
        return pdfMi(path) || epubMi(path);
    }

    public static boolean pdfMi(Path path) {
        return ".pdf".equals(uzanti(path));
    }

    public static boolean epubMi(Path path) {
        return ".epub".equals(uzanti(path));
    }

    public static String uzanti(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        String ad = path.getFileName().toString();
        int nokta = ad.lastIndexOf('.');
        return nokta >= 0 ? ad.substring(nokta).toLowerCase(Locale.ROOT) : "";
    }

    public static String sha256(Path dosya) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(dosya)) {
            byte[] buffer = new byte[8192];
            int okunan;
            while ((okunan = input.read(buffer)) != -1) {
                digest.update(buffer, 0, okunan);
            }
        }

        StringBuilder sonuc = new StringBuilder(64);
        for (byte b : digest.digest()) {
            sonuc.append(String.format("%02x", b));
        }
        return sonuc.toString();
    }

    public static Path arsiveTasi(Path kaynak, Path arsivKlasoru, int id, String eserAdi) throws Exception {
        String guvenliAd = dosyaAdinaUygunHaleGetir(eserAdi);
        if (guvenliAd.isBlank() || "Bilinmiyor".equalsIgnoreCase(guvenliAd)) {
            guvenliAd = dosyaAdinaUygunHaleGetir(uzantiyiKaldir(kaynak.getFileName().toString()));
        }

        String uzanti = uzanti(kaynak);
        if (uzanti.isBlank()) {
            uzanti = ".bin";
        }

        String temelAd = String.format("ESER-%05d - %s", id, guvenliAd);
        Path hedef = benzersizHedef(arsivKlasoru, temelAd, uzanti);
        return Files.move(kaynak, hedef);
    }

    public static Path tekrarlaraTasi(Path kaynak, Path tekrarKlasoru, int eserId) throws Exception {
        String orijinal = dosyaAdinaUygunHaleGetir(uzantiyiKaldir(kaynak.getFileName().toString()));
        String temelAd = String.format("TEKRAR-ESER-%05d - %s", Math.max(eserId, 0), orijinal);
        String uzanti = uzanti(kaynak);
        if (uzanti.isBlank()) {
            uzanti = ".bin";
        }
        Path hedef = benzersizHedef(tekrarKlasoru, temelAd, uzanti);
        return Files.move(kaynak, hedef);
    }

    public static void geriTasi(Path tasinmisDosya, Path eskiYol) {
        try {
            if (Files.exists(tasinmisDosya) && !Files.exists(eskiYol)) {
                Files.move(tasinmisDosya, eskiYol);
                System.err.println("Eser dosyası eski klasörüne geri taşındı.");
            }
        } catch (Exception e) {
            System.err.println("UYARI: Eser dosyası eski klasörüne geri taşınamadı: " + e.getMessage());
        }
    }

    private static Path benzersizHedef(Path klasor, String temelAd, String uzanti) {
        Path hedef = klasor.resolve(temelAd + uzanti);
        int sayac = 2;
        while (Files.exists(hedef)) {
            hedef = klasor.resolve(temelAd + " (" + sayac + ")" + uzanti);
            sayac++;
        }
        return hedef;
    }

    private static String dosyaAdinaUygunHaleGetir(String metin) {
        if (metin == null) {
            return "";
        }

        String temiz = metin
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (temiz.length() > 100) {
            temiz = temiz.substring(0, 100).trim();
        }

        while (!temiz.isEmpty() && (temiz.endsWith(".") || temiz.endsWith(" "))) {
            temiz = temiz.substring(0, temiz.length() - 1);
        }

        return temiz;
    }

    private static String uzantiyiKaldir(String dosyaAdi) {
        int nokta = dosyaAdi.lastIndexOf('.');
        return nokta > 0 ? dosyaAdi.substring(0, nokta) : dosyaAdi;
    }
}
