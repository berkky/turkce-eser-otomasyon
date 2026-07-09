/**
 * Forced alignment sonuç placeholder — canlı API Adım 29+.
 */
public record AlignmentResult(
        String status,
        String outputPath,
        String aciklama
) {
    public static AlignmentResult notRequested() {
        return new AlignmentResult(
                "NOT_REQUESTED",
                "",
                "Adım 29 veya sonrasında forced alignment ile kelime zamanlaması üretilebilir."
        );
    }
}
