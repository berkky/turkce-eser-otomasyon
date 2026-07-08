import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class UretimOrkestratoruApp {
    private UretimOrkestratoruApp() {}

    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        Path proje = Path.of(System.getProperty("user.dir"));
        Path masaustu = Path.of(System.getProperty("user.home"), "Desktop");
        Path metin = ortamYol("ESER_METIN_ARSIVI", masaustu.resolve("metin-arsivi"));
        Path ses = ortamYol("ESER_SES_ARSIVI", masaustu.resolve("ses-arsivi"));
        Path kuyrukYolu = ortamYol("ESER_URETIM_KUYRUGU", masaustu.resolve("eser-otomasyon-kuyruk"));
        Files.createDirectories(metin); Files.createDirectories(ses); Files.createDirectories(kuyrukYolu);

        UretimKuyruguService kuyruk = new UretimKuyruguService(kuyrukYolu, ses);
        UretimOrkestratoruService ork = new UretimOrkestratoruService(proje, kuyruk);
        UretimPaketlemeService paket = new UretimPaketlemeService(proje, kuyruk);

        if (args.length > 0) {
            komutCalistir(args, metin, kuyruk, ork, paket);
            return;
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            while (true) {
                System.out.println("\n=== ESER OTOMASYON ÜRETİM ORKESTRATÖRÜ — ADIM 18 ===");
                System.out.println("1 - Metin ve ses arşivlerini uzlaştır");
                System.out.println("2 - Kuyruk durumunu göster");
                System.out.println("3 - Bir işin üretim planını göster");
                System.out.println("4 - Bir işi kaldığı yerden üret");
                System.out.println("5 - Tamamlanan sesi MP3 + M4B paketle");
                System.out.println("6 - Sıradaki hazır işi çalıştır");
                System.out.println("7 - Sistem doktoru");
                System.out.println("8 - İşin sağlayıcı politikasını değiştir");
                System.out.println("0 - Çıkış");
                System.out.print("Seçimin: ");
                String secim = in.readLine();
                if ("0".equals(secim)) return;
                try {
                    switch (secim) {
                        case "1" -> senkronizasyonYaz(kuyruk.senkronizeEt(metin));
                        case "2" -> durumYaz(kuyruk.listele());
                        case "3" -> { UretimIsi is = isSec(kuyruk, in); if (is != null) planYaz(ork.planla(is)); }
                        case "4" -> { UretimIsi is = isSec(kuyruk, in); if (is != null) interaktifCalistir(ork, is, in); }
                        case "5" -> { UretimIsi is = isSec(kuyruk, in); if (is != null) paketSonucuYaz(paket.paketle(is)); }
                        case "6" -> { UretimIsi is = siradaki(kuyruk.listele()); if (is == null) System.out.println("Çalıştırılacak iş yok."); else interaktifCalistir(ork, is, in); }
                        case "7" -> doktor(proje, metin, ses, kuyrukYolu);
                        case "8" -> { UretimIsi is = isSec(kuyruk, in); if (is != null) politikaDegistir(kuyruk, is, in); }
                        default -> System.out.println("Geçersiz seçim.");
                    }
                } catch (Exception e) {
                    System.err.println("İşlem tamamlanamadı: " + e.getMessage());
                }
            }
        }
    }

    private static void komutCalistir(String[] args, Path metin, UretimKuyruguService kuyruk,
                                     UretimOrkestratoruService ork, UretimPaketlemeService paket) throws Exception {
        String komut = args[0].toLowerCase(Locale.ROOT);
        switch (komut) {
            case "sync", "reconcile" -> senkronizasyonYaz(kuyruk.senkronizeEt(metin));
            case "status" -> durumYaz(kuyruk.listele());
            case "doctor" -> doktor(Path.of(System.getProperty("user.dir")), metin,
                    ortamYol("ESER_SES_ARSIVI", Path.of(System.getProperty("user.home"), "Desktop", "ses-arsivi")),
                    ortamYol("ESER_URETIM_KUYRUGU", Path.of(System.getProperty("user.home"), "Desktop", "eser-otomasyon-kuyruk")));
            case "plan" -> planYaz(ork.planla(gerekliIs(args, kuyruk)));
            case "run" -> sonucYaz(ork.calistir(gerekliIs(args, kuyruk), contains(args, "--override-limit")));
            case "run-next" -> {
                UretimIsi is = siradaki(kuyruk.listele());
                if (is == null) System.out.println("Çalıştırılacak iş yok.");
                else sonucYaz(ork.calistir(is, contains(args, "--override-limit")));
            }
            case "package" -> paketSonucuYaz(paket.paketle(gerekliIs(args, kuyruk)));
            default -> throw new IllegalArgumentException("Komutlar: sync/reconcile, status, doctor, plan <id>, run <id>, run-next, package <id>");
        }
    }


    private static void senkronizasyonYaz(UretimKuyruguService.SenkronizasyonSonucu s) {
        System.out.println("Yeni is: " + s.yeniIs());
        System.out.println("Mevcut nihai paketten tamamlanan: " + s.mevcutPaketTamamlandi());
        System.out.println("Kaynak degisikligiyle guncellenen: " + s.guncellenen());
        System.out.println("Degismeyen: " + s.degismeyen());
        if (s.mevcutPaketTamamlandi() > 0) {
            System.out.println("Koruma: Mevcut MP3/M4B paketleri bulundu; bu eserler yeniden TTS'e gonderilmeyecek.");
        }
    }

    private static void politikaDegistir(UretimKuyruguService kuyruk, UretimIsi is, BufferedReader in) throws Exception {
        System.out.println("1 - OTOMATIK (Google Chirp, ilk parçada hata olursa Piper)");
        System.out.println("2 - GOOGLE_CHIRP");
        System.out.println("3 - PIPER");
        System.out.println("4 - KURU_KOSU");
        System.out.print("Politika: ");
        UretimPolitikasi p = switch (in.readLine().trim()) {
            case "1" -> UretimPolitikasi.OTOMATIK;
            case "2" -> UretimPolitikasi.GOOGLE_CHIRP;
            case "3" -> UretimPolitikasi.PIPER;
            case "4" -> UretimPolitikasi.KURU_KOSU;
            default -> null;
        };
        if (p == null) { System.out.println("Değişiklik yapılmadı."); return; }
        UretimIsi guncel = is.politikaDegistir(p);
        kuyruk.kaydet(guncel);
        kuyruk.olayYaz(guncel, "Üretim politikası değiştirildi: " + p);
        System.out.println("Yeni politika: " + p);
    }

    private static void interaktifCalistir(UretimOrkestratoruService ork, UretimIsi is, BufferedReader in) throws Exception {
        UretimOrkestratoruService.Plan p = ork.planla(is);
        planYaz(p);
        String gerekli = p.maliyet().limitAsimi() ? "LIMITI_AS" : "EVET";
        System.out.print("Başlatmak için " + gerekli + " yaz: ");
        if (!gerekli.equalsIgnoreCase(in.readLine().trim())) { System.out.println("İptal edildi."); return; }
        sonucYaz(ork.calistir(is, p.maliyet().limitAsimi()));
    }

    private static UretimIsi gerekliIs(String[] args, UretimKuyruguService kuyruk) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("İş kimliği gerekli.");
        UretimIsi is = kuyruk.bul(args[1]);
        if (is == null) throw new IllegalArgumentException("İş bulunamadı: " + args[1]);
        return is;
    }

    private static UretimIsi isSec(UretimKuyruguService kuyruk, BufferedReader in) throws Exception {
        List<UretimIsi> liste = kuyruk.listele();
        if (liste.isEmpty()) { System.out.println("Kuyruk boş. Önce senkronize et."); return null; }
        for (int i = 0; i < liste.size(); i++) {
            UretimIsi x = liste.get(i);
            System.out.printf("%2d - %-45s | %-12s | %d/%d%n", i + 1, x.id(), x.durum(), x.hazirParca(), x.toplamParca());
        }
        System.out.print("İş numarası (0=iptal): ");
        int n;
        try { n = Integer.parseInt(in.readLine().trim()); } catch (Exception e) { return null; }
        return n >= 1 && n <= liste.size() ? liste.get(n - 1) : null;
    }

    private static UretimIsi siradaki(List<UretimIsi> liste) {
        return liste.stream().filter(i -> i.durum() == UretimDurumu.HAZIR || i.durum() == UretimDurumu.BEKLIYOR
                || i.durum() == UretimDurumu.DURAKLATILDI || i.durum() == UretimDurumu.HATALI).findFirst().orElse(null);
    }

    private static void durumYaz(List<UretimIsi> liste) {
        if (liste.isEmpty()) { System.out.println("Kuyruk boş."); return; }
        System.out.println("\n--- ÜRETİM KUYRUĞU ---");
        for (UretimIsi x : liste) {
            System.out.printf("%-45s | %-12s | %4d/%-4d | politika=%-12s | %s%n",
                    x.id(), x.durum(), x.hazirParca(), x.toplamParca(), x.politika(), x.eserAdi());
            if (!x.sonHata().isBlank()) System.out.println("  Son hata: " + x.sonHata());
        }
    }

    private static void planYaz(UretimOrkestratoruService.Plan p) {
        System.out.println("\n--- ÜRETİM PLANI ---");
        System.out.println("İş: " + p.is().id());
        System.out.println("Eser: " + p.is().eserAdi());
        System.out.println("Sağlayıcı: " + (p.saglayici() == null ? "Kuru koşu" : p.saglayici().ad() + " / " + p.saglayici().ses()));
        System.out.println("Politika: " + p.secimAciklamasi());
        System.out.println("Hazır parça: " + p.hazirParca() + "/" + p.toplamParca());
        System.out.println("Eksik karakter: " + UretimMaliyetKoruyucu.bicimle(p.eksikKarakter()));
        System.out.printf(Locale.forLanguageTag("tr-TR"), "Tahmini ses: %.2f saat%n", p.maliyet().tahminiSesSaati());
        if (p.maliyet().limitAsimi()) {
            System.out.println("MALİYET KORUYUCU: Eksik karakter, bulut limitini aşıyor. Limit="
                    + UretimMaliyetKoruyucu.bicimle(p.maliyet().limit()));
        }
    }

    private static void sonucYaz(UretimOrkestratoruService.Sonuc s) {
        System.out.println("Sonuç: " + s.mesaj());
        System.out.println("Bu çalışmada üretilen: " + s.uretilen());
        System.out.println("Hazır: " + s.hazir() + "/" + s.toplam());
    }

    private static void paketSonucuYaz(UretimPaketlemeService.Sonuc s) {
        System.out.println("Paketleme tamamlandı.");
        System.out.println("Bölüm: " + s.toplamBolum());
        System.out.println("Tam MP3: " + s.tamMp3());
        System.out.println("M4B: " + s.m4b());
        System.out.println("Paket: " + s.paketKlasoru());
    }

    private static void doktor(Path proje, Path metin, Path ses, Path kuyruk) {
        System.out.println("\n--- SİSTEM DOKTORU ---");
        System.out.println("Java: " + System.getProperty("java.version"));
        System.out.println("Proje: " + proje.toAbsolutePath());
        System.out.println("Metin arşivi: " + metin + " | " + (Files.isDirectory(metin) ? "HAZIR" : "YOK"));
        System.out.println("Ses arşivi: " + ses + " | " + (Files.isDirectory(ses) ? "HAZIR" : "YOK"));
        System.out.println("Kuyruk: " + kuyruk + " | " + (Files.isDirectory(kuyruk) ? "HAZIR" : "YOK"));
        try {
            TtsSaglayici g = new GoogleCloudTtsSaglayici(ortam("URETIM_GOOGLE_TTS_VOICE", "tr-TR-Chirp3-HD-Kore"));
            System.out.println("Google Chirp: " + (g.hazirlik().hazir() ? "HAZIR" : "KAPALI") + " | " + g.hazirlik().mesaj());
        } catch (Exception e) { System.out.println("Google Chirp: KAPALI | " + e.getMessage()); }
        try {
            TtsSaglayici p = new PiperTtsSaglayici(proje);
            System.out.println("Piper: " + (p.hazirlik().hazir() ? "HAZIR" : "KAPALI") + " | " + p.hazirlik().mesaj());
        } catch (Exception e) { System.out.println("Piper: KAPALI | " + e.getMessage()); }
        FfmpegClient.KontrolSonucu f = new FfmpegClient(proje).kontrolEt();
        System.out.println("FFmpeg: " + (f.hazir() ? "HAZIR" : "KAPALI") + " | " + f.mesaj());
    }

    private static Path ortamYol(String ad, Path varsayilan) { String v = System.getenv(ad); return v == null || v.isBlank() ? varsayilan : Path.of(v.trim()); }
    private static String ortam(String ad, String varsayilan) { String v = System.getenv(ad); return v == null || v.isBlank() ? varsayilan : v.trim(); }
    private static boolean contains(String[] a, String x) { for (String s : a) if (x.equalsIgnoreCase(s)) return true; return false; }
}
