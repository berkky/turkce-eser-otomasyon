import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tam eser üretim güvenlik ve dosya adı kuralları.
 */
public final class TamEserUretimGuvenlikService {
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    private TamEserUretimGuvenlikService() {
    }

    public static Path uretimKlasoru(Path kuyrukKok) {
        return kuyrukKok.resolve("production-approvals");
    }

    public static String guvenliDosyaAdi(int eserId, String tur) {
        String ad = String.format(Locale.ROOT, "ESER-%05d-production-%s.json", eserId, tur);
        if (!SAFE_NAME.matcher(ad).matches()) {
            throw new IllegalArgumentException("Geçersiz üretim dosya adı: " + tur);
        }
        return ad;
    }

    public static String outputSafeName(int eserId) {
        return String.format(Locale.ROOT, "ESER-%05d-production-output", eserId);
    }

    public static void dosyaGuvenligi(Path kok, Path hedef) {
        if (!WebGuvenlikService.guvenliAltDosya(kok, hedef)) {
            throw new WebGuvenlikService.GuvenlikIstisnasi("Üretim dosya yolu reddedildi.");
        }
        if (!WebGuvenlikService.uzantiIzinli(hedef)) {
            throw new WebGuvenlikService.GuvenlikIstisnasi("Üretim uzantısı izinli değil.");
        }
    }

    public static String jsonGuvenli(String json) {
        String temiz = WebGuvenlikService.gizliDegerleriTemizle(json);
        if (yolSizintisiVar(temiz)) {
            throw new WebGuvenlikService.GuvenlikIstisnasi("JSON çıktısında tam yol tespit edildi.");
        }
        return WebGuvenlikService.guvenliJson(temiz);
    }

    public static boolean yolSizintisiVar(String metin) {
        if (metin == null) {
            return false;
        }
        return metin.matches("(?is).*[A-Za-z]:\\\\Users\\\\.*")
                || metin.matches("(?is).*/Users/.*")
                || metin.matches("(?is).*\\\\\\\\Desktop\\\\\\\\.*");
    }
}
