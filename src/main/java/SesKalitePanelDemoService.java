import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

/**
 * Test ve demo için mock önizleme verisi oluşturur.
 * Gerçek kullanıcıya mock ses gerçek ses gibi sunulmamalıdır.
 */
public final class SesKalitePanelDemoService {
    private static final ObjectMapper JSON = new ObjectMapper();

    private SesKalitePanelDemoService() {
    }

    public static Path demoVerisiOlustur(Path sesArsivKlasoru) throws Exception {
        Path kasagi = sesArsivKlasoru.resolve("ESER-00005 - Kasagi - Vikikaynak");
        Path astronomi = sesArsivKlasoru.resolve("ESER-00006 - Astronomi Alfa Yayinlari");
        Path elKlasor = kasagi.resolve("onizleme").resolve("elevenlabs");
        Path piperKlasor = kasagi.resolve("onizleme").resolve("piper");
        Path astroKlasor = astronomi.resolve("onizleme").resolve("elevenlabs");

        Files.createDirectories(elKlasor);
        Files.createDirectories(piperKlasor);
        Files.createDirectories(astroKlasor);

        String metin = "Kaşağı hikâyesinden demo önizleme metni. MOCK — gerçek TTS değildir; test amaçlıdır.";
        Files.writeString(elKlasor.resolve("preview-input.txt"), metin, StandardCharsets.UTF_8);

        Path mp3 = elKlasor.resolve("preview-elevenlabs.mp3");
        Files.write(elKlasor.resolve("preview-elevenlabs.mp3.partial"), ElevenLabsMockClient.MINIMAL_MP3_FIXTURE);
        Files.move(elKlasor.resolve("preview-elevenlabs.mp3.partial"), mp3,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        ObjectNode json = JSON.createObjectNode();
        json.put("eserId", 5);
        json.put("eserAdi", "Kaşağı - Vikikaynak");
        json.put("provider", "ELEVENLABS");
        json.put("modelId", "eleven_multilingual_v2");
        json.put("voiceIdMasked", "mock...0001");
        json.put("inputCharacterCount", metin.length());
        json.put("estimatedCharacterCost", metin.length());
        json.put("generatedAt", OffsetDateTime.now().toString());
        json.put("outputFile", mp3.toAbsolutePath().toString());
        json.put("requestHash", "demo-mock-kasagi-hash-0001");
        json.put("status", "MOCK");
        json.put("mockMode", true);
        json.put("mock", true);
        json.put("format", "mp3_mock_fixture");
        json.put("warning", "MOCK — gerçek TTS değildir; test amaçlıdır");
        Files.writeString(elKlasor.resolve("preview-elevenlabs.json"),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(json),
                StandardCharsets.UTF_8);

        Path piperMp3 = piperKlasor.resolve("preview-piper.mp3");
        Files.write(piperMp3, ElevenLabsMockClient.MINIMAL_MP3_FIXTURE);
        ObjectNode piperJson = JSON.createObjectNode();
        piperJson.put("provider", "PIPER");
        piperJson.put("modelId", "tr_TR-dfki-medium");
        piperJson.put("mock", true);
        piperJson.put("status", "MOCK");
        piperJson.put("requestHash", "demo-mock-piper-hash-0001");
        piperJson.put("warning", "MOCK — gerçek TTS değildir");
        Files.writeString(piperKlasor.resolve("preview-piper.json"),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(piperJson),
                StandardCharsets.UTF_8);

        Files.write(astroKlasor.resolve("preview-elevenlabs.mp3"), new byte[0]);
        ObjectNode invalid = JSON.createObjectNode();
        invalid.put("eserId", 6);
        invalid.put("eserAdi", "Astronomi Alfa Yayınları");
        invalid.put("status", "BASARISIZ");
        invalid.put("errorMessage", "0 byte demo — GECERSIZ işareti testi");
        Files.writeString(astroKlasor.resolve("preview-elevenlabs.json"),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(invalid),
                StandardCharsets.UTF_8);

        return sesArsivKlasoru;
    }
}
