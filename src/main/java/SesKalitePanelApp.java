import java.nio.file.Path;

/**
 * Ses kalite paneli CLI — statik HTML/JSON/CSV/Markdown üretir.
 */
public final class SesKalitePanelApp {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        Path proje = Path.of(System.getProperty("user.dir"));
        EserVeriYollari yollar = EserVeriYollari.varsayilan();
        Path sesArsivi = yollar.ses();
        Path metinArsivi = yollar.metin();
        Path panelKlasoru = yollar.kalitePanel();

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
}
