import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tam eser üretim maliyet ve risk planı — üretim başlatmaz.
 */
public record TamEserUretimPlani(
        int eserId,
        String baslik,
        int toplamKarakter,
        int ttsParcaSayisi,
        int tahminiDakika,
        String secilenSaglayici,
        String secilenModel,
        long kalanKredi,
        long tahminiKrediIhtiyaci,
        boolean krediYeterliMi,
        boolean buyukEserMi,
        boolean onayGerekliMi,
        boolean tamUretimVarsayilanKapaliMi,
        TamEserUretimRisk riskSeviyesi,
        String onerilenAksiyon,
        List<TamEserUretimParcasi> parcalar,
        String createdAt
) {
    ObjectNode json(ObjectMapper mapper) {
        ObjectNode n = mapper.createObjectNode();
        n.put("eserId", eserId);
        n.put("baslik", baslik);
        n.put("toplamKarakter", toplamKarakter);
        n.put("ttsParcaSayisi", ttsParcaSayisi);
        n.put("tahminiDakika", tahminiDakika);
        n.put("secilenSaglayici", secilenSaglayici);
        n.put("secilenModel", secilenModel);
        n.put("kalanKredi", kalanKredi);
        n.put("tahminiKrediIhtiyaci", tahminiKrediIhtiyaci);
        n.put("krediYeterliMi", krediYeterliMi);
        n.put("buyukEserMi", buyukEserMi);
        n.put("onayGerekliMi", onayGerekliMi);
        n.put("tamUretimVarsayilanKapaliMi", tamUretimVarsayilanKapaliMi);
        n.put("riskSeviyesi", riskSeviyesi.name());
        n.put("onerilenAksiyon", onerilenAksiyon);
        n.put("createdAt", createdAt);
        ArrayNode parcaDizi = n.putArray("parcalar");
        for (TamEserUretimParcasi p : parcalar) {
            parcaDizi.add(p.json(mapper));
        }
        return n;
    }

    static TamEserUretimPlani fromJson(ObjectMapper mapper, ObjectNode n, List<TamEserUretimParcasi> parcalar) {
        return new TamEserUretimPlani(
                n.path("eserId").asInt(),
                n.path("baslik").asText(""),
                n.path("toplamKarakter").asInt(),
                n.path("ttsParcaSayisi").asInt(),
                n.path("tahminiDakika").asInt(),
                n.path("secilenSaglayici").asText(""),
                n.path("secilenModel").asText(""),
                n.path("kalanKredi").asLong(),
                n.path("tahminiKrediIhtiyaci").asLong(),
                n.path("krediYeterliMi").asBoolean(false),
                n.path("buyukEserMi").asBoolean(false),
                n.path("onayGerekliMi").asBoolean(true),
                n.path("tamUretimVarsayilanKapaliMi").asBoolean(true),
                TamEserUretimRisk.valueOf(n.path("riskSeviyesi").asText(TamEserUretimRisk.ORTA.name())),
                n.path("onerilenAksiyon").asText(""),
                parcalar,
                n.path("createdAt").asText(OffsetDateTime.now().toString())
        );
    }
}
