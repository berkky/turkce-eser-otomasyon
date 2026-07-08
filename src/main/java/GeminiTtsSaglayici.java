import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public final class GeminiTtsSaglayici implements TtsSaglayici {
    private static final String API = "https://generativelanguage.googleapis.com/v1beta/interactions";
    private final String apiKey = TtsLaboratuvarYardimci.ortam("GEMINI_API_KEY", "");
    private final String model = TtsLaboratuvarYardimci.ortam("GEMINI_TTS_MODEL", "gemini-3.1-flash-tts-preview");
    private final String voice = TtsLaboratuvarYardimci.ortam("GEMINI_TTS_VOICE", "Kore");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final FfmpegClient ffmpeg;

    public GeminiTtsSaglayici(Path projeKlasoru) {
        String komut = TtsLaboratuvarYardimci.ortam("FFMPEG_PATH", "ffmpeg");
        String bitrate = TtsLaboratuvarYardimci.ortam("TTS_LAB_MP3_BITRATE", "128k");
        this.ffmpeg = new FfmpegClient(komut, bitrate);
    }

    @Override public String kimlik() { return "gemini"; }
    @Override public String ad() { return "Google Gemini TTS"; }
    @Override public String model() { return model; }
    @Override public String ses() { return voice; }

    @Override
    public Hazirlik hazirlik() {
        if (apiKey.isBlank()) return Hazirlik.degil("GEMINI_API_KEY yok");
        FfmpegClient.KontrolSonucu k = ffmpeg.kontrolEt();
        return k.hazir() ? Hazirlik.hazir("API anahtarı ve FFmpeg hazır") : Hazirlik.degil(k.mesaj());
    }

    @Override
    public TtsUretimSonucu uret(TtsUretimIstegi istek, Path ciktiKlasoru) throws Exception {
        if (!hazirlik().hazir()) throw new IllegalStateException(hazirlik().mesaj());
        Path klasor = ciktiKlasoru.resolve(kimlik());
        Files.createDirectories(klasor);
        Path wav = klasor.resolve(istek.ornekId() + ".gemini.tmp.wav");
        Path mp3 = klasor.resolve(istek.ornekId() + ".mp3");

        ObjectNode govde = json.createObjectNode();
        govde.put("model", model);
        String prompt = "Synthesize Turkish speech. Do not read these instructions aloud. "
                + "Read exactly and only the text inside <TRANSCRIPT>. Voice direction: "
                + (istek.yonerge().isBlank() ? "natural, fluent, neutral Turkish narration" : istek.yonerge())
                + "\n<TRANSCRIPT>\n" + istek.metin() + "\n</TRANSCRIPT>";
        govde.put("input", prompt);
        govde.putObject("response_format").put("type", "audio");
        ObjectNode generation = govde.putObject("generation_config");
        ArrayNode speech = generation.putArray("speech_config");
        speech.addObject().put("voice", voice);

        HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                .timeout(Duration.ofMinutes(5))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Api-Revision", "2026-05-20")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(govde), StandardCharsets.UTF_8))
                .build();
        long baslangic = System.nanoTime();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Gemini TTS HTTP " + response.statusCode() + ": "
                    + TtsLaboratuvarYardimci.kisalt(response.body(), 700));
        }
        JsonNode kok = json.readTree(response.body());
        String base64 = sesVerisiniBul(kok);
        if (base64.isBlank()) {
            throw new IllegalStateException("Gemini yanıtında output_audio.data bulunamadı: "
                    + TtsLaboratuvarYardimci.kisalt(response.body(), 700));
        }
        byte[] pcm = Base64.getDecoder().decode(base64);
        WavYazici.pcm16MonoYaz(wav, pcm, 24_000);
        try {
            ffmpeg.mp3eDonustur(wav, mp3);
        } finally {
            Files.deleteIfExists(wav);
        }
        long sure = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - baslangic);
        return new TtsUretimSonucu(kimlik(), ad(), model, voice, istek.ornekId(), istek.metinTuru(),
                mp3, istek.metin().length(), Files.size(mp3), sure);
    }

    private String sesVerisiniBul(JsonNode kok) {
        String[] yollar = {
                kok.path("output_audio").path("data").asText(""),
                kok.path("outputAudio").path("data").asText(""),
                kok.path("output").path("audio").path("data").asText("")
        };
        for (String s : yollar) if (!s.isBlank()) return s;
        JsonNode outputs = kok.path("outputs");
        if (outputs.isArray()) {
            for (JsonNode n : outputs) {
                String s = n.path("audio").path("data").asText("");
                if (!s.isBlank()) return s;
            }
        }
        return "";
    }
}
