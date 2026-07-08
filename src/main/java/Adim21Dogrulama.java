import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class Adim21Dogrulama {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        test("https://archive.org/details/bilim-kitabi-alfa-yayinlari/Astronomi%20Alfa%20Yay%C4%B1nlar%C4%B1/",
                "Astronomi",
                new String[]{"Fizik Alfa Yayınları/Fizik.pdf", "Astronomi Alfa Yayınları/Astronomi Alfa Yayınları.pdf", "Astronomi Alfa Yayınları/Astronomi Alfa Yayınları.epub"});
        test("https://archive.org/details/arif-sarsilmaz-bilimin-ilham-kaynagi-altin-burc-yayinlari/Salih%20Seref%20Duran%20-%20Hayatin%20Sesi%20-%20Fotosentez%20Mucizesi%20-%20AltinBurcYayinlari/",
                "Fotosentez",
                new String[]{"Arif Sarsilmaz - Bilimin Ilham Kaynagi.pdf", "Salih Seref Duran - Hayatin Sesi - Fotosentez Mucizesi - AltinBurcYayinlari/Salih Seref Duran - Hayatin Sesi - Fotosentez Mucizesi.pdf"});
        test("https://archive.org/details/5000-epub-kitap/5000%20Epub%20kitap/A%20Harfi/Abdullah%20Ziya%20Kozano%C4%9Flu%20-%20T%C3%BCrk%20Korsanlar%C4%B1/",
                "Türk Korsanları",
                new String[]{"5000 Epub kitap/B Harfi/Baska.epub", "5000 Epub kitap/A Harfi/Abdullah Ziya Kozanoğlu - Türk Korsanları/Abdullah Ziya Kozanoğlu - Türk Korsanları.epub"});
        test("https://archive.org/details/anonimosmanlipadisahlari/Aziz%20Nesin%20-%20Borclu%20Olduklarimiz/page/n5/mode/2up",
                "Borclu Olduklarimiz",
                new String[]{"Anonim - Osmanli Padisahlari.pdf", "Aziz Nesin - Borclu Olduklarimiz.pdf"});
        System.out.println("ADIM 21 DOĞRULAMA: BAŞARILI");
    }

    private static void test(String url, String beklenen, String[] dosyalar) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode files = root.putArray("files");
        for (String ad : dosyalar) {
            ObjectNode f = files.addObject();
            f.put("name", ad);
            f.put("source", "original");
            f.put("format", ad.toLowerCase().endsWith(".epub") ? "EPUB" : "Text PDF");
            f.put("size", "1000000");
        }
        ArchiveOrgCozumleyici.DosyaSecimi secim = ArchiveOrgCozumleyici.dosyaSec(url, root);
        if (!secim.ana().ad().contains(beklenen)) {
            throw new IllegalStateException("Archive seçimi yanlış. Beklenen=" + beklenen + " seçilen=" + secim.ana().ad());
        }
        System.out.println("OK: " + secim.ana().ad());
    }
}
