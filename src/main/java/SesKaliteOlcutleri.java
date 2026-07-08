/**
 * Ses kalite paneli eşikleri ve sabitleri.
 */
public final class SesKaliteOlcutleri {
    public static final int EN_AZ_GECERLI_MP3_BOYUTU = 128;
    public static final int BUYUK_ESER_PARCA_ESIGI = 80;
    public static final int ASTRONOMI_ESER_ID = 6;
    public static final int KASAGI_ESER_ID = 5;
    public static final String DURUM_GECERLI = "GECERLI";
    public static final String DURUM_GECERSIZ = "GECERSIZ";
    public static final String DURUM_BEKLENIYOR = "BEKLENIYOR";
    public static final String DURUM_MOCK = "MOCK";
    public static final String RISK_DUSUK = "DUSUK";
    public static final String RISK_ORTA = "ORTA";
    public static final String RISK_YUKSEK = "YUKSEK";

    private SesKaliteOlcutleri() {
    }

    public static String krediRiski(long karakter, int parcaSayisi, int eserId) {
        if (eserId == ASTRONOMI_ESER_ID || parcaSayisi >= BUYUK_ESER_PARCA_ESIGI) {
            return RISK_YUKSEK;
        }
        if (karakter > 500_000L) {
            return RISK_YUKSEK;
        }
        if (karakter > 100_000L || parcaSayisi > 30) {
            return RISK_ORTA;
        }
        return RISK_DUSUK;
    }
}
