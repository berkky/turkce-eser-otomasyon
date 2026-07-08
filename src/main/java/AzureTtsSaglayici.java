import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class AzureTtsSaglayici implements TtsSaglayici {
    private final String apiKey = TtsLaboratuvarYardimci.ortam("AZURE_SPEECH_KEY", "");
    private final String region = TtsLaboratuvarYardimci.ortam("AZURE_SPEECH_REGION", "");
    private final String endpoint = TtsLaboratuvarYardimci.ortam("AZURE_SPEECH_ENDPOINT", "");
    private final String voice = TtsLaboratuvarYardimci.ortam("AZURE_TTS_VOICE", "tr-TR-AhmetNeural");
    private final String output = TtsLaboratuvarYardimci.ortam("AZURE_TTS_OUTPUT_FORMAT", "audio-24khz-96kbitrate-mono-mp3");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    @Override public String kimlik() { return "azure"; }
    @Override public String ad() { return "Microsoft Azure Speech"; }
    @Override public String model() { return "Azure Neural / MAI Voice"; }
    @Override public String ses() { return voice; }

    @Override
    public Hazirlik hazirlik() {
        if (apiKey.isBlank()) return Hazirlik.degil("AZURE_SPEECH_KEY yok");
        if (endpoint.isBlank() && region.isBlank()) return Hazirlik.degil("AZURE_SPEECH_REGION veya AZURE_SPEECH_ENDPOINT yok");
        return Hazirlik.hazir("Azure anahtarı ve bölge hazır");
    }

    @Override
    public TtsUretimSonucu uret(TtsUretimIstegi istek, Path ciktiKlasoru) throws Exception {
        if (!hazirlik().hazir()) throw new IllegalStateException(hazirlik().mesaj());
        String url = endpoint.isBlank()
                ? "https://" + region + ".tts.speech.microsoft.com/cognitiveservices/v1"
                : endpoint.replaceAll("/+$", "") + "/cognitiveservices/v1";
        Path hedef = ciktiKlasoru.resolve(kimlik()).resolve(istek.ornekId() + ".mp3");
        Files.createDirectories(hedef.getParent());
        String rate = TtsLaboratuvarYardimci.ortam("AZURE_TTS_RATE", "-3%");
        String ssml = "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"tr-TR\">"
                + "<voice name=\"" + TtsLaboratuvarYardimci.xmlKacis(voice) + "\">"
                + "<prosody rate=\"" + TtsLaboratuvarYardimci.xmlKacis(rate) + "\">"
                + TtsLaboratuvarYardimci.xmlKacis(istek.metin())
                + "</prosody></voice></speak>";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .header("Content-Type", "application/ssml+xml")
                .header("X-Microsoft-OutputFormat", output)
                .header("User-Agent", "eser-otomasyon-tts-lab")
                .POST(HttpRequest.BodyPublishers.ofString(ssml, StandardCharsets.UTF_8))
                .build();
        long baslangic = System.nanoTime();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String hata = new String(response.body(), StandardCharsets.UTF_8);
            throw new IllegalStateException("Azure TTS HTTP " + response.statusCode() + ": "
                    + TtsLaboratuvarYardimci.kisalt(hata, 500));
        }
        TtsLaboratuvarYardimci.atomikYaz(hedef, response.body());
        long sure = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - baslangic);
        return new TtsUretimSonucu(kimlik(), ad(), model(), voice, istek.ornekId(), istek.metinTuru(),
                hedef, istek.metin().length(), Files.size(hedef), sure);
    }
}
