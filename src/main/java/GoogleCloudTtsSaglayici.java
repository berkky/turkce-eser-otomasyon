import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class GoogleCloudTtsSaglayici implements TtsSaglayici {
    private static final String API = "https://texttospeech.googleapis.com/v1/text:synthesize";
    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final String proje = TtsLaboratuvarYardimci.ortam("GOOGLE_CLOUD_PROJECT", "turkce-eser-tts-lab-2026");
    private final String model = TtsLaboratuvarYardimci.ortam("GOOGLE_TTS_MODEL", "Chirp 3 HD");
    private final String voice;
    private final String providerId;
    private final String displayName;
    private final String languageCode = TtsLaboratuvarYardimci.ortam("GOOGLE_TTS_LANGUAGE", "tr-TR");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final ObjectMapper json = new ObjectMapper();
    private GoogleCredentials credentials;

    public GoogleCloudTtsSaglayici() {
        this(TtsLaboratuvarYardimci.ortam("GOOGLE_TTS_VOICE", "tr-TR-Chirp3-HD-Kore"));
    }

    public GoogleCloudTtsSaglayici(String voice) {
        if (voice == null || voice.isBlank()) throw new IllegalArgumentException("Google TTS sesi bos olamaz.");
        this.voice = voice.trim();
        String kisaAd = this.voice.substring(this.voice.lastIndexOf('-') + 1);
        String guvenliAd = kisaAd.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        this.providerId = "google-cloud-" + guvenliAd;
        this.displayName = "Google Cloud Chirp 3 HD - " + kisaAd;
    }

    @Override public String kimlik() { return providerId; }
    @Override public String ad() { return displayName; }
    @Override public String model() { return model; }
    @Override public String ses() { return voice; }

    @Override
    public Hazirlik hazirlik() {
        try {
            erisimBelirteci();
            return Hazirlik.hazir("ADC hazir | proje: " + proje);
        } catch (Exception e) {
            return Hazirlik.degil("Google ADC kullanilamadi: " + TtsLaboratuvarYardimci.kisalt(e.getMessage(), 300));
        }
    }

    @Override
    public TtsUretimSonucu uret(TtsUretimIstegi istek, Path ciktiKlasoru) throws Exception {
        Path hedef = ciktiKlasoru.resolve(kimlik()).resolve(istek.ornekId() + ".mp3");
        Files.createDirectories(hedef.getParent());

        ObjectNode govde = json.createObjectNode();
        govde.putObject("input").put("text", istek.metin());
        ObjectNode voiceNode = govde.putObject("voice");
        voiceNode.put("languageCode", languageCode);
        voiceNode.put("name", voice);
        govde.putObject("audioConfig").put("audioEncoding", "MP3");

        HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + erisimBelirteci())
                .header("x-goog-user-project", proje)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(govde), StandardCharsets.UTF_8))
                .build();

        long baslangic = System.nanoTime();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String hata = new String(response.body(), StandardCharsets.UTF_8);
            throw new IllegalStateException("Google Cloud TTS HTTP " + response.statusCode() + ": " +
                    TtsLaboratuvarYardimci.kisalt(hata, 500));
        }

        JsonNode cevap = json.readTree(response.body());
        String audioContent = cevap.path("audioContent").asText("");
        if (audioContent.isBlank()) {
            throw new IllegalStateException("Google Cloud TTS yanitinda audioContent yok.");
        }

        TtsLaboratuvarYardimci.atomikYaz(hedef, Base64.getDecoder().decode(audioContent));
        long sure = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - baslangic);
        return new TtsUretimSonucu(kimlik(), ad(), model, voice, istek.ornekId(), istek.metinTuru(),
                hedef, istek.metin().length(), Files.size(hedef), sure);
    }

    private synchronized GoogleCredentials kimlikBilgisi() throws Exception {
        if (credentials == null) {
            GoogleCredentials bulunan = GoogleCredentials.getApplicationDefault();
            if (bulunan.createScopedRequired()) {
                bulunan = bulunan.createScoped(List.of(CLOUD_PLATFORM_SCOPE));
            }
            credentials = bulunan;
        }
        return credentials;
    }

    private String erisimBelirteci() throws Exception {
        GoogleCredentials kimlik = kimlikBilgisi();
        kimlik.refreshIfExpired();
        AccessToken token = kimlik.getAccessToken();
        if (token == null || token.getTokenValue() == null || token.getTokenValue().isBlank()) {
            token = kimlik.refreshAccessToken();
        }
        return token.getTokenValue();
    }
}
