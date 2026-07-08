import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Locale;

/** Masaüstündeki eser-katalogu.xlsx dosyasını atomik ve tekil Eser ID ile yönetir. */
public final class EserKatalogService {
    private static final String SAYFA = "Eser Kataloğu";
    private static final String[] BASLIKLAR = {
            "Eser ID", "Eser Türü", "Eser Adı", "Yazar", "Yayınevi", "Yayın Yılı", "ISBN", "Dil",
            "Kaynak URL", "Archive.org Identifier", "Orijinal Dosya Adı", "Arşiv Dosya Adı", "Arşiv Yolu",
            "Dosya Türü", "SHA-256", "Sayfa/Birim Sayısı", "Karakter Sayısı", "Bölüm Sayısı",
            "Metadata Güven Puanı", "Metadata Durumu", "Bilgi Kaynağı", "AI Modeli", "Lisans/Hak Durumu",
            "Metin Arşivi", "TTS Durumu", "Ses Sağlayıcısı", "MP3 Yolu", "M4B Yolu", "Eklenme Tarihi", "Kanıt",
            "Orijinal Adı", "Çevirmen", "Basım Bilgisi"
    };

    private final Path yol;

    public EserKatalogService(Path yol) { this.yol = yol; }

    public Path yol() { return yol; }

    public synchronized void kaydet(KatalogKaydi k) throws Exception {
        Files.createDirectories(yol.toAbsolutePath().getParent());
        try (Workbook wb = ac()) {
            Sheet sh = hazirla(wb);
            int satir = idSatiri(sh, k.eserId());
            Row r = satir >= 0 ? sh.getRow(satir) : sh.createRow(sh.getLastRowNum() + 1);
            yaz(r, k);
            bicimlendirSatir(wb, r, k.metadata().metadataDurumu);
            sh.setAutoFilter(new CellRangeAddress(0, sh.getLastRowNum(), 0, BASLIKLAR.length - 1));
            atomikYaz(wb);
        }
    }

    public synchronized boolean idVarMi(int id) throws Exception {
        if (!Files.isRegularFile(yol)) return false;
        try (Workbook wb = ac()) { return idSatiri(hazirla(wb), id) >= 0; }
    }

    private Workbook ac() throws Exception {
        if (Files.isRegularFile(yol) && Files.size(yol) > 0) {
            try (InputStream in = Files.newInputStream(yol)) { return WorkbookFactory.create(in); }
        }
        return new XSSFWorkbook();
    }

    private Sheet hazirla(Workbook wb) {
        Sheet sh = wb.getSheet(SAYFA);
        if (sh == null) sh = wb.createSheet(SAYFA);
        Row h = sh.getRow(0); if (h == null) h = sh.createRow(0);
        CellStyle hs = baslikStili(wb);
        for (int i = 0; i < BASLIKLAR.length; i++) {
            Cell c = h.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            c.setCellValue(BASLIKLAR[i]); c.setCellStyle(hs);
        }
        sh.createFreezePane(0, 1);
        sh.setAutoFilter(new CellRangeAddress(0, Math.max(0, sh.getLastRowNum()), 0, BASLIKLAR.length - 1));
        int[] genislik = {12,16,34,24,22,12,18,12,38,24,32,36,45,12,45,16,16,14,18,22,28,22,24,45,14,20,35,35,24,55,34,24,22};
        for (int i=0;i<genislik.length;i++) sh.setColumnWidth(i, Math.min(255,genislik[i])*256);
        h.setHeightInPoints(28);
        return sh;
    }

    private void yaz(Row r, KatalogKaydi k) {
        EserMetadata m = k.metadata(); m.temizle();
        int c=0;
        metin(r,c++,String.format(Locale.ROOT,"ESER-%05d",k.eserId()));
        metin(r,c++,m.eserTuru); metin(r,c++,m.eserAdi); metin(r,c++,m.yazar); metin(r,c++,m.yayinevi);
        metin(r,c++,m.yayinYili); metin(r,c++,m.isbn); metin(r,c++,m.dil); metin(r,c++,m.kaynakUrl);
        metin(r,c++,m.archiveIdentifier); metin(r,c++,m.orijinalDosyaAdi);
        metin(r,c++,k.arsivDosyasi()==null?"":k.arsivDosyasi().getFileName().toString());
        metin(r,c++,k.arsivDosyasi()==null?"":k.arsivDosyasi().toAbsolutePath().toString());
        metin(r,c++,k.dosyaTuru()); metin(r,c++,k.sha256());
        sayi(r,c++,k.kaynakBirimSayisi()); sayi(r,c++,k.karakterSayisi()); sayi(r,c++,k.bolumSayisi());
        ondalik(r,c++,m.guvenPuani); metin(r,c++,m.metadataDurumu); metin(r,c++,m.bilgiKaynagi);
        metin(r,c++,m.kullanilanAiModeli); metin(r,c++,m.lisans);
        metin(r,c++,k.metinKlasoru()==null?"":k.metinKlasoru().toAbsolutePath().toString());
        metin(r,c++,k.ttsDurumu()); metin(r,c++,k.sesSaglayicisi()); metin(r,c++,k.mp3Yolu()); metin(r,c++,k.m4bYolu());
        metin(r,c++,k.eklenmeTarihi()==null?OffsetDateTime.now().toString():k.eklenmeTarihi()); metin(r,c++,m.kanit);
        metin(r,c++,m.orijinalAdi); metin(r,c++,m.cevirmen); metin(r,c,m.basimBilgisi);
        r.setHeightInPoints(40);
    }

    private void bicimlendirSatir(Workbook wb, Row r, String durum) {
        CellStyle s = wb.createCellStyle();
        s.setWrapText(true); s.setVerticalAlignment(VerticalAlignment.TOP);
        s.setBorderBottom(BorderStyle.HAIR); s.setBorderTop(BorderStyle.HAIR);
        s.setBorderLeft(BorderStyle.HAIR); s.setBorderRight(BorderStyle.HAIR);
        if ("KONTROL_GEREKIYOR".equalsIgnoreCase(durum)) {
            s.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex()); s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        for (int i=0;i<BASLIKLAR.length;i++) r.getCell(i,Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellStyle(s);
        CellStyle pct=wb.createCellStyle(); pct.cloneStyleFrom(s); pct.setDataFormat(wb.createDataFormat().getFormat("0.0%")); r.getCell(18).setCellStyle(pct);
    }

    private static CellStyle baslikStili(Workbook wb) {
        CellStyle s=wb.createCellStyle(); s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font f=wb.createFont(); f.setBold(true); f.setColor(IndexedColors.WHITE.getIndex()); s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER); s.setVerticalAlignment(VerticalAlignment.CENTER); s.setWrapText(true);
        return s;
    }

    private int idSatiri(Sheet sh, int id) {
        DataFormatter f=new DataFormatter(); String aranan=String.format(Locale.ROOT,"ESER-%05d",id);
        for(int i=1;i<=sh.getLastRowNum();i++){Row r=sh.getRow(i); if(r!=null&&aranan.equalsIgnoreCase(f.formatCellValue(r.getCell(0)))) return i;}
        return -1;
    }

    private void atomikYaz(Workbook wb) throws Exception {
        Path tmp=yol.resolveSibling(yol.getFileName()+".tmp");
        try(OutputStream out=Files.newOutputStream(tmp)){wb.write(out);}
        try{Files.move(tmp,yol,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,yol,StandardCopyOption.REPLACE_EXISTING);}
    }
    private static void metin(Row r,int i,String v){r.getCell(i,Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(v==null?"":v);}
    private static void sayi(Row r,int i,long v){r.getCell(i,Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(v);}
    private static void ondalik(Row r,int i,double v){r.getCell(i,Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(v);}

    public record KatalogKaydi(int eserId, EserMetadata metadata, Path arsivDosyasi, Path metinKlasoru,
                               String dosyaTuru, String sha256, int kaynakBirimSayisi, int karakterSayisi,
                               int bolumSayisi, String ttsDurumu, String sesSaglayicisi,
                               String mp3Yolu, String m4bYolu, String eklenmeTarihi) {}
}
