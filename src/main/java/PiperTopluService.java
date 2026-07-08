import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * TTS paketlerini yerel Piper ile üretir ve FFmpeg kullanarak MP3'e dönüştürür.
 * Metin bilgisayardan dışarı gönderilmez ve kredi/token kullanılmaz.
 */
public final class PiperTopluService {
    private static final int EN_AZ_GECERLI_MP3_BOYUTU = 1_000;
    private static final int EN_AZ_GECERLI_WAV_BOYUTU = 1_000;
    private static final Pattern PARCA_ADI = Pattern.compile("^(\\d{3})-(\\d{3})\\.txt$");

    private PiperTopluService() {
    }

    public static Sonuc interaktifUret(Path projeKlasoru,
                                       Path metinArsivKlasoru,
                                       Path sesArsivKlasoru,
                                       BufferedReader konsol) {
        try {
            PiperClient piper = new PiperClient(projeKlasoru);
            PiperClient.KontrolSonucu kontrol = piper.kontrolEt();
            if (!kontrol.hazir()) {
                System.err.println("Yerel Piper hazır değil:");
                System.err.println(kontrol.mesaj());
                System.err.println("Kurulum için: powershell -ExecutionPolicy Bypass -File .\\piper-kurulum.ps1");
                return Sonuc.atlandi("Piper hazır değil");
            }

            FfmpegClient ffmpeg = new FfmpegClient(projeKlasoru);
            FfmpegClient.KontrolSonucu ffmpegKontrol = ffmpeg.kontrolEt();
            if (!ffmpegKontrol.hazir()) {
                System.err.println("FFmpeg hazır değil:");
                System.err.println(ffmpegKontrol.mesaj());
                System.err.println("Kurulum için: powershell -ExecutionPolicy Bypass -File .\\ffmpeg-kurulum.ps1");
                return Sonuc.atlandi("FFmpeg hazır değil");
            }

            List<Path> eserKlasorleri = ttsHazirEserleriListele(metinArsivKlasoru);
            if (eserKlasorleri.isEmpty()) {
                System.out.println("Yerel Piper: seslendirilecek TTS paketi bulunamadı.");
                return Sonuc.atlandi("TTS paketi yok");
            }

            Path eserKlasoru = eserSec(eserKlasorleri, konsol);
            if (eserKlasoru == null) {
                return Sonuc.atlandi("Eser seçilmedi");
            }

            Path ttsKlasoru = eserKlasoru.resolve("tts-parcalari");
            List<Path> tumMetinParcalari = metinParcalariniListele(ttsKlasoru);
            if (tumMetinParcalari.isEmpty()) {
                return Sonuc.atlandi("TTS metin parçası yok");
            }

            String modelEtiketi = dosyaEtiketi(piper.sesModeli());
            Path eserSesKlasoru = sesArsivKlasoru.resolve(eserKlasoru.getFileName().toString());
            Path parcaSesKlasoru = eserSesKlasoru.resolve("parcalar-piper-" + modelEtiketi);
            Files.createDirectories(parcaSesKlasoru);

            int donusturulenEskiWav = eskiWavlariMp3eDonustur(
                    tumMetinParcalari, parcaSesKlasoru, piper.sesModeli(), ffmpeg
            );
            if (donusturulenEskiWav > 0) {
                System.out.println("Önceki sürümden MP3'e dönüştürülen WAV: " + donusturulenEskiWav);
            }

            List<Path> eksikParcalar = new ArrayList<>();
            int hazirParca = 0;
            for (Path metinDosyasi : tumMetinParcalari) {
                if (parcaGecerliMi(metinDosyasi, parcaSesKlasoru, piper.sesModeli())) {
                    hazirParca++;
                } else {
                    eksikParcalar.add(metinDosyasi);
                }
            }

            Istatistik toplamIstatistik = istatistik(tumMetinParcalari);
            Istatistik eksikIstatistik = istatistik(eksikParcalar);

            System.out.println();
            System.out.println("--- YEREL PIPER SESLENDİRME DURUMU ---");
            System.out.println("Seçilen eser       : " + eserKlasoru.getFileName());
            System.out.println("Sağlayıcı          : Yerel Piper");
            System.out.println("Ses modeli         : " + piper.sesModeli());
            System.out.println("Çalışma            : " + (piper.cudaKullan() ? "CUDA/GPU" : "CPU"));
            System.out.println("Buluta gönderim    : YOK");
            System.out.println("Kredi/token        : YOK");
            System.out.println("Ses formatı        : MP3 — " + ffmpeg.bitrate() + " mono");
            System.out.println("FFmpeg             : " + ffmpegKontrol.ffmpegKomutu());
            System.out.println("Ses parça klasörü  : " + parcaSesKlasoru.getFileName());
            System.out.println("Toplam parça       : " + tumMetinParcalari.size());
            System.out.println("Hazır MP3          : " + hazirParca);
            System.out.println("Eksik MP3          : " + eksikParcalar.size());
            System.out.println("Toplam karakter    : " + sayiBicimle(toplamIstatistik.karakter()));
            System.out.println("Eksik karakter     : " + sayiBicimle(eksikIstatistik.karakter()));
            System.out.println("Eksik tahmini süre : " + sureBicimle(eksikIstatistik.tahminiSaniye()));

            manifestVeListeleriYaz(eserSesKlasoru, parcaSesKlasoru,
                    tumMetinParcalari, piper.sesModeli(), piper.cudaKullan(), ffmpeg.bitrate());

            if (eksikParcalar.isEmpty()) {
                System.out.println("Yerel Piper MP3 arşivi için bütün parçalar zaten hazır.");
                return Sonuc.basarili(eserSesKlasoru, 0, hazirParca,
                        tumMetinParcalari.size(), true, piper.sesModeli());
            }

            UretimPlani plan = planSec(eksikParcalar, konsol);
            if (plan == null || plan.parcalar().isEmpty()) {
                return Sonuc.atlandi("Üretim planı seçilmedi");
            }

            Istatistik planIstatistik = istatistik(plan.parcalar());
            System.out.println();
            System.out.println("--- YEREL PIPER ÜRETİM PLANI ---");
            System.out.println("Plan               : " + plan.aciklama());
            System.out.println("Üretilecek MP3     : " + plan.parcalar().size());
            System.out.println("Karakter sayısı    : " + sayiBicimle(planIstatistik.karakter()));
            System.out.println("Kelime sayısı      : " + sayiBicimle(planIstatistik.kelime()));
            System.out.println("Tahmini ses süresi : " + sureBicimle(planIstatistik.tahminiSaniye()));
            System.out.println("Maliyet            : Ücretsiz — yerel bilgisayarda çalışır");
            System.out.println("MP3 ayarı          : " + ffmpeg.bitrate() + " mono");
            System.out.println("Tahmini MP3 boyutu : " + tahminiMp3Boyutu(planIstatistik.tahminiSaniye(), ffmpeg.bitrate()));
            System.out.println("Not: CPU ile bütün eser üretimi uzun sürebilir.");
            System.out.println("Ctrl+C ile durdurursan hazır MP3 dosyaları korunur.");
            System.out.print("Bu üretim planı başlatılsın mı? (E/H): ");
            if (!evetMi(konsol.readLine())) {
                return Sonuc.atlandi("Kullanıcı son onayda iptal etti");
            }

            int uretilen = 0;
            int hatali = 0;
            String sonHata = "";
            long toplamUretimMs = 0L;

            for (Path metinDosyasi : plan.parcalar()) {
                if (parcaGecerliMi(metinDosyasi, parcaSesKlasoru, piper.sesModeli())) {
                    continue;
                }

                String metin = Files.readString(metinDosyasi, StandardCharsets.UTF_8).trim();
                String temelAd = uzantisiz(metinDosyasi.getFileName().toString());
                Path hedefMp3 = parcaSesKlasoru.resolve(temelAd + ".mp3");
                Path kurtarmaWav = parcaSesKlasoru.resolve(temelAd + ".wav");
                Path geciciWav = Files.createTempFile(parcaSesKlasoru, temelAd + "-", ".tmp.wav");
                Path geciciMp3 = Files.createTempFile(parcaSesKlasoru, temelAd + "-", ".tmp.mp3");
                Files.deleteIfExists(geciciMp3);

                System.out.println();
                System.out.println("Yerel TTS parçası üretiliyor: " + metinDosyasi.getFileName());
                System.out.println("Karakter sayısı: " + sayiBicimle(metin.length()));
                System.out.println("İlerleme: " + (uretilen + 1) + "/" + plan.parcalar().size());

                boolean wavUretildi = false;
                try {
                    PiperClient.UretimSonucu uretim = piper.wavUret(metin, geciciWav);
                    wavUretildi = true;
                    FfmpegClient.DonusumSonucu donusum = ffmpeg.mp3eDonustur(geciciWav, geciciMp3);
                    atomikTasi(geciciMp3, hedefMp3);
                    Files.deleteIfExists(geciciWav);
                    Files.deleteIfExists(kurtarmaWav);

                    toplamUretimMs += uretim.uretimSuresiMs() + donusum.donusumSuresiMs();
                    parcaMetadataYaz(metinDosyasi, hedefMp3, uretim, donusum,
                            piper.sesModeli(), ffmpeg.bitrate());
                    uretilen++;

                    System.out.println("MP3 hazır: " + hedefMp3);
                    System.out.println("WAV → MP3: " + okunabilirBoyut(donusum.wavBoyutu())
                            + " → " + okunabilirBoyut(donusum.mp3Boyutu()));
                    System.out.println("Boyut kazancı: "
                            + String.format(Locale.ROOT, "%.1f%%", donusum.sikistirmaOrani() * 100.0));
                    System.out.println("Üretim + dönüşüm süresi: "
                            + String.format(Locale.ROOT, "%.1f sn",
                            (uretim.uretimSuresiMs() + donusum.donusumSuresiMs()) / 1_000.0));

                    manifestVeListeleriYaz(eserSesKlasoru, parcaSesKlasoru,
                            tumMetinParcalari, piper.sesModeli(), piper.cudaKullan(), ffmpeg.bitrate());
                } catch (Exception e) {
                    Files.deleteIfExists(geciciMp3);
                    if (wavUretildi && Files.isRegularFile(geciciWav)
                            && Files.size(geciciWav) >= EN_AZ_GECERLI_WAV_BOYUTU) {
                        try {
                            atomikTasi(geciciWav, kurtarmaWav);
                            System.err.println("MP3 dönüşümü tamamlanamadı; üretilen WAV kurtarma için korundu: "
                                    + kurtarmaWav);
                        } catch (Exception tasimaHatasi) {
                            Files.deleteIfExists(geciciWav);
                        }
                    } else {
                        Files.deleteIfExists(geciciWav);
                    }
                    hatali++;
                    sonHata = e.getMessage();
                    System.err.println("Bu parça üretilemedi: " + e.getMessage());
                    System.err.println("Mevcut MP3 dosyaları korunarak üretim durduruldu.");
                    break;
                }
            }

            int toplamHazir = manifestVeListeleriYaz(eserSesKlasoru, parcaSesKlasoru,
                    tumMetinParcalari, piper.sesModeli(), piper.cudaKullan(), ffmpeg.bitrate());
            boolean tumuTamam = toplamHazir == tumMetinParcalari.size();

            System.out.println();
            System.out.println("Yerel Piper toplu TTS işlemi tamamlandı.");
            System.out.println("Bu çalışmada üretilen MP3: " + uretilen);
            System.out.println("Toplam hazır MP3: " + toplamHazir + "/" + tumMetinParcalari.size());
            System.out.println("Tamamlanma oranı: "
                    + String.format(Locale.ROOT, "%.1f%%",
                    toplamHazir * 100.0 / tumMetinParcalari.size()));
            if (uretilen > 0) {
                System.out.println("Bu çalışma üretim süresi: " + sureBicimle(Math.max(1L, toplamUretimMs / 1_000L)));
            }
            System.out.println("Ses klasörü: " + eserSesKlasoru);

            if (hatali > 0) {
                return Sonuc.hatali(eserSesKlasoru, uretilen, toplamHazir,
                        tumMetinParcalari.size(), sonHata, piper.sesModeli());
            }
            return Sonuc.basarili(eserSesKlasoru, uretilen, toplamHazir,
                    tumMetinParcalari.size(), tumuTamam, piper.sesModeli());
        } catch (Exception e) {
            System.err.println("Yerel Piper toplu TTS işlemi tamamlanamadı: " + e.getMessage());
            return Sonuc.hatali(null, 0, 0, 0, e.getMessage(), "Bilinmiyor");
        }
    }

    private static List<Path> ttsHazirEserleriListele(Path metinArsivKlasoru) throws Exception {
        if (!Files.isDirectory(metinArsivKlasoru)) {
            return List.of();
        }
        try (Stream<Path> yollar = Files.list(metinArsivKlasoru)) {
            return yollar
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isDirectory(path.resolve("tts-parcalari")))
                    .filter(path -> {
                        try {
                            return !metinParcalariniListele(path.resolve("tts-parcalari")).isEmpty();
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static Path eserSec(List<Path> eserler, BufferedReader konsol) throws Exception {
        if (eserler.size() == 1) {
            System.out.println("Yerel Piper eseri: " + eserler.get(0).getFileName());
            return eserler.get(0);
        }

        System.out.println();
        System.out.println("--- TTS HAZIR ESERLER ---");
        for (int i = 0; i < eserler.size(); i++) {
            System.out.printf(Locale.ROOT, "%2d - %s%n", i + 1, eserler.get(i).getFileName());
        }
        System.out.print("Seslendirilecek eser numarası (0 = iptal): ");
        int secim = sayiOku(konsol.readLine(), 0);
        if (secim < 1 || secim > eserler.size()) {
            return null;
        }
        return eserler.get(secim - 1);
    }

    private static List<Path> metinParcalariniListele(Path ttsKlasoru) throws Exception {
        if (!Files.isDirectory(ttsKlasoru)) {
            return List.of();
        }
        try (Stream<Path> yollar = Files.list(ttsKlasoru)) {
            return yollar
                    .filter(Files::isRegularFile)
                    .filter(path -> PARCA_ADI.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static UretimPlani planSec(List<Path> eksikParcalar,
                                       BufferedReader konsol) throws Exception {
        System.out.println();
        System.out.println("--- YEREL PIPER TOPLU SESLENDİRME MENÜSÜ ---");
        System.out.println("1 - İlk 3 eksik parçayı üret");
        System.out.println("2 - İlk 10 eksik parçayı üret");
        System.out.println("3 - Tek bir bölümü üret");
        System.out.println("4 - Bütün eksik parçaları üret");
        System.out.println("0 - İptal");
        System.out.print("Seçimin: ");
        int secim = sayiOku(konsol.readLine(), -1);

        return switch (secim) {
            case 1 -> new UretimPlani(eksikParcalar.stream().limit(3).toList(), "İlk 3 eksik parça");
            case 2 -> new UretimPlani(eksikParcalar.stream().limit(10).toList(), "İlk 10 eksik parça");
            case 3 -> bolumPlaniSec(eksikParcalar, konsol);
            case 4 -> new UretimPlani(List.copyOf(eksikParcalar), "Bütün eksik parçalar");
            default -> null;
        };
    }

    private static UretimPlani bolumPlaniSec(List<Path> eksikParcalar,
                                             BufferedReader konsol) throws Exception {
        Map<String, List<Path>> bolumler = new LinkedHashMap<>();
        for (Path path : eksikParcalar) {
            Matcher matcher = PARCA_ADI.matcher(path.getFileName().toString());
            if (matcher.matches()) {
                bolumler.computeIfAbsent(matcher.group(1), k -> new ArrayList<>()).add(path);
            }
        }
        if (bolumler.isEmpty()) {
            return null;
        }

        List<String> numaralar = new ArrayList<>(bolumler.keySet());
        System.out.println();
        System.out.println("--- EKSİK BÖLÜMLER ---");
        for (int i = 0; i < numaralar.size(); i++) {
            String bolum = numaralar.get(i);
            System.out.printf(Locale.ROOT, "%2d - Bölüm %s | %d eksik parça%n",
                    i + 1, bolum, bolumler.get(bolum).size());
        }
        System.out.print("Bölüm sıra numarası (0 = iptal): ");
        int secim = sayiOku(konsol.readLine(), 0);
        if (secim < 1 || secim > numaralar.size()) {
            return null;
        }
        String bolum = numaralar.get(secim - 1);
        return new UretimPlani(List.copyOf(bolumler.get(bolum)), "Bölüm " + bolum);
    }

    private static boolean parcaGecerliMi(Path metinDosyasi,
                                          Path parcaSesKlasoru,
                                          String model) {
        try {
            String temelAd = uzantisiz(metinDosyasi.getFileName().toString());
            Path mp3 = parcaSesKlasoru.resolve(temelAd + ".mp3");
            Path json = parcaSesKlasoru.resolve(temelAd + ".json");
            if (!Files.isRegularFile(mp3) || Files.size(mp3) < EN_AZ_GECERLI_MP3_BOYUTU
                    || !Files.isRegularFile(json)) {
                return false;
            }
            String metadata = Files.readString(json, StandardCharsets.UTF_8);
            String hash = sha256(metinDosyasi);
            return metadata.contains("\"metinSha256\": \"" + hash + "\"")
                    && metadata.contains("\"sesModeli\": \"" + jsonKacis(model) + "\"")
                    && metadata.contains("\"format\": \"MP3\"");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean eskiWavGecerliMi(Path metinDosyasi,
                                             Path parcaSesKlasoru,
                                             String model) {
        try {
            String temelAd = uzantisiz(metinDosyasi.getFileName().toString());
            Path wav = parcaSesKlasoru.resolve(temelAd + ".wav");
            Path json = parcaSesKlasoru.resolve(temelAd + ".json");
            if (!Files.isRegularFile(wav) || Files.size(wav) < EN_AZ_GECERLI_WAV_BOYUTU
                    || !Files.isRegularFile(json)) {
                return false;
            }
            String metadata = Files.readString(json, StandardCharsets.UTF_8);
            return metadata.contains("\"metinSha256\": \"" + sha256(metinDosyasi) + "\"")
                    && metadata.contains("\"sesModeli\": \"" + jsonKacis(model) + "\"");
        } catch (Exception e) {
            return false;
        }
    }

    private static int eskiWavlariMp3eDonustur(List<Path> metinParcalari,
                                                Path parcaSesKlasoru,
                                                String model,
                                                FfmpegClient ffmpeg) throws Exception {
        int donusturulen = 0;
        for (Path metinDosyasi : metinParcalari) {
            if (parcaGecerliMi(metinDosyasi, parcaSesKlasoru, model)
                    || !eskiWavGecerliMi(metinDosyasi, parcaSesKlasoru, model)) {
                continue;
            }

            String temelAd = uzantisiz(metinDosyasi.getFileName().toString());
            Path wav = parcaSesKlasoru.resolve(temelAd + ".wav");
            Path hedefMp3 = parcaSesKlasoru.resolve(temelAd + ".mp3");
            Path geciciMp3 = Files.createTempFile(parcaSesKlasoru, temelAd + "-migrasyon-", ".tmp.mp3");
            Files.deleteIfExists(geciciMp3);

            try {
                FfmpegClient.DonusumSonucu donusum = ffmpeg.mp3eDonustur(wav, geciciMp3);
                atomikTasi(geciciMp3, hedefMp3);
                eskiWavMetadataMp3eCevir(metinDosyasi, hedefMp3, donusum, model, ffmpeg.bitrate());
                Files.deleteIfExists(wav);
                donusturulen++;
            } catch (Exception e) {
                Files.deleteIfExists(geciciMp3);
                System.err.println("Eski WAV MP3'e dönüştürülemedi, WAV korunuyor: "
                        + wav.getFileName() + " — " + e.getMessage());
            }
        }
        return donusturulen;
    }

    private static void eskiWavMetadataMp3eCevir(Path metinDosyasi,
                                                  Path hedefMp3,
                                                  FfmpegClient.DonusumSonucu donusum,
                                                  String model,
                                                  String bitrate) throws Exception {
        String temelAd = uzantisiz(metinDosyasi.getFileName().toString());
        Path json = hedefMp3.getParent().resolve(temelAd + ".json");
        String icerik = "{\n"
                + "  \"saglayici\": \"Yerel Piper\",\n"
                + "  \"format\": \"MP3\",\n"
                + "  \"sesModeli\": \"" + jsonKacis(model) + "\",\n"
                + "  \"kaynakMetin\": \"" + jsonKacis(metinDosyasi.toString()) + "\",\n"
                + "  \"metinSha256\": \"" + sha256(metinDosyasi) + "\",\n"
                + "  \"mp3Dosyasi\": \"" + jsonKacis(hedefMp3.toString()) + "\",\n"
                + "  \"bitrate\": \"" + jsonKacis(bitrate) + "\",\n"
                + "  \"wavDosyaBoyutu\": " + donusum.wavBoyutu() + ",\n"
                + "  \"mp3DosyaBoyutu\": " + donusum.mp3Boyutu() + ",\n"
                + "  \"donusumSuresiMs\": " + donusum.donusumSuresiMs() + ",\n"
                + "  \"oncekiWavdanDonusturuldu\": true,\n"
                + "  \"bulutaGonderildi\": false,\n"
                + "  \"krediKullanildi\": false,\n"
                + "  \"uretimZamani\": \"" + OffsetDateTime.now() + "\"\n"
                + "}\n";
        Files.writeString(json, icerik, StandardCharsets.UTF_8);
    }

    private static void parcaMetadataYaz(Path metinDosyasi,
                                         Path hedefMp3,
                                         PiperClient.UretimSonucu uretim,
                                         FfmpegClient.DonusumSonucu donusum,
                                         String model,
                                         String bitrate) throws Exception {
        String temelAd = uzantisiz(metinDosyasi.getFileName().toString());
        Path json = hedefMp3.getParent().resolve(temelAd + ".json");
        String icerik = "{\n"
                + "  \"saglayici\": \"Yerel Piper\",\n"
                + "  \"format\": \"MP3\",\n"
                + "  \"sesModeli\": \"" + jsonKacis(model) + "\",\n"
                + "  \"kaynakMetin\": \"" + jsonKacis(metinDosyasi.toString()) + "\",\n"
                + "  \"metinSha256\": \"" + sha256(metinDosyasi) + "\",\n"
                + "  \"mp3Dosyasi\": \"" + jsonKacis(hedefMp3.toString()) + "\",\n"
                + "  \"bitrate\": \"" + jsonKacis(bitrate) + "\",\n"
                + "  \"karakterSayisi\": " + uretim.karakterSayisi() + ",\n"
                + "  \"wavDosyaBoyutu\": " + donusum.wavBoyutu() + ",\n"
                + "  \"mp3DosyaBoyutu\": " + donusum.mp3Boyutu() + ",\n"
                + "  \"piperUretimSuresiMs\": " + uretim.uretimSuresiMs() + ",\n"
                + "  \"mp3DonusumSuresiMs\": " + donusum.donusumSuresiMs() + ",\n"
                + "  \"cudaKullanildi\": " + uretim.cudaKullanildi() + ",\n"
                + "  \"bulutaGonderildi\": false,\n"
                + "  \"krediKullanildi\": false,\n"
                + "  \"uretimZamani\": \"" + OffsetDateTime.now() + "\"\n"
                + "}\n";
        Files.writeString(json, icerik, StandardCharsets.UTF_8);
    }

    /** Manifest ve çalma listelerini yazar, hazır parça sayısını döndürür. */
    private static int manifestVeListeleriYaz(Path eserSesKlasoru,
                                               Path parcaSesKlasoru,
                                               List<Path> metinParcalari,
                                               String model,
                                               boolean cuda,
                                               String bitrate) throws Exception {
        Files.createDirectories(eserSesKlasoru);
        Files.createDirectories(parcaSesKlasoru);

        StringBuilder json = new StringBuilder();
        StringBuilder okumaListesi = new StringBuilder();
        StringBuilder tumEserM3u = new StringBuilder("#EXTM3U\n");
        Map<String, StringBuilder> bolumM3u = new LinkedHashMap<>();
        int hazir = 0;

        json.append("{\n")
                .append("  \"saglayici\": \"Yerel Piper\",\n")
                .append("  \"format\": \"MP3\",\n")
                .append("  \"sesModeli\": \"").append(jsonKacis(model)).append("\",\n")
                .append("  \"bitrate\": \"").append(jsonKacis(bitrate)).append("\",\n")
                .append("  \"cudaKullanildi\": ").append(cuda).append(",\n")
                .append("  \"bulutaGonderildi\": false,\n")
                .append("  \"krediKullanildi\": false,\n")
                .append("  \"guncellenmeZamani\": \"").append(OffsetDateTime.now()).append("\",\n")
                .append("  \"parcalar\": [\n");

        for (int i = 0; i < metinParcalari.size(); i++) {
            Path metin = metinParcalari.get(i);
            String dosyaAdi = metin.getFileName().toString();
            String temelAd = uzantisiz(dosyaAdi);
            Path mp3 = parcaSesKlasoru.resolve(temelAd + ".mp3");
            boolean hazirMi = parcaGecerliMi(metin, parcaSesKlasoru, model);
            if (hazirMi) {
                hazir++;
                String relative = eserSesKlasoru.relativize(mp3).toString().replace('\\', '/');
                okumaListesi.append(temelAd).append(" | ").append(relative).append(System.lineSeparator());
                tumEserM3u.append(relative).append('\n');

                Matcher matcher = PARCA_ADI.matcher(dosyaAdi);
                if (matcher.matches()) {
                    bolumM3u.computeIfAbsent(matcher.group(1), k -> new StringBuilder("#EXTM3U\n"))
                            .append("../").append(relative).append('\n');
                }
            }

            json.append("    {\n")
                    .append("      \"sira\": ").append(i + 1).append(",\n")
                    .append("      \"metinDosyasi\": \"").append(jsonKacis(metin.toString())).append("\",\n")
                    .append("      \"mp3Dosyasi\": \"").append(jsonKacis(mp3.toString())).append("\",\n")
                    .append("      \"hazir\": ").append(hazirMi).append("\n")
                    .append("    }");
            if (i < metinParcalari.size() - 1) {
                json.append(',');
            }
            json.append('\n');
        }

        json.append("  ],\n")
                .append("  \"toplamParca\": ").append(metinParcalari.size()).append(",\n")
                .append("  \"hazirParca\": ").append(hazir).append(",\n")
                .append("  \"eksikParca\": ").append(Math.max(0, metinParcalari.size() - hazir)).append(",\n")
                .append("  \"tamamlanmaOrani\": ")
                .append(metinParcalari.isEmpty() ? "0.0"
                        : String.format(Locale.ROOT, "%.2f", hazir * 100.0 / metinParcalari.size()))
                .append("\n}\n");

        Files.writeString(parcaSesKlasoru.resolve("ses-manifest-piper.json"),
                json.toString(), StandardCharsets.UTF_8);
        Files.writeString(parcaSesKlasoru.resolve("ses-okuma-listesi-piper.txt"),
                okumaListesi.toString(), StandardCharsets.UTF_8);
        Files.writeString(eserSesKlasoru.resolve("tum-eser-piper-" + dosyaEtiketi(model) + ".m3u8"),
                tumEserM3u.toString(), StandardCharsets.UTF_8);

        Path bolumKlasoru = eserSesKlasoru.resolve("bolum-listeleri-piper-" + dosyaEtiketi(model));
        Files.createDirectories(bolumKlasoru);
        for (Map.Entry<String, StringBuilder> entry : bolumM3u.entrySet()) {
            Files.writeString(bolumKlasoru.resolve("bolum-" + entry.getKey() + ".m3u8"),
                    entry.getValue().toString(), StandardCharsets.UTF_8);
        }

        String durum = "saglayici=Yerel Piper" + System.lineSeparator()
                + "model=" + model + System.lineSeparator()
                + "format=MP3" + System.lineSeparator()
                + "bitrate=" + bitrate + System.lineSeparator()
                + "bulutaGonderildi=false" + System.lineSeparator()
                + "krediKullanildi=false" + System.lineSeparator()
                + "toplamParca=" + metinParcalari.size() + System.lineSeparator()
                + "hazirParca=" + hazir + System.lineSeparator()
                + "eksikParca=" + Math.max(0, metinParcalari.size() - hazir) + System.lineSeparator()
                + "guncellenmeZamani=" + OffsetDateTime.now() + System.lineSeparator();
        Files.writeString(eserSesKlasoru.resolve("_piper_ses_uretim_durumu.flag"),
                durum, StandardCharsets.UTF_8);
        Files.writeString(eserSesKlasoru.resolve("_ses_uretim_durumu.flag"),
                durum, StandardCharsets.UTF_8);

        Path tamamlandi = eserSesKlasoru.resolve("_tamamlandi-piper.flag");
        if (hazir == metinParcalari.size() && !metinParcalari.isEmpty()) {
            Files.writeString(tamamlandi,
                    "tamamlanmaZamani=" + OffsetDateTime.now() + System.lineSeparator()
                            + "saglayici=Yerel Piper" + System.lineSeparator()
                            + "model=" + model + System.lineSeparator()
                            + "toplamParca=" + hazir + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } else {
            Files.deleteIfExists(tamamlandi);
        }
        return hazir;
    }

    private static Istatistik istatistik(List<Path> parcalar) throws Exception {
        long karakter = 0L;
        long kelime = 0L;
        for (Path path : parcalar) {
            String metin = Files.readString(path, StandardCharsets.UTF_8).trim();
            karakter += metin.length();
            if (!metin.isBlank()) {
                kelime += metin.split("\\s+").length;
            }
        }
        long tahminiSaniye = kelime == 0 ? 0L : Math.max(1L, Math.round(kelime / 150.0 * 60.0));
        return new Istatistik(karakter, kelime, tahminiSaniye);
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[16 * 1024];
            int okunan;
            while ((okunan = input.read(buffer)) >= 0) {
                if (okunan > 0) {
                    digest.update(buffer, 0, okunan);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void atomikTasi(Path kaynak, Path hedef) throws Exception {
        try {
            Files.move(kaynak, hedef,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(kaynak, hedef, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String uzantisiz(String ad) {
        int nokta = ad.lastIndexOf('.');
        return nokta > 0 ? ad.substring(0, nokta) : ad;
    }

    private static String dosyaEtiketi(String metin) {
        return metin.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static boolean evetMi(String cevap) {
        if (cevap == null) {
            return false;
        }
        String temiz = cevap.trim().toUpperCase(Locale.forLanguageTag("tr-TR"));
        return temiz.equals("E") || temiz.equals("EVET") || temiz.equals("Y") || temiz.equals("YES");
    }

    private static int sayiOku(String metin, int varsayilan) {
        try {
            return Integer.parseInt(metin == null ? "" : metin.trim());
        } catch (Exception e) {
            return varsayilan;
        }
    }

    private static String sayiBicimle(long sayi) {
        return String.format(Locale.forLanguageTag("tr-TR"), "%,d", sayi);
    }

    private static String sureBicimle(long saniye) {
        long saat = saniye / 3_600;
        long dakika = (saniye % 3_600) / 60;
        long kalanSaniye = saniye % 60;
        if (saat > 0) {
            return saat + " sa " + dakika + " dk";
        }
        if (dakika > 0) {
            return dakika + " dk " + kalanSaniye + " sn";
        }
        return kalanSaniye + " sn";
    }

    private static String tahminiMp3Boyutu(long saniye, String bitrate) {
        long kbps = 64L;
        try {
            kbps = Long.parseLong(bitrate.toLowerCase(Locale.ROOT).replace("k", ""));
        } catch (Exception ignored) {
            // Varsayılan 64 kbit/s kullanılır.
        }
        long bayt = Math.max(0L, Math.round(saniye * kbps * 1_000.0 / 8.0));
        return okunabilirBoyut(bayt);
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

    private static String jsonKacis(String metin) {
        return metin
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private record Istatistik(long karakter, long kelime, long tahminiSaniye) {
    }

    private record UretimPlani(List<Path> parcalar, String aciklama) {
    }

    public record Sonuc(boolean basarili,
                        boolean atlandi,
                        boolean tumuTamam,
                        String mesaj,
                        Path eserSesKlasoru,
                        int buCalismadaUretilen,
                        int toplamHazir,
                        int toplamParca,
                        String sesModeli) {
        public static Sonuc basarili(Path klasor,
                                     int uretilen,
                                     int hazir,
                                     int toplam,
                                     boolean tumuTamam,
                                     String model) {
            return new Sonuc(true, false, tumuTamam, "Başarılı",
                    klasor, uretilen, hazir, toplam, model);
        }

        public static Sonuc atlandi(String mesaj) {
            return new Sonuc(false, true, false, mesaj,
                    null, 0, 0, 0, "Bilinmiyor");
        }

        public static Sonuc hatali(Path klasor,
                                   int uretilen,
                                   int hazir,
                                   int toplam,
                                   String mesaj,
                                   String model) {
            return new Sonuc(false, false, false, mesaj,
                    klasor, uretilen, hazir, toplam, model);
        }
    }
}
