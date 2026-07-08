import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class PiperTtsSaglayici implements TtsSaglayici {
    private final PiperClient piper;
    private final FfmpegClient ffmpeg;

    public PiperTtsSaglayici(Path projeKlasoru) {
        this.piper = new PiperClient(projeKlasoru);
        this.ffmpeg = new FfmpegClient(
                TtsLaboratuvarYardimci.ortam("FFMPEG_PATH", "ffmpeg"),
                TtsLaboratuvarYardimci.ortam("TTS_LAB_MP3_BITRATE", "128k")
        );
    }

    @Override public String kimlik() { return "piper"; }
    @Override public String ad() { return "Yerel Piper (referans)"; }
    @Override public String model() { return piper.sesModeli(); }
    @Override public String ses() { return piper.sesModeli(); }

    @Override
    public Hazirlik hazirlik() {
        PiperClient.KontrolSonucu p = piper.kontrolEt();
        if (!p.hazir()) return Hazirlik.degil(p.mesaj());
        FfmpegClient.KontrolSonucu f = ffmpeg.kontrolEt();
        return f.hazir() ? Hazirlik.hazir("Piper ve FFmpeg hazır") : Hazirlik.degil(f.mesaj());
    }

    @Override
    public TtsUretimSonucu uret(TtsUretimIstegi istek, Path ciktiKlasoru) throws Exception {
        if (!hazirlik().hazir()) throw new IllegalStateException(hazirlik().mesaj());
        Path klasor = ciktiKlasoru.resolve(kimlik());
        Files.createDirectories(klasor);
        Path wav = klasor.resolve(istek.ornekId() + ".tmp.wav");
        Path mp3 = klasor.resolve(istek.ornekId() + ".mp3");
        long baslangic = System.nanoTime();
        try {
            piper.wavUret(istek.metin(), wav);
            ffmpeg.mp3eDonustur(wav, mp3);
        } finally {
            Files.deleteIfExists(wav);
        }
        long sure = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - baslangic);
        return new TtsUretimSonucu(kimlik(), ad(), model(), ses(), istek.ornekId(), istek.metinTuru(),
                mp3, istek.metin().length(), Files.size(mp3), sure);
    }
}
