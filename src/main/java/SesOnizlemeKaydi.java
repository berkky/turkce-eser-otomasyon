import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Tek bir ses önizleme kaydı.
 */
public record SesOnizlemeKaydi(
        int eserId,
        String eserAdi,
        String provider,
        String modelId,
        String voiceIdMasked,
        String voiceName,
        String inputTextPath,
        String audioPath,
        String metadataJsonPath,
        int characterCount,
        Double durationSeconds,
        long fileSizeBytes,
        String generatedAt,
        String requestHash,
        boolean mock,
        String status,
        String errorMessage,
        long estimatedFullWorkCharacterCost,
        String estimatedFullWorkCreditRisk,
        int ttsParcaSayisi,
        boolean buyukEser,
        String previewMetin,
        String normalizationWarnings
) {
    public ObjectNode jsonOzeti(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        ObjectNode n = mapper.createObjectNode();
        n.put("eserId", eserId);
        n.put("eserAdi", eserAdi);
        n.put("provider", provider);
        n.put("modelId", modelId == null ? "" : modelId);
        n.put("voiceIdMasked", voiceIdMasked == null ? "" : voiceIdMasked);
        n.put("voiceName", voiceName == null ? "" : voiceName);
        n.put("inputTextPath", inputTextPath == null ? "" : inputTextPath);
        n.put("audioPath", audioPath == null ? "" : audioPath);
        n.put("metadataJsonPath", metadataJsonPath == null ? "" : metadataJsonPath);
        n.put("characterCount", characterCount);
        if (durationSeconds != null) {
            n.put("durationSeconds", durationSeconds);
        }
        n.put("fileSizeBytes", fileSizeBytes);
        n.put("generatedAt", generatedAt == null ? "" : generatedAt);
        n.put("requestHash", requestHash == null ? "" : requestHash);
        n.put("mock", mock);
        n.put("status", status);
        n.put("errorMessage", errorMessage == null ? "" : errorMessage);
        n.put("estimatedFullWorkCharacterCost", estimatedFullWorkCharacterCost);
        n.put("estimatedFullWorkCreditRisk", estimatedFullWorkCreditRisk);
        n.put("ttsParcaSayisi", ttsParcaSayisi);
        n.put("buyukEser", buyukEser);
        n.put("previewMetin", previewMetin == null ? "" : previewMetin);
        n.put("normalizationWarnings", normalizationWarnings == null ? "" : normalizationWarnings);
        return n;
    }
}
