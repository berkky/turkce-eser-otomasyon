import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;

/**
 * Sistem durumu ve sağlayıcı özeti (anahtar değerleri gösterilmez).
 */
public final class WebSistemDurumService {
    private final WebOrtam ortam;
    private final ObjectMapper json = new ObjectMapper();

    public WebSistemDurumService(WebOrtam ortam) {
        this.ortam = ortam;
    }

    public SistemDurumu durum() {
        ElevenLabsFabrika.DurumOzeti el = ElevenLabsFabrika.durumOzeti();
        boolean ffmpeg = false;
        boolean piper = false;
        try {
            ffmpeg = new FfmpegClient(ortam.projeKlasoru()).kontrolEt().hazir();
            piper = new PiperTtsSaglayici(ortam.projeKlasoru()).hazirlik().hazir();
        } catch (Exception ignored) {
        }
        boolean tesseract = YerelOcrService.kullanilabilirMi();
        return new SistemDurumu(
                System.getProperty("java.version"),
                WebGuvenlikService.yolMaskele(ortam.projeKlasoru()),
                WebGuvenlikService.yolMaskele(ortam.arsiv()),
                WebGuvenlikService.yolMaskele(ortam.metinArsivi()),
                WebGuvenlikService.yolMaskele(ortam.sesArsivi()),
                WebGuvenlikService.yolMaskele(ortam.kuyruk()),
                WebGuvenlikService.yolMaskele(ortam.katalog()),
                Files.isRegularFile(ortam.katalog()),
                ffmpeg,
                piper,
                WebGuvenlikService.envDurumu("ELEVENLABS_API_KEY"),
                WebGuvenlikService.envDurumu("ELEVENLABS_VOICE_ID"),
                el.abonelikOkunabiliyor(),
                el.ttsDurumu(),
                el.kalanKredi(),
                WebGuvenlikService.envDurumu("OPENAI_API_KEY"),
                WebGuvenlikService.envDurumu("GEMINI_API_KEY"),
                WebGuvenlikService.envDurumu("AZURE_SPEECH_KEY"),
                tesseract
        );
    }

    public String durumJson() throws Exception {
        ObjectNode n = json.createObjectNode();
        SistemDurumu d = durum();
        n.put("javaSurumu", d.javaSurumu());
        n.put("projeKlasoru", d.projeKlasoru());
        n.put("arsiv", d.arsiv());
        n.put("metinArsivi", d.metinArsivi());
        n.put("sesArsivi", d.sesArsivi());
        n.put("kuyruk", d.kuyruk());
        n.put("excelKatalog", d.excelVar() ? "VAR" : "YOK");
        n.put("ffmpeg", d.ffmpeg() ? "HAZIR" : "KAPALI");
        n.put("piper", d.piper() ? "HAZIR" : "KAPALI");
        n.put("elevenLabsApi", d.elevenLabsApi());
        n.put("elevenLabsVoice", d.elevenLabsVoice());
        n.put("elevenLabsAbonelik", d.elevenLabsAbonelikOk() ? "OKUNABILIYOR" : "OKUNAMIYOR");
        n.put("elevenLabsTts", d.elevenLabsTts());
        n.put("elevenLabsKalanKredi", d.elevenLabsKalanKredi());
        n.put("openAiApi", d.openAiApi());
        n.put("geminiApi", d.geminiApi());
        n.put("azureApi", d.azureApi());
        n.put("tesseract", d.tesseract() ? "HAZIR" : "KAPALI");
        return WebGuvenlikService.guvenliJson(json.writerWithDefaultPrettyPrinter().writeValueAsString(n));
    }

    public record SistemDurumu(String javaSurumu, String projeKlasoru, String arsiv, String metinArsivi,
                               String sesArsivi, String kuyruk, String katalog, boolean excelVar,
                               boolean ffmpeg, boolean piper, String elevenLabsApi, String elevenLabsVoice,
                               boolean elevenLabsAbonelikOk, String elevenLabsTts, long elevenLabsKalanKredi,
                               String openAiApi, String geminiApi, String azureApi, boolean tesseract) {
    }
}
