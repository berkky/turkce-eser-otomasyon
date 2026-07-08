import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Tek bir kısa TTS örneği üretir. Tam eser üretimine başlamadan önce ses kalitesini
 * ve Türkçe telaffuzu güvenli ve düşük maliyetli biçimde sınamak için kullanılır.
 */
public final class ElevenLabsOrnekService {
    private static final int ORNEK_HEDEF_KARAKTER = 900;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ElevenLabsOrnekService() {
    }

    public static OrnekSonucu interaktifOrnekOlustur(Path metinArsivKlasoru,
                                                      Path sesArsivKlasoru,
                                                      BufferedReader konsol) {
        try {
            String apiKey = System.getenv("ELEVENLABS_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                System.out.println("ElevenLabs TTS: API anahtarı yok; ses örneği adımı atlandı.");
                System.out.println("Hazır olduğunda ELEVENLABS_API_KEY ortam değişkenini tanımlayabilirsin.");
                return OrnekSonucu.atlandi("API anahtarı yok");
            }

            List<Path> eserKlasorleri = ttsHazirEserleriListele(metinArsivKlasoru);
            if (eserKlasorleri.isEmpty()) {
                System.out.println("ElevenLabs TTS: seslendirilecek TTS parçası bulunamadı.");
                return OrnekSonucu.atlandi("TTS parçası yok");
            }

            System.out.print("ElevenLabs ile kısa bir Türkçe ses örneği üretmek ister misin? (E/H): ");
            if (!evetMi(konsol.readLine())) {
                return OrnekSonucu.atlandi("Kullanıcı atladı");
            }

            Path eserKlasoru = eserSec(eserKlasorleri, konsol);
            if (eserKlasoru == null) {
                return OrnekSonucu.atlandi("Eser seçilmedi");
            }

            Path ttsKlasoru = eserKlasoru.resolve("tts-parcalari");
            Path ilkParca = ilkMetinParcasi(ttsKlasoru);
            if (ilkParca == null) {
                throw new IllegalStateException("Seçilen eserde .txt TTS parçası bulunamadı.");
            }

            String tamMetin = Files.readString(ilkParca, StandardCharsets.UTF_8).trim();
            String ornekMetin = ornekMetinHazirla(tamMetin, ORNEK_HEDEF_KARAKTER);
            if (ornekMetin.isBlank()) {
                throw new IllegalStateException("Örnek için kullanılabilir metin bulunamadı.");
            }

            ElevenLabsClient client = new ElevenLabsClient(apiKey);
            System.out.println("ElevenLabs modeli: " + client.getModel());
            System.out.println("Kullanılabilir sesler alınıyor...");
            List<ElevenLabsClient.Ses> sesler = client.sesleriListele();
            if (sesler.isEmpty()) {
                throw new IllegalStateException("ElevenLabs hesabında kullanılabilir ses bulunamadı.");
            }

            ElevenLabsClient.Ses ses = sesSec(sesler, konsol);
            if (ses == null) {
                return OrnekSonucu.atlandi("Ses seçilmedi");
            }

            String eserKlasorAdi = eserKlasoru.getFileName().toString();
            Path ornekKlasoru = sesArsivKlasoru.resolve(eserKlasorAdi).resolve("ornekler");
            Files.createDirectories(ornekKlasoru);

            String temelAd = uzantisiz(ilkParca.getFileName().toString())
                    + " - " + dosyaAdiTemizle(ses.ad()) + " - elevenlabs";
            Path mp3 = benzersizDosya(ornekKlasoru.resolve(temelAd + ".mp3"));

            System.out.println("Örnek metin uzunluğu: " + ornekMetin.length() + " karakter");
            System.out.println("Seçilen ses: " + ses.kisaBilgi());
            System.out.println("Ses örneği üretiliyor...");

            ElevenLabsClient.SesUretimSonucu sonuc = client.sesUret(ornekMetin, ses, mp3);
            Path bilgiDosyasi = mp3.resolveSibling(uzantisiz(mp3.getFileName().toString()) + ".json");
            metadataYaz(bilgiDosyasi, sonuc, eserKlasoru, ilkParca, ornekMetin);
            secilenSesiKaydet(sesArsivKlasoru.resolve("elevenlabs-secilen-ses.json"), sonuc);

            System.out.println("ElevenLabs ses örneği başarıyla oluşturuldu.");
            System.out.println("MP3: " + sonuc.dosya());
            System.out.println("Bilgi dosyası: " + bilgiDosyasi);
            return OrnekSonucu.basarili(sonuc.dosya(), ses.ad(), sonuc.karakter());

        } catch (Exception e) {
            System.err.println("ElevenLabs ses örneği oluşturulamadı: " + e.getMessage());
            System.err.println("Arşivleme ve metin işlemleri devam edecek.");
            return OrnekSonucu.hatali(e.getMessage());
        }
    }

    private static List<Path> ttsHazirEserleriListele(Path metinArsivKlasoru) throws Exception {
        Files.createDirectories(metinArsivKlasoru);
        try (Stream<Path> stream = Files.list(metinArsivKlasoru)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isDirectory(path.resolve("tts-parcalari")))
                    .filter(path -> ilkMetinParcasi(path.resolve("tts-parcalari")) != null)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    private static Path eserSec(List<Path> eserler, BufferedReader konsol) throws Exception {
        if (eserler.size() == 1) {
            System.out.println("Ses örneği eseri: " + eserler.getFirst().getFileName());
            return eserler.getFirst();
        }

        System.out.println("\n--- TTS HAZIR ESERLER ---");
        for (int i = 0; i < eserler.size(); i++) {
            System.out.printf(Locale.ROOT, "%2d - %s%n", i + 1, eserler.get(i).getFileName());
        }
        while (true) {
            System.out.print("Ses örneği üretilecek eser numarası (0 = iptal): ");
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

    private static ElevenLabsClient.Ses sesSec(List<ElevenLabsClient.Ses> sesler,
                                                BufferedReader konsol) throws Exception {
        System.out.println("\n--- ELEVENLABS SESLERİ ---");
        int limit = Math.min(30, sesler.size());
        for (int i = 0; i < limit; i++) {
            ElevenLabsClient.Ses ses = sesler.get(i);
            System.out.printf(Locale.ROOT, "%2d - %s%n", i + 1, ses.kisaBilgi());
        }
        if (sesler.size() > limit) {
            System.out.println("Not: İlk " + limit + " ses gösterildi.");
        }

        while (true) {
            System.out.print("Kullanılacak ses numarası (0 = iptal): ");
            String cevap = konsol.readLine();
            try {
                int secim = Integer.parseInt(cevap == null ? "0" : cevap.trim());
                if (secim == 0) {
                    return null;
                }
                if (secim >= 1 && secim <= limit) {
                    return sesler.get(secim - 1);
                }
            } catch (NumberFormatException ignored) {
            }
            System.out.println("Geçerli bir numara gir.");
        }
    }

    private static Path ilkMetinParcasi(Path ttsKlasoru) {
        if (!Files.isDirectory(ttsKlasoru)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(ttsKlasoru)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("\\d{3}-\\d{3}\\.txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String ornekMetinHazirla(String metin, int hedef) {
        String temiz = metin == null ? "" : metin.replaceAll("\\s+", " ").trim();
        if (temiz.length() <= hedef) {
            return temiz;
        }

        int altSinir = Math.max(300, hedef - 250);
        int ustSinir = Math.min(temiz.length(), hedef + 150);
        for (int i = hedef; i < ustSinir; i++) {
            char c = temiz.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                return temiz.substring(0, i + 1).trim();
            }
        }
        for (int i = hedef; i >= altSinir; i--) {
            char c = temiz.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                return temiz.substring(0, i + 1).trim();
            }
        }
        int bosluk = temiz.lastIndexOf(' ', hedef);
        return temiz.substring(0, bosluk > 0 ? bosluk : hedef).trim();
    }

    private static void metadataYaz(Path dosya,
                                    ElevenLabsClient.SesUretimSonucu sonuc,
                                    Path eserKlasoru,
                                    Path metinDosyasi,
                                    String metin) throws Exception {
        ObjectNode json = OBJECT_MAPPER.createObjectNode();
        json.put("saglayici", "ElevenLabs");
        json.put("model", sonuc.model());
        json.put("voiceId", sonuc.ses().id());
        json.put("voiceName", sonuc.ses().ad());
        json.put("format", sonuc.format());
        json.put("karakterSayisi", sonuc.karakter());
        json.put("dosyaBoyutuBayt", sonuc.bayt());
        json.put("eserKlasoru", eserKlasoru.toAbsolutePath().toString());
        json.put("kaynakMetinDosyasi", metinDosyasi.toAbsolutePath().toString());
        json.put("sesDosyasi", sonuc.dosya().toAbsolutePath().toString());
        json.put("olusturulmaZamani", OffsetDateTime.now().toString());
        json.put("ornekMetin", metin);
        Files.writeString(dosya, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json), StandardCharsets.UTF_8);
    }

    private static void secilenSesiKaydet(Path dosya,
                                          ElevenLabsClient.SesUretimSonucu sonuc) throws Exception {
        Files.createDirectories(dosya.getParent());
        ObjectNode json = OBJECT_MAPPER.createObjectNode();
        json.put("saglayici", "ElevenLabs");
        json.put("voiceId", sonuc.ses().id());
        json.put("voiceName", sonuc.ses().ad());
        json.put("model", sonuc.model());
        json.put("guncellenmeZamani", OffsetDateTime.now().toString());
        Files.writeString(dosya, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json), StandardCharsets.UTF_8);
    }

    private static Path benzersizDosya(Path istenen) {
        if (!Files.exists(istenen)) {
            return istenen;
        }
        String ad = uzantisiz(istenen.getFileName().toString());
        String uzanti = ".mp3";
        for (int i = 2; i < 10_000; i++) {
            Path aday = istenen.resolveSibling(ad + "-" + i + uzanti);
            if (!Files.exists(aday)) {
                return aday;
            }
        }
        throw new IllegalStateException("Benzersiz ses dosyası adı üretilemedi.");
    }

    private static String dosyaAdiTemizle(String deger) {
        String temiz = deger == null ? "ses" : deger
                .replaceAll("[\\\\/:*?\"<>|]", "-")
                .replaceAll("\\s+", " ")
                .trim();
        return temiz.isBlank() ? "ses" : temiz;
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

    public record OrnekSonucu(boolean basarili,
                              boolean atlandi,
                              String mesaj,
                              Path mp3,
                              String sesAdi,
                              int karakter) {
        public static OrnekSonucu basarili(Path mp3, String sesAdi, int karakter) {
            return new OrnekSonucu(true, false, "Başarılı", mp3, sesAdi, karakter);
        }

        public static OrnekSonucu atlandi(String mesaj) {
            return new OrnekSonucu(false, true, mesaj, null, "", 0);
        }

        public static OrnekSonucu hatali(String mesaj) {
            return new OrnekSonucu(false, false, mesaj, null, "", 0);
        }
    }
}
