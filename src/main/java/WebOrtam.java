import java.nio.file.Path;

/**
 * Web paneli için ortam yolları ve sabitler.
 */
public final class WebOrtam {
    public static final String HOST = "127.0.0.1";
    public static final int PORT = 8787;

    private final Path projeKlasoru;
    private final Path gelenKlasoru;
    private final Path arsiv;
    private final Path metinArsivi;
    private final Path sesArsivi;
    private final Path kuyruk;
    private final Path katalog;
    private final Path kalitePanel;

    public WebOrtam(Path projeKlasoru) {
        this(projeKlasoru, null, null, null, null, null, null, null);
    }

    /** Test ve özelleştirilmiş kurulumlar için; null alanlar varsayılan masaüstü yollarını kullanır. */
    public WebOrtam(Path projeKlasoru, Path gelenKlasoru, Path arsiv, Path metinArsivi,
                    Path sesArsivi, Path kuyruk, Path katalog, Path kalitePanel) {
        this.projeKlasoru = projeKlasoru.toAbsolutePath().normalize();
        Path masaustu = Path.of(System.getProperty("user.home"), "Desktop");
        this.gelenKlasoru = gelenKlasoru != null ? gelenKlasoru
                : ortamYolu("ESER_GELEN_KLASORU", masaustu.resolve("gelen-eser"));
        this.arsiv = arsiv != null ? arsiv : ortamYolu("ESER_ARSIVI", masaustu.resolve("arsiv"));
        this.metinArsivi = metinArsivi != null ? metinArsivi
                : ortamYolu("ESER_METIN_ARSIVI", masaustu.resolve("metin-arsivi"));
        this.sesArsivi = sesArsivi != null ? sesArsivi
                : ortamYolu("ESER_SES_ARSIVI", masaustu.resolve("ses-arsivi"));
        this.kuyruk = kuyruk != null ? kuyruk : ortamYolu("ESER_URETIM_KUYRUGU", masaustu.resolve("eser-otomasyon-kuyruk"));
        this.katalog = katalog != null ? katalog : ortamYolu("ESER_KATALOGU", masaustu.resolve("eser-katalogu.xlsx"));
        this.kalitePanel = kalitePanel != null ? kalitePanel
                : ortamYolu("SES_KALITE_PANEL", masaustu.resolve("ses-arsivi_kalite-panel"));
    }

    public static WebOrtam varsayilan() {
        return new WebOrtam(Path.of(System.getProperty("user.dir")));
    }

    private static Path ortamYolu(String ad, Path varsayilan) {
        String deger = System.getenv(ad);
        return deger == null || deger.isBlank() ? varsayilan : Path.of(deger.trim()).toAbsolutePath().normalize();
    }

    public Path projeKlasoru() { return projeKlasoru; }
    public Path gelenKlasoru() { return gelenKlasoru; }
    public Path arsiv() { return arsiv; }
    public Path metinArsivi() { return metinArsivi; }
    public Path sesArsivi() { return sesArsivi; }
    public Path kuyruk() { return kuyruk; }
    public Path katalog() { return katalog; }
    public Path kalitePanel() { return kalitePanel; }
}
