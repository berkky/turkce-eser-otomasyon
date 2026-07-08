import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class TtsPuanlamaExcelService {
    private TtsPuanlamaExcelService() {
    }

    public static Path olustur(Path hedef,
                               List<KorKayit> kayitlar,
                               List<TtsUretimIstegi> ornekler) throws Exception {
        Files.createDirectories(hedef.toAbsolutePath().getParent());
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle baslik = baslikStili(wb);
            CellStyle ortali = ortaliStil(wb);

            XSSFSheet puan = wb.createSheet("Puanlama");
            String[] basliklar = {"Kod", "Metin Türü", "Doğallık (1-10)", "Türkçe Telaffuz (1-10)",
                    "Vurgu-Ritim (1-10)", "Duygu Uygunluğu (1-10)", "Uzun Dinleme (1-10)",
                    "Genel (1-10)", "Not"};
            Row hr = puan.createRow(0);
            for (int i = 0; i < basliklar.length; i++) {
                Cell c = hr.createCell(i); c.setCellValue(basliklar[i]); c.setCellStyle(baslik);
            }
            int r = 1;
            for (KorKayit k : kayitlar) {
                Row row = puan.createRow(r++);
                row.createCell(0).setCellValue(k.kod());
                row.createCell(1).setCellValue(k.sonuc().metinTuru());
                for (int c = 2; c <= 7; c++) row.createCell(c).setCellStyle(ortali);
                row.createCell(8).setCellValue("");
            }
            puan.createFreezePane(0, 1);
            puan.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(1, r - 1), 0, 8));
            puan.setColumnWidth(0, 14 * 256); puan.setColumnWidth(1, 25 * 256);
            for (int i = 2; i <= 7; i++) puan.setColumnWidth(i, 22 * 256);
            puan.setColumnWidth(8, 55 * 256);
            if (r > 1) {
                DataValidationHelper helper = new XSSFDataValidationHelper(puan);
                DataValidationConstraint constraint = helper.createIntegerConstraint(
                        DataValidationConstraint.OperatorType.BETWEEN, "1", "10");
                CellRangeAddressList regions = new CellRangeAddressList(1, r - 1, 2, 7);
                DataValidation validation = helper.createValidation(constraint, regions);
                validation.setShowErrorBox(true);
                validation.createErrorBox("Geçersiz puan", "1 ile 10 arasında tam sayı gir.");
                puan.addValidationData(validation);
            }

            Sheet metin = wb.createSheet("Metinler");
            Row mh = metin.createRow(0);
            String[] mb = {"Örnek", "Tür", "Metin"};
            for (int i = 0; i < mb.length; i++) { Cell c = mh.createCell(i); c.setCellValue(mb[i]); c.setCellStyle(baslik); }
            int mr = 1;
            for (TtsUretimIstegi o : ornekler) {
                Row row = metin.createRow(mr++);
                row.createCell(0).setCellValue(o.ornekId());
                row.createCell(1).setCellValue(o.metinTuru());
                row.createCell(2).setCellValue(o.metin());
            }
            metin.setColumnWidth(0, 25 * 256); metin.setColumnWidth(1, 25 * 256); metin.setColumnWidth(2, 100 * 256);

            Sheet anahtar = wb.createSheet("Cevap Anahtari");
            String[] ab = {"Kod", "Sağlayıcı Kimliği", "Sağlayıcı", "Model", "Ses", "Örnek", "Ham Dosya", "Karakter", "Süre ms", "Boyut"};
            Row ah = anahtar.createRow(0);
            for (int i = 0; i < ab.length; i++) { Cell c = ah.createCell(i); c.setCellValue(ab[i]); c.setCellStyle(baslik); }
            int ar = 1;
            for (KorKayit k : kayitlar) {
                TtsUretimSonucu s = k.sonuc();
                Row row = anahtar.createRow(ar++);
                row.createCell(0).setCellValue(k.kod());
                row.createCell(1).setCellValue(s.saglayiciKimligi());
                row.createCell(2).setCellValue(s.saglayiciAdi());
                row.createCell(3).setCellValue(s.model());
                row.createCell(4).setCellValue(s.ses());
                row.createCell(5).setCellValue(s.ornekId());
                row.createCell(6).setCellValue(s.sesDosyasi().toString());
                row.createCell(7).setCellValue(s.karakter());
                row.createCell(8).setCellValue(s.sureMs());
                row.createCell(9).setCellValue(s.dosyaBoyutu());
            }
            wb.setSheetVisibility(wb.getSheetIndex(anahtar), SheetVisibility.VERY_HIDDEN);

            try (OutputStream out = Files.newOutputStream(hedef)) { wb.write(out); }
        }
        return hedef;
    }

    public static List<Siralama> siralamaOku(Path excel) throws Exception {
        try (InputStream in = Files.newInputStream(excel); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet puan = wb.getSheet("Puanlama");
            Sheet anahtar = wb.getSheet("Cevap Anahtari");
            if (puan == null || anahtar == null) throw new IllegalArgumentException("Geçerli laboratuvar Excel'i değil.");
            Map<String, String[]> map = new HashMap<>();
            for (int r = 1; r <= anahtar.getLastRowNum(); r++) {
                Row row = anahtar.getRow(r); if (row == null) continue;
                map.put(metin(row.getCell(0)), new String[]{metin(row.getCell(1)), metin(row.getCell(2)), metin(row.getCell(3)), metin(row.getCell(4))});
            }
            Map<String, Toplam> toplamlar = new LinkedHashMap<>();
            for (int r = 1; r <= puan.getLastRowNum(); r++) {
                Row row = puan.getRow(r); if (row == null) continue;
                String kod = metin(row.getCell(0));
                String[] bilgi = map.get(kod); if (bilgi == null) continue;
                double toplam = 0; int adet = 0;
                for (int c = 2; c <= 7; c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                        double v = cell.getNumericCellValue();
                        if (v >= 1 && v <= 10) { toplam += v; adet++; }
                    }
                }
                if (adet == 0) continue;
                Toplam t = toplamlar.computeIfAbsent(bilgi[0], x -> new Toplam(bilgi[1], bilgi[2], bilgi[3]));
                t.puan += toplam / adet; t.ornek++;
            }
            List<Siralama> sonuc = new ArrayList<>();
            for (var e : toplamlar.entrySet()) {
                Toplam t = e.getValue();
                sonuc.add(new Siralama(e.getKey(), t.ad, t.model, t.ses, t.ornek == 0 ? 0 : t.puan / t.ornek, t.ornek));
            }
            sonuc.sort(Comparator.comparingDouble(Siralama::ortalama).reversed());
            return sonuc;
        }
    }

    private static String metin(Cell c) {
        if (c == null) return "";
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue().trim();
            case NUMERIC -> String.valueOf(c.getNumericCellValue());
            default -> c.toString().trim();
        };
    }

    private static CellStyle baslikStili(Workbook wb) {
        Font f = wb.createFont(); f.setBold(true);
        CellStyle s = wb.createCellStyle(); s.setFont(f); s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER); s.setWrapText(true);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private static CellStyle ortaliStil(Workbook wb) {
        CellStyle s = wb.createCellStyle(); s.setAlignment(HorizontalAlignment.CENTER); return s;
    }

    private static final class Toplam {
        final String ad, model, ses; double puan; int ornek;
        Toplam(String ad, String model, String ses) { this.ad = ad; this.model = model; this.ses = ses; }
    }

    public record KorKayit(String kod, TtsUretimSonucu sonuc, Path korDosya) {}
    public record Siralama(String kimlik, String ad, String model, String ses, double ortalama, int puanlananOrnek) {}
}
