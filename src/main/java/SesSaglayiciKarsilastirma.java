import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Sağlayıcı/model bazlı özet karşılaştırma.
 */
public record SesSaglayiciKarsilastirma(
        String provider,
        String modelId,
        int gecerliPreviewSayisi,
        int mockPreviewSayisi,
        int gercekPreviewSayisi,
        int gecersizPreviewSayisi,
        double ortalamaInsanPuani,
        int puanliKayitSayisi,
        boolean onerilen,
        String not
) {
    public ObjectNode jsonOzeti(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        ObjectNode n = mapper.createObjectNode();
        n.put("provider", provider);
        n.put("modelId", modelId == null ? "" : modelId);
        n.put("gecerliPreviewSayisi", gecerliPreviewSayisi);
        n.put("mockPreviewSayisi", mockPreviewSayisi);
        n.put("gercekPreviewSayisi", gercekPreviewSayisi);
        n.put("gecersizPreviewSayisi", gecersizPreviewSayisi);
        n.put("ortalamaInsanPuani", ortalamaInsanPuani);
        n.put("puanliKayitSayisi", puanliKayitSayisi);
        n.put("onerilen", onerilen);
        n.put("not", not == null ? "" : not);
        return n;
    }
}
