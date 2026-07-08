import java.util.Locale;

public final class UretimMaliyetKoruyucu {
    private UretimMaliyetKoruyucu() {}

    public static long bulutKarakterSiniri() {
        String v = System.getenv("URETIM_BULUT_KARAKTER_LIMITI");
        if (v == null || v.isBlank()) return 100_000L;
        try { return Math.max(1L, Long.parseLong(v.trim())); }
        catch (Exception e) { throw new IllegalArgumentException("URETIM_BULUT_KARAKTER_LIMITI sayı olmalı."); }
    }

    public static Plan plan(long toplamKarakter, long eksikKarakter, boolean bulut) {
        long limit = bulut ? bulutKarakterSiniri() : Long.MAX_VALUE;
        boolean limitAsimi = bulut && eksikKarakter > limit;
        double tahminiSaat = eksikKarakter / 900.0 / 60.0;
        return new Plan(toplamKarakter, eksikKarakter, limit, limitAsimi, tahminiSaat);
    }

    public static String bicimle(long sayi) {
        return String.format(Locale.forLanguageTag("tr-TR"), "%,d", sayi);
    }

    public record Plan(long toplamKarakter, long eksikKarakter, long limit,
                       boolean limitAsimi, double tahminiSesSaati) {}
}
