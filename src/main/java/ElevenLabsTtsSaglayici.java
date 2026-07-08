import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class ElevenLabsTtsSaglayici implements TtsSaglayici {
    private static final String ABONELIK_API = "https://api.elevenlabs.io/v1/user/subscription";

    private final String apiKey = TtsLaboratuvarYardimci.ortam("ELEVENLABS_API_KEY", "");
    private final String voiceId = TtsLaboratuvarYardimci.ortam("ELEVENLABS_VOICE_ID", "");
    private final String voiceName = TtsLaboratuvarYardimci.ortam(
            "ELEVENLABS_VOICE_NAME",
            "Seçili ElevenLabs sesi"
    );
    private final String model = TtsLaboratuvarYardimci.ortam(
            "ELEVENLABS_MODEL",
            "eleven_multilingual_v2"
    );

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final ObjectMapper json = new ObjectMapper();
    private volatile Hazirlik onbellekliHazirlik;

    @Override
    public String kimlik() {
        return "elevenlabs";
    }

    @Override
    public String ad() {
        return "ElevenLabs";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String ses() {
        return voiceName;
    }

    @Override
    public Hazirlik hazirlik() {
        if (apiKey.isBlank()) {
            return Hazirlik.degil("ELEVENLABS_API_KEY yok");
        }

        if (voiceId.isBlank()) {
            return Hazirlik.degil("ELEVENLABS_VOICE_ID yok");
        }

        Hazirlik mevcut = onbellekliHazirlik;
        if (mevcut != null) {
            return mevcut;
        }

        synchronized (this) {
            if (onbellekliHazirlik == null) {
                onbellekliHazirlik = kotaKontrolEt();
            }
            return onbellekliHazirlik;
        }
    }

    private Hazirlik kotaKontrolEt() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ABONELIK_API))
                    .timeout(Duration.ofSeconds(30))
                    .header("xi-api-key", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return Hazirlik.degil(
                        "ElevenLabs API anahtarı geçersiz veya yetkisiz (HTTP "
                                + response.statusCode() + ")"
                );
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Hazirlik.degil(
                        "ElevenLabs kota kontrolü başarısız (HTTP "
                                + response.statusCode() + "): "
                                + TtsLaboratuvarYardimci.kisalt(response.body(), 250)
                );
            }

            JsonNode kok = json.readTree(response.body());
            long kullanilan = kok.path("character_count").asLong(-1);
            long limit = kok.path("character_limit").asLong(-1);

            if (kullanilan < 0 || limit < 0) {
                return Hazirlik.degil("ElevenLabs kota bilgisi okunamadı");
            }

            long kalan = Math.max(0, limit - kullanilan);

            if (kalan <= 0) {
                return Hazirlik.degil(
                        "ElevenLabs kredisi yok: "
                                + kullanilan + "/" + limit + " kullanıldı"
                );
            }

            return Hazirlik.hazir(
                    "API, ses ve kredi hazır | kalan kredi: " + kalan
            );

        } catch (Exception e) {
            return Hazirlik.degil(
                    "ElevenLabs kota kontrolü başarısız: "
                            + TtsLaboratuvarYardimci.kisalt(e.getMessage(), 250)
            );
        }
    }

    @Override
    public TtsUretimSonucu uret(
            TtsUretimIstegi istek,
            Path ciktiKlasoru
    ) throws Exception {
        Hazirlik durum = hazirlik();

        if (!durum.hazir()) {
            throw new IllegalStateException(durum.mesaj());
        }

        Path hedef = ciktiKlasoru
                .resolve(kimlik())
                .resolve(istek.ornekId() + ".mp3");

        ElevenLabsClient client = new ElevenLabsClient(apiKey, model);

        ElevenLabsClient.Ses ses = new ElevenLabsClient.Ses(
                voiceId,
                voiceName,
                "laboratuvar",
                "",
                "tr",
                "",
                true
        );

        long baslangic = System.nanoTime();

        client.sesUret(
                istek.metin(),
                ses,
                hedef
        );

        long sure = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - baslangic
        );

        return new TtsUretimSonucu(
                kimlik(),
                ad(),
                model,
                voiceName,
                istek.ornekId(),
                istek.metinTuru(),
                hedef,
                istek.metin().length(),
                Files.size(hedef),
                sure
        );
    }
}
