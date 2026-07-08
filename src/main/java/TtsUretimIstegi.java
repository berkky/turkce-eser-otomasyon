public record TtsUretimIstegi(String ornekId,
                              String metinTuru,
                              String metin,
                              String yonerge) {
    public TtsUretimIstegi {
        if (ornekId == null || ornekId.isBlank()) {
            throw new IllegalArgumentException("Örnek kimliği boş olamaz.");
        }
        if (metin == null || metin.isBlank()) {
            throw new IllegalArgumentException("Seslendirilecek metin boş olamaz.");
        }
        metinTuru = metinTuru == null || metinTuru.isBlank() ? "Bilinmiyor" : metinTuru.trim();
        yonerge = yonerge == null ? "" : yonerge.trim();
    }
}
