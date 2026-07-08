public final class Adim23Dogrulama {
    public static void main(String[] args) {
        Utf8Konsol.etkinlestir();

        String gurultu = """
                Başlık: Sayfa 1-20 KİTABI
                Yazar: ıdır. DK Yayınlarının Universe, Space, The Stars
                Yayınevi: Alfa Basım Yayım Dağıtım Ltd. Şti
                ISBN 978-605-171-555-1
                Çeviren: Ahmet Fethi
                """;

        YerelKunyeAnalizService.Sonuc r = new YerelKunyeAnalizService()
                .analizEt(gurultu, "Astronomi Alfa Yayınları", "ESER-00006 - Astronomi Alfa Yayınları.pdf");
        bekle("Astronomi Alfa Yayınları", r.eserAdi(), "OCR gürültüsünde mevcut başlık korunmalı");
        bekle("Bilinmiyor", r.yazar(), "şüpheli yazar reddedilmeli");
        bekle("9786051715551", r.isbn(), "geçerli ISBN korunmalı");

        EserMetadata bozuk = new EserMetadata();
        bozuk.eserAdi = "Sayfa 1-20 KiTABI";
        bozuk.yazar = "ıdır. DK Yayınlarının Universe, Space, The Stars";
        bozuk.yayinevi = "Alfa Basım Yayım Dağıtım Ltd. Şti";
        bozuk.isbn = "9786051715551";
        bozuk.cevirmen = "Ahmet Fethi";
        bozuk.archiveDosyaAdi = "Astronomi Alfa Yayınları.pdf";
        bozuk.kaynakUrl = "https://archive.org/details/bilim-kitabi-alfa-yayinlari/Astronomi%20Alfa%20Yay%C4%B1nlar%C4%B1/";

        bekle("Astronomi Alfa Yayınları", MetadataGuvenlikService.kanonikBaslik(bozuk, bozuk.eserAdi),
                "Archive.org kanonik başlığı");
        bekle("Bilinmiyor", MetadataGuvenlikService.guvenliYazar(bozuk.yazar), "şüpheli yazar temizliği");
        if (!MetadataGuvenlikService.supheliBaslik("Sayfa 1-20 KiTABI")) hata("şüpheli başlık algılanmadı");
        if (MetadataGuvenlikService.supheliBaslik("Astronomi Alfa Yayınları")) hata("geçerli başlık şüpheli sayıldı");
        if (MetadataGuvenlikService.yerelOnarimGuveni(bozuk) > 0.67) hata("güven tavanı aşıldı");

        System.out.println("OK: OCR sayfa aralığı başlık olarak reddedildi.");
        System.out.println("OK: Cümle parçası/kitap açıklaması yazar olarak reddedildi.");
        System.out.println("OK: Archive.org dosya adı kanonik başlık olarak geri kazanıldı.");
        System.out.println("OK: Yerel metadata güven puanı otomatik onay eşiğinin altında tutuldu.");
        System.out.println("ADIM 23 DOĞRULAMA: BAŞARILI");
    }

    private static void bekle(String beklenen, String gercek, String alan) {
        if (!beklenen.equals(gercek)) hata(alan + " beklenen='" + beklenen + "' gerçek='" + gercek + "'");
    }

    private static void hata(String mesaj) { throw new IllegalStateException("ADIM 23 TEST HATASI: " + mesaj); }
}
