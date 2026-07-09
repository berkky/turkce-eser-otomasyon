/**
 * Forced alignment plan placeholder — canlı API Adım 29+.
 */
public record AlignmentPlan(
        String status,
        String outputPathHint,
        String aciklama
) {
    public static AlignmentPlan notRequested() {
        return new AlignmentPlan(
                "NOT_REQUESTED",
                "",
                "Adım 29 veya sonrasında forced alignment ile ses-metin hizalama yapılabilir."
        );
    }
}
