import java.nio.file.Path;

/**
 * CLI: ElevenLabs forced alignment (mock veya açık onaylı gerçek API).
 */
public final class ElevenLabsAlignmentApp {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        int eserId = 5;
        boolean mock = false;
        boolean gercekApi = false;
        boolean demoFixture = false;

        for (String arg : args) {
            String a = arg.trim();
            if ("-Mock".equalsIgnoreCase(a) || "--mock".equalsIgnoreCase(a)) {
                mock = true;
                continue;
            }
            if ("-GercekApiOnayli".equalsIgnoreCase(a) || "--gercek-api-onayli".equalsIgnoreCase(a)) {
                gercekApi = true;
                continue;
            }
            if ("-DemoFixture".equalsIgnoreCase(a) || "--demo-fixture".equalsIgnoreCase(a)) {
                demoFixture = true;
                continue;
            }
            if (a.startsWith("-")) {
                continue;
            }
            try {
                eserId = Integer.parseInt(a.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                System.err.println("Geçersiz eser ID: " + a);
                System.exit(2);
            }
        }

        if (demoFixture || mock) {
            System.setProperty("ELEVENLABS_MOCK", "true");
        } else if (gercekApi) {
            System.clearProperty("ELEVENLABS_MOCK");
        }

        Path masaustu = Path.of(System.getProperty("user.home"), "Desktop");
        Path sesArsivi = ortamYolu("ESER_SES_ARSIVI", masaustu.resolve("ses-arsivi"));

        System.out.println("--- ELEVENLABS FORCED ALIGNMENT (Adım 30) ---");
        System.out.println("Eser ID: ESER-" + String.format("%05d", eserId));
        if (demoFixture) {
            System.out.println("Mod: DEMO FIXTURE (mock, gerçek önizlemeye dokunmaz)");
        } else if (gercekApi && !mock) {
            System.out.println("Mod: GERÇEK API (açık onaylı — ElevenLabs forced alignment)");
        } else {
            System.out.println("Mod: MOCK (API çağrısı yapılmaz)");
        }
        System.out.println(ElevenLabsFabrika.durumOzeti().konsolOzeti());
        System.out.println();

        if (gercekApi && !mock && !demoFixture) {
            System.out.println("Not: Gerçek API yalnızca preview MP3 + preview-input.txt ile çalışır.");
        }

        AlignmentService svc = new AlignmentService(sesArsivi);
        try {
            AlignmentService.AlignmentSonucu sonuc = demoFixture
                    ? svc.uretDemoFixture(eserId)
                    : svc.uret(eserId, mock || !gercekApi, gercekApi);

            if (sonuc.basarili()) {
                System.out.println(sonuc.mesaj());
                System.out.println("Kaynak: " + sonuc.sonuc().kaynakEtiketi());
                System.out.println("Segment: " + sonuc.sonuc().segmentCount());
                System.out.println("Kelime: " + sonuc.sonuc().wordCount());
                System.out.println("Gerçek API: " + sonuc.sonuc().realApiUsed());
                System.out.println("JSON : " + sonuc.sonuc().outputPathSafeName());
                System.out.println("SRT  : " + sonuc.sonuc().srtSafeName());
                System.out.println("VTT  : " + sonuc.sonuc().vttSafeName());
                System.exit(0);
            }
            System.err.println("Alignment tamamlanamadı: " + sonuc.mesaj());
            System.exit(1);
        } catch (IllegalStateException e) {
            System.err.println("Alignment tamamlanamadı: " + e.getMessage());
            System.exit(1);
        }
    }

    private static Path ortamYolu(String ad, Path varsayilan) {
        String deger = System.getenv(ad);
        if (deger == null || deger.isBlank()) {
            deger = System.getProperty(ad);
        }
        return deger == null || deger.isBlank() ? varsayilan : Path.of(deger.trim());
    }
}
