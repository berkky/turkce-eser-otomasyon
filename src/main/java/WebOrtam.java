import java.nio.file.Path;
import java.util.List;

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
    private final List<String> pathWarnings;

    public WebOrtam(Path projeKlasoru) {
        this(projeKlasoru, null, null, null, null, null, null, null);
    }

    /** Test ve özelleştirilmiş kurulumlar için; null alanlar varsayılan masaüstü yollarını kullanır. */
    public WebOrtam(Path projeKlasoru, Path gelenKlasoru, Path arsiv, Path metinArsivi,
                    Path sesArsivi, Path kuyruk, Path katalog, Path kalitePanel) {
        this.projeKlasoru = projeKlasoru.toAbsolutePath().normalize();
        EserVeriYollari yollar = EserVeriYollari.varsayilan();
        this.gelenKlasoru = gelenKlasoru != null ? gelenKlasoru
                : yollar.gelen();
        this.arsiv = arsiv != null ? arsiv : yollar.arsiv();
        this.metinArsivi = metinArsivi != null ? metinArsivi
                : yollar.metin();
        this.sesArsivi = sesArsivi != null ? sesArsivi
                : yollar.ses();
        this.kuyruk = kuyruk != null ? kuyruk : yollar.kuyruk();
        this.katalog = katalog != null ? katalog : yollar.katalog();
        this.kalitePanel = kalitePanel != null ? kalitePanel
                : yollar.kalitePanel();
        this.pathWarnings = gelenKlasoru == null && arsiv == null && metinArsivi == null
                && sesArsivi == null && kuyruk == null && katalog == null
                ? yollar.warnings() : List.of();
    }

    public static WebOrtam varsayilan() {
        return new WebOrtam(Path.of(System.getProperty("user.dir")));
    }

    public Path projeKlasoru() { return projeKlasoru; }
    public Path gelenKlasoru() { return gelenKlasoru; }
    public Path arsiv() { return arsiv; }
    public Path metinArsivi() { return metinArsivi; }
    public Path sesArsivi() { return sesArsivi; }
    public Path kuyruk() { return kuyruk; }
    public Path katalog() { return katalog; }
    public Path kalitePanel() { return kalitePanel; }
    public List<String> pathWarnings() { return pathWarnings; }
}
