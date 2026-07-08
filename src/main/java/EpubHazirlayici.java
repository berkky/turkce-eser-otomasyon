import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class EpubHazirlayici {
    private static final int ONIZLEME_MAKSIMUM_KARAKTER = 30_000;
    private static final int TAM_METIN_MAKSIMUM_KARAKTER = 25_000_000;
    private static final int BOLUM_MAKSIMUM_KARAKTER = 2_000_000;
    private static final int MINIMUM_ANLAMLI_BOLUM_KARAKTERI = 10;

    private static final Pattern SAYFA_NUMARASI = Pattern.compile(
            "(?i)^(?:sayfa\\s*)?\\d{1,5}$|^[ivxlcdm]{1,10}$"
    );

    private EpubHazirlayici() {
    }

    /**
     * EPUB'un tamamını okur; ilk belirtilen bölüm sayısından AI ön izlemesi üretir.
     * Tam bölüm listesi de sonuç nesnesinde tutulur ve metin arşivinde kullanılabilir.
     */
    public static EpubVerisi hazirla(File epubDosyasi, int maksimumOnizlemeBolumu) throws Exception {
        if (epubDosyasi == null || !epubDosyasi.isFile()) {
            throw new IllegalArgumentException("EPUB dosyası bulunamadı.");
        }

        try (ZipFile zip = new ZipFile(epubDosyasi, StandardCharsets.UTF_8)) {
            String opfYolu = opfYolunuBul(zip);
            Document opf = xmlOku(zip, opfYolu);

            EserBilgisi metadata = metadataOku(opf);
            Map<String, ManifestKaydi> manifest = manifestOku(opf);
            List<String> spineIdleri = spineOku(opf);

            List<Bolum> bolumler = new ArrayList<>();
            long toplamKarakter = 0;
            int sira = 1;

            for (String idref : spineIdleri) {
                ManifestKaydi kayit = manifest.get(idref);
                if (kayit == null || !metinIcerigiMi(kayit)) {
                    continue;
                }

                String girisYolu = opfYolunaGoreCoz(opfYolu, kayit.href());
                ZipEntry entry = zipEntryBul(zip, girisYolu);
                if (entry == null) {
                    continue;
                }

                HtmlSonucu htmlSonucu = bolumMetniniOku(zip, entry);
                String baslik = bolumBasligiBelirle(htmlSonucu.baslik(), girisYolu, sira);
                String bolumMetni = temizMetin(htmlSonucu.metin());
                bolumMetni = basligiMetindenAyir(bolumMetni, baslik);
                if (bolumMetni.length() < MINIMUM_ANLAMLI_BOLUM_KARAKTERI) {
                    continue;
                }

                if (bolumMetni.length() > BOLUM_MAKSIMUM_KARAKTER) {
                    bolumMetni = bolumMetni.substring(0, BOLUM_MAKSIMUM_KARAKTER).trim();
                }

                boolean tamMetneDahil = tamMetneDahilMi(baslik, girisYolu, bolumMetni);
                bolumler.add(new Bolum(sira++, baslik, bolumMetni, girisYolu, tamMetneDahil));
                toplamKarakter += bolumMetni.length();

                if (toplamKarakter >= TAM_METIN_MAKSIMUM_KARAKTER) {
                    break;
                }
            }

            if (bolumler.stream().noneMatch(Bolum::isTamMetneDahil)) {
                bolumler = bolumler.stream()
                        .map(b -> new Bolum(b.sira(), b.baslik(), b.metin(), b.kaynakYolu(), true))
                        .toList();
            }

            String onizlemeMetni = onizlemeOlustur(bolumler, maksimumOnizlemeBolumu);
            String tamMetin = tamMetinOlustur(bolumler);

            int toplamBolum = bolumler.size();
            metadata.eser_turu = dolu(metadata.eser_turu) ? metadata.eser_turu : "Kitap";
            metadata.sayfa_sayisi = toplamBolum > 0 ? toplamBolum + " bölüm" : "Bilinmiyor";
            metadata.bilinmeyenleriDuzelt();

            int incelenenBolum = (int) bolumler.stream()
                    .filter(Bolum::isTamMetneDahil)
                    .limit(Math.max(1, maksimumOnizlemeBolumu))
                    .count();

            return new EpubVerisi(
                    metadata,
                    onizlemeMetni,
                    tamMetin,
                    bolumler,
                    incelenenBolum,
                    toplamBolum,
                    tamMetin.length()
            );
        }
    }

    public static EpubVerisi tamMetniHazirla(File epubDosyasi) throws Exception {
        return hazirla(epubDosyasi, 3);
    }

    public static boolean metadataYeterliMi(EserBilgisi bilgi) {
        return bilgi != null && doluVeBilinen(bilgi.eser_adi);
    }

    public static EserBilgisi metadataIleBirlestir(EserBilgisi yapayZeka, EserBilgisi metadata) {
        EserBilgisi sonuc = yapayZeka == null ? new EserBilgisi() : yapayZeka;
        if (metadata == null) {
            sonuc.bilinmeyenleriDuzelt();
            return sonuc;
        }

        sonuc.eser_adi = tercihEt(metadata.eser_adi, sonuc.eser_adi);
        sonuc.eser_turu = tercihEt(metadata.eser_turu, sonuc.eser_turu);
        sonuc.yazar = tercihEt(metadata.yazar, sonuc.yazar);
        sonuc.yayinevi = tercihEt(metadata.yayinevi, sonuc.yayinevi);
        sonuc.basim_yili = tercihEt(metadata.basim_yili, sonuc.basim_yili);
        sonuc.dil = tercihEt(metadata.dil, sonuc.dil);
        sonuc.isbn = tercihEt(metadata.isbn, sonuc.isbn);
        sonuc.sayfa_sayisi = tercihEt(metadata.sayfa_sayisi, sonuc.sayfa_sayisi);
        sonuc.bilinmeyenleriDuzelt();
        return sonuc;
    }

    private static String opfYolunuBul(ZipFile zip) throws Exception {
        Document container = xmlOku(zip, "META-INF/container.xml");
        NodeList rootfiles = container.getElementsByTagNameNS("*", "rootfile");
        if (rootfiles.getLength() == 0) {
            rootfiles = container.getElementsByTagName("rootfile");
        }
        if (rootfiles.getLength() == 0) {
            throw new IllegalArgumentException("EPUB içinde OPF paket yolu bulunamadı.");
        }

        Element rootfile = (Element) rootfiles.item(0);
        String fullPath = rootfile.getAttribute("full-path");
        if (fullPath == null || fullPath.isBlank()) {
            throw new IllegalArgumentException("EPUB OPF paket yolu boş.");
        }
        return fullPath.replace('\\', '/');
    }

    private static EserBilgisi metadataOku(Document opf) {
        EserBilgisi bilgi = new EserBilgisi();
        bilgi.eser_adi = ilkMetin(opf, "title");
        bilgi.yazar = birdenFazlaMetin(opf, "creator");
        bilgi.yayinevi = ilkMetin(opf, "publisher");
        bilgi.basim_yili = yilAyikla(ilkMetin(opf, "date"));
        bilgi.dil = dilAdi(ilkMetin(opf, "language"));
        bilgi.isbn = isbnAyikla(birdenFazlaMetin(opf, "identifier"));
        bilgi.eser_turu = "Kitap";
        bilgi.lisans = "Kontrol edilmedi";
        bilgi.seslendirme_durumu = "Bekliyor";
        return bilgi;
    }

    private static Map<String, ManifestKaydi> manifestOku(Document opf) {
        Map<String, ManifestKaydi> sonuc = new HashMap<>();
        NodeList itemlar = opf.getElementsByTagNameNS("*", "item");
        if (itemlar.getLength() == 0) {
            itemlar = opf.getElementsByTagName("item");
        }

        for (int i = 0; i < itemlar.getLength(); i++) {
            Node node = itemlar.item(i);
            if (!(node instanceof Element item)) {
                continue;
            }
            String id = item.getAttribute("id");
            String href = item.getAttribute("href");
            String mediaType = item.getAttribute("media-type");
            if (!id.isBlank() && !href.isBlank()) {
                sonuc.put(id, new ManifestKaydi(href, mediaType));
            }
        }
        return sonuc;
    }

    private static List<String> spineOku(Document opf) {
        List<String> sonuc = new ArrayList<>();
        NodeList itemrefler = opf.getElementsByTagNameNS("*", "itemref");
        if (itemrefler.getLength() == 0) {
            itemrefler = opf.getElementsByTagName("itemref");
        }

        for (int i = 0; i < itemrefler.getLength(); i++) {
            Node node = itemrefler.item(i);
            if (node instanceof Element itemref) {
                String idref = itemref.getAttribute("idref");
                if (!idref.isBlank()) {
                    sonuc.add(idref);
                }
            }
        }
        return sonuc;
    }

    /**
     * Bölüm XHTML/HTML dosyaları XML olarak ayrıştırılmaz. Böylece EPUB içindeki
     * DOCTYPE satırları konsola gereksiz "Fatal Error" yazdırmaz.
     */
    private static HtmlSonucu bolumMetniniOku(ZipFile zip, ZipEntry entry) throws Exception {
        byte[] baytlar;
        try (InputStream input = zip.getInputStream(entry)) {
            baytlar = input.readAllBytes();
        }

        String html = new String(baytlar, StandardCharsets.UTF_8);
        HtmlAyiklayici ayiklayici = new HtmlAyiklayici();
        new ParserDelegator().parse(new StringReader(html), ayiklayici, true);
        return new HtmlSonucu(ayiklayici.getBaslik(), ayiklayici.getMetin());
    }

    private static ZipEntry zipEntryBul(ZipFile zip, String yol) {
        ZipEntry entry = zip.getEntry(yol);
        if (entry == null) {
            entry = zip.getEntry(urlCoz(yol));
        }
        return entry;
    }

    private static Document xmlOku(ZipFile zip, String yol) throws Exception {
        ZipEntry entry = zipEntryBul(zip, yol);
        if (entry == null) {
            throw new IllegalArgumentException("EPUB içinde dosya bulunamadı: " + yol);
        }
        try (InputStream input = zip.getInputStream(entry)) {
            return xmlBuilder().parse(input);
        }
    }

    private static DocumentBuilder xmlBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // Bazı XML ayrıştırıcılarında bu özellikler desteklenmeyebilir.
        }

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override
            public void warning(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                throw e;
            }

            @Override
            public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                throw e;
            }

            @Override
            public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                throw e;
            }
        });
        return builder;
    }

    private static String onizlemeOlustur(List<Bolum> bolumler, int maksimumBolum) {
        int limit = Math.max(1, maksimumBolum);
        StringBuilder sonuc = new StringBuilder();
        int eklenen = 0;

        for (Bolum bolum : bolumler) {
            if (!bolum.tamMetneDahil()) {
                continue;
            }
            if (!sonuc.isEmpty()) {
                sonuc.append("\n\n");
            }
            sonuc.append(bolum.baslik()).append("\n").append(bolum.metin());
            eklenen++;
            if (eklenen >= limit || sonuc.length() >= ONIZLEME_MAKSIMUM_KARAKTER) {
                break;
            }
        }

        String metin = sonuc.toString().trim();
        if (metin.length() > ONIZLEME_MAKSIMUM_KARAKTER) {
            metin = metin.substring(0, ONIZLEME_MAKSIMUM_KARAKTER).trim();
        }
        return metin;
    }

    private static String tamMetinOlustur(List<Bolum> bolumler) {
        StringBuilder sonuc = new StringBuilder();
        for (Bolum bolum : bolumler) {
            if (!bolum.tamMetneDahil()) {
                continue;
            }
            if (!sonuc.isEmpty()) {
                sonuc.append("\n\n====================\n\n");
            }
            sonuc.append(bolum.baslik()).append("\n\n").append(bolum.metin());
            if (sonuc.length() >= TAM_METIN_MAKSIMUM_KARAKTER) {
                break;
            }
        }

        String metin = sonuc.toString().trim();
        if (metin.length() > TAM_METIN_MAKSIMUM_KARAKTER) {
            metin = metin.substring(0, TAM_METIN_MAKSIMUM_KARAKTER).trim();
        }
        return metin;
    }

    private static String temizMetin(String hamMetin) {
        if (hamMetin == null || hamMetin.isBlank()) {
            return "";
        }

        String normalized = hamMetin
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        String[] hamSatirlar = normalized.split("\\n");
        List<String> satirlar = new ArrayList<>();
        String onceki = null;

        for (String hamSatir : hamSatirlar) {
            String satir = hamSatir.replaceAll("[\\t\\x0B\\f ]+", " ").trim();
            if (satir.isBlank()) {
                if (!satirlar.isEmpty() && !satirlar.getLast().isBlank()) {
                    satirlar.add("");
                }
                continue;
            }
            if (SAYFA_NUMARASI.matcher(satir).matches()) {
                continue;
            }
            if (onceki != null && onceki.equalsIgnoreCase(satir)) {
                continue;
            }
            satirlar.add(satir);
            onceki = satir;
        }

        while (!satirlar.isEmpty() && satirlar.getLast().isBlank()) {
            satirlar.removeLast();
        }

        String metin = String.join("\n", satirlar)
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return metin;
    }


    private static String basligiMetindenAyir(String metin, String baslik) {
        if (metin == null || metin.isBlank() || baslik == null || baslik.isBlank()) {
            return metin == null ? "" : metin;
        }
        String[] satirlar = metin.split("\n", 2);
        String ilkSatir = satirlar[0].replaceAll("\\s+", " ").trim();
        String temizBaslik = baslik.replaceAll("\\s+", " ").trim();
        if (ilkSatir.equalsIgnoreCase(temizBaslik)) {
            return satirlar.length > 1 ? satirlar[1].trim() : "";
        }
        return metin;
    }

    private static boolean tamMetneDahilMi(String baslik, String kaynakYolu, String metin) {
        String kontrol = (baslik + " " + kaynakYolu)
                .toLowerCase(Locale.ROOT)
                .replace('ı', 'i');

        boolean teknikSayfa = kontrol.contains("cover")
                || kontrol.contains("kapak")
                || kontrol.contains("toc")
                || kontrol.contains("nav")
                || kontrol.contains("contents")
                || kontrol.contains("icindekiler")
                || kontrol.contains("copyright")
                || kontrol.contains("titlepage");

        return !teknikSayfa || metin.length() >= 2_000;
    }

    private static String bolumBasligiBelirle(String htmlBasligi, String kaynakYolu, int sira) {
        if (htmlBasligi != null && !htmlBasligi.isBlank()) {
            String temiz = htmlBasligi.replaceAll("\\s+", " ").trim();
            if (temiz.length() <= 180) {
                return temiz;
            }
        }

        String ad = kaynakYolu;
        int slash = ad.lastIndexOf('/');
        if (slash >= 0) {
            ad = ad.substring(slash + 1);
        }
        ad = urlCoz(ad).replaceAll("(?i)\\.(xhtml|html|htm)$", "");
        ad = ad.replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ").trim();
        if (ad.isBlank() || ad.matches("(?i)(chapter|bolum|section)?\\s*\\d+")) {
            return "Bölüm " + sira;
        }
        return ad;
    }

    private static String ilkMetin(Document document, String localName) {
        NodeList nodeList = document.getElementsByTagNameNS("*", localName);
        if (nodeList.getLength() == 0) {
            nodeList = document.getElementsByTagName(localName);
        }
        for (int i = 0; i < nodeList.getLength(); i++) {
            String value = nodeList.item(i).getTextContent();
            if (value != null && !value.isBlank()) {
                return value.replaceAll("\\s+", " ").trim();
            }
        }
        return "";
    }

    private static String birdenFazlaMetin(Document document, String localName) {
        NodeList nodeList = document.getElementsByTagNameNS("*", localName);
        if (nodeList.getLength() == 0) {
            nodeList = document.getElementsByTagName(localName);
        }
        List<String> degerler = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            String value = nodeList.item(i).getTextContent();
            if (value != null && !value.isBlank()) {
                String temiz = value.replaceAll("\\s+", " ").trim();
                if (!degerler.contains(temiz)) {
                    degerler.add(temiz);
                }
            }
        }
        return String.join(", ", degerler);
    }

    private static boolean metinIcerigiMi(ManifestKaydi kayit) {
        String media = kayit.mediaType() == null ? "" : kayit.mediaType().toLowerCase(Locale.ROOT);
        String href = kayit.href().toLowerCase(Locale.ROOT);
        return media.contains("xhtml")
                || media.contains("html")
                || href.endsWith(".xhtml")
                || href.endsWith(".html")
                || href.endsWith(".htm");
    }

    private static String opfYolunaGoreCoz(String opfYolu, String href) {
        String baseDir = "";
        int slash = opfYolu.lastIndexOf('/');
        if (slash >= 0) {
            baseDir = opfYolu.substring(0, slash + 1);
        }
        URI base = URI.create("https://epub.local/" + baseDir);
        String path = base.resolve(href).getPath();
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static String urlCoz(String metin) {
        try {
            return URLDecoder.decode(metin, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return metin;
        }
    }

    private static String isbnAyikla(String metin) {
        if (metin == null || metin.isBlank()) return "Bilinmiyor";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)(?:97[89][- ]?)?[0-9][- 0-9]{8,16}[0-9X]").matcher(metin);
        if (!m.find()) return "Bilinmiyor";
        String x = m.group().replaceAll("[^0-9Xx]", "").toUpperCase(Locale.ROOT);
        return x.length() == 10 || x.length() == 13 ? x : "Bilinmiyor";
    }

    private static String yilAyikla(String deger) {
        if (deger == null) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:18|19|20|21)\\d{2}")
                .matcher(deger);
        return matcher.find() ? matcher.group() : deger.trim();
    }

    private static String dilAdi(String kod) {
        if (kod == null || kod.isBlank()) {
            return "";
        }
        String temiz = kod.trim().toLowerCase(Locale.ROOT);
        return switch (temiz) {
            case "tr", "tr-tr", "tur", "turkish" -> "Türkçe";
            case "en", "en-us", "en-gb", "eng", "english" -> "İngilizce";
            case "de", "de-de", "ger", "deu", "german" -> "Almanca";
            case "fr", "fr-fr", "fre", "fra", "french" -> "Fransızca";
            default -> kod.trim();
        };
    }

    private static String tercihEt(String birinci, String ikinci) {
        if (doluVeBilinen(birinci)) {
            return birinci.trim();
        }
        return ikinci;
    }

    private static boolean dolu(String deger) {
        return deger != null && !deger.isBlank();
    }

    private static boolean doluVeBilinen(String deger) {
        return dolu(deger)
                && !"Bilinmiyor".equalsIgnoreCase(deger.trim())
                && !"null".equalsIgnoreCase(deger.trim());
    }

    private static boolean blokEtiketi(HTML.Tag tag) {
        String ad = tag.toString().toLowerCase(Locale.ROOT);
        return ad.matches("h[1-6]")
                || ad.equals("p")
                || ad.equals("div")
                || ad.equals("section")
                || ad.equals("article")
                || ad.equals("blockquote")
                || ad.equals("li")
                || ad.equals("ul")
                || ad.equals("ol")
                || ad.equals("table")
                || ad.equals("tr");
    }

    private static boolean baslikEtiketi(HTML.Tag tag) {
        return tag.toString().toLowerCase(Locale.ROOT).matches("h[1-6]");
    }

    private record ManifestKaydi(String href, String mediaType) {
    }

    private record HtmlSonucu(String baslik, String metin) {
    }

    public record Bolum(int sira, String baslik, String metin, String kaynakYolu, boolean tamMetneDahil) {
        public boolean isTamMetneDahil() {
            return tamMetneDahil;
        }
    }

    private static final class HtmlAyiklayici extends HTMLEditorKit.ParserCallback {
        private final StringBuilder metin = new StringBuilder();
        private final StringBuilder ilkBaslik = new StringBuilder();
        private boolean baslikIcindeyiz;
        private int atlaDerinligi;

        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            String ad = tag.toString().toLowerCase(Locale.ROOT);
            if (ad.equals("script") || ad.equals("style") || ad.equals("head")) {
                atlaDerinligi++;
                return;
            }
            if (atlaDerinligi > 0) {
                return;
            }
            if (baslikEtiketi(tag) && ilkBaslik.isEmpty()) {
                baslikIcindeyiz = true;
            }
            if (blokEtiketi(tag)) {
                yeniSatir();
            }
            if (ad.equals("li")) {
                metin.append("• ");
            }
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
            String ad = tag.toString().toLowerCase(Locale.ROOT);
            if (ad.equals("script") || ad.equals("style") || ad.equals("head")) {
                if (atlaDerinligi > 0) {
                    atlaDerinligi--;
                }
                return;
            }
            if (atlaDerinligi > 0) {
                return;
            }
            if (baslikEtiketi(tag)) {
                baslikIcindeyiz = false;
            }
            if (blokEtiketi(tag)) {
                yeniSatir();
            }
        }

        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (atlaDerinligi > 0) {
                return;
            }
            String ad = tag.toString().toLowerCase(Locale.ROOT);
            if (ad.equals("br") || ad.equals("hr")) {
                yeniSatir();
            }
        }

        @Override
        public void handleText(char[] data, int position) {
            if (atlaDerinligi > 0 || data == null || data.length == 0) {
                return;
            }
            String parca = new String(data).replaceAll("\\s+", " ").trim();
            if (parca.isBlank()) {
                return;
            }
            if (!metin.isEmpty() && !Character.isWhitespace(metin.charAt(metin.length() - 1))) {
                metin.append(' ');
            }
            metin.append(parca);

            if (baslikIcindeyiz && ilkBaslik.length() < 180) {
                if (!ilkBaslik.isEmpty()) {
                    ilkBaslik.append(' ');
                }
                ilkBaslik.append(parca);
            }
        }

        private void yeniSatir() {
            int uzunluk = metin.length();
            if (uzunluk == 0) {
                return;
            }
            if (metin.charAt(uzunluk - 1) != '\n') {
                metin.append('\n');
            }
        }

        String getBaslik() {
            return ilkBaslik.toString().replaceAll("\\s+", " ").trim();
        }

        String getMetin() {
            return metin.toString();
        }
    }

    public static final class EpubVerisi {
        private final EserBilgisi metadata;
        private final String metin;
        private final String tamMetin;
        private final List<Bolum> bolumler;
        private final int incelenenBolumSayisi;
        private final int toplamBolumSayisi;
        private final int toplamKarakterSayisi;

        private EpubVerisi(EserBilgisi metadata,
                           String metin,
                           String tamMetin,
                           List<Bolum> bolumler,
                           int incelenenBolumSayisi,
                           int toplamBolumSayisi,
                           int toplamKarakterSayisi) {
            this.metadata = metadata;
            this.metin = metin;
            this.tamMetin = tamMetin;
            this.bolumler = List.copyOf(bolumler);
            this.incelenenBolumSayisi = incelenenBolumSayisi;
            this.toplamBolumSayisi = toplamBolumSayisi;
            this.toplamKarakterSayisi = toplamKarakterSayisi;
        }

        public EserBilgisi getMetadata() {
            return metadata;
        }

        /** AI'ye gönderilecek sınırlı ön izleme metni. */
        public String getMetin() {
            return metin;
        }

        /** Seslendirme hazırlığı için temizlenmiş tam metin. */
        public String getTamMetin() {
            return tamMetin;
        }

        public List<Bolum> getBolumler() {
            return bolumler;
        }

        public int getIncelenenBolumSayisi() {
            return incelenenBolumSayisi;
        }

        public int getToplamBolumSayisi() {
            return toplamBolumSayisi;
        }

        public int getToplamKarakterSayisi() {
            return toplamKarakterSayisi;
        }
    }
}
