import java.io.BufferedReader;
import java.nio.file.Path;
import java.util.Locale;

/**
 * ElevenLabs ve yerel Piper arasındaki sağlayıcı seçimini yönetir.
 * Otomatik modda ElevenLabs kullanılabilir değilse Piper'a geçer.
 */
public final class SeslendirmeOrkestratorService {
    private SeslendirmeOrkestratorService() {
    }

    public static Sonuc interaktifUret(Path projeKlasoru,
                                       Path metinArsivKlasoru,
                                       Path sesArsivKlasoru,
                                       BufferedReader konsol) {
        try {
            System.out.println();
            System.out.println("--- SESLENDİRME SAĞLAYICI MENÜSÜ ---");
            System.out.println("1 - Otomatik: Yerel Piper (ElevenLabs tam üretim kapalı)");
            System.out.println("2 - Yalnız ElevenLabs tam üretim (açık onay gerekir)");
            System.out.println("3 - Yalnız Yerel Piper");
            System.out.println("4 - ElevenLabs durumunu göster");
            System.out.println("5 - ElevenLabs önizleme üret");
            System.out.println("0 - Seslendirmeyi atla");
            System.out.print("Seçimin: ");

            int secim = sayiOku(konsol.readLine(), -1);
            return switch (secim) {
                case 1 -> otomatikUret(projeKlasoru, metinArsivKlasoru, sesArsivKlasoru, konsol);
                case 2 -> elevenLabsUret(metinArsivKlasoru, sesArsivKlasoru, konsol);
                case 3 -> piperUret(projeKlasoru, metinArsivKlasoru, sesArsivKlasoru, konsol);
                case 4 -> {
                    ElevenLabsDurumApp.main(new String[0]);
                    yield Sonuc.atlandi("ElevenLabs durum gösterildi");
                }
                case 5 -> onizlemeUret(metinArsivKlasoru, sesArsivKlasoru, konsol);
                default -> Sonuc.atlandi("Kullanıcı seslendirmeyi atladı");
            };
        } catch (Exception e) {
            System.err.println("Seslendirme sağlayıcı seçimi tamamlanamadı: " + e.getMessage());
            return Sonuc.hatali(null, 0, 0, 0, "Bilinmiyor", e.getMessage());
        }
    }

    private static Sonuc otomatikUret(Path projeKlasoru,
                                      Path metinArsivKlasoru,
                                      Path sesArsivKlasoru,
                                      BufferedReader konsol) {
        System.out.println("Otomatik mod: tam eser ElevenLabs üretimi varsayılan olarak kapalıdır.");
        System.out.println("ElevenLabs önizleme için: elevenlabs-onizleme.ps1");
        System.out.println("Yerel Piper yedeği kullanılacak.");
        return piperUret(projeKlasoru, metinArsivKlasoru, sesArsivKlasoru, konsol);
    }

    private static Sonuc onizlemeUret(Path metinArsivKlasoru,
                                      Path sesArsivKlasoru,
                                      BufferedReader konsol) throws Exception {
        System.out.print("Önizleme eser ID (varsayılan 5): ");
        String cevap = konsol.readLine();
        int eserId = 5;
        if (cevap != null && !cevap.isBlank()) {
            eserId = Integer.parseInt(cevap.trim());
        }
        ElevenLabsOnizlemeService.OnizlemeSonucu sonuc =
                ElevenLabsOnizlemeService.uret(eserId, metinArsivKlasoru, sesArsivKlasoru, null);
        if (sonuc.basarili()) {
            System.out.println(sonuc.mesaj());
            return new Sonuc(true, false, false, sonuc.mesaj(),
                    "ElevenLabs Önizleme", "MP3", sonuc.mp3() == null ? null : sonuc.mp3().getParent(),
                    sonuc.mevcutKullanildi() ? 0 : 1, 1, 1);
        }
        return Sonuc.hatali(null, 0, 0, 0, "ElevenLabs Önizleme", sonuc.mesaj());
    }

    private static Sonuc elevenLabsUret(Path metinArsivKlasoru,
                                        Path sesArsivKlasoru,
                                        BufferedReader konsol) {
        ElevenLabsTamEserService.Sonuc sonuc =
                ElevenLabsTamEserService.interaktifUret(metinArsivKlasoru, sesArsivKlasoru, konsol);
        return new Sonuc(
                sonuc.basarili(),
                sonuc.atlandi(),
                sonuc.tumuTamam(),
                sonuc.mesaj(),
                "ElevenLabs",
                "MP3",
                sonuc.eserSesKlasoru(),
                sonuc.buCalismadaUretilen(),
                sonuc.toplamHazir(),
                sonuc.toplamParca()
        );
    }

    private static Sonuc piperUret(Path projeKlasoru,
                                   Path metinArsivKlasoru,
                                   Path sesArsivKlasoru,
                                   BufferedReader konsol) {
        PiperTopluService.Sonuc sonuc = PiperTopluService.interaktifUret(
                projeKlasoru, metinArsivKlasoru, sesArsivKlasoru, konsol
        );
        return new Sonuc(
                sonuc.basarili(),
                sonuc.atlandi(),
                sonuc.tumuTamam(),
                sonuc.mesaj(),
                "Yerel Piper — " + sonuc.sesModeli(),
                "MP3",
                sonuc.eserSesKlasoru(),
                sonuc.buCalismadaUretilen(),
                sonuc.toplamHazir(),
                sonuc.toplamParca()
        );
    }

    private static boolean kullaniciIptaliMi(String mesaj) {
        if (mesaj == null) {
            return false;
        }
        String temiz = mesaj.toLowerCase(Locale.forLanguageTag("tr-TR"));
        return temiz.contains("kullanıcı")
                || temiz.contains("seçilmedi")
                || temiz.contains("planı seçilmedi")
                || temiz.contains("iptal");
    }

    private static int sayiOku(String metin, int varsayilan) {
        try {
            return Integer.parseInt(metin == null ? "" : metin.trim());
        } catch (Exception e) {
            return varsayilan;
        }
    }

    private static String sayiBicimle(long sayi) {
        return String.format(Locale.forLanguageTag("tr-TR"), "%,d", sayi);
    }

    public record Sonuc(boolean basarili,
                        boolean atlandi,
                        boolean tumuTamam,
                        String mesaj,
                        String saglayici,
                        String format,
                        Path eserSesKlasoru,
                        int buCalismadaUretilen,
                        int toplamHazir,
                        int toplamParca) {
        public static Sonuc atlandi(String mesaj) {
            return new Sonuc(false, true, false, mesaj,
                    "Yok", "Yok", null, 0, 0, 0);
        }

        public static Sonuc hatali(Path klasor,
                                   int uretilen,
                                   int hazir,
                                   int toplam,
                                   String saglayici,
                                   String mesaj) {
            return new Sonuc(false, false, false, mesaj,
                    saglayici, "Bilinmiyor", klasor, uretilen, hazir, toplam);
        }
    }
}
