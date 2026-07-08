import java.util.LinkedHashMap;
import java.util.Map;

public final class EserMetadata {
    public String eserAdi = "Bilinmiyor";
    public String eserTuru = "Diğer";
    public String yazar = "Bilinmiyor";
    public String yayinevi = "Bilinmiyor";
    public String yayinYili = "Bilinmiyor";
    public String isbn = "Bilinmiyor";
    public String orijinalAdi = "Bilinmiyor";
    public String cevirmen = "Bilinmiyor";
    public String basimBilgisi = "Bilinmiyor";
    public String dil = "Türkçe";
    public String lisans = "Kontrol edilmedi";
    public String kaynakUrl = "";
    public String indirmeUrl = "";
    public String archiveIdentifier = "";
    public String archiveDosyaAdi = "";
    public String orijinalDosyaAdi = "";
    public String bilgiKaynagi = "Kural tabanlı";
    public String kullanilanAiModeli = "Yok";
    public double guvenPuani = 0.0;
    public String metadataDurumu = "KONTROL_GEREKIYOR";
    public String kanit = "";

    public void temizle() {
        eserAdi = deger(eserAdi, "Bilinmiyor");
        eserTuru = deger(eserTuru, "Diğer");
        yazar = deger(yazar, "Bilinmiyor");
        yayinevi = deger(yayinevi, "Bilinmiyor");
        yayinYili = deger(yayinYili, "Bilinmiyor");
        isbn = isbnTemizle(isbn);
        orijinalAdi = deger(orijinalAdi, "Bilinmiyor");
        cevirmen = deger(cevirmen, "Bilinmiyor");
        basimBilgisi = deger(basimBilgisi, "Bilinmiyor");
        dil = deger(dil, "Türkçe");
        lisans = deger(lisans, "Kontrol edilmedi");
        kaynakUrl = bosDegil(kaynakUrl) ? kaynakUrl.trim() : "";
        indirmeUrl = bosDegil(indirmeUrl) ? indirmeUrl.trim() : "";
        archiveIdentifier = bosDegil(archiveIdentifier) ? archiveIdentifier.trim() : "";
        archiveDosyaAdi = bosDegil(archiveDosyaAdi) ? archiveDosyaAdi.trim() : "";
        orijinalDosyaAdi = bosDegil(orijinalDosyaAdi) ? orijinalDosyaAdi.trim() : "";
        bilgiKaynagi = deger(bilgiKaynagi, "Kural tabanlı");
        kullanilanAiModeli = deger(kullanilanAiModeli, "Yok");
        guvenPuani = Math.max(0.0, Math.min(1.0, guvenPuani));
        metadataDurumu = deger(metadataDurumu, "KONTROL_GEREKIYOR");
        kanit = bosDegil(kanit) ? kanit.trim() : "";
    }

    public Map<String, Object> map() {
        temizle();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eserAdi", eserAdi);
        m.put("eserTuru", eserTuru);
        m.put("yazar", yazar);
        m.put("yayinevi", yayinevi);
        m.put("yayinYili", yayinYili);
        m.put("isbn", isbn);
        m.put("orijinalAdi", orijinalAdi);
        m.put("cevirmen", cevirmen);
        m.put("basimBilgisi", basimBilgisi);
        m.put("dil", dil);
        m.put("lisans", lisans);
        m.put("kaynakUrl", kaynakUrl);
        m.put("indirmeUrl", indirmeUrl);
        m.put("archiveIdentifier", archiveIdentifier);
        m.put("archiveDosyaAdi", archiveDosyaAdi);
        m.put("orijinalDosyaAdi", orijinalDosyaAdi);
        m.put("bilgiKaynagi", bilgiKaynagi);
        m.put("kullanilanAiModeli", kullanilanAiModeli);
        m.put("guvenPuani", guvenPuani);
        m.put("metadataDurumu", metadataDurumu);
        m.put("kanit", kanit);
        return m;
    }

    public static boolean bilinen(String s) {
        return bosDegil(s) && !"Bilinmiyor".equalsIgnoreCase(s.trim()) && !"null".equalsIgnoreCase(s.trim());
    }

    private static String deger(String s, String varsayilan) { return bosDegil(s) && !"null".equalsIgnoreCase(s.trim()) ? s.trim() : varsayilan; }
    private static boolean bosDegil(String s) { return s != null && !s.isBlank(); }
    private static String isbnTemizle(String s) {
        if (!bilinen(s)) return "Bilinmiyor";
        String x = s.replaceAll("(?i)isbn(?:-1[03])?\\s*:?\\s*", "").replaceAll("[^0-9Xx]", "");
        return x.length() == 10 || x.length() == 13 ? x.toUpperCase() : s.trim();
    }
}
