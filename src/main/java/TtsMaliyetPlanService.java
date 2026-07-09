import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Eser bazlı TTS maliyet/kredi planı — tam üretim başlatmaz.
 */
public final class TtsMaliyetPlanService {
    private static final int ONIZLEME_HEdef_KARAKTER = 900;
    private final WebOrtam ortam;
    private final ObjectMapper json = new ObjectMapper();

    public TtsMaliyetPlanService(WebOrtam ortam) {
        this.ortam = ortam;
    }

    public TtsMaliyetPlani planla(int eserId) throws Exception {
        WebEserService.WebEserDetay detay = new WebEserService(ortam).eserDetay(eserId);
        if (detay == null) {
            return new TtsMaliyetPlani(eserId, 0, 0, ONIZLEME_HEdef_KARAKTER, 0, 0,
                    false, true, "ESER_BULUNAMADI", "Eser bulunamadı");
        }
        WebEserService.WebEserOzeti o = detay.ozet();
        String modelId = ElevenLabsModelPolitikasi.ortamModeliVeyaVarsayilan();
        long tamMaliyet = tahminiKredi(o.karakter(), modelId);
        long onizMaliyet = tahminiKredi(ONIZLEME_HEdef_KARAKTER, modelId);

        long kalan = 0;
        boolean elHazir = false;
        try {
            var ozet = ElevenLabsFabrika.durumOzeti();
            elHazir = ozet.hazir();
            if (ElevenLabsFabrika.mockModAktif() || elHazir) {
                ElevenLabsIstemci client = ElevenLabsFabrika.mockModAktif()
                        ? new ElevenLabsMockClient()
                        : ElevenLabsFabrika.olusturGuvenli();
                kalan = client.abonelikBilgisiniGetir().kalanPlanKredisi();
            }
        } catch (Exception ignored) {
        }

        boolean buyuk = o.buyukEser() || eserId == SesKaliteOlcutleri.ASTRONOMI_ESER_ID;
        String onerilen;
        if (buyuk) {
            onerilen = "BUYUK_ESER_MALIYET_ONAYI";
        } else if (!elHazir || kalan <= 0) {
            onerilen = "KREDI_BEKLENIYOR";
        } else if (kalan < onizMaliyet) {
            onerilen = "ONIZLEME_ICIN_YETERSIZ_KREDI";
        } else if (kalan < tamMaliyet) {
            onerilen = "ONIZLEME_URETILEBILIR_TAM_ESER_YETERSIZ";
        } else {
            onerilen = "ONIZLEME_URETILEBILIR";
        }

        return new TtsMaliyetPlani(eserId, o.karakter(), o.ttsParca(), ONIZLEME_HEdef_KARAKTER,
                kalan, tamMaliyet, buyuk, !elHazir || kalan <= 0, onerilen,
                aciklama(onerilen, buyuk));
    }

    public String json(int eserId) throws Exception {
        TtsMaliyetPlani p = planla(eserId);
        ObjectNode n = json.createObjectNode();
        n.put("eserId", p.eserId());
        n.put("toplamKarakter", p.toplamKarakter());
        n.put("ttsParca", p.ttsParca());
        n.put("tahminiOnizlemeKarakteri", p.tahminiOnizlemeKarakteri());
        n.put("kalanElevenLabsKredisi", p.kalanElevenLabsKredisi());
        n.put("tamUretimTahminiKredi", p.tamUretimTahminiKredi());
        n.put("buyukEser", p.buyukEser());
        n.put("krediYok", p.krediYok());
        n.put("onerilenAksiyon", p.onerilenAksiyon());
        n.put("aciklama", p.aciklama());
        n.put("tamUretimKapali", true);
        return WebGuvenlikService.guvenliJson(json.writerWithDefaultPrettyPrinter().writeValueAsString(n));
    }

    private static long tahminiKredi(int karakter, String modelId) {
        double carpan = ElevenLabsClient.bilinenModelKrediCarpani(modelId);
        return Math.max(1L, (long) Math.ceil(karakter * carpan));
    }

    private static String aciklama(String aksiyon, boolean buyuk) {
        return switch (aksiyon) {
            case "BUYUK_ESER_MALIYET_ONAYI" ->
                    "Büyük eser — önce önizleme ve maliyet onayı gerekir. Tam üretim web panelinden başlatılamaz.";
            case "KREDI_BEKLENIYOR" -> "ElevenLabs kredisi yok veya API kapalı — yalnızca mock/izleme modu.";
            case "ONIZLEME_ICIN_YETERSIZ_KREDI" -> "Kalan kredi önizleme için yetersiz.";
            case "ONIZLEME_URETILEBILIR_TAM_ESER_YETERSIZ" ->
                    "Önizleme üretilebilir; tam eser için kredi yetersiz olabilir.";
            case "ONIZLEME_URETILEBILIR" -> "Kaşağı için güvenli önizleme üretilebilir (açık onay gerekir).";
            default -> buyuk ? "Büyük eser koruması aktif." : "Plan salt okunur — tam üretim kapalı.";
        };
    }

    public record TtsMaliyetPlani(
            int eserId, int toplamKarakter, int ttsParca, int tahminiOnizlemeKarakteri,
            long kalanElevenLabsKredisi, long tamUretimTahminiKredi,
            boolean buyukEser, boolean krediYok, String onerilenAksiyon, String aciklama
    ) {
    }
}
