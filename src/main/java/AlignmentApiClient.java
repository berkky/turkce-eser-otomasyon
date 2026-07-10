import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ElevenLabs Forced Alignment API istemcisi — varsayılan kapalı.
 * Gerçek çağrı yalnızca açık onay ve kredi ile yapılır.
 */
public final class AlignmentApiClient {
    private static final String API_URL = "https://api.elevenlabs.io/v1/forced-alignment";
    private static final int TIMEOUT_SANIYE = 120;
    private static final Pattern CUMLE = Pattern.compile("[^.!?]+[.!?]+|[^.!?]+$");

    private static volatile AlignmentHttpTransport testTransport;

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper json = new ObjectMapper();

    @FunctionalInterface
    public interface AlignmentHttpTransport {
        record Yanit(int statusCode, String body, String requestId) {
        }

        Yanit post(byte[] audio, String dosyaAdi, String metin) throws Exception;
    }

    private AlignmentApiClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API anahtarı boş.");
        }
        this.apiKey = apiKey.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    static void testTransportAyarla(AlignmentHttpTransport transport) {
        testTransport = transport;
    }

    static void testTransportTemizle() {
        testTransport = null;
    }

    public static AlignmentResult uret(AlignmentStorageService.PreviewKaynaklari kaynak,
                                       String textHash,
                                       String audioHash,
                                       double audioDurationSeconds) throws AlignmentApiException {
        if (ElevenLabsFabrika.mockModAktif()) {
            throw new AlignmentApiException(AlignmentHata.mockModAktif());
        }
        String apiKey = TtsLaboratuvarYardimci.ortam("ELEVENLABS_API_KEY", "");
        if (apiKey.isBlank()) {
            throw new AlignmentApiException(AlignmentHata.apiKeyYok());
        }
        if (testTransport == null) {
            var ozet = ElevenLabsFabrika.durumOzeti();
            if (!ozet.hazir() || ozet.kalanKredi() <= 0) {
                throw new AlignmentApiException(AlignmentHata.krediYok());
            }
        }
        return new AlignmentApiClient(apiKey).forcedAlign(kaynak, textHash, audioHash);
    }

    private AlignmentResult forcedAlign(AlignmentStorageService.PreviewKaynaklari kaynak,
                                        String textHash,
                                        String audioHash) throws AlignmentApiException {
        try {
            byte[] audio = Files.readAllBytes(kaynak.mp3());
            if (audio.length < AlignmentStorageService.EN_AZ_MP3) {
                throw new AlignmentApiException(AlignmentHata.girdi(400));
            }
            String metin = kaynak.metin().trim();
            if (metin.isBlank()) {
                throw new AlignmentApiException(AlignmentHata.metinYok());
            }

            AlignmentHttpTransport.Yanit yanit = testTransport != null
                    ? testTransport.post(audio, "preview-elevenlabs.mp3", metin)
                    : gercekPost(audio, metin);

            if (yanit.statusCode() < 200 || yanit.statusCode() >= 300) {
                throw new AlignmentApiException(AlignmentHata.http(yanit.statusCode(),
                        "ElevenLabs alignment isteği başarısız (HTTP " + yanit.statusCode() + ")."));
            }

            return parseYanit(kaynak, textHash, audioHash, yanit.body(), yanit.requestId());
        } catch (AlignmentApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AlignmentApiException(AlignmentHata.saglayici(0));
        }
    }

    private AlignmentHttpTransport.Yanit gercekPost(byte[] audio, String metin) throws Exception {
        String boundary = "----ElevenLabsAlign" + UUID.randomUUID();
        byte[] govde = multipartGovde(boundary, audio, "preview-elevenlabs.mp3", "audio/mpeg", metin);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(TIMEOUT_SANIYE))
                .header("xi-api-key", apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(govde))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String requestId = response.headers().firstValue("request-id")
                .or(() -> response.headers().firstValue("x-request-id"))
                .orElse("");
        return new AlignmentHttpTransport.Yanit(response.statusCode(), response.body(), requestId);
    }

    static byte[] multipartGovde(String boundary, byte[] dosya, String dosyaAdi,
                                 String mime, String metin) {
        String baslik = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + dosyaAdi + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n";
        String metinBaslik = "\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"text\"\r\n\r\n"
                + metin + "\r\n--" + boundary + "--\r\n";
        byte[] baslikB = baslik.getBytes(StandardCharsets.UTF_8);
        byte[] metinB = metinBaslik.getBytes(StandardCharsets.UTF_8);
        byte[] govde = new byte[baslikB.length + dosya.length + metinB.length];
        System.arraycopy(baslikB, 0, govde, 0, baslikB.length);
        System.arraycopy(dosya, 0, govde, baslikB.length, dosya.length);
        System.arraycopy(metinB, 0, govde, baslikB.length + dosya.length, metinB.length);
        return govde;
    }

    AlignmentResult parseYanit(AlignmentStorageService.PreviewKaynaklari kaynak,
                               String textHash,
                               String audioHash,
                               String body,
                               String requestId) throws AlignmentApiException {
        try {
            JsonNode kok = json.readTree(body == null ? "" : body);
            JsonNode kelimeDugumleri = kok.path("words");
            if (!kelimeDugumleri.isArray() || kelimeDugumleri.isEmpty()) {
                return AlignmentResult.failed(kaynak.eserId(), kaynak.previewId(), textHash, audioHash,
                        "Alignment yanıtında kelime zamanlaması yok.", AlignmentHata.parse());
            }

            List<AlignmentWord> kelimeler = new ArrayList<>();
            int idx = 0;
            double maxBitis = 0;
            for (JsonNode w : kelimeDugumleri) {
                String text = w.path("text").asText("").trim();
                if (text.isBlank()) {
                    continue;
                }
                double bas = w.path("start").asDouble(0);
                double bit = w.path("end").asDouble(bas);
                kelimeler.add(new AlignmentWord(idx++, text, bas, bit));
                maxBitis = Math.max(maxBitis, bit);
            }
            if (kelimeler.isEmpty()) {
                return AlignmentResult.failed(kaynak.eserId(), kaynak.previewId(), textHash, audioHash,
                        "Alignment kelimeleri boş.", AlignmentHata.parse());
            }

            boolean karakterVar = kok.path("characters").isArray() && !kok.path("characters").isEmpty();
            List<AlignmentSegment> segmentler = segmentlereBol(kaynak.metin(), kelimeler);
            if (segmentler.isEmpty()) {
                segmentler = List.of(new AlignmentSegment(0,
                        kaynak.metin().trim(), kelimeler.getFirst().startSeconds(),
                        kelimeler.getLast().endSeconds(), kelimeler));
            }

            String now = OffsetDateTime.now().toString();
            String safeRequestId = guvenliRequestId(requestId);
            List<String> uyarilar = new ArrayList<>();
            uyarilar.add("Gerçek ElevenLabs forced alignment.");
            if (kok.has("loss")) {
                uyarilar.add("Ortalama alignment loss: " + kok.path("loss").asDouble());
            }

            return new AlignmentResult(
                    kaynak.eserId(),
                    kaynak.previewId(),
                    AlignmentPlan.STATUS_COMPLETED,
                    AlignmentResult.SOURCE_ELEVENLABS,
                    "tr",
                    textHash,
                    audioHash,
                    maxBitis,
                    kelimeler.size(),
                    segmentler.size(),
                    karakterVar,
                    true,
                    segmentler,
                    uyarilar,
                    AlignmentResult.SOURCE_ELEVENLABS,
                    AlignmentPlan.STATUS_COMPLETED,
                    AlignmentGuvenlikService.guvenliDosyaAdi(kaynak.eserId(), "alignment.json"),
                    AlignmentGuvenlikService.guvenliDosyaAdi(kaynak.eserId(), "subtitles.srt"),
                    AlignmentGuvenlikService.guvenliDosyaAdi(kaynak.eserId(), "subtitles.vtt"),
                    false,
                    true,
                    AlignmentResult.SOURCE_ELEVENLABS,
                    safeRequestId,
                    now,
                    now);
        } catch (Exception e) {
            return AlignmentResult.failed(kaynak.eserId(), kaynak.previewId(), textHash, audioHash,
                    "Alignment yanıtı parse edilemedi.", AlignmentHata.parse());
        }
    }

    private static String guvenliRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return "";
        }
        String temiz = requestId.trim();
        if (temiz.length() > 64) {
            return temiz.substring(0, 64);
        }
        if (!temiz.matches("^[A-Za-z0-9._-]+$")) {
            return "req-" + Math.abs(temiz.hashCode());
        }
        return temiz;
    }

    static List<AlignmentSegment> segmentlereBol(String metin, List<AlignmentWord> kelimeler) {
        List<String> cumleler = new ArrayList<>();
        Matcher m = CUMLE.matcher(metin.replaceAll("\\s+", " ").trim());
        while (m.find()) {
            String parca = m.group().trim();
            if (!parca.isBlank()) {
                cumleler.add(parca);
            }
        }
        if (cumleler.isEmpty()) {
            cumleler.add(metin.trim());
        }

        List<AlignmentSegment> segmentler = new ArrayList<>();
        int wordIdx = 0;
        int segIdx = 0;
        for (String cumle : cumleler) {
            int kelimeSayisi = cumle.split("\\s+").length;
            if (wordIdx >= kelimeler.size()) {
                break;
            }
            int son = Math.min(kelimeler.size(), wordIdx + kelimeSayisi);
            List<AlignmentWord> segKelimeler = new ArrayList<>(kelimeler.subList(wordIdx, son));
            if (segKelimeler.isEmpty()) {
                break;
            }
            segmentler.add(new AlignmentSegment(
                    segIdx++,
                    cumle,
                    segKelimeler.getFirst().startSeconds(),
                    segKelimeler.getLast().endSeconds(),
                    segKelimeler));
            wordIdx = son;
        }
        return segmentler;
    }
}
