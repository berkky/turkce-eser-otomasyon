import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Forced alignment ana servisi — mock varsayılan, gerçek API açık onaylı.
 */
public final class AlignmentService {
    public static final String ALTYAZI_YOK_MESAJ =
            "Altyazı henüz üretilmedi. Önce mock alignment veya gerçek preview alignment üretin.";

    private final AlignmentStorageService depo;
    private final ObjectMapper json = new ObjectMapper();

    public AlignmentService(Path sesArsivi) {
        this.depo = new AlignmentStorageService(sesArsivi);
    }

    public AlignmentService(AlignmentStorageService depo) {
        this.depo = depo;
    }

    public AlignmentPlan planla(int eserId) throws Exception {
        if (eserId == SesKaliteOlcutleri.ASTRONOMI_ESER_ID) {
            return new AlignmentPlan(eserId, "preview-elevenlabs", "", "", "", "LOCAL",
                    AlignmentPlan.STATUS_BLOCKED,
                    "Büyük eser — tam/preview alignment engelli. Önce maliyet onayı gerekir.",
                    0, "", "", OffsetDateTime.now().toString(), OffsetDateTime.now().toString());
        }
        AlignmentStorageService.PreviewKaynaklari k = depo.previewKaynaklari(eserId);
        AlignmentResult mevcutSonuc = depo.yukle(eserId);
        if (k == null) {
            if (mevcutSonuc.segmentCount() > 0 && mevcutSonuc.demoFixture()) {
                return new AlignmentPlan(eserId, mevcutSonuc.previewId(), "", mevcutSonuc.textHash(),
                        mevcutSonuc.audioHash(), "MOCK",
                        AlignmentPlan.STATUS_COMPLETED,
                        "Demo fixture alignment mevcut — gerçek önizleme gerekmez (patron demo).",
                        0, "preview-elevenlabs-demo-fixture",
                        AlignmentGuvenlikService.guvenliDosyaAdi(eserId, "alignment.json"),
                        OffsetDateTime.now().toString(), OffsetDateTime.now().toString());
            }
            AlignmentStorageService.PreviewKontrol kontrol = depo.previewKontrol(eserId);
            String neden = switch (kontrol) {
                case METIN_YOK -> "Alignment metni bulunamadı (preview-input.txt).";
                case MP3_YOK, SIFIR_BOYUT, ESER_YOK ->
                        "Önce ElevenLabs önizleme üretin (preview MP3 ve preview-input.txt gerekli).";
                case TAMAM -> "Önizleme hazır.";
            };
            return new AlignmentPlan(eserId, "preview-elevenlabs", "", "", "", "LOCAL",
                    kontrol == AlignmentStorageService.PreviewKontrol.TAMAM
                            ? AlignmentPlan.STATUS_READY : AlignmentPlan.STATUS_BLOCKED,
                    neden, 0, "preview-elevenlabs.mp3",
                    AlignmentGuvenlikService.guvenliDosyaAdi(eserId, "alignment.json"),
                    OffsetDateTime.now().toString(), OffsetDateTime.now().toString());
        }
        String textHash = depo.metinHash(k.metin());
        String audioHash = depo.audioHash(k.mp3());
        AlignmentResult mevcut = depo.yukle(eserId);
        String status = mevcut.status() != null && AlignmentPlan.STATUS_COMPLETED.equalsIgnoreCase(mevcut.status())
                ? AlignmentPlan.STATUS_COMPLETED
                : AlignmentPlan.STATUS_READY;
        String reason = status.equals(AlignmentPlan.STATUS_COMPLETED)
                ? mevcut.kaynakEtiketi() + " mevcut."
                : "Mock alignment üretilebilir; gerçek API yalnızca -GercekApiOnayli ile.";
        return new AlignmentPlan(eserId, k.previewId(), k.audioSafeId(), textHash, audioHash,
                ElevenLabsFabrika.durumOzeti().hazir() ? "ELEVENLABS" : "MOCK",
                status, reason, k.metin().length(),
                "preview-elevenlabs.mp3",
                AlignmentGuvenlikService.guvenliDosyaAdi(eserId, "alignment.json"),
                OffsetDateTime.now().toString(), OffsetDateTime.now().toString());
    }

    public AlignmentSonucu uret(int eserId, boolean mock, boolean gercekApiOnayli) throws Exception {
        AlignmentGuvenlikService.eserIzni(eserId);
        onizlemeDogrula(eserId, gercekApiOnayli && !mock);

        AlignmentStorageService.PreviewKaynaklari k = depo.previewKaynaklari(eserId);
        if (k == null) {
            throw new IllegalStateException(onizlemeHataMesaji(eserId, gercekApiOnayli && !mock));
        }

        String textHash = depo.metinHash(k.metin());
        String audioHash = depo.audioHash(k.mp3());
        if (depo.mevcutKullanilabilir(eserId, textHash, audioHash)) {
            AlignmentResult mevcut = depo.yukle(eserId);
            return AlignmentSonucu.basarili(mevcut, true, "Mevcut alignment kullanıldı");
        }

        if (gercekApiOnayli && !mock) {
            if (ElevenLabsFabrika.mockModAktif()) {
                throw new IllegalStateException(AlignmentHata.mockModAktif().kullaniciMesaji());
            }
            if (!TtsLaboratuvarYardimci.ortamVar("ELEVENLABS_API_KEY")) {
                depo.sonHataKaydet(eserId, AlignmentHata.apiKeyYok());
                throw new IllegalStateException(AlignmentHata.apiKeyYok().kullaniciMesaji());
            }
        }

        double sure = AlignmentMockService.tahminiSure(k.mp3(), k.metin());
        AlignmentResult sonuc;
        try {
            if (mock || !gercekApiOnayli) {
                sonuc = AlignmentMockService.uret(k, textHash, audioHash, sure);
            } else {
                sonuc = AlignmentApiClient.uret(k, textHash, audioHash, sure);
            }
        } catch (AlignmentApiException e) {
            depo.sonHataKaydet(eserId, e.hata());
            throw new IllegalStateException(e.hata().kullaniciMesaji());
        }

        if (AlignmentPlan.STATUS_FAILED.equalsIgnoreCase(sonuc.status())) {
            depo.sonHataKaydet(eserId, AlignmentHata.parse());
            throw new IllegalStateException("Alignment başarısız — yanıt işlenemedi.");
        }

        SubtitleExportService.dogrula(sonuc);
        String srt = SubtitleExportService.srt(sonuc);
        String vtt = SubtitleExportService.vtt(sonuc);
        depo.kaydet(sonuc, srt, vtt);
        return AlignmentSonucu.basarili(sonuc, false, "Alignment üretildi (" + sonuc.kaynakEtiketi() + ")");
    }

    private void onizlemeDogrula(int eserId, boolean gercekApi) throws Exception {
        AlignmentStorageService.PreviewKontrol kontrol = depo.previewKontrol(eserId);
        if (kontrol == AlignmentStorageService.PreviewKontrol.TAMAM) {
            return;
        }
        String mesaj = onizlemeHataMesaji(eserId, gercekApi);
        if (gercekApi) {
            AlignmentHata hata = switch (kontrol) {
                case METIN_YOK -> AlignmentHata.metinYok();
                default -> AlignmentHata.onizlemeYok();
            };
            depo.sonHataKaydet(eserId, hata);
        }
        throw new IllegalStateException(mesaj);
    }

    private String onizlemeHataMesaji(int eserId, boolean gercekApi) throws Exception {
        return switch (depo.previewKontrol(eserId)) {
            case METIN_YOK -> gercekApi
                    ? AlignmentHata.metinYok().kullaniciMesaji()
                    : "Önizleme bulunamadı — önce ElevenLabs önizleme üretin.";
            case MP3_YOK, SIFIR_BOYUT, ESER_YOK -> gercekApi
                    ? AlignmentHata.onizlemeYok().kullaniciMesaji()
                    : "Önizleme bulunamadı — önce ElevenLabs önizleme üretin.";
            case TAMAM -> "";
        };
    }

    public AlignmentSonucu uretDemoFixture(int eserId) throws Exception {
        AlignmentGuvenlikService.eserIzni(eserId);
        AlignmentDemoFixtureService fixture = new AlignmentDemoFixtureService(depo);
        AlignmentStorageService.PreviewKaynaklari k = fixture.kaynaklar(eserId);
        String textHash = depo.metinHash(k.metin());
        String audioHash = depo.audioHash(k.mp3());
        if (depo.mevcutKullanilabilir(eserId, textHash, audioHash)) {
            AlignmentResult mevcut = depo.yukle(eserId);
            if (mevcut.demoFixture()) {
                return AlignmentSonucu.basarili(mevcut, true, "Mevcut demo fixture alignment kullanıldı");
            }
        }
        double sure = AlignmentMockService.tahminiSure(k.mp3(), k.metin());
        AlignmentResult sonuc = AlignmentMockService.uret(k, textHash, audioHash, sure, true);
        SubtitleExportService.dogrula(sonuc);
        String srt = SubtitleExportService.srt(sonuc);
        String vtt = SubtitleExportService.vtt(sonuc);
        depo.kaydet(sonuc, srt, vtt);
        return AlignmentSonucu.basarili(sonuc, false, "Demo fixture alignment üretildi");
    }

    public AlignmentResult sonuc(int eserId) throws Exception {
        return depo.yukle(eserId);
    }

    public String jsonListe() throws Exception {
        ObjectNode kok = json.createObjectNode();
        ArrayNode dizi = kok.putArray("eserler");
        for (int id : List.of(SesKaliteOlcutleri.KASAGI_ESER_ID, SesKaliteOlcutleri.ASTRONOMI_ESER_ID)) {
            AlignmentPlan p = planla(id);
            dizi.add(p.json(json));
        }
        return AlignmentGuvenlikService.jsonGuvenli(json.writerWithDefaultPrettyPrinter().writeValueAsString(kok));
    }

    public String jsonPlan(int eserId) throws Exception {
        AlignmentPlan p = planla(eserId);
        AlignmentResult r = depo.yukle(eserId);
        ObjectNode n = p.json(json);
        if (r.segmentCount() > 0 || r.eserId() > 0) {
            n.put("segmentCount", r.segmentCount());
            n.put("wordCount", r.wordCount());
            n.put("durationSeconds", r.durationSeconds());
            n.put("srtSafeName", r.srtSafeName());
            n.put("vttSafeName", r.vttSafeName());
            n.put("demoFixture", r.demoFixture());
            n.put("realApiUsed", r.realApiUsed());
            n.put("source", r.source());
            n.put("apiProvider", r.apiProvider());
            n.put("kaynakEtiketi", r.kaynakEtiketi());
            if (r.apiRequestId() != null && !r.apiRequestId().isBlank()) {
                n.put("apiRequestId", r.apiRequestId());
            }
        }
        depo.sonHataOku(eserId).ifPresent(h -> {
            n.put("sonHataKodu", h.hataKodu());
            n.put("sonHataMesaji", h.kullaniciMesaji());
        });
        n.put("gercekApiWebdenKapali", true);
        n.put("gercekApiKomut",
                "powershell -ExecutionPolicy Bypass -File .\\elevenlabs-alignment.ps1 -EserId 5 -GercekApiOnayli");
        return AlignmentGuvenlikService.jsonGuvenli(json.writerWithDefaultPrettyPrinter().writeValueAsString(n));
    }

    public String jsonSegments(int eserId) throws Exception {
        AlignmentResult r = depo.yukle(eserId);
        if (r.segmentCount() == 0) {
            return AlignmentGuvenlikService.jsonGuvenli("{\"segments\":[]}");
        }
        ArrayNode dizi = json.createArrayNode();
        for (AlignmentSegment s : r.segments()) {
            dizi.add(s.json(json));
        }
        ObjectNode kok = json.createObjectNode();
        kok.set("segments", dizi);
        kok.put("eserId", eserId);
        return AlignmentGuvenlikService.jsonGuvenli(json.writerWithDefaultPrettyPrinter().writeValueAsString(kok));
    }

    public boolean altyaziMevcut(int eserId) throws Exception {
        Path vtt = depo.vttDosya(eserId);
        Path srt = depo.srtDosya(eserId);
        if (java.nio.file.Files.isRegularFile(vtt) && java.nio.file.Files.size(vtt) > 0) {
            return true;
        }
        if (java.nio.file.Files.isRegularFile(srt) && java.nio.file.Files.size(srt) > 0) {
            return true;
        }
        return depo.yukle(eserId).segmentCount() > 0;
    }

    public java.util.Optional<String> altyaziIcerik(int eserId, String format) throws Exception {
        Path dosya = "vtt".equalsIgnoreCase(format) ? depo.vttDosya(eserId) : depo.srtDosya(eserId);
        if (java.nio.file.Files.isRegularFile(dosya) && java.nio.file.Files.size(dosya) > 0) {
            return java.util.Optional.of(java.nio.file.Files.readString(dosya, java.nio.charset.StandardCharsets.UTF_8));
        }
        AlignmentResult r = depo.yukle(eserId);
        if (r.segmentCount() == 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of("vtt".equalsIgnoreCase(format)
                ? SubtitleExportService.vtt(r) : SubtitleExportService.srt(r));
    }

    public String altyazi(int eserId, String format) throws Exception {
        return altyaziIcerik(eserId, format).orElseThrow(
                () -> new IllegalStateException(ALTYAZI_YOK_MESAJ));
    }

    public record EserAlignmentDurum(
            boolean gercekOnizlemeVar,
            boolean altyaziVar,
            boolean demoFixture,
            boolean gercekApiUsed,
            String kaynakEtiketi,
            boolean mockButonAktif,
            String mockButonNeden,
            String sonHataMesaji,
            String gercekApiKapaliNeden
    ) {
    }

    public EserAlignmentDurum eserDurumu(int eserId, AlignmentPlan plan) throws Exception {
        boolean gercekOniz = depo.previewKaynaklari(eserId) != null;
        boolean altyazi = altyaziMevcut(eserId);
        AlignmentResult sonuc = depo.yukle(eserId);
        boolean mockAktif = eserId == SesKaliteOlcutleri.KASAGI_ESER_ID
                && gercekOniz
                && !AlignmentPlan.STATUS_BLOCKED.equals(plan.status());
        String neden = "";
        if (!mockAktif) {
            if (!gercekOniz) {
                neden = "Önizleme MP3 bulunamadı.";
            } else if (AlignmentPlan.STATUS_BLOCKED.equals(plan.status())) {
                neden = plan.reason();
            }
        }
        String sonHata = depo.sonHataOku(eserId).map(AlignmentHata::kullaniciMesaji).orElse("");
        String apiKapali = "Gerçek Forced Alignment API yalnızca komut satırında -GercekApiOnayli ile çalıştırılır.";
        return new EserAlignmentDurum(gercekOniz, altyazi, sonuc.demoFixture(), sonuc.realApiUsed(),
                sonuc.kaynakEtiketi(), mockAktif, neden, sonHata, apiKapali);
    }

    public AlignmentStorageService depo() {
        return depo;
    }

    public record AlignmentSonucu(boolean basarili, boolean mevcut, String mesaj, AlignmentResult sonuc) {
        public static AlignmentSonucu basarili(AlignmentResult sonuc, boolean mevcut, String mesaj) {
            return new AlignmentSonucu(true, mevcut, mesaj, sonuc);
        }

        public static AlignmentSonucu hatali(String mesaj) {
            return new AlignmentSonucu(false, false, mesaj, null);
        }
    }
}
