import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Demo mock önizleme verisi oluşturur.
 */
public final class SesKalitePanelDemoApp {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        Path sesArsiv = args.length > 0
                ? Path.of(args[0])
                : EserVeriYollari.varsayilan().ses();
        Files.createDirectories(sesArsiv);
        SesKalitePanelDemoService.demoVerisiOlustur(sesArsiv);
        System.out.println("Demo mock önizleme verisi oluşturuldu: " + sesArsiv);
        System.out.println("MOCK — gerçek TTS değildir; test amaçlıdır.");
    }
}
