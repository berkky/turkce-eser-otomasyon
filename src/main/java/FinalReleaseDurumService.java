import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

/**
 * Final release raporu salt okunur durum — web bilgilendirme için.
 */
public final class FinalReleaseDurumService {
    public static final String RAPOR_KLASOR_ADI = "turkce-eser-final-release";
    public static final String RAPOR_DOSYA = "final-release-report.json";

    private static final ObjectMapper JSON = new ObjectMapper();

    private FinalReleaseDurumService() {
    }

    public static Path raporKlasoru() {
        return Path.of(System.getProperty("user.home"), "Desktop", RAPOR_KLASOR_ADI);
    }

    public static Path raporDosyasi() {
        return raporKlasoru().resolve(RAPOR_DOSYA);
    }

    public static FinalDurum oku() {
        try {
            Path dosya = raporDosyasi();
            if (!Files.isRegularFile(dosya)) {
                return varsayilan("Final release raporu henüz oluşturulmadı. "
                        + "Komut: final-release-check.ps1");
            }
            JsonNode n = JSON.readTree(Files.readString(dosya, StandardCharsets.UTF_8));
            boolean basarili = "BASARILI".equalsIgnoreCase(n.path("result").asText(""));
            return new FinalDurum(
                    basarili,
                    n.path("buildPassed").asBoolean(false),
                    n.path("regressionPassed").asBoolean(false),
                    n.path("secretScanPassed").asBoolean(false),
                    n.path("demoPackagePassed").asBoolean(false),
                    n.path("gitStatusClean").asBoolean(false),
                    n.path("version").asText("32.0.0"),
                    n.path("commitHash").asText(""),
                    n.path("timestamp").asText(""),
                    basarili
                            ? "Final kalite kapısı geçti — teslim hazırlığı tamam."
                            : "Son kontrol başarısız veya eksik — final-release-check.ps1 çalıştırın."
            );
        } catch (Exception e) {
            return varsayilan("Rapor okunamadı: " + e.getMessage());
        }
    }

    private static FinalDurum varsayilan(String mesaj) {
        return new FinalDurum(false, false, false, false, false, false,
                "32.0.0", "", OffsetDateTime.now().toString(), mesaj);
    }

    public record FinalDurum(
            boolean raporMevcutVeBasarili,
            boolean buildPassed,
            boolean regressionPassed,
            boolean secretScanPassed,
            boolean demoPackagePassed,
            boolean gitStatusClean,
            String version,
            String commitHash,
            String timestamp,
            String mesaj
    ) {
    }
}
