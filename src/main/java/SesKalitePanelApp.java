import java.nio.file.Path;

/**
 * Ses kalite paneli CLI — statik HTML/JSON/CSV/Markdown üretir.
 */
public final class SesKalitePanelApp {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        Path proje = Path.of(System.getProperty("user.dir"));
        Path masaustu = Path.of(System.getProperty("user.home"), "Desktop");

        Path sesArsivi = ortamYolu("ESER_SES_ARSIVI", masaustu.resolve("ses-arsivi"));
        Path metinArsivi = ortamYolu("ESER_METIN_ARSIVI", masaustu.resolve("metin-arsivi"));
        Path panelKlasoru = ortamYolu("SES_KALITE_PANEL", masaustu.resolve("ses-arsivi_kalite-panel"));

        System.out.println("========================================");
        System.out.println("SES KALİTE PANELİ ÜRETİMİ");
        System.out.println("========================================");
        System.out.println("Ses arşivi    : " + sesArsivi);
        System.out.println("Metin arşivi  : " + metinArsivi);
        System.out.println("Panel klasörü : " + panelKlasoru);
        System.out.println();
        System.out.println("Not: Gerçek ElevenLabs API çağrısı yapılmaz.");
        System.out.println("Not: Var olan arşiv dosyalarına yazılmaz.");
        System.out.println();

        SesKalitePanelService.PanelSonucu sonuc =
                SesKalitePanelService.uret(sesArsivi, metinArsivi, panelKlasoru, proje);

        System.out.println("Panel üretildi.");
        System.out.println("HTML : " + sonuc.html());
        System.out.println("JSON : " + sonuc.json());
        System.out.println("CSV  : " + sonuc.csv());
        System.out.println("MD   : " + sonuc.md());
        System.out.println("Önizleme sayısı: " + sonuc.rapor().onizlemeler().size());
        System.out.println();
        System.out.println("Tarayıcıda açın: " + sonuc.html().toUri());
    }

    private static Path ortamYolu(String ad, Path varsayilan) {
        String deger = System.getenv(ad);
        return deger == null || deger.isBlank() ? varsayilan : Path.of(deger.trim());
    }
}
