import java.util.Locale;

public enum UretimPolitikasi {
    OTOMATIK,
    GOOGLE_CHIRP,
    PIPER,
    KURU_KOSU;

    public static UretimPolitikasi oku(String deger) {
        if (deger == null || deger.isBlank()) return OTOMATIK;
        String temiz = deger.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (temiz) {
            case "AUTO", "OTOMATIK" -> OTOMATIK;
            case "GOOGLE", "GOOGLE_CHIRP", "CHIRP" -> GOOGLE_CHIRP;
            case "PIPER", "YEREL" -> PIPER;
            case "DRY_RUN", "KURU_KOSU", "PLAN" -> KURU_KOSU;
            default -> throw new IllegalArgumentException("Bilinmeyen üretim politikası: " + deger);
        };
    }
}
