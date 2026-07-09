/**
 * WebVTT altyazı yazıcı.
 */
public final class VttWriter {
    private VttWriter() {
    }

    public static String yaz(AlignmentResult sonuc) {
        if (sonuc.segments() == null || sonuc.segments().isEmpty()) {
            return "WEBVTT\n\n";
        }
        StringBuilder sb = new StringBuilder("WEBVTT\n\n");
        for (AlignmentSegment s : sonuc.segments()) {
            if (s.text() == null || s.text().isBlank()) {
                continue;
            }
            sb.append(zaman(s.startSeconds())).append(" --> ").append(zaman(s.endSeconds())).append('\n');
            sb.append(s.text().replaceAll("\\s+", " ").trim()).append("\n\n");
        }
        return sb.toString();
    }

    private static String zaman(double saniye) {
        if (saniye < 0) {
            saniye = 0;
        }
        int ms = (int) Math.round((saniye - Math.floor(saniye)) * 1000);
        int toplam = (int) Math.floor(saniye);
        int s = toplam % 60;
        int m = (toplam / 60) % 60;
        int h = toplam / 3600;
        return String.format("%02d:%02d:%02d.%03d", h, m, s, ms);
    }
}
