import org.apache.pdfbox.pdmodel.PDDocument;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PdfOnizlemeService {
    private PdfOnizlemeService() {}

    public static int ilkSayfalariKaydet(Path kaynak, Path hedef, int maksimumSayfa) throws Exception {
        try (PDDocument ana = PDDocument.load(kaynak.toFile()); PDDocument onizleme = new PDDocument()) {
            int sayfa = Math.min(Math.max(1, maksimumSayfa), ana.getNumberOfPages());
            for (int i = 0; i < sayfa; i++) onizleme.importPage(ana.getPage(i));
            Files.createDirectories(hedef.getParent());
            onizleme.save(hedef.toFile());
            return sayfa;
        }
    }
}
