import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

public final class KaynakAlimOrkestratoruApp {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        Path proje = Path.of(System.getProperty("user.dir"));
        Path masaustu = Path.of(System.getProperty("user.home"), "Desktop");
        EserKaynakAlimService servis = new EserKaynakAlimService(
                proje,
                ortamYol("ESER_GELEN_KLASORU", masaustu.resolve("gelen-eser")),
                ortamYol("ESER_ARSIVI", masaustu.resolve("arsiv")),
                ortamYol("ESER_METIN_ARSIVI", masaustu.resolve("metin-arsivi")),
                ortamYol("ESER_SES_ARSIVI", masaustu.resolve("ses-arsivi")),
                ortamYol("ESER_URETIM_KUYRUGU", masaustu.resolve("eser-otomasyon-kuyruk"))
        );
        if (args.length > 0) { komut(args, servis); return; }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            while (true) {
                System.out.println("\n=== ESER FABRİKASI — ADIM 23 ===");
                System.out.println("1 - Yerel PDF/EPUB/TXT/HTML dosyasını işle");
                System.out.println("2 - URL veya Archive.org bağlantısını işle");
                System.out.println("3 - Metin dosyasındaki URL listesini toplu işle");
                System.out.println("4 - Archive.org bağlantısında seçilecek dosyayı önceden göster");
                System.out.println("5 - gelen-eser klasöründeki tüm dosyaları işle");
                System.out.println("6 - Alınmış eser durumunu göster");
                System.out.println("7 - Mevcut manifestleri Excel kataloğuyla uzlaştır");
                System.out.println("8 - Sistem doktoru");
                System.out.println("9 - Bir eserin metadata/künye kaydını yerel olarak onar");
                System.out.println("10 - Kontrol gereken tüm metadata kayıtlarını güvenli zenginleştir");
                System.out.println("11 - Şüpheli/bozulmuş bir metadata kaydını güvenli kurtar");
                System.out.println("0 - Çıkış");
                System.out.print("Seçimin: ");
                String s = in.readLine();
                if ("0".equals(s)) return;
                try {
                    switch (s) {
                        case "1" -> { System.out.print("Dosya yolu: "); sonuc(servis.yerelDosyaAl(Path.of(tirnakSil(in.readLine())))); }
                        case "2" -> { System.out.print("URL: "); sonuc(servis.urlAl(in.readLine().trim())); }
                        case "3" -> { System.out.print("URL listesi dosyası: "); urlToplu(servis.urlListesiAl(Path.of(tirnakSil(in.readLine())))); }
                        case "4" -> { System.out.print("Archive.org URL: "); archiveSecim(servis.archiveIncele(in.readLine().trim())); }
                        case "5" -> toplu(servis.gelenKutusuIsle());
                        case "6" -> durum(servis);
                        case "7" -> uzlastir(servis.kataloguUzlastir());
                        case "8" -> doktor(servis);
                        case "9" -> { System.out.print("Eser ID (örn. 6): "); metadataOnar(servis.metadataOnar(Integer.parseInt(in.readLine().trim()))); }
                        case "10" -> metadataOnarTum(servis.metadataOnarTum());
                        case "11" -> { System.out.print("Eser ID (örn. 6): "); metadataKurtar(servis.metadataKurtar(Integer.parseInt(in.readLine().trim()))); }
                        default -> System.out.println("Geçersiz seçim.");
                    }
                } catch (Exception e) {
                    System.err.println("İşlem tamamlanamadı: " + e.getMessage());
                }
            }
        }
    }

    private static void komut(String[] a, EserKaynakAlimService s) throws Exception {
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "doctor" -> doktor(s);
            case "import-local" -> { gerekli(a,2,"Dosya yolu gerekli."); sonuc(s.yerelDosyaAl(Path.of(a[1]))); }
            case "import-url" -> { gerekli(a,2,"URL gerekli."); sonuc(s.urlAl(a[1])); }
            case "import-url-list" -> { gerekli(a,2,"URL listesi yolu gerekli."); urlToplu(s.urlListesiAl(Path.of(a[1]))); }
            case "inspect-archive" -> { gerekli(a,2,"Archive.org URL gerekli."); archiveSecim(s.archiveIncele(a[1])); }
            case "inbox" -> toplu(s.gelenKutusuIsle());
            case "status" -> durum(s);
            case "reconcile-catalog" -> uzlastir(s.kataloguUzlastir());
            case "repair-metadata" -> { gerekli(a,2,"Eser ID gerekli."); metadataOnar(s.metadataOnar(eserId(a[1]))); }
            case "repair-all-metadata" -> metadataOnarTum(s.metadataOnarTum());
            case "recover-metadata" -> { gerekli(a,2,"Eser ID gerekli."); metadataKurtar(s.metadataKurtar(eserId(a[1]))); }
            default -> throw new IllegalArgumentException("Komutlar: doctor, import-local, import-url, import-url-list, inspect-archive, inbox, status, reconcile-catalog, repair-metadata, repair-all-metadata, recover-metadata");
        }
    }

    private static void doktor(EserKaynakAlimService s) throws Exception {
        var d=s.doktor();
        System.out.println("\n--- ESER FABRİKASI DOKTORU ---");
        System.out.println("Java: " + System.getProperty("java.version"));
        System.out.println("Proje: " + d.proje());
        System.out.println("Gelen: " + d.gelen());
        System.out.println("Kaynak arşivi: " + d.arsiv());
        System.out.println("Metin arşivi: " + d.metin());
        System.out.println("Ses arşivi: " + d.ses());
        System.out.println("Kuyruk: " + d.kuyruk());
        System.out.println("Excel kataloğu: " + d.katalog());
        System.out.println("Yerel künye motoru: HAZIR — OCR sonucu otomatik onaylanmaz");
        YerelOcrService.Durum ocr=YerelOcrService.durum();
        System.out.println("Yerel OCR (Tesseract): " + (ocr.hazir()?"HAZIR — "+ocr.aciklama():"YOK — taranmış yerel PDF için isteğe bağlı"));
        System.out.println("Gemini metadata: " + (ortamVar("GEMINI_API_KEY") ? "HAZIR (isteğe bağlı)" : "YOK — engel değil"));
        System.out.println("OpenAI metadata: " + (ortamVar("OPENAI_API_KEY") ? "ANAHTAR VAR (yerel sonuç yetersizse denenir)" : "YOK — engel değil"));
        System.out.println("ElevenLabs TTS: " + (ortamVar("ELEVENLABS_API_KEY") ? "HAZIR" : "ANAHTAR YOK"));
        System.out.println("Durum: " + (d.hazir()?"HAZIR":"KAPALI"));
        System.out.println("Destek: PDF, EPUB, TXT, Markdown, HTML, doğrudan URL, Archive.org alt klasör bağlantıları");
    }

    private static void sonuc(KaynakAlimSonucu x) {
        System.out.println("\n--- ESER FABRİKASI SONUCU ---");
        System.out.println("Eser: ESER-"+String.format("%05d",x.eserId())+" | "+x.eserAdi());
        System.out.println("Tür: "+x.tur()+" | Tekrar: "+(x.tekrar()?"EVET":"HAYIR"));
        System.out.println("Bölüm: "+x.bolumSayisi()+" | TTS parçası: "+x.ttsParcaSayisi()+" | Karakter: "+x.toplamKarakter());
        if(x.kaynakDosyasi()!=null) System.out.println("Arşiv dosyası: "+x.kaynakDosyasi());
        if(x.metinEserKlasoru()!=null) System.out.println("Metin klasörü: "+x.metinEserKlasoru());
        System.out.println(x.mesaj());
    }

    private static void archiveSecim(ArchiveOrgCozumleyici.DosyaSecimi x) {
        System.out.println("\n--- ARCHIVE.ORG DOSYA SEÇİMİ ---");
        System.out.println("Identifier: "+x.baglanti().identifier());
        System.out.println("Hedef alt yol: "+(x.baglanti().hedefYolu().isBlank()?"(yok)":x.baglanti().hedefYolu()));
        System.out.println("Ana kaynak: "+x.ana().ad()+" | puan="+x.ana().puan()+" | boyut="+boyut(x.ana().boyut()));
        if(x.metinYedegi()!=null) System.out.println("Metin yedeği: "+x.metinYedegi().ad()+" | boyut="+boyut(x.metinYedegi().boyut()));
        System.out.println("En güçlü adaylar:");
        x.siraliAdaylar().stream().limit(5).forEach(a -> System.out.println("- "+a.puan()+" | "+boyut(a.boyut())+" | "+a.ad()));
    }

    private static void toplu(EserKaynakAlimService.TopluSonuc x) {
        System.out.println("Toplam: "+x.toplam()+" | başarılı="+x.basarili()+" | tekrar="+x.tekrar()+" | hatalı="+x.hatali());
        x.mesajlar().forEach(m->System.out.println("- "+m));
    }
    private static void urlToplu(EserKaynakAlimService.TopluUrlSonucu x) {
        System.out.println("Toplam URL: "+x.toplam()+" | başarılı="+x.basarili()+" | tekrar="+x.tekrar()+" | hatalı="+x.hatali());
        x.mesajlar().forEach(m->System.out.println("- "+m));
    }
    private static void uzlastir(EserKaynakAlimService.UzlastirmaSonucu x) {
        System.out.println("Kataloğa eklenen="+x.eklendi()+" | değişmeyen="+x.degismedi()+" | hatalı="+x.hatali());
        x.mesajlar().forEach(m->System.out.println("- "+m));
    }
    private static void durum(EserKaynakAlimService s) throws Exception {
        var l=s.durumlar(); if(l.isEmpty()){System.out.println("Kaynak alım kaydı yok.");return;}
        System.out.println("\n--- ALINMIŞ ESERLER ---");
        for(var x:l) System.out.printf("ESER-%05d | %-5s | %8d krk | %3d bölüm | TTS=%-5s | META=%-18s | %s%n",
                x.eserId(),x.tur(),x.karakter(),x.bolum(),x.ttsHazir()?"HAZIR":"YOK",x.metadataDurumu(),x.eserAdi());
    }

    private static void metadataOnar(EserKaynakAlimService.MetadataOnarimSonucu x) {
        System.out.println("\n--- METADATA ONARIM SONUCU ---");
        System.out.println(String.format(Locale.ROOT,"ESER-%05d | %s",x.eserId(),x.eserAdi()));
        System.out.println("Yazar: "+x.yazar());
        System.out.println("Yayınevi: "+x.yayinevi());
        System.out.println("Yıl: "+x.yayinYili()+" | ISBN: "+x.isbn());
        System.out.println("Çevirmen: "+x.cevirmen());
        System.out.println("Durum: "+x.metadataDurumu()+" | Güven: "+String.format(Locale.ROOT,"%.0f%%",x.guvenPuani()*100));
        if(x.arsivDosyasi()!=null) System.out.println("Arşiv dosyası: "+x.arsivDosyasi());
        System.out.println(x.mesaj());
    }
    private static void metadataKurtar(EserKaynakAlimService.MetadataKurtarmaSonucu x) {
        System.out.println("\n--- METADATA GÜVENLİ KURTARMA SONUCU ---");
        System.out.println(String.format(Locale.ROOT,"ESER-%05d | %s",x.eserId(),x.eserAdi()));
        System.out.println("Yazar: "+x.yazar());
        System.out.println("Yayınevi: "+x.yayinevi()+" | ISBN: "+x.isbn());
        System.out.println("Durum: "+x.metadataDurumu()+" | Güven: "+String.format(Locale.ROOT,"%.0f%%",x.guvenPuani()*100));
        System.out.println("Arşiv dosyası: "+x.arsivDosyasi());
        System.out.println("Geri alma geçmişi: "+x.gecmisKlasoru());
        System.out.println(x.mesaj());
    }
    private static void metadataOnarTum(EserKaynakAlimService.TopluMetadataOnarimSonucu x) {
        System.out.println("Toplam="+x.toplam()+" | güncellenen="+x.guncellenen()+" | değişmeyen="+x.degismeyen()+" | hatalı="+x.hatali());
        x.mesajlar().forEach(m->System.out.println("- "+m));
    }
    private static int eserId(String s){
        String x=s.trim().toUpperCase(Locale.ROOT).replace("ESER-","");
        return Integer.parseInt(x);
    }

    private static String boyut(long b){return b<0?"?":String.format(Locale.ROOT,"%.1f MB",b/1024.0/1024.0);}
    private static Path ortamYol(String a,Path v){String x=System.getenv(a);return x==null||x.isBlank()?v:Path.of(x.trim());}
    private static boolean ortamVar(String a){String x=System.getenv(a);return x!=null&&!x.isBlank();}
    private static String tirnakSil(String s){String x=s.trim();return x.length()>1&&x.startsWith("\"")&&x.endsWith("\"")?x.substring(1,x.length()-1):x;}
    private static void gerekli(String[] a,int n,String m){if(a.length<n)throw new IllegalArgumentException(m);}
}
