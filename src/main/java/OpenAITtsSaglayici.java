import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class OpenAITtsSaglayici implements TtsSaglayici {
    private static final String API = "https://api.openai.com/v1/audio/speech";
    private final String apiKey = TtsLaboratuvarYardimci.ortam("OPENAI_API_KEY", "");
    private final String model = TtsLaboratuvarYardimci.ortam("OPENAI_TTS_MODEL", "gpt-4o-mini-tts");
    private final String voice = TtsLaboratuvarYardimci.ortam("OPENAI_TTS_VOICE", "cedar");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Override public String kimlik() { return "openai"; }
    @Override public String ad() { return "OpenAI GPT TTS"; }
    @Override public String model() { return model; }
    @Override public String ses() { return voice; }

    @Override
    public Hazirlik hazirlik() {
        return apiKey.isBlank() ? Hazirlik.degil("OPENAI_API_KEY yok") : Hazirlik.hazir("API anahtarı bulundu");
    }

    @Override
    public TtsUretimSonucu uret(TtsUretimIstegi istek, Path ciktiKlasoru) throws Exception {
        if (!hazirlik().hazir()) throw new IllegalStateException(hazirlik().mesaj());
        Path hedef = ciktiKlasoru.resolve(kimlik()).resolve(istek.ornekId() + ".mp3");
        Files.createDirectories(hedef.getParent());

        ObjectNode govde = json.createObjectNode();
        govde.put("model", model);
        govde.put("voice", voice);
        govde.put("input", istek.metin());
        govde.put("response_format", "mp3");
        String yonerge = "Doğal ve akıcı Türkiye Türkçesiyle konuş. Metni aynen oku; ekleme veya çıkarma yapma. "
                + (istek.yonerge().isBlank() ? "" : istek.yonerge());
        govde.put("instructions", yonerge);

        HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(govde), StandardCharsets.UTF_8))
                .build();
        long baslangic = System.nanoTime();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String hata = new String(response.body(), StandardCharsets.UTF_8);
            throw new IllegalStateException("OpenAI TTS HTTP " + response.statusCode() + ": "
                    + TtsLaboratuvarYardimci.kisalt(hata, 500));
        }
        TtsLaboratuvarYardimci.atomikYaz(hedef, response.body());
        long sure = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - baslangic);
        return new TtsUretimSonucu(kimlik(), ad(), model, voice, istek.ornekId(), istek.metinTuru(),
                hedef, istek.metin().length(), Files.size(hedef), sure);
    }
}
