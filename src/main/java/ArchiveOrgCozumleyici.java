import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Archive.org details/metadata/download bağlantılarını çözer. Koleksiyon içindeki
 * alt klasör hedefini URL'den ayırır, doğru PDF/EPUB/TXT dosyasını benzerlik
 * puanıyla seçer ve gerektiğinde tam metin için ikinci bir eş dosya indirir.
 */
public final class ArchiveOrgCozumleyici {
    private static final String UA = "EserOtomasyon/22.0 Java/21";
    private static final long VARSAYILAN_MAKSIMUM_BAYT = 700L * 1024L * 1024L;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Sonuc indir(String girdi, Path hedefKlasor) throws Exception {
        Baglanti baglanti = baglantiyiCoz(girdi);
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://archive.org/metadata/" + urlParcasi(baglanti.identifier())))
                .timeout(Duration.ofSeconds(120))
                .header("User-Agent", UA)
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            throw new IllegalStateException("Archive.org metadata HTTP " + res.statusCode());
        }

        JsonNode root = json.readTree(res.body());
        DosyaSecimi secim = dosyaSec(girdi, root);
        long maksimum = maksimumIndirmeBayti();
        if (secim.ana().boyut() > 0 && secim.ana().boyut() > maksimum) {
            throw new IllegalArgumentException("Seçilen Archive.org dosyası güvenlik sınırını aşıyor: "
                    + baytYaz(secim.ana().boyut()) + " > " + baytYaz(maksimum)
                    + ". ESER_MAKSIMUM_INDIRME_MB ile sınır değiştirilebilir.");
        }

        Files.createDirectories(hedefKlasor);
        Path anaDosya = indirDosya(baglanti.identifier(), secim.ana(), hedefKlasor);
        Path metinYedegi = null;
        String yedekIndirmeUrl = "";
        if (secim.metinYedegi() != null && !secim.metinYedegi().ad().equals(secim.ana().ad())) {
            if (secim.metinYedegi().boyut() <= 0 || secim.metinYedegi().boyut() <= maksimum) {
                metinYedegi = indirDosya(baglanti.identifier(), secim.metinYedegi(), hedefKlasor);
                yedekIndirmeUrl = indirmeUri(baglanti.identifier(), secim.metinYedegi().ad()).toString();
            }
        }

        JsonNode metadata = root.path("metadata");
        HedefIpucu ipucu = hedefIpucu(baglanti.hedefYolu());
        return new Sonuc(
                anaDosya,
                metinYedegi,
                baglanti.identifier(),
                text(metadata.path("title")),
                text(metadata.path("creator")),
                text(metadata.path("publisher")),
                ilkDolu(text(metadata.path("date")), text(metadata.path("year"))),
                text(metadata.path("language")),
                ilkDolu(text(metadata.path("licenseurl")), text(metadata.path("rights")), "Kontrol edilmedi"),
                girdi,
                indirmeUri(baglanti.identifier(), secim.ana().ad()).toString(),
                yedekIndirmeUrl,
                secim.ana().ad(),
                secim.metinYedegi() == null ? "" : secim.metinYedegi().ad(),
                baglanti.hedefYolu(),
                ipucu.baslik(),
                ipucu.yazar(),
                secim.ana().puan(),
                secim.aciklama()
        );
    }

    public DosyaSecimi incele(String girdi) throws Exception {
        Baglanti baglanti = baglantiyiCoz(girdi);
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://archive.org/metadata/" + urlParcasi(baglanti.identifier())))
                .timeout(Duration.ofSeconds(120)).header("User-Agent", UA).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IllegalStateException("Archive.org metadata HTTP " + res.statusCode());
        return dosyaSec(girdi, json.readTree(res.body()));
    }

    static DosyaSecimi dosyaSec(String girdi, JsonNode root) {
        Baglanti baglanti = baglantiyiCoz(girdi);
        List<Aday> adaylar = adaylariOlustur(root.path("files"), baglanti.hedefYolu());
        if (adaylar.isEmpty()) {
            throw new IllegalArgumentException("Archive.org öğesinde uygun PDF/EPUB/TXT bulunamadı.");
        }

        adaylar.sort(Comparator.comparingInt(Aday::puan).reversed()
                .thenComparingLong(Aday::boyut)
                .thenComparing(Aday::ad));
        Aday ana = adaylar.getFirst();

        int hedefEslesmesi = ana.hedefPuani();
        Aday yedek = adaylar.stream()
                .filter(a -> !a.ad().equals(ana.ad()))
                .filter(a -> metinYedegiMi(a.ad()))
                .filter(a -> baglanti.hedefYolu().isBlank()
                        ? a.hedefPuani() >= hedefEslesmesi - 80
                        : a.hedefPuani() >= Math.max(220, hedefEslesmesi - 180))
                .max(Comparator.comparingInt(ArchiveOrgCozumleyici::yedekPuani)
                        .thenComparingInt(Aday::hedefPuani)
                        .thenComparingLong(a -> -a.boyut()))
                .orElse(null);

        String aciklama = baglanti.hedefYolu().isBlank()
                ? "Öğe geneli için en uygun kaynak seçildi."
                : "URL alt yolu ile dosya adı eşleştirildi: " + baglanti.hedefYolu();
        return new DosyaSecimi(ana, yedek, baglanti, List.copyOf(adaylar), aciklama);
    }

    private Path indirDosya(String identifier, Aday aday, Path hedefKlasor) throws Exception {
        Path hedef = benzersiz(hedefKlasor, guvenliAd(sonParca(aday.ad())));
        URI uri = indirmeUri(identifier, aday.ad());
        HttpRequest dr = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofHours(2))
                .header("User-Agent", UA)
                .GET().build();
        HttpResponse<Path> dres = http.send(dr, HttpResponse.BodyHandlers.ofFile(hedef));
        if (dres.statusCode() < 200 || dres.statusCode() >= 300) {
            Files.deleteIfExists(hedef);
            throw new IllegalStateException("Archive.org indirme HTTP " + dres.statusCode() + " | " + aday.ad());
        }
        return hedef;
    }

    public static boolean archiveOrgMu(String s) {
        try {
            String host = URI.create(s.trim()).getHost();
            return host != null && (host.equalsIgnoreCase("archive.org") || host.toLowerCase(Locale.ROOT).endsWith(".archive.org"));
        } catch (Exception e) {
            return false;
        }
    }

    public static Baglanti baglantiyiCoz(String girdi) {
        if (girdi == null || girdi.isBlank()) throw new IllegalArgumentException("Archive.org bağlantısı boş.");
        URI u = URI.create(girdi.trim());
        String[] ham = u.getPath().split("/");
        List<String> p = new ArrayList<>();
        for (String x : ham) if (!x.isBlank()) p.add(urlCoz(x));
        for (int i = 0; i < p.size() - 1; i++) {
            String tur = p.get(i).toLowerCase(Locale.ROOT);
            if (tur.equals("details") || tur.equals("metadata") || tur.equals("download")) {
                String id = p.get(i + 1);
                List<String> hedef = new ArrayList<>();
                for (int j = i + 2; j < p.size(); j++) {
                    String x = p.get(j);
                    if (x.equalsIgnoreCase("page") || x.equalsIgnoreCase("mode") || x.equalsIgnoreCase("stream")) break;
                    hedef.add(x);
                }
                return new Baglanti(id, String.join("/", hedef), tur);
            }
        }
        throw new IllegalArgumentException("Archive.org identifier çözülemedi.");
    }

    private static List<Aday> adaylariOlustur(JsonNode files, String hedefYolu) {
        List<Aday> sonuc = new ArrayList<>();
        for (JsonNode f : files) {
            String ad = text(f.path("name"));
            if (ad.isBlank() || f.path("private").asBoolean(false) || !destekli(ad)) continue;
            long size = parseLong(text(f.path("size")));
            String source = text(f.path("source"));
            String format = text(f.path("format"));
            int hedef = hedefPuani(ad, hedefYolu);
            int puan = hedef + formatPuani(ad, format) + ("original".equalsIgnoreCase(source) ? 55 : 0);
            String k = normalize(ad);
            if (k.contains("metadata") || k.contains("files xml") || k.contains("meta xml")) puan -= 500;
            if (k.contains("_bw") || k.contains("black and white")) puan -= 35;
            sonuc.add(new Aday(ad, source, format, size, puan, hedef));
        }
        return sonuc;
    }

    private static int hedefPuani(String adayAdi, String hedefYolu) {
        if (hedefYolu == null || hedefYolu.isBlank()) return 0;
        String h = normalize(uzantisiz(hedefYolu));
        String a = normalize(uzantisiz(adayAdi));
        String hb = normalize(uzantisiz(sonParca(hedefYolu)));
        String ab = normalize(uzantisiz(sonParca(adayAdi)));
        int p = 0;
        if (a.equals(h)) p += 1500;
        if (ab.equals(hb) && !hb.isBlank()) p += 1050;
        if (a.startsWith(h) || h.startsWith(a)) p += 750;
        if (a.contains(h) || h.contains(a)) p += 650;
        if (a.contains(hb) && hb.length() >= 5) p += 520;
        p += (int) Math.round(jaccard(tokens(h), tokens(a)) * 650.0);
        p += ortakSonKlasorSayisi(hedefYolu, adayAdi) * 120;
        return p;
    }

    private static int formatPuani(String ad, String format) {
        String k = ad.toLowerCase(Locale.ROOT);
        String f = format.toLowerCase(Locale.ROOT);
        int p;
        if (k.endsWith(".pdf")) p = 175;
        else if (k.endsWith(".epub")) p = 165;
        else if (k.endsWith(".txt")) p = 95;
        else p = 30;
        if (k.contains("text pdf") || k.contains("searchable") || f.contains("text pdf")) p += 45;
        if (k.contains("djvu.txt") || f.contains("full text")) p += 35;
        if (k.contains("ocr")) p += 15;
        return p;
    }

    private static int yedekPuani(Aday a) {
        String k = a.ad().toLowerCase(Locale.ROOT);
        int p = a.hedefPuani();
        if (k.endsWith(".epub")) p += 320;
        else if (k.endsWith("_djvu.txt") || k.contains("full text")) p += 280;
        else if (k.endsWith(".txt")) p += 220;
        else if (k.endsWith(".pdf")) p += 80;
        return p;
    }

    private static boolean metinYedegiMi(String ad) {
        String k = ad.toLowerCase(Locale.ROOT);
        return k.endsWith(".epub") || k.endsWith(".txt") || k.contains("full text");
    }

    private static boolean destekli(String ad) {
        String k = ad.toLowerCase(Locale.ROOT);
        return k.endsWith(".pdf") || k.endsWith(".epub") || k.endsWith(".txt") || k.endsWith(".html") || k.endsWith(".htm");
    }

    private static HedefIpucu hedefIpucu(String hedefYolu) {
        String son = uzantisiz(sonParca(hedefYolu)).replace('_', ' ').trim();
        if (son.isBlank()) return new HedefIpucu("", "");
        String[] bol = son.split("\\s+-\\s+", 2);
        if (bol.length == 2 && bol[0].length() >= 2 && bol[1].length() >= 2) {
            return new HedefIpucu(bol[1].trim(), bol[0].trim());
        }
        return new HedefIpucu(son, "");
    }

    private static Set<String> tokens(String s) {
        Set<String> t = new HashSet<>();
        for (String x : s.split("[^a-z0-9]+")) if (x.length() >= 2) t.add(x);
        return t;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> kesisim = new HashSet<>(a); kesisim.retainAll(b);
        Set<String> birlesim = new HashSet<>(a); birlesim.addAll(b);
        return birlesim.isEmpty() ? 0 : (double) kesisim.size() / birlesim.size();
    }

    private static int ortakSonKlasorSayisi(String a, String b) {
        String[] x = normalize(a).split("/");
        String[] y = normalize(b).split("/");
        int i = x.length - 1, j = y.length - 1, n = 0;
        while (i >= 0 && j >= 0 && x[i].equals(y[j])) { n++; i--; j--; }
        return n;
    }

    private static URI indirmeUri(String identifier, String ad) throws Exception {
        return new URI("https", "archive.org", "/download/" + identifier + "/" + ad, null);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('ı', 'i').replace('İ', 'i')
                .toLowerCase(Locale.ROOT)
                .replace('\\', '/')
                .replaceAll("[^a-z0-9/]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String text(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return "";
        if (n.isArray()) {
            List<String> l = new ArrayList<>();
            for (JsonNode x : n) if (!x.asText("").isBlank()) l.add(x.asText());
            return String.join(", ", l);
        }
        return n.asText("");
    }

    private static String urlCoz(String s) { return URLDecoder.decode(s, StandardCharsets.UTF_8); }
    private static String urlParcasi(String s) { return s.replace(" ", "%20"); }
    private static String sonParca(String s) { if (s == null || s.isBlank()) return ""; String x=s.replace('\\','/'); int i=x.lastIndexOf('/'); return i<0?x:x.substring(i+1); }
    private static String uzantisiz(String s) { int slash=Math.max(s.lastIndexOf('/'),s.lastIndexOf('\\')); int dot=s.lastIndexOf('.'); return dot>slash?s.substring(0,dot):s; }
    private static long parseLong(String s) { try { return Long.parseLong(s); } catch (Exception e) { return 0L; } }
    private static String ilkDolu(String... s) { for (String x : s) if (x != null && !x.isBlank()) return x; return ""; }
    private static String guvenliAd(String s) {
        String x = s.replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ").trim();
        int nokta = x.lastIndexOf('.');
        String uzanti = nokta > 0 ? x.substring(nokta) : "";
        String govde = nokta > 0 ? x.substring(0, nokta) : x;
        if (govde.length() > 180) govde = govde.substring(0, 180).trim();
        return govde + uzanti;
    }
    private static Path benzersiz(Path dir, String ad) { Path p=dir.resolve(ad); int n=2; int dot=ad.lastIndexOf('.'); String b=dot>0?ad.substring(0,dot):ad,e=dot>0?ad.substring(dot):""; while(Files.exists(p))p=dir.resolve(b+" ("+(n++)+")"+e); return p; }
    private static long maksimumIndirmeBayti() { String x=System.getenv("ESER_MAKSIMUM_INDIRME_MB"); try { return x==null||x.isBlank()?VARSAYILAN_MAKSIMUM_BAYT:Long.parseLong(x.trim())*1024L*1024L; } catch(Exception e){return VARSAYILAN_MAKSIMUM_BAYT;} }
    private static String baytYaz(long b) { return String.format(Locale.ROOT, "%.1f MB", b/1024.0/1024.0); }

    public record Baglanti(String identifier, String hedefYolu, String tur) {}
    public record Aday(String ad, String kaynak, String format, long boyut, int puan, int hedefPuani) {}
    public record DosyaSecimi(Aday ana, Aday metinYedegi, Baglanti baglanti, List<Aday> siraliAdaylar, String aciklama) {}
    private record HedefIpucu(String baslik, String yazar) {}

    public record Sonuc(Path dosya, Path metinYedegi, String identifier,
                        String itemBasligi, String itemYazari, String yayinevi,
                        String yayinTarihi, String dil, String lisans,
                        String kaynakUrl, String indirmeUrl, String yedekIndirmeUrl,
                        String archiveDosyaAdi, String yedekArchiveDosyaAdi,
                        String hedefYolu, String hedefBasligi, String hedefYazari,
                        int secimPuani, String secimAciklamasi) {}
}
