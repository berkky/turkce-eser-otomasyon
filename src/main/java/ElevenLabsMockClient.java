import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test ve mock mod için ElevenLabs istemcisi. Gerçek API çağrısı yapmaz.
 */
public final class ElevenLabsMockClient implements ElevenLabsIstemci {
    public static final byte[] MINIMAL_MP3_FIXTURE = new byte[] {
            (byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    private final String model;
    private final long kullanilanKredi;
    private final long limitKredi;
    private final boolean aktifAbonelik;
    private final String voiceId;
    private final AtomicLong ttsCagriSayisi = new AtomicLong();

    public ElevenLabsMockClient() {
        this(ElevenLabsModelPolitikasi.VARSAYILAN_MODEL, 1_000L, 10_000L, true, "mock-voice-id-0001");
    }

    public ElevenLabsMockClient(String model,
                                long kullanilanKredi,
                                long limitKredi,
                                boolean aktifAbonelik,
                                String voiceId) {
        this.model = ElevenLabsModelPolitikasi.modelIdTemizle(model);
        this.kullanilanKredi = Math.max(0L, kullanilanKredi);
        this.limitKredi = Math.max(0L, limitKredi);
        this.aktifAbonelik = aktifAbonelik;
        this.voiceId = voiceId == null || voiceId.isBlank() ? "mock-voice-id-0001" : voiceId.trim();
    }

    public long ttsCagriSayisi() {
        return ttsCagriSayisi.get();
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public ElevenLabsClient.AbonelikBilgisi abonelikBilgisiniGetir() {
        return new ElevenLabsClient.AbonelikBilgisi(
                "mock",
                aktifAbonelik ? "active" : "inactive",
                kullanilanKredi,
                limitKredi,
                0L,
                false,
                false,
                false,
                0L
        );
    }

    @Override
    public ElevenLabsClient.ModelKrediBilgisi modelKrediBilgisiniGetir() {
        return new ElevenLabsClient.ModelKrediBilgisi(
                model,
                model,
                ElevenLabsClient.bilinenModelKrediCarpani(model),
                5_000,
                true
        );
    }

    @Override
    public List<ElevenLabsClient.Ses> sesleriListele() {
        return List.of(new ElevenLabsClient.Ses(
                voiceId,
                "Mock Türkçe Ses",
                "mock",
                "female",
                "tr",
                "Mock test sesi",
                true
        ));
    }

    @Override
    public boolean sesIdDogrula(String voiceId) {
        return voiceId != null && voiceId.trim().equals(this.voiceId);
    }

    @Override
    public boolean modelDestekleniyorMu(String modelId) {
        return ElevenLabsModelPolitikasi.desteklenenModelMi(modelId);
    }

    @Override
    public ElevenLabsClient.SesUretimSonucu sesUret(String metin,
                                                    ElevenLabsClient.Ses ses,
                                                    Path ciktiDosyasi) throws Exception {
        return sesUret(metin, null, null, ses, ciktiDosyasi);
    }

    @Override
    public ElevenLabsClient.SesUretimSonucu sesUret(String metin,
                                                    String oncekiMetin,
                                                    String sonrakiMetin,
                                                    ElevenLabsClient.Ses ses,
                                                    Path ciktiDosyasi) throws Exception {
        long kalan = abonelikBilgisiniGetir().kalanPlanKredisi();
        if (!aktifAbonelik || kalan <= 0L) {
            throw new ElevenLabsClient.ElevenLabsApiException(402, "ElevenLabs kredisi yetersiz.");
        }
        if (metin == null || metin.isBlank()) {
            throw new IllegalArgumentException("Seslendirilecek metin boş olamaz.");
        }
        ElevenLabsClient.Ses kullanilacakSes = ses == null
                ? new ElevenLabsClient.Ses(voiceId, "Mock Türkçe Ses", "mock", "", "tr", "", true)
                : ses;

        ttsCagriSayisi.incrementAndGet();
        Files.createDirectories(ciktiDosyasi.getParent());
        Path partial = ciktiDosyasi.resolveSibling(ciktiDosyasi.getFileName() + ".partial");
        Files.write(partial, MINIMAL_MP3_FIXTURE);
        Files.move(partial, ciktiDosyasi, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        return new ElevenLabsClient.SesUretimSonucu(
                ciktiDosyasi,
                MINIMAL_MP3_FIXTURE.length,
                metin.length(),
                kullanilacakSes,
                model,
                "mp3_mock_fixture"
        );
    }

    public static boolean sifirBaytAudioReddedilir(byte[] audio) {
        return audio == null || audio.length == 0;
    }

    public static boolean gecerliAudioMu(byte[] audio) {
        return !sifirBaytAudioReddedilir(audio) && audio.length >= 128;
    }

    public static String mockModAktifMi() {
        String deger = System.getenv("ELEVENLABS_MOCK");
        if (deger == null || deger.isBlank()) {
            return "false";
        }
        String temiz = deger.trim().toLowerCase(Locale.ROOT);
        return String.valueOf(temiz.equals("true") || temiz.equals("1") || temiz.equals("yes"));
    }
}
