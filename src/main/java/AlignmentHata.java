/**
 * Forced alignment güvenli hata modeli — stack trace kullanıcıya gösterilmez.
 */
public record AlignmentHata(
        String hataKodu,
        String kullaniciMesaji,
        boolean teknikMesajGizliMi,
        boolean retryable,
        int providerStatusCode
) {
    public static AlignmentHata apiKeyYok() {
        return new AlignmentHata("API_KEY_YOK", "API anahtarı yok.", true, false, 0);
    }

    public static AlignmentHata mockModAktif() {
        return new AlignmentHata("MOCK_MOD", "Mock mod aktif — gerçek alignment API çağrılamaz.", true, false, 0);
    }

    public static AlignmentHata onizlemeYok() {
        return new AlignmentHata("ONIZLEME_YOK",
                "Gerçek alignment için önce ElevenLabs önizleme üretin.", true, false, 0);
    }

    public static AlignmentHata metinYok() {
        return new AlignmentHata("METIN_YOK", "Alignment metni bulunamadı.", true, false, 0);
    }

    public static AlignmentHata krediYok() {
        return new AlignmentHata("KREDI_YOK",
                "ElevenLabs kredisi yok veya API erişimi başarısız.", true, true, 402);
    }

    public static AlignmentHata yetki(int kod) {
        return new AlignmentHata("YETKI",
                "ElevenLabs yetki/API anahtarı problemi.", true, false, kod);
    }

    public static AlignmentHata rateLimit(int kod) {
        return new AlignmentHata("RATE_LIMIT",
                "ElevenLabs kredi veya rate limit problemi.", true, true, kod);
    }

    public static AlignmentHata girdi(int kod) {
        return new AlignmentHata("GIRDI",
                "Alignment girdisi geçersiz (ses veya metin).", true, false, kod);
    }

    public static AlignmentHata saglayici(int kod) {
        return new AlignmentHata("SAGLAYICI",
                "ElevenLabs sağlayıcı hatası — daha sonra tekrar deneyin.", true, true, kod);
    }

    public static AlignmentHata parse() {
        return new AlignmentHata("PARSE",
                "Alignment yanıtı işlenemedi.", true, false, 0);
    }

    public static AlignmentHata onayGerekli() {
        return new AlignmentHata("ONAY_GEREKLI",
                "Gerçek API yalnızca -GercekApiOnayli ile çalıştırılabilir.", true, false, 0);
    }

    public static AlignmentHata http(int kod, String guvenliMesaj) {
        if (kod == 401 || kod == 403) {
            return yetki(kod);
        }
        if (kod == 402 || kod == 429) {
            return rateLimit(kod);
        }
        if (kod == 400 || kod == 422) {
            return girdi(kod);
        }
        if (kod >= 500) {
            return saglayici(kod);
        }
        return new AlignmentHata("HTTP", guvenliMesaj, true, kod >= 500, kod);
    }
}
