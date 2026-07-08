import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.BreakIterator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Metin arşivindeki EPUB bölümlerini TTS servislerine uygun büyüklükte parçalara ayırır.
 * Parçalama cümle ve paragraf sınırlarını mümkün olduğunca korur.
 */
public final class TtsParcalamaService {
    private static final int HEDEF_KARAKTER = 3_200;
    private static final int MAKSIMUM_KARAKTER = 4_000;
    private static final int MINIMUM_KARAKTER = 1_200;
    private static final Pattern BOLUM_SIRA_DESENI = Pattern.compile("^(\\d{1,5})\\s*[-_. ]");
    private static final Pattern ESER_ID_DESENI = Pattern.compile("(?i)^ESER-(\\d{1,8})\\s*-.*$");
    private static final Pattern URL_DESENI = Pattern.compile("(?i)https?://\\S+|www\\.\\S+");

    private TtsParcalamaService() {
    }

    public static TopluSonuc eksikTtsParcalariniHazirla(Path metinArsivKlasoru) {
        int hazirlanan = 0;
        int zatenHazir = 0;
        int hatali = 0;
        int toplamParca = 0;

        try {
            Files.createDirectories(metinArsivKlasoru);
            List<Path> eserKlasorleri;
            try (Stream<Path> stream = Files.list(metinArsivKlasoru)) {
                eserKlasorleri = stream
                        .filter(Files::isDirectory)
                        .filter(path -> Files.isDirectory(path.resolve("bolumler")))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .toList();
            }

            for (Path eserKlasoru : eserKlasorleri) {
                try {
                    String kaynakHash = kaynakHashHesapla(eserKlasoru.resolve("bolumler"));
                    if (hazirMi(eserKlasoru, kaynakHash)) {
                        zatenHazir++;
                        continue;
                    }

                    Sonuc sonuc = olustur(eserKlasoru, kaynakHash);
                    hazirlanan++;
                    toplamParca += sonuc.parcaSayisi();
                    System.out.println("TTS metin parçaları hazırlandı: " + eserKlasoru.getFileName());
                    System.out.println("TTS parça klasörü: " + sonuc.ttsKlasoru());
                    System.out.println("Oluşturulan parça sayısı: " + sonuc.parcaSayisi());
                } catch (Exception e) {
                    hatali++;
                    System.err.println("TTS parçaları hazırlanamadı: " + eserKlasoru.getFileName());
                    System.err.println("Neden: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            hatali++;
            System.err.println("TTS metin arşivi taraması tamamlanamadı: " + e.getMessage());
        }

        return new TopluSonuc(hazirlanan, zatenHazir, hatali, toplamParca);
    }

    public static Sonuc olustur(Path metinEserKlasoru) throws Exception {
        return olustur(metinEserKlasoru, kaynakHashHesapla(metinEserKlasoru.resolve("bolumler")));
    }

    private static Sonuc olustur(Path metinEserKlasoru, String kaynakHash) throws Exception {
        if (metinEserKlasoru == null || !Files.isDirectory(metinEserKlasoru)) {
            throw new IllegalArgumentException("Geçerli metin eser klasörü bulunamadı.");
        }

        Path bolumKlasoru = metinEserKlasoru.resolve("bolumler");
        if (!Files.isDirectory(bolumKlasoru)) {
            throw new IllegalArgumentException("Bölümler klasörü bulunamadı: " + bolumKlasoru);
        }

        List<Path> bolumDosyalari = bolumDosyalariniListele(bolumKlasoru);
        if (bolumDosyalari.isEmpty()) {
            throw new IllegalArgumentException("TTS için kullanılabilecek bölüm metni bulunamadı.");
        }

        Path hedef = metinEserKlasoru.resolve("tts-parcalari");
        Path gecici = metinEserKlasoru.resolve(".tts-hazirlaniyor-" + UUID.randomUUID());
        Files.createDirectories(gecici);

        try {
            List<ParcaKaydi> kayitlar = new ArrayList<>();
            int genelSira = 1;
            int toplamKarakter = 0;
            int toplamKelime = 0;

            for (int dosyaIndeksi = 0; dosyaIndeksi < bolumDosyalari.size(); dosyaIndeksi++) {
                Path bolumDosyasi = bolumDosyalari.get(dosyaIndeksi);
                String hamMetin = Files.readString(bolumDosyasi, StandardCharsets.UTF_8);
                String temizMetin = ttsIcinNormalizeEt(hamMetin);
                if (temizMetin.isBlank()) {
                    continue;
                }

                int bolumSira = bolumSiraBul(bolumDosyasi, dosyaIndeksi + 1);
                String bolumBasligi = baslikBul(temizMetin, bolumDosyasi);
                List<String> parcalar = metniParcala(temizMetin);

                for (int parcaIndeksi = 0; parcaIndeksi < parcalar.size(); parcaIndeksi++) {
                    String parca = parcalar.get(parcaIndeksi).trim();
                    if (parca.isBlank()) {
                        continue;
                    }

                    int bolumParcaSira = parcaIndeksi + 1;
                    String dosyaAdi = String.format(
                            Locale.ROOT,
                            "%03d-%03d.txt",
                            bolumSira,
                            bolumParcaSira
                    );
                    Path parcaDosyasi = gecici.resolve(dosyaAdi);
                    Files.writeString(parcaDosyasi, parca + System.lineSeparator(), StandardCharsets.UTF_8);

                    int karakter = parca.length();
                    int kelime = kelimeSayisi(parca);
                    int tahminiSaniye = (int) Math.max(1, Math.round(kelime / 2.5d));
                    toplamKarakter += karakter;
                    toplamKelime += kelime;

                    kayitlar.add(new ParcaKaydi(
                            genelSira++,
                            bolumSira,
                            bolumBasligi,
                            bolumParcaSira,
                            dosyaAdi,
                            bolumDosyasi.getFileName().toString(),
                            karakter,
                            kelime,
                            tahminiSaniye,
                            dosyaAdi.replace(".txt", ".mp3")
                    ));
                }
            }

            if (kayitlar.isEmpty()) {
                throw new IllegalArgumentException("TTS için üretilebilir metin parçası bulunamadı.");
            }

            int eserId = eserIdBul(metinEserKlasoru.getFileName().toString());
            Files.writeString(
                    gecici.resolve("tts-manifest.json"),
                    manifestJson(eserId, metinEserKlasoru, kaynakHash, kayitlar, toplamKarakter, toplamKelime),
                    StandardCharsets.UTF_8
            );
            Files.writeString(
                    gecici.resolve("tts-okuma-listesi.txt"),
                    okumaListesi(kayitlar),
                    StandardCharsets.UTF_8
            );
            Files.writeString(
                    gecici.resolve("_tts_hazir.flag"),
                    "kaynakHash=" + kaynakHash + System.lineSeparator()
                            + "hazirlanmaZamani=" + OffsetDateTime.now() + System.lineSeparator()
                            + "parcaSayisi=" + kayitlar.size() + System.lineSeparator(),
                    StandardCharsets.UTF_8
            );

            if (Files.exists(hedef)) {
                diziniSil(hedef);
            }
            klasoruTasi(gecici, hedef);

            return new Sonuc(
                    hedef,
                    hedef.resolve("tts-manifest.json"),
                    hedef.resolve("tts-okuma-listesi.txt"),
                    kayitlar.size(),
                    toplamKarakter,
                    toplamKelime
            );
        } catch (Exception e) {
            diziniSilSessiz(gecici);
            throw e;
        }
    }

    private static List<String> metniParcala(String metin) {
        List<MetinBirimi> birimler = new ArrayList<>();
        String[] paragraflar = metin.split("\n\s*\n+");

        for (String paragraf : paragraflar) {
            String temiz = paragraf.replaceAll("[ \t]+", " ").trim();
            if (temiz.isBlank()) {
                continue;
            }

            if (temiz.length() <= MAKSIMUM_KARAKTER) {
                birimler.add(new MetinBirimi(temiz, true));
            } else {
                List<String> cumleler = cumlelereAyir(temiz);
                for (int i = 0; i < cumleler.size(); i++) {
                    String cumle = cumleler.get(i);
                    List<String> altParcalar = cumle.length() > MAKSIMUM_KARAKTER
                            ? uzunBirimiBol(cumle)
                            : List.of(cumle);
                    for (int j = 0; j < altParcalar.size(); j++) {
                        birimler.add(new MetinBirimi(altParcalar.get(j), i == 0 && j == 0));
                    }
                }
            }
        }

        List<String> sonuc = new ArrayList<>();
        StringBuilder mevcut = new StringBuilder();

        for (MetinBirimi birim : birimler) {
            String ayirici = mevcut.isEmpty() ? "" : (birim.yeniParagraf() ? "\n\n" : " ");
            int yeniUzunluk = mevcut.length() + ayirici.length() + birim.metin().length();

            if (!mevcut.isEmpty()
                    && (yeniUzunluk > MAKSIMUM_KARAKTER
                    || (mevcut.length() >= HEDEF_KARAKTER && mevcut.length() >= MINIMUM_KARAKTER))) {
                sonuc.add(mevcut.toString().trim());
                mevcut.setLength(0);
                ayirici = "";
            }

            mevcut.append(ayirici).append(birim.metin());
        }

        if (!mevcut.isEmpty()) {
            sonuc.add(mevcut.toString().trim());
        }

        sonKucukParcayiBirlestir(sonuc);
        return sonuc;
    }

    private static List<String> cumlelereAyir(String paragraf) {
        List<String> cumleler = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.forLanguageTag("tr-TR"));
        iterator.setText(paragraf);
        int baslangic = iterator.first();
        for (int bitis = iterator.next(); bitis != BreakIterator.DONE; baslangic = bitis, bitis = iterator.next()) {
            String cumle = paragraf.substring(baslangic, bitis).trim();
            if (!cumle.isBlank()) {
                cumleler.add(cumle);
            }
        }
        if (cumleler.isEmpty()) {
            cumleler.add(paragraf);
        }
        return cumleler;
    }

    private static List<String> uzunBirimiBol(String metin) {
        List<String> sonuc = new ArrayList<>();
        String kalan = metin.trim();

        while (kalan.length() > MAKSIMUM_KARAKTER) {
            int kesim = kelimeSiniriBul(kalan, MAKSIMUM_KARAKTER);
            String parca = kalan.substring(0, kesim).trim();
            if (!parca.isBlank()) {
                sonuc.add(parca);
            }
            kalan = kalan.substring(kesim).trim();
        }

        if (!kalan.isBlank()) {
            sonuc.add(kalan);
        }
        return sonuc;
    }

    private static int kelimeSiniriBul(String metin, int ustSinir) {
        int baslangic = Math.min(ustSinir, metin.length() - 1);
        for (int i = baslangic; i >= Math.max(1, ustSinir - 500); i--) {
            if (Character.isWhitespace(metin.charAt(i))) {
                return i;
            }
        }
        return Math.min(ustSinir, metin.length());
    }

    private static void sonKucukParcayiBirlestir(List<String> parcalar) {
        if (parcalar.size() < 2) {
            return;
        }
        int sonIndex = parcalar.size() - 1;
        String son = parcalar.get(sonIndex);
        String onceki = parcalar.get(sonIndex - 1);
        if (son.length() < MINIMUM_KARAKTER
                && onceki.length() + 2 + son.length() <= MAKSIMUM_KARAKTER) {
            parcalar.set(sonIndex - 1, onceki + "\n\n" + son);
            parcalar.remove(sonIndex);
        }
    }

    private static String ttsIcinNormalizeEt(String metin) {
        if (metin == null || metin.isBlank()) {
            return "";
        }

        String sonuc = metin
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('“', '"')
                .replace('”', '"')
                .replace('‘', '\'')
                .replace('’', '\'')
                .replace("…", "...");

        sonuc = URL_DESENI.matcher(sonuc).replaceAll(" internet bağlantısı ");
        sonuc = sonuc.replaceAll("%\\s*(\\d+(?:[.,]\\d+)?)", "yüzde $1");
        sonuc = sonuc.replaceAll("(\\d+(?:[.,]\\d+)?)\\s*%", "yüzde $1");
        sonuc = sonuc.replace("&", " ve ");

        String[][] kisaltmalar = {
                {"(?iu)\\bProf\\.", "Profesör"},
                {"(?iu)\\bDoç\\.", "Doçent"},
                {"(?iu)\\bDr\\.", "Doktor"},
                {"(?iu)\\bSn\\.", "Sayın"},
                {"(?iu)\\bHz\\.", "Hazreti"},
                {"(?iu)\\bT\\.\\s*C\\.", "Türkiye Cumhuriyeti"},
                {"(?iu)\\bM\\.\\s*Ö\\.", "Milattan önce"},
                {"(?iu)\\bM\\.\\s*S\\.", "Milattan sonra"},
                {"(?iu)\\bvb\\.", "ve benzeri"},
                {"(?iu)\\bvs\\.", "vesaire"},
                {"(?iu)\\börn\\.", "örneğin"},
                {"(?iu)\\bbkz\\.", "bakınız"}
        };
        for (String[] degisim : kisaltmalar) {
            sonuc = sonuc.replaceAll(degisim[0], degisim[1]);
        }

        sonuc = sonuc
                .replaceAll("(?m)^\\s*[=_*~-]{3,}\\s*$", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return sonuc;
    }

    private static List<Path> bolumDosyalariniListele(Path bolumKlasoru) throws IOException {
        try (Stream<Path> stream = Files.list(bolumKlasoru)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    private static int bolumSiraBul(Path dosya, int varsayilan) {
        Matcher matcher = BOLUM_SIRA_DESENI.matcher(dosya.getFileName().toString());
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return varsayilan;
    }

    private static String baslikBul(String metin, Path bolumDosyasi) {
        for (String satir : metin.split("\\n")) {
            String temiz = satir.trim();
            if (!temiz.isBlank()) {
                return temiz.length() <= 160 ? temiz : temiz.substring(0, 160).trim();
            }
        }
        String ad = bolumDosyasi.getFileName().toString().replaceFirst("(?i)\\.txt$", "");
        return ad.replaceFirst("^\\d+\\s*[-_. ]\\s*", "").trim();
    }

    private static int kelimeSayisi(String metin) {
        String temiz = metin == null ? "" : metin.trim();
        return temiz.isBlank() ? 0 : temiz.split("\\s+").length;
    }

    private static int eserIdBul(String klasorAdi) {
        Matcher matcher = ESER_ID_DESENI.matcher(klasorAdi);
        if (!matcher.matches()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String kaynakHashHesapla(Path bolumKlasoru) throws Exception {
        if (!Files.isDirectory(bolumKlasoru)) {
            throw new IllegalArgumentException("Bölüm klasörü bulunamadı: " + bolumKlasoru);
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        List<Path> dosyalar = bolumDosyalariniListele(bolumKlasoru);
        for (Path dosya : dosyalar) {
            digest.update(dosya.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(dosya));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static boolean hazirMi(Path eserKlasoru, String kaynakHash) {
        Path ttsKlasoru = eserKlasoru.resolve("tts-parcalari");
        Path flag = ttsKlasoru.resolve("_tts_hazir.flag");
        if (!Files.isRegularFile(flag) || !Files.isRegularFile(ttsKlasoru.resolve("tts-manifest.json"))) {
            return false;
        }
        try {
            String icerik = Files.readString(flag, StandardCharsets.UTF_8);
            return icerik.contains("kaynakHash=" + kaynakHash);
        } catch (IOException e) {
            return false;
        }
    }

    private static String manifestJson(int eserId,
                                       Path eserKlasoru,
                                       String kaynakHash,
                                       List<ParcaKaydi> kayitlar,
                                       int toplamKarakter,
                                       int toplamKelime) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"surum\": 1,\n");
        json.append("  \"eserId\": ").append(eserId).append(",\n");
        json.append("  \"eserKlasoru\": \"").append(jsonKacir(eserKlasoru.toAbsolutePath().toString())).append("\",\n");
        json.append("  \"kaynakHash\": \"").append(kaynakHash).append("\",\n");
        json.append("  \"hedefKarakter\": ").append(HEDEF_KARAKTER).append(",\n");
        json.append("  \"maksimumKarakter\": ").append(MAKSIMUM_KARAKTER).append(",\n");
        json.append("  \"minimumKarakter\": ").append(MINIMUM_KARAKTER).append(",\n");
        json.append("  \"toplamParca\": ").append(kayitlar.size()).append(",\n");
        json.append("  \"toplamKarakter\": ").append(toplamKarakter).append(",\n");
        json.append("  \"toplamKelime\": ").append(toplamKelime).append(",\n");
        json.append("  \"tahminiToplamSureSaniye\": ")
                .append(kayitlar.stream().mapToInt(ParcaKaydi::tahminiSureSaniye).sum())
                .append(",\n");
        json.append("  \"hazirlanmaZamani\": \"").append(jsonKacir(OffsetDateTime.now().toString())).append("\",\n");
        json.append("  \"parcalar\": [\n");

        for (int i = 0; i < kayitlar.size(); i++) {
            ParcaKaydi kayit = kayitlar.get(i);
            json.append("    {\n");
            json.append("      \"sira\": ").append(kayit.sira()).append(",\n");
            json.append("      \"bolumSira\": ").append(kayit.bolumSira()).append(",\n");
            json.append("      \"bolumBasligi\": \"").append(jsonKacir(kayit.bolumBasligi())).append("\",\n");
            json.append("      \"bolumParcaSira\": ").append(kayit.bolumParcaSira()).append(",\n");
            json.append("      \"metinDosyasi\": \"").append(jsonKacir(kayit.dosyaAdi())).append("\",\n");
            json.append("      \"kaynakBolumDosyasi\": \"").append(jsonKacir(kayit.kaynakBolumDosyasi())).append("\",\n");
            json.append("      \"karakterSayisi\": ").append(kayit.karakterSayisi()).append(",\n");
            json.append("      \"kelimeSayisi\": ").append(kayit.kelimeSayisi()).append(",\n");
            json.append("      \"tahminiSureSaniye\": ").append(kayit.tahminiSureSaniye()).append(",\n");
            json.append("      \"sesDosyasi\": \"").append(jsonKacir(kayit.sesDosyasi())).append("\",\n");
            json.append("      \"durum\": \"Bekliyor\"\n");
            json.append("    }");
            if (i < kayitlar.size() - 1) {
                json.append(',');
            }
            json.append('\n');
        }

        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static String okumaListesi(List<ParcaKaydi> kayitlar) {
        StringBuilder sonuc = new StringBuilder();
        sonuc.append("TTS OKUMA LİSTESİ\n");
        sonuc.append("=================\n\n");
        for (ParcaKaydi kayit : kayitlar) {
            sonuc.append(String.format(
                    Locale.ROOT,
                    "%03d | Bölüm %03d | Parça %03d | %s | %d karakter | yaklaşık %s%n",
                    kayit.sira(),
                    kayit.bolumSira(),
                    kayit.bolumParcaSira(),
                    kayit.dosyaAdi(),
                    kayit.karakterSayisi(),
                    sureMetni(kayit.tahminiSureSaniye())
            ));
        }
        return sonuc.toString();
    }

    private static String sureMetni(int saniye) {
        int dakika = saniye / 60;
        int kalan = saniye % 60;
        return dakika + " dk " + kalan + " sn";
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
            for (Path yol : stream.sorted(Comparator.reverseOrder()).toList()) {
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

    private record MetinBirimi(String metin, boolean yeniParagraf) {
    }

    private record ParcaKaydi(int sira,
                              int bolumSira,
                              String bolumBasligi,
                              int bolumParcaSira,
                              String dosyaAdi,
                              String kaynakBolumDosyasi,
                              int karakterSayisi,
                              int kelimeSayisi,
                              int tahminiSureSaniye,
                              String sesDosyasi) {
    }

    public record Sonuc(Path ttsKlasoru,
                        Path manifestDosyasi,
                        Path okumaListesiDosyasi,
                        int parcaSayisi,
                        int toplamKarakter,
                        int toplamKelime) {
    }

    public record TopluSonuc(int hazirlanan,
                             int zatenHazir,
                             int hatali,
                             int toplamParca) {
    }
}
