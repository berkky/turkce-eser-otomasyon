import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adım 31 — Onaylı tam eser üretim kapısı doğrulamaları.
 */
public final class Adim31Dogrulama {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        System.setProperty("ELEVENLABS_MOCK", "true");

        Path kok = Files.createTempDirectory("adim31-");
        try {
            hazirlaFixture(kok);
            WebOrtam ortam = new WebOrtam(kok, null, kok.resolve("arsiv"),
                    kok.resolve("metin"), kok.resolve("ses"), kok.resolve("kuyruk"),
                    null, kok.resolve("panel"));

            planUretimi(ortam);
            astronomiYuksekRisk(ortam);
            krediYokGuvenliPlan(ortam);
            onayTaslagi(ortam);
            onaysizKuyrukReddi(ortam);
            onayliKuyrukKaydi(ortam);
            uretimBaslatilmadi();
            jsonGuvenligi(ortam);
            webApiTestleri(ortam);
        } finally {
            System.clearProperty("ELEVENLABS_MOCK");
            silRecursif(kok);
        }

        dokumanTestleri(Path.of(System.getProperty("user.dir")));
        patronPaketTestleri();

        System.out.println("ADIM 31 DOĞRULAMA: BAŞARILI");
    }

    private static void planUretimi(WebOrtam ortam) throws Exception {
        TamEserUretimPlanService svc = new TamEserUretimPlanService(ortam);
        TamEserUretimPlani plan = svc.planUret(5);
        if (plan.eserId() != 5 || !plan.tamUretimVarsayilanKapaliMi() || !plan.onayGerekliMi()) {
            hata("ESER-00005 plan alanları hatalı");
        }
        if (plan.riskSeviyesi() != TamEserUretimRisk.DUSUK && plan.riskSeviyesi() != TamEserUretimRisk.ORTA) {
            hata("ESER-00005 risk beklenenden farklı: " + plan.riskSeviyesi());
        }
        Path dosya = TamEserUretimGuvenlikService.uretimKlasoru(ortam.kuyruk())
                .resolve(TamEserUretimGuvenlikService.guvenliDosyaAdi(5, "plan"));
        if (!Files.isRegularFile(dosya)) {
            hata("Plan dosyası oluşmadı");
        }
        System.out.println("OK: ESER-00005 plan üretimi");
    }

    private static void astronomiYuksekRisk(WebOrtam ortam) throws Exception {
        TamEserUretimPlani plan = new TamEserUretimPlanService(ortam).planUret(6);
        if (!plan.buyukEserMi() || plan.riskSeviyesi() != TamEserUretimRisk.YUKSEK) {
            hata("ESER-00006 yüksek risk olmalı");
        }
        System.out.println("OK: ESER-00006 yüksek risk planı");
    }

    private static void krediYokGuvenliPlan(WebOrtam ortam) throws Exception {
        System.clearProperty("ELEVENLABS_MOCK");
        try {
            TamEserUretimPlani plan = new TamEserUretimPlanService(ortam).planUret(5);
            if (plan.riskSeviyesi() != TamEserUretimRisk.ENGELLI || plan.krediYeterliMi()) {
                if (!TtsLaboratuvarYardimci.ortamVar("ELEVENLABS_API_KEY") && plan.riskSeviyesi() != TamEserUretimRisk.ENGELLI) {
                    hata("Kredi yokken ENGELLI risk bekleniyordu");
                }
            }
            System.out.println("OK: Kredi yoksa güvenli plan");
        } finally {
            System.setProperty("ELEVENLABS_MOCK", "true");
            new TamEserUretimPlanService(ortam).planUret(5);
        }
    }

    private static void onayTaslagi(WebOrtam ortam) throws Exception {
        TamEserUretimOnayi onay = new TamEserUretimOnayService(ortam).taslakOlustur(5);
        if (!TamEserUretimOnayi.OnayDurumu.TASLAK.equals(onay.onayDurumu()) || onay.tamUretimBaslatildiMi()) {
            hata("Onay taslağı hatalı");
        }
        if (!onay.onayMetni().contains("Gerçek ses üretimi başlamadı")) {
            hata("Onay metni eksik");
        }
        System.out.println("OK: Onay taslağı oluşturma");
    }

    private static void onaysizKuyrukReddi(WebOrtam ortam) throws Exception {
        var sonuc = new TamEserUretimKuyrukService(ortam).kuyrugaAl(5, false);
        if (sonuc.basarili()) {
            hata("-Onayli olmadan kuyruk oluşmamalı");
        }
        System.out.println("OK: -Onayli yoksa kuyruk reddi");
    }

    private static void onayliKuyrukKaydi(WebOrtam ortam) throws Exception {
        var sonuc = new TamEserUretimKuyrukService(ortam).kuyrugaAl(5, true);
        if (!sonuc.basarili() || sonuc.kayit() == null) {
            hata("Onaylı kuyruk oluşmalı");
        }
        if (sonuc.kayit().status() != TamEserUretimKuyrukKaydi.KuyrukDurumu.READY_FOR_MANUAL_RUN
                && sonuc.kayit().status() != TamEserUretimKuyrukKaydi.KuyrukDurumu.APPROVED_NOT_STARTED) {
            hata("ESER-00005 kuyruk durumu beklenen değil: " + sonuc.kayit().status());
        }
        if (!sonuc.kayit().outputSafeName().startsWith("ESER-00005-production-output")) {
            hata("outputSafeName hatalı");
        }
        System.out.println("OK: Onaylı kuyruk kaydı (üretim başlamaz)");
    }

    private static void uretimBaslatilmadi() {
        if (TamEserUretimKuyrukService.uretimBaslatildiMi()) {
            hata("Gerçek TTS üretimi başlatılmış olmamalı");
        }
        System.out.println("OK: Gerçek TTS provider çağrısı yapılmadı");
    }

    private static void jsonGuvenligi(WebOrtam ortam) throws Exception {
        String json = new TamEserUretimPlanService(ortam).json(5);
        if (WebGuvenlikService.apiKeySizintisiVar(json)
                || TamEserUretimGuvenlikService.yolSizintisiVar(json)) {
            hata("API key veya full path sızıntısı");
        }
        System.out.println("OK: JSON güvenliği");
    }

    private static void webApiTestleri(WebOrtam ortam) throws Exception {
        YerelWebSunucu sunucu = new YerelWebSunucu(ortam);

        String plan5 = new String(sunucu.route("GET", "/api/uretim-plan/5", null, "", true).body(),
                StandardCharsets.UTF_8);
        if (!plan5.contains("DUSUK") && !plan5.contains("ORTA")) {
            hata("/api/uretim-plan/5 risk eksik");
        }
        if (plan5.contains("C:\\Users\\") || plan5.contains("sk_")) {
            hata("/api/uretim-plan/5 güvensiz");
        }

        String plan6 = new String(sunucu.route("GET", "/api/uretim-plan/6", null, "", true).body(),
                StandardCharsets.UTF_8);
        if (!plan6.contains("YUKSEK")) {
            hata("/api/uretim-plan/6 yüksek risk göstermeli");
        }

        String kuyruk = new String(sunucu.route("GET", "/api/uretim-kuyruk", null, "", true).body(),
                StandardCharsets.UTF_8);
        if (!kuyruk.startsWith("[")) {
            hata("/api/uretim-kuyruk çalışmıyor");
        }

        String uretim = new String(sunucu.route("GET", "/uretim", null, "", true).body(),
                StandardCharsets.UTF_8);
        if (!uretim.contains("Tam üretim varsayılan kapalı")) {
            hata("/uretim render hatası");
        }

        String eser5 = new String(sunucu.route("GET", "/eser/5/uretim", null, "", true).body(),
                StandardCharsets.UTF_8);
        if (!eser5.contains("Üretim Planı")) {
            hata("/eser/5/uretim render hatası");
        }

        String eser6 = new String(sunucu.route("GET", "/eser/6/uretim", null, "", true).body(),
                StandardCharsets.UTF_8);
        if (!eser6.contains("Yüksek risk") && !eser6.contains("YUKSEK")) {
            hata("/eser/6/uretim yüksek risk eksik");
        }

        String demo = new String(sunucu.route("GET", "/demo", null, "", true).body(), StandardCharsets.UTF_8);
        if (!demo.contains("Adım 31")) {
            hata("/demo Adım 31 eksik");
        }
        System.out.println("OK: Web API ve sayfalar");
    }

    private static void patronPaketTestleri() throws Exception {
        Path paket = Files.createTempDirectory("adim31-paket-");
        System.setProperty("DEMO_PAKET_KLASORU", paket.toString());
        try {
            DemoRaporService rapor = new DemoRaporService(WebOrtam.varsayilan());
            DemoRaporService.PaketSonucu sonuc = rapor.uret();
            String md = Files.readString(sonuc.klasor().resolve("patron-demo-ozeti.md"), StandardCharsets.UTF_8);
            if (!md.contains("Adım 31") && !md.contains("üretim kapısı")) {
                hata("Patron paketi Adım 31 eksik");
            }
            System.out.println("OK: Patron demo paketi");
        } finally {
            System.clearProperty("DEMO_PAKET_KLASORU");
            silRecursif(paket);
        }
    }

    private static void dokumanTestleri(Path proje) throws Exception {
        Path mimari = proje.resolve("ADIM_31_MIMARI.md");
        if (!Files.isRegularFile(mimari)) {
            hata("ADIM_31_MIMARI.md eksik");
        }
        String icerik = Files.readString(mimari, StandardCharsets.UTF_8);
        if (!icerik.contains("Tam eser üretim kapısı") || !icerik.contains("Adım 32")) {
            hata("ADIM_31_MIMARI.md içerik eksik");
        }
        System.out.println("OK: ADIM_31_MIMARI.md");
    }

    private static void hazirlaFixture(Path kok) throws Exception {
        Path eser5 = kok.resolve("metin").resolve("ESER-00005 - Kasagi");
        Path tts5 = eser5.resolve("tts-parcalari");
        Files.createDirectories(tts5);
        Files.writeString(tts5.resolve("001-001.txt"), "Kısa test metni Kaşağı parçası.", StandardCharsets.UTF_8);

        Path eser6 = kok.resolve("metin").resolve("ESER-00006 - Astronomi");
        Path tts6 = eser6.resolve("tts-parcalari");
        Files.createDirectories(tts6);
        for (int i = 1; i <= 3; i++) {
            Files.writeString(tts6.resolve(String.format("%03d-%03d.txt", i, i)),
                    "Astronomi büyük eser parça " + i + " metin içeriği.", StandardCharsets.UTF_8);
        }
        Files.createDirectories(kok.resolve("ses"));
        Files.createDirectories(kok.resolve("kuyruk"));
        Files.createDirectories(kok.resolve("panel"));
    }

    private static void silRecursif(Path kok) throws Exception {
        if (!Files.exists(kok)) {
            return;
        }
        try (var s = Files.walk(kok)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private static void hata(String mesaj) {
        throw new AssertionError(mesaj);
    }
}
