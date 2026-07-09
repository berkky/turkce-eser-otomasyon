import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic mock forced alignment — gerçek API'ye gitmez.
 */
public final class AlignmentMockService {
    private static final double CHARS_PER_SECOND = 14.5;
    private static final Pattern CUMLE = Pattern.compile("[^.!?]+[.!?]+|[^.!?]+$");

    private AlignmentMockService() {
    }

    public static AlignmentResult uret(AlignmentStorageService.PreviewKaynaklari kaynak,
                                       String textHash,
                                       String audioHash,
                                       double audioDurationSeconds) {
        return uret(kaynak, textHash, audioHash, audioDurationSeconds, false);
    }

    public static AlignmentResult uret(AlignmentStorageService.PreviewKaynaklari kaynak,
                                       String textHash,
                                       String audioHash,
                                       double audioDurationSeconds,
                                       boolean demoFixture) {
        String metin = kaynak.metin().replaceAll("\\s+", " ").trim();
        List<String> cumleler = cumlelereBol(metin);
        if (cumleler.isEmpty()) {
            throw new IllegalStateException("Segment üretilemedi — metin boş.");
        }

        double toplamSure = audioDurationSeconds > 0
                ? audioDurationSeconds
                : Math.max(5.0, metin.length() / CHARS_PER_SECOND);

        List<AlignmentSegment> segmentler = new ArrayList<>();
        int kelimeToplam = 0;
        double cursor = 0;
        int segIdx = 0;
        int toplamKarakter = cumleler.stream().mapToInt(String::length).sum();

        for (String cumle : cumleler) {
            String temiz = cumle.trim();
            if (temiz.isBlank()) {
                continue;
            }
            double oran = temiz.length() / (double) Math.max(1, toplamKarakter);
            double segSure = Math.max(0.4, toplamSure * oran);
            double baslangic = cursor;
            double bitis = Math.min(toplamSure, baslangic + segSure);
            List<AlignmentWord> kelimeler = kelimeleriZamanla(temiz, baslangic, bitis);
            kelimeToplam += kelimeler.size();
            segmentler.add(new AlignmentSegment(segIdx++, temiz, baslangic, bitis, kelimeler));
            cursor = bitis;
        }

        String outputName = AlignmentGuvenlikService.guvenliDosyaAdi(kaynak.eserId(), "alignment.json");
        List<String> uyarilar = new ArrayList<>();
        uyarilar.add("Mock alignment — gerçek ElevenLabs forced alignment değildir.");
        if (demoFixture) {
            uyarilar.add("Demo fixture — gerçek üretim önizlemesi kullanılmadı.");
        }
        return new AlignmentResult(
                kaynak.eserId(),
                kaynak.previewId(),
                AlignmentPlan.STATUS_COMPLETED,
                "MOCK",
                "tr",
                textHash,
                audioHash,
                toplamSure,
                kelimeToplam,
                segmentler.size(),
                false,
                true,
                segmentler,
                uyarilar,
                "MOCK",
                AlignmentPlan.STATUS_COMPLETED,
                outputName,
                AlignmentGuvenlikService.guvenliDosyaAdi(kaynak.eserId(), "subtitles.srt"),
                AlignmentGuvenlikService.guvenliDosyaAdi(kaynak.eserId(), "subtitles.vtt"),
                demoFixture,
                java.time.OffsetDateTime.now().toString());
    }

    private static List<String> cumlelereBol(String metin) {
        List<String> liste = new ArrayList<>();
        Matcher m = CUMLE.matcher(metin);
        while (m.find()) {
            String parca = m.group().trim();
            if (!parca.isBlank()) {
                liste.add(parca);
            }
        }
        if (liste.isEmpty() && !metin.isBlank()) {
            liste.add(metin);
        }
        return liste;
    }

    private static List<AlignmentWord> kelimeleriZamanla(String cumle, double baslangic, double bitis) {
        String[] parcalar = cumle.split("\\s+");
        List<AlignmentWord> liste = new ArrayList<>();
        if (parcalar.length == 0) {
            return liste;
        }
        double sure = Math.max(0.05, bitis - baslangic);
        double adim = sure / parcalar.length;
        double t = baslangic;
        for (int i = 0; i < parcalar.length; i++) {
            String kelime = parcalar[i].trim();
            if (kelime.isBlank()) {
                continue;
            }
            double son = i == parcalar.length - 1 ? bitis : t + adim;
            liste.add(new AlignmentWord(i, kelime, t, son));
            t = son;
        }
        return liste;
    }

    public static double tahminiSure(Path mp3, String metin) throws Exception {
        long boyut = java.nio.file.Files.size(mp3);
        double boyutTahmini = boyut / 4000.0;
        double metinTahmini = metin.length() / CHARS_PER_SECOND;
        return Math.max(3.0, Math.max(boyutTahmini, metinTahmini));
    }
}
