import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Tam eser üretim planı — maliyet ve risk hesabı, TTS başlatmaz.
 */
public final class TamEserUretimPlanService {
    private static final Pattern PARCA = Pattern.compile("^\\d{3}-\\d{3}\\.txt$");
    private static final int KARAKTER_DAKIKA = 900;

    private final WebOrtam ortam;
    private final TamEserUretimStorageService depo;

    public TamEserUretimPlanService(WebOrtam ortam) {
        this.ortam = ortam;
        this.depo = new TamEserUretimStorageService(ortam.kuyruk());
    }

    public TamEserUretimPlani planUret(int eserId) throws Exception {
        TtsMaliyetPlanService.TtsMaliyetPlani maliyet = new TtsMaliyetPlanService(ortam).planla(eserId);
        WebEserService.WebEserDetay detay = new WebEserService(ortam).eserDetay(eserId);
        String baslik = detay != null ? detay.ozet().eserAdi() : "ESER-" + String.format("%05d", eserId);
        List<TamEserUretimParcasi> parcalar = parcalariTopla(eserId);

        int karakter = parcalar.stream().mapToInt(TamEserUretimParcasi::karakterSayisi).sum();
        int parcaSayisi = parcalar.size();
        boolean kaynakGecerli = karakter > 0 && parcaSayisi > 0;
        String model = ElevenLabsModelPolitikasi.ortamModeliVeyaVarsayilan();
        long krediIhtiyaci = maliyet.tamUretimTahminiKredi();
        long kalan = maliyet.kalanElevenLabsKredisi();
        boolean mockAktif = ElevenLabsFabrika.mockModAktif() || ElevenLabsFabrika.durumOzeti().mockMod();
        boolean apiHazir = ElevenLabsFabrika.durumOzeti().hazir();
        boolean krediYeterli = mockAktif || (apiHazir && kalan >= krediIhtiyaci);
        boolean buyuk = maliyet.buyukEser() || eserId == SesKaliteOlcutleri.ASTRONOMI_ESER_ID;

        TamEserUretimRisk risk;
        String onerilen;
        if (!kaynakGecerli) {
            risk = TamEserUretimRisk.ENGELLI;
            onerilen = maliyet.onerilenAksiyon().startsWith("SOURCE_")
                    ? maliyet.onerilenAksiyon()
                    : "SOURCE_TEXT_NOT_FOUND";
        } else if (buyuk) {
            risk = TamEserUretimRisk.YUKSEK;
            onerilen = "BUYUK_ESER_MANUEL_ONAY — yüksek risk; açık onay ve maliyet planı zorunlu. Otomatik üretim kapalı.";
        } else if (!mockAktif && !apiHazir && maliyet.krediYok()) {
            risk = TamEserUretimRisk.ENGELLI;
            onerilen = "KREDI_BEKLENIYOR — üretilemez; kredi bekleniyor. Onay olmadan başlatılmaz.";
        } else if (!krediYeterli) {
            risk = TamEserUretimRisk.ORTA;
            onerilen = "KREDI_BEKLENIYOR — plan görüntülenebilir; kredi yetersiz. Onay olmadan başlatılmaz.";
        } else if (eserId == SesKaliteOlcutleri.KASAGI_ESER_ID) {
            risk = TamEserUretimRisk.DUSUK;
            onerilen = "ONAY_TASLAK_OLUSTUR — küçük eser planı hazır; onay taslağı oluşturulabilir. Üretim başlatılmaz.";
        } else {
            risk = TamEserUretimRisk.ORTA;
            onerilen = "ONAY_TASLAK_OLUSTUR — plan hazır; onay olmadan başlatılmaz.";
        }

        TamEserUretimPlani plan = new TamEserUretimPlani(
                eserId,
                baslik,
                karakter,
                parcaSayisi,
                tahminiDakika(karakter),
                "elevenlabs",
                model,
                kalan,
                krediIhtiyaci,
                krediYeterli,
                buyuk,
                true,
                true,
                risk,
                onerilen,
                parcalar,
                OffsetDateTime.now().toString()
        );
        depo.planKaydet(plan);
        return plan;
    }

    public TamEserUretimPlani planGetir(int eserId) throws Exception {
        TamEserUretimPlani existing = depo.planOku(eserId);
        List<TamEserUretimParcasi> currentParts = parcalariTopla(eserId);
        int currentCharacters = currentParts.stream().mapToInt(TamEserUretimParcasi::karakterSayisi).sum();
        if (existing != null && existing.toplamKarakter() > 0 && existing.ttsParcaSayisi() > 0
                && existing.toplamKarakter() == currentCharacters
                && existing.ttsParcaSayisi() == currentParts.size()
                && existing.parcalar().equals(currentParts)) {
            return existing;
        }
        // Sıfır veya kaynakla uyuşmayan cache yalnız canonical kaynaktan yenilenir.
        return planUret(eserId);
    }

    public String json(int eserId) throws Exception {
        TamEserUretimPlani plan = planGetir(eserId);
        return TamEserUretimGuvenlikService.jsonGuvenli(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(plan.json(new com.fasterxml.jackson.databind.ObjectMapper())));
    }

    private List<TamEserUretimParcasi> parcalariTopla(int eserId) throws Exception {
        Path metinKlasor = eserKlasoruBul(ortam.metinArsivi(), eserId);
        if (metinKlasor == null) {
            return List.of();
        }
        Path tts = metinKlasor.resolve("tts-parcalari");
        if (!Files.isDirectory(tts)) {
            return List.of();
        }
        List<TamEserUretimParcasi> parcalar = new ArrayList<>();
        try (Stream<Path> s = Files.list(tts)) {
            List<Path> dosyalar = s.filter(Files::isRegularFile)
                    .filter(p -> PARCA.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            int sira = 1;
            for (Path p : dosyalar) {
                int karakter = Files.readString(p, StandardCharsets.UTF_8).length();
                int sure = Math.max(1, karakter / 15);
                parcalar.add(new TamEserUretimParcasi(sira++, p.getFileName().toString(), karakter, sure));
            }
        }
        return parcalar;
    }

    private static int tahminiDakika(int karakter) {
        return karakter <= 0 ? 0 : (int) Math.ceil(karakter / (double) KARAKTER_DAKIKA);
    }

    private static Path eserKlasoruBul(Path ana, int eserId) throws Exception {
        if (!Files.isDirectory(ana)) {
            return null;
        }
        String onEk = String.format(Locale.ROOT, "ESER-%05d", eserId).toLowerCase(Locale.ROOT);
        try (Stream<Path> s = Files.list(ana)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(onEk))
                    .findFirst().orElse(null);
        }
    }
}
