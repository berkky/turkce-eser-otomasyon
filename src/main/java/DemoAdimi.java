/**
 * Demo akış adımı.
 */
public record DemoAdimi(
        int sira,
        String baslik,
        String aciklama,
        String durum,
        String kanit,
        String link,
        int eserId,
        String kaynakTipi
) {
    public static final String DURUM_TAMAMLANDI = "TAMAMLANDI";
    public static final String DURUM_KONTROL = "KONTROL_GEREKIYOR";
    public static final String DURUM_KREDI = "KREDI_BEKLENIYOR";
    public static final String DURUM_DEVAM = "DEVAM_EDIYOR";
}
