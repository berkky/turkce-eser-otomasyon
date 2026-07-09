import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Alignment path ve eser güvenlik kontrolleri.
 */
public final class AlignmentGuvenlikService {
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    private AlignmentGuvenlikService() {
    }

    public static void eserIzni(int eserId) {
        if (eserId == SesKaliteOlcutleri.ASTRONOMI_ESER_ID) {
            throw new IllegalStateException(
                    "ESER-00006 büyük eser — alignment bu adımda engelli. Yalnızca plan ve uyarı gösterilir.");
        }
        if (eserId != SesKaliteOlcutleri.KASAGI_ESER_ID) {
            throw new IllegalArgumentException("Alignment yalnızca ESER-00005 önizlemesi için açıktır.");
        }
    }

    public static String guvenliDosyaAdi(int eserId, String sonEk) {
        String ad = String.format(Locale.ROOT, "ESER-%05d-preview-%s", eserId, sonEk);
        if (!SAFE_NAME.matcher(ad).matches()) {
            throw new IllegalArgumentException("Geçersiz alignment dosya adı");
        }
        return ad;
    }

    public static void dosyaGuvenligi(Path kok, Path hedef) {
        if (!WebGuvenlikService.guvenliAltDosya(kok, hedef)) {
            throw new WebGuvenlikService.GuvenlikIstisnasi("Alignment dosya yolu reddedildi.");
        }
        if (!WebGuvenlikService.uzantiIzinli(hedef)) {
            throw new WebGuvenlikService.GuvenlikIstisnasi("Alignment uzantısı izinli değil.");
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
