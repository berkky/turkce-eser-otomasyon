import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
 * TTS paketlerini ElevenLabs ile kontrollü biçimde seslendirir.
 * İlk 10 parça, tek bölüm veya bütün eksik parçalar seçilebilir.
 * Hazır ve geçerli MP3 dosyaları atlanır; yarım kalan işlem sonraki çalıştırmada devam eder.
 */
public final class ElevenLabsTamEserService {
    private static final int ILK_GRUP_LIMITI = 10;
    private static final int BAGLAM_KARAKTERI = 700;
    private static final int EN_AZ_GECERLI_MP3_BOYUTU = 1_024;
    private static final long PARCALAR_ARASI_BEKLEME_MS = 1_500L;
    private static final Pattern PARCA_ADI = Pattern.compile("^(\\d{3})-(\\d{3})\\.txt$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TARIH_SAAT_BICIMI = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm")
            .withLocale(Locale.forLanguageTag("tr-TR"))
            .withZone(ZoneId.systemDefault());

    private ElevenLabsTamEserService() {
    }

    public static Sonuc interaktifUret(Path metinArsivKlasoru,
                                       Path sesArsivKlasoru,
                                       BufferedReader konsol) {
        try {
            String apiKey = System.getenv("ELEVENLABS_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                System.out.println("ElevenLabs tam eser TTS: API anahtarı yok; adım atlandı.");
                return Sonuc.atlandi("API anahtarı yok");
            }

            SecilenSes secilenSes = secilenSesiOku(sesArsivKlasoru.resolve("elevenlabs-secilen-ses.json"));
            if (secilenSes == null) {
                System.out.println("ElevenLabs tam eser TTS: seçilmiş ses kaydı bulunamadı.");
                System.out.println("Önce kısa ses örneği oluşturarak bir ses seçmelisin.");
                return Sonuc.atlandi("Seçili ses yok");
            }

            List<Path> eserKlasorleri = ttsHazirEserleriListele(metinArsivKlasoru);
            if (eserKlasorleri.isEmpty()) {
                System.out.println("ElevenLabs tam eser TTS: seslendirilecek TTS paketi bulunamadı.");
                return Sonuc.atlandi("TTS paketi yok");
            }

            System.out.print("ElevenLabs toplu seslendirme menüsünü açmak ister misin? (E/H): ");
            if (!evetMi(konsol.readLine())) {
                return Sonuc.atlandi("Kullanıcı atladı");
            }

            Path eserKlasoru = eserSec(eserKlasorleri, konsol);
            if (eserKlasoru == null) {
                return Sonuc.atlandi("Eser seçilmedi");
            }

            int eserId = eserIdBul(eserKlasoru.getFileName().toString());
            Path ttsKlasoru = eserKlasoru.resolve("tts-parcalari");
            List<Path> tumMetinParcalari = metinParcalariniListele(ttsKlasoru);
            if (buyukEserOtomatikUretimEngelli(eserId, tumMetinParcalari.size())) {
                System.out.println();
                System.out.println("Uyarı: Bu eser büyük kapsamlı bir üretimdir.");
                System.out.println("ESER-" + String.format(Locale.ROOT, "%05d", eserId)
                        + " için otomatik tam üretim kapalıdır.");
                System.out.println("Yalnızca önizleme veya küçük planlarla devam edebilirsin.");
                System.out.println("Önizleme: powershell -ExecutionPolicy Bypass -File .\\elevenlabs-onizleme.ps1 -EserId "
                        + eserId);
            }
            if (tumMetinParcalari.isEmpty()) {
                throw new IllegalStateException("Seçilen eserde TTS metin parçası bulunamadı.");
            }

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
            Files.createDirectories(eserSesKlasoru);

            ModelSecenegi secilenModel = modelSec(
                    tumMetinParcalari,
                    eserSesKlasoru,
                    ses,
                    secilenSes.model(),
                    konsol
            );
            if (secilenModel == null) {
                return Sonuc.atlandi("Ses modeli seçilmedi");
            }

            ElevenLabsClient client = new ElevenLabsClient(apiKey, secilenModel.modelId());
            Path parcaSesKlasoru = modelParcaKlasoru(eserSesKlasoru, secilenModel);
            Files.createDirectories(parcaSesKlasoru);

            List<Path> eksikParcalar = new ArrayList<>();
            int hazirParca = 0;
            for (Path metinDosyasi : tumMetinParcalari) {
                if (parcaGecerliMi(metinDosyasi, parcaSesKlasoru, ses, client.getModel())) {
                    hazirParca++;
                } else {
                    eksikParcalar.add(metinDosyasi);
                }
            }

            Istatistik tumIstatistik = istatistik(tumMetinParcalari);
            Istatistik eksikIstatistik = istatistik(eksikParcalar);
            System.out.println();
            System.out.println("--- SESLENDİRME DURUMU ---");
            System.out.println("Seçilen eser       : " + eserKlasoru.getFileName());
            System.out.println("Seçilen ses        : " + ses.ad());
            System.out.println("Model              : " + secilenModel.ad() + " (" + client.getModel() + ")");
            System.out.println("Model karakter bedeli: " + carpanBicimle(secilenModel.krediCarpani())
                    + " kredi/karakter");
            System.out.println("Ses parça klasörü  : " + parcaSesKlasoru.getFileName());
            System.out.println("Toplam parça       : " + tumMetinParcalari.size());
            System.out.println("Hazır MP3          : " + hazirParca);
            System.out.println("Eksik MP3          : " + eksikParcalar.size());
            System.out.println("Toplam karakter    : " + sayiBicimle(tumIstatistik.karakter()));
            System.out.println("Eksik karakter     : " + sayiBicimle(eksikIstatistik.karakter()));
            System.out.println("Eksik tahmini kredi: "
                    + sayiBicimle(tahminiKredi(eksikIstatistik.karakter(), secilenModel.krediCarpani())));
            System.out.println("Eksik tahmini süre : " + sureBicimle(eksikIstatistik.tahminiSaniye()));

            if (eksikParcalar.isEmpty()) {
                manifestVeListeleriYaz(eserKlasoru, eserSesKlasoru, parcaSesKlasoru,
                        tumMetinParcalari, ses, client.getModel());
                System.out.println("Bu eser ve seçilen model için bütün TTS parçaları zaten hazır.");
                return Sonuc.basarili(eserSesKlasoru, 0, hazirParca, tumMetinParcalari.size(), true);
            }

            boolean ucretliAsimAcik = ortamBoolean("ELEVENLABS_ALLOW_OVERAGE", false);
            ElevenLabsClient.AbonelikBilgisi abonelik;
            try {
                abonelik = client.abonelikBilgisiniGetir();
            } catch (Exception e) {
                System.err.println();
                System.err.println("ElevenLabs kredi durumu okunamadı: " + e.getMessage());
                System.err.println("Güvenlik nedeniyle ses üretimi başlatılmadı.");
                System.err.println("API anahtarında user_read izninin açık olduğundan emin ol.");
                krediKontrolHatasiYaz(eserSesKlasoru, e.getMessage());
                return Sonuc.atlandi("Kredi durumu okunamadı");
            }

            ElevenLabsClient.ModelKrediBilgisi modelKredi = modelKrediBilgisiniGuvenliGetir(client);
            krediDurumunuYaz(eserSesKlasoru, abonelik, modelKredi, ucretliAsimAcik, null, null);
            krediDurumunuEkranaYaz(abonelik, modelKredi, ucretliAsimAcik);

            if (!abonelik.aktifMi()) {
                System.out.println("ElevenLabs aboneliği aktif görünmüyor; üretim başlatılmadı.");
                krediYetersizFlagYaz(eserSesKlasoru, abonelik, modelKredi, 0L,
                        "Abonelik aktif değil");
                return Sonuc.atlandi("Abonelik aktif değil");
            }

            long kullanilabilirKredi = abonelik.kullanilabilirKredi(ucretliAsimAcik);
            if (kullanilabilirKredi <= 0L) {
                System.out.println();
                System.out.println("ElevenLabs kalan kredisi 0. API'ye ses üretim isteği gönderilmedi.");
                System.out.println("Kredi yenilendiğinde veya plan yükseltildiğinde kaldığın yerden devam edebilirsin.");
                krediYetersizFlagYaz(eserSesKlasoru, abonelik, modelKredi, 0L,
                        "Kalan kredi yok");
                return Sonuc.atlandi("Kalan kredi yok");
            }

            UretimPlani plan = planSec(eksikParcalar, ttsKlasoru, konsol);
            if (plan == null || plan.parcalar().isEmpty()) {
                return Sonuc.atlandi("Üretim planı seçilmedi");
            }

            Istatistik planIstatistik = istatistik(plan.parcalar());
            long planKrediIhtiyaci = modelKredi.tahminiKredi(planIstatistik.karakter());
            long planSonrasiKalan = kullanilabilirKredi == Long.MAX_VALUE
                    ? Long.MAX_VALUE
                    : Math.max(0L, kullanilabilirKredi - planKrediIhtiyaci);

            System.out.println();
            System.out.println("--- ÜRETİM PLANI ---");
            System.out.println("Plan               : " + plan.aciklama());
            System.out.println("Üretilecek MP3     : " + plan.parcalar().size());
            System.out.println("Karakter sayısı    : " + sayiBicimle(planIstatistik.karakter()));
            System.out.println("Kredi çarpanı      : " + carpanBicimle(modelKredi.karakterKrediCarpani())
                    + (modelKredi.apiTarafindanDogrulandi() ? " (API)" : " (güvenli varsayım)"));
            System.out.println("Tahmini kredi      : " + sayiBicimle(planKrediIhtiyaci));
            System.out.println("Kullanılabilir     : " + krediBicimle(kullanilabilirKredi));
            System.out.println("Üretim sonrası     : " + krediBicimle(planSonrasiKalan));
            System.out.println("Kelime sayısı      : " + sayiBicimle(planIstatistik.kelime()));
            System.out.println("Tahmini ses süresi : " + sureBicimle(planIstatistik.tahminiSaniye()));
            System.out.println("Not: Ücretli aşım varsayılan olarak kapalıdır.");
            System.out.println("İstediğin anda Ctrl+C ile durdurabilirsin; hazır MP3'ler korunur.");

            krediDurumunuYaz(eserSesKlasoru, abonelik, modelKredi, ucretliAsimAcik,
                    planKrediIhtiyaci, planSonrasiKalan);

            if (kullanilabilirKredi != Long.MAX_VALUE && planKrediIhtiyaci > kullanilabilirKredi) {
                System.out.println();
                System.out.println("Kredi yetersiz olduğu için üretim başlatılmadı.");
                System.out.println("Eksik kredi: " + sayiBicimle(planKrediIhtiyaci - kullanilabilirKredi));
                System.out.println("Daha küçük bir plan seçebilir veya ElevenLabs kredisi ekleyebilirsin.");
                krediYetersizFlagYaz(eserSesKlasoru, abonelik, modelKredi,
                        planKrediIhtiyaci, "Seçilen plan için kredi yetersiz");
                return Sonuc.atlandi("Kredi yetersiz");
            }

            Files.deleteIfExists(eserSesKlasoru.resolve("_kredi_yetersiz.flag"));
            System.out.print("Bu üretim planı başlatılsın mı? Onay için EVET yazın: ");
            if (!acikOnayAlindi(konsol.readLine())) {
                return Sonuc.atlandi("Kullanıcı son onayda iptal etti");
            }

            long tahminiKalanKredi = kullanilabilirKredi;
            int uretilen = 0;
            int hatali = 0;
            String sonHata = "";
            Map<Path, Integer> indeksler = indeksHaritasi(tumMetinParcalari);

            for (Path metinDosyasi : plan.parcalar()) {
                Integer tumListeIndeksi = indeksler.get(metinDosyasi);
                if (tumListeIndeksi == null) {
                    continue;
                }

                // Başka bir işlem veya önceki deneme dosyayı tamamlamış olabilir.
                if (parcaGecerliMi(metinDosyasi, parcaSesKlasoru, ses, client.getModel())) {
                    continue;
                }

                String metin = Files.readString(metinDosyasi, StandardCharsets.UTF_8).trim();
                String onceki = baglamMetni(tumMetinParcalari, tumListeIndeksi - 1, false);
                String sonraki = baglamMetni(tumMetinParcalari, tumListeIndeksi + 1, true);
                Path mp3 = parcaSesKlasoru.resolve(uzantisiz(metinDosyasi.getFileName().toString()) + ".mp3");
                long parcaKrediIhtiyaci = modelKredi.tahminiKredi(metin.length());
                if (tahminiKalanKredi != Long.MAX_VALUE && parcaKrediIhtiyaci > tahminiKalanKredi) {
                    sonHata = "Kalan tahmini kredi bu parça için yeterli değil.";
                    System.err.println();
                    System.err.println(sonHata);
                    System.err.println("Gerekli kredi: " + sayiBicimle(parcaKrediIhtiyaci));
                    System.err.println("Tahmini kalan: " + sayiBicimle(tahminiKalanKredi));
                    krediYetersizFlagYaz(eserSesKlasoru, abonelik, modelKredi,
                            parcaKrediIhtiyaci, sonHata);
                    break;
                }

                System.out.println();
                System.out.println("TTS parçası üretiliyor: " + metinDosyasi.getFileName());
                System.out.println("Karakter sayısı: " + sayiBicimle(metin.length()));
                System.out.println("İlerleme: " + (uretilen + 1) + "/" + plan.parcalar().size());

                try {
                    ElevenLabsClient.SesUretimSonucu uretimSonucu = yenidenDeneyerekUret(
                            client, metin, onceki, sonraki, ses, mp3
                    );
                    parcaMetadataYaz(metinDosyasi, uretimSonucu, onceki, sonraki);
                    uretilen++;
                    if (tahminiKalanKredi != Long.MAX_VALUE) {
                        tahminiKalanKredi = Math.max(0L, tahminiKalanKredi - parcaKrediIhtiyaci);
                    }
                    System.out.println("MP3 hazır: " + uretimSonucu.dosya());
                    System.out.println("Dosya boyutu: " + okunabilirBoyut(uretimSonucu.bayt()));
                    System.out.println("Tahmini kalan kredi: " + krediBicimle(tahminiKalanKredi));

                    manifestVeListeleriYaz(eserKlasoru, eserSesKlasoru, parcaSesKlasoru,
                            tumMetinParcalari, ses, client.getModel());
                    if (uretilen < plan.parcalar().size()) {
                        Thread.sleep(PARCALAR_ARASI_BEKLEME_MS);
                    }
                } catch (Exception e) {
                    hatali++;
                    sonHata = e.getMessage();
                    System.err.println("Bu parça üretilemedi: " + e.getMessage());
                    System.err.println("Mevcut MP3'ler korunarak üretim durduruldu.");
                    break;
                }
            }

            manifestVeListeleriYaz(eserKlasoru, eserSesKlasoru, parcaSesKlasoru,
                    tumMetinParcalari, ses, client.getModel());
            try {
                ElevenLabsClient.AbonelikBilgisi guncelAbonelik = client.abonelikBilgisiniGetir();
                krediDurumunuYaz(eserSesKlasoru, guncelAbonelik, modelKredi,
                        ucretliAsimAcik, null, null);
            } catch (Exception e) {
                System.err.println("Güncel kredi bilgisi tekrar okunamadı: " + e.getMessage());
            }
            int toplamHazir = hazirParcaSayisi(tumMetinParcalari, parcaSesKlasoru, ses, client.getModel());
            boolean tumuTamam = toplamHazir == tumMetinParcalari.size();

            System.out.println();
            System.out.println("Toplu TTS işlemi tamamlandı.");
            System.out.println("Bu çalışmada üretilen MP3: " + uretilen);
            System.out.println("Toplam hazır MP3: " + toplamHazir + "/" + tumMetinParcalari.size());
            System.out.println("Tamamlanma oranı: "
                    + String.format(Locale.ROOT, "%.1f%%", toplamHazir * 100.0 / tumMetinParcalari.size()));
            System.out.println("Ses klasörü: " + eserSesKlasoru);

            if (hatali > 0) {
                return Sonuc.hatali(eserSesKlasoru, uretilen, toplamHazir,
                        tumMetinParcalari.size(), sonHata);
            }
            return Sonuc.basarili(eserSesKlasoru, uretilen, toplamHazir,
                    tumMetinParcalari.size(), tumuTamam);
        } catch (Exception e) {
            System.err.println("ElevenLabs tam eser TTS işlemi tamamlanamadı: " + e.getMessage());
            System.err.println("Arşivleme ve metin işlemleri devam edecek.");
            return Sonuc.hatali(null, 0, 0, 0, e.getMessage());
        }
    }

    private static ModelSecenegi modelSec(List<Path> tumMetinParcalari,
                                            Path eserSesKlasoru,
                                            ElevenLabsClient.Ses ses,
                                            String varsayilanModel,
                                            BufferedReader konsol) throws Exception {
        List<ModelSecenegi> modeller = desteklenenModeller();
        Istatistik toplam = istatistik(tumMetinParcalari);

        System.out.println();
        System.out.println("--- SES MODELİ VE MALİYET KARŞILAŞTIRMASI ---");
        for (int i = 0; i < modeller.size(); i++) {
            ModelSecenegi model = modeller.get(i);
            Path klasor = modelParcaKlasoru(eserSesKlasoru, model);
            int hazir = hazirParcaSayisi(tumMetinParcalari, klasor, ses, model.modelId());
            List<Path> eksik = new ArrayList<>();
            for (Path parca : tumMetinParcalari) {
                if (!parcaGecerliMi(parca, klasor, ses, model.modelId())) {
                    eksik.add(parca);
                }
            }
            Istatistik eksikIstatistik = istatistik(eksik);
            long tahmin = tahminiKredi(eksikIstatistik.karakter(), model.krediCarpani());
            String varsayilan = model.modelId().equals(varsayilanModel) ? " | varsayılan" : "";
            System.out.printf(Locale.ROOT,
                    "%d - %s | %.1f kredi/karakter | hazır %d/%d | eksik tahmini %s kredi%s%n",
                    i + 1,
                    model.ad(),
                    model.krediCarpani(),
                    hazir,
                    tumMetinParcalari.size(),
                    sayiBicimle(tahmin),
                    varsayilan);
        }
        System.out.println("0 - İptal");
        System.out.println();
        System.out.println("Not: Multilingual v2 uzun anlatımda kalite/tutarlılık odaklıdır.");
        System.out.println("Flash v2.5 ve Turbo v2.5 yaklaşık yarı kredi tüketir.");
        System.out.println("Farklı modeller ayrı MP3 klasörlerinde tutulur; mevcut sesler ezilmez.");

        while (true) {
            System.out.print("Kullanılacak model numarası: ");
            int secim = sayiyaCevir(konsol.readLine(), -1);
            if (secim == 0) {
                return null;
            }
            if (secim >= 1 && secim <= modeller.size()) {
                return modeller.get(secim - 1);
            }
            System.out.println("Geçerli bir model numarası gir.");
        }
    }

    private static List<ModelSecenegi> desteklenenModeller() {
        return List.of(
                new ModelSecenegi(
                        "eleven_multilingual_v2",
                        "Multilingual v2",
                        1.0d,
                        "parcalar",
                        "En doğal ve uzun form için en tutarlı seçenek"
                ),
                new ModelSecenegi(
                        "eleven_flash_v2_5",
                        "Flash v2.5",
                        0.5d,
                        "parcalar-flash-v2-5",
                        "Daha hızlı ve yaklaşık yüzde 50 daha ekonomik"
                ),
                new ModelSecenegi(
                        "eleven_turbo_v2_5",
                        "Turbo v2.5",
                        0.5d,
                        "parcalar-turbo-v2-5",
                        "Hız ve kalite dengesi, yaklaşık yüzde 50 daha ekonomik"
                )
        );
    }

    private static Path modelParcaKlasoru(Path eserSesKlasoru, ModelSecenegi model) {
        return eserSesKlasoru.resolve(model.klasorAdi());
    }

    private static long tahminiKredi(long karakter, double carpan) {
        if (karakter <= 0L) {
            return 0L;
        }
        return Math.max(1L, (long) Math.ceil(karakter * carpan));
    }

    private static ElevenLabsClient.ModelKrediBilgisi modelKrediBilgisiniGuvenliGetir(
            ElevenLabsClient client) {
        try {
            return client.modelKrediBilgisiniGetir();
        } catch (Exception e) {
            System.out.println("Model kredi çarpanı API'den okunamadı; güvenli varsayım 1.0 kullanılacak.");
            System.out.println("Bilgi: " + e.getMessage());
            return new ElevenLabsClient.ModelKrediBilgisi(
                    client.getModel(),
                    client.getModel(),
                    ElevenLabsClient.bilinenModelKrediCarpani(client.getModel()),
                    0,
                    false
            );
        }
    }

    private static void krediDurumunuEkranaYaz(ElevenLabsClient.AbonelikBilgisi abonelik,
                                                ElevenLabsClient.ModelKrediBilgisi model,
                                                boolean ucretliAsimAcik) {
        long kullanilabilir = abonelik.kullanilabilirKredi(ucretliAsimAcik);
        System.out.println();
        System.out.println("--- ELEVENLABS KREDİ DURUMU ---");
        System.out.println("Plan               : " + abonelik.plan());
        System.out.println("Abonelik durumu    : " + abonelik.durum());
        System.out.println("Kullanılan kredi   : " + sayiBicimle(abonelik.kullanilanKredi()));
        System.out.println("Dönem kredi limiti : " + sayiBicimle(abonelik.donemKrediLimiti()));
        System.out.println("Kalan plan kredisi : " + sayiBicimle(abonelik.kalanPlanKredisi()));
        System.out.println("Ücretli aşım       : " + (ucretliAsimAcik ? "AÇIK" : "KAPALI"));
        System.out.println("Kullanılabilir     : " + krediBicimle(kullanilabilir));
        System.out.println("Model kredi çarpanı: " + carpanBicimle(model.karakterKrediCarpani())
                + (model.apiTarafindanDogrulandi() ? " (API)" : " (güvenli varsayım)"));
        if (abonelik.sonrakiSifirlamaUnix() > 0L) {
            System.out.println("Sonraki yenilenme  : "
                    + TARIH_SAAT_BICIMI.format(Instant.ofEpochSecond(abonelik.sonrakiSifirlamaUnix())));
        }
        if (abonelik.acikFaturaVar()) {
            System.out.println("Uyarı              : Hesapta açık fatura görünüyor.");
        }
    }

    private static void krediDurumunuYaz(Path eserSesKlasoru,
                                          ElevenLabsClient.AbonelikBilgisi abonelik,
                                          ElevenLabsClient.ModelKrediBilgisi model,
                                          boolean ucretliAsimAcik,
                                          Long planlananKredi,
                                          Long planSonrasiKalan) throws Exception {
        Files.createDirectories(eserSesKlasoru);
        ObjectNode json = OBJECT_MAPPER.createObjectNode();
        json.put("saglayici", "ElevenLabs");
        json.put("plan", abonelik.plan());
        json.put("abonelikDurumu", abonelik.durum());
        json.put("kullanilanKredi", abonelik.kullanilanKredi());
        json.put("donemKrediLimiti", abonelik.donemKrediLimiti());
        json.put("kalanPlanKredisi", abonelik.kalanPlanKredisi());
        json.put("ucretliAsimAcik", ucretliAsimAcik);
        json.put("ucretliAsimKullanilabilir", abonelik.ucretliAsimKullanilabilir());
        json.put("sinirsizUcretliAsim", abonelik.sinirsizUcretliAsim());
        json.put("kullanilabilirKredi", abonelik.kullanilabilirKredi(ucretliAsimAcik));
        json.put("model", model.modelId());
        json.put("modelAdi", model.modelAdi());
        json.put("modelKrediCarpani", model.karakterKrediCarpani());
        json.put("modelBilgisiApiDogrulandi", model.apiTarafindanDogrulandi());
        if (planlananKredi != null) {
            json.put("planlananKredi", planlananKredi);
        }
        if (planSonrasiKalan != null) {
            json.put("planSonrasiTahminiKalan", planSonrasiKalan);
        }
        if (abonelik.sonrakiSifirlamaUnix() > 0L) {
            json.put("sonrakiSifirlamaUnix", abonelik.sonrakiSifirlamaUnix());
            json.put("sonrakiSifirlamaYerel",
                    TARIH_SAAT_BICIMI.format(Instant.ofEpochSecond(abonelik.sonrakiSifirlamaUnix())));
        }
        json.put("guncellenmeZamani", OffsetDateTime.now().toString());
        Files.writeString(eserSesKlasoru.resolve("_kredi_durumu.json"),
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json),
                StandardCharsets.UTF_8);
        Files.deleteIfExists(eserSesKlasoru.resolve("_kredi_kontrol_hatasi.flag"));
    }

    private static void krediYetersizFlagYaz(Path eserSesKlasoru,
                                              ElevenLabsClient.AbonelikBilgisi abonelik,
                                              ElevenLabsClient.ModelKrediBilgisi model,
                                              long gerekliKredi,
                                              String neden) throws Exception {
        Files.createDirectories(eserSesKlasoru);
        String icerik = "neden=" + neden + System.lineSeparator()
                + "plan=" + abonelik.plan() + System.lineSeparator()
                + "model=" + model.modelId() + System.lineSeparator()
                + "kalanPlanKredisi=" + abonelik.kalanPlanKredisi() + System.lineSeparator()
                + "gerekliKredi=" + gerekliKredi + System.lineSeparator()
                + "zaman=" + OffsetDateTime.now() + System.lineSeparator();
        Files.writeString(eserSesKlasoru.resolve("_kredi_yetersiz.flag"),
                icerik, StandardCharsets.UTF_8);
    }

    private static void krediKontrolHatasiYaz(Path eserSesKlasoru, String mesaj) {
        try {
            Files.createDirectories(eserSesKlasoru);
            Files.writeString(eserSesKlasoru.resolve("_kredi_kontrol_hatasi.flag"),
                    "mesaj=" + (mesaj == null ? "Bilinmeyen hata" : mesaj) + System.lineSeparator()
                            + "zaman=" + OffsetDateTime.now() + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static boolean ortamBoolean(String ad, boolean varsayilan) {
        String deger = System.getenv(ad);
        if (deger == null || deger.isBlank()) {
            return varsayilan;
        }
        String temiz = deger.trim().toLowerCase(Locale.ROOT);
        return temiz.equals("true") || temiz.equals("1") || temiz.equals("yes")
                || temiz.equals("evet") || temiz.equals("e");
    }

    private static String modelDosyaEtiketi(String model) {
        if (model == null || model.isBlank()) {
            return "bilinmeyen-model";
        }
        return model.toLowerCase(Locale.ROOT)
                .replace("eleven_", "")
                .replace('_', '-')
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private static String krediBicimle(long kredi) {
        return kredi == Long.MAX_VALUE ? "Sınırsız ücretli aşım" : sayiBicimle(kredi);
    }

    private static String carpanBicimle(double carpan) {
        return String.format(Locale.ROOT, "%.3f", carpan)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static UretimPlani planSec(List<Path> eksikParcalar,
                                       Path ttsKlasoru,
                                       BufferedReader konsol) throws Exception {
        while (true) {
            System.out.println();
            System.out.println("--- TOPLU SESLENDİRME MENÜSÜ ---");
            System.out.println("1 - İlk " + ILK_GRUP_LIMITI + " eksik parçayı üret");
            System.out.println("2 - Tek bir bölümü üret");
            System.out.println("3 - Bütün eksik parçaları üret");
            System.out.println("4 - Yalnızca 1 eksik parçayla kalite testi yap");
            System.out.println("0 - İptal");
            System.out.print("Seçimin: ");
            String cevap = konsol.readLine();
            int secim = sayiyaCevir(cevap, -1);

            if (secim == 0) {
                return null;
            }
            if (secim == 1) {
                int adet = Math.min(ILK_GRUP_LIMITI, eksikParcalar.size());
                return new UretimPlani(
                        new ArrayList<>(eksikParcalar.subList(0, adet)),
                        "İlk " + adet + " eksik parça"
                );
            }
            if (secim == 2) {
                return bolumPlaniSec(eksikParcalar, ttsKlasoru, konsol);
            }
            if (secim == 3) {
                return new UretimPlani(new ArrayList<>(eksikParcalar), "Bütün eksik parçalar");
            }
            if (secim == 4) {
                return new UretimPlani(
                        new ArrayList<>(eksikParcalar.subList(0, 1)),
                        "1 parçalık kalite testi"
                );
            }
            System.out.println("Geçerli bir seçenek gir.");
        }
    }

    private static UretimPlani bolumPlaniSec(List<Path> eksikParcalar,
                                             Path ttsKlasoru,
                                             BufferedReader konsol) throws Exception {
        Map<String, List<Path>> bolumler = new LinkedHashMap<>();
        for (Path parca : eksikParcalar) {
            String bolum = bolumKodu(parca);
            bolumler.computeIfAbsent(bolum, key -> new ArrayList<>()).add(parca);
        }
        if (bolumler.isEmpty()) {
            return null;
        }

        Map<String, String> basliklar = bolumBasliklariniOku(ttsKlasoru.resolve("tts-manifest.json"));
        List<String> kodlar = new ArrayList<>(bolumler.keySet());
        System.out.println();
        System.out.println("--- EKSİK BÖLÜMLER ---");
        for (int i = 0; i < kodlar.size(); i++) {
            String kod = kodlar.get(i);
            List<Path> parcalar = bolumler.get(kod);
            Istatistik istatistik = istatistik(parcalar);
            String baslik = basliklar.getOrDefault(kod, "Bölüm " + kod);
            System.out.printf(Locale.ROOT,
                    "%2d - %s | %d parça | %s karakter | %s%n",
                    i + 1,
                    baslik,
                    parcalar.size(),
                    sayiBicimle(istatistik.karakter()),
                    sureBicimle(istatistik.tahminiSaniye()));
        }

        while (true) {
            System.out.print("Üretilecek bölüm numarası (0 = iptal): ");
            int secim = sayiyaCevir(konsol.readLine(), -1);
            if (secim == 0) {
                return null;
            }
            if (secim >= 1 && secim <= kodlar.size()) {
                String kod = kodlar.get(secim - 1);
                String baslik = basliklar.getOrDefault(kod, "Bölüm " + kod);
                return new UretimPlani(new ArrayList<>(bolumler.get(kod)), baslik);
            }
            System.out.println("Geçerli bir bölüm numarası gir.");
        }
    }

    private static Map<String, String> bolumBasliklariniOku(Path manifest) {
        Map<String, String> sonuc = new LinkedHashMap<>();
        if (!Files.isRegularFile(manifest)) {
            return sonuc;
        }
        try {
            JsonNode kok = OBJECT_MAPPER.readTree(Files.readString(manifest, StandardCharsets.UTF_8));
            for (JsonNode parca : kok.path("parcalar")) {
                String dosya = parca.path("dosyaAdi").asText("");
                if (dosya.isBlank()) {
                    dosya = parca.path("metinDosyasi").asText("");
                }
                String kod = bolumKodu(dosya);
                String baslik = parca.path("bolumBasligi").asText("").trim();
                if (!kod.isBlank() && !baslik.isBlank()) {
                    sonuc.putIfAbsent(kod, baslik);
                }
            }
        } catch (Exception ignored) {
        }
        return sonuc;
    }

    private static String bolumKodu(Path parca) {
        return bolumKodu(parca.getFileName().toString());
    }

    private static String bolumKodu(String dosyaAdi) {
        Matcher matcher = PARCA_ADI.matcher(dosyaAdi == null ? "" : dosyaAdi);
        return matcher.matches() ? matcher.group(1) : "000";
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
        System.out.println();
        System.out.println("--- TTS HAZIR ESERLER ---");
        for (int i = 0; i < eserler.size(); i++) {
            System.out.printf(Locale.ROOT, "%2d - %s%n", i + 1, eserler.get(i).getFileName());
        }
        while (true) {
            System.out.print("Ses üretilecek eser numarası (0 = iptal): ");
            int secim = sayiyaCevir(konsol.readLine(), -1);
            if (secim == 0) {
                return null;
            }
            if (secim >= 1 && secim <= eserler.size()) {
                return eserler.get(secim - 1);
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
                    .filter(path -> PARCA_ADI.matcher(path.getFileName().toString()).matches())
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
        Path jsonDosyasi = sonuc.dosya().resolveSibling(
                uzantisiz(sonuc.dosya().getFileName().toString()) + ".json"
        );
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

    private static void manifestVeListeleriYaz(Path eserKlasoru,
                                                Path eserSesKlasoru,
                                                Path parcaSesKlasoru,
                                                List<Path> metinParcalari,
                                                ElevenLabsClient.Ses ses,
                                                String model) throws Exception {
        Files.createDirectories(parcaSesKlasoru);
        String goreliParcaKlasoru = eserSesKlasoru.relativize(parcaSesKlasoru)
                .toString()
                .replace('\\', '/');
        String modelEtiketi = modelDosyaEtiketi(model);
        boolean varsayilanModel = "eleven_multilingual_v2".equals(model);

        ObjectNode kok = OBJECT_MAPPER.createObjectNode();
        kok.put("saglayici", "ElevenLabs");
        kok.put("model", model);
        kok.put("voiceId", ses.id());
        kok.put("voiceName", ses.ad());
        kok.put("eserKlasoru", eserKlasoru.toAbsolutePath().toString());
        kok.put("guncellenmeZamani", OffsetDateTime.now().toString());
        kok.put("toplamParca", metinParcalari.size());

        ArrayNode parcalarJson = kok.putArray("parcalar");
        StringBuilder okumaListesi = new StringBuilder();
        StringBuilder tumEserM3u = new StringBuilder("#EXTM3U\n");
        Map<String, StringBuilder> bolumM3u = new LinkedHashMap<>();
        int hazir = 0;

        for (int i = 0; i < metinParcalari.size(); i++) {
            Path metinDosyasi = metinParcalari.get(i);
            String temelAd = uzantisiz(metinDosyasi.getFileName().toString());
            Path mp3 = parcaSesKlasoru.resolve(temelAd + ".mp3");
            Path json = parcaSesKlasoru.resolve(temelAd + ".json");
            boolean tamam = parcaGecerliMi(metinDosyasi, parcaSesKlasoru, ses, model);
            if (tamam) {
                hazir++;
                String goreli = goreliParcaKlasoru + "/" + mp3.getFileName().toString().replace('\\', '/');
                tumEserM3u.append(goreli).append('\n');
                String bolum = bolumKodu(metinDosyasi);
                bolumM3u.computeIfAbsent(bolum, key -> new StringBuilder("#EXTM3U\n"))
                        .append("../")
                        .append(goreliParcaKlasoru)
                        .append("/")
                        .append(mp3.getFileName().toString().replace('\\', '/'))
                        .append('\n');
            }

            ObjectNode kayit = parcalarJson.addObject();
            kayit.put("sira", i + 1);
            kayit.put("bolum", bolumKodu(metinDosyasi));
            kayit.put("metinDosyasi", metinDosyasi.getFileName().toString());
            kayit.put("sesDosyasi", mp3.getFileName().toString());
            kayit.put("metadataDosyasi", json.getFileName().toString());
            kayit.put("durum", tamam ? "TAMAMLANDI" : "BEKLIYOR");
            String metin = Files.readString(metinDosyasi, StandardCharsets.UTF_8).trim();
            kayit.put("karakterSayisi", metin.length());
            kayit.put("kaynakMetinSha256", sha256(metin));
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
        kok.put("tamamlanmaOrani", metinParcalari.isEmpty()
                ? 0.0
                : hazir * 100.0 / metinParcalari.size());

        Files.writeString(parcaSesKlasoru.resolve("ses-manifest.json"),
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(kok),
                StandardCharsets.UTF_8);
        Files.writeString(parcaSesKlasoru.resolve("ses-okuma-listesi.txt"),
                okumaListesi.toString(), StandardCharsets.UTF_8);
        Path tumEserListesi = eserSesKlasoru.resolve(
                varsayilanModel ? "tum-eser.m3u8" : "tum-eser-" + modelEtiketi + ".m3u8"
        );
        Files.writeString(tumEserListesi,
                tumEserM3u.toString(), StandardCharsets.UTF_8);

        Path bolumListeKlasoru = eserSesKlasoru.resolve(
                varsayilanModel ? "bolum-listeleri" : "bolum-listeleri-" + modelEtiketi
        );
        Files.createDirectories(bolumListeKlasoru);
        for (Map.Entry<String, StringBuilder> entry : bolumM3u.entrySet()) {
            Files.writeString(bolumListeKlasoru.resolve("bolum-" + entry.getKey() + ".m3u8"),
                    entry.getValue().toString(), StandardCharsets.UTF_8);
        }

        String durum = "saglayici=ElevenLabs" + System.lineSeparator()
                + "model=" + model + System.lineSeparator()
                + "voiceId=" + ses.id() + System.lineSeparator()
                + "voiceName=" + ses.ad() + System.lineSeparator()
                + "toplamParca=" + metinParcalari.size() + System.lineSeparator()
                + "hazirParca=" + hazir + System.lineSeparator()
                + "eksikParca=" + Math.max(0, metinParcalari.size() - hazir) + System.lineSeparator()
                + "guncellenmeZamani=" + OffsetDateTime.now() + System.lineSeparator();
        Path modelDurumFlag = eserSesKlasoru.resolve(
                varsayilanModel
                        ? "_ses_uretim_durumu.flag"
                        : "_ses_uretim_durumu-" + modelEtiketi + ".flag"
        );
        Files.writeString(modelDurumFlag, durum, StandardCharsets.UTF_8);
        // Son seçilen modelin durumu kolay görülsün diye genel durum dosyasını da güncelle.
        Files.writeString(eserSesKlasoru.resolve("_ses_uretim_durumu.flag"),
                durum, StandardCharsets.UTF_8);

        Path tamamlandi = eserSesKlasoru.resolve(
                varsayilanModel
                        ? "_tamamlandi.flag"
                        : "_tamamlandi-" + modelEtiketi + ".flag"
        );
        if (hazir == metinParcalari.size()) {
            Files.writeString(tamamlandi,
                    "tamamlanmaZamani=" + OffsetDateTime.now() + System.lineSeparator()
                            + "model=" + model + System.lineSeparator()
                            + "toplamParca=" + hazir + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } else {
            Files.deleteIfExists(tamamlandi);
        }
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

    private static Map<Path, Integer> indeksHaritasi(List<Path> yollar) {
        Map<Path, Integer> sonuc = new LinkedHashMap<>();
        for (int i = 0; i < yollar.size(); i++) {
            sonuc.put(yollar.get(i), i);
        }
        return sonuc;
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

    private static Istatistik istatistik(List<Path> parcalar) throws Exception {
        long karakter = 0;
        long kelime = 0;
        for (Path parca : parcalar) {
            String metin = Files.readString(parca, StandardCharsets.UTF_8).trim();
            karakter += metin.length();
            if (!metin.isBlank()) {
                kelime += metin.split("\\s+").length;
            }
        }
        long saniye = Math.max(0L, Math.round(kelime / 2.5d));
        return new Istatistik(karakter, kelime, saniye);
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

    private static String sayiBicimle(long sayi) {
        return String.format(Locale.forLanguageTag("tr-TR"), "%,d", sayi);
    }

    private static String sureBicimle(long saniye) {
        long saat = saniye / 3_600;
        long dakika = (saniye % 3_600) / 60;
        long kalan = saniye % 60;
        if (saat > 0) {
            return saat + " sa " + dakika + " dk";
        }
        if (dakika > 0) {
            return dakika + " dk " + kalan + " sn";
        }
        return kalan + " sn";
    }

    private static int sayiyaCevir(String metin, int varsayilan) {
        try {
            return Integer.parseInt(metin == null ? "" : metin.trim());
        } catch (NumberFormatException e) {
            return varsayilan;
        }
    }

    private static String uzantisiz(String ad) {
        int nokta = ad.lastIndexOf('.');
        return nokta > 0 ? ad.substring(0, nokta) : ad;
    }

    public static boolean acikOnayAlindi(String onay) {
        return onay != null && onay.trim().equalsIgnoreCase("EVET");
    }

    public static boolean buyukEserOtomatikUretimEngelli(int eserId, int parcaSayisi) {
        return eserId == 6 || parcaSayisi >= 80;
    }

    private static int eserIdBul(String klasorAdi) {
        if (klasorAdi == null) {
            return -1;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("ESER-(\\d{5})")
                .matcher(klasorAdi);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
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

    private record Istatistik(long karakter, long kelime, long tahminiSaniye) {
    }

    private record ModelSecenegi(String modelId,
                                      String ad,
                                      double krediCarpani,
                                      String klasorAdi,
                                      String aciklama) {
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
                        int toplamParca) {
        public static Sonuc basarili(Path klasor,
                                     int uretilen,
                                     int hazir,
                                     int toplam,
                                     boolean tumuTamam) {
            return new Sonuc(true, false, tumuTamam, "Başarılı",
                    klasor, uretilen, hazir, toplam);
        }

        public static Sonuc atlandi(String mesaj) {
            return new Sonuc(false, true, false, mesaj,
                    null, 0, 0, 0);
        }

        public static Sonuc hatali(Path klasor,
                                   int uretilen,
                                   int hazir,
                                   int toplam,
                                   String mesaj) {
            return new Sonuc(false, false, false, mesaj,
                    klasor, uretilen, hazir, toplam);
        }
    }
}
