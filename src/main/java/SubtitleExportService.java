/**
 * Alignment sonucundan SRT/VTT export.
 */
public final class SubtitleExportService {
    private SubtitleExportService() {
    }

    public static String srt(AlignmentResult sonuc) {
        return SrtWriter.yaz(sonuc);
    }

    public static String vtt(AlignmentResult sonuc) {
        return VttWriter.yaz(sonuc);
    }

    public static void dogrula(AlignmentResult sonuc) {
        if (sonuc.segments() == null) {
            throw new IllegalStateException("Segment listesi yok");
        }
        double onceki = -1;
        for (AlignmentSegment s : sonuc.segments()) {
            if (s.text() == null || s.text().isBlank()) {
                throw new IllegalStateException("Boş segment");
            }
            if (s.startSeconds() < 0 || s.endSeconds() < s.startSeconds()) {
                throw new IllegalStateException("Geçersiz segment süresi");
            }
            if (s.startSeconds() < onceki) {
                throw new IllegalStateException("Segment süreleri artan sırada değil");
            }
            onceki = s.startSeconds();
        }
    }
}
