import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;

public final class KaynakBilgisiService {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private KaynakBilgisiService() {
    }

    public static void kaydet(Path eserDosyasi, KaynakBilgisi bilgi) throws Exception {
        if (eserDosyasi == null || bilgi == null || !bilgi.varMi()) {
            return;
        }

        Path yanDosya = yanDosyaYolu(eserDosyasi);
        MAPPER.writeValue(yanDosya.toFile(), bilgi);
    }

    public static KaynakBilgisi oku(Path eserDosyasi) {
        if (eserDosyasi == null) {
            return null;
        }

        Path yanDosya = yanDosyaYolu(eserDosyasi);
        if (!Files.exists(yanDosya)) {
            return null;
        }

        try {
            KaynakBilgisi bilgi = MAPPER.readValue(yanDosya.toFile(), KaynakBilgisi.class);
            return bilgi != null && bilgi.varMi() ? bilgi : null;
        } catch (Exception e) {
            System.err.println("Kaynak bilgisi okunamadı: " + e.getMessage());
            return null;
        }
    }

    public static void esereUygula(EserBilgisi eser, KaynakBilgisi kaynak) {
        if (eser == null || kaynak == null) {
            return;
        }

        if (dolu(kaynak.kaynak_url)) {
            eser.kaynak_url = kaynak.kaynak_url.trim();
        }

        if (dolu(kaynak.lisans)) {
            eser.lisans = kaynak.lisans.trim();
        }
    }

    public static void sil(Path eserDosyasi) {
        if (eserDosyasi == null) {
            return;
        }

        try {
            Files.deleteIfExists(yanDosyaYolu(eserDosyasi));
        } catch (Exception e) {
            System.err.println("Kaynak yan dosyası silinemedi: " + e.getMessage());
        }
    }

    public static Path yanDosyaYolu(Path eserDosyasi) {
        return eserDosyasi.resolveSibling(eserDosyasi.getFileName() + ".kaynak.json");
    }

    private static boolean dolu(String deger) {
        if (deger == null || deger.isBlank()) {
            return false;
        }
        String temiz = deger.trim();
        return !"null".equalsIgnoreCase(temiz)
                && !"Bilinmiyor".equalsIgnoreCase(temiz)
                && !"Kontrol edilmedi".equalsIgnoreCase(temiz);
    }
}
