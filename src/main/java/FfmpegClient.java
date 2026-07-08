import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * FFmpeg komut satırı aracını kullanarak yerel WAV dosyalarını MP3'e dönüştürür.
 * Varsayılan çıktı konuşma için mono 64 kbit/s MP3'tür.
 */
public final class FfmpegClient {
    private static final Duration KONTROL_ZAMAN_ASIMI = Duration.ofSeconds(45);
    private static final Duration DONUSUM_ZAMAN_ASIMI = Duration.ofMinutes(10);
    private static final Duration BIRLESTIRME_ZAMAN_ASIMI = Duration.ofHours(8);
    private static final int EN_AZ_GECERLI_MP3_BOYUTU = 1_000;

    private final String ffmpegKomutu;
    private final String bitrate;
    private volatile KontrolSonucu kontrolOnbellegi;

    public FfmpegClient(Path projeKlasoru) {
        this(ffmpegKomutunuBul(projeKlasoru), ortamVeyaVarsayilan("PIPER_MP3_BITRATE", "64k"));
    }

    public FfmpegClient(String ffmpegKomutu, String bitrate) {
        if (ffmpegKomutu == null || ffmpegKomutu.isBlank()) {
            throw new IllegalArgumentException("FFmpeg komutu boş olamaz.");
        }
        this.ffmpegKomutu = ffmpegKomutu.trim();
        this.bitrate = bitrateGecerliyse(bitrate) ? bitrate.trim().toLowerCase(Locale.ROOT) : "64k";
    }

    public KontrolSonucu kontrolEt() {
        KontrolSonucu onbellek = kontrolOnbellegi;
        if (onbellek != null && onbellek.hazir()) {
            return onbellek;
        }

        try {
            KomutSonucu surum = komutCalistir(
                    List.of(ffmpegKomutu, "-version"),
                    KONTROL_ZAMAN_ASIMI
            );
            if (surum.cikisKodu() != 0) {
                return new KontrolSonucu(false,
                        "FFmpeg çalıştırılamadı. Çıkış kodu: " + surum.cikisKodu()
                                + System.lineSeparator() + kisalt(surum.cikti(), 900),
                        ffmpegKomutu, bitrate);
            }

            KomutSonucu encoderlar = komutCalistir(
                    List.of(ffmpegKomutu, "-hide_banner", "-encoders"),
                    KONTROL_ZAMAN_ASIMI
            );
            if (encoderlar.cikisKodu() != 0 || !encoderlar.cikti().contains("libmp3lame")) {
                return new KontrolSonucu(false,
                        "FFmpeg bulundu ancak libmp3lame MP3 kodlayıcısı kullanılamıyor.",
                        ffmpegKomutu, bitrate);
            }

            String ilkSatir = surum.cikti().lines().findFirst().orElse("FFmpeg hazır").trim();
            KontrolSonucu basarili = new KontrolSonucu(true, ilkSatir, ffmpegKomutu, bitrate);
            kontrolOnbellegi = basarili;
            return basarili;
        } catch (Exception e) {
            return new KontrolSonucu(false,
                    "FFmpeg bulunamadı veya çalıştırılamadı: " + e.getMessage(),
                    ffmpegKomutu, bitrate);
        }
    }

    public DonusumSonucu mp3eDonustur(Path kaynakWav, Path hedefMp3) throws Exception {
        if (!Files.isRegularFile(kaynakWav) || Files.size(kaynakWav) < 1_000) {
            throw new IOException("Dönüştürülecek WAV dosyası bulunamadı veya boş: " + kaynakWav);
        }

        KontrolSonucu kontrol = kontrolEt();
        if (!kontrol.hazir()) {
            throw new IllegalStateException(kontrol.mesaj());
        }

        Files.createDirectories(hedefMp3.toAbsolutePath().getParent());
        long wavBoyutu = Files.size(kaynakWav);
        long baslangic = System.nanoTime();

        List<String> komut = new ArrayList<>();
        komut.add(ffmpegKomutu);
        komut.add("-y");
        komut.add("-hide_banner");
        komut.add("-loglevel");
        komut.add("error");
        komut.add("-i");
        komut.add(kaynakWav.toAbsolutePath().toString());
        komut.add("-vn");
        komut.add("-ac");
        komut.add("1");
        komut.add("-codec:a");
        komut.add("libmp3lame");
        komut.add("-b:a");
        komut.add(bitrate);
        komut.add("-map_metadata");
        komut.add("-1");
        komut.add(hedefMp3.toAbsolutePath().toString());

        KomutSonucu sonuc = komutCalistir(komut, DONUSUM_ZAMAN_ASIMI);
        if (sonuc.cikisKodu() != 0) {
            Files.deleteIfExists(hedefMp3);
            throw new IOException("FFmpeg MP3 dönüşümü başarısız. Çıkış kodu: "
                    + sonuc.cikisKodu() + System.lineSeparator() + kisalt(sonuc.cikti(), 1_500));
        }
        if (!Files.isRegularFile(hedefMp3) || Files.size(hedefMp3) < EN_AZ_GECERLI_MP3_BOYUTU) {
            Files.deleteIfExists(hedefMp3);
            throw new IOException("FFmpeg geçerli bir MP3 dosyası oluşturamadı.");
        }

        long sureMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - baslangic);
        return new DonusumSonucu(
                kaynakWav,
                hedefMp3,
                wavBoyutu,
                Files.size(hedefMp3),
                sureMs,
                bitrate,
                ffmpegKomutu
        );
    }

    /**
     * Birden fazla MP3 dosyasını sırasını koruyarak tek MP3 dosyasında birleştirir.
     * Girdiler yeniden kodlanır; böylece farklı başlık/etiket yapıları ve küçük MP3
     * farklılıkları güvenli şekilde tek dosyada toplanır.
     */
    public BirlesimSonucu mp3leriBirlestir(List<Path> kaynakMp3ler,
                                           Path hedefMp3,
                                           MedyaEtiketleri etiketler) throws Exception {
        List<Path> kaynaklar = gecerliSesKaynaklariniDogrula(kaynakMp3ler);
        KontrolSonucu kontrol = kontrolEt();
        if (!kontrol.hazir()) {
            throw new IllegalStateException(kontrol.mesaj());
        }

        Files.createDirectories(hedefMp3.toAbsolutePath().getParent());
        Path liste = concatListesiOlustur(kaynaklar);
        long baslangic = System.nanoTime();
        long girdiBoyutu = toplamBoyut(kaynaklar);

        try {
            List<String> komut = new ArrayList<>();
            komut.add(ffmpegKomutu);
            komut.add("-y");
            komut.add("-hide_banner");
            komut.add("-loglevel");
            komut.add("error");
            komut.add("-f");
            komut.add("concat");
            komut.add("-safe");
            komut.add("0");
            komut.add("-i");
            komut.add(liste.toAbsolutePath().toString());
            komut.add("-vn");
            komut.add("-ac");
            komut.add("1");
            komut.add("-codec:a");
            komut.add("libmp3lame");
            komut.add("-b:a");
            komut.add(bitrate);
            komut.add("-id3v2_version");
            komut.add("3");
            metadataArgumanlariniEkle(komut, etiketler);
            komut.add(hedefMp3.toAbsolutePath().toString());

            KomutSonucu sonuc = komutCalistir(komut, BIRLESTIRME_ZAMAN_ASIMI);
            if (sonuc.cikisKodu() != 0) {
                Files.deleteIfExists(hedefMp3);
                throw new IOException("MP3 birleştirme başarısız. Çıkış kodu: "
                        + sonuc.cikisKodu() + System.lineSeparator() + kisalt(sonuc.cikti(), 2_000));
            }
            medyaCiktisiniDogrula(hedefMp3, "MP3");

            long sureMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - baslangic);
            return new BirlesimSonucu(
                    List.copyOf(kaynaklar), hedefMp3, girdiBoyutu, Files.size(hedefMp3),
                    sureMs, sureSaniye(hedefMp3), "MP3", bitrate
            );
        } finally {
            Files.deleteIfExists(liste);
        }
    }

    /**
     * Bölüm MP3'lerini tek, bölümlü M4B sesli kitaba dönüştürür.
     * Kapak dosyası verilirse M4B içine gömülür; verilmezse yalnız ses ve bölüm
     * işaretleri yazılır.
     */
    public BirlesimSonucu m4bOlustur(List<Path> bolumMp3leri,
                                     List<BolumIsareti> bolumler,
                                     Path hedefM4b,
                                     MedyaEtiketleri etiketler,
                                     Path kapakDosyasi) throws Exception {
        List<Path> kaynaklar = gecerliSesKaynaklariniDogrula(bolumMp3leri);
        if (bolumler == null || bolumler.size() != kaynaklar.size()) {
            throw new IllegalArgumentException("M4B bölüm işaretleri ile bölüm MP3 sayısı eşleşmiyor.");
        }
        KontrolSonucu kontrol = kontrolEt();
        if (!kontrol.hazir()) {
            throw new IllegalStateException(kontrol.mesaj());
        }

        Files.createDirectories(hedefM4b.toAbsolutePath().getParent());
        Path liste = concatListesiOlustur(kaynaklar);
        Path metadata = ffmetadataOlustur(etiketler, bolumler);
        boolean kapakVar = kapakDosyasi != null && Files.isRegularFile(kapakDosyasi);
        long baslangic = System.nanoTime();
        long girdiBoyutu = toplamBoyut(kaynaklar);

        try {
            List<String> komut = new ArrayList<>();
            komut.add(ffmpegKomutu);
            komut.add("-y");
            komut.add("-hide_banner");
            komut.add("-loglevel");
            komut.add("error");
            komut.add("-f");
            komut.add("concat");
            komut.add("-safe");
            komut.add("0");
            komut.add("-i");
            komut.add(liste.toAbsolutePath().toString());
            komut.add("-f");
            komut.add("ffmetadata");
            komut.add("-i");
            komut.add(metadata.toAbsolutePath().toString());
            if (kapakVar) {
                komut.add("-i");
                komut.add(kapakDosyasi.toAbsolutePath().toString());
            }
            komut.add("-map");
            komut.add("0:a:0");
            komut.add("-map_metadata");
            komut.add("1");
            komut.add("-map_chapters");
            komut.add("1");
            if (kapakVar) {
                komut.add("-map");
                komut.add("2:v:0");
                komut.add("-c:v");
                komut.add("mjpeg");
                komut.add("-frames:v");
                komut.add("1");
                komut.add("-disposition:v:0");
                komut.add("attached_pic");
                komut.add("-metadata:s:v");
                komut.add("title=Cover");
                komut.add("-metadata:s:v");
                komut.add("comment=Cover (front)");
            } else {
                komut.add("-vn");
            }
            komut.add("-c:a");
            komut.add("aac");
            komut.add("-b:a");
            komut.add(bitrate);
            komut.add("-ac");
            komut.add("1");
            komut.add("-movflags");
            komut.add("+faststart");
            komut.add("-f");
            komut.add("mp4");
            komut.add(hedefM4b.toAbsolutePath().toString());

            KomutSonucu sonuc = komutCalistir(komut, BIRLESTIRME_ZAMAN_ASIMI);
            if (sonuc.cikisKodu() != 0) {
                Files.deleteIfExists(hedefM4b);
                throw new IOException("M4B oluşturma başarısız. Çıkış kodu: "
                        + sonuc.cikisKodu() + System.lineSeparator() + kisalt(sonuc.cikti(), 2_000));
            }
            medyaCiktisiniDogrula(hedefM4b, "M4B");

            long sureMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - baslangic);
            return new BirlesimSonucu(
                    List.copyOf(kaynaklar), hedefM4b, girdiBoyutu, Files.size(hedefM4b),
                    sureMs, sureSaniye(hedefM4b), "M4B", bitrate
            );
        } finally {
            Files.deleteIfExists(liste);
            Files.deleteIfExists(metadata);
        }
    }

    /** Medya süresini ffprobe ile saniye olarak döndürür. */
    public double sureSaniye(Path medyaDosyasi) throws Exception {
        if (!Files.isRegularFile(medyaDosyasi)) {
            throw new IOException("Süresi okunacak medya dosyası bulunamadı: " + medyaDosyasi);
        }
        List<String> komut = List.of(
                ffprobeKomutu(),
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                medyaDosyasi.toAbsolutePath().toString()
        );
        KomutSonucu sonuc = komutCalistir(komut, KONTROL_ZAMAN_ASIMI);
        if (sonuc.cikisKodu() != 0) {
            throw new IOException("FFprobe medya süresini okuyamadı: " + kisalt(sonuc.cikti(), 900));
        }
        try {
            return Double.parseDouble(sonuc.cikti().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IOException("FFprobe geçerli süre döndürmedi: " + sonuc.cikti().trim());
        }
    }

    public String ffprobeKomutu() {
        Path ffmpegYolu;
        try {
            ffmpegYolu = Path.of(ffmpegKomutu);
        } catch (Exception e) {
            ffmpegYolu = null;
        }
        if (ffmpegYolu != null && Files.isRegularFile(ffmpegYolu)) {
            String ad = isWindows() ? "ffprobe.exe" : "ffprobe";
            Path kardes = ffmpegYolu.toAbsolutePath().getParent().resolve(ad);
            if (Files.isRegularFile(kardes)) {
                return kardes.toString();
            }
        }
        String sistemdeki = sistemKomutunuBul(isWindows() ? "where" : "which", "ffprobe");
        return sistemdeki == null ? "ffprobe" : sistemdeki;
    }

    private static List<Path> gecerliSesKaynaklariniDogrula(List<Path> kaynaklar) throws Exception {
        if (kaynaklar == null || kaynaklar.isEmpty()) {
            throw new IllegalArgumentException("Birleştirilecek ses dosyası listesi boş.");
        }
        List<Path> sonuc = new ArrayList<>();
        for (Path kaynak : kaynaklar) {
            if (kaynak == null || !Files.isRegularFile(kaynak) || Files.size(kaynak) < EN_AZ_GECERLI_MP3_BOYUTU) {
                throw new IOException("Birleştirilecek ses dosyası bulunamadı veya geçersiz: " + kaynak);
            }
            sonuc.add(kaynak.toAbsolutePath().normalize());
        }
        return sonuc;
    }

    private static Path concatListesiOlustur(List<Path> kaynaklar) throws Exception {
        Path liste = Files.createTempFile("ffmpeg-concat-", ".txt");
        StringBuilder icerik = new StringBuilder();
        for (Path kaynak : kaynaklar) {
            String yol = kaynak.toAbsolutePath().normalize().toString().replace('\\', '/');
            yol = yol.replace("'", "'\\''");
            icerik.append("file '").append(yol).append("'\n");
        }
        Files.writeString(liste, icerik.toString(), StandardCharsets.UTF_8);
        return liste;
    }

    private static Path ffmetadataOlustur(MedyaEtiketleri etiketler,
                                           List<BolumIsareti> bolumler) throws Exception {
        Path metadata = Files.createTempFile("ffmpeg-m4b-metadata-", ".txt");
        StringBuilder icerik = new StringBuilder(";FFMETADATA1\n");
        MedyaEtiketleri e = etiketler == null ? MedyaEtiketleri.bos() : etiketler;
        metadataSatiri(icerik, "title", e.baslik());
        metadataSatiri(icerik, "artist", e.sanatci());
        metadataSatiri(icerik, "album", e.album());
        metadataSatiri(icerik, "genre", e.tur());
        metadataSatiri(icerik, "language", e.dil());
        metadataSatiri(icerik, "comment", e.aciklama());
        for (BolumIsareti bolum : bolumler) {
            long baslangic = Math.max(0L, bolum.baslangicMs());
            long bitis = Math.max(baslangic + 1L, bolum.bitisMs());
            icerik.append("[CHAPTER]\n")
                    .append("TIMEBASE=1/1000\n")
                    .append("START=").append(baslangic).append('\n')
                    .append("END=").append(bitis).append('\n')
                    .append("title=").append(ffmetadataKacis(bolum.baslik())).append('\n');
        }
        Files.writeString(metadata, icerik.toString(), StandardCharsets.UTF_8);
        return metadata;
    }

    private static void metadataArgumanlariniEkle(List<String> komut, MedyaEtiketleri etiketler) {
        MedyaEtiketleri e = etiketler == null ? MedyaEtiketleri.bos() : etiketler;
        metadataArgumaniEkle(komut, "title", e.baslik());
        metadataArgumaniEkle(komut, "artist", e.sanatci());
        metadataArgumaniEkle(komut, "album", e.album());
        metadataArgumaniEkle(komut, "genre", e.tur());
        metadataArgumaniEkle(komut, "language", e.dil());
        metadataArgumaniEkle(komut, "comment", e.aciklama());
    }

    private static void metadataArgumaniEkle(List<String> komut, String anahtar, String deger) {
        if (deger == null || deger.isBlank()) {
            return;
        }
        komut.add("-metadata");
        komut.add(anahtar + "=" + deger.trim());
    }

    private static void metadataSatiri(StringBuilder hedef, String anahtar, String deger) {
        if (deger != null && !deger.isBlank()) {
            hedef.append(anahtar).append('=').append(ffmetadataKacis(deger.trim())).append('\n');
        }
    }

    private static String ffmetadataKacis(String deger) {
        if (deger == null) {
            return "";
        }
        return deger
                .replace("\\", "\\\\")
                .replace("=", "\\=")
                .replace(";", "\\;")
                .replace("#", "\\#")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    private static void medyaCiktisiniDogrula(Path hedef, String format) throws Exception {
        if (!Files.isRegularFile(hedef) || Files.size(hedef) < EN_AZ_GECERLI_MP3_BOYUTU) {
            Files.deleteIfExists(hedef);
            throw new IOException("FFmpeg geçerli bir " + format + " dosyası oluşturamadı.");
        }
    }

    private static long toplamBoyut(List<Path> dosyalar) throws Exception {
        long toplam = 0L;
        for (Path dosya : dosyalar) {
            toplam += Files.size(dosya);
        }
        return toplam;
    }

    public String ffmpegKomutu() {
        return ffmpegKomutu;
    }

    public String bitrate() {
        return bitrate;
    }

    private static String ffmpegKomutunuBul(Path projeKlasoru) {
        String ortam = System.getenv("FFMPEG_PATH");
        if (ortam != null && !ortam.isBlank()) {
            Path yol = Path.of(ortam.trim());
            if (Files.isRegularFile(yol)) {
                return yol.toAbsolutePath().normalize().toString();
            }
        }

        List<Path> adaylar = new ArrayList<>();
        if (projeKlasoru != null) {
            adaylar.add(projeKlasoru.resolve("tools").resolve("ffmpeg").resolve("bin").resolve("ffmpeg.exe"));
            adaylar.add(projeKlasoru.resolve("tools").resolve("ffmpeg").resolve("ffmpeg.exe"));
        }
        if (isWindows()) {
            adaylar.add(Path.of("C:\\ffmpeg\\bin\\ffmpeg.exe"));
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                Path wingetPaketleri = Path.of(localAppData, "Microsoft", "WinGet", "Packages");
                Path wingetFfmpeg = wingetFfmpegBul(wingetPaketleri);
                if (wingetFfmpeg != null) {
                    adaylar.add(wingetFfmpeg);
                }
            }
        }
        for (Path aday : adaylar) {
            if (Files.isRegularFile(aday)) {
                return aday.toAbsolutePath().normalize().toString();
            }
        }

        String sistemdeki = sistemKomutunuBul(isWindows() ? "where" : "which", "ffmpeg");
        return sistemdeki == null ? "ffmpeg" : sistemdeki;
    }

    private static Path wingetFfmpegBul(Path paketKlasoru) {
        if (!Files.isDirectory(paketKlasoru)) {
            return null;
        }
        try (Stream<Path> yollar = Files.walk(paketKlasoru, 7)) {
            return yollar
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("ffmpeg.exe"))
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).contains("gyan.ffmpeg"))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String sistemKomutunuBul(String bulmaKomutu, String aranan) {
        try {
            ProcessBuilder builder = new ProcessBuilder(bulmaKomutu, aranan);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String cikti = oku(process);
            boolean bitti = process.waitFor(15, TimeUnit.SECONDS);
            if (!bitti) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            return cikti.lines()
                    .map(String::trim)
                    .filter(satir -> !satir.isBlank())
                    .findFirst()
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static KomutSonucu komutCalistir(List<String> komut, Duration zamanAsimi) throws Exception {
        Path ciktiDosyasi = Files.createTempFile("ffmpeg-komut-", ".log");
        try {
            ProcessBuilder builder = new ProcessBuilder(komut);
            builder.redirectErrorStream(true);
            builder.redirectOutput(ciktiDosyasi.toFile());
            Process process = builder.start();
            boolean bitti = process.waitFor(zamanAsimi.toSeconds(), TimeUnit.SECONDS);
            if (!bitti) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new IOException("Komut " + zamanAsimi.toMinutes() + " dakika içinde tamamlanamadı.");
            }
            String cikti = Files.readString(ciktiDosyasi, StandardCharsets.UTF_8);
            return new KomutSonucu(process.exitValue(), cikti);
        } finally {
            Files.deleteIfExists(ciktiDosyasi);
        }
    }

    private static String oku(Process process) throws IOException {
        StringBuilder cikti = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String satir;
            while ((satir = reader.readLine()) != null) {
                cikti.append(satir).append(System.lineSeparator());
            }
        }
        return cikti.toString();
    }

    private static boolean bitrateGecerliyse(String deger) {
        return deger != null && deger.trim().toLowerCase(Locale.ROOT).matches("\\d{2,3}k");
    }

    private static String ortamVeyaVarsayilan(String ad, String varsayilan) {
        String deger = System.getenv(ad);
        return deger == null || deger.isBlank() ? varsayilan : deger.trim();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String kisalt(String metin, int sinir) {
        if (metin == null) {
            return "";
        }
        String temiz = metin.trim();
        return temiz.length() <= sinir ? temiz : temiz.substring(0, sinir) + "...";
    }

    private record KomutSonucu(int cikisKodu, String cikti) {
    }

    public record KontrolSonucu(boolean hazir,
                                String mesaj,
                                String ffmpegKomutu,
                                String bitrate) {
    }

    public record DonusumSonucu(Path kaynakWav,
                                Path hedefMp3,
                                long wavBoyutu,
                                long mp3Boyutu,
                                long donusumSuresiMs,
                                String bitrate,
                                String ffmpegKomutu) {
        public double sikistirmaOrani() {
            if (wavBoyutu <= 0) {
                return 0.0;
            }
            return 1.0 - (mp3Boyutu / (double) wavBoyutu);
        }
    }

    public record MedyaEtiketleri(String baslik,
                                     String sanatci,
                                     String album,
                                     String tur,
                                     String dil,
                                     String aciklama) {
        public static MedyaEtiketleri bos() {
            return new MedyaEtiketleri("", "", "", "", "", "");
        }
    }

    public record BolumIsareti(String baslik, long baslangicMs, long bitisMs) {
    }

    public record BirlesimSonucu(List<Path> kaynaklar,
                                Path hedef,
                                long toplamGirdiBoyutu,
                                long ciktiBoyutu,
                                long islemSuresiMs,
                                double sesSuresiSaniye,
                                String format,
                                String bitrate) {
    }
}
