import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

public final class ManuelGirisService {
    private final BufferedReader okuyucu;

    public ManuelGirisService(InputStream input) {
        this(new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)));
    }

    public ManuelGirisService(BufferedReader okuyucu) {
        this.okuyucu = Objects.requireNonNull(okuyucu, "okuyucu");
    }

    public EserBilgisi bilgiAl(Path pdf, String neden) throws IOException {
        return bilgiAl(pdf, neden, null);
    }

    public EserBilgisi bilgiAl(Path pdf, String neden, KaynakBilgisi kaynakBilgisi) throws IOException {
        System.out.println();
        System.out.println("--- MANUEL ESER BİLGİSİ GİRİŞİ ---");
        System.out.println("Dosya: " + pdf.getFileName());

        if (neden != null && !neden.isBlank()) {
            System.out.println("Neden: " + tekSatiraCevir(neden));
        }

        String secim = sor("Bu eseri manuel olarak kaydetmek ister misin? (E/H): ");
        if (!evetMi(secim)) {
            System.out.println("Manuel giriş atlandı. Eser dosyası gelen-pdf klasöründe bırakıldı.");
            return null;
        }

        EserBilgisi bilgi = new EserBilgisi();
        bilgi.eser_turu = varsayilanliAlan("Eser türü", "Kitap");
        bilgi.eser_adi = eserAdiAl(dosyaAdindanBaslik(pdf.getFileName().toString()));
        bilgi.yazar = istegeBagliAlan("Yazar", "Bilinmiyor");
        bilgi.yayinevi = istegeBagliAlan("Yayınevi", "Bilinmiyor");
        bilgi.basim_yili = basimYiliAl();
        bilgi.dil = varsayilanliAlan("Dil", "Türkçe");

        String varsayilanKaynak = kaynakBilgisi != null && dolu(kaynakBilgisi.kaynak_url)
                ? kaynakBilgisi.kaynak_url
                : "Bilinmiyor";
        String varsayilanLisans = kaynakBilgisi != null && dolu(kaynakBilgisi.lisans)
                ? kaynakBilgisi.lisans
                : "Kontrol edilmedi";

        bilgi.kaynak_url = varsayilanliAlan("Kaynak URL", varsayilanKaynak);
        bilgi.lisans = varsayilanliAlan("Lisans", varsayilanLisans);
        bilgi.seslendirme_durumu = "Bekliyor";
        bilgi.bilinmeyenleriDuzelt();

        System.out.println();
        System.out.println("Girilen bilgiler:");
        System.out.println("Eser türü   : " + bilgi.eser_turu);
        System.out.println("Eser adı    : " + bilgi.eser_adi);
        System.out.println("Yazar       : " + bilgi.yazar);
        System.out.println("Yayınevi    : " + bilgi.yayinevi);
        System.out.println("Basım yılı  : " + bilgi.basim_yili);
        System.out.println("Dil         : " + bilgi.dil);
        System.out.println("Kaynak URL  : " + bilgi.kaynak_url);
        System.out.println("Lisans      : " + bilgi.lisans);

        String onay = sor("Bu bilgilerle kaydedilsin mi? (E/H): ");
        if (!evetMi(onay)) {
            System.out.println("Kayıt iptal edildi. Eser dosyası gelen-pdf klasöründe bırakıldı.");
            return null;
        }

        return bilgi;
    }

    private String eserAdiAl(String varsayilan) throws IOException {
        while (true) {
            String cevap = sor("Eser adı [" + varsayilan + "]: ");
            if (cevap.isBlank()) {
                return varsayilan;
            }

            if ("e".equalsIgnoreCase(cevap) || "h".equalsIgnoreCase(cevap)) {
                System.out.println("Buraya E/H değil, eser adını yaz. Önerilen adı kullanmak için sadece Enter'a bas.");
                continue;
            }

            return cevap.trim();
        }
    }

    private String varsayilanliAlan(String alanAdi, String varsayilan) throws IOException {
        String cevap = sor(alanAdi + " [" + varsayilan + "]: ");
        return cevap.isBlank() ? varsayilan : cevap.trim();
    }

    private String istegeBagliAlan(String alanAdi, String varsayilan) throws IOException {
        String cevap = sor(alanAdi + " (boş bırakırsan " + varsayilan + "): ");
        return cevap.isBlank() ? varsayilan : cevap.trim();
    }

    private String basimYiliAl() throws IOException {
        while (true) {
            String cevap = sor("Basım yılı (boş bırakırsan Bilinmiyor): ");
            if (cevap.isBlank()) {
                return "Bilinmiyor";
            }

            String temiz = cevap.trim();
            if (temiz.matches("\\d{4}")) {
                return temiz;
            }

            System.out.println("Basım yılı 4 rakam olmalı. Örnek: 2026");
        }
    }

    private String sor(String soru) throws IOException {
        System.out.print(soru);
        System.out.flush();
        String cevap = okuyucu.readLine();
        return cevap == null ? "" : cevap.trim();
    }

    private static boolean evetMi(String cevap) {
        return "e".equalsIgnoreCase(cevap)
                || "evet".equalsIgnoreCase(cevap)
                || "y".equalsIgnoreCase(cevap)
                || "yes".equalsIgnoreCase(cevap);
    }

    private static String dosyaAdindanBaslik(String dosyaAdi) {
        int nokta = dosyaAdi.lastIndexOf('.');
        String baslik = nokta > 0 ? dosyaAdi.substring(0, nokta) : dosyaAdi;
        baslik = baslik.replace('_', ' ').replaceAll("\\s+", " ").trim();
        return baslik.isBlank() ? "Bilinmiyor" : baslik;
    }

    private static boolean dolu(String deger) {
        return deger != null && !deger.isBlank() && !"null".equalsIgnoreCase(deger.trim());
    }

    private static String tekSatiraCevir(String metin) {
        String tekSatir = metin.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();
        if (tekSatir.length() > 300) {
            return tekSatir.substring(0, 300) + "...";
        }
        return tekSatir;
    }
}
