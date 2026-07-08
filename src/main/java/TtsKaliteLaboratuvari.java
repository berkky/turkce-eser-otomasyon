import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class TtsKaliteLaboratuvari {
    private static final DateTimeFormatter DAMGA = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        Path proje = Path.of("").toAbsolutePath().normalize();
        List<TtsSaglayici> saglayicilar = List.of(
                new ElevenLabsTtsSaglayici(),
                new OpenAITtsSaglayici(),
                new GeminiTtsSaglayici(proje),
                new GoogleCloudTtsSaglayici("tr-TR-Chirp3-HD-Charon"),
                new GoogleCloudTtsSaglayici("tr-TR-Chirp3-HD-Fenrir"),
                new GoogleCloudTtsSaglayici("tr-TR-Chirp3-HD-Achernar"),
                new GoogleCloudTtsSaglayici("tr-TR-Chirp3-HD-Kore"),
                new AzureTtsSaglayici(),
                new PiperTtsSaglayici(proje)
        );
        try (BufferedReader konsol = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            System.out.println("\n=== TÜRKÇE TTS KALİTE LABORATUVARI ===");
            durumYaz(saglayicilar);
            while (true) {
                System.out.println("\n1 - Hızlı kör test (edebi + telaffuz)");
                System.out.println("2 - Tam kör test (5 içerik türü)");
                System.out.println("3 - Doldurulmuş puanlama Excel'ini hesapla");
                System.out.println("4 - Sağlayıcı durumunu yeniden göster");
                System.out.println("0 - Çıkış");
                System.out.print("Seçimin: ");
                String secim = konsol.readLine();
                if ("0".equals(secim)) return;
                if ("4".equals(secim)) { durumYaz(saglayicilar); continue; }
                if ("3".equals(secim)) { siralamaHesapla(konsol); continue; }
                if ("1".equals(secim) || "2".equals(secim)) {
                    List<TtsUretimIstegi> tum = TtsOrnekMetinleri.tumu();
                    List<TtsUretimIstegi> ornekler = "1".equals(secim) ? List.of(tum.get(0), tum.get(4)) : tum;
                    paketUret(proje, konsol, saglayicilar, ornekler);
                }
            }
        }
    }

    private static void durumYaz(List<TtsSaglayici> saglayicilar) {
        System.out.println("\n--- SAĞLAYICI DURUMU ---");
        int hazir = 0;
        for (TtsSaglayici s : saglayicilar) {
            TtsSaglayici.Hazirlik h;
            try { h = s.hazirlik(); } catch (Exception e) { h = TtsSaglayici.Hazirlik.degil(e.getMessage()); }
            if (h.hazir()) hazir++;
            System.out.printf("%-26s : %-6s | model=%s | ses=%s | %s%n",
                    s.ad(), h.hazir() ? "HAZIR" : "KAPALI", s.model(), s.ses(), h.mesaj());
        }
        System.out.println("Hazır sağlayıcı: " + hazir + "/" + saglayicilar.size());
        System.out.println("Not: API anahtarları ekrana yazdırılmaz.");
    }

    private static void paketUret(Path proje,
                                  BufferedReader konsol,
                                  List<TtsSaglayici> saglayicilar,
                                  List<TtsUretimIstegi> ornekler) throws Exception {
        List<TtsSaglayici> hazirlar = new ArrayList<>();
        for (TtsSaglayici s : saglayicilar) if (s.hazirlik().hazir()) hazirlar.add(s);
        if (hazirlar.size() < 2) {
            System.out.println("Kör karşılaştırma için en az 2 sağlayıcı hazır olmalı. Piper + en az bir bulut sağlayıcısı kur.");
            return;
        }
        int karakter = ornekler.stream().mapToInt(o -> o.metin().length()).sum();
        System.out.println("\n--- ÜRETİM PLANI ---");
        System.out.println("Sağlayıcı: " + hazirlar.size());
        System.out.println("Metin örneği: " + ornekler.size());
        System.out.println("Toplam API/yerel üretim çağrısı: " + (hazirlar.size() * ornekler.size()));
        System.out.println("Sağlayıcı başına karakter: " + karakter);
        System.out.println("Bu test ücretli API kullanabilir. Yalnızca kısa örnekler üretilecek.");
        System.out.print("Başlatmak için EVET yaz: ");
        if (!"EVET".equalsIgnoreCase(konsol.readLine().trim())) {
            System.out.println("İptal edildi."); return;
        }

        Path kok = Path.of(System.getProperty("user.home"), "Desktop", "tts-kalite-laboratuvari",
                LocalDateTime.now().format(DAMGA));
        Path ham = kok.resolve("ham-ciktilar");
        Path kor = kok.resolve("kor-dinleme");
        Files.createDirectories(ham); Files.createDirectories(kor);
        List<TtsUretimSonucu> sonuclar = new ArrayList<>();
        List<String> hatalar = new ArrayList<>();

        int toplam = hazirlar.size() * ornekler.size(); int sayac = 0;
        for (TtsSaglayici s : hazirlar) {
            for (TtsUretimIstegi o : ornekler) {
                sayac++;
                System.out.println("\n[" + sayac + "/" + toplam + "] " + s.ad() + " → " + o.metinTuru());
                try {
                    TtsUretimSonucu sonuc = s.uret(o, ham);
                    sonuclar.add(sonuc);
                    System.out.printf(Locale.forLanguageTag("tr-TR"), "Hazır: %s | %.1f KB | %.1f sn%n",
                            sonuc.sesDosyasi().getFileName(), sonuc.dosyaBoyutu() / 1024.0, sonuc.sureMs() / 1000.0);
                } catch (Exception e) {
                    String hata = s.ad() + " / " + o.ornekId() + ": " + e.getMessage();
                    hatalar.add(hata); System.err.println("HATA: " + hata);
                }
            }
        }
        long basariliSaglayiciSayisi = sonuclar.stream()
                .map(TtsUretimSonucu::saglayiciKimligi)
                .distinct()
                .count();

        if (basariliSaglayiciSayisi < 2) {
            System.out.println("Kör karşılaştırma oluşturulmadı: en az 2 farklı sağlayıcı başarıyla ses üretmeli.");
            System.out.println("Başarılı sağlayıcı: " + basariliSaglayiciSayisi
                    + " | başarılı ses dosyası: " + sonuclar.size()
                    + " | hata: " + hatalar.size());
            raporYaz(kok, hatalar);
            return;
        }

        List<TtsUretimSonucu> karisik = new ArrayList<>(sonuclar);
        Collections.shuffle(karisik, new Random());
        List<TtsPuanlamaExcelService.KorKayit> kayitlar = new ArrayList<>();
        int no = 1;
        for (TtsUretimSonucu s : karisik) {
            String kod = String.format("K%03d", no++);
            Path hedef = kor.resolve(kod + ".mp3");
            Files.copy(s.sesDosyasi(), hedef, StandardCopyOption.REPLACE_EXISTING);
            kayitlar.add(new TtsPuanlamaExcelService.KorKayit(kod, s, hedef));
        }
        Path excel = TtsPuanlamaExcelService.olustur(kok.resolve("tts-kor-test-puanlama.xlsx"), kayitlar, ornekler);
        String talimat = "TÜRKÇE TTS KÖR TEST\n\n"
                + "1) kor-dinleme klasöründeki K001, K002... dosyalarını karışık sırada dinle.\n"
                + "2) tts-kor-test-puanlama.xlsx dosyasındaki 1-10 alanlarını doldur.\n"
                + "3) Sağlayıcı adlarını görmeden puanlamayı bitir. Cevap anahtarı Excel içinde çok gizli sayfadadır.\n"
                + "4) Programı yeniden çalıştır ve 3 numaralı menüden Excel'i hesaplat.\n"
                + "5) Uygulamada son kullanıcıya sesin yapay zekâ ile üretildiğini açıkça belirt.\n";
        TtsLaboratuvarYardimci.metinYaz(kok.resolve("NASIL-PUANLANIR.txt"), talimat);
        raporYaz(kok, hatalar);
        System.out.println("\nKÖR TEST PAKETİ HAZIR");
        System.out.println("Klasör : " + kok);
        System.out.println("Sesler : " + kor);
        System.out.println("Excel  : " + excel);
        if (!hatalar.isEmpty()) System.out.println("Bazı sağlayıcı/örnekler hata verdi; HATALAR.txt dosyasını kontrol et.");
    }

    private static void raporYaz(Path kok, List<String> hatalar) throws Exception {
        String metin = hatalar.isEmpty() ? "Hata yok.\n" : String.join(System.lineSeparator(), hatalar) + System.lineSeparator();
        TtsLaboratuvarYardimci.metinYaz(kok.resolve("HATALAR.txt"), metin);
    }

    private static void siralamaHesapla(BufferedReader konsol) throws Exception {
        Path ana = Path.of(System.getProperty("user.home"), "Desktop", "tts-kalite-laboratuvari");
        Path varsayilan = enYeniExcel(ana);
        System.out.print("Excel yolu" + (varsayilan == null ? "" : " [Enter = " + varsayilan + "]") + ": ");
        String girdi = konsol.readLine().trim();
        Path excel = girdi.isBlank() ? varsayilan : Path.of(girdi.replace("\"", ""));
        if (excel == null || !Files.isRegularFile(excel)) { System.out.println("Excel bulunamadı."); return; }
        List<TtsPuanlamaExcelService.Siralama> s = TtsPuanlamaExcelService.siralamaOku(excel);
        if (s.isEmpty()) { System.out.println("Henüz puan girilmemiş."); return; }
        System.out.println("\n--- TÜRKÇE TTS SIRALAMASI ---");
        int i = 1;
        for (var x : s) {
            System.out.printf(Locale.forLanguageTag("tr-TR"), "%d. %s | %.2f/10 | %s | %s | puanlanan=%d%n",
                    i++, x.ad(), x.ortalama(), x.model(), x.ses(), x.puanlananOrnek());
        }
    }

    private static Path enYeniExcel(Path ana) throws Exception {
        if (!Files.isDirectory(ana)) return null;
        try (var s = Files.walk(ana, 3)) {
            return s.filter(p -> p.getFileName().toString().equals("tts-kor-test-puanlama.xlsx"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElse(null);
        }
    }
}
