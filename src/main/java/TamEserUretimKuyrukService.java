import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Onaylı tam eser kuyruk kaydı — TTS üretimini asla başlatmaz.
 */
public final class TamEserUretimKuyrukService {
    private static final AtomicBoolean URETIM_BASLATILDI = new AtomicBoolean(false);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final TamEserUretimStorageService depo;

    public TamEserUretimKuyrukService(WebOrtam ortam) {
        this.depo = new TamEserUretimStorageService(ortam.kuyruk());
    }

    /** Test ve doğrulama için — Adım 31'de her zaman false kalır. */
    public static boolean uretimBaslatildiMi() {
        return URETIM_BASLATILDI.get();
    }

    public KuyrukSonucu kuyrugaAl(int eserId, boolean onayli) throws Exception {
        if (!onayli) {
            return KuyrukSonucu.reddedildi("Kuyruk oluşturulmadı — -Onayli bayrağı veya açık onay gerekli.");
        }
        TamEserUretimOnayi onay = depo.onayOku(eserId);
        if (onay == null) {
            return KuyrukSonucu.reddedildi("Onay taslağı yok — önce tam-eser-onay-taslagi.ps1 çalıştırın.");
        }
        TamEserUretimPlani plan = depo.planOku(eserId);
        if (plan == null) {
            return KuyrukSonucu.reddedildi("Maliyet planı yok — önce tam-eser-plan.ps1 çalıştırın.");
        }

        TamEserUretimKuyrukKaydi.KuyrukDurumu durum;
        String notlar;
        if (plan.buyukEserMi() || eserId == SesKaliteOlcutleri.ASTRONOMI_ESER_ID) {
            durum = TamEserUretimKuyrukKaydi.KuyrukDurumu.BLOCKED;
            notlar = "Büyük eser (ESER-00006) — kuyruk kaydı oluşturuldu ancak gerçek üretim Adım 31'de kapalı. "
                    + "Manuel çalıştırma bekleniyor; TTS başlatılmadı.";
        } else if (plan.riskSeviyesi() == TamEserUretimRisk.ENGELLI || !plan.krediYeterliMi()) {
            durum = TamEserUretimKuyrukKaydi.KuyrukDurumu.APPROVED_NOT_STARTED;
            notlar = "Onaylı kuyruk kaydı oluşturuldu; kredi yetersiz — gerçek TTS başlatılmadı. Manuel çalıştırma bekleniyor.";
        } else {
            durum = TamEserUretimKuyrukKaydi.KuyrukDurumu.READY_FOR_MANUAL_RUN;
            notlar = "Onaylı kuyruk kaydı oluşturuldu. Gerçek TTS üretimi başlatılmadı — manuel çalıştırma bekleniyor.";
        }

        String simdi = OffsetDateTime.now().toString();
        TamEserUretimKuyrukKaydi kayit = new TamEserUretimKuyrukKaydi(
                UUID.randomUUID().toString(),
                eserId,
                onay.onayId(),
                plan.secilenSaglayici(),
                plan.secilenModel(),
                durum,
                plan.ttsParcaSayisi(),
                plan.toplamKarakter(),
                TamEserUretimGuvenlikService.outputSafeName(eserId),
                simdi,
                simdi,
                notlar
        );
        depo.kuyrukKaydet(kayit);
        // Bilinçli olarak TTS/üretim servisi çağrılmaz
        return KuyrukSonucu.basarili(kayit, plan.buyukEserMi());
    }

    public List<TamEserUretimKuyrukKaydi> listele() throws Exception {
        return depo.tumKuyrukKayitlari();
    }

    public String jsonListe() throws Exception {
        ArrayNode dizi = JSON.createArrayNode();
        for (TamEserUretimKuyrukKaydi k : listele()) {
            dizi.add(k.json(JSON));
        }
        return TamEserUretimGuvenlikService.jsonGuvenli(
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(dizi));
    }

    public record KuyrukSonucu(boolean basarili, String mesaj, TamEserUretimKuyrukKaydi kayit, boolean buyukUyari) {
        static KuyrukSonucu basarili(TamEserUretimKuyrukKaydi kayit, boolean buyuk) {
            return new KuyrukSonucu(true, kayit.notlar(), kayit, buyuk);
        }

        static KuyrukSonucu reddedildi(String mesaj) {
            return new KuyrukSonucu(false, mesaj, null, false);
        }
    }
}
