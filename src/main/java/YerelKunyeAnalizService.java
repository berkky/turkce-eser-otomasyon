import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bulut API'si olmadan ön sayfa / OCR / EPUB metninden temel künye çıkarır.
 * Bilgi uydurmaz; yalnızca metinde görülen veya açık kolektif işaretlerinden
 * türetilen alanları döndürür.
 */
public final class YerelKunyeAnalizService {
    private static final int MAKSIMUM_INCELEME_KARAKTERI = 140_000;

    private static final Pattern ISBN = Pattern.compile(
            "(?iu)\\bISBN(?:-1[03])?\\s*[:：]?\\s*((?:97[89][\\s-]?)?[0-9X][0-9X\\s-]{8,22})");
    private static final Pattern BASIM_YILI = Pattern.compile(
            "(?iu)\\b(?:[1-9]\\.?\\s*)?(?:basım|baskı|yayın\\s*tarihi|basım\\s*yılı)\\s*[:：-]?\\s*((?:18|19|20)\\d{2})\\b");
    private static final Pattern ORIJINAL_AD = Pattern.compile(
            "(?imu)^\\s*(?:orijinal\\s*adı|özgün\\s*adı|original\\s*title)\\s*[:：-]?\\s*(.{2,140}?)\\s*$");
    private static final Pattern CEVIRMEN = Pattern.compile(
            "(?imu)^\\s*(?:(?:[A-Za-zÇĞİÖŞÜçğıöşü]+\\s+)?aslından\\s+çeviren|çeviren|çeviri)\\s*[:：-]?\\s*(.{2,120}?)\\s*$");
    private static final Pattern ACIK_YAZAR = Pattern.compile(
            "(?imu)^\\s*(?:yazar|yazan|hazırlayan|derleyen|eser\\s*sahibi)\\s*[:：-]?\\s*(.{2,140}?)\\s*$");
    private static final Pattern ACIK_YAYINEVI = Pattern.compile(
            "(?imu)^\\s*(?:yayınevi|yayıncı\\s*kuruluş|publisher)\\s*[:：-]?\\s*(.{2,160}?)\\s*$");
    private static final Pattern KURUMSAL_YAYINEVI = Pattern.compile(
            "(?imu)(?:©\\s*(?:18|19|20)\\d{2}\\s*,?\\s*)?([A-ZÇĞİÖŞÜ][A-Za-zÇĞİÖŞÜçğıöşü0-9 .&'’\\-]{1,110}?(?:Basım\\s+Yayım(?:\\s+Dağıtım)?(?:\\s+Ltd\\.?\\s*Şti\\.?)?|Yayınları|Yayıncılık|Yayınevi))\\b");
    private static final Pattern KITAP_BASLIGI = Pattern.compile(
            "(?imu)^\\s*([A-ZÇĞİÖŞÜ0-9][A-ZÇĞİÖŞÜ0-9 '’\\-]{2,70}?(?:\\s+KİTABI))\\s*$");
    private static final Pattern ACIK_BASLIK = Pattern.compile(
            "(?imu)^\\s*(?:eser(?:in)?\\s*adı|kitab(?:ın)?\\s*adı|başlık|title)\\s*[:：-]?\\s*(.{2,140}?)\\s*$");
    private static final Pattern BASIM_BILGISI = Pattern.compile(
            "(?imu)^\\s*((?:[1-9]\\.?\\s*)?(?:basım|baskı)\\s*[:：-]?\\s*(?:18|19|20)\\d{2})\\s*$");

    public Sonuc analizEt(String tamMetin, String mevcutBaslik, String dosyaAdi) {
        String onMetin = onMetin(tamMetin);
        List<String> kanit = new ArrayList<>();

        String isbn = isbnBul(onMetin);
        if (dolu(isbn)) kanit.add("ISBN ön sayfalarda bulundu: " + isbn);

        String yil = grupBul(BASIM_YILI, onMetin, 1);
        if (dolu(yil)) kanit.add("Basım/yayın yılı ön sayfalarda bulundu: " + yil);

        String orijinalAdi = satirTemizle(grupBul(ORIJINAL_AD, onMetin, 1));
        if (dolu(orijinalAdi)) kanit.add("Orijinal ad satırı bulundu: " + orijinalAdi);

        String cevirmen = kisiTemizle(grupBul(CEVIRMEN, onMetin, 1));
        if (dolu(cevirmen)) kanit.add("Çevirmen satırı bulundu: " + cevirmen);

        String basimBilgisi = satirTemizle(grupBul(BASIM_BILGISI, onMetin, 1));
        if (!dolu(basimBilgisi) && dolu(yil)) basimBilgisi = "Basım: " + yil;

        String yayinevi = yayineviBul(onMetin);
        if (MetadataGuvenlikService.supheliYayinevi(yayinevi)) yayinevi = "";
        if (dolu(yayinevi)) kanit.add("Yayınevi/yayıncı ön sayfalarda bulundu: " + yayinevi);

        String baslik = baslikBul(onMetin, mevcutBaslik, dosyaAdi);
        if (MetadataGuvenlikService.supheliBaslik(baslik)) baslik = "";
        if (dolu(baslik)) kanit.add("Eser adı ön sayfa/dosya bağlamından çıkarıldı: " + baslik);

        String yazar = kisiTemizle(grupBul(ACIK_YAZAR, onMetin, 1));
        if (MetadataGuvenlikService.supheliKisi(yazar)) yazar = "";
        if (!dolu(yazar) && kolektifIsaretiVar(onMetin)) yazar = "Kolektif";
        if (dolu(yazar)) kanit.add("Yazar/sorumluluk bilgisi çıkarıldı: " + yazar);

        String lisans = hakDurumu(onMetin);
        if (dolu(lisans)) kanit.add("Hak bilgisi ön sayfalardan çıkarıldı: " + lisans);

        double guven = 0.08;
        if (dolu(baslik)) guven += 0.24;
        if (dolu(yazar)) guven += 0.13;
        if (dolu(yayinevi)) guven += 0.13;
        if (dolu(yil)) guven += 0.10;
        if (dolu(isbn)) guven += 0.15;
        if (dolu(orijinalAdi)) guven += 0.06;
        if (dolu(cevirmen)) guven += 0.06;
        if (dolu(lisans)) guven += 0.03;
        guven = Math.min(0.96, guven);

        return new Sonuc(
                varsayilan(baslik), varsayilan(yazar), varsayilan(yayinevi), varsayilan(yil),
                varsayilan(isbn), varsayilan(orijinalAdi), varsayilan(cevirmen),
                varsayilan(basimBilgisi), varsayilan(lisans), guven,
                String.join(" | ", kanit), !kanit.isEmpty());
    }

    private static String onMetin(String metin) {
        if (metin == null) return "";
        String x = Normalizer.normalize(metin, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        if (x.length() > MAKSIMUM_INCELEME_KARAKTERI) x = x.substring(0, MAKSIMUM_INCELEME_KARAKTERI);
        return x.replaceAll("(?m)[ \\t]+$", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{4,}", "\n\n\n")
                .trim();
    }

    private static String isbnBul(String metin) {
        Matcher m = ISBN.matcher(metin);
        while (m.find()) {
            String x = m.group(1).replaceAll("[^0-9Xx]", "").toUpperCase(Locale.ROOT);
            if (isbnGecerli(x)) return x;
        }
        return "";
    }

    static boolean isbnGecerli(String x) {
        if (x == null) return false;
        String s = x.replaceAll("[^0-9Xx]", "").toUpperCase(Locale.ROOT);
        if (s.length() == 13 && s.chars().allMatch(Character::isDigit)) {
            int toplam = 0;
            for (int i = 0; i < 12; i++) toplam += (s.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
            int kontrol = (10 - (toplam % 10)) % 10;
            return kontrol == s.charAt(12) - '0';
        }
        if (s.length() == 10) {
            int toplam = 0;
            for (int i = 0; i < 10; i++) {
                char c = s.charAt(i);
                int deger = (i == 9 && c == 'X') ? 10 : Character.digit(c, 10);
                if (deger < 0) return false;
                toplam += (10 - i) * deger;
            }
            return toplam % 11 == 0;
        }
        return false;
    }

    private static String yayineviBul(String metin) {
        String acik = satirTemizle(grupBul(ACIK_YAYINEVI, metin, 1));
        if (dolu(acik)) return yayineviTemizle(acik);

        Matcher m = KURUMSAL_YAYINEVI.matcher(metin);
        String enIyi = "";
        while (m.find()) {
            String aday = yayineviTemizle(m.group(1));
            if (!dolu(aday)) continue;
            if (aday.toLowerCase(Locale.ROOT).contains("basım yayım")) return aday;
            if (enIyi.isBlank() || aday.length() < enIyi.length()) enIyi = aday;
        }
        return enIyi;
    }

    private static String baslikBul(String metin, String mevcutBaslik, String dosyaAdi) {
        String acik = satirTemizle(grupBul(ACIK_BASLIK, metin, 1));
        if (dolu(acik) && !acik.toLowerCase(Locale.ROOT).startsWith("the ")
                && !MetadataGuvenlikService.supheliBaslik(acik)) return baslikBuyukKucuk(acik);

        Matcher k = KITAP_BASLIGI.matcher(metin);
        while (k.find()) {
            String aday = baslikBuyukKucuk(k.group(1));
            if (!MetadataGuvenlikService.supheliBaslik(aday)) return aday;
        }

        String mevcut = satirTemizle(mevcutBaslik);
        if (dolu(mevcut) && !MetadataGuvenlikService.supheliBaslik(mevcut)) return mevcut;
        String dosya = dosyaAdi == null ? "" : dosyaAdi.replaceFirst("(?i)\\.(pdf|epub|txt|html?|md)$", "");
        String aday = satirTemizle(dosya);
        return MetadataGuvenlikService.supheliBaslik(aday) ? "" : aday;
    }

    private static boolean kolektifIsaretiVar(String metin) {
        String k = metin.toUpperCase(new Locale("tr", "TR"));
        if (k.contains("KATKIDA BULUNANLAR") || k.contains("KATKIDA BULUNAN")) return true;
        int rol = 0;
        for (String anahtar : List.of("DANIŞMAN EDİTÖR", "KIDEMLİ EDİTÖR", "SANAT EDİTÖRÜ", "YAYIN YÖNETMENİ")) {
            if (k.contains(anahtar)) rol++;
        }
        return rol >= 3;
    }

    private static String hakDurumu(String metin) {
        String k = metin.toLowerCase(new Locale("tr", "TR"));
        if (k.contains("tüm yayın hakları") || k.contains("manevi ve mali hakları saklıdır") || k.contains("izni olmaksızın")) {
            return "Telifli — hakları saklı";
        }
        if (k.contains("public domain") || k.contains("kamu malı")) return "Kamu malı";
        if (k.contains("creative commons")) return "Creative Commons — ayrıntı kontrolü gerekli";
        return "";
    }

    private static String grupBul(Pattern p, String s, int grup) {
        if (s == null) return "";
        Matcher m = p.matcher(s);
        return m.find() ? m.group(grup) : "";
    }

    private static String satirTemizle(String s) {
        if (s == null) return "";
        String x = s.replaceAll("[|]+", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("^[.:;,_\\-–— ]+|[.:;,_\\-–— ]+$", "")
                .trim();
        if (x.length() > 180) x = x.substring(0, 180).trim();
        return x;
    }

    private static String kisiTemizle(String s) {
        String x = satirTemizle(s);
        x = x.replaceAll("(?iu)\\s+(?:ISBN|[1-9]\\.?\\s*Basım|Yayıncı|Genel Müdür).*$", "").trim();
        return x.length() >= 2 ? x : "";
    }

    private static String yayineviTemizle(String s) {
        String x = satirTemizle(s)
                .replaceAll("(?iu)\\s*[:：]\\s*\\d+\\s*$", "")
                .replaceAll("(?iu)^©\\s*(?:18|19|20)\\d{2}\\s*,?\\s*", "")
                .trim();
        x = x.replaceAll("(?iu)^ALFA\\b", "Alfa");
        return x;
    }

    private static String baslikBuyukKucuk(String s) {
        String x = satirTemizle(s);
        if (!x.equals(x.toUpperCase(new Locale("tr", "TR")))) return x;
        Locale tr = new Locale("tr", "TR");
        StringBuilder b = new StringBuilder();
        for (String parca : x.toLowerCase(tr).split("\\s+")) {
            if (!b.isEmpty()) b.append(' ');
            if (parca.isBlank()) continue;
            b.append(parca.substring(0, 1).toUpperCase(tr)).append(parca.substring(1));
        }
        return b.toString();
    }

    private static String varsayilan(String s) { return dolu(s) ? s.trim() : "Bilinmiyor"; }
    private static boolean dolu(String s) {
        return s != null && !s.isBlank() && !"Bilinmiyor".equalsIgnoreCase(s.trim()) && !"null".equalsIgnoreCase(s.trim());
    }

    public record Sonuc(String eserAdi, String yazar, String yayinevi, String yayinYili,
                        String isbn, String orijinalAdi, String cevirmen, String basimBilgisi,
                        String lisans, double guvenPuani, String kanit, boolean veriBulundu) {
        public Set<String> bilinenAlanlar() {
            Set<String> s = new LinkedHashSet<>();
            if (dolu(eserAdi)) s.add("eserAdi");
            if (dolu(yazar)) s.add("yazar");
            if (dolu(yayinevi)) s.add("yayinevi");
            if (dolu(yayinYili)) s.add("yayinYili");
            if (dolu(isbn)) s.add("isbn");
            if (dolu(orijinalAdi)) s.add("orijinalAdi");
            if (dolu(cevirmen)) s.add("cevirmen");
            if (dolu(basimBilgisi)) s.add("basimBilgisi");
            if (dolu(lisans)) s.add("lisans");
            return s;
        }
    }
}
