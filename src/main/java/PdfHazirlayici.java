import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;

public final class PdfHazirlayici {
    private static final int MINIMUM_METIN_UZUNLUGU = 120;
    private static final long MAKSIMUM_INLINE_PDF_BOYUTU = 14L * 1024L * 1024L;

    private PdfHazirlayici() {
    }

    public static int toplamSayfaSayisi(File pdfDosyasi) throws Exception {
        try (PDDocument belge = PDDocument.load(pdfDosyasi)) {
            return belge.getNumberOfPages();
        }
    }

    public static PdfVerisi hazirla(File pdfDosyasi, int maksimumSayfaSayisi) throws Exception {
        try (PDDocument kaynak = PDDocument.load(pdfDosyasi)) {
            int toplamSayfa = kaynak.getNumberOfPages();
            if (toplamSayfa <= 0) {
                throw new IllegalArgumentException("PDF içerisinde sayfa bulunamadı: " + pdfDosyasi.getName());
            }

            int okunacakSayfa = Math.min(Math.max(maksimumSayfaSayisi, 1), toplamSayfa);

            PDFTextStripper okuyucu = new PDFTextStripper();
            okuyucu.setStartPage(1);
            okuyucu.setEndPage(okunacakSayfa);

            String metin = okuyucu.getText(kaynak);
            String temizMetin = metin == null ? "" : metin.replaceAll("\\s+", " ").trim();

            if (temizMetin.length() >= MINIMUM_METIN_UZUNLUGU) {
                return PdfVerisi.metin(temizMetin, okunacakSayfa, toplamSayfa);
            }

            try (PDDocument ilkSayfalar = new PDDocument();
                 ByteArrayOutputStream cikti = new ByteArrayOutputStream()) {

                for (int i = 0; i < okunacakSayfa; i++) {
                    ilkSayfalar.importPage(kaynak.getPage(i));
                }

                ilkSayfalar.save(cikti);
                byte[] pdfBaytlari = cikti.toByteArray();

                if (pdfBaytlari.length > MAKSIMUM_INLINE_PDF_BOYUTU) {
                    throw new IllegalArgumentException(
                            "İlk sayfalar yapay zekâ servisine inline gönderilemeyecek kadar büyük (" +
                                    (pdfBaytlari.length / 1024 / 1024) + " MB)."
                    );
                }

                String base64 = Base64.getEncoder().encodeToString(pdfBaytlari);
                return PdfVerisi.gorselPdf(base64, okunacakSayfa, toplamSayfa);
            }
        }
    }

    public static final class PdfVerisi {
        private final String metin;
        private final String base64Pdf;
        private final boolean gorselPdf;
        private final int incelenenSayfaSayisi;
        private final int toplamSayfaSayisi;
        private final String belgeTuru;
        private final String birimAdi;

        private PdfVerisi(String metin, String base64Pdf, boolean gorselPdf,
                          int incelenenSayfaSayisi, int toplamSayfaSayisi,
                          String belgeTuru, String birimAdi) {
            this.metin = metin;
            this.base64Pdf = base64Pdf;
            this.gorselPdf = gorselPdf;
            this.incelenenSayfaSayisi = incelenenSayfaSayisi;
            this.toplamSayfaSayisi = toplamSayfaSayisi;
            this.belgeTuru = belgeTuru;
            this.birimAdi = birimAdi;
        }

        private static PdfVerisi metin(String metin, int incelenenSayfaSayisi, int toplamSayfaSayisi) {
            return new PdfVerisi(metin, null, false, incelenenSayfaSayisi, toplamSayfaSayisi, "PDF", "sayfa");
        }

        private static PdfVerisi gorselPdf(String base64Pdf, int incelenenSayfaSayisi, int toplamSayfaSayisi) {
            return new PdfVerisi(null, base64Pdf, true, incelenenSayfaSayisi, toplamSayfaSayisi, "PDF", "sayfa");
        }


        public static PdfVerisi metinBelgesi(String metin, String belgeTuru, String birimAdi,
                                             int incelenenBirimSayisi, int toplamBirimSayisi) {
            if (metin == null || metin.isBlank()) {
                throw new IllegalArgumentException("Analiz için metin bulunamadı.");
            }
            String tur = belgeTuru == null || belgeTuru.isBlank() ? "Belge" : belgeTuru.trim();
            String birim = birimAdi == null || birimAdi.isBlank() ? "bölüm" : birimAdi.trim();
            return new PdfVerisi(metin.trim(), null, false,
                    Math.max(incelenenBirimSayisi, 1), Math.max(toplamBirimSayisi, incelenenBirimSayisi),
                    tur, birim);
        }

        public String getMetin() {
            return metin;
        }

        public String getBase64Pdf() {
            return base64Pdf;
        }

        public boolean isGorselPdf() {
            return gorselPdf;
        }

        public int getSayfaSayisi() {
            return incelenenSayfaSayisi;
        }

        public int getToplamSayfaSayisi() {
            return toplamSayfaSayisi;
        }

        public String getBelgeTuru() {
            return belgeTuru;
        }

        public String getBirimAdi() {
            return birimAdi;
        }
    }
}
