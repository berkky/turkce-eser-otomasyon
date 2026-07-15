import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Yerel Piper + FFmpeg MP3 kalite testi.
 * Ana App akışından bağımsız olarak ses üretimi ve MP3 dönüşümünü doğrular.
 */
public class PiperYerelTest {
    private static final int TEST_KARAKTER_SINIRI = 1_000;

    public static void main(String[] args) {
        Utf8Konsol.etkinlestir();
        try {
            Path projeKlasoru = Path.of(System.getProperty("user.dir"));
            EserVeriYollari yollar = EserVeriYollari.varsayilan();
            Path metinArsivi = yollar.metin();
            Path sesArsivi = yollar.ses();

            PiperClient piper = new PiperClient(projeKlasoru);
            FfmpegClient ffmpeg = new FfmpegClient(projeKlasoru);

            System.out.println("--- YEREL PIPER + FFMPEG MP3 TESTİ ---");
            System.out.println("Python ortamı : " + piper.pythonYolu());
            System.out.println("Ses modeli    : " + piper.sesModeli());
            System.out.println("Model klasörü : " + piper.sesModelKlasoru());
            System.out.println("CUDA          : " + (piper.cudaKullan() ? "AÇIK" : "KAPALI (CPU)"));

            PiperClient.KontrolSonucu kontrol = piper.kontrolEt();
            if (!kontrol.hazir()) {
                System.err.println();
                System.err.println("Piper henüz hazır değil:");
                System.err.println(kontrol.mesaj());
                System.err.println();
                System.err.println("Önce proje klasöründe şu komutu çalıştır:");
                System.err.println("powershell -ExecutionPolicy Bypass -File .\\piper-kurulum.ps1");
                System.exit(2);
            }

            FfmpegClient.KontrolSonucu ffmpegKontrol = ffmpeg.kontrolEt();
            if (!ffmpegKontrol.hazir()) {
                System.err.println();
                System.err.println("FFmpeg henüz hazır değil:");
                System.err.println(ffmpegKontrol.mesaj());
                System.err.println("Kurulum için: powershell -ExecutionPolicy Bypass -File .\\ffmpeg-kurulum.ps1");
                System.exit(3);
            }
            System.out.println("FFmpeg        : " + ffmpegKontrol.ffmpegKomutu());
            System.out.println("MP3 ayarı     : " + ffmpeg.bitrate() + " mono");

            Path kaynakMetin = ilkTtsMetniniBul(metinArsivi)
                    .orElseThrow(() -> new IllegalStateException(
                            "metin-arsivi içinde TTS parçası bulunamadı."));

            String tamMetin = Files.readString(kaynakMetin, StandardCharsets.UTF_8).trim();
            String testMetni = testMetniniHazirla(tamMetin);

            String eserKlasorAdi = eserKlasorAdiniBul(kaynakMetin);
            Path cikisKlasoru = sesArsivi.resolve(eserKlasorAdi).resolve("piper-test");
            Files.createDirectories(cikisKlasoru);

            Path wav = cikisKlasoru.resolve("piper-dfki-test-gecici.wav");
            Path mp3 = cikisKlasoru.resolve("piper-dfki-test.mp3");
            Path json = cikisKlasoru.resolve("piper-dfki-test.json");

            System.out.println();
            System.out.println("Kaynak metin   : " + kaynakMetin);
            System.out.println("Test karakteri : " + testMetni.length());
            System.out.println("Yerel ses üretimi başlıyor...");

            PiperClient.UretimSonucu sonuc = piper.wavUret(testMetni, wav);
            FfmpegClient.DonusumSonucu donusum = ffmpeg.mp3eDonustur(wav, mp3);
            Files.deleteIfExists(wav);
            bilgiDosyasiYaz(json, kaynakMetin, testMetni, sonuc, donusum);

            System.out.println();
            System.out.println("PIPER + FFMPEG TESTİ BAŞARILI");
            System.out.println("MP3 dosyası   : " + donusum.hedefMp3());
            System.out.println("WAV → MP3     : " + okunabilirBoyut(donusum.wavBoyutu())
                    + " → " + okunabilirBoyut(donusum.mp3Boyutu()));
            System.out.println("Boyut kazancı : " + String.format(Locale.ROOT, "%.1f%%",
                    donusum.sikistirmaOrani() * 100.0));
            System.out.println("Toplam süre   : " + String.format(Locale.ROOT, "%.1f sn",
                    (sonuc.uretimSuresiMs() + donusum.donusumSuresiMs()) / 1_000.0));
            System.out.println("Metin buluta gönderilmedi; ses tamamen yerel bilgisayarda üretildi.");
        } catch (Exception e) {
            System.err.println("Piper yerel test başarısız: " + e.getMessage());
            System.exit(1);
        }
    }

    private static Optional<Path> ilkTtsMetniniBul(Path metinArsivi) throws Exception {
        if (!Files.isDirectory(metinArsivi)) {
            return Optional.empty();
        }
        try (Stream<Path> yollar = Files.walk(metinArsivi)) {
            return yollar
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getParent() != null
                            && path.getParent().getFileName().toString().equalsIgnoreCase("tts-parcalari"))
                    .filter(path -> path.getFileName().toString().matches("\\d{3}-\\d{3}\\.txt"))
                    .sorted(Comparator.comparing(Path::toString))
                    .findFirst();
        }
    }

    private static String eserKlasorAdiniBul(Path kaynakMetin) {
        Path ttsKlasoru = kaynakMetin.getParent();
        Path eserKlasoru = ttsKlasoru == null ? null : ttsKlasoru.getParent();
        return eserKlasoru == null
                ? "yerel-piper-test"
                : eserKlasoru.getFileName().toString();
    }

    private static String testMetniniHazirla(String metin) {
        if (metin.length() <= TEST_KARAKTER_SINIRI) {
            return metin;
        }
        int son = metin.lastIndexOf('.', TEST_KARAKTER_SINIRI);
        if (son < 400) {
            son = TEST_KARAKTER_SINIRI;
        } else {
            son++;
        }
        return metin.substring(0, son).trim();
    }

    private static void bilgiDosyasiYaz(Path json,
                                        Path kaynakMetin,
                                        String testMetni,
                                        PiperClient.UretimSonucu sonuc,
                                        FfmpegClient.DonusumSonucu donusum) throws Exception {
        String icerik = "{\n"
                + "  \"saglayici\": \"Yerel Piper\",\n"
                + "  \"sesModeli\": \"" + jsonKacis(sonuc.sesModeli()) + "\",\n"
                + "  \"format\": \"MP3\",\n"
                + "  \"bitrate\": \"" + jsonKacis(donusum.bitrate()) + "\",\n"
                + "  \"kaynakMetin\": \"" + jsonKacis(kaynakMetin.toString()) + "\",\n"
                + "  \"mp3Dosyasi\": \"" + jsonKacis(donusum.hedefMp3().toString()) + "\",\n"
                + "  \"karakterSayisi\": " + sonuc.karakterSayisi() + ",\n"
                + "  \"wavDosyaBoyutu\": " + donusum.wavBoyutu() + ",\n"
                + "  \"mp3DosyaBoyutu\": " + donusum.mp3Boyutu() + ",\n"
                + "  \"piperUretimSuresiMs\": " + sonuc.uretimSuresiMs() + ",\n"
                + "  \"mp3DonusumSuresiMs\": " + donusum.donusumSuresiMs() + ",\n"
                + "  \"cudaKullanildi\": " + sonuc.cudaKullanildi() + ",\n"
                + "  \"bulutaGonderildi\": false,\n"
                + "  \"uretimZamani\": \"" + OffsetDateTime.now() + "\",\n"
                + "  \"testMetni\": \"" + jsonKacis(testMetni) + "\"\n"
                + "}\n";
        Files.writeString(json, icerik, StandardCharsets.UTF_8);
    }

    private static String jsonKacis(String metin) {
        return metin
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static String okunabilirBoyut(long bayt) {
        if (bayt < 1_024) {
            return bayt + " B";
        }
        if (bayt < 1_048_576) {
            return String.format(Locale.ROOT, "%.1f KB", bayt / 1_024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bayt / 1_048_576.0);
    }
}
