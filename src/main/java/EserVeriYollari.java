import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tüm uygulama ve CLI bileşenleri için tek canonical veri yolu politikası.
 * Legacy Desktop yolları yalnız tespit edilir; hiçbir zaman fallback veya yazma hedefi olmaz.
 */
public final class EserVeriYollari {
    public static final String LEGACY_WARNING = "LEGACY_DATA_ROOT_DETECTED";

    private final Path canonicalRoot;
    private final Path legacyRoot;
    private final Path gelen;
    private final Path arsiv;
    private final Path metin;
    private final Path ses;
    private final Path kuyruk;
    private final Path katalog;
    private final Path kalitePanel;
    private final List<String> warnings;

    private EserVeriYollari(Path canonicalRoot, Path legacyRoot, Path gelen, Path arsiv,
                           Path metin, Path ses, Path kuyruk, Path katalog, Path kalitePanel,
                           List<String> warnings) {
        this.canonicalRoot = canonicalRoot;
        this.legacyRoot = legacyRoot;
        this.gelen = gelen;
        this.arsiv = arsiv;
        this.metin = metin;
        this.ses = ses;
        this.kuyruk = kuyruk;
        this.katalog = katalog;
        this.kalitePanel = kalitePanel;
        this.warnings = List.copyOf(warnings);
    }

    public static EserVeriYollari varsayilan() {
        Map<String, String> settings = new HashMap<>(System.getenv());
        for (String key : List.of("ESER_GELEN_KLASORU", "ESER_ARSIVI", "ESER_METIN_ARSIVI",
                "ESER_SES_ARSIVI", "ESER_URETIM_KUYRUGU", "ESER_KATALOGU", "SES_KALITE_PANEL")) {
            String property = System.getProperty(key);
            if (property != null && !property.isBlank()) {
                settings.putIfAbsent(key, property);
            }
        }
        return cozumle(Path.of(System.getProperty("user.home")), settings);
    }

    static EserVeriYollari cozumle(Path userHome, Map<String, String> environment) {
        Path desktop = userHome.toAbsolutePath().normalize().resolve("Desktop");
        Path canonical = desktop.resolve("ESER").normalize();
        List<String> warnings = new ArrayList<>();
        if (legacyDataExists(desktop)) {
            warnings.add(LEGACY_WARNING);
        }
        return new EserVeriYollari(
                canonical,
                desktop,
                resolveOverride("ESER_GELEN_KLASORU", canonical.resolve("gelen-eser"), environment, true, warnings),
                resolveOverride("ESER_ARSIVI", canonical.resolve("arsiv"), environment, true, warnings),
                resolveOverride("ESER_METIN_ARSIVI", canonical.resolve("metin-arsivi"), environment, true, warnings),
                resolveOverride("ESER_SES_ARSIVI", canonical.resolve("ses-arsivi"), environment, true, warnings),
                resolveOverride("ESER_URETIM_KUYRUGU", canonical.resolve("eser-otomasyon-kuyruk"), environment, true, warnings),
                resolveOverride("ESER_KATALOGU", canonical.resolve("eser-katalogu.xlsx"), environment, false, warnings),
                resolveOverride("SES_KALITE_PANEL", canonical.resolve("ses-arsivi_kalite-panel"), environment, true, warnings),
                warnings);
    }

    private static Path resolveOverride(String name, Path fallback, Map<String, String> environment,
                                        boolean directory, List<String> warnings) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            Path path = Path.of(value.trim());
            if (!path.isAbsolute()) {
                warnings.add("INVALID_PATH_OVERRIDE:" + name);
                return fallback;
            }
            path = path.normalize();
            if (Files.exists(path) && (directory ? !Files.isDirectory(path) : Files.isDirectory(path))) {
                warnings.add("INVALID_PATH_OVERRIDE:" + name);
                return fallback;
            }
            return path;
        } catch (InvalidPathException exception) {
            warnings.add("INVALID_PATH_OVERRIDE:" + name);
            return fallback;
        }
    }

    private static boolean legacyDataExists(Path desktop) {
        return Files.exists(desktop.resolve("eser-otomasyon-kuyruk"))
                || Files.exists(desktop.resolve("metin-arsivi"))
                || Files.exists(desktop.resolve("ses-arsivi"))
                || Files.exists(desktop.resolve("arsiv"));
    }

    public boolean legacyDetected() {
        return warnings.contains(LEGACY_WARNING);
    }

    public List<String> warnings() {
        return warnings;
    }

    public Path canonicalRoot() { return canonicalRoot; }
    public Path legacyRoot() { return legacyRoot; }
    public Path gelen() { return gelen; }
    public Path arsiv() { return arsiv; }
    public Path metin() { return metin; }
    public Path ses() { return ses; }
    public Path kuyruk() { return kuyruk; }
    public Path katalog() { return katalog; }
    public Path kalitePanel() { return kalitePanel; }
    public Path productionApprovals() { return kuyruk.resolve("production-approvals"); }
}
