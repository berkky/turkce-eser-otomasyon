import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kalite paneli raporu üretir: JSON, CSV, Markdown ve statik HTML.
 */
public final class SesKalitePanelService {
    private static final ObjectMapper JSON = new ObjectMapper();

    private SesKalitePanelService() {
    }

    public static PanelSonucu uret(Path sesArsivKlasoru,
                                   Path metinArsivKlasoru,
                                   Path panelKlasoru,
                                   Path projeKlasoru) throws Exception {
        Files.createDirectories(panelKlasoru);
        Files.createDirectories(panelKlasoru.resolve("assets"));

        SesOnizlemeKesifService kesif = new SesOnizlemeKesifService(projeKlasoru);
        List<SesOnizlemeKaydi> onizlemeler = kesif.kesfet(sesArsivKlasoru, metinArsivKlasoru);
        List<SesKaliteDegerlendirmesi> degerlendirmeler = degerlendirmeleriYukleVeKoru(panelKlasoru, onizlemeler);
        List<SesSaglayiciKarsilastirma> karsilastirmalar = karsilastirmaOlustur(onizlemeler, degerlendirmeler);

        String enIyiSaglayici = "";
        String enIyiModel = "";
        double enIyiPuan = -1;
        for (SesSaglayiciKarsilastirma k : karsilastirmalar) {
            if (k.onerilen() && k.ortalamaInsanPuani() > enIyiPuan) {
                enIyiPuan = k.ortalamaInsanPuani();
                enIyiSaglayici = k.provider();
                enIyiModel = k.modelId();
            }
        }
            if (enIyiSaglayici.isBlank()) {
            for (SesSaglayiciKarsilastirma k : karsilastirmalar) {
                if (k.gecerliPreviewSayisi() > 0 && k.mockPreviewSayisi() < k.gecerliPreviewSayisi()) {
                    enIyiSaglayici = k.provider();
                    enIyiModel = k.modelId();
                    break;
                }
            }
        }

        List<Integer> uygunEserler = degerlendirmeler.stream()
                .filter(d -> Boolean.TRUE.equals(d.recommendedForFullProduction()))
                .map(SesKaliteDegerlendirmesi::eserId)
                .distinct()
                .sorted()
                .toList();

        List<Integer> yuksekRisk = onizlemeler.stream()
                .filter(SesOnizlemeKaydi::buyukEser)
                .map(SesOnizlemeKaydi::eserId)
                .distinct()
                .sorted()
                .toList();

        SesKalitePanelRaporu rapor = new SesKalitePanelRaporu(
                SesKalitePanelRaporu.simdi(),
                sesArsivKlasoru.toAbsolutePath().toString(),
                metinArsivKlasoru.toAbsolutePath().toString(),
                panelKlasoru.toAbsolutePath().toString(),
                onizlemeler,
                degerlendirmeler,
                karsilastirmalar,
                enIyiSaglayici,
                enIyiModel,
                uygunEserler,
                yuksekRisk
        );

        Path jsonYolu = panelKlasoru.resolve("kalite-panel.json");
        Path csvYolu = panelKlasoru.resolve("kalite-panel.csv");
        Path mdYolu = panelKlasoru.resolve("kalite-panel.md");
        Path htmlYolu = panelKlasoru.resolve("index.html");
        Path degerlendirmeYolu = panelKlasoru.resolve("kalite-degerlendirmeleri.json");
        Path telaffuzYolu = panelKlasoru.resolve("telaffuz-notlari.json");

        Files.writeString(jsonYolu,
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(rapor.jsonOzeti(JSON)),
                StandardCharsets.UTF_8);
        Files.writeString(csvYolu, csvYaz(rapor), StandardCharsets.UTF_8);
        Files.writeString(mdYolu, markdownYaz(rapor), StandardCharsets.UTF_8);
        Files.writeString(degerlendirmeYolu, degerlendirmeSablonuYaz(degerlendirmeler, onizlemeler),
                StandardCharsets.UTF_8);
        telaffuzSablonuKoru(telaffuzYolu);
        Files.writeString(panelKlasoru.resolve("assets/style.css"), cssIcerik(), StandardCharsets.UTF_8);
        Files.writeString(panelKlasoru.resolve("assets/panel.js"), jsIcerik(), StandardCharsets.UTF_8);
        Files.writeString(htmlYolu, htmlYaz(rapor), StandardCharsets.UTF_8);

        return new PanelSonucu(rapor, htmlYolu, jsonYolu, csvYolu, mdYolu, degerlendirmeYolu);
    }

    private static List<SesKaliteDegerlendirmesi> degerlendirmeleriYukleVeKoru(
            Path panelKlasoru,
            List<SesOnizlemeKaydi> onizlemeler) throws IOException {
        Path dosya = panelKlasoru.resolve("kalite-degerlendirmeleri.json");
        Map<String, SesKaliteDegerlendirmesi> mevcut = new LinkedHashMap<>();
        if (Files.isRegularFile(dosya)) {
            JsonNode kok = JSON.readTree(Files.readString(dosya, StandardCharsets.UTF_8));
            for (JsonNode n : kok.path("degerlendirmeler")) {
                SesKaliteDegerlendirmesi d = jsondanDegerlendirme(n);
                String anahtar = anahtar(d.requestHash(), d.audioPath());
                mevcut.put(anahtar, d);
            }
        }

        for (SesOnizlemeKaydi o : onizlemeler) {
            if (SesKaliteOlcutleri.DURUM_BEKLENIYOR.equals(o.status())) {
                continue;
            }
            String anahtar = anahtar(o.requestHash(), o.audioPath());
            mevcut.computeIfAbsent(anahtar, k -> new SesKaliteDegerlendirmesi(
                    o.requestHash(), o.audioPath(), o.eserId(), o.provider(), o.modelId(),
                    null, null, null, null, null, "", null, "", ""
            ));
        }

        return mevcut.values().stream()
                .sorted(Comparator.comparingInt(SesKaliteDegerlendirmesi::eserId)
                        .thenComparing(SesKaliteDegerlendirmesi::provider))
                .toList();
    }

    private static String degerlendirmeSablonuYaz(List<SesKaliteDegerlendirmesi> liste,
                                                    List<SesOnizlemeKaydi> onizlemeler) throws Exception {
        ObjectNode kok = JSON.createObjectNode();
        kok.put("aciklama", "Bu dosyayı düzenleyerek insan puanlarını girebilirsiniz. Panel yeniden üretildiğinde mevcut puanlar korunur.");
        kok.put("guncelleme", SesKalitePanelRaporu.simdi());
        ArrayNode dizi = kok.putArray("degerlendirmeler");
        for (SesKaliteDegerlendirmesi d : liste) {
            dizi.add(d.jsonOzeti(JSON));
        }
        ArrayNode bekleyen = kok.putArray("onizlemeBekleyenEserler");
        Set<Integer> onizlemeVar = onizlemeler.stream()
                .filter(o -> !SesKaliteOlcutleri.DURUM_BEKLENIYOR.equals(o.status()))
                .map(SesOnizlemeKaydi::eserId)
                .collect(Collectors.toSet());
        for (SesOnizlemeKaydi o : onizlemeler) {
            if (SesKaliteOlcutleri.DURUM_BEKLENIYOR.equals(o.status())) {
                ObjectNode n = bekleyen.addObject();
                n.put("eserId", o.eserId());
                n.put("eserAdi", o.eserAdi());
                n.put("not", o.errorMessage());
            }
        }
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(kok);
    }

    private static void telaffuzSablonuKoru(Path dosya) throws IOException {
        if (Files.isRegularFile(dosya)) {
            return;
        }
        ObjectNode kok = JSON.createObjectNode();
        kok.put("aciklama", "Telaffuz notları — ileride ElevenLabs pronunciation dictionary veya metin normalizasyonuna aktarılabilir.");
        ArrayNode notlar = kok.putArray("notlar");
        for (String kelime : List.of("Kaşağı", "Alfa Yayınları", "DK", "ISBN", "Ahmet Fethi", "Evliya Çelebi")) {
            ObjectNode n = notlar.addObject();
            n.put("kelime", kelime);
            n.put("telaffuz", "");
            n.put("not", "");
        }
        Files.writeString(dosya, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(kok),
                StandardCharsets.UTF_8);
    }

    private static List<SesSaglayiciKarsilastirma> karsilastirmaOlustur(
            List<SesOnizlemeKaydi> onizlemeler,
            List<SesKaliteDegerlendirmesi> degerlendirmeler) {
        Map<String, List<SesOnizlemeKaydi>> gruplar = new HashMap<>();
        for (SesOnizlemeKaydi o : onizlemeler) {
            if (SesKaliteOlcutleri.DURUM_BEKLENIYOR.equals(o.status())) {
                continue;
            }
            String anahtar = o.provider() + "|" + (o.modelId() == null ? "" : o.modelId());
            gruplar.computeIfAbsent(anahtar, k -> new ArrayList<>()).add(o);
        }

        List<SesSaglayiciKarsilastirma> sonuc = new ArrayList<>();
        for (Map.Entry<String, List<SesOnizlemeKaydi>> giris : gruplar.entrySet()) {
            List<SesOnizlemeKaydi> liste = giris.getValue();
            String provider = liste.getFirst().provider();
            String model = liste.getFirst().modelId();
            int gecerli = 0, mock = 0, gercek = 0, gecersiz = 0;
            for (SesOnizlemeKaydi o : liste) {
                switch (o.status()) {
                    case SesKaliteOlcutleri.DURUM_GECERLI -> { gecerli++; gercek++; }
                    case SesKaliteOlcutleri.DURUM_MOCK -> { gecerli++; mock++; }
                    case SesKaliteOlcutleri.DURUM_GECERSIZ -> gecersiz++;
                    default -> { }
                }
            }
            double ortalama = 0;
            int puanli = 0;
            for (SesKaliteDegerlendirmesi d : degerlendirmeler) {
                if (!provider.equalsIgnoreCase(d.provider())) {
                    continue;
                }
                double p = d.ortalamaPuan();
                if (p > 0) {
                    ortalama += p;
                    puanli++;
                }
            }
            if (puanli > 0) {
                ortalama /= puanli;
            }
            boolean onerilen = gecerli > 0 && gecersiz == 0 && ortalama >= 3.5;
            sonuc.add(new SesSaglayiciKarsilastirma(
                    provider, model, gecerli, mock, gercek, gecersiz, ortalama, puanli, onerilen,
                    mock > 0 ? "Mock önizlemeler test amaçlıdır; gerçek TTS değildir" : ""
            ));
        }
        sonuc.sort(Comparator.comparing(SesSaglayiciKarsilastirma::provider));
        return sonuc;
    }

    private static SesKaliteDegerlendirmesi jsondanDegerlendirme(JsonNode n) {
        return new SesKaliteDegerlendirmesi(
                n.path("requestHash").asText(""),
                n.path("audioPath").asText(""),
                n.path("eserId").asInt(0),
                n.path("provider").asText(""),
                n.path("modelId").asText(""),
                nullableDouble(n, "humanScoreNaturalness"),
                nullableDouble(n, "humanScorePronunciation"),
                nullableDouble(n, "humanScoreEmotion"),
                nullableDouble(n, "humanScorePacing"),
                nullableDouble(n, "humanScoreOverall"),
                n.path("reviewerNote").asText(""),
                n.has("recommendedForFullProduction") && !n.path("recommendedForFullProduction").isNull()
                        ? n.path("recommendedForFullProduction").asBoolean() : null,
                n.path("pronunciationIssues").asText(""),
                n.path("updatedAt").asText("")
        );
    }

    private static Double nullableDouble(JsonNode n, String alan) {
        return n.has(alan) && !n.path(alan).isNull() ? n.path(alan).asDouble() : null;
    }

    private static String anahtar(String hash, String audioPath) {
        if (hash != null && !hash.isBlank()) {
            return "hash:" + hash;
        }
        return "audio:" + (audioPath == null ? "" : audioPath);
    }

    private static String csvYaz(SesKalitePanelRaporu rapor) {
        StringBuilder sb = new StringBuilder();
        sb.append("eserId,eserAdi,provider,modelId,status,mock,characterCount,durationSeconds,fileSizeBytes,")
                .append("estimatedFullWorkCharacterCost,estimatedFullWorkCreditRisk,buyukEser,audioPath,requestHash\n");
        for (SesOnizlemeKaydi o : rapor.onizlemeler()) {
            sb.append(o.eserId()).append(',')
                    .append(csv(o.eserAdi())).append(',')
                    .append(csv(o.provider())).append(',')
                    .append(csv(o.modelId())).append(',')
                    .append(csv(o.status())).append(',')
                    .append(o.mock()).append(',')
                    .append(o.characterCount()).append(',')
                    .append(o.durationSeconds() == null ? "" : o.durationSeconds()).append(',')
                    .append(o.fileSizeBytes()).append(',')
                    .append(o.estimatedFullWorkCharacterCost()).append(',')
                    .append(csv(o.estimatedFullWorkCreditRisk())).append(',')
                    .append(o.buyukEser()).append(',')
                    .append(csv(o.audioPath())).append(',')
                    .append(csv(o.requestHash())).append('\n');
        }
        return sb.toString();
    }

    private static String csv(String deger) {
        if (deger == null) {
            return "";
        }
        String temiz = deger.replace("\"", "\"\"");
        return "\"" + temiz + "\"";
    }

    private static String markdownYaz(SesKalitePanelRaporu rapor) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Ses Kalite Paneli Raporu\n\n");
        sb.append("Üretim: ").append(rapor.uretimZamani()).append("\n\n");
        sb.append("## Özet\n\n");
        sb.append("- En iyi önerilen sağlayıcı: **").append(bosIse(rapor.enIyiOnerilenSaglayici(), "—")).append("**\n");
        sb.append("- En iyi önerilen model: **").append(bosIse(rapor.enIyiOnerilenModel(), "—")).append("**\n");
        sb.append("- Tam üretime uygun eserler: ").append(rapor.tamUretimeUygunEserler()).append("\n");
        sb.append("- Yüksek kredi riski eserler: ").append(rapor.yuksekKrediRiskiEserler()).append("\n\n");

        sb.append("## Sağlayıcı karşılaştırması\n\n");
        sb.append("| Sağlayıcı | Model | Geçerli | Mock | Gerçek | Geçersiz | Ort. puan |\n");
        sb.append("|-----------|-------|---------|------|--------|----------|----------|\n");
        for (SesSaglayiciKarsilastirma k : rapor.karsilastirmalar()) {
            sb.append("| ").append(k.provider()).append(" | ").append(k.modelId())
                    .append(" | ").append(k.gecerliPreviewSayisi())
                    .append(" | ").append(k.mockPreviewSayisi())
                    .append(" | ").append(k.gercekPreviewSayisi())
                    .append(" | ").append(k.gecersizPreviewSayisi())
                    .append(" | ").append(String.format(Locale.ROOT, "%.1f", k.ortalamaInsanPuani()))
                    .append(" |\n");
        }

        sb.append("\n## Önizlemeler\n\n");
        for (SesOnizlemeKaydi o : rapor.onizlemeler()) {
            sb.append("### ESER-").append(String.format("%05d", o.eserId())).append(" — ").append(o.eserAdi()).append("\n");
            sb.append("- Sağlayıcı: ").append(o.provider()).append(" / ").append(o.modelId()).append("\n");
            sb.append("- Durum: **").append(o.status()).append("**");
            if (o.mock()) {
                sb.append(" (MOCK — gerçek TTS değildir)");
            }
            sb.append("\n");
            if (o.buyukEser()) {
                sb.append("- ⚠ Büyük eser — önizleme önerilir; tam üretim için kredi onayı gerekir\n");
            }
            sb.append("- Tahmini tam eser maliyeti: ").append(o.estimatedFullWorkCharacterCost())
                    .append(" karakter (risk: ").append(o.estimatedFullWorkCreditRisk()).append(")\n\n");
        }
        return sb.toString();
    }

    private static String bosIse(String deger, String varsayilan) {
        return deger == null || deger.isBlank() ? varsayilan : deger;
    }

    private static String htmlYaz(SesKalitePanelRaporu rapor) throws Exception {
        String json = JSON.writeValueAsString(rapor.jsonOzeti(JSON));
        return """
                <!DOCTYPE html>
                <html lang="tr">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Ses Kalite Paneli</title>
                  <link rel="stylesheet" href="assets/style.css">
                </head>
                <body>
                  <header>
                    <h1>Ses Önizleme Kalite Paneli</h1>
                    <p class="subtitle">Sağlayıcı / model / voice karşılaştırması — statik panel (sunucu gerekmez)</p>
                  </header>
                  <section class="filters">
                    <label>Eser <select id="filter-eser"><option value="">Tümü</option></select></label>
                    <label>Sağlayıcı <select id="filter-provider"><option value="">Tümü</option></select></label>
                    <label>Model <select id="filter-model"><option value="">Tümü</option></select></label>
                    <label>Mock <select id="filter-mock"><option value="">Tümü</option><option value="true">Mock</option><option value="false">Gerçek</option></select></label>
                  </section>
                  <section id="summary" class="summary"></section>
                  <section id="cards" class="cards"></section>
                  <section id="comparison" class="comparison"></section>
                  <footer>
                    <p>Puanlama: <code>kalite-degerlendirmeleri.json</code> dosyasını düzenleyin ve paneli yeniden üretin.</p>
                    <p>Telaffuz: <code>telaffuz-notlari.json</code></p>
                    <p>Üretim: %s</p>
                  </footer>
                  <script id="panel-data" type="application/json">%s</script>
                  <script src="assets/panel.js"></script>
                </body>
                </html>
                """.formatted(rapor.uretimZamani(), json.replace("</", "<\\/"));
    }

    private static String cssIcerik() {
        return """
                :root { --bg:#0f1419; --card:#1a2332; --text:#e7ecf3; --muted:#9aa8bc; --accent:#4da3ff; --warn:#ffb020; --bad:#ff6b6b; --ok:#3dd68c; }
                * { box-sizing:border-box; }
                body { margin:0; font-family:Segoe UI,system-ui,sans-serif; background:var(--bg); color:var(--text); line-height:1.5; }
                header { padding:1.5rem 2rem; border-bottom:1px solid #2a3548; }
                h1 { margin:0 0 .25rem; font-size:1.6rem; }
                .subtitle { color:var(--muted); margin:0; }
                .filters { display:flex; flex-wrap:wrap; gap:1rem; padding:1rem 2rem; background:#121a24; }
                .filters label { display:flex; flex-direction:column; font-size:.85rem; color:var(--muted); }
                select { margin-top:.25rem; padding:.4rem; background:var(--card); color:var(--text); border:1px solid #334; border-radius:4px; }
                .summary { padding:1rem 2rem; display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:1rem; }
                .stat { background:var(--card); padding:1rem; border-radius:8px; }
                .stat strong { display:block; font-size:1.4rem; color:var(--accent); }
                .cards { padding:0 2rem 2rem; display:grid; gap:1rem; }
                .card { background:var(--card); border-radius:10px; padding:1.25rem; border-left:4px solid var(--accent); }
                .card.mock { border-left-color:var(--warn); }
                .card.invalid { border-left-color:var(--bad); }
                .card.pending { border-left-color:var(--muted); }
                .card h3 { margin:0 0 .5rem; }
                .badge { display:inline-block; padding:.15rem .5rem; border-radius:4px; font-size:.75rem; background:#2a3548; margin-right:.35rem; }
                .badge.mock { background:#4a3a1a; color:var(--warn); }
                .badge.big { background:#3a1a1a; color:var(--warn); }
                audio { width:100%; margin:.75rem 0; }
                .preview-text { background:#0d1218; padding:.75rem; border-radius:6px; font-size:.9rem; max-height:120px; overflow:auto; white-space:pre-wrap; }
                .comparison { padding:0 2rem 2rem; }
                table { width:100%; border-collapse:collapse; background:var(--card); border-radius:8px; overflow:hidden; }
                th, td { padding:.6rem .8rem; text-align:left; border-bottom:1px solid #2a3548; }
                th { background:#121a24; color:var(--muted); font-size:.85rem; }
                footer { padding:1.5rem 2rem; color:var(--muted); font-size:.85rem; border-top:1px solid #2a3548; }
                code { background:#0d1218; padding:.1rem .35rem; border-radius:3px; }
                """;
    }

    private static String jsIcerik() {
        return """
                (function(){
                  const data = JSON.parse(document.getElementById('panel-data').textContent);
                  const onizlemeler = data.onizlemeler || [];
                  const karsilastirmalar = data.karsilastirmalar || [];
                  const degerlendirmeler = data.degerlendirmeler || [];
                  const degerMap = {};
                  degerlendirmeler.forEach(d => { degerMap[d.requestHash || d.audioPath] = d; });

                  function uniq(arr, fn){ return [...new Set(arr.map(fn).filter(v => v !== undefined && v !== null && v !== ''))].sort(); }
                  const eserSel = document.getElementById('filter-eser');
                  const provSel = document.getElementById('filter-provider');
                  const modelSel = document.getElementById('filter-model');
                  const mockSel = document.getElementById('filter-mock');

                  uniq(onizlemeler, o => o.eserId).forEach(id => {
                    const o = onizlemeler.find(x => x.eserId === id);
                    const opt = document.createElement('option');
                    opt.value = id;
                    opt.textContent = 'ESER-' + String(id).padStart(5,'0') + ' — ' + (o.eserAdi || '');
                    eserSel.appendChild(opt);
                  });
                  uniq(onizlemeler, o => o.provider).forEach(p => {
                    const opt = document.createElement('option'); opt.value = p; opt.textContent = p; provSel.appendChild(opt);
                  });
                  uniq(onizlemeler, o => o.modelId).forEach(m => {
                    const opt = document.createElement('option'); opt.value = m; opt.textContent = m; modelSel.appendChild(opt);
                  });

                  function fileUrl(path){
                    if (!path) return '';
                    return 'file:///' + path.replace(/\\\\/g, '/');
                  }

                  function render(){
                    const fe = eserSel.value;
                    const fp = provSel.value;
                    const fm = modelSel.value;
                    const mockFilter = mockSel.value;
                    const list = onizlemeler.filter(o => {
                      if (fe && String(o.eserId) !== fe) return false;
                      if (fp && o.provider !== fp) return false;
                      if (fm && o.modelId !== fm) return false;
                      if (mockFilter === 'true' && !o.mock) return false;
                      if (mockFilter === 'false' && o.mock) return false;
                      return true;
                    });

                    document.getElementById('summary').innerHTML = `
                      <div class="stat"><span>Önizleme</span><strong>${list.length}</strong></div>
                      <div class="stat"><span>En iyi sağlayıcı</span><strong>${data.enIyiOnerilenSaglayici || '—'}</strong></div>
                      <div class="stat"><span>Yüksek risk</span><strong>${(data.yuksekKrediRiskiEserler||[]).map(i=>'ESER-'+String(i).padStart(5,'0')).join(', ') || '—'}</strong></div>
                    `;

                    document.getElementById('cards').innerHTML = list.map(o => {
                      const cls = o.status === 'BEKLENIYOR' ? 'pending' : o.status === 'GECERSIZ' ? 'invalid' : o.mock ? 'mock' : '';
                      const d = degerMap[o.requestHash] || degerMap[o.audioPath] || {};
                      const audio = o.audioPath ? `<audio controls src="${fileUrl(o.audioPath)}"></audio>` : '<p><em>Ses dosyası yok</em></p>';
                      const warn = o.mock ? '<span class="badge mock">MOCK — gerçek TTS değildir</span>' : '';
                      const big = o.buyukEser ? '<span class="badge big">Büyük eser — önizleme önerilir</span>' : '';
                      const esc = s => String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;');
                      return `<article class="card ${cls}">
                        <h3>ESER-${String(o.eserId).padStart(5,'0')} — ${esc(o.eserAdi)}</h3>
                        <div>${warn}${big}<span class="badge">${esc(o.provider)}</span><span class="badge">${esc(o.modelId||'—')}</span><span class="badge">${esc(o.status)}</span></div>
                        <p>Karakter: ${o.characterCount} | Süre: ${o.durationSeconds != null ? o.durationSeconds.toFixed(1)+' sn' : 'bilinmiyor'} | Boyut: ${o.fileSizeBytes} B</p>
                        <p>Tahmini tam eser: ${o.estimatedFullWorkCharacterCost} karakter — risk: ${esc(o.estimatedFullWorkCreditRisk)}</p>
                        ${audio}
                        <div class="preview-text">${esc(o.previewMetin)}</div>
                        <p><small>İnsan puanı (genel): ${d.humanScoreOverall ?? '—'} | Tam üretim: ${d.recommendedForFullProduction ?? '—'}</small></p>
                        <p><small>Telaffuz sorunları: ${esc(d.pronunciationIssues) || '—'}</small></p>
                        <p><small>Not: ${esc(d.reviewerNote || o.errorMessage) || '—'}</small></p>
                      </article>`;
                    }).join('');

                    document.getElementById('comparison').innerHTML = `<h2>Sağlayıcı karşılaştırması</h2><table>
                      <thead><tr><th>Sağlayıcı</th><th>Model</th><th>Geçerli</th><th>Mock</th><th>Gerçek</th><th>Geçersiz</th><th>Ort. puan</th></tr></thead>
                      <tbody>${karsilastirmalar.map(k => `<tr><td>${k.provider}</td><td>${k.modelId}</td><td>${k.gecerliPreviewSayisi}</td><td>${k.mockPreviewSayisi}</td><td>${k.gercekPreviewSayisi}</td><td>${k.gecersizPreviewSayisi}</td><td>${Number(k.ortalamaInsanPuani).toFixed(1)}</td></tr>`).join('')}</tbody></table>`;
                  }

                  [eserSel, provSel, modelSel, mockSel].forEach(el => el.addEventListener('change', render));
                  render();
                })();
                """;
    }

    public record PanelSonucu(SesKalitePanelRaporu rapor,
                              Path html,
                              Path json,
                              Path csv,
                              Path md,
                              Path degerlendirmeler) {
    }
}
