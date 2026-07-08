public final class Adim22Dogrulama {
    public static void main(String[] args) {
        Utf8Konsol.etkinlestir();
        String onSayfalar = """
                ASTRONOMİ KİTABI
                Orijinal Adı The Astronomy Book
                İngilizce Aslından Çeviren Ahmet Fethi
                1. Basım: 2018
                ISBN 978-605-171-555-1
                © 2017, ALFA Basım Yayım Dağıtım Ltd. Şti.
                Kitabın tüm yayın hakları Alfa Basım Yayım Dağıtım Ltd. Şti.'ne aittir.
                Eser sahiplerinin manevi ve mali hakları saklıdır.
                KATKIDA BULUNANLAR
                JACQUELINE MITTON, DANIŞMAN EDİTÖR
                DAVID W. HUGHES
                ROBERT DINWIDDIE
                """;

        YerelKunyeAnalizService.Sonuc r = new YerelKunyeAnalizService()
                .analizEt(onSayfalar, "Astronomi Alfa Yayınları", "Astronomi Alfa Yayınları.pdf");
        bekle("Astronomi Kitabı", r.eserAdi(), "eser adı");
        bekle("Kolektif", r.yazar(), "yazar");
        bekle("Alfa Basım Yayım Dağıtım Ltd. Şti", r.yayinevi(), "yayınevi");
        bekle("2018", r.yayinYili(), "yayın yılı");
        bekle("9786051715551", r.isbn(), "ISBN");
        bekle("The Astronomy Book", r.orijinalAdi(), "orijinal ad");
        bekle("Ahmet Fethi", r.cevirmen(), "çevirmen");
        if (!YerelKunyeAnalizService.isbnGecerli(r.isbn())) hata("ISBN doğrulaması başarısız");

        YerelKunyeAnalizService.Sonuc gecersiz = new YerelKunyeAnalizService()
                .analizEt("BAŞLIK\nISBN 978-605-171-555-2", "Başlık", "baslik.pdf");
        if (!"Bilinmiyor".equals(gecersiz.isbn())) hata("geçersiz ISBN kabul edildi: " + gecersiz.isbn());

        EserMetadata mevcut = new EserMetadata();
        mevcut.eserAdi = "Astronomi Alfa Yayınları";
        mevcut.eserTuru = "Kitap";
        mevcut.yazar = "Bilinmiyor";
        mevcut.yayinevi = "Bilinmiyor";
        mevcut.yayinYili = "Bilinmiyor";
        mevcut.isbn = "Bilinmiyor";
        mevcut.guvenPuani = 0.47;
        mevcut.metadataDurumu = "KONTROL_GEREKIYOR";

        EserMetadata onarilmis = new MetadataCikarmaService()
                .yerelOnar(mevcut, onSayfalar, "ESER-00006 - Astronomi Alfa Yayınları.pdf");
        bekle("Astronomi Alfa Yayınları", onarilmis.eserAdi, "kanonik eser adı korunmalı");
        bekle("KONTROL_GEREKIYOR", onarilmis.metadataDurumu, "metadata durumu");
        if (onarilmis.guvenPuani > 0.67) hata("yerel onarım güven tavanını aştı: " + onarilmis.guvenPuani);

        String ilkOnarim = onarilmis.map().toString();
        EserMetadata ikinci = new MetadataCikarmaService()
                .yerelOnar(onarilmis, onSayfalar, "ESER-00006 - Astronomi Alfa Yayınları.pdf");
        if (!ilkOnarim.equals(ikinci.map().toString())) hata("metadata onarımı idempotent değil");

        System.out.println("OK: Yerel künye motoru temiz örnekte başlık/yazar/yayınevi/yıl/ISBN çıkardı.");
        System.out.println("OK: Orijinal ad, çevirmen ve telif bilgisi çıkarıldı.");
        System.out.println("OK: Geçersiz ISBN reddedildi.");
        System.out.println("OK: Yerel/OCR onarımı kanonik başlığı değiştirmedi ve otomatik HAZIR yapmadı.");
        System.out.println("OK: İkinci onarım aynı sonucu verdi; kanıt tekrarı oluşmadı.");
        System.out.println("ADIM 22 GÜVENLİ GERİYE DÖNÜK DOĞRULAMA: BAŞARILI");
    }

    private static void bekle(String beklenen, String gercek, String alan) {
        if (!beklenen.equals(gercek)) hata(alan + " beklenen='" + beklenen + "' gerçek='" + gercek + "'");
    }

    private static void hata(String mesaj) { throw new IllegalStateException("ADIM 22 TEST HATASI: " + mesaj); }
}
