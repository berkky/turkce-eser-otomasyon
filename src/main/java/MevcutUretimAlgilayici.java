import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Daha once paketlenmis nihai sesleri yeniden TTS'e gondermemek icin ses arsivini uzlastirir.
 * Eski sistemlerde parca metadata'si bulunmasa bile guclu bir nihai paket sinyali
 * (M4B + tam MP3 veya paket manifesti) varsa mevcut ciktinin kaynak anlik goruntusune
 * baglanmasini saglar.
 */
public final class MevcutUretimAlgilayici {
    private static final long EN_AZ_NIHAI_SES = 100_000L;
    private static final String KAYIT_DOSYASI = "mevcut-paket-kaydi.properties";

    private MevcutUretimAlgilayici() {}

    public static KaynakOzeti kaynakOzeti(List<Path> parcalar) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long karakter = 0;
        for (Path p : parcalar.stream().sorted(Comparator.comparing(x -> x.getFileName().toString())).toList()) {
            String metin = Files.readString(p, StandardCharsets.UTF_8).trim();
            karakter += metin.length();
            md.update(p.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(metin.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0xff);
        }
        return new KaynakOzeti(parcalar.size(), karakter, HexFormat.of().formatHex(md.digest()));
    }

    public static Sonuc algila(Path sesEserKlasoru, Path isKlasoru, KaynakOzeti kaynak) throws Exception {
        if (!Files.isDirectory(sesEserKlasoru)) return Sonuc.yok();

        List<Path> m4bler = new ArrayList<>();
        List<Path> tamMp3ler = new ArrayList<>();
        List<Path> manifestler = new ArrayList<>();
        try (Stream<Path> s = Files.walk(sesEserKlasoru, 6)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                if (geciciVeyaParca(p)) continue;
                String ad = p.getFileName().toString().toLowerCase(Locale.ROOT);
                long boyut = Files.size(p);
                if (ad.endsWith(".m4b") && boyut >= EN_AZ_NIHAI_SES) m4bler.add(p);
                else if (ad.endsWith(".mp3") && boyut >= EN_AZ_NIHAI_SES && tamMp3Adayi(p)) tamMp3ler.add(p);
                else if (ad.equals("paket-manifest.json") || ad.contains("manifest") && ad.endsWith(".json")) manifestler.add(p);
            }
        }

        m4bler.sort(Comparator.comparingLong(MevcutUretimAlgilayici::boyut).reversed());
        tamMp3ler.sort(Comparator.comparingLong(MevcutUretimAlgilayici::boyut).reversed());
        manifestler.sort(Comparator.comparing(Path::toString));

        boolean gucluPaket = (!m4bler.isEmpty() && !tamMp3ler.isEmpty())
                || (!m4bler.isEmpty() && !manifestler.isEmpty());
        if (!gucluPaket) return Sonuc.yok();

        Path kayit = isKlasoru.resolve(KAYIT_DOSYASI);
        if (Files.isRegularFile(kayit)) {
            Properties p = UretimDosyaYardimci.propertiesOku(kayit);
            String eskiKaynak = p.getProperty("kaynakBilesikSha256", "");
            String eskiM4b = p.getProperty("m4b", "");
            String eskiMp3 = p.getProperty("tamMp3", "");
            boolean kaynakAyni = kaynak.bilesikSha256().equals(eskiKaynak)
                    && kaynak.parcaSayisi() == Integer.parseInt(p.getProperty("kaynakParcaSayisi", "-1"))
                    && kaynak.toplamKarakter() == Long.parseLong(p.getProperty("kaynakToplamKarakter", "-1"));
            boolean ciktXVar = (!eskiM4b.isBlank() && Files.isRegularFile(Path.of(eskiM4b)))
                    || (!eskiMp3.isBlank() && Files.isRegularFile(Path.of(eskiMp3)));
            if (kaynakAyni && ciktXVar) {
                return new Sonuc(true, true, yol(eskiM4b), yol(eskiMp3),
                        "Kayitli nihai paket ve kaynak ozeti dogrulandi");
            }
            if (!kaynakAyni) {
                return new Sonuc(false, false, null, null,
                        "Nihai paket var ancak kaynak ozeti degisti; otomatik tamamlanmis sayilmadi");
            }
        }

        Path m4b = m4bler.isEmpty() ? null : m4bler.get(0);
        Path mp3 = tamMp3ler.isEmpty() ? null : tamMp3ler.get(0);
        Properties p = new Properties();
        p.setProperty("surum", "1");
        p.setProperty("kayitTuru", "legacy-final-package");
        p.setProperty("kaynakParcaSayisi", String.valueOf(kaynak.parcaSayisi()));
        p.setProperty("kaynakToplamKarakter", String.valueOf(kaynak.toplamKarakter()));
        p.setProperty("kaynakBilesikSha256", kaynak.bilesikSha256());
        p.setProperty("m4b", m4b == null ? "" : m4b.toAbsolutePath().toString());
        p.setProperty("m4bSha256", m4b == null ? "" : UretimDosyaYardimci.sha256(m4b));
        p.setProperty("tamMp3", mp3 == null ? "" : mp3.toAbsolutePath().toString());
        p.setProperty("tamMp3Sha256", mp3 == null ? "" : UretimDosyaYardimci.sha256(mp3));
        p.setProperty("kayitZamani", OffsetDateTime.now().toString());
        p.setProperty("not", "Eski nihai paket mevcut kaynak anlik goruntusune baglandi; TTS yeniden calistirilmayacak.");
        UretimDosyaYardimci.propertiesAtomikYaz(kayit, p, "Mevcut Nihai Paket Kaydi");
        return new Sonuc(true, false, m4b, mp3,
                "Eski nihai paket algilandi ve mevcut kaynak ozetiyle kaydedildi");
    }

    private static boolean geciciVeyaParca(Path p) {
        String yol = p.toString().toLowerCase(Locale.ROOT).replace('\\', '/');
        return yol.contains("/uretim-parcalari/") || yol.contains("/kor-dinleme/")
                || yol.contains("/ham-ciktilar/") || yol.endsWith(".tmp.mp3") || yol.endsWith(".tmp.m4b");
    }

    private static boolean tamMp3Adayi(Path p) {
        String ad = p.getFileName().toString().toLowerCase(Locale.ROOT);
        String ust = p.getParent() == null ? "" : p.getParent().getFileName().toString().toLowerCase(Locale.ROOT);
        if (ust.equals("bolumler") || ust.equals("chapters")) return false;
        return !ad.matches("^\\d{3,5}[-_].*\\.mp3$") && !ad.contains("ornek") && !ad.contains("test");
    }

    private static long boyut(Path p) {
        try { return Files.size(p); } catch (Exception e) { return 0L; }
    }

    private static Path yol(String s) { return s == null || s.isBlank() ? null : Path.of(s); }

    public record KaynakOzeti(int parcaSayisi, long toplamKarakter, String bilesikSha256) {}

    public record Sonuc(boolean tamamlanmis, boolean dahaOnceKayitli, Path m4b, Path tamMp3, String mesaj) {
        static Sonuc yok() { return new Sonuc(false, false, null, null, "Nihai paket bulunamadi"); }
    }
}
