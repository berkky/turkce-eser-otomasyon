import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Hazır Piper MP3 parçalarını bölüm MP3'lerine, tek tam-eser MP3'e ve
 * bölüm işaretli M4B sesli kitaba paketler.
 */
public final class SesliKitapPaketlemeService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PIPER_KLASORU = Pattern.compile("^parcalar-piper-(.+)$");
    private static final int EN_AZ_GECERLI_SES_BOYUTU = 1_000;

    private SesliKitapPaketlemeService() {
    }

    public static Sonuc interaktifPaketle(Path projeKlasoru,
                                          Path metinArsivKlasoru,
                                          Path sesArsivKlasoru,
                                          BufferedReader konsol) {
        try {
            FfmpegClient ffmpeg = new FfmpegClient(projeKlasoru);
            FfmpegClient.KontrolSonucu kontrol = ffmpeg.kontrolEt();
            if (!kontrol.hazir()) {
                System.err.println("Sesli kitap paketleme atlandı: FFmpeg hazır değil.");
                return Sonuc.atlandi("FFmpeg hazır değil");
            }

            List<Aday> adaylar = adaylariBul(metinArsivKlasoru, sesArsivKlasoru);
            if (adaylar.isEmpty()) {
                return Sonuc.atlandi("Paketlenecek Piper MP3 arşivi yok");
            }

            Aday aday = adaySec(adaylar, konsol);
            if (aday == null) {
                return Sonuc.atlandi("Paketleme için eser seçilmedi");
            }

            Analiz analiz = analizEt(aday);
            durumYaz(analiz, ffmpeg);

            System.out.println();
            System.out.println("--- SESLİ KİTAP PAKETLEME MENÜSÜ ---");
            System.out.println("1 - Hazır olan tamamlanmış bölümleri MP3 olarak birleştir");
            System.out.println("2 - Tüm parçalar hazırsa tek tam-eser MP3 oluştur");
            System.out.println("3 - Tüm parçalar hazırsa bölümlü M4B oluştur");
            System.out.println("4 - Şu anda mümkün olan her şeyi oluştur");
            System.out.println("0 - Paketlemeyi atla");
            System.out.print("Seçimin: ");
            int secim = sayiOku(konsol.readLine(), -1);
            if (secim == 0) {
                return Sonuc.atlandi("Kullanıcı paketlemeyi atladı");
            }
            if (secim < 1 || secim > 4) {
                return Sonuc.atlandi("Geçersiz paketleme seçimi");
            }

            int yeniBolum = 0;
            Path tamMp3 = null;
            Path m4b = null;
            boolean bolumlerKontrolEdildi = false;

            if (secim == 1 || secim == 4) {
                yeniBolum += bolumleriPaketle(analiz, ffmpeg);
                bolumlerKontrolEdildi = true;
            }
            if (secim == 2 || secim == 4) {
                if (analiz.tumParcalarHazir()) {
                    if (!bolumlerKontrolEdildi) {
                        yeniBolum += bolumleriPaketle(analiz, ffmpeg);
                        bolumlerKontrolEdildi = true;
                    }
                    tamMp3 = tamEserMp3Olustur(analiz, ffmpeg);
                } else {
                    tumEserUyarisi(analiz, "Tam eser MP3");
                }
            }
            if (secim == 3 || secim == 4) {
                if (analiz.tumParcalarHazir()) {
                    if (!bolumlerKontrolEdildi) {
                        yeniBolum += bolumleriPaketle(analiz, ffmpeg);
                    }
                    m4b = m4bOlustur(analiz, ffmpeg);
                } else {
                    tumEserUyarisi(analiz, "M4B");
                }
            }

            Analiz sonAnaliz = analizEt(aday);
            paketlemeManifestiYaz(sonAnaliz, ffmpeg, tamMp3, m4b);

            System.out.println();
            System.out.println("Sesli kitap paketleme işlemi tamamlandı.");
            System.out.println("Bu çalışmada oluşturulan bölüm MP3: " + yeniBolum);
            System.out.println("Hazır bölüm MP3: " + sonAnaliz.hazirBolumCiktisi()
                    + "/" + sonAnaliz.bolumler().size());
            if (tamMp3 != null) {
                System.out.println("Tam eser MP3: " + tamMp3);
            }
            if (m4b != null) {
                System.out.println("Bölümlü M4B: " + m4b);
            }
            System.out.println("Paket klasörü: " + sonAnaliz.paketKlasoru());

            return Sonuc.basarili(
                    sonAnaliz.eserSesKlasoru(),
                    sonAnaliz.paketKlasoru(),
                    yeniBolum,
                    sonAnaliz.hazirBolumCiktisi(),
                    sonAnaliz.bolumler().size(),
                    tamMp3,
                    m4b
            );
        } catch (Exception e) {
            System.err.println("Sesli kitap paketleme tamamlanamadı: " + e.getMessage());
            return Sonuc.hatali(e.getMessage());
        }
    }

    private static List<Aday> adaylariBul(Path metinArsivKlasoru,
                                           Path sesArsivKlasoru) throws Exception {
        if (!Files.isDirectory(metinArsivKlasoru) || !Files.isDirectory(sesArsivKlasoru)) {
            return List.of();
        }
        List<Aday> sonuc = new ArrayList<>();
        try (Stream<Path> metinKlasorleri = Files.list(metinArsivKlasoru)) {
            for (Path metinEser : metinKlasorleri.filter(Files::isDirectory).toList()) {
                Path ttsManifest = metinEser.resolve("tts-parcalari").resolve("tts-manifest.json");
                if (!Files.isRegularFile(ttsManifest)) {
                    continue;
                }
                Path sesEser = sesArsivKlasoru.resolve(metinEser.getFileName().toString());
                if (!Files.isDirectory(sesEser)) {
                    continue;
                }
                try (Stream<Path> altKlasorler = Files.list(sesEser)) {
                    for (Path parcaKlasoru : altKlasorler.filter(Files::isDirectory).toList()) {
                        Matcher matcher = PIPER_KLASORU.matcher(parcaKlasoru.getFileName().toString());
                        if (!matcher.matches()) {
                            continue;
                        }
                        boolean mp3Var;
                        try (Stream<Path> dosyalar = Files.list(parcaKlasoru)) {
                            mp3Var = dosyalar.anyMatch(path -> Files.isRegularFile(path)
                                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3"));
                        }
                        if (mp3Var) {
                            sonuc.add(new Aday(metinEser, sesEser, parcaKlasoru, matcher.group(1)));
                        }
                    }
                }
            }
        }
        return sonuc.stream()
                .sorted(Comparator.comparing((Aday a) -> a.metinEserKlasoru().getFileName().toString())
                        .thenComparing(Aday::modelEtiketi))
                .toList();
    }

    private static Aday adaySec(List<Aday> adaylar, BufferedReader konsol) throws Exception {
        if (adaylar.size() == 1) {
            System.out.println("Sesli kitap paketleme eseri: "
                    + adaylar.get(0).metinEserKlasoru().getFileName());
            return adaylar.get(0);
        }
        System.out.println();
        System.out.println("--- PAKETLENEBİLİR PIPER ARŞİVLERİ ---");
        for (int i = 0; i < adaylar.size(); i++) {
            Aday aday = adaylar.get(i);
            System.out.printf(Locale.ROOT, "%2d - %s | %s%n",
                    i + 1,
                    aday.metinEserKlasoru().getFileName(),
                    aday.modelEtiketi());
        }
        System.out.print("Paketlenecek arşiv numarası (0 = iptal): ");
        int secim = sayiOku(konsol.readLine(), 0);
        if (secim < 1 || secim > adaylar.size()) {
            return null;
        }
        return adaylar.get(secim - 1);
    }

    private static Analiz analizEt(Aday aday) throws Exception {
        Path ttsManifest = aday.metinEserKlasoru().resolve("tts-parcalari").resolve("tts-manifest.json");
        JsonNode tts = JSON.readTree(ttsManifest.toFile());
        JsonNode metinManifest = manifestOku(aday.metinEserKlasoru().resolve("manifest.json"));

        String klasorAdi = aday.metinEserKlasoru().getFileName().toString();
        String varsayilanEserAdi = klasorAdi.replaceFirst("(?i)^ESER-\\d+\\s*-\\s*", "").trim();
        String eserAdi = metinManifest.path("eserAdi").asText(varsayilanEserAdi);
        String yazar = metinManifest.path("yazar").asText("Bilinmiyor");
        String dil = metinManifest.path("dil").asText("Türkçe");

        Map<Integer, BolumBuilder> bolumHaritasi = new LinkedHashMap<>();
        int toplamParca = 0;
        int hazirParca = 0;

        for (JsonNode parca : tts.path("parcalar")) {
            int bolumSira = parca.path("bolumSira").asInt();
            int bolumParcaSira = parca.path("bolumParcaSira").asInt();
            String bolumBasligi = parca.path("bolumBasligi").asText("Bölüm " + bolumSira);
            String metinDosyasi = parca.path("metinDosyasi").asText();
            String temelAd = metinDosyasi.replaceFirst("(?i)\\.txt$", "");
            Path mp3 = aday.parcaSesKlasoru().resolve(temelAd + ".mp3");
            boolean hazir = sesGecerliMi(mp3);

            BolumBuilder builder = bolumHaritasi.computeIfAbsent(
                    bolumSira,
                    sira -> new BolumBuilder(bolumSira, bolumBasligi)
            );
            builder.parcalar.add(new Parca(bolumParcaSira, metinDosyasi, mp3, hazir));
            toplamParca++;
            if (hazir) {
                hazirParca++;
            }
        }

        List<Bolum> bolumler = new ArrayList<>();
        Path bolumKlasoru = aday.eserSesKlasoru().resolve("bolumler-piper-" + aday.modelEtiketi());
        Path paketKlasoru = aday.eserSesKlasoru().resolve("sesli-kitap-piper-" + aday.modelEtiketi());
        Files.createDirectories(bolumKlasoru);
        Files.createDirectories(paketKlasoru);

        int hazirBolumCiktisi = 0;
        for (BolumBuilder builder : bolumHaritasi.values()) {
            builder.parcalar.sort(Comparator.comparingInt(Parca::bolumParcaSira));
            boolean tamam = builder.parcalar.stream().allMatch(Parca::hazir);
            String dosyaAdi = String.format(Locale.ROOT, "bolum-%03d - %s.mp3",
                    builder.sira, guvenliDosyaAdi(builder.baslik, 90));
            Path cikti = bolumKlasoru.resolve(dosyaAdi);
            Path yanBilgi = yanBilgiYolu(cikti);
            String imza = tamam ? kaynakImzasi(builder.parcalar.stream().map(Parca::mp3).toList()) : "";
            boolean ciktiHazir = tamam && paketCiktisiGecerliMi(cikti, yanBilgi, imza);
            if (ciktiHazir) {
                hazirBolumCiktisi++;
            }
            bolumler.add(new Bolum(
                    builder.sira,
                    builder.baslik,
                    List.copyOf(builder.parcalar),
                    tamam,
                    cikti,
                    yanBilgi,
                    imza,
                    ciktiHazir
            ));
        }

        Path kapak = kapakBul(aday.metinEserKlasoru(), aday.eserSesKlasoru());
        return new Analiz(
                aday,
                eserAdi,
                yazar,
                dil,
                List.copyOf(bolumler),
                toplamParca,
                hazirParca,
                bolumKlasoru,
                paketKlasoru,
                hazirBolumCiktisi,
                kapak
        );
    }

    private static JsonNode manifestOku(Path manifest) throws Exception {
        if (!Files.isRegularFile(manifest)) {
            return JSON.createObjectNode();
        }
        return JSON.readTree(manifest.toFile());
    }

    private static void durumYaz(Analiz analiz, FfmpegClient ffmpeg) {
        long tamamBolum = analiz.bolumler().stream().filter(Bolum::tamam).count();
        long eksikBolum = analiz.bolumler().size() - tamamBolum;

        System.out.println();
        System.out.println("--- SESLİ KİTAP PAKETLEME DURUMU ---");
        System.out.println("Eser               : " + analiz.eserAdi());
        System.out.println("Yazar              : " + analiz.yazar());
        System.out.println("Piper modeli       : " + analiz.aday().modelEtiketi());
        System.out.println("Hazır parça        : " + analiz.hazirParca() + "/" + analiz.toplamParca());
        System.out.println("Tamamlanmış bölüm  : " + tamamBolum + "/" + analiz.bolumler().size());
        System.out.println("Eksik bölüm        : " + eksikBolum);
        System.out.println("Hazır bölüm MP3    : " + analiz.hazirBolumCiktisi());
        System.out.println("Tam eser durumu    : "
                + (analiz.tumParcalarHazir() ? "HAZIR — MP3 ve M4B üretilebilir" : "BEKLİYOR"));
        System.out.println("MP3/M4B ayarı      : " + ffmpeg.bitrate() + " mono");
        System.out.println("Kapak              : " + (analiz.kapak() == null ? "Bulunamadı — kapaksız üretilecek" : analiz.kapak()));
    }

    private static int bolumleriPaketle(Analiz analiz, FfmpegClient ffmpeg) throws Exception {
        int olusturulan = 0;
        int paketlenebilir = 0;
        for (Bolum bolum : analiz.bolumler()) {
            if (!bolum.tamam()) {
                continue;
            }
            paketlenebilir++;
            if (paketCiktisiGecerliMi(bolum.ciktiMp3(), bolum.yanBilgi(), bolum.kaynakImzasi())) {
                continue;
            }

            System.out.println();
            System.out.println("Bölüm birleştiriliyor: " + String.format(Locale.ROOT, "%03d", bolum.sira())
                    + " — " + bolum.baslik());
            List<Path> kaynaklar = bolum.parcalar().stream().map(Parca::mp3).toList();
            FfmpegClient.MedyaEtiketleri etiketler = new FfmpegClient.MedyaEtiketleri(
                    bolum.baslik(),
                    analiz.yazar(),
                    analiz.eserAdi(),
                    "Audiobook",
                    analiz.dil(),
                    "Yerel Piper ile oluşturuldu — Bölüm " + bolum.sira()
            );
            FfmpegClient.BirlesimSonucu sonuc = ffmpeg.mp3leriBirlestir(kaynaklar, bolum.ciktiMp3(), etiketler);
            yanBilgiYaz(
                    bolum.yanBilgi(),
                    "BOLUM_MP3",
                    bolum.kaynakImzasi(),
                    sonuc,
                    analiz,
                    bolum.baslik(),
                    bolum.sira()
            );
            olusturulan++;
            System.out.println("Bölüm MP3 hazır: " + bolum.ciktiMp3());
            System.out.println("Ses süresi: " + sureBicimle(sonuc.sesSuresiSaniye()));
            System.out.println("Dosya boyutu: " + okunabilirBoyut(sonuc.ciktiBoyutu()));
        }

        if (paketlenebilir == 0) {
            System.out.println("Henüz tüm parçaları hazır olan bir bölüm bulunmuyor.");
        } else if (olusturulan == 0) {
            System.out.println("Tamamlanmış bölümlerin MP3 dosyaları zaten güncel.");
        }
        return olusturulan;
    }

    private static Path tamEserMp3Olustur(Analiz analiz, FfmpegClient ffmpeg) throws Exception {
        Analiz guncel = analizEt(analiz.aday());
        List<Path> bolumMp3leri = guncel.bolumler().stream()
                .map(Bolum::ciktiMp3)
                .toList();
        for (Path bolumMp3 : bolumMp3leri) {
            if (!sesGecerliMi(bolumMp3)) {
                throw new IOException("Tam eser için bölüm MP3 eksik: " + bolumMp3.getFileName());
            }
        }

        String imza = kaynakImzasi(bolumMp3leri);
        Path hedef = guncel.paketKlasoru().resolve(guvenliDosyaAdi(guncel.eserAdi(), 100) + " - tam-eser.mp3");
        Path yanBilgi = yanBilgiYolu(hedef);
        if (paketCiktisiGecerliMi(hedef, yanBilgi, imza)) {
            System.out.println("Tam eser MP3 zaten güncel: " + hedef);
            return hedef;
        }

        System.out.println();
        System.out.println("Tam eser MP3 oluşturuluyor...");
        FfmpegClient.MedyaEtiketleri etiketler = new FfmpegClient.MedyaEtiketleri(
                guncel.eserAdi(),
                guncel.yazar(),
                guncel.eserAdi(),
                "Audiobook",
                guncel.dil(),
                "Yerel Piper ile oluşturulan tam sesli kitap"
        );
        FfmpegClient.BirlesimSonucu sonuc = ffmpeg.mp3leriBirlestir(bolumMp3leri, hedef, etiketler);
        yanBilgiYaz(yanBilgi, "TAM_ESER_MP3", imza, sonuc, guncel, guncel.eserAdi(), 0);
        System.out.println("Tam eser MP3 hazır: " + hedef);
        System.out.println("Ses süresi: " + sureBicimle(sonuc.sesSuresiSaniye()));
        System.out.println("Dosya boyutu: " + okunabilirBoyut(sonuc.ciktiBoyutu()));
        return hedef;
    }

    private static Path m4bOlustur(Analiz analiz, FfmpegClient ffmpeg) throws Exception {
        Analiz guncel = analizEt(analiz.aday());
        List<Path> bolumMp3leri = guncel.bolumler().stream().map(Bolum::ciktiMp3).toList();
        for (Path bolumMp3 : bolumMp3leri) {
            if (!sesGecerliMi(bolumMp3)) {
                throw new IOException("M4B için bölüm MP3 eksik: " + bolumMp3.getFileName());
            }
        }

        String imza = kaynakImzasi(bolumMp3leri);
        Path hedef = guncel.paketKlasoru().resolve(guvenliDosyaAdi(guncel.eserAdi(), 100) + ".m4b");
        Path yanBilgi = yanBilgiYolu(hedef);
        if (paketCiktisiGecerliMi(hedef, yanBilgi, imza)) {
            System.out.println("Bölümlü M4B zaten güncel: " + hedef);
            return hedef;
        }

        List<FfmpegClient.BolumIsareti> isaretler = new ArrayList<>();
        long baslangicMs = 0L;
        for (int i = 0; i < bolumMp3leri.size(); i++) {
            double sure = ffmpeg.sureSaniye(bolumMp3leri.get(i));
            long sureMs = Math.max(1L, Math.round(sure * 1_000.0));
            Bolum bolum = guncel.bolumler().get(i);
            String baslik = String.format(Locale.ROOT, "%03d - %s", bolum.sira(), bolum.baslik());
            isaretler.add(new FfmpegClient.BolumIsareti(baslik, baslangicMs, baslangicMs + sureMs));
            baslangicMs += sureMs;
        }

        System.out.println();
        System.out.println("Bölümlü M4B oluşturuluyor...");
        FfmpegClient.MedyaEtiketleri etiketler = new FfmpegClient.MedyaEtiketleri(
                guncel.eserAdi(),
                guncel.yazar(),
                guncel.eserAdi(),
                "Audiobook",
                guncel.dil(),
                "Yerel Piper ile oluşturulan bölümlü M4B sesli kitap"
        );
        FfmpegClient.BirlesimSonucu sonuc = ffmpeg.m4bOlustur(
                bolumMp3leri,
                isaretler,
                hedef,
                etiketler,
                guncel.kapak()
        );
        yanBilgiYaz(yanBilgi, "M4B", imza, sonuc, guncel, guncel.eserAdi(), 0);
        System.out.println("Bölümlü M4B hazır: " + hedef);
        System.out.println("Bölüm işareti: " + isaretler.size());
        System.out.println("Ses süresi: " + sureBicimle(sonuc.sesSuresiSaniye()));
        System.out.println("Dosya boyutu: " + okunabilirBoyut(sonuc.ciktiBoyutu()));
        return hedef;
    }

    private static void tumEserUyarisi(Analiz analiz, String format) {
        System.out.println(format + " henüz oluşturulamaz.");
        System.out.println("Hazır parça: " + analiz.hazirParca() + "/" + analiz.toplamParca());
        System.out.println("Eksik parça: " + (analiz.toplamParca() - analiz.hazirParca()));
    }

    private static boolean paketCiktisiGecerliMi(Path cikti,
                                                  Path yanBilgi,
                                                  String kaynakImzasi) {
        try {
            if (!sesGecerliMi(cikti) || !Files.isRegularFile(yanBilgi)) {
                return false;
            }
            JsonNode bilgi = JSON.readTree(yanBilgi.toFile());
            return kaynakImzasi.equals(bilgi.path("kaynakImzasi").asText());
        } catch (Exception e) {
            return false;
        }
    }

    private static void yanBilgiYaz(Path hedef,
                                    String tur,
                                    String kaynakImzasi,
                                    FfmpegClient.BirlesimSonucu sonuc,
                                    Analiz analiz,
                                    String baslik,
                                    int bolumSira) throws Exception {
        Map<String, Object> bilgi = new LinkedHashMap<>();
        bilgi.put("tur", tur);
        bilgi.put("eserAdi", analiz.eserAdi());
        bilgi.put("yazar", analiz.yazar());
        bilgi.put("baslik", baslik);
        bilgi.put("bolumSira", bolumSira);
        bilgi.put("piperModeli", analiz.aday().modelEtiketi());
        bilgi.put("format", sonuc.format());
        bilgi.put("bitrate", sonuc.bitrate());
        bilgi.put("kaynakImzasi", kaynakImzasi);
        bilgi.put("kaynakSayisi", sonuc.kaynaklar().size());
        bilgi.put("sesSuresiSaniye", sonuc.sesSuresiSaniye());
        bilgi.put("dosyaBoyutu", sonuc.ciktiBoyutu());
        bilgi.put("islemSuresiMs", sonuc.islemSuresiMs());
        bilgi.put("cikti", sonuc.hedef().toAbsolutePath().toString());
        bilgi.put("olusturmaZamani", OffsetDateTime.now().toString());
        JSON.writerWithDefaultPrettyPrinter().writeValue(hedef.toFile(), bilgi);
    }

    private static void paketlemeManifestiYaz(Analiz analiz,
                                               FfmpegClient ffmpeg,
                                               Path tamMp3,
                                               Path m4b) throws Exception {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("surum", 1);
        manifest.put("eserAdi", analiz.eserAdi());
        manifest.put("yazar", analiz.yazar());
        manifest.put("dil", analiz.dil());
        manifest.put("piperModeli", analiz.aday().modelEtiketi());
        manifest.put("bitrate", ffmpeg.bitrate());
        manifest.put("toplamParca", analiz.toplamParca());
        manifest.put("hazirParca", analiz.hazirParca());
        manifest.put("toplamBolum", analiz.bolumler().size());
        manifest.put("tamamlanmisBolum", analiz.bolumler().stream().filter(Bolum::tamam).count());
        manifest.put("hazirBolumMp3", analiz.hazirBolumCiktisi());
        manifest.put("tumParcalarHazir", analiz.tumParcalarHazir());
        manifest.put("tamEserMp3", tamMp3 == null ? mevcutTamEserMp3(analiz) : tamMp3.toString());
        manifest.put("m4b", m4b == null ? mevcutM4b(analiz) : m4b.toString());
        manifest.put("kapak", analiz.kapak() == null ? "" : analiz.kapak().toString());
        manifest.put("guncellenmeZamani", OffsetDateTime.now().toString());

        List<Map<String, Object>> bolumler = new ArrayList<>();
        for (Bolum bolum : analiz.bolumler()) {
            Map<String, Object> bilgi = new LinkedHashMap<>();
            bilgi.put("sira", bolum.sira());
            bilgi.put("baslik", bolum.baslik());
            bilgi.put("toplamParca", bolum.parcalar().size());
            bilgi.put("hazirParca", bolum.parcalar().stream().filter(Parca::hazir).count());
            bilgi.put("tamam", bolum.tamam());
            bilgi.put("bolumMp3Hazir", paketCiktisiGecerliMi(
                    bolum.ciktiMp3(), bolum.yanBilgi(), bolum.kaynakImzasi()));
            bilgi.put("bolumMp3", bolum.ciktiMp3().toString());
            bolumler.add(bilgi);
        }
        manifest.put("bolumler", bolumler);

        JSON.writerWithDefaultPrettyPrinter().writeValue(
                analiz.paketKlasoru().resolve("sesli-kitap-paketleme-manifest.json").toFile(),
                manifest
        );

        String rapor = "SESLİ KİTAP PAKETLEME RAPORU" + System.lineSeparator()
                + "==============================" + System.lineSeparator()
                + "Eser: " + analiz.eserAdi() + System.lineSeparator()
                + "Yazar: " + analiz.yazar() + System.lineSeparator()
                + "Piper modeli: " + analiz.aday().modelEtiketi() + System.lineSeparator()
                + "Hazır parça: " + analiz.hazirParca() + "/" + analiz.toplamParca() + System.lineSeparator()
                + "Hazır bölüm MP3: " + analiz.hazirBolumCiktisi() + "/" + analiz.bolumler().size() + System.lineSeparator()
                + "Tam eser hazır: " + analiz.tumParcalarHazir() + System.lineSeparator()
                + "Kapak: " + (analiz.kapak() == null ? "Yok" : analiz.kapak()) + System.lineSeparator()
                + "Güncellenme: " + OffsetDateTime.now() + System.lineSeparator();
        Files.writeString(
                analiz.paketKlasoru().resolve("sesli-kitap-paketleme-raporu.txt"),
                rapor,
                StandardCharsets.UTF_8
        );
    }

    private static String mevcutTamEserMp3(Analiz analiz) {
        Path yol = analiz.paketKlasoru().resolve(guvenliDosyaAdi(analiz.eserAdi(), 100) + " - tam-eser.mp3");
        return sesGecerliMi(yol) ? yol.toString() : "";
    }

    private static String mevcutM4b(Analiz analiz) {
        Path yol = analiz.paketKlasoru().resolve(guvenliDosyaAdi(analiz.eserAdi(), 100) + ".m4b");
        return sesGecerliMi(yol) ? yol.toString() : "";
    }

    private static String kaynakImzasi(List<Path> dosyalar) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Path dosya : dosyalar) {
            digest.update(dosya.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Long.toString(Files.size(dosya)).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Long.toString(Files.getLastModifiedTime(dosya).toMillis()).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path kapakBul(Path metinEserKlasoru, Path sesEserKlasoru) {
        List<String> adayAdlari = List.of(
                "kapak.jpg", "kapak.jpeg", "kapak.png",
                "cover.jpg", "cover.jpeg", "cover.png"
        );
        for (Path kok : List.of(metinEserKlasoru, sesEserKlasoru)) {
            for (String ad : adayAdlari) {
                Path aday = kok.resolve(ad);
                if (Files.isRegularFile(aday)) {
                    return aday;
                }
            }
            try (Stream<Path> yollar = Files.walk(kok, 2)) {
                Path bulunan = yollar
                        .filter(Files::isRegularFile)
                        .filter(path -> adayAdlari.contains(path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .findFirst()
                        .orElse(null);
                if (bulunan != null) {
                    return bulunan;
                }
            } catch (Exception ignored) {
                // Kapak isteğe bağlıdır.
            }
        }
        return null;
    }

    private static boolean sesGecerliMi(Path yol) {
        try {
            return yol != null && Files.isRegularFile(yol) && Files.size(yol) >= EN_AZ_GECERLI_SES_BOYUTU;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path yanBilgiYolu(Path medya) {
        return medya.resolveSibling(medya.getFileName().toString() + ".json");
    }

    private static int sayiOku(String metin, int varsayilan) {
        try {
            return Integer.parseInt(metin == null ? "" : metin.trim());
        } catch (Exception e) {
            return varsayilan;
        }
    }

    private static String guvenliDosyaAdi(String deger, int sinir) {
        String temiz = deger == null ? "Bilinmeyen" : deger
                .replaceAll("[\\\\/:*?\"<>|]", "-")
                .replaceAll("\\s+", " ")
                .trim();
        if (temiz.isBlank()) {
            temiz = "Bilinmeyen";
        }
        if (temiz.length() > sinir) {
            temiz = temiz.substring(0, sinir).trim();
        }
        return temiz.replaceAll("[. ]+$", "");
    }

    private static String okunabilirBoyut(long bayt) {
        if (bayt < 1_024) {
            return bayt + " B";
        }
        if (bayt < 1_048_576) {
            return String.format(Locale.ROOT, "%.1f KB", bayt / 1_024.0);
        }
        if (bayt < 1_073_741_824L) {
            return String.format(Locale.ROOT, "%.1f MB", bayt / 1_048_576.0);
        }
        return String.format(Locale.ROOT, "%.2f GB", bayt / 1_073_741_824.0);
    }

    private static String sureBicimle(double saniye) {
        long toplam = Math.max(0L, Math.round(saniye));
        long saat = toplam / 3_600;
        long dakika = (toplam % 3_600) / 60;
        long kalan = toplam % 60;
        if (saat > 0) {
            return saat + " sa " + dakika + " dk " + kalan + " sn";
        }
        return dakika + " dk " + kalan + " sn";
    }

    private static final class BolumBuilder {
        private final int sira;
        private final String baslik;
        private final List<Parca> parcalar = new ArrayList<>();

        private BolumBuilder(int sira, String baslik) {
            this.sira = sira;
            this.baslik = baslik;
        }
    }

    private record Aday(Path metinEserKlasoru,
                        Path eserSesKlasoru,
                        Path parcaSesKlasoru,
                        String modelEtiketi) {
    }

    private record Parca(int bolumParcaSira,
                         String metinDosyasi,
                         Path mp3,
                         boolean hazir) {
    }

    private record Bolum(int sira,
                         String baslik,
                         List<Parca> parcalar,
                         boolean tamam,
                         Path ciktiMp3,
                         Path yanBilgi,
                         String kaynakImzasi,
                         boolean ciktiHazir) {
    }

    private record Analiz(Aday aday,
                          String eserAdi,
                          String yazar,
                          String dil,
                          List<Bolum> bolumler,
                          int toplamParca,
                          int hazirParca,
                          Path bolumKlasoru,
                          Path paketKlasoru,
                          int hazirBolumCiktisi,
                          Path kapak) {
        private boolean tumParcalarHazir() {
            return toplamParca > 0 && hazirParca == toplamParca;
        }

        private Path eserSesKlasoru() {
            return aday.eserSesKlasoru();
        }
    }

    public record Sonuc(boolean basarili,
                        boolean atlandi,
                        String mesaj,
                        Path eserSesKlasoru,
                        Path paketKlasoru,
                        int buCalismadaOlusturulanBolum,
                        int hazirBolum,
                        int toplamBolum,
                        Path tamEserMp3,
                        Path m4b) {
        public static Sonuc basarili(Path eserSesKlasoru,
                                     Path paketKlasoru,
                                     int yeniBolum,
                                     int hazirBolum,
                                     int toplamBolum,
                                     Path tamEserMp3,
                                     Path m4b) {
            return new Sonuc(true, false, "Başarılı", eserSesKlasoru, paketKlasoru,
                    yeniBolum, hazirBolum, toplamBolum, tamEserMp3, m4b);
        }

        public static Sonuc atlandi(String mesaj) {
            return new Sonuc(false, true, mesaj, null, null, 0, 0, 0, null, null);
        }

        public static Sonuc hatali(String mesaj) {
            return new Sonuc(false, false, mesaj, null, null, 0, 0, 0, null, null);
        }
    }
}
