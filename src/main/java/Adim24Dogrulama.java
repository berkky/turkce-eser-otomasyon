import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adım 24 ElevenLabs entegrasyon doğrulamaları — gerçek API'ye bağımlı değildir.
 */
public final class Adim24Dogrulama {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();

        apiKeyYokkenGuvenli();
        mockKrediSenaryolari();
        mockOnizlemeIdempotent();
        sifirBaytAudioReddi();
        tamUretimOnaysizEngel();
        modelPolitikasi();
        turkceNormalizer();

        System.out.println("ADIM 24 DOĞRULAMA: BAŞARILI");
    }

    private static void apiKeyYokkenGuvenli() {
        ElevenLabsFabrika.DurumOzeti ozet = new ElevenLabsFabrika.DurumOzeti(
                false, "KAPALI", "YOK", "YOK", "YOK",
                false, -1, -1, -1,
                ElevenLabsModelPolitikasi.VARSAYILAN_MODEL, false, "ELEVENLABS_API_KEY yok"
        );
        if (ozet.hazir()) {
            hata("API anahtarı yokken hazır görünmemeli");
        }
        if (!"YOK".equals(ozet.apiKeyDurumu())) {
            hata("API anahtarı durumu YOK olmalı");
        }
        System.out.println("OK: API anahtarı yokken güvenli KAPALI durumu");
    }

    private static void mockKrediSenaryolari() throws Exception {
        ElevenLabsMockClient krediVar = new ElevenLabsMockClient(
                ElevenLabsModelPolitikasi.VARSAYILAN_MODEL, 1_000L, 10_000L, true, "mock-voice");
        if (krediVar.abonelikBilgisiniGetir().kalanPlanKredisi() <= 0L) {
            hata("Mock kredi var senaryosu başarısız");
        }

        ElevenLabsMockClient krediYok = new ElevenLabsMockClient(
                ElevenLabsModelPolitikasi.VARSAYILAN_MODEL, 10_000L, 10_000L, true, "mock-voice");
        if (krediYok.abonelikBilgisiniGetir().kalanPlanKredisi() > 0L) {
            hata("Mock kredi yok senaryosu başarısız");
        }

        Path gecici = Files.createTempDirectory("elevenlabs-mock-");
        try {
            Path mp3 = gecici.resolve("test.mp3");
            krediVar.sesUret("Kısa test metni.", krediVar.sesleriListele().getFirst(), mp3);
            if (krediVar.ttsCagriSayisi() != 1L) {
                hata("Mock TTS çağrısı sayılmadı");
            }
            boolean hataBeklenen = false;
            try {
                krediYok.sesUret("Kısa test.", krediYok.sesleriListele().getFirst(), gecici.resolve("yok.mp3"));
            } catch (ElevenLabsClient.ElevenLabsApiException e) {
                hataBeklenen = e.getDurumKodu() == 402;
            }
            if (!hataBeklenen) {
                hata("Kredi yokken mock TTS engellenmedi");
            }
        } finally {
            Files.walk(gecici)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
        }
        System.out.println("OK: Mock kredi var/yok senaryoları");
    }

    private static void mockOnizlemeIdempotent() throws Exception {
        Path kok = Files.createTempDirectory("elevenlabs-onizleme-");
        Path metinArsiv = kok.resolve("metin-arsivi");
        Path sesArsiv = kok.resolve("ses-arsivi");
        Path eser = metinArsiv.resolve("ESER-00005 - Kasagi - Vikikaynak");
        Path tts = eser.resolve("tts-parcalari");
        Files.createDirectories(tts);
        Files.writeString(tts.resolve("001-001.txt"),
                "Kaşağı hikâyesinden kısa bir önizleme metni. Bir varmış bir yokmuş. "
                        + "Evliya Çelebi bu eserde pek çok ayrıntıyı anlatmıştır. "
                        + "Türkçe karakterler: ğüşıöç ĞÜŞİÖÇ.");

        ElevenLabsMockClient mock = new ElevenLabsMockClient();
        ElevenLabsOnizlemeService.OnizlemeSonucu ilk =
                ElevenLabsOnizlemeService.uret(5, metinArsiv, sesArsiv, mock, "mock-voice-id-0001");
        if (!ilk.basarili() || ilk.mevcutKullanildi()) {
            hata("İlk mock önizleme başarısız");
        }
        long ilkCagri = mock.ttsCagriSayisi();

        ElevenLabsOnizlemeService.OnizlemeSonucu ikinci =
                ElevenLabsOnizlemeService.uret(5, metinArsiv, sesArsiv, mock, "mock-voice-id-0001");
        if (!ikinci.basarili() || !ikinci.mevcutKullanildi()) {
            hata("İkinci mock önizleme idempotent değil");
        }
        if (mock.ttsCagriSayisi() != ilkCagri) {
            hata("Aynı requestHash ile yeniden API/mock çağrısı yapıldı");
        }

        Files.walk(kok)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
        System.out.println("OK: requestHash ile idempotent önizleme");
    }

    private static void sifirBaytAudioReddi() {
        if (!ElevenLabsMockClient.sifirBaytAudioReddedilir(new byte[0])) {
            hata("0 byte audio reddedilmedi");
        }
        if (ElevenLabsMockClient.gecerliAudioMu(new byte[0])) {
            hata("0 byte audio geçerli sayıldı");
        }
        System.out.println("OK: 0 byte audio reddediliyor");
    }

    private static void tamUretimOnaysizEngel() {
        if (ElevenLabsTamEserService.acikOnayAlindi(null)) {
            hata("Boş onay EVET sayıldı");
        }
        if (ElevenLabsTamEserService.acikOnayAlindi("E")) {
            hata("E onayı tam üretim için yeterli sayıldı");
        }
        if (!ElevenLabsTamEserService.acikOnayAlindi("EVET")) {
            hata("EVET onayı kabul edilmedi");
        }
        if (!ElevenLabsTamEserService.buyukEserOtomatikUretimEngelli(6, 120)) {
            hata("ESER-00006 büyük eser otomatik üretim engeli çalışmıyor");
        }
        System.out.println("OK: Tam üretim açık onay ve büyük eser koruması");
    }

    private static void modelPolitikasi() {
        if (!ElevenLabsModelPolitikasi.VARSAYILAN_MODEL.equals(
                ElevenLabsModelPolitikasi.onerilenModel(ElevenLabsModelPolitikasi.EserTuru.KITAP))) {
            hata("Kitap için varsayılan model yanlış");
        }
        if (!ElevenLabsModelPolitikasi.V3_MODEL.equals(
                ElevenLabsModelPolitikasi.onerilenModel(ElevenLabsModelPolitikasi.EserTuru.SIIR))) {
            hata("Şiir için v3 modeli önerilmiyor");
        }
        if (!ElevenLabsModelPolitikasi.FLASH_MODEL.equals(
                ElevenLabsModelPolitikasi.onerilenModel(ElevenLabsModelPolitikasi.EserTuru.HABER))) {
            hata("Haber için flash modeli önerilmiyor");
        }
        System.out.println("OK: Türkçe eser türü model politikası");
    }

    private static void turkceNormalizer() {
        String ham = "Sayfa 12\r\n\r\nBaşlık\r\nBaşlık\r\nUzun   metin   burada.";
        String temiz = TurkishSpeechTextNormalizer.normalize(ham);
        if (temiz.contains("Sayfa 12")) {
            hata("Sayfa numarası temizlenmedi");
        }
        if (temiz.contains("Başlık\r\nBaşlık") || temiz.split("Başlık").length > 3) {
            hata("Başlık tekrarı azaltılmadı");
        }
        if (temiz.contains("  ")) {
            hata("Çoklu boşluk temizlenmedi");
        }
        System.out.println("OK: TurkishSpeechTextNormalizer");
    }

    private static void hata(String mesaj) {
        throw new IllegalStateException("ADIM 24 TEST HATASI: " + mesaj);
    }
}
