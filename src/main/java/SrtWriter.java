/**
 * SRT altyazı yazıcı.
 */
public final class SrtWriter {
    private SrtWriter() {
    }

    public static String yaz(AlignmentResult sonuc) {
        if (sonuc.segments() == null || sonuc.segments().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int no = 1;
        for (AlignmentSegment s : sonuc.segments()) {
            if (s.text() == null || s.text().isBlank()) {
                continue;
            }
            sb.append(no++).append('\n');
            sb.append(zaman(s.startSeconds())).append(" --> ").append(zaman(s.endSeconds())).append('\n');
            sb.append(kir(s.text())).append("\n\n");
        }
        return sb.toString().trim() + "\n";
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
        return String.format("%02d:%02d:%02d,%03d", h, m, s, ms);
    }

    private static String kir(String metin) {
        String temiz = metin.replaceAll("\\s+", " ").trim();
        if (temiz.length() <= 80) {
            return temiz;
        }
        int bosluk = temiz.lastIndexOf(' ', 80);
        return temiz.substring(0, bosluk > 40 ? bosluk : 80);
    }
}
