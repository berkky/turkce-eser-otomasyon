import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Statik HTML şablonları.
 */
public final class WebTemplateService {
    private WebTemplateService() {
    }

    public static String layout(String baslik, String aktif, String govde) {
        return """
                <!DOCTYPE html>
                <html lang="tr">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s — Eser Otomasyon</title>
                  <link rel="stylesheet" href="/assets/app.css">
                </head>
                <body>
                  <header>
                    <h1>Türkçe Eser Otomasyonu</h1>
                    <p style="color:#9aa8bc;margin:.25rem 0 0">Yerel Web MVP — localhost only</p>
                    <nav>
                      %s
                    </nav>
                  </header>
                  <main>
                    %s
                  </main>
                  <footer>Eser Otomasyon · Adım 29 · API anahtarları asla gösterilmez</footer>
                  <script src="/assets/app.js"></script>
                </body>
                </html>
                """.formatted(WebGuvenlikService.htmlKacis(baslik), nav(aktif), govde);
    }

    private static String nav(String aktif) {
        String[][] linkler = {
                {"/", "ana", "Ana Sayfa"},
                {"/demo", "demo", "Patron Demo"},
                {"/eserler", "eserler", "Eserler"},
                {"/kalite", "kalite", "Ses Kalite"},
                {"/sistem", "sistem", "Sistem"},
                {"/kuyruk", "kuyruk", "Kuyruk"},
                {"/islemler", "islemler", "Güvenli İşlemler"},
                {"/docs", "docs", "Dokümantasyon"},
                {"/telaffuz", "telaffuz", "Telaffuz"},
                {"/alignment", "alignment", "Alignment"}
        };
        StringBuilder sb = new StringBuilder();
        for (String[] l : linkler) {
            String cls = l[1].equals(aktif) ? " class=\"active\"" : "";
            sb.append("<a href=\"").append(l[0]).append("\"").append(cls).append(">")
                    .append(l[2]).append("</a>");
        }
        return sb.toString();
    }

    public static String dashboard(DashboardVeri v) {
        String govde = """
                <h2>Kontrol Paneli</h2>
                <p>Son güncelleme: %s</p>
                <div class="cards">
                  <div class="card"><span>Toplam eser</span><strong>%d</strong></div>
                  <div class="card"><span>Metadata HAZIR</span><strong>%d</strong></div>
                  <div class="card warn"><span>KONTROL_GEREKIYOR</span><strong>%d</strong></div>
                  <div class="card"><span>TTS hazır / tamam</span><strong>%d / %d</strong></div>
                  <div class="card"><span>Önizleme</span><strong>%d</strong></div>
                  <div class="card warn"><span>Büyük eser</span><strong>%d</strong></div>
                  <div class="card"><span>ElevenLabs</span><strong>%s</strong></div>
                  <div class="card"><span>Piper</span><strong>%s</strong></div>
                  <div class="card"><span>FFmpeg</span><strong>%s</strong></div>
                  <div class="card"><span>Excel katalog</span><strong>%s</strong></div>
                </div>
                <div class="alert">Tam eser ses üretimi bu panelden başlatılamaz. Yalnızca önizleme ve izleme.</div>
                <p>
                  <a class="btn" href="/demo">Patron Demo</a>
                  <a class="btn secondary" href="/eserler">Eserleri görüntüle</a>
                  <a class="btn secondary" href="/kalite">Kalite paneli</a>
                  <a class="btn secondary" href="/sistem">Sistem durumu</a>
                </p>
                """.formatted(v.guncelleme(), v.toplamEser(), v.metadataHazir(), v.kontrolGerek(),
                v.ttsHazir(), v.ttsTamam(), v.onizleme(), v.buyukEser(),
                WebGuvenlikService.htmlKacis(v.elevenLabs()), WebGuvenlikService.htmlKacis(v.piper()),
                v.ffmpeg() ? "HAZIR" : "KAPALI", v.excel() ? "VAR" : "YOK");
        return layout("Kontrol Paneli", "ana", govde);
    }

    public static String eserler(List<WebEserService.WebEserOzeti> eserler) {
        StringBuilder satirlar = new StringBuilder();
        for (WebEserService.WebEserOzeti e : eserler) {
            String buyukBadge = e.buyukEser() ? "<span class=\"badge warn\">Büyük</span>" : "";
            String onizBadge = e.onizlemeVar() ? "<span class=\"badge ok\">Önizleme</span>" : "";
            satirlar.append("<tr data-eser-row data-ad=\"").append(WebGuvenlikService.htmlKacis(e.eserAdi()))
                    .append("\" data-durum=\"").append(WebGuvenlikService.htmlKacis(e.metadataDurumu()))
                    .append("\" data-buyuk=\"").append(e.buyukEser())
                    .append("\" data-onizleme=\"").append(e.onizlemeVar()).append("\">")
                    .append("<td>ESER-").append(String.format("%05d", e.eserId())).append("</td>")
                    .append("<td>").append(WebGuvenlikService.htmlKacis(e.eserAdi())).append(" ").append(buyukBadge).append("</td>")
                    .append("<td>").append(WebGuvenlikService.htmlKacis(e.yazar())).append("</td>")
                    .append("<td>").append(WebGuvenlikService.htmlKacis(e.metadataDurumu())).append("</td>")
                    .append("<td>").append(String.format(Locale.ROOT, "%.0f%%", e.guvenPuani() * 100)).append("</td>")
                    .append("<td>").append(e.ttsParca()).append("</td>")
                    .append("<td>").append(onizBadge).append("</td>")
                    .append("<td><a href=\"/eser/").append(e.eserId()).append("\">Detay</a></td></tr>");
        }
        String govde = """
                <h2>Eserler</h2>
                <div class="filters">
                  <input id="eser-ara" placeholder="Başlıkta ara…">
                  <select id="durum-filtre"><option value="">Tüm durumlar</option>
                    <option>HAZIR</option><option>KONTROL_GEREKIYOR</option><option>BILINMIYOR</option></select>
                  <select id="buyuk-filtre"><option value="">Büyük eser</option>
                    <option value="evet">Yalnız büyük</option><option value="hayir">Büyük değil</option></select>
                  <select id="onizleme-filtre"><option value="">Önizleme</option>
                    <option value="evet">Önizlemeli</option><option value="hayir">Önizlemesiz</option></select>
                </div>
                <table><thead><tr>
                  <th>ID</th><th>Eser</th><th>Yazar</th><th>Metadata</th><th>Güven</th><th>TTS parça</th><th>Önizleme</th><th></th>
                </tr></thead><tbody>%s</tbody></table>
                """.formatted(satirlar);
        return layout("Eserler", "eserler", govde);
    }

    public static String eserDetay(WebEserService.WebEserDetay d,
                                   WebKalitePanelService kalite,
                                   TtsMaliyetPlanService.TtsMaliyetPlani plan,
                                   String nonce) {
        WebEserService.WebEserOzeti e = d.ozet();
        StringBuilder oniz = new StringBuilder();
        for (SesOnizlemeKaydi k : d.onizlemeler()) {
            String mediaId = WebKalitePanelService.guvenliId(k);
            String mock = k.mock() ? " <span class=\"badge warn\">MOCK</span>" : "";
            String player = "";
            if (!SesKaliteOlcutleri.DURUM_BEKLENIYOR.equals(k.status())
                    && !SesKaliteOlcutleri.DURUM_GECERSIZ.equals(k.status())
                    && k.audioPath() != null && !k.audioPath().isBlank()) {
                player = "<audio controls src=\"/media/preview/" + WebGuvenlikService.htmlKacis(mediaId) + "\"></audio>";
            }
            oniz.append("<li><strong>").append(WebGuvenlikService.htmlKacis(k.provider())).append("</strong> ")
                    .append(WebGuvenlikService.htmlKacis(k.status())).append(mock)
                    .append(player).append("</li>");
        }
        String buyukUyari = e.buyukEser()
                ? "<div class=\"alert\">Büyük eser — önizleme önerilir. Tam üretim için kredi onayı gerekir. Web panelinden başlatılamaz.</div>"
                : "";
        String pdfLink = d.kaynakPdf() != null
                ? "<a class=\"btn secondary\" href=\"/media/file?tip=pdf&eser=" + e.eserId() + "\">Kaynak PDF</a>" : "";
        String metaLink = d.metadataJson() != null
                ? "<a class=\"btn secondary\" href=\"/media/file?tip=meta&eser=" + e.eserId() + "\">Metadata JSON</a>" : "";
        String demoBolum = demoEserBolumu(e);
        String ttsPlan = ttsPlanBolumu(e, plan, nonce);
        String govde = """
                <h2>ESER-%s — %s</h2>
                %s
                %s
                %s
                <div class="cards">
                  <div class="card"><span>Yazar</span><strong style="font-size:1rem">%s</strong></div>
                  <div class="card"><span>Metadata</span><strong style="font-size:1rem">%s</strong></div>
                  <div class="card"><span>TTS parça</span><strong>%d</strong></div>
                  <div class="card"><span>Karakter</span><strong>%d</strong></div>
                </div>
                <p><strong>ISBN:</strong> %s · <strong>Yayınevi:</strong> %s</p>
                <p><strong>Kanıt:</strong> %s</p>
                <p><strong>Kaynak:</strong> %s</p>
                <p>%s %s <a class="btn secondary" href="/kalite">Kalite paneli</a></p>
                <h3>Önizlemeler</h3><ul>%s</ul>
                <h3>Metin özeti</h3><pre>%s</pre>
                """.formatted(
                String.format("%05d", e.eserId()), WebGuvenlikService.htmlKacis(e.eserAdi()), buyukUyari, demoBolum, ttsPlan,
                WebGuvenlikService.htmlKacis(e.yazar()), WebGuvenlikService.htmlKacis(e.metadataDurumu()),
                e.ttsParca(), e.karakter(), WebGuvenlikService.htmlKacis(e.isbn()),
                WebGuvenlikService.htmlKacis(e.yayinevi()), WebGuvenlikService.htmlKacis(d.kanit()),
                WebGuvenlikService.htmlKacis(d.kaynakDosya()), pdfLink, metaLink,
                oniz, WebGuvenlikService.htmlKacis(d.metinOzet()));
        return layout("Eser " + e.eserId(), "eserler", govde);
    }

    public static String kalite(SesKalitePanelRaporu rapor, Map<String, String> mediaIdleri) {
        StringBuilder kartlar = new StringBuilder();
        for (SesOnizlemeKaydi k : rapor.onizlemeler()) {
            String id = mediaIdleri.get(k.audioPath());
            String mock = k.mock() ? "<span class=\"badge warn\">MOCK — gerçek TTS değildir</span>" : "";
            String audio = id != null
                    ? "<audio controls src=\"/media/preview/" + WebGuvenlikService.htmlKacis(id) + "\"></audio>" : "";
            kartlar.append("<div class=\"card\"><h3>ESER-").append(String.format("%05d", k.eserId()))
                    .append(" — ").append(WebGuvenlikService.htmlKacis(k.eserAdi())).append("</h3>")
                    .append(mock).append("<p>").append(WebGuvenlikService.htmlKacis(k.provider())).append(" / ")
                    .append(WebGuvenlikService.htmlKacis(k.status())).append("</p>")
                    .append(audio).append("<pre style=\"max-height:80px\">")
                    .append(WebGuvenlikService.htmlKacis(k.previewMetin())).append("</pre></div>");
        }
        String govde = "<h2>Ses Kalite Paneli</h2><p>En iyi öneri: "
                + WebGuvenlikService.htmlKacis(rapor.enIyiOnerilenSaglayici()) + " / "
                + WebGuvenlikService.htmlKacis(rapor.enIyiOnerilenModel()) + "</p>"
                + kartlar + "<p><a href=\"/islemler\">Paneli yenile</a></p>";
        return layout("Ses Kalite", "kalite", govde);
    }

    public static String sistem(WebSistemDurumService.SistemDurumu d) {
        String govde = """
                <h2>Sistem Durumu</h2>
                <table>
                  <tr><th>Java</th><td>%s</td></tr>
                  <tr><th>Proje</th><td>%s</td></tr>
                  <tr><th>Arşiv</th><td>%s</td></tr>
                  <tr><th>Metin arşivi</th><td>%s</td></tr>
                  <tr><th>Ses arşivi</th><td>%s</td></tr>
                  <tr><th>Kuyruk</th><td>%s</td></tr>
                  <tr><th>Excel</th><td>%s</td></tr>
                  <tr><th>FFmpeg</th><td>%s</td></tr>
                  <tr><th>Piper</th><td>%s</td></tr>
                  <tr><th>ElevenLabs API</th><td>%s</td></tr>
                  <tr><th>ElevenLabs voice</th><td>%s</td></tr>
                  <tr><th>ElevenLabs TTS</th><td>%s (kalan: %d)</td></tr>
                  <tr><th>OpenAI</th><td>%s</td></tr>
                  <tr><th>Gemini</th><td>%s</td></tr>
                  <tr><th>Tesseract</th><td>%s</td></tr>
                </table>
                """.formatted(d.javaSurumu(), d.projeKlasoru(), d.arsiv(), d.metinArsivi(), d.sesArsivi(),
                d.kuyruk(), d.excelVar() ? "VAR" : "YOK", d.ffmpeg() ? "HAZIR" : "KAPALI",
                d.piper() ? "HAZIR" : "KAPALI", d.elevenLabsApi(), d.elevenLabsVoice(),
                WebGuvenlikService.htmlKacis(d.elevenLabsTts()), d.elevenLabsKalanKredi(),
                d.openAiApi(), d.geminiApi(), d.tesseract() ? "HAZIR" : "KAPALI");
        return layout("Sistem", "sistem", govde);
    }

    public static String kuyruk(List<UretimIsi> isler) {
        StringBuilder satirlar = new StringBuilder();
        for (UretimIsi i : isler) {
            satirlar.append("<tr><td>").append(WebGuvenlikService.htmlKacis(i.id())).append("</td>")
                    .append("<td>").append(WebGuvenlikService.htmlKacis(i.eserAdi())).append("</td>")
                    .append("<td>").append(i.politika()).append("</td>")
                    .append("<td>").append(i.durum()).append("</td>")
                    .append("<td>").append(i.hazirParca()).append("/").append(i.toplamParca()).append("</td>")
                    .append("<td>").append(WebGuvenlikService.htmlKacis(i.aktifSaglayici())).append("</td>")
                    .append("<td>").append(i.toplamKarakter()).append("</td></tr>");
        }
        String govde = """
                <h2>Üretim Kuyruğu</h2>
                <div class="alert">Tam eser üretimi web panelinden başlatılamaz. Yalnızca izleme.</div>
                <table><thead><tr><th>İş ID</th><th>Eser</th><th>Politika</th><th>Durum</th><th>Parça</th><th>Sağlayıcı</th><th>Karakter</th></tr></thead>
                <tbody>%s</tbody></table>
                """.formatted(satirlar);
        return layout("Kuyruk", "kuyruk", govde);
    }

    public static String islemler(String nonce, String mesaj) {
        String alert = mesaj == null || mesaj.isBlank() ? "" : "<div class=\"alert\">" + WebGuvenlikService.htmlKacis(mesaj) + "</div>";
        String govde = alert + """
                <h2>Güvenli İşlemler</h2>
                <p>Yalnızca salt okunur veya idempotent işlemler. Shell komutu çalıştırılmaz.</p>
                <form method="post" action="/islemler" style="margin:.75rem 0">
                  <input type="hidden" name="nonce" value="%s">
                  <input type="hidden" name="aksiyon" value="kalite-yenile">
                  <button class="btn" type="submit">Kalite panelini yeniden oluştur</button>
                </form>
                <form method="post" action="/islemler" style="margin:.75rem 0">
                  <input type="hidden" name="nonce" value="%s">
                  <input type="hidden" name="aksiyon" value="sistem-yenile">
                  <button class="btn secondary" type="submit">Sistem durumunu yenile</button>
                </form>
                <form method="post" action="/islemler" style="margin:.75rem 0">
                  <input type="hidden" name="nonce" value="%s">
                  <input type="hidden" name="aksiyon" value="eser-tara">
                  <button class="btn secondary" type="submit">Eser listesini yeniden tara</button>
                </form>
                <h3>ElevenLabs Önizleme (Adım 28)</h3>
                <div class="alert">Yalnızca ESER-00005 Kaşağı için açık önizleme. Tam eser üretimi başlatılmaz.</div>
                <form method="post" action="/islemler" style="margin:.75rem 0">
                  <input type="hidden" name="nonce" value="%s">
                  <input type="hidden" name="aksiyon" value="elevenlabs-onizleme">
                  <input type="hidden" name="eserId" value="5">
                  <button class="btn" type="submit">Kaşağı ElevenLabs önizlemesi üret</button>
                </form>
                <h3>Mock Alignment (Adım 29)</h3>
                <form method="post" action="/islemler" style="margin:.75rem 0">
                  <input type="hidden" name="nonce" value="%s">
                  <input type="hidden" name="aksiyon" value="alignment-mock">
                  <input type="hidden" name="eserId" value="5">
                  <button class="btn secondary" type="submit">ESER-00005 mock alignment üret</button>
                </form>
                """.formatted(nonce, nonce, nonce, nonce, nonce);
        return layout("Güvenli İşlemler", "islemler", govde);
    }

    public static String docs(List<String> dosyalar) {
        StringBuilder links = new StringBuilder();
        for (String d : dosyalar) {
            links.append("<li><a href=\"/docs/").append(WebGuvenlikService.htmlKacis(d)).append("\">")
                    .append(WebGuvenlikService.htmlKacis(d)).append("</a></li>");
        }
        return layout("Dokümantasyon", "docs", "<h2>Dokümantasyon</h2><ul>" + links + "</ul>");
    }

    public static String docIcerik(String ad, String icerik) {
        return layout(ad, "docs", "<h2>" + WebGuvenlikService.htmlKacis(ad)
                + "</h2><pre>" + WebGuvenlikService.htmlKacis(icerik) + "</pre>");
    }

    public static String telaffuz(List<TelaffuzNotu> notlar, String json) {
        StringBuilder satirlar = new StringBuilder();
        for (TelaffuzNotu n : notlar) {
            String eser = n.eserId() > 0 ? "ESER-" + String.format("%05d", n.eserId()) : "Genel";
            String mod = n.uygulanmaModu();
            String uygulandi = n.aktifMetinNormalize() ? "Evet (METIN_NORMALIZE)" : "Hayır";
            String dictAday = TelaffuzNotu.MOD_DICTIONARY.equalsIgnoreCase(mod)
                    ? "<span class=\"badge warn\">Dictionary adayı</span>" : "";
            satirlar.append("<tr><td>").append(WebGuvenlikService.htmlKacis(n.id())).append("</td>")
                    .append("<td>").append(WebGuvenlikService.htmlKacis(n.ifade())).append("</td>")
                    .append("<td>").append(WebGuvenlikService.htmlKacis(n.onerilenOkunus())).append("</td>")
                    .append("<td>").append(WebGuvenlikService.htmlKacis(n.durum())).append("</td>")
                    .append("<td>").append(WebGuvenlikService.htmlKacis(eser)).append("</td>")
                    .append("<td>").append(WebGuvenlikService.htmlKacis(mod)).append("</td>")
                    .append("<td>").append(uygulandi).append("</td>")
                    .append("<td>").append(dictAday).append("</td></tr>");
        }
        String govde = """
                <h2>Telaffuz Notları</h2>
                <div class="alert">Bu sözlük ileride ElevenLabs pronunciation dictionary veya metin normalizasyonuna aktarılabilir.
                Canlı dictionary API bu adımda kullanılmaz. Örnek notlar gerçek kullanıcı verisi değildir.</div>
                <table><thead><tr>
                  <th>ID</th><th>İfade</th><th>Önerilen okunuş</th><th>Durum</th><th>Eser</th>
                  <th>Uygulama modu</th><th>Önizlemede uygulanır?</th><th>EL Dictionary</th>
                </tr></thead><tbody>%s</tbody></table>
                <h3>JSON</h3><pre>%s</pre>
                """.formatted(satirlar, WebGuvenlikService.htmlKacis(json));
        return layout("Telaffuz", "telaffuz", govde);
    }

    public static String demo(DemoSayfaVeri v) {
        StringBuilder timeline = new StringBuilder("<div class=\"timeline\">");
        for (DemoAdimi a : v.adimlar()) {
            String badgeCls = badgeSinif(a.durum());
            String link = a.link().startsWith("http") ? a.link()
                    : "<a href=\"" + WebGuvenlikService.htmlKacis(a.link()) + "\">Detay</a>";
            timeline.append("<div class=\"tl-step\">")
                    .append("<div class=\"tl-num\">").append(a.sira()).append("</div>")
                    .append("<div class=\"tl-body\"><span class=\"badge ").append(badgeCls).append("\">")
                    .append(WebGuvenlikService.htmlKacis(a.durum())).append("</span>")
                    .append("<h3>").append(WebGuvenlikService.htmlKacis(a.baslik())).append("</h3>")
                    .append("<p>").append(WebGuvenlikService.htmlKacis(a.aciklama())).append("</p>")
                    .append("<p class=\"tl-kanit\"><em>").append(WebGuvenlikService.htmlKacis(a.kanit())).append("</em> · ")
                    .append(link).append("</p></div></div>");
        }
        timeline.append("</div>");

        StringBuilder ornekEserler = new StringBuilder();
        for (DemoSenaryo s : v.senaryolar()) {
            ornekEserler.append("<div class=\"card\"><h3>ESER-").append(String.format("%05d", s.eserId()))
                    .append(" — ").append(WebGuvenlikService.htmlKacis(s.eserAdi())).append("</h3>")
                    .append("<p>").append(WebGuvenlikService.htmlKacis(s.demoRolu())).append("</p>")
                    .append("<p><strong>Kaynak:</strong> ").append(WebGuvenlikService.htmlKacis(s.kaynakTipi()))
                    .append("</p><a class=\"btn secondary\" href=\"/eser/").append(s.eserId()).append("\">Detay</a></div>");
        }

        StringBuilder once = new StringBuilder("<ul>");
        v.onceSonra().once().forEach(o -> once.append("<li>").append(WebGuvenlikService.htmlKacis(o)).append("</li>"));
        once.append("</ul>");
        StringBuilder sonra = new StringBuilder("<ul>");
        v.onceSonra().sonra().forEach(s -> sonra.append("<li>").append(WebGuvenlikService.htmlKacis(s)).append("</li>"));
        sonra.append("</ul>");

        StringBuilder yapildi = new StringBuilder("<ul>");
        v.yapildiKaldi().yapildi().forEach(y -> yapildi.append("<li class=\"ok-li\">").append(WebGuvenlikService.htmlKacis(y)).append("</li>"));
        yapildi.append("</ul>");
        StringBuilder kaldi = new StringBuilder("<ul>");
        v.yapildiKaldi().kaldi().forEach(k -> kaldi.append("<li>").append(WebGuvenlikService.htmlKacis(k)).append("</li>"));
        kaldi.append("</ul>");

        StringBuilder uyarilar = new StringBuilder();
        v.uyarilar().forEach(u -> uyarilar.append("<div class=\"alert\">").append(WebGuvenlikService.htmlKacis(u)).append("</div>"));

        String govde = """
                <div class="demo-hero">
                  <h2>Türkçe Eser Otomasyonu</h2>
                  <p class="demo-tagline">%s</p>
                  <p class="demo-sim">%s</p>
                  <p>
                    <a class="btn" href="#timeline">Demo Akışını Gör</a>
                    <a class="btn secondary" href="/demo/paket">Sunum Paketi</a>
                    <a class="btn secondary" href="%s" target="_blank" rel="noopener">GitHub</a>
                  </p>
                </div>
                <div class="cards">
                  <div class="card"><span>Toplam eser</span><strong>%d</strong></div>
                  <div class="card"><span>Demo örnek</span><strong>%d</strong></div>
                  <div class="card"><span>Karakter</span><strong>%,d</strong></div>
                  <div class="card"><span>TTS parça</span><strong>%d</strong></div>
                  <div class="card"><span>Önizleme</span><strong>%d</strong></div>
                  <div class="card"><span>Git commit</span><strong style="font-size:1rem">%s</strong></div>
                </div>
                <h2 id="timeline">Demo İlerleme Akışı</h2>
                %s
                <h2>Adım 28: Premium Ses Önizleme</h2>
                <div class="demo-eser-box">
                  <p>%s</p>
                  %s
                  <p><a class="btn secondary" href="/kalite">Kalite paneli</a>
                     <a class="btn secondary" href="/telaffuz">Telaffuz notları</a>
                     <a class="btn secondary" href="/eser/5">ESER-00005 plan</a></p>
                  <div class="alert">Tam eser üretimi bu adımda kapalı — yalnızca onaylı kısa önizleme.</div>
                </div>
                <h2>Adım 29: Ses-Metin Hizalama ve Okuma Takibi</h2>
                <div class="demo-eser-box">
                  <p>%s</p>
                  <p><a class="btn secondary" href="/alignment">Alignment paneli</a>
                     <a class="btn secondary" href="/eser/5/alignment">ESER-00005 okuma takibi</a>
                     <a class="btn secondary" href="/kalite">Kalite</a>
                     <a class="btn secondary" href="/telaffuz">Telaffuz</a></p>
                  <div class="alert">Gerçek ElevenLabs forced alignment yalnızca açık onaylı komutla çalışır. Mock demo her zaman kullanılabilir.</div>
                </div>
                <h2>Örnek Eserler</h2>
                <div class="cards">%s</div>
                <h2>Önce / Sonra</h2>
                <div class="before-after"><div><h3>Önce</h3>%s</div><div><h3>Sonra</h3>%s</div></div>
                <h2>Ne Yapıldı / Ne Kaldı</h2>
                <div class="before-after"><div><h3>Yapıldı</h3>%s</div><div><h3>Kaldı</h3>%s</div></div>
                <h2>Hızlı Bağlantılar</h2>
                <p>
                  <a class="btn secondary" href="/eserler">Eserler</a>
                  <a class="btn secondary" href="/eser/5">ESER-00005</a>
                  <a class="btn secondary" href="/eser/6">ESER-00006</a>
                  <a class="btn secondary" href="/kalite">Kalite</a>
                  <a class="btn secondary" href="/sistem">Sistem</a>
                  <a class="btn secondary" href="/docs">Docs</a>
                  <a class="btn secondary" href="/telaffuz">Telaffuz</a>
                </p>
                <h2>Güvenlik ve Riskler</h2>
                %s
                <h2>Sonraki Adımlar</h2>
                <ul>%s</ul>
                """.formatted(
                WebGuvenlikService.htmlKacis(v.degerOnerisi()),
                WebGuvenlikService.htmlKacis(v.simulasyonNotu()),
                DemoGuvenlikService.GITHUB_URL,
                v.metrikler().toplamEser(), v.metrikler().ornekEser(),
                v.metrikler().toplamKarakter(), v.metrikler().toplamTtsParca(),
                v.metrikler().onizleme(), WebGuvenlikService.htmlKacis(v.metrikler().gitCommit()),
                timeline,
                WebGuvenlikService.htmlKacis(v.adim28().krediMesaj()),
                v.adim28().onizlemeVar() ? v.adim28().audioHtml() : "",
                WebGuvenlikService.htmlKacis(v.adim29().mesaj()),
                ornekEserler, once, sonra, yapildi, kaldi, uyarilar,
                v.riskler().stream().map(r -> "<li>" + WebGuvenlikService.htmlKacis(r) + "</li>").reduce("", String::concat));
        return layout("Patron Demo", "demo", govde);
    }

    public static String demoPaket(DemoRaporService.PaketDurumu d) {
        StringBuilder dosyaList = new StringBuilder("<ul>");
        for (String bek : d.beklenen()) {
            boolean var = d.dosyalar().contains(bek);
            String badge = var ? "<span class=\"badge ok\">VAR</span>" : "<span class=\"badge bad\">YOK</span>";
            dosyaList.append("<li>").append(badge).append(" ")
                    .append(WebGuvenlikService.htmlKacis(bek)).append("</li>");
        }
        dosyaList.append("</ul>");
        String govde = """
                <h2>Patron Sunum Paketi</h2>
                <p><strong>Klasör:</strong> %s</p>
                <p><strong>Durum:</strong> %s</p>
                <p><strong>Son güncelleme:</strong> %s</p>
                <h3>Dosyalar</h3>
                %s
                <div class="alert">Paket oluşturmak için proje klasöründe şu komutu çalıştırın:<br>
                <code>powershell -ExecutionPolicy Bypass -File .\\patron-demo-paketi.ps1</code></div>
                <p><a class="btn" href="/demo">Demo sayfasına dön</a></p>
                """.formatted(
                WebGuvenlikService.htmlKacis(WebGuvenlikService.yolMaskele(d.klasor())),
                d.mevcut() ? "Paket klasörü mevcut" : "Henüz oluşturulmadı",
                WebGuvenlikService.htmlKacis(d.sonOlusturma()),
                dosyaList);
        return layout("Demo Paketi", "demo", govde);
    }

    private static String ttsPlanBolumu(WebEserService.WebEserOzeti e,
                                        TtsMaliyetPlanService.TtsMaliyetPlani plan,
                                        String nonce) {
        String onerilen = plan.onerilenAksiyon();
        String aksiyonMetin = switch (onerilen) {
            case "ONIZLEME_URETILEBILIR", "ONIZLEME_URETILEBILIR_TAM_ESER_YETERSIZ" -> "Önizleme üret";
            case "KREDI_BEKLENIYOR" -> "Kredi bekleniyor";
            case "BUYUK_ESER_MALIYET_ONAYI" -> "Büyük eser: maliyet onayı şart";
            case "ONIZLEME_ICIN_YETERSIZ_KREDI" -> "Önizleme için kredi yetersiz";
            default -> plan.aciklama();
        };
        String onizForm = "";
        if (e.eserId() == SesKaliteOlcutleri.KASAGI_ESER_ID && !plan.buyukEser()) {
            onizForm = """
                    <form method="post" action="/islemler" style="margin-top:.5rem">
                      <input type="hidden" name="nonce" value="%s">
                      <input type="hidden" name="aksiyon" value="elevenlabs-onizleme">
                      <input type="hidden" name="eserId" value="5">
                      <button class="btn" type="submit">ElevenLabs önizleme üret</button>
                    </form>
                    """.formatted(WebGuvenlikService.htmlKacis(nonce));
        }
        return """
                <div class="demo-eser-box">
                  <h3>TTS Maliyet Planı (salt okunur)</h3>
                  <p><strong>Toplam karakter:</strong> %,d · <strong>TTS parça:</strong> %d</p>
                  <p><strong>Tahmini önizleme:</strong> %d karakter · <strong>Kalan ElevenLabs kredisi:</strong> %d</p>
                  <p><strong>Tam üretim tahmini:</strong> %d kredi · <strong>Risk:</strong> %s</p>
                  <p><strong>Önerilen aksiyon:</strong> %s</p>
                  <p>%s</p>
                  %s
                  <p><a class="btn secondary" href="/api/tts-plan/%d">JSON plan</a></p>
                </div>
                """.formatted(
                plan.toplamKarakter(), plan.ttsParca(), plan.tahminiOnizlemeKarakteri(),
                plan.kalanElevenLabsKredisi(), plan.tamUretimTahminiKredi(),
                plan.buyukEser() ? "yüksek" : "düşük",
                WebGuvenlikService.htmlKacis(aksiyonMetin),
                WebGuvenlikService.htmlKacis(plan.aciklama()),
                onizForm, e.eserId());
    }

    private static String demoEserBolumu(WebEserService.WebEserOzeti e) {
        if (e.eserId() != SesKaliteOlcutleri.KASAGI_ESER_ID
                && e.eserId() != SesKaliteOlcutleri.ASTRONOMI_ESER_ID) {
            return "";
        }
        DemoSenaryo s = e.eserId() == SesKaliteOlcutleri.KASAGI_ESER_ID
                ? DemoSenaryo.kasagi() : DemoSenaryo.astronomi();
        String uretimPlani = e.buyukEser()
                ? "Tam üretim kapalı — önce önizleme ve maliyet onayı gerekir. Tahmini yüksek kredi riski."
                : "Kısa eser — önizleme ile sağlayıcı seçimi yapılabilir. Tam üretim web panelinden başlatılamaz.";
        return """
                <div class="demo-eser-box">
                  <h3>Demo Eseri</h3>
                  <p><strong>Bu eser demo için neden önemli?</strong> %s</p>
                  <p><strong>Tam üretim koruması:</strong> %s</p>
                  <p><strong>Metadata güvenlik:</strong> %s</p>
                  <p><strong>TTS üretim planı (salt okunur):</strong> %s</p>
                  <a class="btn secondary" href="/demo">Patron demo akışı</a>
                </div>
                """.formatted(
                WebGuvenlikService.htmlKacis(s.onemAciklamasi()),
                e.buyukEser() ? "Büyük eser — otomatik tam üretim engelli" : "Kısa eser — düşük maliyetli test",
                WebGuvenlikService.htmlKacis(e.metadataDurumu()),
                WebGuvenlikService.htmlKacis(uretimPlani));
    }

    public static String alignmentGenel(AlignmentPlan p5, AlignmentPlan p6, AlignmentResult r5, String nonce) {
        String govde = """
                <h2>Ses-Metin Hizalama (Forced Alignment)</h2>
                <div class="alert">Gerçek ElevenLabs forced alignment API yalnızca açık onaylı komutla çalışır. Mock alignment web panelden üretilebilir.</div>
                <h3>ESER-00005 — Kaşağı</h3>
                <p><strong>Durum:</strong> %s · %s</p>
                <p><strong>Segment:</strong> %d · <strong>Kelime:</strong> %d</p>
                <p><a class="btn" href="/eser/5/alignment">Okuma takibi</a>
                   <a class="btn secondary" href="/api/alignment/5">JSON plan</a></p>
                <h3>ESER-00006 — Büyük eser</h3>
                <p><strong>Durum:</strong> %s</p>
                <p>%s</p>
                """.formatted(
                WebGuvenlikService.htmlKacis(p5.status()), WebGuvenlikService.htmlKacis(p5.reason()),
                r5.segmentCount(), r5.wordCount(),
                WebGuvenlikService.htmlKacis(p6.status()), WebGuvenlikService.htmlKacis(p6.reason()));
        return layout("Alignment", "alignment", govde);
    }

    public static String eserAlignment(int eserId, AlignmentPlan plan, AlignmentResult sonuc,
                                     String mediaId, AlignmentService.EserAlignmentDurum durum,
                                     String nonce) {
        StringBuilder segmentler = new StringBuilder("<ul id=\"alignment-segments\" class=\"alignment-list\">");
        if (sonuc.segments() != null && !sonuc.segments().isEmpty()) {
            for (AlignmentSegment s : sonuc.segments()) {
                segmentler.append("<li class=\"align-seg\" data-start=\"")
                        .append(s.startSeconds()).append("\">")
                        .append("<span class=\"badge\">").append(String.format("%.1fs", s.startSeconds()))
                        .append("</span> ")
                        .append(WebGuvenlikService.htmlKacis(s.text()))
                        .append("</li>");
            }
        } else {
            segmentler.append("<li class=\"muted\">Henüz segment yok.</li>");
        }
        segmentler.append("</ul>");

        StringBuilder uyariBlok = new StringBuilder();
        if (durum.demoFixture()) {
            uyariBlok.append("""
                    <div class="alert warn"><strong>Demo fixture alignment</strong> — gerçek ElevenLabs önizlemesi kullanılmadı.
                    Patron demosu için mock altyazı ve okuma takibi gösterilir.</div>
                    """);
        }
        if (!durum.altyaziVar()) {
            uyariBlok.append("""
                    <div class="alert warn">
                      <p><strong>Henüz altyazı yok</strong></p>
                      <p>Önce ElevenLabs önizleme gerekir; gerçek ses-metin hizalama için preview MP3 şarttır.</p>
                      <p>Kredi yoksa mock fixture ile demo doğrulaması yapılabilir:
                         <code>elevenlabs-alignment.ps1 -EserId 5 -Mock -DemoFixture</code></p>
                    </div>
                    """);
        }

        String player = mediaId != null && !mediaId.isBlank()
                ? "<audio id=\"alignment-audio\" controls src=\"/media/preview/"
                + WebGuvenlikService.htmlKacis(mediaId) + "\"></audio>"
                : "<p class=\"muted\">Önizleme sesi yok — demo fixture veya ElevenLabs önizlemesi gerekir.</p>";

        String mockForm;
        if (durum.mockButonAktif()) {
            mockForm = """
                    <form method="post" action="/islemler" style="margin:.75rem 0">
                      <input type="hidden" name="nonce" value="%s">
                      <input type="hidden" name="aksiyon" value="alignment-mock">
                      <input type="hidden" name="eserId" value="5">
                      <button class="btn" type="submit">Mock alignment üret</button>
                    </form>
                    """.formatted(WebGuvenlikService.htmlKacis(nonce));
        } else if (eserId == SesKaliteOlcutleri.KASAGI_ESER_ID) {
            mockForm = "<p class=\"muted\"><strong>Mock alignment üret</strong> — kullanılamıyor: "
                    + WebGuvenlikService.htmlKacis(durum.mockButonNeden()) + "</p>";
        } else {
            mockForm = "";
        }

        String altyaziLink = durum.altyaziVar()
                ? """
                   <a class="btn secondary" href="/api/alignment/%d/subtitles?format=srt">SRT</a>
                   <a class="btn secondary" href="/api/alignment/%d/subtitles?format=vtt">VTT</a>
                   """.formatted(eserId, eserId)
                : "<span class=\"muted\">SRT/VTT — altyazı henüz yok</span>";

        String govde = """
                <h2>ESER-%s — Okuma Takibi</h2>
                <p><strong>Alignment durumu:</strong> %s</p>
                <p>%s</p>
                %s
                %s
                %s
                <div class="cards">
                  <div class="card"><span>Segment</span><strong>%d</strong></div>
                  <div class="card"><span>Kelime</span><strong>%d</strong></div>
                  <div class="card"><span>Süre</span><strong>%.1f sn</strong></div>
                </div>
                <p><a class="btn secondary" href="/api/alignment/%d/segments">Segment JSON</a>
                   %s</p>
                <h3>Segmentler</h3>
                %s
                """.formatted(
                String.format("%05d", eserId),
                WebGuvenlikService.htmlKacis(plan.status()),
                WebGuvenlikService.htmlKacis(plan.reason()),
                uyariBlok,
                player, mockForm,
                sonuc.segmentCount(), sonuc.wordCount(), sonuc.durationSeconds(),
                eserId, altyaziLink, segmentler);
        return layout("Eser " + eserId + " Alignment", "alignment", govde);
    }

    private static String badgeSinif(String durum) {
        if (durum == null) return "";
        return switch (durum) {
            case DemoAdimi.DURUM_TAMAMLANDI -> "ok";
            case DemoAdimi.DURUM_KONTROL -> "warn";
            case DemoAdimi.DURUM_KREDI -> "warn";
            default -> "";
        };
    }

    public record Adim28Bolum(boolean krediVar, boolean onizlemeVar, String krediMesaj, String audioHtml) {
    }

    public record Adim29Bolum(boolean onizlemeVar, boolean alignmentVar, boolean krediVar, String mesaj) {
    }

    public record DemoSayfaVeri(
            String degerOnerisi, String simulasyonNotu,
            DemoMetrikService.DemoMetrikler metrikler,
            List<DemoAdimi> adimlar, List<DemoSenaryo> senaryolar,
            DemoDegerOnerisiService.OnceSonra onceSonra,
            DemoDegerOnerisiService.YapildiKaldi yapildiKaldi,
            List<String> uyarilar, List<String> riskler,
            Adim28Bolum adim28,
            Adim29Bolum adim29
    ) {
    }

    public record DashboardVeri(String guncelleme, int toplamEser, int metadataHazir, int kontrolGerek,
                                int ttsHazir, int ttsTamam, int onizleme, int buyukEser,
                                String elevenLabs, String piper, boolean ffmpeg, boolean excel) {
    }
}
