import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class UretimKuyruguService {
    private static final Pattern PARCA = Pattern.compile("^\\d{3,5}-\\d{3,5}\\.txt$", Pattern.CASE_INSENSITIVE);
    private final Path kuyrukKlasoru;
    private final Path sesArsivi;

    public UretimKuyruguService(Path kuyrukKlasoru, Path sesArsivi) {
        this.kuyrukKlasoru = kuyrukKlasoru;
        this.sesArsivi = sesArsivi;
    }

    public SenkronizasyonSonucu senkronizeEt(Path metinArsivi) throws Exception {
        Files.createDirectories(kuyrukKlasoru);
        Files.createDirectories(sesArsivi);
        if (!Files.isDirectory(metinArsivi)) return new SenkronizasyonSonucu(0, 0, 0, 0);
        int yeni = 0;
        int legacyTamam = 0;
        int guncellenen = 0;
        int degismeyen = 0;
        try (Stream<Path> s = Files.list(metinArsivi)) {
            for (Path eser : s.filter(Files::isDirectory).sorted().toList()) {
                Path tts = eser.resolve("tts-parcalari");
                List<Path> parcalar = parcalariListele(tts);
                if (parcalar.isEmpty()) continue;
                String id = guvenliId(eser.getFileName().toString());
                Path isKlasoru = kuyrukKlasoru.resolve(id);
                Path durumDosyasi = isKlasoru.resolve("is.properties");
                Path sesEser = sesArsivi.resolve(eser.getFileName().toString());
                MevcutUretimAlgilayici.KaynakOzeti kaynak = MevcutUretimAlgilayici.kaynakOzeti(parcalar);
                MevcutUretimAlgilayici.Sonuc mevcut = MevcutUretimAlgilayici.algila(sesEser, isKlasoru, kaynak);

                UretimIsi eski = Files.isRegularFile(durumDosyasi)
                        ? UretimIsi.propertiesOku(UretimDosyaYardimci.propertiesOku(durumDosyasi)) : null;

                if (mevcut.tamamlanmis()) {
                    UretimPolitikasi politika = eski == null ? UretimPolitikasi.OTOMATIK : eski.politika();
                    String olusturma = eski == null ? OffsetDateTime.now().toString() : eski.olusturmaZamani();
                    UretimIsi tamam = new UretimIsi(id, eser.getFileName().toString(), eser, tts, sesEser,
                            politika, UretimDurumu.TAMAMLANDI, parcalar.size(), parcalar.size(), kaynak.toplamKarakter(),
                            "legacy-package", "", olusturma, OffsetDateTime.now().toString());
                    boolean zaten = eski != null && eski.durum() == UretimDurumu.TAMAMLANDI
                            && eski.hazirParca() == parcalar.size() && "legacy-package".equals(eski.aktifSaglayici());
                    kaydet(tamam);
                    if (!zaten) {
                        olayYaz(tamam, mevcut.mesaj() + ". M4B=" + mevcut.m4b() + ", MP3=" + mevcut.tamMp3()
                                + ". Yeniden TTS uretimi engellendi.");
                        legacyTamam++;
                    } else degismeyen++;
                    continue;
                }

                if (eski != null) {
                    boolean kaynakDegisti = eski.toplamParca() != parcalar.size()
                            || eski.toplamKarakter() != kaynak.toplamKarakter()
                            || !eski.ttsKlasoru().toAbsolutePath().equals(tts.toAbsolutePath());
                    if (kaynakDegisti) {
                        UretimIsi guncel = new UretimIsi(eski.id(), eser.getFileName().toString(), eser, tts,
                                sesEser, eski.politika(), UretimDurumu.HAZIR, parcalar.size(), 0,
                                kaynak.toplamKarakter(), eski.aktifSaglayici(),
                                mevcut.mesaj().startsWith("Nihai paket var") ? mevcut.mesaj() : "Kaynak metin degisti; yeniden dogrulama gerekli",
                                eski.olusturmaZamani(), OffsetDateTime.now().toString());
                        kaydet(guncel);
                        olayYaz(guncel, "Kaynak degisikligi algilandi; is yeniden hazirlandi. Parca=" + parcalar.size());
                        guncellenen++;
                    } else degismeyen++;
                    continue;
                }

                UretimIsi is = new UretimIsi(id, eser.getFileName().toString(), eser, tts, sesEser,
                        UretimPolitikasi.OTOMATIK, UretimDurumu.HAZIR, parcalar.size(), 0,
                        kaynak.toplamKarakter(), "", "", OffsetDateTime.now().toString(), OffsetDateTime.now().toString());
                kaydet(is);
                olayYaz(is, "Is kuyruga eklendi. Parca=" + parcalar.size() + ", karakter=" + kaynak.toplamKarakter());
                yeni++;
            }
        }
        return new SenkronizasyonSonucu(yeni, legacyTamam, guncellenen, degismeyen);
    }

    public List<UretimIsi> listele() throws Exception {
        Files.createDirectories(kuyrukKlasoru);
        List<UretimIsi> sonuc = new ArrayList<>();
        try (Stream<Path> s = Files.list(kuyrukKlasoru)) {
            for (Path dir : s.filter(Files::isDirectory).sorted().toList()) {
                Path f = dir.resolve("is.properties");
                if (Files.isRegularFile(f)) sonuc.add(UretimIsi.propertiesOku(UretimDosyaYardimci.propertiesOku(f)));
            }
        }
        return sonuc.stream().sorted(Comparator.comparing(UretimIsi::olusturmaZamani)).toList();
    }

    public UretimIsi bul(String id) throws Exception {
        Path f = kuyrukKlasoru.resolve(id).resolve("is.properties");
        return Files.isRegularFile(f) ? UretimIsi.propertiesOku(UretimDosyaYardimci.propertiesOku(f)) : null;
    }

    public void kaydet(UretimIsi is) throws Exception {
        Path dir = kuyrukKlasoru.resolve(is.id());
        Files.createDirectories(dir);
        UretimDosyaYardimci.propertiesAtomikYaz(dir.resolve("is.properties"), is.properties(), "Eser Otomasyon Uretim Isi");
        UretimDosyaYardimci.metinAtomikYaz(dir.resolve("is.json"), json(is));
    }

    public Path isKlasoru(UretimIsi is) { return kuyrukKlasoru.resolve(is.id()); }

    public void olayYaz(UretimIsi is, String mesaj) throws Exception {
        Path log = isKlasoru(is).resolve("olaylar.log");
        Files.createDirectories(log.getParent());
        Files.writeString(log, OffsetDateTime.now() + " | " + mesaj + System.lineSeparator(),
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    public static List<Path> parcalariListele(Path tts) throws Exception {
        if (!Files.isDirectory(tts)) return List.of();
        try (Stream<Path> s = Files.list(tts)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> PARCA.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    private static String guvenliId(String ad) {
        String s = ad.toLowerCase(Locale.ROOT)
                .replace('ç','c').replace('ğ','g').replace('ı','i').replace('ö','o').replace('ş','s').replace('ü','u')
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (s.length() > 80) s = s.substring(0, 80).replaceAll("-+$", "");
        return "IS-" + (s.isBlank() ? Integer.toHexString(ad.hashCode()) : s);
    }

    private static String json(UretimIsi i) {
        return "{\n" +
                "  \"surum\": 1,\n" +
                "  \"id\": \"" + UretimDosyaYardimci.jsonKacir(i.id()) + "\",\n" +
                "  \"eserAdi\": \"" + UretimDosyaYardimci.jsonKacir(i.eserAdi()) + "\",\n" +
                "  \"politika\": \"" + i.politika() + "\",\n" +
                "  \"durum\": \"" + i.durum() + "\",\n" +
                "  \"toplamParca\": " + i.toplamParca() + ",\n" +
                "  \"hazirParca\": " + i.hazirParca() + ",\n" +
                "  \"toplamKarakter\": " + i.toplamKarakter() + ",\n" +
                "  \"aktifSaglayici\": \"" + UretimDosyaYardimci.jsonKacir(i.aktifSaglayici()) + "\",\n" +
                "  \"sonHata\": \"" + UretimDosyaYardimci.jsonKacir(i.sonHata()) + "\",\n" +
                "  \"metinEserKlasoru\": \"" + UretimDosyaYardimci.jsonKacir(i.metinEserKlasoru().toString()) + "\",\n" +
                "  \"sesEserKlasoru\": \"" + UretimDosyaYardimci.jsonKacir(i.sesEserKlasoru().toString()) + "\",\n" +
                "  \"olusturmaZamani\": \"" + i.olusturmaZamani() + "\",\n" +
                "  \"guncellemeZamani\": \"" + i.guncellemeZamani() + "\"\n" +
                "}\n";
    }
    public record SenkronizasyonSonucu(int yeniIs, int mevcutPaketTamamlandi, int guncellenen, int degismeyen) {
        public int toplamDegisiklik() { return yeniIs + mevcutPaketTamamlandi + guncellenen; }
    }

}
