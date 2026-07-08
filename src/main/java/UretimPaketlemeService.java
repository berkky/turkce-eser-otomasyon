import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class UretimPaketlemeService {
    private static final Pattern PARCA = Pattern.compile("^(\\d{3,5})-(\\d{3,5})\\.mp3$", Pattern.CASE_INSENSITIVE);
    private final Path projeKlasoru;
    private final UretimKuyruguService kuyruk;

    public UretimPaketlemeService(Path projeKlasoru, UretimKuyruguService kuyruk) {
        this.projeKlasoru = projeKlasoru;
        this.kuyruk = kuyruk;
    }

    public Sonuc paketle(UretimIsi is) throws Exception {
        if (is.aktifSaglayici() == null || is.aktifSaglayici().isBlank()) {
            throw new IllegalStateException("İşin aktif sağlayıcısı bilinmiyor.");
        }
        Path parcaKlasoru = is.sesEserKlasoru().resolve("uretim-parcalari").resolve(is.aktifSaglayici());
        List<Path> parcalar = mp3Listele(parcaKlasoru);
        if (parcalar.size() != is.toplamParca()) {
            throw new IllegalStateException("Paketleme için tüm parçalar hazır olmalı. Hazır=" + parcalar.size() + "/" + is.toplamParca());
        }

        FfmpegClient ffmpeg = new FfmpegClient(projeKlasoru);
        FfmpegClient.KontrolSonucu kontrol = ffmpeg.kontrolEt();
        if (!kontrol.hazir()) throw new IllegalStateException("FFmpeg hazır değil: " + kontrol.mesaj());

        Path paket = is.sesEserKlasoru().resolve("paket-" + is.aktifSaglayici());
        Path bolumKlasoru = paket.resolve("bolumler");
        Files.createDirectories(bolumKlasoru);

        Map<Integer, List<Path>> gruplar = new LinkedHashMap<>();
        for (Path p : parcalar) {
            Matcher m = PARCA.matcher(p.getFileName().toString());
            if (!m.matches()) continue;
            int bolum = Integer.parseInt(m.group(1));
            gruplar.computeIfAbsent(bolum, k -> new ArrayList<>()).add(p);
        }
        if (gruplar.isEmpty()) throw new IllegalStateException("Parça adlarından bölüm bilgisi çıkarılamadı.");

        List<Path> bolumMp3leri = new ArrayList<>();
        List<String> bolumBasliklari = new ArrayList<>();
        FfmpegClient.MedyaEtiketleri etiket = new FfmpegClient.MedyaEtiketleri(
                is.eserAdi(), "Bilinmiyor", is.eserAdi(), "Sesli Eser", "tr", "Eser Otomasyon üretimi"
        );
        int yeniBolum = 0;
        for (Map.Entry<Integer, List<Path>> e : gruplar.entrySet()) {
            e.getValue().sort(Comparator.comparing(p -> p.getFileName().toString()));
            Path hedef = bolumKlasoru.resolve(String.format(Locale.ROOT, "%03d-bolum.mp3", e.getKey()));
            if (!Files.isRegularFile(hedef) || Files.size(hedef) < 1_000L) {
                Path gecici = hedef.resolveSibling(hedef.getFileName() + ".tmp.mp3");
                ffmpeg.mp3leriBirlestir(e.getValue(), gecici, etiket);
                UretimDosyaYardimci.atomikTasi(gecici, hedef);
                yeniBolum++;
            }
            bolumMp3leri.add(hedef);
            bolumBasliklari.add("Bölüm " + e.getKey());
        }

        Path tamMp3 = paket.resolve(guvenliAd(is.eserAdi()) + ".mp3");
        Path geciciTam = tamMp3.resolveSibling(tamMp3.getFileName() + ".tmp.mp3");
        ffmpeg.mp3leriBirlestir(bolumMp3leri, geciciTam, etiket);
        UretimDosyaYardimci.atomikTasi(geciciTam, tamMp3);

        List<FfmpegClient.BolumIsareti> isaretler = new ArrayList<>();
        long baslangic = 0L;
        for (int i = 0; i < bolumMp3leri.size(); i++) {
            long sure = Math.max(1L, Math.round(ffmpeg.sureSaniye(bolumMp3leri.get(i)) * 1_000d));
            isaretler.add(new FfmpegClient.BolumIsareti(bolumBasliklari.get(i), baslangic, baslangic + sure));
            baslangic += sure;
        }
        Path m4b = paket.resolve(guvenliAd(is.eserAdi()) + ".m4b");
        Path geciciM4b = m4b.resolveSibling(m4b.getFileName() + ".tmp.m4b");
        ffmpeg.m4bOlustur(bolumMp3leri, isaretler, geciciM4b, etiket, kapakBul(is));
        UretimDosyaYardimci.atomikTasi(geciciM4b, m4b);

        UretimIsi tamam = is.guncelle(UretimDurumu.TAMAMLANDI, is.toplamParca(), is.aktifSaglayici(), "");
        kuyruk.kaydet(tamam);
        kuyruk.olayYaz(tamam, "Paketleme tamamlandı. Bölüm=" + bolumMp3leri.size() + ", MP3=" + tamMp3 + ", M4B=" + m4b);
        manifestYaz(tamam, paket, bolumMp3leri, tamMp3, m4b);
        return new Sonuc(true, yeniBolum, bolumMp3leri.size(), tamMp3, m4b, paket);
    }

    private static List<Path> mp3Listele(Path klasor) throws Exception {
        if (!Files.isDirectory(klasor)) return List.of();
        try (Stream<Path> s = Files.list(klasor)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> PARCA.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
    }

    private static Path kapakBul(UretimIsi is) {
        for (String ad : List.of("kapak.jpg", "kapak.jpeg", "kapak.png", "cover.jpg", "cover.png")) {
            Path p = is.metinEserKlasoru().resolve(ad);
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private static String guvenliAd(String ad) {
        String s = ad.replaceAll("(?i)^ESER-\\d+\\s*-\\s*", "").trim()
                .replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", " ");
        return s.isBlank() ? "sesli-eser" : s;
    }

    private static void manifestYaz(UretimIsi is, Path paket, List<Path> bolumler, Path mp3, Path m4b) throws Exception {
        String j = "{\n" +
                "  \"surum\": 1,\n" +
                "  \"isId\": \"" + UretimDosyaYardimci.jsonKacir(is.id()) + "\",\n" +
                "  \"eserAdi\": \"" + UretimDosyaYardimci.jsonKacir(is.eserAdi()) + "\",\n" +
                "  \"saglayiciKimligi\": \"" + UretimDosyaYardimci.jsonKacir(is.aktifSaglayici()) + "\",\n" +
                "  \"bolumSayisi\": " + bolumler.size() + ",\n" +
                "  \"tamMp3\": \"" + UretimDosyaYardimci.jsonKacir(mp3.toString()) + "\",\n" +
                "  \"m4b\": \"" + UretimDosyaYardimci.jsonKacir(m4b.toString()) + "\",\n" +
                "  \"tamamlanmaZamani\": \"" + OffsetDateTime.now() + "\"\n" +
                "}\n";
        UretimDosyaYardimci.metinAtomikYaz(paket.resolve("paket-manifest.json"), j);
    }

    public record Sonuc(boolean basarili, int yeniBolum, int toplamBolum,
                        Path tamMp3, Path m4b, Path paketKlasoru) {}
}
