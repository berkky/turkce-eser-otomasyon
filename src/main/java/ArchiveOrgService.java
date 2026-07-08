import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ArchiveOrgService {
    private static final int LISTE_SINIRI = 50;
    private static final String USER_AGENT = "EserOtomasyon/1.0 (+Java 21)";

    private final BufferedReader okuyucu;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public ArchiveOrgService(BufferedReader okuyucu) {
        this.okuyucu = Objects.requireNonNull(okuyucu, "okuyucu");
        this.mapper = new ObjectMapper();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(25))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public IndirmeSonucu interaktifIndir(Path hedefKlasor) throws Exception {
        String secim = sor("Archive.org'dan PDF veya EPUB indirmek ister misin? (E/H): ");
        if (!evetMi(secim)) {
            return null;
        }

        String girdi = sor("Archive.org bağlantısını yapıştır: ");
        if (girdi.isBlank()) {
            System.out.println("Bağlantı boş bırakıldı. İndirme atlandı.");
            return null;
        }

        BaglantiBilgisi baglanti = baglantiyiCoz(girdi);
        System.out.println("Archive.org öğesi: " + baglanti.identifier());
        System.out.println("Dosya listesi alınıyor...");

        MetadataSonucu metadata = metadataGetir(baglanti);
        List<ArchiveDosyasi> eserDosyalari = eserDosyalariniSec(metadata.dosyalar(), baglanti.tercihYolu());

        if (eserDosyalari.isEmpty()) {
            System.out.println("Bu bağlantıda indirilebilir PDF veya EPUB bulunamadı.");
            return null;
        }

        if (eserDosyalari.size() > LISTE_SINIRI) {
            System.out.println("Çok sayıda eser dosyası bulundu: " + eserDosyalari.size());
            String arama = sor("Dosya adında aranacak kelimeyi yaz (boş bırakırsan ilk 50 gösterilir): ");
            if (!arama.isBlank()) {
                List<ArchiveDosyasi> filtreli = adaGoreFiltrele(eserDosyalari, arama);
                if (!filtreli.isEmpty()) {
                    eserDosyalari = filtreli;
                } else {
                    System.out.println("Arama kelimesine uyan eser dosyası bulunamadı; ilk 50 dosya gösterilecek.");
                }
            }
        }

        int gosterilecek = Math.min(eserDosyalari.size(), LISTE_SINIRI);
        System.out.println();
        System.out.println("--- BULUNAN PDF / EPUB DOSYALARI ---");
        for (int i = 0; i < gosterilecek; i++) {
            ArchiveDosyasi dosya = eserDosyalari.get(i);
            System.out.printf(Locale.ROOT, "%2d - %s | %s | %s%n",
                    i + 1,
                    dosya.ad(),
                    boyutYaz(dosya.boyut()),
                    dosya.kaynakTuru());
        }

        if (eserDosyalari.size() > gosterilecek) {
            System.out.println("Not: Yalnızca ilk " + gosterilecek + " sonuç gösterildi.");
        }

        int dosyaNo = sayiSor("İndirilecek dosya numarası (0 = iptal): ", 0, gosterilecek);
        if (dosyaNo == 0) {
            System.out.println("İndirme iptal edildi.");
            return null;
        }

        ArchiveDosyasi secilen = eserDosyalari.get(dosyaNo - 1);
        Path indirilen = indir(baglanti.identifier(), secilen, hedefKlasor);

        String kaynakUrl = baglanti.orijinalGirdi().startsWith("http")
                ? baglanti.orijinalGirdi()
                : detailsUrl(baglanti.identifier());
        String indirmeUrl = downloadUri(baglanti.identifier(), secilen.ad()).toString();

        KaynakBilgisi kaynak = new KaynakBilgisi(
                kaynakUrl,
                indirmeUrl,
                metadata.lisans(),
                baglanti.identifier(),
                secilen.ad(),
                metadata.itemBasligi()
        );
        KaynakBilgisiService.kaydet(indirilen, kaynak);

        System.out.println("Eser dosyası başarıyla indirildi:");
        System.out.println(indirilen.toAbsolutePath());
        System.out.println("Kaynak bilgisi yan dosyaya kaydedildi.");
        return new IndirmeSonucu(indirilen, kaynak);
    }

    private MetadataSonucu metadataGetir(BaglantiBilgisi baglanti) throws Exception {
        URI uri = new URI("https", "archive.org", "/metadata/" + baglanti.identifier(), null);
        HttpRequest istek = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> cevap = http.send(istek, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (cevap.statusCode() != 200) {
            throw new IllegalStateException("Archive.org metadata isteği başarısız: HTTP " + cevap.statusCode());
        }

        JsonNode kok = mapper.readTree(cevap.body());
        JsonNode files = kok.path("files");
        if (!files.isArray()) {
            throw new IllegalStateException("Archive.org yanıtında dosya listesi bulunamadı.");
        }

        List<ArchiveDosyasi> dosyalar = new ArrayList<>();
        for (JsonNode file : files) {
            String ad = text(file.get("name"));
            if (ad.isBlank()) {
                continue;
            }

            boolean ozel = booleanDeger(file.get("private"));
            if (ozel) {
                continue;
            }

            String kucuk = ad.toLowerCase(Locale.ROOT);
            String format = text(file.get("format"));
            String formatKucuk = format.toLowerCase(Locale.ROOT);
            boolean pdf = kucuk.endsWith(".pdf") || formatKucuk.contains("pdf");
            boolean epub = kucuk.endsWith(".epub") || formatKucuk.contains("epub");
            if (!pdf && !epub) {
                continue;
            }

            long boyut = longDeger(file.get("size"));
            String source = text(file.get("source"));
            String kaynakTuru = source.equalsIgnoreCase("original") ? "Orijinal" : "Türetilmiş";
            dosyalar.add(new ArchiveDosyasi(ad, boyut, format, kaynakTuru));
        }

        JsonNode metadata = kok.path("metadata");
        String itemBasligi = metadataMetni(metadata, "title");
        String lisans = ilkDolu(
                metadataMetni(metadata, "licenseurl"),
                metadataMetni(metadata, "license"),
                metadataMetni(metadata, "rights"),
                metadataMetni(metadata, "usage")
        );
        if (lisans.isBlank()) {
            lisans = "Kontrol edilmedi";
        }

        return new MetadataSonucu(dosyalar, itemBasligi, lisans);
    }

    private List<ArchiveDosyasi> eserDosyalariniSec(List<ArchiveDosyasi> tumu, String tercihYolu) {
        List<ArchiveDosyasi> sonuc = new ArrayList<>(tumu);
        String tercih = normalize(tercihYolu);

        if (!tercih.isBlank()) {
            List<ArchiveDosyasi> eslesen = sonuc.stream()
                    .filter(d -> eslesmePuani(d.ad(), tercihYolu) > 0)
                    .toList();
            if (!eslesen.isEmpty()) {
                sonuc = new ArrayList<>(eslesen);
                System.out.println("Bağlantıdaki alt yol ile eşleşen dosya sayısı: " + sonuc.size());
            }
        }

        sonuc.sort(Comparator
                .comparingInt((ArchiveDosyasi d) -> eslesmePuani(d.ad(), tercihYolu)).reversed()
                .thenComparingInt(d -> "Orijinal".equals(d.kaynakTuru()) ? 0 : 1)
                .thenComparing(ArchiveDosyasi::ad, String.CASE_INSENSITIVE_ORDER));
        return sonuc;
    }

    private List<ArchiveDosyasi> adaGoreFiltrele(List<ArchiveDosyasi> eserDosyalari, String arama) {
        String normalizeArama = normalize(arama);
        return eserDosyalari.stream()
                .filter(d -> normalize(d.ad()).contains(normalizeArama))
                .toList();
    }

    private Path indir(String identifier, ArchiveDosyasi dosya, Path hedefKlasor) throws Exception {
        Files.createDirectories(hedefKlasor);
        URI uri = downloadUri(identifier, dosya.ad());

        HttpRequest istek = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(20))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        System.out.println();
        System.out.println("İndiriliyor: " + dosya.ad());
        HttpResponse<java.io.InputStream> cevap = http.send(istek, HttpResponse.BodyHandlers.ofInputStream());
        if (cevap.statusCode() != 200) {
            try (var ignored = cevap.body()) {
                // Akış kapatılır.
            }
            throw new IllegalStateException("Eser dosyası indirilemedi: HTTP " + cevap.statusCode());
        }

        String yerelAd = guvenliYerelAd(dosya.ad());
        Path hedef = benzersizHedef(hedefKlasor, yerelAd);
        Path gecici = hedef.resolveSibling(hedef.getFileName() + ".part");

        long toplam = dosya.boyut() > 0
                ? dosya.boyut()
                : cevap.headers().firstValueAsLong("Content-Length").orElse(-1L);

        long yazilan = 0;
        long sonrakiBildirim = 5L * 1024L * 1024L;
        try (BufferedInputStream input = new BufferedInputStream(cevap.body());
             BufferedOutputStream output = new BufferedOutputStream(Files.newOutputStream(gecici))) {
            byte[] buffer = new byte[64 * 1024];
            int okunan;
            while ((okunan = input.read(buffer)) != -1) {
                output.write(buffer, 0, okunan);
                yazilan += okunan;

                if (yazilan >= sonrakiBildirim) {
                    ilerlemeYaz(yazilan, toplam);
                    sonrakiBildirim += 5L * 1024L * 1024L;
                }
            }
        } catch (Exception e) {
            Files.deleteIfExists(gecici);
            throw e;
        }

        if (yazilan == 0) {
            Files.deleteIfExists(gecici);
            throw new IllegalStateException("İndirilen eser dosyası boş görünüyor.");
        }

        if (dosya.boyut() > 0 && yazilan != dosya.boyut()) {
            Files.deleteIfExists(gecici);
            throw new IllegalStateException(
                    "İndirme boyutu uyuşmadı. Beklenen: " + dosya.boyut() + ", gelen: " + yazilan
            );
        }

        try {
            Files.move(gecici, hedef, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.move(gecici, hedef, StandardCopyOption.REPLACE_EXISTING);
        }

        ilerlemeYaz(yazilan, toplam);
        return hedef;
    }

    private BaglantiBilgisi baglantiyiCoz(String girdi) throws Exception {
        String temiz = girdi.trim();
        if (!temiz.contains("://")) {
            if (!temiz.matches("[A-Za-z0-9._-]+")) {
                throw new IllegalArgumentException("Geçerli bir Archive.org bağlantısı veya öğe kimliği girilmedi.");
            }
            return new BaglantiBilgisi(temiz, "", temiz);
        }

        URI uri = URI.create(temiz);
        String host = uri.getHost();
        if (host == null || !(host.equalsIgnoreCase("archive.org") || host.endsWith(".archive.org"))) {
            throw new IllegalArgumentException("Bağlantı archive.org alan adına ait değil.");
        }

        String[] parcalar = uri.getRawPath().split("/");
        int kokIndex = -1;
        for (int i = 0; i < parcalar.length; i++) {
            String parca = parcalar[i];
            if ("details".equalsIgnoreCase(parca)
                    || "download".equalsIgnoreCase(parca)
                    || "metadata".equalsIgnoreCase(parca)) {
                kokIndex = i;
                break;
            }
        }

        if (kokIndex < 0 || kokIndex + 1 >= parcalar.length) {
            throw new IllegalArgumentException("Bağlantıdan Archive.org öğe kimliği çıkarılamadı.");
        }

        String identifier = decode(parcalar[kokIndex + 1]);
        if (!identifier.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Archive.org öğe kimliği geçersiz: " + identifier);
        }

        StringBuilder altYol = new StringBuilder();
        for (int i = kokIndex + 2; i < parcalar.length; i++) {
            if (parcalar[i].isBlank()) {
                continue;
            }
            if (!altYol.isEmpty()) {
                altYol.append('/');
            }
            altYol.append(decode(parcalar[i]));
        }

        return new BaglantiBilgisi(identifier, altYol.toString(), temiz);
    }

    private static URI downloadUri(String identifier, String dosyaAdi) throws Exception {
        return new URI("https", "archive.org", "/download/" + identifier + "/" + dosyaAdi, null);
    }

    private static String detailsUrl(String identifier) {
        return "https://archive.org/details/" + identifier;
    }

    private static String metadataMetni(JsonNode metadata, String alan) {
        if (metadata == null || metadata.isMissingNode()) {
            return "";
        }
        return text(metadata.get(alan));
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isArray()) {
            List<String> degerler = new ArrayList<>();
            for (JsonNode eleman : node) {
                String deger = text(eleman);
                if (!deger.isBlank()) {
                    degerler.add(deger);
                }
            }
            return String.join(", ", degerler);
        }
        return node.asText().trim();
    }

    private static boolean booleanDeger(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        return node.isBoolean() ? node.asBoolean() : "true".equalsIgnoreCase(node.asText());
    }

    private static long longDeger(JsonNode node) {
        if (node == null || node.isNull()) {
            return -1L;
        }
        if (node.isNumber()) {
            return node.asLong(-1L);
        }
        try {
            return Long.parseLong(node.asText().trim());
        } catch (Exception e) {
            return -1L;
        }
    }

    private static int eslesmePuani(String dosyaAdi, String tercihYolu) {
        String dosya = normalize(dosyaAdi);
        String tercih = normalize(tercihYolu);
        if (tercih.isBlank()) {
            return 0;
        }

        String tercihSon = tercih;
        int slash = tercih.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < tercih.length()) {
            tercihSon = tercih.substring(slash + 1);
        }

        if (dosya.equals(tercih) || dosya.equals(tercih + ".pdf") || dosya.equals(tercih + ".epub")) {
            return 100;
        }
        if (dosya.startsWith(tercih)) {
            return 90;
        }
        if (dosya.contains(tercih)) {
            return 80;
        }
        if (!tercihSon.isBlank() && dosya.contains(tercihSon)) {
            return 60;
        }
        return 0;
    }

    private static String normalize(String metin) {
        if (metin == null) {
            return "";
        }
        return metin
                .toLowerCase(Locale.of("tr", "TR"))
                .replace('ı', 'i')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ş', 's')
                .replace('ö', 'o')
                .replace('ç', 'c')
                .replace('\\', '/')
                .replaceAll("\\.(pdf|epub)$", "")
                .replaceAll("[^a-z0-9/]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String decode(String metin) {
        return URLDecoder.decode(metin, StandardCharsets.UTF_8);
    }

    private static String ilkDolu(String... degerler) {
        for (String deger : degerler) {
            if (deger != null && !deger.isBlank()) {
                return deger.trim();
            }
        }
        return "";
    }

    private static String guvenliYerelAd(String archiveAdi) {
        String ad = archiveAdi.replace('\\', '/');
        int slash = ad.lastIndexOf('/');
        if (slash >= 0) {
            ad = ad.substring(slash + 1);
        }
        ad = ad.replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("\\s+", " ")
                .trim();

        String kucuk = ad.toLowerCase(Locale.ROOT);
        String uzanti = kucuk.endsWith(".epub") ? ".epub" : ".pdf";
        if (!kucuk.endsWith(".pdf") && !kucuk.endsWith(".epub")) {
            ad += uzanti;
        }
        if (ad.length() > 180) {
            String govde = uzantiyiKaldir(ad);
            govde = govde.substring(0, Math.min(170, govde.length())).trim();
            ad = govde + uzanti;
        }
        return ad.isBlank() ? "archive-org-eser" + uzanti : ad;
    }

    private static Path benzersizHedef(Path klasor, String dosyaAdi) {
        Path ilk = klasor.resolve(dosyaAdi);
        if (!Files.exists(ilk)) {
            return ilk;
        }

        int nokta = dosyaAdi.lastIndexOf('.');
        String govde = nokta > 0 ? dosyaAdi.substring(0, nokta) : dosyaAdi;
        String uzanti = nokta > 0 ? dosyaAdi.substring(nokta) : "";
        int sayac = 2;
        Path aday;
        do {
            aday = klasor.resolve(govde + " (" + sayac++ + ")" + uzanti);
        } while (Files.exists(aday));
        return aday;
    }

    private static String uzantiyiKaldir(String dosyaAdi) {
        int nokta = dosyaAdi.lastIndexOf('.');
        return nokta > 0 ? dosyaAdi.substring(0, nokta) : dosyaAdi;
    }

    private static String boyutYaz(long byteSayisi) {
        if (byteSayisi < 0) {
            return "Boyut bilinmiyor";
        }
        double mb = byteSayisi / (1024.0 * 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", mb);
    }

    private static void ilerlemeYaz(long yazilan, long toplam) {
        if (toplam > 0) {
            double yuzde = Math.min(100.0, yazilan * 100.0 / toplam);
            System.out.printf(Locale.ROOT, "İndirilen: %s / %s (%%%.1f)%n",
                    boyutYaz(yazilan), boyutYaz(toplam), yuzde);
        } else {
            System.out.println("İndirilen: " + boyutYaz(yazilan));
        }
    }

    private String sor(String soru) throws Exception {
        System.out.print(soru);
        System.out.flush();
        String cevap = okuyucu.readLine();
        return cevap == null ? "" : cevap.trim();
    }

    private int sayiSor(String soru, int min, int max) throws Exception {
        while (true) {
            String cevap = sor(soru);
            try {
                int sayi = Integer.parseInt(cevap);
                if (sayi >= min && sayi <= max) {
                    return sayi;
                }
            } catch (NumberFormatException ignored) {
            }
            System.out.println("Lütfen " + min + " ile " + max + " arasında bir sayı gir.");
        }
    }

    private static boolean evetMi(String cevap) {
        return "e".equalsIgnoreCase(cevap)
                || "evet".equalsIgnoreCase(cevap)
                || "y".equalsIgnoreCase(cevap)
                || "yes".equalsIgnoreCase(cevap);
    }

    public record IndirmeSonucu(Path dosya, KaynakBilgisi kaynakBilgisi) {
    }

    private record BaglantiBilgisi(String identifier, String tercihYolu, String orijinalGirdi) {
    }

    private record MetadataSonucu(List<ArchiveDosyasi> dosyalar, String itemBasligi, String lisans) {
    }

    private record ArchiveDosyasi(String ad, long boyut, String format, String kaynakTuru) {
    }
}
