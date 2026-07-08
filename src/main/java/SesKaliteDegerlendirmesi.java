import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * İnsan tarafından verilen kalite puanları.
 */
public record SesKaliteDegerlendirmesi(
        String requestHash,
        String audioPath,
        int eserId,
        String provider,
        String modelId,
        Double humanScoreNaturalness,
        Double humanScorePronunciation,
        Double humanScoreEmotion,
        Double humanScorePacing,
        Double humanScoreOverall,
        String reviewerNote,
        Boolean recommendedForFullProduction,
        String pronunciationIssues,
        String updatedAt
) {
    public ObjectNode jsonOzeti(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        ObjectNode n = mapper.createObjectNode();
        n.put("requestHash", requestHash == null ? "" : requestHash);
        n.put("audioPath", audioPath == null ? "" : audioPath);
        n.put("eserId", eserId);
        n.put("provider", provider == null ? "" : provider);
        n.put("modelId", modelId == null ? "" : modelId);
        putNullable(n, "humanScoreNaturalness", humanScoreNaturalness);
        putNullable(n, "humanScorePronunciation", humanScorePronunciation);
        putNullable(n, "humanScoreEmotion", humanScoreEmotion);
        putNullable(n, "humanScorePacing", humanScorePacing);
        putNullable(n, "humanScoreOverall", humanScoreOverall);
        n.put("reviewerNote", reviewerNote == null ? "" : reviewerNote);
        if (recommendedForFullProduction != null) {
            n.put("recommendedForFullProduction", recommendedForFullProduction);
        }
        n.put("pronunciationIssues", pronunciationIssues == null ? "" : pronunciationIssues);
        n.put("updatedAt", updatedAt == null ? "" : updatedAt);
        return n;
    }

    private static void putNullable(ObjectNode n, String alan, Double deger) {
        if (deger != null) {
            n.put(alan, deger);
        }
    }

    public double ortalamaPuan() {
        double toplam = 0;
        int adet = 0;
        if (humanScoreNaturalness != null) { toplam += humanScoreNaturalness; adet++; }
        if (humanScorePronunciation != null) { toplam += humanScorePronunciation; adet++; }
        if (humanScoreEmotion != null) { toplam += humanScoreEmotion; adet++; }
        if (humanScorePacing != null) { toplam += humanScorePacing; adet++; }
        if (humanScoreOverall != null) { toplam += humanScoreOverall; adet++; }
        return adet == 0 ? 0.0 : toplam / adet;
    }
}
