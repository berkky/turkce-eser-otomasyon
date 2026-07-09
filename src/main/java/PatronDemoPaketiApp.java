/**
 * Patron demo paketi CLI.
 */
public final class PatronDemoPaketiApp {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        WebOrtam ortam = WebOrtam.varsayilan();
        DemoRaporService rapor = new DemoRaporService(ortam);
        DemoRaporService.PaketSonucu sonuc = rapor.uret();

        System.out.println("========================================");
        System.out.println("PATRON DEMO PAKETİ OLUŞTURULDU");
        System.out.println("========================================");
        System.out.println("Klasör: " + sonuc.klasor());
        for (String dosya : sonuc.dosyalar()) {
            System.out.println("  ✓ " + dosya);
        }
        System.out.println();
        System.out.println("Tarayıcıda açın: " + sonuc.klasor().resolve("patron-demo-ozeti.html"));
        System.out.println("Web panel: http://127.0.0.1:8787/demo");
    }
}
