import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BelgeMetinCikarmaService {
    private static final int PDF_SAYFA_GRUBU = 20;
    private static final int PDF_MINIMUM_TOPLAM_KARAKTER = 500;
    private static final Pattern HTML_TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern SCRIPT_STYLE = Pattern.compile("(?is)<(script|style|noscript|svg|canvas)[^>]*>.*?</\\1>");
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<[^>]+>");

    private BelgeMetinCikarmaService() {}

    public static CikarmaSonucu cikar(Path dosya) throws Exception {
        String ad = dosya.getFileName().toString().toLowerCase(Locale.ROOT);
        if (ad.endsWith(".pdf")) return pdf(dosya);
        if (ad.endsWith(".epub")) return epub(dosya);
        if (ad.endsWith(".txt") || ad.endsWith(".md")) return metin(dosya);
        if (ad.endsWith(".html") || ad.endsWith(".htm")) return html(dosya);
        throw new IllegalArgumentException("Desteklenmeyen kaynak türü: " + dosya.getFileName());
    }

    public static CikarmaSonucu cikarYerelOcr(Path pdf) throws Exception {
        if (pdf == null || !Files.isRegularFile(pdf) || !pdf.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
            throw new IllegalArgumentException("Yerel OCR için PDF dosyası gerekli.");
        YerelOcrService.OcrSonucu ocr = YerelOcrService.pdfOcr(pdf, 0);
        List<BolumMetni> bolumler = new ArrayList<>();
        StringBuilder grup = new StringBuilder();
        int grupBaslangic = 1;
        int sira = 1;
        for (YerelOcrService.SayfaMetni sayfa : ocr.sayfalar()) {
            if (!grup.isEmpty()) grup.append("\n\n");
            grup.append(sayfa.metin());
            boolean grupSonu = sayfa.sayfa() % PDF_SAYFA_GRUBU == 0 || sayfa.sayfa() == ocr.okunanSayfa();
            if (grupSonu) {
                String metin = metniTemizle(grup.toString());
                if (!metin.isBlank()) bolumler.add(new BolumMetni(sira++, "Sayfa " + grupBaslangic + (sayfa.sayfa()==grupBaslangic?"":"-"+sayfa.sayfa()), metin));
                grup.setLength(0);
                grupBaslangic = sayfa.sayfa() + 1;
            }
        }
        String tam = birlestir(bolumler);
        if (tam.length() < PDF_MINIMUM_TOPLAM_KARAKTER)
            throw new IllegalStateException("Yerel OCR yeterli metin üretemedi. Karakter=" + tam.length());
        return new CikarmaSonucu(KaynakAlimTuru.PDF, uzantisiz(pdf.getFileName().toString()),
                "Bilinmiyor", "Türkçe", bolumler, tam, ocr.toplamSayfa(), true,
                "Tesseract yerel OCR (" + ocr.dil() + ", " + ocr.dpi() + " DPI)");
    }

    private static CikarmaSonucu epub(Path dosya) throws Exception {
        EpubHazirlayici.EpubVerisi veri = EpubHazirlayici.tamMetniHazirla(dosya.toFile());
        List<BolumMetni> bolumler = veri.getBolumler().stream()
                .filter(EpubHazirlayici.Bolum::tamMetneDahil)
                .map(b -> new BolumMetni(b.sira(), temizBaslik(b.baslik(), "Bölüm " + b.sira()), metniTemizle(b.metin())))
                .filter(b -> !b.metin().isBlank())
                .toList();
        if (bolumler.isEmpty()) throw new IllegalArgumentException("EPUB içinde okunabilir metin bulunamadı.");
        EserBilgisi m = veri.getMetadata();
        String baslik = dolu(m.eser_adi) ? m.eser_adi : uzantisiz(dosya.getFileName().toString());
        return new CikarmaSonucu(KaynakAlimTuru.EPUB, baslik, m.yazar, m.dil,
                bolumler, birlestir(bolumler), veri.getToplamBolumSayisi(), false, "EPUB spine metni");
    }

    private static CikarmaSonucu pdf(Path dosya) throws Exception {
        try (PDDocument belge = PDDocument.load(dosya.toFile())) {
            int sayfa = belge.getNumberOfPages();
            if (sayfa < 1) throw new IllegalArgumentException("PDF içinde sayfa bulunamadı.");
            List<BolumMetni> bolumler = new ArrayList<>();
            int sira = 1;
            int toplam = 0;
            for (int bas = 1; bas <= sayfa; bas += PDF_SAYFA_GRUBU) {
                int son = Math.min(sayfa, bas + PDF_SAYFA_GRUBU - 1);
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(bas);
                stripper.setEndPage(son);
                stripper.setSortByPosition(true);
                String metin = metniTemizle(stripper.getText(belge));
                toplam += metin.length();
                if (!metin.isBlank()) {
                    bolumler.add(new BolumMetni(sira++, "Sayfa " + bas + (son == bas ? "" : "-" + son), metin));
                }
            }
            boolean ocrGerekli = toplam < PDF_MINIMUM_TOPLAM_KARAKTER || toplam < sayfa * 20L;
            if (ocrGerekli) {
                throw new OcrGerekliException("PDF büyük ölçüde taranmış/görsel görünüyor. Yerel Türkçe OCR gerekir. "
                        + "Sayfa=" + sayfa + ", çıkarılan karakter=" + toplam);
            }
            return new CikarmaSonucu(KaynakAlimTuru.PDF, uzantisiz(dosya.getFileName().toString()),
                    "Bilinmiyor", "Türkçe", bolumler, birlestir(bolumler), sayfa, false,
                    "PDFBox tam metin");
        }
    }

    private static CikarmaSonucu metin(Path dosya) throws Exception {
        String ham = guvenliMetinOku(dosya);
        String temiz = metniTemizle(ham);
        if (temiz.length() < 20) throw new IllegalArgumentException("Metin dosyası boş veya çok kısa.");
        List<BolumMetni> bolumler = basliklaraGoreBol(temiz);
        return new CikarmaSonucu(KaynakAlimTuru.TXT, uzantisiz(dosya.getFileName().toString()),
                "Bilinmiyor", "Türkçe", bolumler, birlestir(bolumler), bolumler.size(), false,
                "UTF-8/metin");
    }

    private static CikarmaSonucu html(Path dosya) throws Exception {
        String ham = guvenliMetinOku(dosya);
        return htmlIcerigi(ham, uzantisiz(dosya.getFileName().toString()));
    }

    public static CikarmaSonucu htmlIcerigi(String html, String varsayilanBaslik) {
        String baslik = varsayilanBaslik;
        Matcher tm = HTML_TITLE.matcher(html == null ? "" : html);
        if (tm.find()) baslik = htmlVarliklariniCoz(HTML_TAG.matcher(tm.group(1)).replaceAll(" ")).trim();
        String govde = SCRIPT_STYLE.matcher(html == null ? "" : html).replaceAll(" ");
        govde = govde.replaceAll("(?is)</(p|div|article|section|h[1-6]|li|br|tr)>", "\n\n");
        govde = HTML_TAG.matcher(govde).replaceAll(" ");
        govde = metniTemizle(htmlVarliklariniCoz(govde));
        if (govde.length() < 100) throw new IllegalArgumentException("Web/HTML kaynağında yeterli okunabilir metin bulunamadı.");
        List<BolumMetni> bolumler = basliklaraGoreBol(govde);
        return new CikarmaSonucu(KaynakAlimTuru.HTML, temizBaslik(baslik, varsayilanBaslik),
                "Bilinmiyor", "Türkçe", bolumler, birlestir(bolumler), bolumler.size(), false,
                "HTML sadeleştirme");
    }

    private static List<BolumMetni> basliklaraGoreBol(String metin) {
        String[] paragraflar = metin.split("\\n\\s*\\n+");
        List<BolumMetni> sonuc = new ArrayList<>();
        String baslik = "Metin";
        StringBuilder govde = new StringBuilder();
        int sira = 1;
        for (String p : paragraflar) {
            String t = p.trim();
            if (t.isBlank()) continue;
            boolean baslikMi = t.length() <= 100 && !t.endsWith(".") && t.split("\\s+").length <= 12;
            if (baslikMi && govde.length() >= 500) {
                sonuc.add(new BolumMetni(sira++, baslik, govde.toString().trim()));
                baslik = t;
                govde.setLength(0);
            } else {
                if (!govde.isEmpty()) govde.append("\n\n");
                govde.append(t);
            }
        }
        if (!govde.isEmpty()) sonuc.add(new BolumMetni(sira, baslik, govde.toString().trim()));
        if (sonuc.isEmpty()) sonuc.add(new BolumMetni(1, "Metin", metin));
        return sonuc;
    }

    private static String guvenliMetinOku(Path dosya) throws Exception {
        byte[] b = Files.readAllBytes(dosya);
        String utf8 = new String(b, StandardCharsets.UTF_8);
        long bozuk = utf8.chars().filter(c -> c == 0xFFFD).count();
        if (bozuk <= 3) return utf8.replace("\uFEFF", "");
        return new String(b, Charset.forName("windows-1254"));
    }

    public static String metniTemizle(String metin) {
        if (metin == null) return "";
        return metin.replace('\u00A0', ' ')
                .replace("\u200B", "")
                .replaceAll("(?m)[ \\t]+$", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static String htmlVarliklariniCoz(String s) {
        return s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    private static String birlestir(List<BolumMetni> b) {
        StringBuilder s = new StringBuilder();
        for (BolumMetni x : b) {
            if (!s.isEmpty()) s.append("\n\n");
            s.append(x.baslik()).append("\n\n").append(x.metin());
        }
        return s.toString().trim();
    }

    private static String temizBaslik(String s, String varsayilan) {
        return dolu(s) ? s.trim().replaceAll("\\s+", " ") : varsayilan;
    }
    private static boolean dolu(String s) { return s != null && !s.isBlank() && !"Bilinmiyor".equalsIgnoreCase(s.trim()); }
    private static String uzantisiz(String ad) { int i = ad.lastIndexOf('.'); return i > 0 ? ad.substring(0, i) : ad; }

    public record BolumMetni(int sira, String baslik, String metin) {}
    public record CikarmaSonucu(KaynakAlimTuru tur, String baslik, String yazar, String dil,
                                List<BolumMetni> bolumler, String tamMetin, int kaynakBirimSayisi,
                                boolean ocrKullanildi, String yontem) {}

    public static final class OcrGerekliException extends Exception {
        public OcrGerekliException(String mesaj) { super(mesaj); }
    }
}
