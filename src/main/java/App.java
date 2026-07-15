import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class App {
    public static void main(String[] args) {
        Utf8Konsol.etkinlestir();
        Path projeKlasoru = Path.of(System.getProperty("user.dir"));
        EserVeriYollari yollar = EserVeriYollari.varsayilan();
        if (yollar.legacyDetected()) {
            System.err.println(EserVeriYollari.LEGACY_WARNING
                    + " — canonical Desktop\\ESER kullanılacak; otomatik migration yapılmayacak.");
        }
        Path gelenKlasoru = yollar.gelen();
        Path arsivKlasoru = yollar.arsiv();
        Path tekrarKlasoru = yollar.canonicalRoot().resolve("tekrarlar");
        Path metinArsivKlasoru = yollar.metin();
        Path sesArsivKlasoru = yollar.ses();
        Path eskiExcelYolu = yollar.canonicalRoot().resolve("kitap-arsivi.xlsx");
        Path excelYolu = yollar.katalog();

        try {
            DosyaArsivService.klasorHazirla(gelenKlasoru);
            DosyaArsivService.klasorHazirla(arsivKlasoru);
            DosyaArsivService.klasorHazirla(tekrarKlasoru);
            DosyaArsivService.klasorHazirla(metinArsivKlasoru);
            DosyaArsivService.klasorHazirla(sesArsivKlasoru);

            eskiExceliKorumaliSekildeTasi(eskiExcelYolu, excelYolu);

            ExcelArsivService excel = new ExcelArsivService(excelYolu);
            excel.sistemiHazirla();
            excel.eksikHashleriTamamla(arsivKlasoru);

            MetinArsivService.TopluSonuc baslangicMetinSonucu =
                    MetinArsivService.eksikEpubMetinleriniTamamla(arsivKlasoru, metinArsivKlasoru);
            TtsParcalamaService.TopluSonuc baslangicTtsSonucu =
                    TtsParcalamaService.eksikTtsParcalariniHazirla(metinArsivKlasoru);

            BufferedReader konsol = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8)
            );

            SeslendirmeOrkestratorService.Sonuc topluSesSonucu =
                    SeslendirmeOrkestratorService.interaktifUret(
                            projeKlasoru, metinArsivKlasoru, sesArsivKlasoru, konsol
                    );

            SesliKitapPaketlemeService.Sonuc paketlemeSonucu =
                    SesliKitapPaketlemeService.interaktifPaketle(
                            projeKlasoru, metinArsivKlasoru, sesArsivKlasoru, konsol
                    );

            ArchiveOrgService archiveOrg = new ArchiveOrgService(konsol);
            try {
                archiveOrg.interaktifIndir(gelenKlasoru);
            } catch (Exception e) {
                System.err.println("Archive.org indirme işlemi tamamlanamadı: " + e.getMessage());
                System.err.println("Mevcut gelen-pdf dosyaları işlenmeye devam edecek.");
            }

            List<Path> eserDosyalari = DosyaArsivService.eserDosyalariniListele(gelenKlasoru);
            if (eserDosyalari.isEmpty()) {
                System.out.println("Eser arşiv sistemi hazır.");
                System.out.println("gelen-pdf klasöründe işlenecek PDF veya EPUB bulunamadı.");
                System.out.println("Excel dosyası: " + excelYolu);
                System.out.println("Metin arşivi: " + metinArsivKlasoru);
                System.out.println("Ses arşivi: " + sesArsivKlasoru);
                System.out.println("Bu çalışmada hazırlanan EPUB metin arşivi: "
                        + baslangicMetinSonucu.hazirlanan());
                System.out.println("Bu çalışmada hazırlanan TTS paketleri: "
                        + baslangicTtsSonucu.hazirlanan());
                System.out.println("Bu çalışmada oluşturulan TTS parçası: "
                        + baslangicTtsSonucu.toplamParca());
                if (topluSesSonucu.basarili()) {
                    System.out.println("Ses sağlayıcısı: " + topluSesSonucu.saglayici());
                    System.out.println("Ses formatı: " + topluSesSonucu.format());
                    System.out.println("Bu çalışmada üretilen ses parçası: "
                            + topluSesSonucu.buCalismadaUretilen());
                    System.out.println("Toplam hazır ses parçası: "
                            + topluSesSonucu.toplamHazir() + "/" + topluSesSonucu.toplamParca());
                    System.out.println("Ses klasörü: " + topluSesSonucu.eserSesKlasoru());
                }
                if (paketlemeSonucu.basarili()) {
                    System.out.println("Hazır bölüm MP3: "
                            + paketlemeSonucu.hazirBolum() + "/" + paketlemeSonucu.toplamBolum());
                    System.out.println("Sesli kitap paket klasörü: " + paketlemeSonucu.paketKlasoru());
                }
                return;
            }

            String geminiApiKey = System.getenv("GEMINI_API_KEY");
            String openAiApiKey = System.getenv("OPENAI_API_KEY");

            GeminiClient gemini = geminiApiKey == null || geminiApiKey.isBlank()
                    ? null
                    : new GeminiClient(geminiApiKey);

            OpenAIClient openAI = openAiApiKey == null || openAiApiKey.isBlank()
                    ? null
                    : new OpenAIClient(openAiApiKey);

            YapayZekaAnalizService yapayZeka = new YapayZekaAnalizService(gemini, openAI);
            ManuelGirisService manuelGiris = new ManuelGirisService(konsol);

            int yeniIslenen = 0;
            int manuelIslenen = 0;
            int epubMetadataIleIslenen = 0;
            int eskiKayittanArsivlenen = 0;
            int tekrar = 0;
            int bekleyen = 0;
            int hatali = 0;
            int metinArsiviHazirlanan = baslangicMetinSonucu.hazirlanan();
            int metinArsiviHatali = baslangicMetinSonucu.hatali();
            int ttsPaketiHazirlanan = baslangicTtsSonucu.hazirlanan();
            int ttsParcasiOlusturulan = baslangicTtsSonucu.toplamParca();
            int ttsPaketiHatali = baslangicTtsSonucu.hatali();

            System.out.println("Toplam eser dosyası: " + eserDosyalari.size());
            System.out.println("Desteklenen türler: PDF, EPUB");
            System.out.println("Gemini: " +
                    (gemini == null ? "API anahtarı yok" : "hazır — " + gemini.getModel()));
            System.out.println("OpenAI yedeği: " +
                    (openAI == null ? "API anahtarı yok" : "hazır — " + openAI.getModel()));
            System.out.println("Manuel giriş yedeği: hazır");
            System.out.println("Excel yapısı: Eserler");
            System.out.println("EPUB tam metin arşivi: hazır");
            System.out.println("TTS metin parçalama sistemi: hazır");
            System.out.println("Ses sağlayıcı sistemi: ElevenLabs + Yerel Piper hazır");

            for (int i = 0; i < eserDosyalari.size(); i++) {
                Path eserDosyasi = eserDosyalari.get(i);
                String orijinalAd = eserDosyasi.getFileName().toString();
                KaynakBilgisi kaynakBilgisi = KaynakBilgisiService.oku(eserDosyasi);

                System.out.println();
                System.out.println("========================================");
                System.out.println("İşlenen dosya: " + orijinalAd);
                System.out.println("Dosya türü: " +
                        (DosyaArsivService.epubMi(eserDosyasi) ? "EPUB" : "PDF"));
                System.out.println("Dosya sırası: " + (i + 1) + "/" + eserDosyalari.size());

                try {
                    String hash = DosyaArsivService.sha256(eserDosyasi);
                    System.out.println("SHA-256: " + hash.substring(0, 16) + "...");

                    ExcelArsivService.Kayit adKaydi = excel.orijinalAdIleBul(orijinalAd);
                    if (adKaydi != null) {
                        Path mevcutArsiv = excel.kayitliArsivDosyasiniBul(adKaydi, arsivKlasoru);
                        String kayitHashi = adKaydi.hash;

                        if (mevcutArsiv != null) {
                            if (!gecerliHash(kayitHashi)) {
                                kayitHashi = DosyaArsivService.sha256(mevcutArsiv);
                                excel.hashGuncelle(adKaydi, kayitHashi);
                                adKaydi.hash = kayitHashi;
                            }

                            if (hash.equalsIgnoreCase(kayitHashi)) {
                                Path tekrarYolu = DosyaArsivService.tekrarlaraTasi(
                                        eserDosyasi, tekrarKlasoru, adKaydi.id
                                );
                                tekrar++;
                                System.out.println("Bu eser zaten arşivlenmiş.");
                                System.out.println("Gelen kopya tekrarlar klasörüne taşındı: " + tekrarYolu);
                                KaynakBilgisiService.sil(eserDosyasi);
                                continue;
                            }

                            System.out.println("Aynı dosya adıyla farklı içerik bulundu.");
                            System.out.println("Bu dosya yeni eser olarak işlenecek.");
                            adKaydi = null;

                        } else if (gecerliHash(kayitHashi) && !hash.equalsIgnoreCase(kayitHashi)) {
                            System.out.println("Aynı dosya adıyla farklı içerik bulundu.");
                            System.out.println("Bu dosya yeni eser olarak işlenecek.");
                            adKaydi = null;
                        }
                    }

                    if (adKaydi != null) {
                        if (!gecerliHash(adKaydi.hash)) {
                            excel.hashGuncelle(adKaydi, hash);
                            adKaydi.hash = hash;
                        }

                        int id = adKaydi.id > 0 ? adKaydi.id : excel.siradakiId();
                        Path arsivYolu = DosyaArsivService.arsiveTasi(
                                eserDosyasi, arsivKlasoru, id, adKaydi.eserBilgisi.eser_adi
                        );

                        try {
                            excel.arsivBilgisiniGuncelle(
                                    adKaydi, id, arsivYolu, hash, kaynakBilgisi
                            );
                        } catch (Exception e) {
                            DosyaArsivService.geriTasi(arsivYolu, eserDosyasi);
                            throw e;
                        }

                        if (DosyaArsivService.epubMi(arsivYolu)) {
                            try {
                                MetinArsivService.Sonuc metinSonucu = MetinArsivService.olustur(
                                        arsivYolu, metinArsivKlasoru, id, adKaydi.eserBilgisi
                                );
                                metinArsiviHazirlanan++;
                                System.out.println("EPUB tam metni hazırlandı: " + metinSonucu.tamMetinDosyasi());
                                try {
                                    TtsParcalamaService.Sonuc ttsSonucu =
                                            TtsParcalamaService.olustur(metinSonucu.metinKlasoru());
                                    ttsPaketiHazirlanan++;
                                    ttsParcasiOlusturulan += ttsSonucu.parcaSayisi();
                                    System.out.println("TTS parçaları hazırlandı: " + ttsSonucu.ttsKlasoru());
                                    System.out.println("Oluşturulan TTS parçası: " + ttsSonucu.parcaSayisi());
                                } catch (Exception ttsHatasi) {
                                    ttsPaketiHatali++;
                                    System.err.println("Tam metin hazırlandı ancak TTS parçaları oluşturulamadı: "
                                            + ttsHatasi.getMessage());
                                }
                            } catch (Exception metinHatasi) {
                                metinArsiviHatali++;
                                System.err.println("EPUB arşivlendi ancak tam metin hazırlanamadı: "
                                        + metinHatasi.getMessage());
                            }
                        }

                        eskiKayittanArsivlenen++;
                        System.out.println("Dosya, mevcut Excel kaydı kullanılarak arşivlendi.");
                        System.out.println("Arşiv yolu: " + arsivYolu);
                        KaynakBilgisiService.sil(eserDosyasi);
                        continue;
                    }

                    ExcelArsivService.Kayit hashKaydi = excel.hashIleBul(hash);
                    if (hashKaydi != null) {
                        Path tekrarYolu = DosyaArsivService.tekrarlaraTasi(
                                eserDosyasi, tekrarKlasoru, hashKaydi.id
                        );
                        tekrar++;
                        System.out.println("Aynı içerikteki eser daha önce arşivlenmiş.");
                        System.out.println("Yapay zekâ API'si kullanılmadı.");
                        System.out.println("Tekrar dosya: " + tekrarYolu);
                        KaynakBilgisiService.sil(eserDosyasi);
                        continue;
                    }

                    EserBilgisi bilgi;
                    String bilgiKaynagi;
                    String kullanilanModel;
                    boolean manuelKayit = false;
                    boolean epubMetadataKaydi = false;
                    String sayfaVeyaBolumBilgisi;
                    EpubHazirlayici.EpubVerisi epubVerisiTam = null;

                    if (DosyaArsivService.epubMi(eserDosyasi)) {
                        EpubHazirlayici.EpubVerisi epubVerisi = EpubHazirlayici.hazirla(
                                eserDosyasi.toFile(), 3
                        );
                        epubVerisiTam = epubVerisi;
                        EserBilgisi epubMetadata = epubVerisi.getMetadata();
                        sayfaVeyaBolumBilgisi = epubVerisi.getToplamBolumSayisi() > 0
                                ? epubVerisi.getToplamBolumSayisi() + " bölüm"
                                : "Bilinmiyor";

                        if (EpubHazirlayici.metadataYeterliMi(epubMetadata)) {
                            bilgi = epubMetadata;
                            bilgiKaynagi = "EPUB metadata";
                            kullanilanModel = "Yok";
                            epubMetadataKaydi = true;
                            System.out.println("EPUB metadata bilgileri bulundu; API kullanılmadı.");
                        } else if (!epubVerisi.getMetin().isBlank() && yapayZeka.kullanilabilirServisVarMi()) {
                            try {
                                PdfHazirlayici.PdfVerisi metinGirdisi = PdfHazirlayici.PdfVerisi.metinBelgesi(
                                        epubVerisi.getMetin(),
                                        "EPUB",
                                        "bölüm",
                                        Math.max(epubVerisi.getIncelenenBolumSayisi(), 1),
                                        Math.max(epubVerisi.getToplamBolumSayisi(), epubVerisi.getIncelenenBolumSayisi())
                                );
                                YapayZekaAnalizService.AnalizSonucu analizSonucu = yapayZeka.analizEt(metinGirdisi);
                                bilgi = EpubHazirlayici.metadataIleBirlestir(
                                        analizSonucu.getEserBilgisi(), epubMetadata
                                );
                                bilgiKaynagi = analizSonucu.getSaglayici() + " + EPUB metadata";
                                kullanilanModel = analizSonucu.getModel();
                            } catch (YapayZekaAnalizService.TumServislerKullanilamazException e) {
                                System.err.println("Yapay zekâ servisleri kullanılamadı.");
                                bilgi = manuelGiris.bilgiAl(eserDosyasi, e.getMessage(), kaynakBilgisi);
                                if (bilgi == null) {
                                    bekleyen++;
                                    continue;
                                }
                                bilgi = EpubHazirlayici.metadataIleBirlestir(bilgi, epubMetadata);
                                bilgiKaynagi = "Manuel giriş + EPUB metadata";
                                kullanilanModel = "Yok";
                                manuelKayit = true;
                            }
                        } else {
                            bilgi = manuelGiris.bilgiAl(
                                    eserDosyasi,
                                    "EPUB metadata yetersiz ve kullanılabilir yapay zekâ servisi yok.",
                                    kaynakBilgisi
                            );
                            if (bilgi == null) {
                                bekleyen++;
                                continue;
                            }
                            bilgi = EpubHazirlayici.metadataIleBirlestir(bilgi, epubMetadata);
                            bilgiKaynagi = "Manuel giriş + EPUB metadata";
                            kullanilanModel = "Yok";
                            manuelKayit = true;
                        }
                    } else {
                        int toplamSayfaSayisi = PdfHazirlayici.toplamSayfaSayisi(eserDosyasi.toFile());
                        sayfaVeyaBolumBilgisi = String.valueOf(toplamSayfaSayisi);

                        if (yapayZeka.kullanilabilirServisVarMi()) {
                            try {
                                PdfHazirlayici.PdfVerisi pdfVerisi = PdfHazirlayici.hazirla(
                                        eserDosyasi.toFile(), 3
                                );
                                toplamSayfaSayisi = pdfVerisi.getToplamSayfaSayisi();
                                sayfaVeyaBolumBilgisi = String.valueOf(toplamSayfaSayisi);

                                YapayZekaAnalizService.AnalizSonucu analizSonucu = yapayZeka.analizEt(pdfVerisi);
                                bilgi = analizSonucu.getEserBilgisi();
                                bilgiKaynagi = analizSonucu.getSaglayici();
                                kullanilanModel = analizSonucu.getModel();

                            } catch (YapayZekaAnalizService.TumServislerKullanilamazException e) {
                                System.err.println("Yapay zekâ servisleri kullanılamadı.");
                                bilgi = manuelGiris.bilgiAl(eserDosyasi, e.getMessage(), kaynakBilgisi);
                                if (bilgi == null) {
                                    bekleyen++;
                                    continue;
                                }
                                bilgiKaynagi = "Manuel giriş";
                                kullanilanModel = "Yok";
                                manuelKayit = true;
                            }
                        } else {
                            bilgi = manuelGiris.bilgiAl(
                                    eserDosyasi,
                                    "Kullanılabilir yapay zekâ servisi yok veya bu çalışma için devre dışı kaldı.",
                                    kaynakBilgisi
                            );
                            if (bilgi == null) {
                                bekleyen++;
                                continue;
                            }
                            bilgiKaynagi = "Manuel giriş";
                            kullanilanModel = "Yok";
                            manuelKayit = true;
                        }
                    }

                    bilgi.islemBilgileriniAyarla(bilgiKaynagi, kullanilanModel, 0);
                    bilgi.sayfa_sayisi = sayfaVeyaBolumBilgisi;
                    KaynakBilgisiService.esereUygula(bilgi, kaynakBilgisi);
                    bilgi.bilinmeyenleriDuzelt();

                    System.out.println();
                    System.out.println("--- ÇIKARILAN / GİRİLEN ESER BİLGİLERİ ---");
                    System.out.println("Bilgi kaynağı : " + bilgi.bilgi_kaynagi);
                    System.out.println("Model         : " + bilgi.kullanilan_ai_modeli);
                    System.out.println("Eser türü     : " + bilgi.eser_turu);
                    System.out.println("Eser adı      : " + bilgi.eser_adi);
                    System.out.println("Yazar         : " + bilgi.yazar);
                    System.out.println("Yayınevi      : " + bilgi.yayinevi);
                    System.out.println("Basım yılı    : " + bilgi.basim_yili);
                    System.out.println("Dil           : " + bilgi.dil);
                    System.out.println("Sayfa/Bölüm   : " + bilgi.sayfa_sayisi);
                    System.out.println("Kaynak URL    : " + bilgi.kaynak_url);
                    System.out.println("Lisans        : " + bilgi.lisans);

                    int yeniId = excel.siradakiId();
                    Path arsivYolu = DosyaArsivService.arsiveTasi(
                            eserDosyasi, arsivKlasoru, yeniId, bilgi.eser_adi
                    );

                    try {
                        excel.yeniKayitEkle(yeniId, orijinalAd, bilgi, arsivYolu, hash);
                    } catch (Exception e) {
                        DosyaArsivService.geriTasi(arsivYolu, eserDosyasi);
                        throw e;
                    }

                    if (DosyaArsivService.epubMi(arsivYolu)) {
                        try {
                            MetinArsivService.Sonuc metinSonucu = epubVerisiTam == null
                                    ? MetinArsivService.olustur(
                                            arsivYolu, metinArsivKlasoru, yeniId, bilgi
                                    )
                                    : MetinArsivService.olustur(
                                            arsivYolu, metinArsivKlasoru, yeniId, bilgi, epubVerisiTam
                                    );
                            metinArsiviHazirlanan++;
                            System.out.println("EPUB bölüm ve tam metin dosyaları hazırlandı.");
                            System.out.println("Tam metin: " + metinSonucu.tamMetinDosyasi());
                            System.out.println("Yazılan bölüm dosyası: " + metinSonucu.yazilanBolumSayisi());
                            try {
                                TtsParcalamaService.Sonuc ttsSonucu =
                                        TtsParcalamaService.olustur(metinSonucu.metinKlasoru());
                                ttsPaketiHazirlanan++;
                                ttsParcasiOlusturulan += ttsSonucu.parcaSayisi();
                                System.out.println("TTS parçaları hazırlandı: " + ttsSonucu.ttsKlasoru());
                                System.out.println("Oluşturulan TTS parçası: " + ttsSonucu.parcaSayisi());
                            } catch (Exception ttsHatasi) {
                                ttsPaketiHatali++;
                                System.err.println("Tam metin hazırlandı ancak TTS parçaları oluşturulamadı: "
                                        + ttsHatasi.getMessage());
                            }
                        } catch (Exception metinHatasi) {
                            metinArsiviHatali++;
                            System.err.println("EPUB arşivlendi ancak tam metin hazırlanamadı: "
                                    + metinHatasi.getMessage());
                        }
                    }

                    yeniIslenen++;
                    if (manuelKayit) {
                        manuelIslenen++;
                    }
                    if (epubMetadataKaydi) {
                        epubMetadataIleIslenen++;
                    }

                    System.out.println("Eser dosyası başarıyla arşive eklendi.");
                    System.out.println("Arşiv yolu: " + arsivYolu);
                    KaynakBilgisiService.sil(eserDosyasi);

                    if (i < eserDosyalari.size() - 1) {
                        Thread.sleep(1_000L);
                    }

                } catch (Exception e) {
                    hatali++;
                    System.err.println("Bu eser dosyası işlenirken hata oluştu: " + e.getMessage());
                }
            }

            System.out.println();
            System.out.println("========================================");
            System.out.println("İŞLEM TAMAMLANDI");
            System.out.println("Yeni işlenen eser sayısı       : " + yeniIslenen);
            System.out.println("EPUB metadata ile işlenen      : " + epubMetadataIleIslenen);
            System.out.println("Manuel girilerek işlenen       : " + manuelIslenen);
            System.out.println("Eski kayıttan arşivlenen       : " + eskiKayittanArsivlenen);
            System.out.println("Tekrar tespit edilen dosya     : " + tekrar);
            System.out.println("Kullanıcı tarafından bekletilen: " + bekleyen);
            System.out.println("Hatalı eser dosyası            : " + hatali);
            System.out.println("EPUB metin arşivi hazırlanan   : " + metinArsiviHazirlanan);
            System.out.println("EPUB metin hazırlama hatası    : " + metinArsiviHatali);
            System.out.println("TTS paketi hazırlanan          : " + ttsPaketiHazirlanan);
            System.out.println("TTS parçası oluşturulan        : " + ttsParcasiOlusturulan);
            System.out.println("TTS paketi hazırlama hatası    : " + ttsPaketiHatali);
            System.out.println("Excel dosyası                  : " + excelYolu);
            System.out.println("Arşiv klasörü                  : " + arsivKlasoru);
            System.out.println("Tekrarlar klasörü              : " + tekrarKlasoru);
            System.out.println("Metin arşivi klasörü           : " + metinArsivKlasoru);
            System.out.println("Ses arşivi klasörü             : " + sesArsivKlasoru);
            if (topluSesSonucu.basarili()) {
                System.out.println("Ses sağlayıcısı                : " + topluSesSonucu.saglayici());
                System.out.println("Ses formatı                    : " + topluSesSonucu.format());
                System.out.println("Bu çalışmada üretilen ses      : "
                        + topluSesSonucu.buCalismadaUretilen());
                System.out.println("Toplam hazır ses parçası       : "
                        + topluSesSonucu.toplamHazir() + "/" + topluSesSonucu.toplamParca());
                System.out.println("Eser ses klasörü               : " + topluSesSonucu.eserSesKlasoru());
            }
            if (paketlemeSonucu.basarili()) {
                System.out.println("Hazır bölüm MP3                : "
                        + paketlemeSonucu.hazirBolum() + "/" + paketlemeSonucu.toplamBolum());
                System.out.println("Sesli kitap paket klasörü      : " + paketlemeSonucu.paketKlasoru());
                if (paketlemeSonucu.tamEserMp3() != null) {
                    System.out.println("Tam eser MP3                   : " + paketlemeSonucu.tamEserMp3());
                }
                if (paketlemeSonucu.m4b() != null) {
                    System.out.println("Bölümlü M4B                    : " + paketlemeSonucu.m4b());
                }
            }

        } catch (Exception e) {
            System.err.println("Program başlatılamadı: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void eskiExceliKorumaliSekildeTasi(Path eskiExcel, Path yeniExcel) throws Exception {
        if (Files.exists(yeniExcel) || !Files.exists(eskiExcel) || Files.size(eskiExcel) == 0) {
            return;
        }

        Files.copy(eskiExcel, yeniExcel, StandardCopyOption.COPY_ATTRIBUTES);
        System.out.println("Eski Excel kayıtları korundu ve yeni eser arşivine kopyalandı.");
        System.out.println("Eski dosya: " + eskiExcel);
        System.out.println("Yeni dosya: " + yeniExcel);
    }

    private static boolean gecerliHash(String hash) {
        return hash != null && hash.matches("[a-fA-F0-9]{64}");
    }
}
