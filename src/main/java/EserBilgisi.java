import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EserBilgisi {
    @JsonAlias({"kitap_adi", "kitapAdi", "eserAdi"})
    public String eser_adi;

    public String eser_turu;
    public String yazar;
    public String yayinevi;
    public String basim_yili;
    public String dil;
    public String isbn;
    public String guven_puani;
    public String kanit;
    public String sayfa_sayisi;
    public String kaynak_url;
    public String lisans;
    public String bilgi_kaynagi;
    public String kullanilan_ai_modeli;
    public String seslendirme_durumu;

    public EserBilgisi() {
    }

    public void bilinmeyenleriDuzelt() {
        eser_adi = temizle(eser_adi, "Bilinmiyor");
        eser_turu = temizle(eser_turu, "Bilinmiyor");
        yazar = temizle(yazar, "Bilinmiyor");
        yayinevi = temizle(yayinevi, "Bilinmiyor");
        basim_yili = temizle(basim_yili, "Bilinmiyor");
        dil = temizle(dil, "Bilinmiyor");
        isbn = temizle(isbn, "Bilinmiyor");
        guven_puani = temizle(guven_puani, "0");
        kanit = temizle(kanit, "");
        sayfa_sayisi = temizle(sayfa_sayisi, "Bilinmiyor");
        kaynak_url = temizle(kaynak_url, "Bilinmiyor");
        lisans = temizle(lisans, "Kontrol edilmedi");
        bilgi_kaynagi = temizle(bilgi_kaynagi, "Bilinmiyor");
        kullanilan_ai_modeli = temizle(kullanilan_ai_modeli, "Yok");
        seslendirme_durumu = temizle(seslendirme_durumu, "Bekliyor");
    }

    public void islemBilgileriniAyarla(String bilgiKaynagi, String model, int toplamSayfaSayisi) {
        this.bilgi_kaynagi = bosMu(bilgiKaynagi) ? "Bilinmiyor" : bilgiKaynagi.trim();
        this.kullanilan_ai_modeli = bosMu(model) ? "Yok" : model.trim();
        this.sayfa_sayisi = toplamSayfaSayisi > 0
                ? String.valueOf(toplamSayfaSayisi)
                : "Bilinmiyor";

        if (bosMu(this.lisans)) {
            this.lisans = "Kontrol edilmedi";
        }
        if (bosMu(this.seslendirme_durumu)) {
            this.seslendirme_durumu = "Bekliyor";
        }
    }

    private static String temizle(String deger, String varsayilan) {
        if (bosMu(deger)) {
            return varsayilan;
        }
        return deger.trim();
    }

    private static boolean bosMu(String deger) {
        return deger == null || deger.isBlank() || "null".equalsIgnoreCase(deger.trim());
    }
}
