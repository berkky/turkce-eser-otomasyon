import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Kalite paneli tam raporu.
 */
public record SesKalitePanelRaporu(
        String uretimZamani,
        String sesArsivYolu,
        String metinArsivYolu,
        String panelKlasoru,
        List<SesOnizlemeKaydi> onizlemeler,
        List<SesKaliteDegerlendirmesi> degerlendirmeler,
        List<SesSaglayiciKarsilastirma> karsilastirmalar,
        String enIyiOnerilenSaglayici,
        String enIyiOnerilenModel,
        List<Integer> tamUretimeUygunEserler,
        List<Integer> yuksekKrediRiskiEserler
) {
    public ObjectNode jsonOzeti(ObjectMapper mapper) {
        ObjectNode kok = mapper.createObjectNode();
        kok.put("uretimZamani", uretimZamani);
        kok.put("sesArsivYolu", sesArsivYolu);
        kok.put("metinArsivYolu", metinArsivYolu);
        kok.put("panelKlasoru", panelKlasoru);
        kok.put("enIyiOnerilenSaglayici", enIyiOnerilenSaglayici == null ? "" : enIyiOnerilenSaglayici);
        kok.put("enIyiOnerilenModel", enIyiOnerilenModel == null ? "" : enIyiOnerilenModel);

        ArrayNode onizlemeDizisi = kok.putArray("onizlemeler");
        for (SesOnizlemeKaydi k : onizlemeler) {
            onizlemeDizisi.add(k.jsonOzeti(mapper));
        }
        ArrayNode degerlendirmeDizisi = kok.putArray("degerlendirmeler");
        for (SesKaliteDegerlendirmesi d : degerlendirmeler) {
            degerlendirmeDizisi.add(d.jsonOzeti(mapper));
        }
        ArrayNode karsilastirmaDizisi = kok.putArray("karsilastirmalar");
        for (SesSaglayiciKarsilastirma k : karsilastirmalar) {
            karsilastirmaDizisi.add(k.jsonOzeti(mapper));
        }
        ArrayNode uygun = kok.putArray("tamUretimeUygunEserler");
        for (int id : tamUretimeUygunEserler) {
            uygun.add(id);
        }
        ArrayNode risk = kok.putArray("yuksekKrediRiskiEserler");
        for (int id : yuksekKrediRiskiEserler) {
            risk.add(id);
        }
        return kok;
    }

    public static String simdi() {
        return OffsetDateTime.now().toString();
    }
}
