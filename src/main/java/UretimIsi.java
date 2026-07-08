import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Properties;

public record UretimIsi(
        String id,
        String eserAdi,
        Path metinEserKlasoru,
        Path ttsKlasoru,
        Path sesEserKlasoru,
        UretimPolitikasi politika,
        UretimDurumu durum,
        int toplamParca,
        int hazirParca,
        long toplamKarakter,
        String aktifSaglayici,
        String sonHata,
        String olusturmaZamani,
        String guncellemeZamani
) {
    public UretimIsi {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("İş kimliği boş olamaz.");
        eserAdi = eserAdi == null || eserAdi.isBlank() ? id : eserAdi.trim();
        aktifSaglayici = aktifSaglayici == null ? "" : aktifSaglayici;
        sonHata = sonHata == null ? "" : sonHata;
        olusturmaZamani = olusturmaZamani == null || olusturmaZamani.isBlank()
                ? OffsetDateTime.now().toString() : olusturmaZamani;
        guncellemeZamani = guncellemeZamani == null || guncellemeZamani.isBlank()
                ? OffsetDateTime.now().toString() : guncellemeZamani;
    }

    public UretimIsi guncelle(UretimDurumu yeniDurum, int yeniHazir, String saglayici, String hata) {
        return new UretimIsi(id, eserAdi, metinEserKlasoru, ttsKlasoru, sesEserKlasoru,
                politika, yeniDurum, toplamParca, yeniHazir, toplamKarakter,
                saglayici, hata, olusturmaZamani, OffsetDateTime.now().toString());
    }

    public UretimIsi politikaDegistir(UretimPolitikasi yeniPolitika) {
        return new UretimIsi(id, eserAdi, metinEserKlasoru, ttsKlasoru, sesEserKlasoru,
                yeniPolitika, durum, toplamParca, hazirParca, toplamKarakter,
                aktifSaglayici, sonHata, olusturmaZamani, OffsetDateTime.now().toString());
    }

    public Properties properties() {
        Properties p = new Properties();
        p.setProperty("id", id);
        p.setProperty("eserAdi", eserAdi);
        p.setProperty("metinEserKlasoru", metinEserKlasoru.toAbsolutePath().toString());
        p.setProperty("ttsKlasoru", ttsKlasoru.toAbsolutePath().toString());
        p.setProperty("sesEserKlasoru", sesEserKlasoru.toAbsolutePath().toString());
        p.setProperty("politika", politika.name());
        p.setProperty("durum", durum.name());
        p.setProperty("toplamParca", String.valueOf(toplamParca));
        p.setProperty("hazirParca", String.valueOf(hazirParca));
        p.setProperty("toplamKarakter", String.valueOf(toplamKarakter));
        p.setProperty("aktifSaglayici", aktifSaglayici);
        p.setProperty("sonHata", sonHata);
        p.setProperty("olusturmaZamani", olusturmaZamani);
        p.setProperty("guncellemeZamani", guncellemeZamani);
        return p;
    }

    public static UretimIsi propertiesOku(Properties p) {
        return new UretimIsi(
                p.getProperty("id"),
                p.getProperty("eserAdi"),
                Path.of(p.getProperty("metinEserKlasoru")),
                Path.of(p.getProperty("ttsKlasoru")),
                Path.of(p.getProperty("sesEserKlasoru")),
                UretimPolitikasi.oku(p.getProperty("politika", "OTOMATIK")),
                UretimDurumu.valueOf(p.getProperty("durum", "BEKLIYOR")),
                Integer.parseInt(p.getProperty("toplamParca", "0")),
                Integer.parseInt(p.getProperty("hazirParca", "0")),
                Long.parseLong(p.getProperty("toplamKarakter", "0")),
                p.getProperty("aktifSaglayici", ""),
                p.getProperty("sonHata", ""),
                p.getProperty("olusturmaZamani", ""),
                p.getProperty("guncellemeZamani", "")
        );
    }
}
