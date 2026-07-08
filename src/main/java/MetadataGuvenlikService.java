import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * OCR ve kural tabanlı metadata sonuçlarını güvenlik süzgecinden geçirir.
 * Yerel/OCR bulguları otomatik olarak kesin bilgi sayılmaz.
 */
public final class MetadataGuvenlikService {
    private static final Locale TR = new Locale("tr", "TR");
    private static final Pattern SAYFA_ARALIGI = Pattern.compile("(?iu)\\b(?:sayfa|page)?\\s*\\d{1,4}\\s*[-–—]\\s*\\d{1,4}\\b");
    private static final Pattern DOSYA_UZANTISI = Pattern.compile("(?iu)\\.(pdf|epub|txt|html?|md)$");
    private static final Pattern ESER_ON_EKI = Pattern.compile("(?iu)^ESER-\\d{1,8}\\s*-\\s*");

    private MetadataGuvenlikService() {}

    public static boolean supheliBaslik(String deger) {
        if (!EserMetadata.bilinen(deger)) return true;
        String s = deger.trim();
        String k = s.toLowerCase(TR);
        if (s.length() < 3 || s.length() > 150) return true;
        if (s.split("\\s+").length > 18) return true;
        if (SAYFA_ARALIGI.matcher(k).find()) return true;
        if (k.matches("^(sayfa|page|bölüm|bolum|içindekiler|icindekiler|contents|önsöz|onsoz|giriş|giris)\\b.*")) return true;
        if (k.matches("^(kitap|kitabı|kitabi|eser|başlık|baslik)$")) return true;
        if (k.contains("ocr") || k.contains("tarama sayfası") || k.contains("scan page")) return true;
        Character ilkHarf = ilkHarf(s);
        return ilkHarf != null && Character.isLowerCase(ilkHarf);
    }

    public static boolean supheliKisi(String deger) {
        if (!EserMetadata.bilinen(deger)) return true;
        String s = deger.trim();
        String k = s.toLowerCase(TR);
        if (s.length() < 3 || s.length() > 120) return true;
        if (s.split("\\s+").length > 9) return true;
        if (s.matches(".*\\d.*")) return true;
        if (k.contains("yayın") || k.contains("yayin") || k.contains("kitap") || k.contains("ltd")
                || k.contains("şti") || k.contains("sti") || k.contains("universe")
                || k.contains("space") || k.contains("the stars") || k.contains("sayfa")) return true;
        if (k.matches(".*\\b(?:dır|dir|dur|dür|tır|tir|tur|tür)\\b.*")) return true;
        if (s.contains(":") || s.contains(";") || s.endsWith(".")) return true;
        Character ilkHarf = ilkHarf(s);
        return ilkHarf != null && Character.isLowerCase(ilkHarf);
    }

    public static boolean supheliYayinevi(String deger) {
        if (!EserMetadata.bilinen(deger)) return true;
        String s = deger.trim();
        if (s.length() < 3 || s.length() > 160) return true;
        if (s.split("\\s+").length > 14) return true;
        return SAYFA_ARALIGI.matcher(s).find();
    }

    public static String kanonikBaslik(EserMetadata m, String manifestBasligi) {
        String archive = temizDosyaBasligi(m == null ? "" : m.archiveDosyaAdi);
        if (!supheliBaslik(archive)) return archive;

        String urlBasligi = urlSonParca(m == null ? "" : m.kaynakUrl);
        if (!supheliBaslik(urlBasligi)) return urlBasligi;

        String mevcut = temizDosyaBasligi(manifestBasligi);
        if (!supheliBaslik(mevcut)) return mevcut;

        String metadataBasligi = m == null ? "" : temizDosyaBasligi(m.eserAdi);
        return supheliBaslik(metadataBasligi) ? "Bilinmiyor" : metadataBasligi;
    }

    public static String guvenliYazar(String deger) {
        return supheliKisi(deger) ? "Bilinmiyor" : deger.trim();
    }

    public static String guvenliYayinevi(String deger) {
        return supheliYayinevi(deger) ? "Bilinmiyor" : deger.trim();
    }

    public static double yerelOnarimGuveni(EserMetadata m) {
        double g = 0.20;
        if (!supheliBaslik(m.eserAdi)) g += 0.20;
        if (!supheliKisi(m.yazar)) g += 0.08;
        if (!supheliYayinevi(m.yayinevi)) g += 0.10;
        if (EserMetadata.bilinen(m.yayinYili)) g += 0.07;
        if (EserMetadata.bilinen(m.isbn) && YerelKunyeAnalizService.isbnGecerli(m.isbn)) g += 0.12;
        if (EserMetadata.bilinen(m.cevirmen) && !supheliKisi(m.cevirmen)) g += 0.05;
        return Math.min(0.67, g);
    }

    public static String temizDosyaBasligi(String deger) {
        if (deger == null) return "";
        String s = deger.trim().replace('\\', '/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        s = DOSYA_UZANTISI.matcher(s).replaceFirst("");
        s = ESER_ON_EKI.matcher(s).replaceFirst("");
        s = s.replaceAll("(?iu)_(?:djvu|text|hocr|bw|jp2|page|searchable).*$", "");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String urlSonParca(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            String x = url;
            while (x.endsWith("/")) x = x.substring(0, x.length() - 1);
            int i = x.lastIndexOf('/');
            String son = i >= 0 ? x.substring(i + 1) : x;
            return temizDosyaBasligi(URLDecoder.decode(son, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "";
        }
    }

    private static Character ilkHarf(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) return c;
        }
        return null;
    }
}
