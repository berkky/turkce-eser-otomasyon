import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Patron demo / test için güvenli alignment fixture — gerçek eser önizlemesine dokunmaz.
 * Dosyalar: ses-arsivi/_alignment/_fixture/
 */
public final class AlignmentDemoFixtureService {
    static final int MIN_AUDIO_BOYUT = 256;

    private final AlignmentStorageService depo;

    public AlignmentDemoFixtureService(AlignmentStorageService depo) {
        this.depo = depo;
    }

    public Path fixtureKlasoru() {
        return depo.alignmentKlasoru().resolve("_fixture");
    }

    public void hazirla(int eserId) throws Exception {
        AlignmentGuvenlikService.eserIzni(eserId);
        Path klasor = fixtureKlasoru();
        Files.createDirectories(klasor);
        String onEk = String.format(Locale.ROOT, "ESER-%05d", eserId);
        Path metin = klasor.resolve(onEk + "-preview-input.txt");
        Path mp3 = klasor.resolve(onEk + "-preview-audio.mp3");
        if (!Files.isRegularFile(metin)) {
            Files.writeString(metin, """
                    Demo fixture metni — gerçek ElevenLabs önizlemesi değildir.
                    Kaşağı hikâyesinden örnek parça: Bir varmış bir yokmuş.
                    Evliya Çelebi bu eserde pek çok ayrıntıyı anlatmıştır.
                    Türkçe karakterler: ğüşıöç.
                    """, StandardCharsets.UTF_8);
        }
        if (!Files.isRegularFile(mp3) || Files.size(mp3) < AlignmentStorageService.EN_AZ_MP3) {
            byte[] veri = new byte[MIN_AUDIO_BOYUT];
            for (int i = 0; i < veri.length; i++) {
                veri[i] = (byte) (0x40 + (i % 64));
            }
            Files.write(mp3, veri);
        }
    }

    public AlignmentStorageService.PreviewKaynaklari kaynaklar(int eserId) throws Exception {
        hazirla(eserId);
        Path klasor = fixtureKlasoru();
        String onEk = String.format(Locale.ROOT, "ESER-%05d", eserId);
        Path mp3 = klasor.resolve(onEk + "-preview-audio.mp3");
        Path metin = klasor.resolve(onEk + "-preview-input.txt");
        if (!Files.isRegularFile(mp3) || Files.size(mp3) < AlignmentStorageService.EN_AZ_MP3) {
            throw new IllegalStateException("Demo fixture audio geçersiz.");
        }
        String icerik = Files.readString(metin, StandardCharsets.UTF_8).trim();
        if (icerik.isBlank()) {
            throw new IllegalStateException("Demo fixture metni boş.");
        }
        return new AlignmentStorageService.PreviewKaynaklari(
                eserId,
                "preview-elevenlabs-demo-fixture",
                mp3,
                metin,
                null,
                icerik,
                "fixture-e" + eserId);
    }
}
