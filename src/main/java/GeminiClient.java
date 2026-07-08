import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class GeminiClient {
    private static final int MAKSIMUM_DENEME = 5;
    private static final long ILK_BEKLEME_MS = 3_000L;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("GEMINI_API_KEY bulunamadı.");
        }

        this.apiKey = apiKey.trim();
        String ortamModeli = System.getenv("GEMINI_MODEL");
        this.model = ortamModeli == null || ortamModeli.isBlank()
                ? "gemini-3.5-flash"
                : ortamModeli.trim();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String getModel() {
        return model;
    }

    public EserBilgisi analizEt(PdfHazirlayici.PdfVerisi pdfVerisi) throws Exception {
        ObjectNode govde = objectMapper.createObjectNode();
        ArrayNode parts = govde.putArray("contents")
                .addObject()
                .put("role", "user")
                .putArray("parts");

        String prompt = pdfVerisi.getBelgeTuru() + " belgesinin ilk " + pdfVerisi.getSayfaSayisi() + " " + pdfVerisi.getBirimAdi() + " içeriğini incele. " +
                "Eser adını, eser türünü, yazarını, yayınevini, basım yılını, ISBN numarasını ve dilini çıkar. " +
                "Eser türü için Kitap, Makale, Rapor, Gazete Haberi, Blog Yazısı, Tweet, Şiir, Hikâye veya Diğer değerlerinden en uygununu kullan. " +
                "Tahmin yürütme; yalnızca belgede açıkça bulunan bilgileri kullan. " +
                "Bulamadığın alanlara Bilinmiyor yaz. guven_puani alanını 0 ile 1 arasında ver. " +
                "kanit alanında bilgilerin hangi sayfa, kapak veya metin bölümünden geldiğini kısa yaz.";

        if (pdfVerisi.isGorselPdf()) {
            System.out.println("PDF taranmış/görüntü tabanlı görünüyor.");
            System.out.println("İlk " + pdfVerisi.getSayfaSayisi() + " sayfa PDF olarak Gemini'ye gönderiliyor...");

            ObjectNode inlineData = parts.addObject().putObject("inline_data");
            inlineData.put("mime_type", "application/pdf");
            inlineData.put("data", pdfVerisi.getBase64Pdf());
            parts.addObject().put("text", prompt);
        } else {
            System.out.println(pdfVerisi.getBelgeTuru() + " metin tabanlı.");
            System.out.println("İlk " + pdfVerisi.getSayfaSayisi() + " " + pdfVerisi.getBirimAdi() + " metni Gemini'ye gönderiliyor...");
            parts.addObject().put("text", prompt + "\n\nBelgeden çıkarılan metin:\n" + pdfVerisi.getMetin());
        }

        ObjectNode generationConfig = govde.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.1);
        generationConfig.put("maxOutputTokens", 500);

        ObjectNode schema = generationConfig.putObject("responseSchema");
        schema.put("type", "OBJECT");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("eser_adi").put("type", "STRING");
        properties.putObject("eser_turu").put("type", "STRING");
        properties.putObject("yazar").put("type", "STRING");
        properties.putObject("yayinevi").put("type", "STRING");
        properties.putObject("basim_yili").put("type", "STRING");
        properties.putObject("dil").put("type", "STRING");
        properties.putObject("isbn").put("type", "STRING");
        properties.putObject("guven_puani").put("type", "STRING");
        properties.putObject("kanit").put("type", "STRING");
        ArrayNode required = schema.putArray("required");
        required.add("eser_adi");
        required.add("eser_turu");
        required.add("yazar");
        required.add("yayinevi");
        required.add("basim_yili");
        required.add("dil");
        required.add("isbn");
        required.add("guven_puani");
        required.add("kanit");

        String jsonPayload = objectMapper.writeValueAsString(govde);
        String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" +
                model + ":generateContent";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = istekGonder(request);
        System.out.println("HTTP durum kodu: " + response.statusCode());

        if (response.statusCode() == 429) {
            throw new KotaDolduException("Gemini API kotası doldu: " + hataMesajiniCikar(response.body()));
        }

        if (response.statusCode() == 500 || response.statusCode() == 502 ||
                response.statusCode() == 503 || response.statusCode() == 504) {
            throw new GeciciKullanilamazException(
                    "Gemini geçici olarak kullanılamıyor (HTTP " + response.statusCode() + "): " +
                            hataMesajiniCikar(response.body())
            );
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Gemini API hatası (HTTP " + response.statusCode() + "): " + hataMesajiniCikar(response.body())
            );
        }

        JsonNode anaCevap = objectMapper.readTree(response.body());
        String metin = anaCevap.path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text")
                .asText();

        if (metin == null || metin.isBlank()) {
            throw new IllegalStateException("Gemini boş veya beklenmeyen biçimde cevap döndürdü.");
        }

        metin = metin.replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        EserBilgisi bilgi = objectMapper.readValue(metin, EserBilgisi.class);
        bilgi.bilinmeyenleriDuzelt();
        return bilgi;
    }

    private HttpResponse<String> istekGonder(HttpRequest request) throws Exception {
        long bekleme = ILK_BEKLEME_MS;
        HttpResponse<String> sonCevap = null;

        for (int deneme = 1; deneme <= MAKSIMUM_DENEME; deneme++) {
            System.out.println("Gemini isteği gönderiliyor. Deneme: " + deneme + "/" + MAKSIMUM_DENEME);
            sonCevap = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int kod = sonCevap.statusCode();
            if (kod != 500 && kod != 502 && kod != 503 && kod != 504) {
                return sonCevap;
            }

            if (deneme == MAKSIMUM_DENEME) {
                return sonCevap;
            }

            System.out.println("Gemini geçici olarak kullanılamıyor. " +
                    (bekleme / 1000) + " saniye sonra tekrar denenecek...");
            Thread.sleep(bekleme);
            bekleme *= 2;
        }

        return sonCevap;
    }

    private String hataMesajiniCikar(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String mesaj = node.path("error").path("message").asText();
            return mesaj == null || mesaj.isBlank() ? body : mesaj;
        } catch (Exception ignored) {
            return body;
        }
    }

    public static class KotaDolduException extends Exception {
        public KotaDolduException(String mesaj) {
            super(mesaj);
        }
    }

    public static class GeciciKullanilamazException extends Exception {
        public GeciciKullanilamazException(String mesaj) {
            super(mesaj);
        }
    }
}
