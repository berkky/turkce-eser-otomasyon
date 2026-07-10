import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tam eser üretim onay taslağı — üretim başlatmaz.
 */
public final class TamEserUretimOnayService {
    private static final String TASLAK_METIN = """
            Bu işlem yalnızca onay taslağı oluşturdu. Gerçek ses üretimi başlamadı.
            Tam eser üretimi varsayılan olarak kapalıdır. Onay olmadan TTS başlatılmaz.
            """.trim();

    private final WebOrtam ortam;
    private final TamEserUretimPlanService planService;
    private final TamEserUretimStorageService depo;

    public TamEserUretimOnayService(WebOrtam ortam) {
        this.ortam = ortam;
        this.planService = new TamEserUretimPlanService(ortam);
        this.depo = new TamEserUretimStorageService(ortam.kuyruk());
    }

    public TamEserUretimOnayi taslakOlustur(int eserId) throws Exception {
        if (!depo.planVarMi(eserId)) {
            planService.planUret(eserId);
        }
        TamEserUretimPlani plan = depo.planOku(eserId);
        if (plan == null) {
            throw new IllegalStateException("Plan oluşturulamadı — eser bulunamadı veya veri eksik.");
        }

        String onayMetni = plan.riskSeviyesi() == TamEserUretimRisk.ENGELLI
                ? TASLAK_METIN + " Not: Kredi yetersiz — bu taslak üretim başlatmaz."
                : TASLAK_METIN;

        TamEserUretimOnayi onay = new TamEserUretimOnayi(
                UUID.randomUUID().toString(),
                eserId,
                plan.secilenSaglayici(),
                plan.secilenModel(),
                plan.toplamKarakter(),
                plan.ttsParcaSayisi(),
                plan.tahminiKrediIhtiyaci(),
                TamEserUretimOnayi.OnayDurumu.TASLAK,
                onayMetni,
                OffsetDateTime.now().toString(),
                "",
                "",
                false
        );
        depo.onayKaydet(onay);
        return onay;
    }

    public TamEserUretimOnayi onayGetir(int eserId) throws Exception {
        return depo.onayOku(eserId);
    }

    public String taslakMesaji() {
        return TASLAK_METIN;
    }
}
