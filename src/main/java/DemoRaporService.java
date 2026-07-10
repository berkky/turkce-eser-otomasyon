import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Patron sunum paketi üretimi — salt yazma, arşive dokunmaz.
 */
public final class DemoRaporService {
    public static final String PAKET_KLASOR_ADI = "turkce-eser-demo-paketi";

    private final WebOrtam ortam;
    private final DemoAkisService akis;
    private final DemoMetrikService metrik;
    private final ObjectMapper json = new ObjectMapper();

    public DemoRaporService(WebOrtam ortam) {
        this.ortam = ortam;
        this.akis = new DemoAkisService(ortam);
        this.metrik = new DemoMetrikService(ortam);
    }

    public Path paketKlasoru() {
        String env = System.getenv("DEMO_PAKET_KLASORU");
        if (env == null || env.isBlank()) {
            env = System.getProperty("DEMO_PAKET_KLASORU");
        }
        if (env != null && !env.isBlank()) {
            return Path.of(env.trim()).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), "Desktop", PAKET_KLASOR_ADI);
    }

    public PaketDurumu durum() throws Exception {
        Path klasor = paketKlasoru();
        boolean var = Files.isDirectory(klasor);
        String tarih = "—";
        List<String> dosyalar = List.of();
        if (var) {
            dosyalar = beklenenDosyalar().stream()
                    .filter(d -> Files.isRegularFile(klasor.resolve(d)))
                    .toList();
            if (!dosyalar.isEmpty()) {
                tarih = Files.getLastModifiedTime(klasor.resolve(dosyalar.get(0))).toString();
            }
        }
        return new PaketDurumu(var, klasor, tarih, dosyalar, beklenenDosyalar());
    }

    public PaketSonucu uret() throws Exception {
        Path klasor = paketKlasoru();
        Files.createDirectories(klasor);
        DemoMetrikService.DemoMetrikler m = metrik.hesapla();
        List<DemoAdimi> adimlar = akis.adimlar();
        var onceSonra = DemoDegerOnerisiService.onceSonra();
        var yapildi = DemoDegerOnerisiService.yapildiKaldi();

        Files.writeString(klasor.resolve("metrikler.json"), metrik.json(), StandardCharsets.UTF_8);
        Files.writeString(klasor.resolve("patron-demo-ozeti.md"), patronMd(m, adimlar, onceSonra, yapildi), StandardCharsets.UTF_8);
        Files.writeString(klasor.resolve("patron-demo-ozeti.html"), patronHtml(m, adimlar, onceSonra, yapildi), StandardCharsets.UTF_8);
        Files.writeString(klasor.resolve("demo-akisi.md"), demoAkisiMd(adimlar), StandardCharsets.UTF_8);
        Files.writeString(klasor.resolve("teknik-ozet.md"), teknikOzetMd(m), StandardCharsets.UTF_8);
        Files.writeString(klasor.resolve("sonraki-adimlar.md"), sonrakiAdimlarMd(), StandardCharsets.UTF_8);
        Files.writeString(klasor.resolve("github-ve-kurulum.md"), githubKurulumMd(), StandardCharsets.UTF_8);

        return new PaketSonucu(klasor, beklenenDosyalar());
    }

    public static List<String> beklenenDosyalar() {
        return List.of(
                "patron-demo-ozeti.html", "patron-demo-ozeti.md", "teknik-ozet.md",
                "demo-akisi.md", "sonraki-adimlar.md", "github-ve-kurulum.md", "metrikler.json"
        );
    }

    private String patronMd(DemoMetrikService.DemoMetrikler m, List<DemoAdimi> adimlar,
                            DemoDegerOnerisiService.OnceSonra os,
                            DemoDegerOnerisiService.YapildiKaldi yk) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Türkçe Eser Otomasyonu — Patron Demo Özeti\n\n");
        sb.append("> ").append(DemoDegerOnerisiService.DEGER_ONERISI).append("\n\n");
        sb.append("**Oluşturma:** ").append(OffsetDateTime.now()).append("\n\n");
        sb.append("## Canlı Metrikler\n\n");
        sb.append("- Toplam eser: ").append(m.toplamEser()).append("\n");
        sb.append("- Örnek eser (demo): ").append(m.ornekEser()).append("\n");
        sb.append("- Toplam karakter: ").append(String.format("%,d", m.toplamKarakter())).append("\n");
        sb.append("- TTS parça: ").append(m.toplamTtsParca()).append("\n");
        sb.append("- Git commit: `").append(m.gitCommit()).append("`\n\n");
        sb.append("\n## Adım 28 — ElevenLabs Önizleme ve Telaffuz\n\n");
        var el = ElevenLabsFabrika.durumOzeti();
        sb.append("- ElevenLabs TTS: ").append(el.ttsDurumu()).append(" — ").append(el.mesaj()).append("\n");
        sb.append("- Önizleme modu: KISA_ONIZLEME (yalnızca ESER-00005, açık onay)\n");
        sb.append("- Telaffuz sözlüğü: yerel JSON + METIN_NORMALIZE\n");
        sb.append("- Tam eser üretimi: KAPALI (web panelinden başlatılamaz)\n");
        sb.append("- Büyük eser (ESER-00006): maliyet onayı şart\n\n");
        sb.append("## Adım 30 — Gerçek Forced Alignment API Kapısı\n\n");
        sb.append("- Gerçek API: yalnızca -GercekApiOnayli + preview MP3 + preview-input.txt\n");
        sb.append("- Web panelden gerçek API çağrısı yapılmaz\n");
        sb.append("- Mock / DemoFixture davranışı korunur\n\n");
        sb.append("## Adım 29 — Forced Alignment ve Okuma Takibi\n\n");
        sb.append("- Alignment klasörü: ses-arsivi/_alignment/\n");
        sb.append("- Çıktılar: alignment.json, .srt, .vtt, summary.json\n");
        sb.append("- Mock alignment: patron demo için kitabı dinlerken metin takibi\n");
        sb.append("- DemoFixture: gercek onizleme yoksa mock altyazi (ses-arsivi/_alignment/_fixture/)\n");
        sb.append("- Gercek alignment icin once gercek ElevenLabs preview MP3 gerekir\n");
        sb.append("- Gerçek API: yalnızca -GercekApiOnayli ve kredi ile (varsayılan kapalı)\n\n");
        sb.append("## Demo Akışı (7 adım)\n\n");
        for (DemoAdimi a : adimlar) {
            sb.append(a.sira()).append(". **").append(a.baslik()).append("** [").append(a.durum()).append("]\n");
            sb.append("   ").append(a.aciklama()).append("\n");
        }
        sb.append("\n## Önce / Sonra\n\n");
        sb.append("**Önce:** ").append(String.join("; ", os.once())).append("\n\n");
        sb.append("**Sonra:** ").append(String.join("; ", os.sonra())).append("\n\n");
        sb.append("## Güvenlik\n\n");
        DemoGuvenlikService.uyarilar().forEach(u -> sb.append("- ").append(u).append("\n"));
        sb.append("\nGitHub: ").append(DemoGuvenlikService.GITHUB_URL).append("\n");
        return sb.toString();
    }

    private String patronHtml(DemoMetrikService.DemoMetrikler m, List<DemoAdimi> adimlar,
                              DemoDegerOnerisiService.OnceSonra os,
                              DemoDegerOnerisiService.YapildiKaldi yk) {
        StringBuilder timeline = new StringBuilder();
        for (DemoAdimi a : adimlar) {
            timeline.append("<div class=\"tl-item\"><span class=\"badge\">").append(a.durum())
                    .append("</span><h3>").append(a.sira()).append(". ")
                    .append(WebGuvenlikService.htmlKacis(a.baslik())).append("</h3><p>")
                    .append(WebGuvenlikService.htmlKacis(a.aciklama())).append("</p></div>");
        }
        return """
                <!DOCTYPE html><html lang="tr"><head><meta charset="UTF-8">
                <title>Patron Demo Özeti</title>
                <style>body{font-family:Segoe UI,sans-serif;max-width:900px;margin:2rem auto;padding:0 1rem;background:#0f1419;color:#e7ecf3}
                h1{color:#4da3ff}.badge{background:#2a3548;padding:.2rem .5rem;border-radius:4px;font-size:.75rem}
                .tl-item{border-left:3px solid #4da3ff;padding-left:1rem;margin:1rem 0}
                .alert{background:#2a2418;border:1px solid #ffb020;padding:1rem;border-radius:8px;margin:1rem 0}
                a{color:#4da3ff}</style></head><body>
                <h1>Türkçe Eser Otomasyonu</h1>
                <p>%s</p>
                <div class="alert">%s</div>
                <h2>Metrikler</h2>
                <p>Toplam eser: %d · Örnek: %d · Karakter: %,d · Git: %s</p>
                <h2>Demo Akışı</h2>%s
                <h2>GitHub</h2><p><a href="%s">%s</a></p>
                </body></html>
                """.formatted(
                WebGuvenlikService.htmlKacis(DemoDegerOnerisiService.DEGER_ONERISI),
                WebGuvenlikService.htmlKacis(DemoGuvenlikService.simulasyonNotu()),
                m.toplamEser(), m.ornekEser(), m.toplamKarakter(), WebGuvenlikService.htmlKacis(m.gitCommit()),
                timeline, DemoGuvenlikService.GITHUB_URL, DemoGuvenlikService.GITHUB_URL);
    }

    private String demoAkisiMd(List<DemoAdimi> adimlar) {
        StringBuilder sb = new StringBuilder("# Demo Akışı\n\n");
        for (DemoAdimi a : adimlar) {
            sb.append("## ").append(a.sira()).append(". ").append(a.baslik())
                    .append(" (`").append(a.durum()).append("`)\n\n");
            sb.append(a.aciklama()).append("\n\n");
            sb.append("- Kanıt: ").append(a.kanit()).append("\n");
            sb.append("- Link: ").append(a.link()).append("\n\n");
        }
        return sb.toString();
    }

    private String teknikOzetMd(DemoMetrikService.DemoMetrikler m) {
        return """
                # Teknik Özet
                
                - Java 21 + Maven
                - HttpServer tabanlı yerel web panel (port 8787)
                - Piper, Google Chirp, ElevenLabs TTS altyapısı
                - Metadata güvenlik/kurtarma sistemi
                - Ses kalite paneli ve idempotent önizleme
                
                ## Metrikler
                
                - Toplam eser: %d
                - Örnek eser: %d
                - Karakter: %,d
                - TTS parça: %d
                - Metadata HAZIR: %d
                - KONTROL_GEREKIYOR: %d
                - Büyük eser: %d
                - Önizleme: %d
                """.formatted(m.toplamEser(), m.ornekEser(), m.toplamKarakter(), m.toplamTtsParca(),
                m.metadataHazir(), m.kontrolGerek(), m.buyukEser(), m.onizleme());
    }

    private String sonrakiAdimlarMd() {
        StringBuilder sb = new StringBuilder("# Sonraki Adımlar\n\n");
        DemoDegerOnerisiService.yapildiKaldi().kaldi().forEach(k -> sb.append("- ").append(k).append("\n"));
        sb.append("\n## Riskler\n\n");
        DemoDegerOnerisiService.risklerVeSonraki().forEach(r -> sb.append("- ").append(r).append("\n"));
        return sb.toString();
    }

    private String githubKurulumMd() {
        return """
                # GitHub ve Kurulum
                
                - Repo: %s
                - Derleme: `mvn -q -DskipTests compile`
                - Web panel: `powershell -ExecutionPolicy Bypass -File .\\web-panel.ps1`
                - Demo sayfası: http://127.0.0.1:8787/demo
                - Self-test: `powershell -ExecutionPolicy Bypass -File .\\adim29-self-test.ps1`
                - Demo paketi: `powershell -ExecutionPolicy Bypass -File .\\patron-demo-paketi.ps1`
                
                API anahtarları ortam değişkenlerinden okunur; hiçbir ekranda gösterilmez.
                """.formatted(DemoGuvenlikService.GITHUB_URL);
    }

    public record PaketDurumu(boolean mevcut, Path klasor, String sonOlusturma, List<String> dosyalar, List<String> beklenen) {
    }

    public record PaketSonucu(Path klasor, List<String> dosyalar) {
    }
}
