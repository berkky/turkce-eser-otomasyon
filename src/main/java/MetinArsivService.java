import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class MetinArsivService {
    private static final Pattern ESER_ID_DESENI = Pattern.compile("(?i)^ESER-(\\d{1,8})\\s*-.*\\.epub$");

    private MetinArsivService() {
    }

    /**
     * Arşiv klasöründe önceden bulunan EPUB'ların eksik metin arşivlerini tamamlar.
     * Böylece Adım 3'te arşivlenen EPUB'lar yeniden indirilmeye gerek kalmadan işlenir.
     */
    public static TopluSonuc eksikEpubMetinleriniTamamla(Path arsivKlasoru, Path metinArsivKlasoru) {
        int hazirlanan = 0;
        int zatenHazir = 0;
        int hatali = 0;

        try {
            Files.createDirectories(arsivKlasoru);
            Files.createDirectories(metinArsivKlasoru);

            List<Path> epubDosyalari;
            try (Stream<Path> stream = Files.list(arsivKlasoru)) {
                epubDosyalari = stream
                        .filter(Files::isRegularFile)
                        .filter(MetinArsivService::epubMi)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .toList();
            }

            for (Path epub : epubDosyalari) {
                int eserId = eserIdBul(epub.getFileName().toString());
                if (eserId <= 0) {
                    continue;
                }

                if (hazirMi(metinArsivKlasoru, eserId)) {
                    zatenHazir++;
                    continue;
                }

                try {
                    EpubHazirlayici.EpubVerisi veri = EpubHazirlayici.tamMetniHazirla(epub.toFile());
                    EserBilgisi bilgi = veri.getMetadata();
                    if (bilgi == null) {
                        bilgi = new EserBilgisi();
                    }
                    bilgi.bilinmeyenleriDuzelt();

                    Sonuc sonuc = olustur(epub, metinArsivKlasoru, eserId, bilgi, veri);
                    hazirlanan++;
                    System.out.println("EPUB tam metni hazırlandı: " + epub.getFileName());
                    System.out.println("Metin arşivi: " + sonuc.metinKlasoru());
                } catch (Exception e) {
                    hatali++;
                    System.err.println("EPUB metin arşivi hazırlanamadı: " + epub.getFileName());
                    System.err.println("Neden: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            hatali++;
            System.err.println("Metin arşivi taraması tamamlanamadı: " + e.getMessage());
        }

        return new TopluSonuc(hazirlanan, zatenHazir, hatali);
    }

    public static Sonuc olustur(Path epubDosyasi,
                                Path metinArsivKlasoru,
                                int eserId,
                                EserBilgisi bilgi,
                                EpubHazirlayici.EpubVerisi veri) throws Exception {
        if (epubDosyasi == null || !Files.isRegularFile(epubDosyasi)) {
            throw new IllegalArgumentException("Metin arşivi için EPUB dosyası bulunamadı.");
        }
        if (eserId <= 0) {
            throw new IllegalArgumentException("Geçerli eser ID değeri bulunamadı.");
        }
        if (veri == null || veri.getBolumler().isEmpty()) {
            throw new IllegalArgumentException("EPUB içinde arşivlenecek metin bölümü bulunamadı.");
        }

        Files.createDirectories(metinArsivKlasoru);
        bilgi = bilgi == null ? veri.getMetadata() : bilgi;
        if (bilgi == null) {
            bilgi = new EserBilgisi();
        }
        bilgi.bilinmeyenleriDuzelt();

        String klasorAdi = String.format(
                Locale.ROOT,
                "ESER-%05d - %s",
                eserId,
                guvenliDosyaAdi(bilgi.eser_adi, 90)
        );
        Path hedef = metinArsivKlasoru.resolve(klasorAdi);
        Path gecici = metinArsivKlasoru.resolve(".hazirlaniyor-" + eserId + "-" + UUID.randomUUID());
        Path bolumKlasoru = gecici.resolve("bolumler");

        try {
            Files.createDirectories(bolumKlasoru);

            List<BolumDosyasi> bolumDosyalari = new ArrayList<>();
            int yazilan = 0;
            for (EpubHazirlayici.Bolum bolum : veri.getBolumler()) {
                String dosyaAdi = String.format(
                        Locale.ROOT,
                        "%03d - %s.txt",
                        bolum.sira(),
                        guvenliDosyaAdi(bolum.baslik(), 80)
                );
                Path bolumDosyasi = bolumKlasoru.resolve(dosyaAdi);
                String icerik = bolum.baslik() + "\n\n" + bolum.metin() + "\n";
                Files.writeString(bolumDosyasi, icerik, StandardCharsets.UTF_8);
                bolumDosyalari.add(new BolumDosyasi(
                        bolum.sira(),
                        bolum.baslik(),
                        dosyaAdi,
                        bolum.kaynakYolu(),
                        bolum.metin().length(),
                        bolum.tamMetneDahil()
                ));
                yazilan++;
            }

            Files.writeString(
                    gecici.resolve("tam-metin.txt"),
                    veri.getTamMetin() + System.lineSeparator(),
                    StandardCharsets.UTF_8
            );

            Files.writeString(
                    gecici.resolve("000-eser-bilgisi.txt"),
                    eserBilgisiMetni(eserId, epubDosyasi, bilgi, veri),
                    StandardCharsets.UTF_8
            );

            Files.writeString(
                    gecici.resolve("manifest.json"),
                    manifestJson(eserId, epubDosyasi, bilgi, veri, bolumDosyalari),
                    StandardCharsets.UTF_8
            );

            Files.writeString(
                    gecici.resolve("_hazir.flag"),
                    "Hazırlanma zamanı: " + OffsetDateTime.now() + System.lineSeparator(),
                    StandardCharsets.UTF_8
            );

            if (Files.exists(hedef)) {
                diziniSil(hedef);
            }
            klasoruTasi(gecici, hedef);

            return new Sonuc(hedef, hedef.resolve("tam-metin.txt"), yazilan, veri.getToplamKarakterSayisi());
        } catch (Exception e) {
            diziniSilSessiz(gecici);
            throw e;
        }
    }

    public static Sonuc olustur(Path epubDosyasi,
                                Path metinArsivKlasoru,
                                int eserId,
                                EserBilgisi bilgi) throws Exception {
        EpubHazirlayici.EpubVerisi veri = EpubHazirlayici.tamMetniHazirla(epubDosyasi.toFile());
        return olustur(epubDosyasi, metinArsivKlasoru, eserId, bilgi, veri);
    }

    private static boolean hazirMi(Path metinArsivKlasoru, int eserId) throws IOException {
        if (!Files.isDirectory(metinArsivKlasoru)) {
            return false;
        }
        String baslangic = String.format(Locale.ROOT, "ESER-%05d - ", eserId);
        try (Stream<Path> stream = Files.list(metinArsivKlasoru)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(baslangic))
                    .anyMatch(path -> Files.isRegularFile(path.resolve("_hazir.flag"))
                            && Files.isRegularFile(path.resolve("tam-metin.txt")));
        }
    }

    private static String eserBilgisiMetni(int eserId,
                                           Path epubDosyasi,
                                           EserBilgisi bilgi,
                                           EpubHazirlayici.EpubVerisi veri) {
        return """
                ESER ID: %05d
                Eser adı: %s
                Eser türü: %s
                Yazar: %s
                Yayınevi: %s
                Basım yılı: %s
                Dil: %s
                Kaynak EPUB: %s
                Bölüm sayısı: %d
                Temiz tam metin karakteri: %d
                Seslendirme durumu: %s
                """.formatted(
                eserId,
                bilgi.eser_adi,
                bilgi.eser_turu,
                bilgi.yazar,
                bilgi.yayinevi,
                bilgi.basim_yili,
                bilgi.dil,
                epubDosyasi.toAbsolutePath(),
                veri.getToplamBolumSayisi(),
                veri.getToplamKarakterSayisi(),
                bilgi.seslendirme_durumu
        );
    }

    private static String manifestJson(int eserId,
                                       Path epubDosyasi,
                                       EserBilgisi bilgi,
                                       EpubHazirlayici.EpubVerisi veri,
                                       List<BolumDosyasi> bolumler) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"eserId\": ").append(eserId).append(",\n");
        json.append("  \"eserAdi\": \"").append(jsonKacir(bilgi.eser_adi)).append("\",\n");
        json.append("  \"yazar\": \"").append(jsonKacir(bilgi.yazar)).append("\",\n");
        json.append("  \"dil\": \"").append(jsonKacir(bilgi.dil)).append("\",\n");
        json.append("  \"kaynakEpub\": \"").append(jsonKacir(epubDosyasi.toAbsolutePath().toString())).append("\",\n");
        json.append("  \"toplamBolum\": ").append(veri.getToplamBolumSayisi()).append(",\n");
        json.append("  \"tamMetinKarakterSayisi\": ").append(veri.getToplamKarakterSayisi()).append(",\n");
        json.append("  \"hazirlanmaZamani\": \"").append(jsonKacir(OffsetDateTime.now().toString())).append("\",\n");
        json.append("  \"bolumler\": [\n");

        for (int i = 0; i < bolumler.size(); i++) {
            BolumDosyasi bolum = bolumler.get(i);
            json.append("    {\n");
            json.append("      \"sira\": ").append(bolum.sira()).append(",\n");
            json.append("      \"baslik\": \"").append(jsonKacir(bolum.baslik())).append("\",\n");
            json.append("      \"dosya\": \"bolumler/").append(jsonKacir(bolum.dosyaAdi())).append("\",\n");
            json.append("      \"kaynakYolu\": \"").append(jsonKacir(bolum.kaynakYolu())).append("\",\n");
            json.append("      \"karakterSayisi\": ").append(bolum.karakterSayisi()).append(",\n");
            json.append("      \"tamMetneDahil\": ").append(bolum.tamMetneDahil()).append("\n");
            json.append("    }");
            if (i < bolumler.size() - 1) {
                json.append(',');
            }
            json.append('\n');
        }

        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static int eserIdBul(String dosyaAdi) {
        Matcher matcher = ESER_ID_DESENI.matcher(dosyaAdi);
        if (!matcher.matches()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean epubMi(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".epub");
    }

    private static String guvenliDosyaAdi(String deger, int maksimumUzunluk) {
        String temiz = deger == null ? "Bilinmiyor" : deger.trim();
        temiz = temiz
                .replace('ı', 'i')
                .replace('İ', 'I')
                .replace('ş', 's')
                .replace('Ş', 'S')
                .replace('ğ', 'g')
                .replace('Ğ', 'G')
                .replace('ç', 'c')
                .replace('Ç', 'C')
                .replace('ö', 'o')
                .replace('Ö', 'O')
                .replace('ü', 'u')
                .replace('Ü', 'U');
        temiz = Normalizer.normalize(temiz, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\x20-\\x7E]", " ")
                .replaceAll("[<>:\"/\\\\|?*]", " ")
                .replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("[. ]+$", "")
                .trim();
        if (temiz.isBlank()) {
            temiz = "Bilinmiyor";
        }
        if (temiz.length() > maksimumUzunluk) {
            temiz = temiz.substring(0, maksimumUzunluk).trim();
        }
        return temiz;
    }

    private static String jsonKacir(String deger) {
        if (deger == null) {
            return "";
        }
        StringBuilder sonuc = new StringBuilder();
        for (int i = 0; i < deger.length(); i++) {
            char c = deger.charAt(i);
            switch (c) {
                case '\\' -> sonuc.append("\\\\");
                case '"' -> sonuc.append("\\\"");
                case '\n' -> sonuc.append("\\n");
                case '\r' -> sonuc.append("\\r");
                case '\t' -> sonuc.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sonuc.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sonuc.append(c);
                    }
                }
            }
        }
        return sonuc.toString();
    }

    private static void klasoruTasi(Path kaynak, Path hedef) throws IOException {
        try {
            Files.move(kaynak, hedef, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(kaynak, hedef);
        }
    }

    private static void diziniSil(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            List<Path> yollar = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path yol : yollar) {
                Files.deleteIfExists(yol);
            }
        }
    }

    private static void diziniSilSessiz(Path path) {
        try {
            diziniSil(path);
        } catch (Exception ignored) {
        }
    }

    private record BolumDosyasi(int sira,
                                String baslik,
                                String dosyaAdi,
                                String kaynakYolu,
                                int karakterSayisi,
                                boolean tamMetneDahil) {
    }

    public record Sonuc(Path metinKlasoru,
                        Path tamMetinDosyasi,
                        int yazilanBolumSayisi,
                        int toplamKarakterSayisi) {
    }

    public record TopluSonuc(int hazirlanan,
                             int zatenHazir,
                             int hatali) {
    }
}
