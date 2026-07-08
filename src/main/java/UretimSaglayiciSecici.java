import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class UretimSaglayiciSecici {
    private UretimSaglayiciSecici() {}

    public static Secim sec(Path projeKlasoru, UretimPolitikasi politika) {
        return switch (politika) {
            case KURU_KOSU -> new Secim(null, List.of(), "Kuru koşu");
            case PIPER -> tek(new PiperTtsSaglayici(projeKlasoru), "Piper zorunlu");
            case GOOGLE_CHIRP -> tek(new GoogleCloudTtsSaglayici(
                    ortam("URETIM_GOOGLE_TTS_VOICE", "tr-TR-Chirp3-HD-Kore")), "Google Cloud zorunlu");
            case OTOMATIK -> otomatik(
                    new GoogleCloudTtsSaglayici(ortam("URETIM_GOOGLE_TTS_VOICE", "tr-TR-Chirp3-HD-Kore")),
                    new PiperTtsSaglayici(projeKlasoru));
        };
    }

    private static Secim tek(TtsSaglayici s, String aciklama) {
        TtsSaglayici.Hazirlik h = guvenliHazirlik(s);
        if (!h.hazir()) throw new IllegalStateException(s.ad() + " hazır değil: " + h.mesaj());
        return new Secim(s, List.of(), aciklama);
    }

    private static Secim otomatik(TtsSaglayici google, TtsSaglayici piper) {
        List<String> notlar = new ArrayList<>();
        TtsSaglayici.Hazirlik gh = guvenliHazirlik(google);
        if (gh.hazir()) {
            TtsSaglayici.Hazirlik ph = guvenliHazirlik(piper);
            if (ph.hazir()) return new Secim(google, List.of(piper), "Google ana, Piper güvenli yedek");
            notlar.add("Piper yedeği kapalı: " + ph.mesaj());
            return new Secim(google, List.of(), "Google ana; yerel yedek yok");
        }
        notlar.add("Google kapalı: " + gh.mesaj());
        TtsSaglayici.Hazirlik ph = guvenliHazirlik(piper);
        if (ph.hazir()) return new Secim(piper, List.of(), "Google kullanılamadı, Piper seçildi");
        throw new IllegalStateException(String.join(" | ", notlar) + " | Piper kapalı: " + ph.mesaj());
    }

    private static TtsSaglayici.Hazirlik guvenliHazirlik(TtsSaglayici s) {
        try { return s.hazirlik(); }
        catch (Exception e) { return TtsSaglayici.Hazirlik.degil(e.getMessage()); }
    }

    private static String ortam(String ad, String varsayilan) {
        String v = System.getenv(ad);
        return v == null || v.isBlank() ? varsayilan : v.trim();
    }

    public record Secim(TtsSaglayici ana, List<TtsSaglayici> yedekler, String aciklama) {}
}
