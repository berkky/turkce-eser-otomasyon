import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Adım 23: Kaynağı işler; metadata güvenliği, kalıcı geçmiş ve güvenli kurtarma sağlar. */
public final class EserKaynakAlimService {
    private static final Pattern ESER_ID = Pattern.compile("(?i)^ESER-(\\d{1,8})(?:\\s*-.*)?$");
    private static final String UA = "EserOtomasyon/23.0 Java/21";

    private final Path proje;
    private final Path gelen;
    private final Path arsiv;
    private final Path metinArsivi;
    private final Path sesArsivi;
    private final Path kuyruk;
    private final Path katalogYolu;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final MetadataCikarmaService metadataService = new MetadataCikarmaService();
    private final EserKatalogService katalog;

    public EserKaynakAlimService(Path proje, Path gelen, Path arsiv, Path metinArsivi, Path sesArsivi, Path kuyruk) {
        this.proje = proje;
        this.gelen = gelen;
        this.arsiv = arsiv;
        this.metinArsivi = metinArsivi;
        this.sesArsivi = sesArsivi;
        this.kuyruk = kuyruk;
        this.katalogYolu = EserVeriYollari.varsayilan().katalog();
        this.katalog = new EserKatalogService(katalogYolu);
    }

    public DoktorSonucu doktor() throws Exception {
        Files.createDirectories(gelen);
        Files.createDirectories(gelen.resolve("_indirilen"));
        Files.createDirectories(gelen.resolve("_islenen"));
        Files.createDirectories(gelen.resolve("_tekrarlar"));
        Files.createDirectories(gelen.resolve("_hatalar"));
        Files.createDirectories(arsiv);
        Files.createDirectories(metinArsivi);
        Files.createDirectories(sesArsivi);
        Files.createDirectories(kuyruk);
        return new DoktorSonucu(proje, gelen, arsiv, metinArsivi, sesArsivi, kuyruk, katalogYolu,
                Files.isDirectory(gelen) && Files.isDirectory(arsiv) && Files.isDirectory(metinArsivi));
    }

    public KaynakAlimSonucu yerelDosyaAl(Path kaynak) throws Exception {
        if (kaynak == null || !Files.isRegularFile(kaynak)) throw new IllegalArgumentException("Kaynak dosya bulunamadı: " + kaynak);
        if (!destekli(uzanti(kaynak))) throw new IllegalArgumentException("Desteklenmeyen dosya türü: " + uzanti(kaynak));
        return isle(kaynak.toAbsolutePath().normalize(), null, kaynak.toUri().toString(), null);
    }

    public KaynakAlimSonucu urlAl(String url) throws Exception {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("URL boş olamaz.");
        if (ArchiveOrgCozumleyici.archiveOrgMu(url)) {
            ArchiveOrgCozumleyici.Sonuc a = new ArchiveOrgCozumleyici().indir(url, gelen.resolve("_indirilen"));
            return isle(a.dosya(), a.metinYedegi(), a.kaynakUrl(), a);
        }

        URI uri = URI.create(url.trim());
        if (uri.getScheme() == null || !List.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Yalnız HTTP/HTTPS URL desteklenir.");
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(20)).header("User-Agent", UA).GET().build();
        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() < 200 || res.statusCode() >= 300) throw new IllegalStateException("URL indirme HTTP " + res.statusCode());
        String contentType = res.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
        String dosyaAdi = urlDosyaAdi(uri, contentType);
        Files.createDirectories(gelen.resolve("_indirilen"));
        Path hedef = benzersiz(gelen.resolve("_indirilen"), dosyaAdi);
        Files.write(hedef, res.body());
        return isle(hedef, null, url, null);
    }

    public TopluUrlSonucu urlListesiAl(Path liste) throws Exception {
        if (!Files.isRegularFile(liste)) throw new IllegalArgumentException("URL listesi bulunamadı: " + liste);
        List<String> urls = Files.readAllLines(liste, StandardCharsets.UTF_8).stream()
                .map(String::trim).filter(s -> !s.isBlank() && !s.startsWith("#")).toList();
        int basarili=0,tekrar=0,hatali=0; List<String> mesajlar=new ArrayList<>();
        for(String u:urls){
            try{KaynakAlimSonucu s=urlAl(u); if(s.tekrar())tekrar++;else basarili++; mesajlar.add(u+" -> "+s.mesaj());}
            catch(Exception e){hatali++;mesajlar.add(u+" -> HATA: "+e.getMessage());}
        }
        return new TopluUrlSonucu(urls.size(),basarili,tekrar,hatali,mesajlar);
    }

    public ArchiveOrgCozumleyici.DosyaSecimi archiveIncele(String url) throws Exception {
        return new ArchiveOrgCozumleyici().incele(url);
    }

    public TopluSonuc gelenKutusuIsle() throws Exception {
        Files.createDirectories(gelen);
        List<Path> dosyalar;
        try (Stream<Path> s = Files.list(gelen)) {
            dosyalar = s.filter(Files::isRegularFile).filter(p -> destekli(uzanti(p)))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
        }
        int basarili = 0, tekrar = 0, hatali = 0;
        List<String> mesajlar = new ArrayList<>();
        for (Path p : dosyalar) {
            try {
                KaynakAlimSonucu s = yerelDosyaAl(p);
                if (s.tekrar()) tekrar++; else basarili++;
                mesajlar.add(p.getFileName() + " -> " + s.mesaj());
            } catch (Exception e) {
                hatali++; mesajlar.add(p.getFileName() + " -> HATA: " + e.getMessage()); hataKaydet(p, e);
            }
        }
        return new TopluSonuc(dosyalar.size(), basarili, tekrar, hatali, mesajlar);
    }

    public List<AlimDurumu> durumlar() throws Exception {
        Files.createDirectories(metinArsivi);
        List<AlimDurumu> sonuc = new ArrayList<>();
        try (Stream<Path> s = Files.list(metinArsivi)) {
            for (Path d : s.filter(Files::isDirectory).sorted().toList()) {
                Path m = d.resolve("alim-manifest.json");
                if (!Files.isRegularFile(m)) continue;
                JsonNode n = json.readTree(m.toFile());
                sonuc.add(new AlimDurumu(n.path("eserId").asInt(), n.path("eserAdi").asText(),
                        n.path("tur").asText(), n.path("toplamKarakter").asInt(),
                        n.path("bolumSayisi").asInt(), Files.isDirectory(d.resolve("tts-parcalari")),
                        n.path("metadataDurumu").asText("BILINMIYOR"), d));
            }
        }
        return sonuc;
    }

    public UzlastirmaSonucu kataloguUzlastir() throws Exception {
        int eklendi=0,degismedi=0,hatali=0; List<String> mesajlar=new ArrayList<>();
        if(!Files.isDirectory(metinArsivi)) return new UzlastirmaSonucu(0,0,0,List.of());
        try(Stream<Path>s=Files.list(metinArsivi)){
            for(Path d:s.filter(Files::isDirectory).sorted().toList()){
                Path mf=d.resolve("alim-manifest.json"); if(!Files.isRegularFile(mf))continue;
                try{
                    JsonNode n=json.readTree(mf.toFile()); int id=n.path("eserId").asInt();
                    if(id<=0||katalog.idVarMi(id)){degismedi++;continue;}
                    EserMetadata m=metadataOku(n.path("metadata"));
                    if(!EserMetadata.bilinen(m.eserAdi))m.eserAdi=n.path("eserAdi").asText("Bilinmiyor");
                    Path ar=n.path("arsivDosyasi").asText("").isBlank()?null:Path.of(n.path("arsivDosyasi").asText());
                    katalog.kaydet(new EserKatalogService.KatalogKaydi(id,m,ar,d,n.path("tur").asText(),n.path("kaynakSha256").asText(),
                            n.path("kaynakBirimSayisi").asInt(),n.path("toplamKarakter").asInt(),n.path("bolumSayisi").asInt(),
                            Files.isDirectory(d.resolve("tts-parcalari"))?"HAZIR":"YOK","","","",n.path("alimZamani").asText()));
                    eklendi++; mesajlar.add(String.format(Locale.ROOT,"ESER-%05d kataloğa eklendi.",id));
                }catch(Exception e){hatali++;mesajlar.add(d.getFileName()+" -> HATA: "+e.getMessage());}
            }
        }
        return new UzlastirmaSonucu(eklendi,degismedi,hatali,mesajlar);
    }

    public MetadataOnarimSonucu metadataOnar(int eserId) throws Exception {
        if (eserId <= 0) throw new IllegalArgumentException("Geçerli bir eser ID gerekli.");
        Path metinKlasoru = eserKlasoruBul(metinArsivi, eserId);
        if (metinKlasoru == null) throw new IllegalArgumentException(String.format(Locale.ROOT,"ESER-%05d metin arşivinde bulunamadı.", eserId));
        Path manifestYolu = metinKlasoru.resolve("alim-manifest.json");
        Path tamMetinYolu = metinKlasoru.resolve("tam-metin.txt");
        Path bilgiYolu = metinKlasoru.resolve("000-eser-bilgisi.txt");
        if (!Files.isRegularFile(manifestYolu) || !Files.isRegularFile(tamMetinYolu))
            throw new IllegalStateException("Onarım için alim-manifest.json ve tam-metin.txt gerekli.");

        byte[] manifestYedegi = Files.readAllBytes(manifestYolu);
        byte[] bilgiYedegi = Files.isRegularFile(bilgiYolu) ? Files.readAllBytes(bilgiYolu) : null;
        byte[] katalogYedegi = Files.isRegularFile(katalogYolu) ? Files.readAllBytes(katalogYolu) : null;
        ObjectNode manifest = (ObjectNode) json.readTree(new String(manifestYedegi, StandardCharsets.UTF_8));
        EserMetadata onceki = metadataOku(manifest.path("metadata"));
        String oncekiOzet = json.writeValueAsString(onceki.map());
        String tamMetin = Files.readString(tamMetinYolu, StandardCharsets.UTF_8);

        Path arsivDosyasi = yolVeyaBos(manifest.path("arsivDosyasi").asText(""));
        if (arsivDosyasi == null || !Files.isRegularFile(arsivDosyasi)) arsivDosyasi = arsivKaynakDosyasiBul(eserId);
        String dosyaAdi = arsivDosyasi == null ? onceki.orijinalDosyaAdi : arsivDosyasi.getFileName().toString();

        Path arsivKlasoru = eserKlasoruBul(arsiv, eserId);
        Path arsivMetadataYolu = arsivKlasoru == null ? null : arsivKlasoru.resolve("metadata").resolve("eser-metadata.json");
        byte[] arsivMetadataYedegi = arsivMetadataYolu != null && Files.isRegularFile(arsivMetadataYolu)
                ? Files.readAllBytes(arsivMetadataYolu) : null;
        Path gecmis = metadataGecmisiOlustur(eserId, manifestYolu, bilgiYolu, arsivMetadataYolu, arsivDosyasi,
                "yerel-onarim-oncesi");

        EserMetadata yeni = metadataService.yerelOnar(onceki, tamMetin, dosyaAdi);
        boolean onizlemeOcrKullanildi = false;
        if (YerelOcrService.kullanilabilirMi()) {
            Path onizlemePdf = arsivKlasoru == null ? null : arsivKlasoru.resolve("onizleme").resolve("ilk-3-sayfa.pdf");
            if (onizlemePdf != null && Files.isRegularFile(onizlemePdf)) {
                try {
                    String onSayfaMetni = YerelOcrService.pdfOcr(onizlemePdf, 3).tamMetin();
                    if (!onSayfaMetni.isBlank()) {
                        yeni = metadataService.yerelOnar(yeni, onSayfaMetni, dosyaAdi);
                        String ocrKaniti = "Arşiv ön izlemesinin ilk 3 sayfası Tesseract ile yerel OCR edildi; otomatik onay verilmedi";
                        yeni.kanit = ekle(yeni.kanit, ocrKaniti);
                        yeni.temizle();
                        onizlemeOcrKullanildi = true;
                    }
                } catch (Exception ignored) {
                    // OCR yardımcı katmandır; hata halinde mevcut kayıt korunur.
                }
            }
        }

        try {
            String sonraOzet = json.writeValueAsString(yeni.map());
            boolean degisti = !oncekiOzet.equals(sonraOzet);

            manifest.put("surum", 4);
            manifest.put("eserAdi", yeni.eserAdi);
            manifest.put("yazar", yeni.yazar);
            manifest.put("dil", yeni.dil);
            manifest.put("lisans", yeni.lisans);
            manifest.put("arsivDosyasi", arsivDosyasi == null ? "" : arsivDosyasi.toAbsolutePath().toString());
            manifest.put("metadataDurumu", yeni.metadataDurumu);
            manifest.put("metadataGuvenPuani", yeni.guvenPuani);
            manifest.put("metadataOnarimZamani", OffsetDateTime.now().toString());
            manifest.put("metadataGecmisKlasoru", gecmis.toAbsolutePath().toString());
            metadataNodeYaz(manifest.putObject("metadata"), yeni);
            jsonAtomikYaz(manifestYolu, manifest);
            metinAtomikYaz(bilgiYolu, manifestOzet(manifest));

            if (arsivMetadataYolu != null) {
                Files.createDirectories(arsivMetadataYolu.getParent());
                jsonAtomikYaz(arsivMetadataYolu, yeni.map());
            }

            katalog.kaydet(new EserKatalogService.KatalogKaydi(eserId, yeni, arsivDosyasi, metinKlasoru,
                    manifest.path("tur").asText(), manifest.path("kaynakSha256").asText(),
                    manifest.path("kaynakBirimSayisi").asInt(), manifest.path("toplamKarakter").asInt(),
                    manifest.path("bolumSayisi").asInt(), Files.isDirectory(metinKlasoru.resolve("tts-parcalari")) ? "HAZIR" : "YOK",
                    "", "", "", manifest.path("alimZamani").asText(OffsetDateTime.now().toString())));

            return new MetadataOnarimSonucu(eserId, yeni.eserAdi, yeni.yazar, yeni.yayinevi, yeni.yayinYili,
                    yeni.isbn, yeni.cevirmen, yeni.metadataDurumu, yeni.guvenPuani, degisti,
                    arsivDosyasi, "Metadata yerel künye motoruyla güvenli biçimde zenginleştirildi"
                            + (onizlemeOcrKullanildi ? "; ilk 3 sayfa yerel OCR ile desteklendi" : "")
                            + "; dosya adı ve TTS parçaları değiştirilmedi; geçmiş=" + gecmis);
        } catch (Exception e) {
            byteAtomikYaz(manifestYolu, manifestYedegi);
            if (bilgiYedegi == null) Files.deleteIfExists(bilgiYolu); else byteAtomikYaz(bilgiYolu, bilgiYedegi);
            if (arsivMetadataYolu != null) {
                if (arsivMetadataYedegi == null) Files.deleteIfExists(arsivMetadataYolu);
                else byteAtomikYaz(arsivMetadataYolu, arsivMetadataYedegi);
            }
            if (katalogYedegi == null) Files.deleteIfExists(katalogYolu); else byteAtomikYaz(katalogYolu, katalogYedegi);
            throw new IllegalStateException("Metadata onarımı geri alındı: " + e.getMessage(), e);
        }
    }

    /** Şüpheli/OCR kaynaklı bozulmuş metadata kaydını Archive.org kanonik adına geri döndürür. */
    public MetadataKurtarmaSonucu metadataKurtar(int eserId) throws Exception {
        if (eserId <= 0) throw new IllegalArgumentException("Geçerli bir eser ID gerekli.");
        Path metinKlasoru = eserKlasoruBul(metinArsivi, eserId);
        Path arsivKlasoru = eserKlasoruBul(arsiv, eserId);
        if (metinKlasoru == null || arsivKlasoru == null)
            throw new IllegalArgumentException(String.format(Locale.ROOT,"ESER-%05d arşiv/metin kaydı bulunamadı.", eserId));

        Path manifestYolu = metinKlasoru.resolve("alim-manifest.json");
        Path bilgiYolu = metinKlasoru.resolve("000-eser-bilgisi.txt");
        Path arsivMetadataYolu = arsivKlasoru.resolve("metadata").resolve("eser-metadata.json");
        if (!Files.isRegularFile(manifestYolu)) throw new IllegalStateException("alim-manifest.json bulunamadı.");

        byte[] manifestYedegi = Files.readAllBytes(manifestYolu);
        byte[] bilgiYedegi = Files.isRegularFile(bilgiYolu) ? Files.readAllBytes(bilgiYolu) : null;
        byte[] arsivMetadataYedegi = Files.isRegularFile(arsivMetadataYolu) ? Files.readAllBytes(arsivMetadataYolu) : null;
        byte[] katalogYedegi = Files.isRegularFile(katalogYolu) ? Files.readAllBytes(katalogYolu) : null;
        ObjectNode manifest = (ObjectNode) json.readTree(new String(manifestYedegi, StandardCharsets.UTF_8));
        EserMetadata m = metadataOku(manifest.path("metadata"));

        Path oncekiDosya = yolVeyaBos(manifest.path("arsivDosyasi").asText(""));
        if (oncekiDosya == null || !Files.isRegularFile(oncekiDosya)) oncekiDosya = arsivKaynakDosyasiBul(eserId);
        if (oncekiDosya == null || !Files.isRegularFile(oncekiDosya)) throw new IllegalStateException("Arşiv kaynak dosyası bulunamadı.");

        Path gecmis = metadataGecmisiOlustur(eserId, manifestYolu, bilgiYolu, arsivMetadataYolu, oncekiDosya,
                "guvenli-kurtarma-oncesi");
        String kanonik = MetadataGuvenlikService.kanonikBaslik(m, manifest.path("eserAdi").asText(""));
        if (!EserMetadata.bilinen(kanonik)) throw new IllegalStateException("Kanonik eser adı Archive.org/dosya metadata bilgisinden belirlenemedi.");

        m.eserAdi = kanonik;
        m.yazar = MetadataGuvenlikService.guvenliYazar(m.yazar);
        m.yayinevi = MetadataGuvenlikService.guvenliYayinevi(m.yayinevi);
        if (MetadataGuvenlikService.supheliKisi(m.cevirmen)) m.cevirmen = "Bilinmiyor";
        m.guvenPuani = MetadataGuvenlikService.yerelOnarimGuveni(m);
        m.metadataDurumu = "KONTROL_GEREKIYOR";
        m.bilgiKaynagi = ekleKaynak(m.bilgiKaynagi, "Güvenli metadata kurtarma — Archive.org kanonik adı");
        m.kanit = ekle(m.kanit, "Şüpheli OCR başlık/yazar alanları güvenlik süzgeciyle temizlendi; kanonik ad Archive.org dosya adından geri yüklendi");
        m.temizle();

        String yeniAd = String.format(Locale.ROOT,"ESER-%05d - %s%s", eserId, guvenliDosyaAdi(kanonik), uzanti(oncekiDosya));
        Path yeniDosya = oncekiDosya.resolveSibling(yeniAd);
        boolean tasindi = false;
        try {
            if (!oncekiDosya.equals(yeniDosya)) {
                if (Files.exists(yeniDosya)) throw new IllegalStateException("Kurtarma hedef dosyası zaten var: " + yeniDosya);
                guvenliTasi(oncekiDosya, yeniDosya, false);
                tasindi = true;
            }

            manifest.put("surum", 4);
            manifest.put("eserAdi", m.eserAdi);
            manifest.put("yazar", m.yazar);
            manifest.put("dil", m.dil);
            manifest.put("lisans", m.lisans);
            manifest.put("arsivDosyasi", yeniDosya.toAbsolutePath().toString());
            manifest.put("metadataDurumu", m.metadataDurumu);
            manifest.put("metadataGuvenPuani", m.guvenPuani);
            manifest.put("metadataKurtarmaZamani", OffsetDateTime.now().toString());
            manifest.put("metadataGecmisKlasoru", gecmis.toAbsolutePath().toString());
            metadataNodeYaz(manifest.putObject("metadata"), m);
            jsonAtomikYaz(manifestYolu, manifest);
            metinAtomikYaz(bilgiYolu, manifestOzet(manifest));
            Files.createDirectories(arsivMetadataYolu.getParent());
            jsonAtomikYaz(arsivMetadataYolu, m.map());
            katalog.kaydet(new EserKatalogService.KatalogKaydi(eserId, m, yeniDosya, metinKlasoru,
                    manifest.path("tur").asText(), manifest.path("kaynakSha256").asText(),
                    manifest.path("kaynakBirimSayisi").asInt(), manifest.path("toplamKarakter").asInt(),
                    manifest.path("bolumSayisi").asInt(), Files.isDirectory(metinKlasoru.resolve("tts-parcalari")) ? "HAZIR" : "YOK",
                    "", "", "", manifest.path("alimZamani").asText(OffsetDateTime.now().toString())));

            return new MetadataKurtarmaSonucu(eserId, m.eserAdi, m.yazar, m.yayinevi, m.isbn,
                    m.metadataDurumu, m.guvenPuani, yeniDosya, gecmis,
                    "Bozuk OCR metadata kaydı güvenli biçimde geri alındı; Excel ve JSON eşitlendi; TTS parçaları değiştirilmedi.");
        } catch (Exception e) {
            byteAtomikYaz(manifestYolu, manifestYedegi);
            if (bilgiYedegi == null) Files.deleteIfExists(bilgiYolu); else byteAtomikYaz(bilgiYolu, bilgiYedegi);
            if (arsivMetadataYedegi == null) Files.deleteIfExists(arsivMetadataYolu); else byteAtomikYaz(arsivMetadataYolu, arsivMetadataYedegi);
            if (katalogYedegi == null) Files.deleteIfExists(katalogYolu); else byteAtomikYaz(katalogYolu, katalogYedegi);
            if (tasindi && Files.exists(yeniDosya) && !Files.exists(oncekiDosya)) guvenliTasi(yeniDosya, oncekiDosya, false);
            throw new IllegalStateException("Metadata kurtarma geri alındı: " + e.getMessage(), e);
        }
    }

    public TopluMetadataOnarimSonucu metadataOnarTum() throws Exception {
        int toplam=0, guncellenen=0, degismeyen=0, hatali=0;
        List<String> mesajlar=new ArrayList<>();
        for (AlimDurumu d : durumlar()) {
            if (!"KONTROL_GEREKIYOR".equalsIgnoreCase(d.metadataDurumu())) continue;
            toplam++;
            try {
                MetadataOnarimSonucu r=metadataOnar(d.eserId());
                if (r.degisti()) guncellenen++; else degismeyen++;
                mesajlar.add(String.format(Locale.ROOT,"ESER-%05d -> %s | %s | %.0f%%",r.eserId(),r.eserAdi(),r.metadataDurumu(),r.guvenPuani()*100));
            } catch (Exception e) {
                hatali++; mesajlar.add(String.format(Locale.ROOT,"ESER-%05d -> HATA: %s",d.eserId(),e.getMessage()));
            }
        }
        return new TopluMetadataOnarimSonucu(toplam,guncellenen,degismeyen,hatali,mesajlar);
    }

    private Path eserKlasoruBul(Path ana, int eserId) throws Exception {
        if (!Files.isDirectory(ana)) return null;
        String onEk=String.format(Locale.ROOT,"ESER-%05d",eserId).toLowerCase(Locale.ROOT);
        try(Stream<Path>s=Files.list(ana)){
            return s.filter(Files::isDirectory)
                    .filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(onEk))
                    .findFirst().orElse(null);
        }
    }

    private Path arsivKaynakDosyasiBul(int eserId) throws Exception {
        Path klasor=eserKlasoruBul(arsiv,eserId);
        if(klasor==null||!Files.isDirectory(klasor.resolve("kaynak")))return null;
        try(Stream<Path>s=Files.list(klasor.resolve("kaynak"))){
            return s.filter(Files::isRegularFile)
                    .filter(p->!p.getFileName().toString().toLowerCase(Locale.ROOT).contains("metin-yedegi"))
                    .findFirst().orElse(null);
        }
    }

    private Path arsivDosyasiniYenidenAdlandir(Path mevcut, int eserId, EserMetadata m) throws Exception {
        if(mevcut==null||!Files.isRegularFile(mevcut))return mevcut;
        String yeniAd=String.format(Locale.ROOT,"ESER-%05d - %s%s%s",eserId,
                EserMetadata.bilinen(m.yazar)?guvenliDosyaAdi(m.yazar)+" - ":"",
                guvenliDosyaAdi(m.eserAdi),uzanti(mevcut));
        Path hedef=mevcut.resolveSibling(yeniAd);
        if(mevcut.getFileName().toString().equals(yeniAd))return mevcut;
        if(Files.exists(hedef))return mevcut;
        return guvenliTasi(mevcut, hedef, false);
    }

    private Path metadataGecmisiOlustur(int eserId, Path manifest, Path bilgi, Path arsivMetadata,
                                               Path arsivDosyasi, String neden) throws Exception {
        Path ana = eserKlasoruBul(arsiv, eserId);
        if (ana == null) ana = eserKlasoruBul(metinArsivi, eserId);
        if (ana == null) throw new IllegalStateException("Metadata geçmişi için eser klasörü bulunamadı.");
        String zaman = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(java.time.LocalDateTime.now());
        Path hedef = ana.resolve("_metadata-gecmisi").resolve(zaman + "-" + neden);
        Files.createDirectories(hedef);
        if (manifest != null && Files.isRegularFile(manifest)) Files.copy(manifest, hedef.resolve("alim-manifest.json"), StandardCopyOption.REPLACE_EXISTING);
        if (bilgi != null && Files.isRegularFile(bilgi)) Files.copy(bilgi, hedef.resolve("000-eser-bilgisi.txt"), StandardCopyOption.REPLACE_EXISTING);
        if (arsivMetadata != null && Files.isRegularFile(arsivMetadata)) Files.copy(arsivMetadata, hedef.resolve("eser-metadata.json"), StandardCopyOption.REPLACE_EXISTING);
        if (Files.isRegularFile(katalogYolu)) Files.copy(katalogYolu, hedef.resolve("eser-katalogu.xlsx"), StandardCopyOption.REPLACE_EXISTING);
        String islem = "eserId=" + eserId + System.lineSeparator()
                + "neden=" + neden + System.lineSeparator()
                + "zaman=" + OffsetDateTime.now() + System.lineSeparator()
                + "arsivDosyasi=" + (arsivDosyasi == null ? "" : arsivDosyasi.toAbsolutePath()) + System.lineSeparator();
        Files.writeString(hedef.resolve("islem.txt"), islem, StandardCharsets.UTF_8);
        return hedef;
    }

    private static void metadataNodeYaz(ObjectNode n, EserMetadata m){
        n.put("eserAdi",m.eserAdi); n.put("eserTuru",m.eserTuru); n.put("yazar",m.yazar);
        n.put("yayinevi",m.yayinevi); n.put("yayinYili",m.yayinYili); n.put("isbn",m.isbn);
        n.put("orijinalAdi",m.orijinalAdi); n.put("cevirmen",m.cevirmen); n.put("basimBilgisi",m.basimBilgisi);
        n.put("dil",m.dil); n.put("lisans",m.lisans); n.put("kaynakUrl",m.kaynakUrl);
        n.put("indirmeUrl",m.indirmeUrl); n.put("archiveIdentifier",m.archiveIdentifier); n.put("archiveDosyaAdi",m.archiveDosyaAdi);
        n.put("orijinalDosyaAdi",m.orijinalDosyaAdi); n.put("bilgiKaynagi",m.bilgiKaynagi); n.put("kullanilanAiModeli",m.kullanilanAiModeli);
        n.put("guvenPuani",m.guvenPuani); n.put("metadataDurumu",m.metadataDurumu); n.put("kanit",m.kanit);
    }

    private void jsonAtomikYaz(Path hedef, Object deger) throws Exception {
        Path tmp = hedef.resolveSibling("." + hedef.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            json.writeValue(tmp.toFile(), deger);
            guvenliTasi(tmp, hedef, true);
        } finally { Files.deleteIfExists(tmp); }
    }

    private static void metinAtomikYaz(Path hedef, String metin) throws Exception {
        byteAtomikYaz(hedef, metin.getBytes(StandardCharsets.UTF_8));
    }

    private static void byteAtomikYaz(Path hedef, byte[] veri) throws Exception {
        Files.createDirectories(hedef.toAbsolutePath().getParent());
        Path tmp = hedef.resolveSibling("." + hedef.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.write(tmp, veri);
            guvenliTasi(tmp, hedef, true);
        } finally { Files.deleteIfExists(tmp); }
    }

    private static Path guvenliTasi(Path kaynak, Path hedef, boolean uzerineYaz) throws Exception {
        StandardCopyOption[] atomik = uzerineYaz
                ? new StandardCopyOption[]{StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE}
                : new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE};
        StandardCopyOption[] normal = uzerineYaz
                ? new StandardCopyOption[]{StandardCopyOption.REPLACE_EXISTING}
                : new StandardCopyOption[]{};
        try { return Files.move(kaynak, hedef, atomik); }
        catch (AtomicMoveNotSupportedException e) { return Files.move(kaynak, hedef, normal); }
    }

    private static Path yolVeyaBos(String s){try{return s==null||s.isBlank()?null:Path.of(s);}catch(Exception e){return null;}}
    private static String manifestOzet(JsonNode n){
        StringBuilder b=new StringBuilder();
        for(String k:List.of("surum","eserId","eserAdi","yazar","dil","tur","kaynakUrl","arsivDosyasi","kaynakSha256","kaynakBirimSayisi","bolumSayisi","toplamKarakter","metinCikarmaYontemi","ocrKullanildi","metadataDurumu","metadataGuvenPuani","metadataOnarimZamani","metadataKurtarmaZamani","metadataGecmisKlasoru","alimZamani"))
            b.append(k).append(": ").append(n.path(k).asText()).append(System.lineSeparator());
        b.append("metadata: ").append(n.path("metadata").toString()).append(System.lineSeparator());
        return b.toString();
    }

    private KaynakAlimSonucu isle(Path anaKaynak, Path metinYedegi, String kaynakUrl,
                                  ArchiveOrgCozumleyici.Sonuc archive) throws Exception {
        String hash = DosyaArsivService.sha256(anaKaynak);
        TekrarKaydi tekrar = tekrarBul(hash);
        if (tekrar != null) {
            gelenDosyasiniKapat(anaKaynak, true); gelenDosyasiniKapat(metinYedegi, true);
            return KaynakAlimSonucu.tekrar(tekrar.eserId(), tekrar.eserAdi(), tur(anaKaynak), anaKaynak,
                    tekrar.klasor(), "Aynı SHA-256 daha önce alınmış; yeniden işlenmedi.");
        }

        BelgeMetinCikarmaService.CikarmaSonucu c;
        String yedekNotu="";
        try {
            c = BelgeMetinCikarmaService.cikar(anaKaynak);
        } catch (BelgeMetinCikarmaService.OcrGerekliException e) {
            if (metinYedegi != null && Files.isRegularFile(metinYedegi)) {
                BelgeMetinCikarmaService.CikarmaSonucu y = BelgeMetinCikarmaService.cikar(metinYedegi);
                c = new BelgeMetinCikarmaService.CikarmaSonucu(y.tur(), y.baslik(), y.yazar(), y.dil(), y.bolumler(), y.tamMetin(),
                        y.kaynakBirimSayisi(), true, y.yontem()+" | Archive.org OCR/metin yedeği");
                yedekNotu="Ana PDF taranmıştı; tam metin yedek kaynaktan çıkarıldı: "+metinYedegi.getFileName();
            } else if (YerelOcrService.kullanilabilirMi()) {
                c = BelgeMetinCikarmaService.cikarYerelOcr(anaKaynak);
                yedekNotu="Ana PDF taranmıştı; tam metin Tesseract yerel OCR ile çıkarıldı.";
            } else {
                throw new BelgeMetinCikarmaService.OcrGerekliException(e.getMessage()+" Tesseract bulunamadı; tesseract-kurulum.ps1 çalıştırılabilir.");
            }
        }

        MetadataCikarmaService.Sonuc metadataSonucu = metadataService.cikar(anaKaynak, c, archive, kaynakUrl);
        EserMetadata metadata = metadataSonucu.metadata();
        if (!EserMetadata.bilinen(metadata.eserAdi)) metadata.eserAdi = c.baslik();
        if (!EserMetadata.bilinen(metadata.yazar)) metadata.yazar = c.yazar();
        if(!yedekNotu.isBlank()) metadata.kanit = metadata.kanit.isBlank()?yedekNotu:metadata.kanit+" | "+yedekNotu;
        metadata.temizle();

        int id = sonrakiId();
        String klasorAdi = String.format(Locale.ROOT, "ESER-%05d - %s", id, guvenliDosyaAdi(metadata.eserAdi));
        Path arsivGecici = arsiv.resolve(".alim-" + UUID.randomUUID());
        Path metinGecici = metinArsivi.resolve(".alim-" + UUID.randomUUID());
        Path arsivHedef = arsiv.resolve(klasorAdi);
        Path metinHedef = metinArsivi.resolve(klasorAdi);
        Path arsivAnaDosya = null;
        boolean arsivTasindi=false,metinTasindi=false;
        try {
            Files.createDirectories(arsivGecici.resolve("kaynak"));
            Files.createDirectories(arsivGecici.resolve("metadata"));
            Files.createDirectories(arsivGecici.resolve("onizleme"));
            String temel = String.format(Locale.ROOT,"ESER-%05d - %s%s%s",id,
                    EserMetadata.bilinen(metadata.yazar)?guvenliDosyaAdi(metadata.yazar)+" - ":"",
                    guvenliDosyaAdi(metadata.eserAdi), uzanti(anaKaynak));
            arsivAnaDosya = arsivGecici.resolve("kaynak").resolve(temel);
            Files.copy(anaKaynak, arsivAnaDosya, StandardCopyOption.COPY_ATTRIBUTES);
            if(metinYedegi!=null&&Files.isRegularFile(metinYedegi)){
                String ya=String.format(Locale.ROOT,"ESER-%05d - %s - metin-yedegi%s",id,guvenliDosyaAdi(metadata.eserAdi),uzanti(metinYedegi));
                Files.copy(metinYedegi,arsivGecici.resolve("kaynak").resolve(ya),StandardCopyOption.COPY_ATTRIBUTES);
            }
            if(uzanti(anaKaynak).equals(".pdf")){
                PdfOnizlemeService.ilkSayfalariKaydet(anaKaynak,arsivGecici.resolve("onizleme").resolve("ilk-3-sayfa.pdf"),3);
            }else{
                String on=c.tamMetin().length()>30000?c.tamMetin().substring(0,30000):c.tamMetin();
                Files.writeString(arsivGecici.resolve("onizleme").resolve("ilk-3-bolum.txt"),on,StandardCharsets.UTF_8);
            }
            json.writeValue(arsivGecici.resolve("metadata").resolve("eser-metadata.json").toFile(),metadata.map());

            metinDosyalariniYaz(metinGecici,c,id,metadata,hash,kaynakUrl,archive,arsivHedef.resolve("kaynak").resolve(temel));
            klasoruTasi(arsivGecici,arsivHedef); arsivTasindi=true;
            klasoruTasi(metinGecici,metinHedef); metinTasindi=true;
            Path finalArsivDosyasi=arsivHedef.resolve("kaynak").resolve(temel);

            TtsParcalamaService.Sonuc tts=TtsParcalamaService.olustur(metinHedef);
            EserKatalogService.KatalogKaydi kayit=new EserKatalogService.KatalogKaydi(id,metadata,finalArsivDosyasi,metinHedef,
                    uzanti(anaKaynak).replace(".","").toUpperCase(Locale.ROOT),hash,c.kaynakBirimSayisi(),c.tamMetin().length(),c.bolumler().size(),
                    "HAZIR","","","",OffsetDateTime.now().toString());
            katalog.kaydet(kayit);

            int yeniIs=0; String kuyrukNotu="";
            try { yeniIs=new UretimKuyruguService(kuyruk,sesArsivi).senkronizeEt(metinArsivi).yeniIs(); }
            catch(Exception qe){ kuyrukNotu=" | UYARI: kuyruk senkronu başarısız: "+qe.getMessage(); }
            gelenDosyasiniKapat(anaKaynak,false); gelenDosyasiniKapat(metinYedegi,false);
            return new KaynakAlimSonucu(true,false,id,metadata.eserAdi,c.tur(),finalArsivDosyasi,metinHedef,
                    tts.ttsKlasoru(),c.bolumler().size(),tts.parcaSayisi(),c.tamMetin().length(),
                    "Eser Fabrikası tamamlandı: arşiv + metadata + Excel + metin + TTS kuyruğu. Yeni iş="+yeniIs
                            +" | Metadata="+metadata.metadataDurumu+" ("+String.format(Locale.ROOT,"%.0f%%",metadata.guvenPuani*100)+")"+kuyrukNotu);
        } catch(Exception e) {
            silSessiz(arsivGecici); silSessiz(metinGecici);
            if(arsivTasindi)silSessiz(arsivHedef); if(metinTasindi)silSessiz(metinHedef);
            hataKaydet(anaKaynak,e); throw e;
        }
    }

    private void metinDosyalariniYaz(Path gecici, BelgeMetinCikarmaService.CikarmaSonucu c, int id,
                                     EserMetadata metadata, String hash, String kaynakUrl,
                                     ArchiveOrgCozumleyici.Sonuc archive, Path arsivDosyasi) throws Exception {
        Files.createDirectories(gecici.resolve("bolumler"));
        int sira=1;
        for(BelgeMetinCikarmaService.BolumMetni b:c.bolumler()){
            String ad=String.format(Locale.ROOT,"%03d - %s.txt",sira++,guvenliDosyaAdi(b.baslik()));
            Files.writeString(gecici.resolve("bolumler").resolve(ad),b.baslik()+System.lineSeparator()+System.lineSeparator()+b.metin()+System.lineSeparator(),StandardCharsets.UTF_8);
        }
        Files.writeString(gecici.resolve("tam-metin.txt"),c.tamMetin()+System.lineSeparator(),StandardCharsets.UTF_8);
        Map<String,Object> manifest=new LinkedHashMap<>();
        manifest.put("surum",3); manifest.put("eserId",id); manifest.put("eserAdi",metadata.eserAdi); manifest.put("yazar",metadata.yazar);
        manifest.put("dil",metadata.dil); manifest.put("tur",c.tur().name()); manifest.put("kaynakUrl",kaynakUrl==null?"":kaynakUrl);
        manifest.put("indirmeUrl",archive==null?"":archive.indirmeUrl()); manifest.put("lisans",metadata.lisans);
        manifest.put("archiveIdentifier",metadata.archiveIdentifier); manifest.put("archiveDosyaAdi",metadata.archiveDosyaAdi);
        manifest.put("arsivDosyasi",arsivDosyasi.toAbsolutePath().toString()); manifest.put("kaynakSha256",hash);
        manifest.put("kaynakBirimSayisi",c.kaynakBirimSayisi()); manifest.put("bolumSayisi",c.bolumler().size());
        manifest.put("toplamKarakter",c.tamMetin().length()); manifest.put("metinCikarmaYontemi",c.yontem()); manifest.put("ocrKullanildi",c.ocrKullanildi());
        manifest.put("metadataDurumu",metadata.metadataDurumu); manifest.put("metadataGuvenPuani",metadata.guvenPuani); manifest.put("metadata",metadata.map());
        manifest.put("alimZamani",OffsetDateTime.now().toString());
        json.writeValue(gecici.resolve("alim-manifest.json").toFile(),manifest);
        Files.writeString(gecici.resolve("000-eser-bilgisi.txt"),bilgiMetni(manifest),StandardCharsets.UTF_8);
        Files.writeString(gecici.resolve("_hazir.flag"),"hazirlanmaZamani="+OffsetDateTime.now()+System.lineSeparator(),StandardCharsets.UTF_8);
    }

    private TekrarKaydi tekrarBul(String hash) throws Exception {
        if(!Files.isDirectory(metinArsivi))return null;
        try(Stream<Path>s=Files.list(metinArsivi)){
            for(Path d:s.filter(Files::isDirectory).toList()){
                Path m=d.resolve("alim-manifest.json"); if(!Files.isRegularFile(m))continue;
                JsonNode n=json.readTree(m.toFile()); if(hash.equalsIgnoreCase(n.path("kaynakSha256").asText()))
                    return new TekrarKaydi(n.path("eserId").asInt(),n.path("eserAdi").asText(),d);
            }
        }
        return null;
    }

    private int sonrakiId() throws Exception {
        int max=0;
        for(Path ana:List.of(metinArsivi,arsiv)){
            if(!Files.isDirectory(ana))continue;
            try(Stream<Path>s=Files.walk(ana,2)){
                for(Path p:s.toList()){
                    Matcher m=ESER_ID.matcher(p.getFileName().toString()); if(m.matches())max=Math.max(max,Integer.parseInt(m.group(1)));
                }
            }
        }
        return max+1;
    }

    private void gelenDosyasiniKapat(Path p, boolean tekrar) {
        try{
            if(p==null||!Files.isRegularFile(p)||!p.toAbsolutePath().normalize().startsWith(gelen.toAbsolutePath().normalize()))return;
            Path hedefKlasor=gelen.resolve(tekrar?"_tekrarlar":"_islenen"); Files.createDirectories(hedefKlasor);
            Files.move(p,benzersiz(hedefKlasor,p.getFileName().toString()));
        }catch(Exception ignored){}
    }

    private void hataKaydet(Path kaynak, Exception e) {
        try{
            Path h=gelen.resolve("_hatalar"); Files.createDirectories(h);
            String ad=kaynak==null?"genel":kaynak.getFileName().toString().replaceAll("[^a-zA-Z0-9._-]","_");
            Files.writeString(h.resolve(ad+".hata.txt"),OffsetDateTime.now()+System.lineSeparator()
                    +(kaynak==null?"":kaynak.toAbsolutePath())+System.lineSeparator()+e.getClass().getName()+": "+e.getMessage(),StandardCharsets.UTF_8);
        }catch(Exception ignored){}
    }

    private EserMetadata metadataOku(JsonNode n){
        EserMetadata m=new EserMetadata(); if(n==null||n.isMissingNode())return m;
        m.eserAdi=n.path("eserAdi").asText(); m.eserTuru=n.path("eserTuru").asText(); m.yazar=n.path("yazar").asText();
        m.yayinevi=n.path("yayinevi").asText(); m.yayinYili=n.path("yayinYili").asText(); m.isbn=n.path("isbn").asText();
        m.orijinalAdi=n.path("orijinalAdi").asText(); m.cevirmen=n.path("cevirmen").asText(); m.basimBilgisi=n.path("basimBilgisi").asText();
        m.dil=n.path("dil").asText(); m.lisans=n.path("lisans").asText(); m.kaynakUrl=n.path("kaynakUrl").asText();
        m.indirmeUrl=n.path("indirmeUrl").asText(); m.archiveIdentifier=n.path("archiveIdentifier").asText();
        m.archiveDosyaAdi=n.path("archiveDosyaAdi").asText(); m.orijinalDosyaAdi=n.path("orijinalDosyaAdi").asText();
        m.bilgiKaynagi=n.path("bilgiKaynagi").asText(); m.kullanilanAiModeli=n.path("kullanilanAiModeli").asText();
        m.guvenPuani=n.path("guvenPuani").asDouble(); m.metadataDurumu=n.path("metadataDurumu").asText(); m.kanit=n.path("kanit").asText(); m.temizle(); return m;
    }

    private static String ekle(String mevcut, String yeni) {
        if (yeni == null || yeni.isBlank()) return mevcut == null ? "" : mevcut;
        if (mevcut == null || mevcut.isBlank()) return yeni;
        return mevcut.contains(yeni) ? mevcut : mevcut + " | " + yeni;
    }
    private static String ekleKaynak(String mevcut, String yeni) {
        if (yeni == null || yeni.isBlank()) return mevcut == null ? "" : mevcut;
        if (mevcut == null || mevcut.isBlank()) return yeni;
        return mevcut.contains(yeni) ? mevcut : mevcut + " + " + yeni;
    }
    private static String bilgiMetni(Map<String,Object>m){StringBuilder s=new StringBuilder();for(var e:m.entrySet())s.append(e.getKey()).append(": ").append(e.getValue()).append(System.lineSeparator());return s.toString();}
    private static boolean destekli(String e){return List.of(".pdf",".epub",".txt",".md",".html",".htm").contains(e);}
    private static KaynakAlimTuru tur(Path p){String e=uzanti(p);return e.equals(".pdf")?KaynakAlimTuru.PDF:e.equals(".epub")?KaynakAlimTuru.EPUB:e.startsWith(".htm")?KaynakAlimTuru.HTML:KaynakAlimTuru.TXT;}
    private static String uzanti(Path p){if(p==null)return"";String a=p.getFileName().toString();int i=a.lastIndexOf('.');return i<0?"":a.substring(i).toLowerCase(Locale.ROOT);}
    private static String urlDosyaAdi(URI u,String ct){String p=u.getPath();String ad=p==null||p.endsWith("/")?"indirilen":p.substring(p.lastIndexOf('/')+1);if(ad.isBlank())ad="indirilen";if(!ad.contains(".")){if(ct.contains("pdf"))ad+=".pdf";else if(ct.contains("epub"))ad+=".epub";else if(ct.contains("text/plain"))ad+=".txt";else ad+=".html";}return ad.replaceAll("[\\\\/:*?\"<>|]","_");}
    private static String guvenliDosyaAdi(String s){if(s==null)return"";String x=s.replaceAll("[\\\\/:*?\"<>|]"," ").replaceAll("[\\p{Cntrl}]"," ").replaceAll("\\s+"," ").trim();while(x.endsWith(".")||x.endsWith(" "))x=x.substring(0,x.length()-1);return x.length()>120?x.substring(0,120).trim():x;}
    private static String sha256(byte[]b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    private static Path benzersiz(Path dir,String ad){Path p=dir.resolve(ad);int n=2;int i=ad.lastIndexOf('.');String b=i>0?ad.substring(0,i):ad,e=i>0?ad.substring(i):"";while(Files.exists(p))p=dir.resolve(b+" ("+(n++)+")"+e);return p;}
    private static void klasoruTasi(Path a,Path b)throws Exception{try{Files.move(a,b,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(a,b);}}
    private static void silSessiz(Path p){try{if(p!=null&&Files.exists(p))try(Stream<Path>s=Files.walk(p)){for(Path x:s.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(x);}}catch(Exception ignored){}}

    private record TekrarKaydi(int eserId,String eserAdi,Path klasor){}
    public record DoktorSonucu(Path proje,Path gelen,Path arsiv,Path metin,Path ses,Path kuyruk,Path katalog,boolean hazir){}
    public record TopluSonuc(int toplam,int basarili,int tekrar,int hatali,List<String>mesajlar){}
    public record TopluUrlSonucu(int toplam,int basarili,int tekrar,int hatali,List<String>mesajlar){}
    public record UzlastirmaSonucu(int eklendi,int degismedi,int hatali,List<String>mesajlar){}
    public record MetadataOnarimSonucu(int eserId,String eserAdi,String yazar,String yayinevi,String yayinYili,
                                       String isbn,String cevirmen,String metadataDurumu,double guvenPuani,
                                       boolean degisti,Path arsivDosyasi,String mesaj){}
    public record MetadataKurtarmaSonucu(int eserId,String eserAdi,String yazar,String yayinevi,String isbn,
                                         String metadataDurumu,double guvenPuani,Path arsivDosyasi,
                                         Path gecmisKlasoru,String mesaj){}
    public record TopluMetadataOnarimSonucu(int toplam,int guncellenen,int degismeyen,int hatali,List<String>mesajlar){}
    public record AlimDurumu(int eserId,String eserAdi,String tur,int karakter,int bolum,boolean ttsHazir,String metadataDurumu,Path klasor){}
}
