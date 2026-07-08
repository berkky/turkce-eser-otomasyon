import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Hazırlanmış TTS metin parçalarını ElevenLabs ile kontrollü biçimde seslendirir.
 * İlk testte varsayılan olarak yalnızca üç eksik parça üretilir.
 */
public final class ElevenLabsTopluService {
    private static final int VARSAYILAN_TEST_LIMITI = 3;
    private static final int BAGLAM_KARAKTERI = 700;
    private static final int EN_AZ_GECERLI_MP3_BOYUTU = 1_024;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ElevenLabsTopluService() {
    }

    public static TopluSonuc interaktifIlkParcalariUret(Path metinArsivKlasoru,
                                                        Path sesArsivKlasoru,
                                                        BufferedReader konsol) {
        try {
            String apiKey = System.getenv("ELEVENLABS_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                System.out.println("ElevenLabs toplu TTS: API anahtarı yok; adım atlandı.");
                return TopluSonuc.atlandi("API anahtarı yok");
            }

            SecilenSes secilenSes = secilenSesiOku(sesArsivKlasoru.resolve("elevenlabs-secilen-ses.json"));
            if (secilenSes == null) {
                System.out.println("ElevenLabs toplu TTS: seçilmiş ses kaydı bulunamadı.");
                System.out.println("Önce Adım 6 ile kısa ses örneği üretip bir ses seçmelisin.");
                return TopluSonuc.atlandi("Seçili ses yok");
            }

            List<Path> eserKlasorleri = ttsHazirEserleriListele(metinArsivKlasoru);
            if (eserKlasorleri.isEmpty()) {
                System.out.println("ElevenLabs toplu TTS: seslendirilecek TTS paketi bulunamadı.");
                return TopluSonuc.atlandi("TTS paketi yok");
            }

            int limit = testLimiti();
            System.out.print("ElevenLabs ile ilk " + limit + " eksik TTS parçasını üretmek ister misin? (E/H): ");
            if (!evetMi(konsol.readLine())) {
                return TopluSonuc.atlandi("Kullanıcı atladı");
            }

            Path eserKlasoru = eserSec(eserKlasorleri, konsol);
            if (eserKlasoru == null) {
                return TopluSonuc.atlandi("Eser seçilmedi");
            }

            Path ttsKlasoru = eserKlasoru.resolve("tts-parcalari");
            List<Path> metinParcalari = metinParcalariniListele(ttsKlasoru);
            if (metinParcalari.isEmpty()) {
                throw new IllegalStateException("Seçilen eserde TTS metin parçası bulunamadı.");
            }

            ElevenLabsClient client = new ElevenLabsClient(apiKey);
            ElevenLabsClient.Ses ses = new ElevenLabsClient.Ses(
                    secilenSes.voiceId(),
                    secilenSes.voiceName(),
                    "seçilen",
                    "",
                    "",
                    "",
                    true
            );

            Path eserSesKlasoru = sesArsivKlasoru.resolve(eserKlasoru.getFileName().toString());
            Path parcaSesKlasoru = eserSesKlasoru.resolve("parcalar");
            Files.createDirectories(parcaSesKlasoru);

            List<Path> eksikParcalar = new ArrayList<>();
            int zatenHazir = 0;
            for (Path metinDosyasi : metinParcalari) {
                if (parcaGecerliMi(metinDosyasi, parcaSesKlasoru, ses, client.getModel())) {
                    zatenHazir++;
                } else {
                    eksikParcalar.add(metinDosyasi);
                }
            }

            System.out.println("Seçilen eser : " + eserKlasoru.getFileName());
            System.out.println("Seçilen ses  : " + ses.ad());
            System.out.println("Model        : " + client.getModel());
            System.out.println("Toplam parça : " + metinParcalari.size());
            System.out.println("Hazır parça  : " + zatenHazir);
            System.out.println("Eksik parça  : " + eksikParcalar.size());

            if (eksikParcalar.isEmpty()) {
                manifestYaz(eserKlasoru, parcaSesKlasoru, metinParcalari, ses, client.getModel());
                System.out.println("Bu eser için bütün TTS parçaları zaten hazır.");
                return TopluSonuc.basarili(eserSesKlasoru, 0, zatenHazir, metinParcalari.size(), true);
            }

            int uretilecek = Math.min(limit, eksikParcalar.size());
            System.out.print("Bu çalışmada " + uretilecek + " yeni MP3 üretilecek. Devam edilsin mi? (E/H): ");
            if (!evetMi(konsol.readLine())) {
                return TopluSonuc.atlandi("Kullanıcı son onayda iptal etti");
            }

            int uretilen = 0;
            int hatali = 0;
            String sonHata = "";

            for (int i = 0; i < eksikParcalar.size() && uretilen < limit; i++) {
                Path metinDosyasi = eksikParcalar.get(i);
                int tumListeIndeksi = metinParcalari.indexOf(metinDosyasi);
                String metin = Files.readString(metinDosyasi, StandardCharsets.UTF_8).trim();
                String onceki = baglamMetni(metinParcalari, tumListeIndeksi - 1, false);
                String sonraki = baglamMetni(metinParcalari, tumListeIndeksi + 1, true);
                Path mp3 = parcaSesKlasoru.resolve(uzantisiz(metinDosyasi.getFileName().toString()) + ".mp3");

                System.out.println();
                System.out.println("TTS parçası üretiliyor: " + metinDosyasi.getFileName());
                System.out.println("Karakter sayısı: " + metin.length());
                System.out.println("İlerleme: " + (uretilen + 1) + "/" + uretilecek);

                try {
                    ElevenLabsClient.SesUretimSonucu sonuc = yenidenDeneyerekUret(
                            client, metin, onceki, sonraki, ses, mp3
                    );
                    parcaMetadataYaz(metinDosyasi, sonuc, onceki, sonraki);
                    uretilen++;
                    System.out.println("MP3 hazır: " + sonuc.dosya());
                    System.out.println("Dosya boyutu: " + okunabilirBoyut(sonuc.bayt()));

                    manifestYaz(eserKlasoru, parcaSesKlasoru, metinParcalari, ses, client.getModel());
                    if (uretilen < uretilecek) {
                        Thread.sleep(1_500L);
                    }
                } catch (Exception e) {
                    hatali++;
                    sonHata = e.getMessage();
                    System.err.println("Bu parça üretilemedi: " + e.getMessage());
                    System.err.println("Mevcut MP3'ler korunarak işlem durduruldu.");
                    break;
                }
            }

            manifestYaz(eserKlasoru, parcaSesKlasoru, metinParcalari, ses, client.getModel());
            int toplamHazir = hazirParcaSayisi(metinParcalari, parcaSesKlasoru, ses, client.getModel());
            boolean tumuTamam = toplamHazir == metinParcalari.size();
            durumDosyasiYaz(eserSesKlasoru, metinParcalari.size(), toplamHazir, ses, client.getModel());

            System.out.println();
            System.out.println("Kontrollü toplu TTS işlemi tamamlandı.");
            System.out.println("Bu çalışmada üretilen MP3: " + uretilen);
            System.out.println("Toplam hazır MP3: " + toplamHazir + "/" + metinParcalari.size());
            System.out.println("Ses klasörü: " + parcaSesKlasoru);

            if (hatali > 0) {
                return TopluSonuc.hatali(eserSesKlasoru, uretilen, toplamHazir,
                        metinParcalari.size(), sonHata);
            }
            return TopluSonuc.basarili(eserSesKlasoru, uretilen, toplamHazir,
                    metinParcalari.size(), tumuTamam);

        } catch (Exception e) {
            System.err.println("ElevenLabs toplu TTS işlemi tamamlanamadı: " + e.getMessage());
            System.err.println("Arşivleme ve metin işlemleri devam edecek.");
            return TopluSonuc.hatali(null, 0, 0, 0, e.getMessage());
        }
    }

    private static ElevenLabsClient.SesUretimSonucu yenidenDeneyerekUret(ElevenLabsClient client,
                                                                         String metin,
                                                                         String onceki,
                                                                         String sonraki,
                                                                         ElevenLabsClient.Ses ses,
                                                                         Path mp3) throws Exception {
        int maksimumDeneme = 3;
        for (int deneme = 1; deneme <= maksimumDeneme; deneme++) {
            try {
                return client.sesUret(metin, onceki, sonraki, ses, mp3);
            } catch (ElevenLabsClient.ElevenLabsApiException e) {
                int kod = e.getDurumKodu();
                boolean tekrarEdilebilir = kod == 429 || kod >= 500;
                if (!tekrarEdilebilir || deneme == maksimumDeneme) {
                    throw e;
                }
                long beklemeSaniyesi = (long) Math.pow(2, deneme) * 3L;
                System.out.println("ElevenLabs geçici hata verdi (HTTP " + kod + "). "
                        + beklemeSaniyesi + " saniye sonra tekrar denenecek...");
                Thread.sleep(beklemeSaniyesi * 1_000L);
            }
        }
        throw new IllegalStateException("Ses üretimi beklenmeyen biçimde tamamlanamadı.");
    }

    private static SecilenSes secilenSesiOku(Path dosya) {
        if (!Files.isRegularFile(dosya)) {
            return null;
        }
        try {
            JsonNode json = OBJECT_MAPPER.readTree(Files.readString(dosya, StandardCharsets.UTF_8));
            String voiceId = json.path("voiceId").asText("").trim();
            String voiceName = json.path("voiceName").asText("Seçili ses").trim();
            String model = json.path("model").asText("eleven_multilingual_v2").trim();
            if (voiceId.isBlank()) {
                return null;
            }
            return new SecilenSes(voiceId, voiceName.isBlank() ? "Seçili ses" : voiceName, model);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Path> ttsHazirEserleriListele(Path metinArsivKlasoru) throws Exception {
        Files.createDirectories(metinArsivKlasoru);
        try (Stream<Path> stream = Files.list(metinArsivKlasoru)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isDirectory(path.resolve("tts-parcalari")))
                    .filter(path -> {
                        try {
                            return !metinParcalariniListele(path.resolve("tts-parcalari")).isEmpty();
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    private static Path eserSec(List<Path> eserler, BufferedReader konsol) throws Exception {
        if (eserler.size() == 1) {
            System.out.println("Toplu ses üretimi eseri: " + eserler.getFirst().getFileName());
            return eserler.getFirst();
        }

        System.out.println("\n--- TTS HAZIR ESERLER ---");
        for (int i = 0; i < eserler.size(); i++) {
            System.out.printf(Locale.ROOT, "%2d - %s%n", i + 1, eserler.get(i).getFileName());
        }
        while (true) {
            System.out.print("Ses üretilecek eser numarası (0 = iptal): ");
            String cevap = konsol.readLine();
            try {
                int secim = Integer.parseInt(cevap == null ? "0" : cevap.trim());
                if (secim == 0) {
                    return null;
                }
                if (secim >= 1 && secim <= eserler.size()) {
                    return eserler.get(secim - 1);
                }
            } catch (NumberFormatException ignored) {
            }
            System.out.println("Geçerli bir numara gir.");
        }
    }

    private static List<Path> metinParcalariniListele(Path ttsKlasoru) throws Exception {
        if (!Files.isDirectory(ttsKlasoru)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(ttsKlasoru)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("\\d{3}-\\d{3}\\.txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    private static boolean parcaGecerliMi(Path metinDosyasi,
                                          Path parcaSesKlasoru,
                                          ElevenLabsClient.Ses ses,
                                          String model) {
        try {
            String temelAd = uzantisiz(metinDosyasi.getFileName().toString());
            Path mp3 = parcaSesKlasoru.resolve(temelAd + ".mp3");
            Path jsonDosyasi = parcaSesKlasoru.resolve(temelAd + ".json");
            if (!Files.isRegularFile(mp3)
                    || Files.size(mp3) < EN_AZ_GECERLI_MP3_BOYUTU
                    || !Files.isRegularFile(jsonDosyasi)) {
                Files.deleteIfExists(mp3);
                Files.deleteIfExists(jsonDosyasi);
                return false;
            }

            JsonNode json = OBJECT_MAPPER.readTree(Files.readString(jsonDosyasi, StandardCharsets.UTF_8));
            String beklenenHash = sha256(Files.readString(metinDosyasi, StandardCharsets.UTF_8));
            return beklenenHash.equalsIgnoreCase(json.path("kaynakMetinSha256").asText(""))
                    && ses.id().equals(json.path("voiceId").asText(""))
                    && model.equals(json.path("model").asText(""));
        } catch (Exception e) {
            return false;
        }
    }

    private static void parcaMetadataYaz(Path metinDosyasi,
                                          ElevenLabsClient.SesUretimSonucu sonuc,
                                          String onceki,
                                          String sonraki) throws Exception {
        Path jsonDosyasi = sonuc.dosya().resolveSibling(uzantisiz(sonuc.dosya().getFileName().toString()) + ".json");
        String kaynakMetin = Files.readString(metinDosyasi, StandardCharsets.UTF_8);

        ObjectNode json = OBJECT_MAPPER.createObjectNode();
        json.put("saglayici", "ElevenLabs");
        json.put("model", sonuc.model());
        json.put("voiceId", sonuc.ses().id());
        json.put("voiceName", sonuc.ses().ad());
        json.put("format", sonuc.format());
        json.put("karakterSayisi", sonuc.karakter());
        json.put("dosyaBoyutuBayt", sonuc.bayt());
        json.put("kaynakMetinDosyasi", metinDosyasi.toAbsolutePath().toString());
        json.put("kaynakMetinSha256", sha256(kaynakMetin));
        json.put("sesDosyasi", sonuc.dosya().toAbsolutePath().toString());
        json.put("oncekiBaglamKarakteri", onceki == null ? 0 : onceki.length());
        json.put("sonrakiBaglamKarakteri", sonraki == null ? 0 : sonraki.length());
        json.put("olusturulmaZamani", OffsetDateTime.now().toString());

        Files.writeString(jsonDosyasi,
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json),
                StandardCharsets.UTF_8);
    }

    private static void manifestYaz(Path eserKlasoru,
                                     Path parcaSesKlasoru,
                                     List<Path> metinParcalari,
                                     ElevenLabsClient.Ses ses,
                                     String model) throws Exception {
        Files.createDirectories(parcaSesKlasoru);
        ObjectNode kok = OBJECT_MAPPER.createObjectNode();
        kok.put("saglayici", "ElevenLabs");
        kok.put("model", model);
        kok.put("voiceId", ses.id());
        kok.put("voiceName", ses.ad());
        kok.put("eserKlasoru", eserKlasoru.toAbsolutePath().toString());
        kok.put("guncellenmeZamani", OffsetDateTime.now().toString());
        kok.put("toplamParca", metinParcalari.size());

        ArrayNode parcalar = kok.putArray("parcalar");
        StringBuilder okumaListesi = new StringBuilder();
        int hazir = 0;
        for (int i = 0; i < metinParcalari.size(); i++) {
            Path metinDosyasi = metinParcalari.get(i);
            String temelAd = uzantisiz(metinDosyasi.getFileName().toString());
            Path mp3 = parcaSesKlasoru.resolve(temelAd + ".mp3");
            Path json = parcaSesKlasoru.resolve(temelAd + ".json");
            boolean tamam = parcaGecerliMi(metinDosyasi, parcaSesKlasoru, ses, model);
            if (tamam) {
                hazir++;
            }

            ObjectNode kayit = parcalar.addObject();
            kayit.put("sira", i + 1);
            kayit.put("metinDosyasi", metinDosyasi.getFileName().toString());
            kayit.put("sesDosyasi", mp3.getFileName().toString());
            kayit.put("metadataDosyasi", json.getFileName().toString());
            kayit.put("durum", tamam ? "TAMAMLANDI" : "BEKLIYOR");
            kayit.put("karakterSayisi", Files.readString(metinDosyasi, StandardCharsets.UTF_8).trim().length());
            kayit.put("kaynakMetinSha256", sha256(Files.readString(metinDosyasi, StandardCharsets.UTF_8)));
            if (tamam) {
                kayit.put("sesBoyutuBayt", Files.size(mp3));
            }

            okumaListesi.append(String.format(Locale.ROOT, "%03d | %s | %s%n",
                    i + 1,
                    tamam ? "TAMAMLANDI" : "BEKLIYOR",
                    mp3.getFileName()));
        }
        kok.put("hazirParca", hazir);
        kok.put("eksikParca", metinParcalari.size() - hazir);
        kok.put("tamamlanmaOrani", metinParcalari.isEmpty() ? 0.0 : (hazir * 100.0 / metinParcalari.size()));

        Files.writeString(parcaSesKlasoru.resolve("ses-manifest.json"),
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(kok),
                StandardCharsets.UTF_8);
        Files.writeString(parcaSesKlasoru.resolve("ses-okuma-listesi.txt"),
                okumaListesi.toString(), StandardCharsets.UTF_8);
    }

    private static void durumDosyasiYaz(Path eserSesKlasoru,
                                        int toplam,
                                        int hazir,
                                        ElevenLabsClient.Ses ses,
                                        String model) throws Exception {
        String durum = "saglayici=ElevenLabs" + System.lineSeparator()
                + "model=" + model + System.lineSeparator()
                + "voiceId=" + ses.id() + System.lineSeparator()
                + "voiceName=" + ses.ad() + System.lineSeparator()
                + "toplamParca=" + toplam + System.lineSeparator()
                + "hazirParca=" + hazir + System.lineSeparator()
                + "eksikParca=" + Math.max(0, toplam - hazir) + System.lineSeparator()
                + "guncellenmeZamani=" + OffsetDateTime.now() + System.lineSeparator();
        Files.writeString(eserSesKlasoru.resolve("_ses_uretim_durumu.flag"), durum, StandardCharsets.UTF_8);
    }

    private static int hazirParcaSayisi(List<Path> metinParcalari,
                                        Path parcaSesKlasoru,
                                        ElevenLabsClient.Ses ses,
                                        String model) {
        int sayi = 0;
        for (Path metin : metinParcalari) {
            if (parcaGecerliMi(metin, parcaSesKlasoru, ses, model)) {
                sayi++;
            }
        }
        return sayi;
    }

    private static String baglamMetni(List<Path> tumParcalar, int indeks, boolean bastan) {
        if (indeks < 0 || indeks >= tumParcalar.size()) {
            return null;
        }
        try {
            String metin = Files.readString(tumParcalar.get(indeks), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ")
                    .trim();
            if (metin.length() <= BAGLAM_KARAKTERI) {
                return metin;
            }
            return bastan
                    ? metin.substring(0, BAGLAM_KARAKTERI)
                    : metin.substring(metin.length() - BAGLAM_KARAKTERI);
        } catch (Exception e) {
            return null;
        }
    }

    private static int testLimiti() {
        String deger = System.getenv("ELEVENLABS_TTS_TEST_LIMIT");
        if (deger == null || deger.isBlank()) {
            return VARSAYILAN_TEST_LIMITI;
        }
        try {
            int sayi = Integer.parseInt(deger.trim());
            return Math.max(1, Math.min(sayi, 20));
        } catch (NumberFormatException e) {
            return VARSAYILAN_TEST_LIMITI;
        }
    }

    private static String sha256(String metin) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(metin.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
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

    private static String uzantisiz(String ad) {
        int nokta = ad.lastIndexOf('.');
        return nokta > 0 ? ad.substring(0, nokta) : ad;
    }

    private static boolean evetMi(String cevap) {
        if (cevap == null) {
            return false;
        }
        String temiz = cevap.trim().toUpperCase(Locale.forLanguageTag("tr-TR"));
        return temiz.equals("E") || temiz.equals("EVET") || temiz.equals("Y") || temiz.equals("YES");
    }

    private record SecilenSes(String voiceId, String voiceName, String model) {
    }

    public record TopluSonuc(boolean basarili,
                             boolean atlandi,
                             boolean tumuTamam,
                             String mesaj,
                             Path eserSesKlasoru,
                             int buCalismadaUretilen,
                             int toplamHazir,
                             int toplamParca) {
        public static TopluSonuc basarili(Path klasor,
                                          int uretilen,
                                          int hazir,
                                          int toplam,
                                          boolean tumuTamam) {
            return new TopluSonuc(true, false, tumuTamam, "Başarılı",
                    klasor, uretilen, hazir, toplam);
        }

        public static TopluSonuc atlandi(String mesaj) {
            return new TopluSonuc(false, true, false, mesaj,
                    null, 0, 0, 0);
        }

        public static TopluSonuc hatali(Path klasor,
                                        int uretilen,
                                        int hazir,
                                        int toplam,
                                        String mesaj) {
            return new TopluSonuc(false, false, false, mesaj,
                    klasor, uretilen, hazir, toplam);
        }
    }
}
