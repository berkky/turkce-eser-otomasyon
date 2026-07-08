import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Yapay zekâ, yerel künye motoru, EPUB metadata, Archive.org ve dosya adını tek kayıtta birleştirir. */
public final class MetadataCikarmaService {
    private final YapayZekaAnalizService yapayZeka;
    private final YerelKunyeAnalizService yerel = new YerelKunyeAnalizService();

    public MetadataCikarmaService() {
        String geminiKey = System.getenv("GEMINI_API_KEY");
        String openAiKey = System.getenv("OPENAI_API_KEY");
        GeminiClient gemini = dolu(geminiKey) ? new GeminiClient(geminiKey) : null;
        OpenAIClient openAI = dolu(openAiKey) ? new OpenAIClient(openAiKey) : null;
        this.yapayZeka = new YapayZekaAnalizService(gemini, openAI);
    }

    public Sonuc cikar(Path onizlemeKaynagi,
                       BelgeMetinCikarmaService.CikarmaSonucu metin,
                       ArchiveOrgCozumleyici.Sonuc archive,
                       String kaynakUrl) {
        String orijinalAd = onizlemeKaynagi == null ? "" : onizlemeKaynagi.getFileName().toString();
        YerelKunyeAnalizService.Sonuc yerelSonuc = yerel.analizEt(
                metin == null ? "" : metin.tamMetin(),
                archive == null ? (metin == null ? "" : metin.baslik()) : archive.hedefBasligi(),
                orijinalAd);

        EserBilgisi ai = null;
        String aiSaglayici = "";
        String aiModel = "Yok";
        String aiHatasi = "";

        try {
            // Yerel motor yeterli künye bulduysa ücretli/kotalı API'yi gereksiz yere çağırma.
            if (aiGerekli(yerelSonuc) && yapayZeka.kullanilabilirServisVarMi()) {
                PdfHazirlayici.PdfVerisi onizleme = onizlemeHazirla(onizlemeKaynagi, metin);
                YapayZekaAnalizService.AnalizSonucu sonuc = yapayZeka.analizEt(onizleme);
                ai = sonuc.getEserBilgisi();
                aiSaglayici = sonuc.getSaglayici();
                aiModel = sonuc.getModel();
            }
        } catch (Exception e) {
            aiHatasi = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }

        EserMetadata m = birlestir(ai, aiSaglayici, aiModel, yerelSonuc, metin, archive, kaynakUrl, orijinalAd);
        if (!aiHatasi.isBlank()) m.kanit = ekle(m.kanit, "AI kullanılamadı: " + kisalt(aiHatasi, 240));
        m.temizle();
        return new Sonuc(m, aiHatasi);
    }

    /**
     * Mevcut bir kaydı yeniden indirmeden yalnız yerel metin üzerinden zenginleştirir.
     * Yerel/OCR sonucu otomatik onay değildir: bilinen kanonik başlık korunur,
     * şüpheli alanlar atılır ve kayıt KONTROL_GEREKIYOR durumunda kalır.
     */
    public EserMetadata yerelOnar(EserMetadata mevcut, String tamMetin, String dosyaAdi) {
        if (mevcut == null) mevcut = new EserMetadata();
        mevcut.temizle();
        YerelKunyeAnalizService.Sonuc y = yerel.analizEt(tamMetin, mevcut.eserAdi, dosyaAdi);

        if (!EserMetadata.bilinen(mevcut.eserAdi) && !MetadataGuvenlikService.supheliBaslik(y.eserAdi())) {
            mevcut.eserAdi = y.eserAdi();
        }
        if (!EserMetadata.bilinen(mevcut.yazar) && !MetadataGuvenlikService.supheliKisi(y.yazar())) {
            mevcut.yazar = y.yazar();
        }
        if (!EserMetadata.bilinen(mevcut.yayinevi) && !MetadataGuvenlikService.supheliYayinevi(y.yayinevi())) {
            mevcut.yayinevi = y.yayinevi();
        }
        mevcut.yayinYili = secBilinmeyen(mevcut.yayinYili, y.yayinYili());
        mevcut.isbn = secBilinmeyen(mevcut.isbn, y.isbn());
        mevcut.orijinalAdi = secBilinmeyen(mevcut.orijinalAdi, y.orijinalAdi());
        if (!EserMetadata.bilinen(mevcut.cevirmen) && !MetadataGuvenlikService.supheliKisi(y.cevirmen())) {
            mevcut.cevirmen = y.cevirmen();
        }
        mevcut.basimBilgisi = secBilinmeyen(mevcut.basimBilgisi, y.basimBilgisi());
        if ((!EserMetadata.bilinen(mevcut.lisans) || "Kontrol edilmedi".equalsIgnoreCase(mevcut.lisans))
                && EserMetadata.bilinen(y.lisans())) mevcut.lisans = y.lisans();

        mevcut.yazar = MetadataGuvenlikService.guvenliYazar(mevcut.yazar);
        mevcut.yayinevi = MetadataGuvenlikService.guvenliYayinevi(mevcut.yayinevi);
        mevcut.bilgiKaynagi = ekleKaynak(mevcut.bilgiKaynagi, "Yerel ön sayfa/OCR künye motoru — doğrulama gerekli");
        mevcut.kanit = ekle(mevcut.kanit, y.kanit());
        mevcut.guvenPuani = Math.max(Math.min(mevcut.guvenPuani, 0.67), MetadataGuvenlikService.yerelOnarimGuveni(mevcut));
        mevcut.metadataDurumu = "KONTROL_GEREKIYOR";
        mevcut.temizle();
        return mevcut;
    }

    private static boolean aiGerekli(YerelKunyeAnalizService.Sonuc y) {
        if (y == null || !y.veriBulundu()) return true;
        boolean temelEksik = !EserMetadata.bilinen(y.eserAdi())
                || (!EserMetadata.bilinen(y.yazar()) && !EserMetadata.bilinen(y.yayinevi()))
                || (!EserMetadata.bilinen(y.isbn()) && !EserMetadata.bilinen(y.yayinYili()));
        return temelEksik || y.guvenPuani() < 0.74;
    }

    private static PdfHazirlayici.PdfVerisi onizlemeHazirla(Path kaynak,
                                                             BelgeMetinCikarmaService.CikarmaSonucu metin) throws Exception {
        String ad = kaynak == null ? "" : kaynak.getFileName().toString().toLowerCase(Locale.ROOT);
        if (kaynak != null && ad.endsWith(".pdf")) return PdfHazirlayici.hazirla(kaynak.toFile(), 3);
        if (kaynak != null && ad.endsWith(".epub")) {
            EpubHazirlayici.EpubVerisi epub = EpubHazirlayici.hazirla(kaynak.toFile(), 3);
            if (!epub.getMetin().isBlank()) {
                return PdfHazirlayici.PdfVerisi.metinBelgesi(epub.getMetin(), "EPUB", "bölüm",
                        Math.max(1, epub.getIncelenenBolumSayisi()), Math.max(1, epub.getToplamBolumSayisi()));
            }
        }
        String tam = metin == null ? "" : metin.tamMetin();
        if (tam.length() > 30_000) tam = tam.substring(0, 30_000);
        if (tam.isBlank()) throw new IllegalArgumentException("Metadata analizi için ön izleme metni bulunamadı.");
        return PdfHazirlayici.PdfVerisi.metinBelgesi(tam, metin.tur().name(), "bölüm", 3,
                Math.max(3, metin.bolumler().size()));
    }

    private static EserMetadata birlestir(EserBilgisi ai, String aiSaglayici, String aiModel,
                                           YerelKunyeAnalizService.Sonuc yerel,
                                           BelgeMetinCikarmaService.CikarmaSonucu metin,
                                           ArchiveOrgCozumleyici.Sonuc archive,
                                           String kaynakUrl, String orijinalAd) {
        EserMetadata m = new EserMetadata();
        String aiBaslik = ai == null ? "" : ai.eser_adi;
        String aiYazar = ai == null ? "" : ai.yazar;
        String aiYayinevi = ai == null ? "" : ai.yayinevi;
        String aiYil = ai == null ? "" : ai.basim_yili;
        String aiDil = ai == null ? "" : ai.dil;
        String aiTur = ai == null ? "" : ai.eser_turu;
        String aiIsbn = ai == null ? "" : ai.isbn;

        String hedefBaslik = archive == null ? "" : archive.hedefBasligi();
        String hedefYazar = archive == null ? "" : archive.hedefYazari();
        String itemBaslik = archive == null ? "" : archive.itemBasligi();
        String itemYazar = archive == null ? "" : archive.itemYazari();

        // Archive.org alt yolundaki hedef başlık, OCR satırlarından daha güvenilir kabul edilir.
        m.eserAdi = baslikSec(aiBaslik, hedefBaslik, metin == null ? "" : metin.baslik(),
                yerel == null ? "" : yerel.eserAdi(), itemBaslik, orijinalAd);
        m.yazar = sec(aiYazar, hedefYazar, metin == null ? "" : metin.yazar(),
                yerel == null ? "" : yerel.yazar(), itemYazar);
        m.yayinevi = sec(aiYayinevi, yerel == null ? "" : yerel.yayinevi(), archive == null ? "" : archive.yayinevi());
        m.yayinYili = sec(aiYil, yerel == null ? "" : yerel.yayinYili(), yilAyikla(archive == null ? "" : archive.yayinTarihi()));
        m.isbn = sec(aiIsbn, yerel == null ? "" : yerel.isbn(), isbnAra(orijinalAd));
        m.orijinalAdi = sec(yerel == null ? "" : yerel.orijinalAdi());
        m.cevirmen = sec(yerel == null ? "" : yerel.cevirmen());
        m.basimBilgisi = sec(yerel == null ? "" : yerel.basimBilgisi());
        m.dil = sec(aiDil, archive == null ? "" : archive.dil(), metin == null ? "" : metin.dil(), "Türkçe");
        m.eserTuru = sec(aiTur, turAdi(metin == null ? null : metin.tur()));
        m.lisans = sec(yerel == null ? "" : yerel.lisans(), archive == null ? "" : archive.lisans(), "Kontrol edilmedi");
        m.kaynakUrl = kaynakUrl == null ? "" : kaynakUrl;
        m.indirmeUrl = archive == null ? "" : archive.indirmeUrl();
        m.archiveIdentifier = archive == null ? "" : archive.identifier();
        m.archiveDosyaAdi = archive == null ? "" : archive.archiveDosyaAdi();
        m.orijinalDosyaAdi = orijinalAd;
        m.bilgiKaynagi = kaynakAciklamasi(ai, aiSaglayici, yerel);
        m.kullanilanAiModeli = ai == null ? "Yok" : aiModel;

        List<String> kanitlar = new ArrayList<>();
        if (yerel != null && dolu(yerel.kanit())) kanitlar.add("Yerel künye: " + yerel.kanit());
        if (ai != null && dolu(ai.kanit)) kanitlar.add("AI: " + ai.kanit.trim());
        if (archive != null && dolu(archive.hedefYolu())) kanitlar.add("Archive hedefi: " + archive.hedefYolu());
        if (archive != null) kanitlar.add("Archive seçimi: " + archive.secimAciklamasi());
        if (metin != null) kanitlar.add("Metin çıkarma: " + metin.yontem());
        m.kanit = String.join(" | ", kanitlar);

        m.guvenPuani = guvenHesapla(m, yerel != null && yerel.veriBulundu(), ai != null,
                archive == null ? 0 : archive.secimPuani());
        if (ai != null && dolu(ai.guven_puani)) {
            try { m.guvenPuani = Math.max(m.guvenPuani, Double.parseDouble(ai.guven_puani.replace(',', '.')) * 0.92); }
            catch (Exception ignored) {}
        }
        m.guvenPuani = Math.min(0.99, m.guvenPuani);
        m.metadataDurumu = durum(m);
        return m;
    }

    private static double guvenHesapla(EserMetadata m, boolean yerelVeri, boolean aiVar, int archivePuani) {
        double guven = 0.05;
        if (EserMetadata.bilinen(m.eserAdi)) guven += 0.25;
        if (EserMetadata.bilinen(m.yazar)) guven += 0.14;
        if (EserMetadata.bilinen(m.yayinevi)) guven += 0.10;
        if (EserMetadata.bilinen(m.yayinYili)) guven += 0.08;
        if (EserMetadata.bilinen(m.isbn)) guven += 0.13;
        if (EserMetadata.bilinen(m.orijinalAdi)) guven += 0.05;
        if (EserMetadata.bilinen(m.cevirmen)) guven += 0.05;
        if (EserMetadata.bilinen(m.basimBilgisi)) guven += 0.03;
        if (archivePuani >= 600) guven += 0.05;
        if (yerelVeri) guven += 0.08;
        if (aiVar) guven += 0.04;
        return Math.min(0.99, guven);
    }

    private static String durum(EserMetadata m) {
        boolean kimlik = EserMetadata.bilinen(m.eserAdi)
                && (EserMetadata.bilinen(m.yazar) || EserMetadata.bilinen(m.yayinevi));
        boolean yayin = EserMetadata.bilinen(m.isbn) || EserMetadata.bilinen(m.yayinYili);
        return kimlik && yayin && m.guvenPuani >= 0.68 ? "HAZIR" : "KONTROL_GEREKIYOR";
    }

    private static String kaynakAciklamasi(EserBilgisi ai, String aiSaglayici, YerelKunyeAnalizService.Sonuc yerel) {
        List<String> l = new ArrayList<>();
        if (yerel != null && yerel.veriBulundu()) l.add("Yerel ön sayfa/OCR künye motoru");
        if (ai != null) l.add(aiSaglayici);
        l.add("Archive.org + gömülü metadata + dosya adı");
        return String.join(" + ", l);
    }

    private static String secBilinmeyen(String mevcut, String aday) {
        return EserMetadata.bilinen(mevcut) ? mevcut : (EserMetadata.bilinen(aday) ? aday : mevcut);
    }

    private static String baslikSec(String... degerler) {
        for (String x : degerler) {
            if (!dolu(x)) continue;
            String t = x.trim();
            if (!dosyaBasligiGibi(t)) return t;
        }
        String dosya = degerler.length == 0 ? "" : degerler[degerler.length - 1];
        String x = dosya == null ? "" : dosya.replaceFirst("(?i)\\.(pdf|epub|txt|html?|md)$", "");
        return dolu(x) ? x : "Bilinmiyor";
    }

    private static boolean dosyaBasligiGibi(String s) { return s.matches("(?i).+\\.(pdf|epub|txt|html?|md)$"); }
    private static String sec(String... degerler) { for (String x : degerler) if (dolu(x)) return x.trim(); return "Bilinmiyor"; }
    private static boolean dolu(String s) { return s != null && !s.isBlank() && !"Bilinmiyor".equalsIgnoreCase(s.trim()) && !"null".equalsIgnoreCase(s.trim()); }
    private static String turAdi(KaynakAlimTuru t) { return t == null ? "Diğer" : switch (t) { case PDF, EPUB -> "Kitap"; case HTML, WEB -> "Web Yazısı"; case TXT -> "Metin"; case ARCHIVE_ORG -> "Eser"; }; }
    private static String yilAyikla(String s) { if (!dolu(s)) return ""; var m=java.util.regex.Pattern.compile("(?:18|19|20)\\d{2}").matcher(s); return m.find()?m.group():s; }
    private static String isbnAra(String s) { if (!dolu(s)) return ""; var m=java.util.regex.Pattern.compile("(?i)(?:97[89][- ]?)?[0-9][- 0-9]{8,16}[0-9X]").matcher(s); return m.find()?m.group().replaceAll("[^0-9Xx]", ""):""; }
    private static String ekle(String a, String b) {
        if (!dolu(b)) return a == null ? "" : a;
        if (a == null || a.isBlank()) return b;
        return a.contains(b) ? a : a + " | " + b;
    }
    private static String ekleKaynak(String a, String b) { if (a == null || a.isBlank()) return b; return a.contains(b) ? a : a + " + " + b; }
    private static String kisalt(String s, int n) { return s.length()<=n?s:s.substring(0,n)+"…"; }

    public record Sonuc(EserMetadata metadata, String aiHatasi) {}
}
