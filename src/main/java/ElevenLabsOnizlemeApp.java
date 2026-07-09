import java.nio.file.Path;

/**
 * CLI: ElevenLabs önizleme üretimi.
 * Kullanım: onizleme [eserId]
 */
public final class ElevenLabsOnizlemeApp {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        int eserId = 5;
        boolean refreshPanel = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            if ("-RefreshPanel".equalsIgnoreCase(arg) || "--refresh-panel".equalsIgnoreCase(arg)) {
                refreshPanel = true;
                continue;
            }
            if (arg.startsWith("-")) {
                continue;
            }
            try {
                eserId = Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                System.err.println("Geçersiz eser ID: " + arg);
                System.exit(2);
            }
        }

        Path masaustu = Path.of(System.getProperty("user.home"), "Desktop");
        Path metinArsivi = ortamYolu("ESER_METIN_ARSIVI", masaustu.resolve("metin-arsivi"));
        Path sesArsivi = ortamYolu("ESER_SES_ARSIVI", masaustu.resolve("ses-arsivi"));
        Path kalitePanel = ortamYolu("SES_KALITE_PANEL", masaustu.resolve("ses-arsivi_kalite-panel"));

        System.out.println("--- ELEVENLABS ÖNİZLEME ---");
        System.out.println("Eser ID: ESER-" + String.format("%05d", eserId));
        System.out.println(ElevenLabsFabrika.durumOzeti().konsolOzeti());
        System.out.println();

        ElevenLabsOnizlemeService.OnizlemeSonucu sonuc =
                ElevenLabsOnizlemeService.uret(eserId, metinArsivi, sesArsivi, kalitePanel, null, null);

        if (sonuc.basarili()) {
            System.out.println(sonuc.mesaj());
            System.out.println("MP3 : " + sonuc.mp3());
            System.out.println("JSON: " + sonuc.json());
            if (refreshPanel) {
                System.out.println("Kalite paneli yenileniyor...");
                SesKalitePanelService.uret(sesArsivi, metinArsivi, kalitePanel,
                        Path.of(System.getProperty("user.dir")));
                System.out.println("Kalite paneli güncellendi: " + kalitePanel.resolve("kalite-panel.json"));
            }
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
