import java.nio.file.Path;

/**
 * CLI: ElevenLabs önizleme üretimi.
 * Kullanım: onizleme [eserId]
 */
public final class ElevenLabsOnizlemeApp {
    public static void main(String[] args) {
        Utf8Konsol.etkinlestir();
        int eserId = 5;
        if (args.length > 0) {
            try {
                eserId = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException e) {
                System.err.println("Geçersiz eser ID: " + args[0]);
                System.exit(2);
            }
        }

        Path masaustu = Path.of(System.getProperty("user.home"), "Desktop");
        Path metinArsivi = ortamYolu("ESER_METIN_ARSIVI", masaustu.resolve("metin-arsivi"));
        Path sesArsivi = ortamYolu("ESER_SES_ARSIVI", masaustu.resolve("ses-arsivi"));

        System.out.println("--- ELEVENLABS ÖNİZLEME ---");
        System.out.println("Eser ID: ESER-" + String.format("%05d", eserId));
        System.out.println(ElevenLabsFabrika.durumOzeti().konsolOzeti());
        System.out.println();

        ElevenLabsOnizlemeService.OnizlemeSonucu sonuc =
                ElevenLabsOnizlemeService.uret(eserId, metinArsivi, sesArsivi, null);

        if (sonuc.basarili()) {
            System.out.println(sonuc.mesaj());
            System.out.println("MP3 : " + sonuc.mp3());
            System.out.println("JSON: " + sonuc.json());
            System.exit(0);
        }

        System.err.println("Önizleme tamamlanamadı: " + sonuc.mesaj());
        System.exit(1);
    }

    private static Path ortamYolu(String ad, Path varsayilan) {
        String deger = System.getenv(ad);
        return deger == null || deger.isBlank() ? varsayilan : Path.of(deger.trim());
    }
}
