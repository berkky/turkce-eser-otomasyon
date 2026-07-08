import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExcelArsivService {
    private static final String SAYFA_ADI = "Eserler";
    private static final String ESKI_SAYFA_ADI = "Kitaplar";

    private static final int ID = 0;
    private static final int ORIJINAL_AD = 1;
    private static final int ESER_ADI = 2;
    private static final int ESER_TURU = 3;
    private static final int YAZAR = 4;
    private static final int YAYINEVI = 5;
    private static final int BASIM_YILI = 6;
    private static final int DIL = 7;
    private static final int SAYFA_SAYISI = 8;
    private static final int KAYNAK_URL = 9;
    private static final int LISANS = 10;
    private static final int BILGI_KAYNAGI = 11;
    private static final int AI_MODELI = 12;
    private static final int ARSIV_ADI = 13;
    private static final int ARSIV_YOLU = 14;
    private static final int DURUM = 15;
    private static final int HASH = 16;
    private static final int SESLENDIRME_DURUMU = 17;

    private static final String[] BASLIKLAR = {
            "ID",
            "Orijinal Dosya Adı",
            "Eser Adı",
            "Eser Türü",
            "Yazar",
            "Yayınevi",
            "Basım Yılı",
            "Dil",
            "Sayfa Sayısı",
            "Kaynak URL",
            "Lisans",
            "Bilgi Kaynağı",
            "Kullanılan AI Modeli",
            "Arşiv Dosya Adı",
            "Arşiv Yolu",
            "Durum",
            "Dosya Hash (SHA-256)",
            "Seslendirme Durumu"
    };

    private final Path excelYolu;

    public ExcelArsivService(Path excelYolu) {
        this.excelYolu = excelYolu;
    }

    public void sistemiHazirla() throws Exception {
        Workbook workbook = workbookAcVeyaOlustur();
        try (workbook) {
            Sheet sheet = eserSayfasiniHazirlaVeGerekirseDonustur(workbook);
            excelYaz(workbook, sheet);
        }
    }

    public Kayit hashIleBul(String dosyaHash) throws Exception {
        if (!gecerliHash(dosyaHash) || !excelVarMi()) {
            return null;
        }

        try (FileInputStream input = new FileInputStream(excelYolu.toFile());
             Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheet(SAYFA_ADI);
            if (sheet == null) {
                return null;
            }

            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String excelHash = oku(formatter, row, HASH);
                if (dosyaHash.equalsIgnoreCase(excelHash)) {
                    return satirdanOku(row, i, formatter);
                }
            }
        }
        return null;
    }

    public Kayit orijinalAdIleBul(String dosyaAdi) throws Exception {
        if (bos(dosyaAdi) || !excelVarMi()) {
            return null;
        }

        try (FileInputStream input = new FileInputStream(excelYolu.toFile());
             Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheet(SAYFA_ADI);
            if (sheet == null) {
                return null;
            }

            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                if (dosyaAdi.equalsIgnoreCase(oku(formatter, row, ORIJINAL_AD))) {
                    return satirdanOku(row, i, formatter);
                }
            }
        }
        return null;
    }

    public int siradakiId() throws Exception {
        if (!excelVarMi()) {
            return 1;
        }

        int enBuyuk = 0;
        try (FileInputStream input = new FileInputStream(excelYolu.toFile());
             Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheet(SAYFA_ADI);
            if (sheet == null) {
                return 1;
            }

            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    enBuyuk = Math.max(enBuyuk, sayiOku(formatter, row.getCell(ID)));
                }
            }
        }
        return enBuyuk + 1;
    }

    public void yeniKayitEkle(int id, String orijinalDosyaAdi, EserBilgisi bilgi,
                              Path arsivDosyasi, String hash) throws Exception {
        Workbook workbook = workbookAcVeyaOlustur();
        try (workbook) {
            Sheet sheet = eserSayfasiniHazirlaVeGerekirseDonustur(workbook);
            Row row = sheet.createRow(sheet.getLastRowNum() + 1);
            kaydiYaz(row, id, orijinalDosyaAdi, bilgi, arsivDosyasi, hash);
            excelYaz(workbook, sheet);
        }
    }

    public void arsivBilgisiniGuncelle(Kayit kayit, int id, Path arsivDosyasi, String hash) throws Exception {
        arsivBilgisiniGuncelle(kayit, id, arsivDosyasi, hash, null);
    }

    public void arsivBilgisiniGuncelle(Kayit kayit, int id, Path arsivDosyasi,
                                        String hash, KaynakBilgisi kaynak) throws Exception {
        Workbook workbook = workbookAcVeyaOlustur();
        try (workbook) {
            Sheet sheet = eserSayfasiniHazirlaVeGerekirseDonustur(workbook);
            Row row = sheet.getRow(kayit.satirNumarasi);
            if (row == null) {
                throw new IllegalStateException("Güncellenecek Excel satırı bulunamadı.");
            }

            sayiYaz(row, ID, id);
            metinYaz(row, ARSIV_ADI, arsivDosyasi.getFileName().toString());
            metinYaz(row, ARSIV_YOLU, arsivDosyasi.toAbsolutePath().toString());
            metinYaz(row, DURUM, "Arşivlendi");
            metinYaz(row, HASH, hash);

            if (kaynak != null) {
                if (doluKaynakDegeri(kaynak.kaynak_url)) {
                    metinYaz(row, KAYNAK_URL, kaynak.kaynak_url);
                }
                if (doluKaynakDegeri(kaynak.lisans)) {
                    metinYaz(row, LISANS, kaynak.lisans);
                }
            }

            excelYaz(workbook, sheet);
        }
    }

    public void hashGuncelle(Kayit kayit, String hash) throws Exception {
        if (kayit == null || !gecerliHash(hash)) {
            return;
        }

        Workbook workbook = workbookAcVeyaOlustur();
        try (workbook) {
            Sheet sheet = eserSayfasiniHazirlaVeGerekirseDonustur(workbook);
            Row row = sheet.getRow(kayit.satirNumarasi);
            if (row != null) {
                metinYaz(row, HASH, hash);
                excelYaz(workbook, sheet);
            }
        }
    }


    public void kaynakBilgisiniGuncelle(Kayit kayit, KaynakBilgisi kaynak) throws Exception {
        if (kayit == null || kaynak == null || !kaynak.varMi()) {
            return;
        }

        Workbook workbook = workbookAcVeyaOlustur();
        try (workbook) {
            Sheet sheet = eserSayfasiniHazirlaVeGerekirseDonustur(workbook);
            Row row = sheet.getRow(kayit.satirNumarasi);
            if (row == null) {
                return;
            }

            if (doluKaynakDegeri(kaynak.kaynak_url)) {
                metinYaz(row, KAYNAK_URL, kaynak.kaynak_url);
            }
            if (doluKaynakDegeri(kaynak.lisans)) {
                metinYaz(row, LISANS, kaynak.lisans);
            }
            excelYaz(workbook, sheet);
        }
    }

    public void eksikHashleriTamamla(Path arsivKlasoru) throws Exception {
        if (!excelVarMi()) {
            return;
        }

        Workbook workbook = workbookAcVeyaOlustur();
        int tamamlanan = 0;
        try (workbook) {
            Sheet sheet = eserSayfasiniHazirlaVeGerekirseDonustur(workbook);
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || gecerliHash(oku(formatter, row, HASH))) {
                    continue;
                }

                Path dosya = kayitliArsivDosyasiniBul(row, formatter, arsivKlasoru);
                if (dosya == null || !Files.exists(dosya)) {
                    continue;
                }

                metinYaz(row, HASH, DosyaArsivService.sha256(dosya));
                tamamlanan++;
            }

            if (tamamlanan > 0) {
                excelYaz(workbook, sheet);
                System.out.println("Eksik hash değeri tamamlanan eski kayıt: " + tamamlanan);
            }
        }
    }

    public Path kayitliArsivDosyasiniBul(Kayit kayit, Path arsivKlasoru) {
        if (kayit == null) {
            return null;
        }

        if (!bos(kayit.arsivYolu)) {
            try {
                Path yol = Path.of(kayit.arsivYolu);
                if (Files.exists(yol)) {
                    return yol;
                }
            } catch (Exception ignored) {
            }
        }

        if (!bos(kayit.arsivDosyaAdi)) {
            Path yol = arsivKlasoru.resolve(kayit.arsivDosyaAdi);
            if (Files.exists(yol)) {
                return yol;
            }
        }
        return null;
    }

    private Path kayitliArsivDosyasiniBul(Row row, DataFormatter formatter, Path arsivKlasoru) {
        String tamYol = oku(formatter, row, ARSIV_YOLU);
        if (!bos(tamYol)) {
            try {
                Path path = Path.of(tamYol);
                if (Files.exists(path)) {
                    return path;
                }
            } catch (Exception ignored) {
            }
        }

        String arsivAdi = oku(formatter, row, ARSIV_ADI);
        if (!bos(arsivAdi)) {
            Path path = arsivKlasoru.resolve(arsivAdi);
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    private boolean excelVarMi() throws Exception {
        return Files.exists(excelYolu) && Files.size(excelYolu) > 0;
    }

    private Workbook workbookAcVeyaOlustur() throws Exception {
        if (excelVarMi()) {
            try (FileInputStream input = new FileInputStream(excelYolu.toFile())) {
                return WorkbookFactory.create(input);
            }
        }
        return new XSSFWorkbook();
    }

    private Sheet eserSayfasiniHazirlaVeGerekirseDonustur(Workbook workbook) {
        Sheet sheet = workbook.getSheet(SAYFA_ADI);

        if (sheet == null) {
            Sheet eski = workbook.getSheet(ESKI_SAYFA_ADI);
            if (eski != null) {
                int index = workbook.getSheetIndex(eski);
                workbook.setSheetName(index, SAYFA_ADI);
                sheet = workbook.getSheetAt(index);
            }
        }

        if (sheet == null) {
            sheet = workbook.createSheet(SAYFA_ADI);
        }

        if (!yeniDuzenMi(sheet)) {
            sheet = eskiDuzeniYeniDuzenleDegistir(workbook, sheet);
        }

        basliklariYazVeBicimlendir(workbook, sheet);
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    private boolean yeniDuzenMi(Sheet sheet) {
        Row baslik = sheet.getRow(0);
        if (baslik == null) {
            return false;
        }

        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i < BASLIKLAR.length; i++) {
            String mevcut = oku(formatter, baslik, i);
            if (!BASLIKLAR[i].equalsIgnoreCase(mevcut)) {
                return false;
            }
        }
        return true;
    }

    private Sheet eskiDuzeniYeniDuzenleDegistir(Workbook workbook, Sheet eskiSheet) {
        List<Map<String, String>> eskiSatirlar = eskiSatirlariOku(eskiSheet);
        int eskiIndex = workbook.getSheetIndex(eskiSheet);

        String geciciAd = "Eserler_Yeni";
        int sayac = 2;
        while (workbook.getSheet(geciciAd) != null) {
            geciciAd = "Eserler_Yeni_" + sayac++;
        }

        Sheet yeniSheet = workbook.createSheet(geciciAd);
        basliklariYazVeBicimlendir(workbook, yeniSheet);

        int yeniSatirNo = 1;
        for (Map<String, String> eski : eskiSatirlar) {
            Row row = yeniSheet.createRow(yeniSatirNo++);
            eskiKaydiYeniDuzenleYaz(row, eski);
        }

        workbook.removeSheetAt(eskiIndex);
        int yeniIndex = workbook.getSheetIndex(yeniSheet);
        workbook.setSheetName(yeniIndex, SAYFA_ADI);
        workbook.setSheetOrder(SAYFA_ADI, Math.min(eskiIndex, workbook.getNumberOfSheets() - 1));

        Sheet sonuc = workbook.getSheet(SAYFA_ADI);
        sonuc.createFreezePane(0, 1);
        return sonuc;
    }

    private List<Map<String, String>> eskiSatirlariOku(Sheet sheet) {
        List<Map<String, String>> sonuc = new ArrayList<>();
        Row baslik = sheet.getRow(0);
        if (baslik == null) {
            return sonuc;
        }

        DataFormatter formatter = new DataFormatter();
        Map<Integer, String> sutunBasliklari = new HashMap<>();
        for (int i = 0; i < baslik.getLastCellNum(); i++) {
            String ad = oku(formatter, baslik, i);
            if (!ad.isBlank()) {
                sutunBasliklari.put(i, normalizeBaslik(ad));
            }
        }

        for (int satirNo = 1; satirNo <= sheet.getLastRowNum(); satirNo++) {
            Row row = sheet.getRow(satirNo);
            if (row == null) {
                continue;
            }

            Map<String, String> degerler = new HashMap<>();
            boolean dolu = false;
            for (Map.Entry<Integer, String> entry : sutunBasliklari.entrySet()) {
                String deger = oku(formatter, row, entry.getKey());
                if (!deger.isBlank()) {
                    dolu = true;
                }
                degerler.put(entry.getValue(), deger);
            }

            if (dolu) {
                sonuc.add(degerler);
            }
        }
        return sonuc;
    }

    private void eskiKaydiYeniDuzenleYaz(Row row, Map<String, String> eski) {
        String id = eskiDeger(eski, "ID");
        String orijinalAd = eskiDeger(eski, "Orijinal Dosya Adı", "PDF Dosya Adı", "Dosya Adı");
        String eserAdi = eskiDeger(eski, "Eser Adı", "Kitap Adı");
        String eserTuru = eskiDeger(eski, "Eser Türü");
        String yazar = eskiDeger(eski, "Yazar");
        String yayinevi = eskiDeger(eski, "Yayınevi", "Yayinevi");
        String basimYili = eskiDeger(eski, "Basım Yılı", "Basim Yili");
        String dil = eskiDeger(eski, "Dil");
        String sayfaSayisi = eskiDeger(eski, "Sayfa Sayısı");
        String kaynakUrl = eskiDeger(eski, "Kaynak URL", "Kaynak Url");
        String lisans = eskiDeger(eski, "Lisans");
        String bilgiKaynagi = eskiDeger(eski, "Bilgi Kaynağı");
        String aiModeli = eskiDeger(eski, "Kullanılan AI Modeli", "AI Modeli");
        String arsivAdi = eskiDeger(eski, "Arşiv Dosya Adı");
        String arsivYolu = eskiDeger(eski, "Arşiv Yolu");
        String durum = eskiDeger(eski, "Durum");
        String hash = eskiDeger(eski, "Dosya Hash (SHA-256)", "SHA-256", "Hash");
        String seslendirme = eskiDeger(eski, "Seslendirme Durumu");

        sayiYaz(row, ID, sayiyaCevir(id));
        metinYaz(row, ORIJINAL_AD, orijinalAd);
        metinYaz(row, ESER_ADI, eserAdi);
        metinYaz(row, ESER_TURU, bos(eserTuru) ? "Kitap" : eserTuru);
        metinYaz(row, YAZAR, yazar);
        metinYaz(row, YAYINEVI, yayinevi);
        metinYaz(row, BASIM_YILI, basimYili);
        metinYaz(row, DIL, dil);
        metinYaz(row, SAYFA_SAYISI, sayfaSayisi);
        metinYaz(row, KAYNAK_URL, kaynakUrl);
        metinYaz(row, LISANS, bos(lisans) ? "Kontrol edilmedi" : lisans);
        metinYaz(row, BILGI_KAYNAGI, bos(bilgiKaynagi) ? "Eski kayıt" : bilgiKaynagi);
        metinYaz(row, AI_MODELI, bos(aiModeli) ? "Yok" : aiModeli);
        metinYaz(row, ARSIV_ADI, arsivAdi);
        metinYaz(row, ARSIV_YOLU, arsivYolu);

        if (bos(durum)) {
            durum = (!bos(arsivAdi) || !bos(arsivYolu)) ? "Arşivlendi" : "Kayıtlı";
        }
        metinYaz(row, DURUM, durum);
        metinYaz(row, HASH, hash);
        metinYaz(row, SESLENDIRME_DURUMU, bos(seslendirme) ? "Bekliyor" : seslendirme);
    }

    private String eskiDeger(Map<String, String> eski, String... adayBasliklar) {
        for (String aday : adayBasliklar) {
            String deger = eski.get(normalizeBaslik(aday));
            if (!bos(deger)) {
                return deger;
            }
        }
        return "";
    }

    private static String normalizeBaslik(String metin) {
        if (metin == null) {
            return "";
        }

        String sonuc = metin
                .replace('ı', 'i')
                .replace('İ', 'I');
        sonuc = Normalizer.normalize(sonuc, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        return sonuc;
    }

    private void basliklariYazVeBicimlendir(Workbook workbook, Sheet sheet) {
        Row baslik = sheet.getRow(0);
        if (baslik == null) {
            baslik = sheet.createRow(0);
        }

        CellStyle stil = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        stil.setFont(font);
        stil.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        stil.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        for (int i = 0; i < BASLIKLAR.length; i++) {
            Cell cell = baslik.getCell(i);
            if (cell == null) {
                cell = baslik.createCell(i);
            }
            cell.setCellValue(BASLIKLAR[i]);
            cell.setCellStyle(stil);
        }
    }

    private void kaydiYaz(Row row, int id, String orijinalDosyaAdi, EserBilgisi bilgi,
                           Path arsivDosyasi, String hash) {
        bilgi.bilinmeyenleriDuzelt();

        sayiYaz(row, ID, id);
        metinYaz(row, ORIJINAL_AD, orijinalDosyaAdi);
        metinYaz(row, ESER_ADI, bilgi.eser_adi);
        metinYaz(row, ESER_TURU, bilgi.eser_turu);
        metinYaz(row, YAZAR, bilgi.yazar);
        metinYaz(row, YAYINEVI, bilgi.yayinevi);
        metinYaz(row, BASIM_YILI, bilgi.basim_yili);
        metinYaz(row, DIL, bilgi.dil);
        metinYaz(row, SAYFA_SAYISI, bilgi.sayfa_sayisi);
        metinYaz(row, KAYNAK_URL, bilgi.kaynak_url);
        metinYaz(row, LISANS, bilgi.lisans);
        metinYaz(row, BILGI_KAYNAGI, bilgi.bilgi_kaynagi);
        metinYaz(row, AI_MODELI, bilgi.kullanilan_ai_modeli);
        metinYaz(row, ARSIV_ADI, arsivDosyasi.getFileName().toString());
        metinYaz(row, ARSIV_YOLU, arsivDosyasi.toAbsolutePath().toString());
        metinYaz(row, DURUM, "Arşivlendi");
        metinYaz(row, HASH, hash);
        metinYaz(row, SESLENDIRME_DURUMU, bilgi.seslendirme_durumu);
    }

    private void excelYaz(Workbook workbook, Sheet sheet) throws Exception {
        for (int i = 0; i < BASLIKLAR.length; i++) {
            sheet.autoSizeColumn(i);
            int mevcut = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.min(Math.max(mevcut + 700, 2500), 18000));
        }

        Path parent = excelYolu.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path gecici = excelYolu.resolveSibling(excelYolu.getFileName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(gecici.toFile())) {
            workbook.write(output);
        }

        try {
            Files.move(gecici, excelYolu,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(gecici, excelYolu, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Kayit satirdanOku(Row row, int satirNumarasi, DataFormatter formatter) {
        Kayit kayit = new Kayit();
        kayit.satirNumarasi = satirNumarasi;
        kayit.id = sayiOku(formatter, row.getCell(ID));
        kayit.orijinalDosyaAdi = oku(formatter, row, ORIJINAL_AD);
        kayit.eserBilgisi = new EserBilgisi();
        kayit.eserBilgisi.eser_adi = oku(formatter, row, ESER_ADI);
        kayit.eserBilgisi.eser_turu = oku(formatter, row, ESER_TURU);
        kayit.eserBilgisi.yazar = oku(formatter, row, YAZAR);
        kayit.eserBilgisi.yayinevi = oku(formatter, row, YAYINEVI);
        kayit.eserBilgisi.basim_yili = oku(formatter, row, BASIM_YILI);
        kayit.eserBilgisi.dil = oku(formatter, row, DIL);
        kayit.eserBilgisi.sayfa_sayisi = oku(formatter, row, SAYFA_SAYISI);
        kayit.eserBilgisi.kaynak_url = oku(formatter, row, KAYNAK_URL);
        kayit.eserBilgisi.lisans = oku(formatter, row, LISANS);
        kayit.eserBilgisi.bilgi_kaynagi = oku(formatter, row, BILGI_KAYNAGI);
        kayit.eserBilgisi.kullanilan_ai_modeli = oku(formatter, row, AI_MODELI);
        kayit.eserBilgisi.seslendirme_durumu = oku(formatter, row, SESLENDIRME_DURUMU);
        kayit.eserBilgisi.bilinmeyenleriDuzelt();
        kayit.arsivDosyaAdi = oku(formatter, row, ARSIV_ADI);
        kayit.arsivYolu = oku(formatter, row, ARSIV_YOLU);
        kayit.durum = oku(formatter, row, DURUM);
        kayit.hash = oku(formatter, row, HASH);
        return kayit;
    }

    private static String oku(DataFormatter formatter, Row row, int sutun) {
        Cell cell = row.getCell(sutun);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static int sayiOku(DataFormatter formatter, Cell cell) {
        if (cell == null) {
            return 0;
        }
        return sayiyaCevir(formatter.formatCellValue(cell));
    }

    private static int sayiyaCevir(String deger) {
        if (deger == null) {
            return 0;
        }
        String temiz = deger.replaceAll("[^0-9]", "");
        if (temiz.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(temiz);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void metinYaz(Row row, int sutun, String deger) {
        Cell cell = row.getCell(sutun);
        if (cell == null) {
            cell = row.createCell(sutun);
        }
        cell.setCellValue(bos(deger) ? "Bilinmiyor" : deger.trim());
    }

    private static void sayiYaz(Row row, int sutun, int deger) {
        Cell cell = row.getCell(sutun);
        if (cell == null) {
            cell = row.createCell(sutun);
        }
        cell.setCellValue(deger);
    }


    private static boolean doluKaynakDegeri(String deger) {
        if (deger == null || deger.isBlank()) {
            return false;
        }
        String temiz = deger.trim();
        return !"null".equalsIgnoreCase(temiz)
                && !"Bilinmiyor".equalsIgnoreCase(temiz)
                && !"Kontrol edilmedi".equalsIgnoreCase(temiz);
    }

    private static boolean bos(String deger) {
        return deger == null || deger.isBlank() || "null".equalsIgnoreCase(deger.trim()) ||
                "Bilinmiyor".equalsIgnoreCase(deger.trim());
    }

    private static boolean gecerliHash(String hash) {
        return hash != null && hash.matches("[a-fA-F0-9]{64}");
    }

    public static final class Kayit {
        public int satirNumarasi;
        public int id;
        public String orijinalDosyaAdi;
        public String arsivDosyaAdi;
        public String arsivYolu;
        public String durum;
        public String hash;
        public EserBilgisi eserBilgisi;
    }
}
