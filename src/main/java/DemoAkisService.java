import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ESER-00005 ve ESER-00006 üzerinden demo akışı oluşturur.
 */
public final class DemoAkisService {
    private final WebOrtam ortam;
    private final ObjectMapper json = new ObjectMapper();

    public DemoAkisService(WebOrtam ortam) {
        this.ortam = ortam;
    }

    public List<DemoAdimi> adimlar() throws Exception {
        WebEserService eserSvc = new WebEserService(ortam);
        WebEserService.WebEserDetay kasagi = eserSvc.eserDetay(SesKaliteOlcutleri.KASAGI_ESER_ID);
        WebEserService.WebEserDetay astronomi = eserSvc.eserDetay(SesKaliteOlcutleri.ASTRONOMI_ESER_ID);
        var el = ElevenLabsFabrika.durumOzeti();

        String kasagiDurum = kasagi != null ? kasagi.ozet().metadataDurumu() : "BILINMIYOR";
        String astroDurum = astronomi != null ? astronomi.ozet().metadataDurumu() : "BILINMIYOR";
        int kasagiParca = kasagi != null ? kasagi.ozet().ttsParca() : 0;
        int astroParca = astronomi != null ? astronomi.ozet().ttsParca() : 0;
        int kasagiKarakter = kasagi != null ? kasagi.ozet().karakter() : 0;
        int astroKarakter = astronomi != null ? astronomi.ozet().karakter() : 0;
        int kasagiBolum = kasagi != null ? kasagi.ozet().bolum() : 0;
        int astroBolum = astronomi != null ? astronomi.ozet().bolum() : 0;

        List<DemoAdimi> liste = new ArrayList<>();
        liste.add(new DemoAdimi(1, "Kaynak seçildi",
                "Kaşağı: Vikikaynak web sayfası. Astronomi: Archive.org PDF. Sistem doğru kaynağı seçer ve kayıt altına alır.",
                DemoAdimi.DURUM_TAMAMLANDI,
                "ESER-00005 web · ESER-00006 Archive.org",
                "/eserler", 0, "Web + Archive.org"));

        liste.add(new DemoAdimi(2, "Eser arşivlendi",
                "Kaynak dosya düzenli ESER-* klasörüne alınır. SHA-256 ile tekrar kontrolü ve dosya adı standardizasyonu uygulanır.",
                DemoAdimi.DURUM_TAMAMLANDI,
                kasagi != null ? kasagi.kaynakDosya() : "Arşiv kaydı mevcut",
                "/eser/5", SesKaliteOlcutleri.KASAGI_ESER_ID, "Arşiv"));

        String kunyeDurum = astroDurum.toUpperCase(Locale.ROOT).contains("KONTROL")
                ? DemoAdimi.DURUM_KONTROL : DemoAdimi.DURUM_TAMAMLANDI;
        liste.add(new DemoAdimi(3, "Metadata çıkarıldı",
                "Başlık, yayınevi, ISBN, dil ve güven puanı çıkarılır. KONTROL_GEREKIYOR, güvenlik için otomatik onay yerine insan kontrolü gerektirir.",
                kunyeDurum,
                "Kaşağı: " + kasagiDurum + " · Astronomi: " + astroDurum,
                "/eser/6", SesKaliteOlcutleri.ASTRONOMI_ESER_ID, "Metadata"));

        liste.add(new DemoAdimi(4, "Metin bölündü",
                "Tam metin bölümlere ve TTS parçalarına ayrılır. Karakter ve parça sayısı üretim planını belirler.",
                DemoAdimi.DURUM_TAMAMLANDI,
                String.format(Locale.ROOT, "Kaşağı: %d bölüm, %d parça, %,d karakter · Astronomi: %d bölüm, %d parça, %,d karakter",
                        kasagiBolum, kasagiParca, kasagiKarakter, astroBolum, astroParca, astroKarakter),
                "/eser/6", SesKaliteOlcutleri.ASTRONOMI_ESER_ID, "Metin"));

        String sesDurum = el.ttsDurumu().toUpperCase(Locale.ROOT).contains("KAPALI")
                || el.ttsDurumu().toUpperCase(Locale.ROOT).contains("YOK")
                ? DemoAdimi.DURUM_KREDI : DemoAdimi.DURUM_TAMAMLANDI;
        liste.add(new DemoAdimi(5, "TTS kuyruğu hazırlandı",
                "Piper, Google Chirp ve ElevenLabs sağlayıcıları desteklenir. Kredi kontrolü, önizleme mantığı ve büyük eser koruması aktif.",
                sesDurum,
                "ElevenLabs: " + el.ttsDurumu() + " · Büyük eser üretimi kapalı",
                "/kuyruk", 0, "TTS"));

        liste.add(new DemoAdimi(6, "Kalite paneline aktarıldı",
                "Önizleme karşılaştırması, insan puanı şablonu ve telaffuz notları ile kalite değerlendirmesi yapılır.",
                DemoAdimi.DURUM_TAMAMLANDI,
                "Önizleme keşfi ve sağlayıcı karşılaştırması",
                "/kalite", 0, "Kalite"));

        liste.add(new DemoAdimi(7, "Web MVP'de gösterildi",
                "Yerel kontrol paneli, API endpointleri ve patron demo modu ile ürünleşme gösterilir.",
                DemoAdimi.DURUM_TAMAMLANDI,
                "http://127.0.0.1:8787/demo · GitHub: " + DemoGuvenlikService.GITHUB_URL,
                "/demo", 0, "Ürün"));

        return liste;
    }

    public List<DemoSenaryo> senaryolar() {
        return List.of(DemoSenaryo.kasagi(), DemoSenaryo.astronomi());
    }

    public String json() throws Exception {
        ObjectNode kok = json.createObjectNode();
        kok.put("degerOnerisi", DemoDegerOnerisiService.DEGER_ONERISI);
        kok.put("simulasyonNotu", DemoGuvenlikService.simulasyonNotu());
        kok.put("github", DemoGuvenlikService.GITHUB_URL);
        ArrayNode adimDizi = kok.putArray("adimlar");
        for (DemoAdimi a : adimlar()) {
            adimDizi.add(adimJson(a));
        }
        ArrayNode senaryoDizi = kok.putArray("senaryolar");
        for (DemoSenaryo s : senaryolar()) {
            ObjectNode n = json.createObjectNode();
            n.put("eserId", s.eserId());
            n.put("eserAdi", s.eserAdi());
            n.put("demoRolu", s.demoRolu());
            n.put("kaynakTipi", s.kaynakTipi());
            n.put("buyukEser", s.buyukEser());
            senaryoDizi.add(n);
        }
        ArrayNode uyari = kok.putArray("guvenlikUyarilari");
        DemoGuvenlikService.uyarilar().forEach(uyari::add);
        return WebGuvenlikService.guvenliJson(json.writerWithDefaultPrettyPrinter().writeValueAsString(kok));
    }

    public String akisJson() throws Exception {
        ArrayNode dizi = json.createArrayNode();
        for (DemoAdimi a : adimlar()) {
            dizi.add(adimJson(a));
        }
        return WebGuvenlikService.guvenliJson(json.writerWithDefaultPrettyPrinter().writeValueAsString(dizi));
    }

    private ObjectNode adimJson(DemoAdimi a) {
        ObjectNode n = json.createObjectNode();
        n.put("sira", a.sira());
        n.put("baslik", a.baslik());
        n.put("aciklama", a.aciklama());
        n.put("durum", a.durum());
        n.put("kanit", a.kanit());
        n.put("link", a.link());
        if (a.eserId() > 0) n.put("eserId", a.eserId());
        n.put("kaynakTipi", a.kaynakTipi());
        return n;
    }
}
