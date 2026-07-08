import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class UretimOrkestratoruService {
    private static final long EN_AZ_MP3 = 1_000L;
    private final Path projeKlasoru;
    private final UretimKuyruguService kuyruk;

    public UretimOrkestratoruService(Path projeKlasoru, UretimKuyruguService kuyruk) {
        this.projeKlasoru = projeKlasoru;
        this.kuyruk = kuyruk;
    }

    public Plan planla(UretimIsi is) throws Exception {
        List<Path> parcalar = UretimKuyruguService.parcalariListele(is.ttsKlasoru());
        if (is.durum() == UretimDurumu.TAMAMLANDI && is.hazirParca() >= is.toplamParca()) {
            UretimMaliyetKoruyucu.Plan maliyet = UretimMaliyetKoruyucu.plan(is.toplamKarakter(), 0, false);
            return new Plan(is, null, List.of(),
                    "Mevcut nihai paket dogrulandi; yeniden TTS uretimi yapilmayacak",
                    parcalar.size(), parcalar.size(), 0, maliyet);
        }
        UretimSaglayiciSecici.Secim secim = UretimSaglayiciSecici.sec(projeKlasoru, is.politika());
        TtsSaglayici s = secim.ana();
        long eksikKarakter = 0;
        int hazir = 0;
        for (Path p : parcalar) {
            String metin = Files.readString(p, StandardCharsets.UTF_8).trim();
            if (s != null && parcaGecerliMi(is, p, s)) hazir++;
            else eksikKarakter += metin.length();
        }
        boolean bulut = s != null && s.kimlik().startsWith("google-cloud");
        UretimMaliyetKoruyucu.Plan maliyet = UretimMaliyetKoruyucu.plan(is.toplamKarakter(), eksikKarakter, bulut);
        return new Plan(is, s, secim.yedekler(), secim.aciklama(), parcalar.size(), hazir, eksikKarakter, maliyet);
    }

    public Sonuc calistir(UretimIsi baslangic, boolean limitAsiminaIzinVer) throws Exception {
        if (baslangic.durum() == UretimDurumu.TAMAMLANDI && baslangic.hazirParca() >= baslangic.toplamParca()) {
            return new Sonuc(true, false, 0, baslangic.hazirParca(), baslangic.toplamParca(),
                    "Is zaten tamamlanmis; yeniden uretim atlandi");
        }
        Path kilit = kuyruk.isKlasoru(baslangic).resolve("uretim.lock");
        try {
            Files.writeString(kilit, ProcessHandle.current().pid() + " | " + OffsetDateTime.now(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            throw new IllegalStateException("Bu iş başka bir süreçte çalışıyor veya eski kilit kaldı: " + kilit);
        }

        UretimIsi is = baslangic;
        try {
            Plan plan = planla(is);
            if (is.politika() == UretimPolitikasi.KURU_KOSU) return new Sonuc(true, false, 0, plan.hazirParca(), plan.toplamParca(), "Kuru koşu tamamlandı");
            if (plan.maliyet().limitAsimi() && !limitAsiminaIzinVer) {
                is = is.guncelle(UretimDurumu.DURAKLATILDI, plan.hazirParca(),
                        plan.saglayici().kimlik(), "Bulut karakter limiti aşıldı");
                kuyruk.kaydet(is);
                kuyruk.olayYaz(is, "Maliyet koruyucu üretimi durdurdu. Eksik karakter=" + plan.eksikKarakter());
                return new Sonuc(false, true, 0, plan.hazirParca(), plan.toplamParca(), "Karakter limiti aşıldı");
            }

            TtsSaglayici saglayici = plan.saglayici();
            List<TtsSaglayici> yedekler = new ArrayList<>(plan.yedekler());
            is = is.guncelle(UretimDurumu.URETILIYOR, plan.hazirParca(), saglayici.kimlik(), "");
            kuyruk.kaydet(is);
            kuyruk.olayYaz(is, "Üretim başladı: " + saglayici.ad() + " / " + saglayici.ses());

            List<Path> parcalar = UretimKuyruguService.parcalariListele(is.ttsKlasoru());
            int uretilen = 0;
            int hazir = 0;
            for (Path metinDosyasi : parcalar) {
                if (parcaGecerliMi(is, metinDosyasi, saglayici)) {
                    hazir++;
                    continue;
                }
                String metin = Files.readString(metinDosyasi, StandardCharsets.UTF_8).trim();
                String parcaId = uzantisiz(metinDosyasi.getFileName().toString());
                Exception son = null;
                TtsUretimSonucu uretim = null;
                for (int deneme = 1; deneme <= 3; deneme++) {
                    try {
                        uretim = saglayici.uret(new TtsUretimIstegi(parcaId, "Eser bölümü", metin, "Metni aynen ve doğal Türkçe ile oku."),
                                is.sesEserKlasoru().resolve("uretim-parcalari"));
                        son = null;
                        break;
                    } catch (Exception e) {
                        son = e;
                        kuyruk.olayYaz(is, parcaId + " deneme " + deneme + " başarısız: " + e.getMessage());
                        if (deneme < 3) Thread.sleep(deneme * 1_500L);
                    }
                }
                if ((son != null || uretim == null) && uretilen == 0 && hazir == 0 && !yedekler.isEmpty()) {
                    TtsSaglayici yedek = yedekler.remove(0);
                    TtsSaglayici.Hazirlik yh = yedek.hazirlik();
                    if (yh.hazir()) {
                        kuyruk.olayYaz(is, "Ana sağlayıcı ilk parçada başarısız oldu; bütün iş için yedeğe geçiliyor: " + yedek.ad());
                        saglayici = yedek;
                        is = is.guncelle(UretimDurumu.URETILIYOR, 0, saglayici.kimlik(), "");
                        kuyruk.kaydet(is);
                        son = null;
                        uretim = null;
                        for (int deneme = 1; deneme <= 3; deneme++) {
                            try {
                                uretim = saglayici.uret(new TtsUretimIstegi(parcaId, "Eser bölümü", metin,
                                                "Metni aynen ve doğal Türkçe ile oku."),
                                        is.sesEserKlasoru().resolve("uretim-parcalari"));
                                son = null;
                                break;
                            } catch (Exception e) {
                                son = e;
                                kuyruk.olayYaz(is, parcaId + " yedek deneme " + deneme + " başarısız: " + e.getMessage());
                                if (deneme < 3) Thread.sleep(deneme * 1_500L);
                            }
                        }
                    }
                }
                if (son != null || uretim == null) {
                    String hata = "Parça üretilemedi: " + parcaId + " — " + (son == null ? "Bilinmeyen hata" : son.getMessage());
                    UretimIsi hatali = is.guncelle(UretimDurumu.DURAKLATILDI, hazir, saglayici.kimlik(), hata);
                    kuyruk.kaydet(hatali);
                    kuyruk.olayYaz(hatali, hata + ". Mevcut dosyalar korundu; farklı seslerin karışmaması için otomatik geçiş yapılmadı.");
                    return new Sonuc(false, true, uretilen, hazir, parcalar.size(), hata);
                }
                metadataYaz(is, metinDosyasi, uretim);
                uretilen++;
                hazir++;
                is = is.guncelle(UretimDurumu.URETILIYOR, hazir, saglayici.kimlik(), "");
                kuyruk.kaydet(is);
                System.out.printf("[%d/%d] Hazır: %s | %s%n", hazir, parcalar.size(), parcaId, saglayici.ad());
            }

            UretimDurumu durum = hazir == parcalar.size() ? UretimDurumu.SES_TAMAM : UretimDurumu.DURAKLATILDI;
            is = is.guncelle(durum, hazir, saglayici.kimlik(), "");
            kuyruk.kaydet(is);
            uretimManifestiYaz(is, saglayici, parcalar);
            kuyruk.olayYaz(is, "Ses üretimi tamamlandı. Hazır=" + hazir + "/" + parcalar.size());
            return new Sonuc(true, false, uretilen, hazir, parcalar.size(), "Ses üretimi tamamlandı");
        } catch (Exception e) {
            UretimIsi hatali = is.guncelle(UretimDurumu.HATALI, is.hazirParca(), is.aktifSaglayici(), e.getMessage());
            kuyruk.kaydet(hatali);
            kuyruk.olayYaz(hatali, "Kritik hata: " + e.getMessage());
            throw e;
        } finally {
            Files.deleteIfExists(kilit);
        }
    }

    private boolean parcaGecerliMi(UretimIsi is, Path metin, TtsSaglayici s) {
        try {
            String id = uzantisiz(metin.getFileName().toString());
            Path mp3 = is.sesEserKlasoru().resolve("uretim-parcalari").resolve(s.kimlik()).resolve(id + ".mp3");
            Path meta = metadataYolu(is, s, id);
            if (!Files.isRegularFile(mp3) || Files.size(mp3) < EN_AZ_MP3 || !Files.isRegularFile(meta)) return false;
            Properties p = UretimDosyaYardimci.propertiesOku(meta);
            return UretimDosyaYardimci.sha256(metin).equals(p.getProperty("metinSha256"))
                    && s.kimlik().equals(p.getProperty("saglayiciKimligi"))
                    && s.model().equals(p.getProperty("model"))
                    && s.ses().equals(p.getProperty("ses"));
        } catch (Exception e) { return false; }
    }

    private void metadataYaz(UretimIsi is, Path metin, TtsUretimSonucu u) throws Exception {
        Properties p = new Properties();
        p.setProperty("parcaId", u.ornekId());
        p.setProperty("metinDosyasi", metin.toAbsolutePath().toString());
        p.setProperty("metinSha256", UretimDosyaYardimci.sha256(metin));
        p.setProperty("saglayiciKimligi", u.saglayiciKimligi());
        p.setProperty("saglayiciAdi", u.saglayiciAdi());
        p.setProperty("model", u.model());
        p.setProperty("ses", u.ses());
        p.setProperty("sesDosyasi", u.sesDosyasi().toAbsolutePath().toString());
        p.setProperty("karakter", String.valueOf(u.karakter()));
        p.setProperty("dosyaBoyutu", String.valueOf(u.dosyaBoyutu()));
        p.setProperty("sureMs", String.valueOf(u.sureMs()));
        p.setProperty("uretimZamani", OffsetDateTime.now().toString());
        UretimDosyaYardimci.propertiesAtomikYaz(metadataYolu(is, u.saglayiciKimligi(), u.ornekId()), p, "TTS Parca Metadata");
    }

    private Path metadataYolu(UretimIsi is, TtsSaglayici s, String id) { return metadataYolu(is, s.kimlik(), id); }
    private Path metadataYolu(UretimIsi is, String saglayici, String id) {
        return is.sesEserKlasoru().resolve("uretim-metadata").resolve(saglayici).resolve(id + ".properties");
    }

    private void uretimManifestiYaz(UretimIsi is, TtsSaglayici s, List<Path> parcalar) throws Exception {
        StringBuilder j = new StringBuilder();
        j.append("{\n  \"surum\": 1,\n")
                .append("  \"isId\": \"").append(UretimDosyaYardimci.jsonKacir(is.id())).append("\",\n")
                .append("  \"eserAdi\": \"").append(UretimDosyaYardimci.jsonKacir(is.eserAdi())).append("\",\n")
                .append("  \"saglayici\": \"").append(UretimDosyaYardimci.jsonKacir(s.ad())).append("\",\n")
                .append("  \"saglayiciKimligi\": \"").append(UretimDosyaYardimci.jsonKacir(s.kimlik())).append("\",\n")
                .append("  \"model\": \"").append(UretimDosyaYardimci.jsonKacir(s.model())).append("\",\n")
                .append("  \"ses\": \"").append(UretimDosyaYardimci.jsonKacir(s.ses())).append("\",\n")
                .append("  \"toplamParca\": ").append(parcalar.size()).append(",\n")
                .append("  \"tamamlanmaZamani\": \"").append(OffsetDateTime.now()).append("\"\n}\n");
        UretimDosyaYardimci.metinAtomikYaz(is.sesEserKlasoru().resolve("uretim-manifest.json"), j.toString());
    }

    private static String uzantisiz(String ad) { int i = ad.lastIndexOf('.'); return i > 0 ? ad.substring(0, i) : ad; }

    public record Plan(UretimIsi is, TtsSaglayici saglayici, List<TtsSaglayici> yedekler, String secimAciklamasi,
                       int toplamParca, int hazirParca, long eksikKarakter,
                       UretimMaliyetKoruyucu.Plan maliyet) {}
    public record Sonuc(boolean basarili, boolean duraklatildi, int uretilen,
                        int hazir, int toplam, String mesaj) {}
}
