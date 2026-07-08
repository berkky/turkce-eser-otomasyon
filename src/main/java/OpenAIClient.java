import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class OpenAIClient {
    private static final int MAKSIMUM_DENEME = 5;
    private static final long ILK_BEKLEME_MS = 3_000L;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAIClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("OPENAI_API_KEY bulunamadı.");
        }

        this.apiKey = apiKey.trim();

        String ortamModeli = System.getenv("OPENAI_MODEL");
        this.model = ortamModeli == null || ortamModeli.isBlank()
                ? "gpt-5.4-mini"
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
        govde.put("model", model);
        govde.put("store", false);
        govde.put("max_output_tokens", 1_000);

        ArrayNode content = govde.putArray("input")
                .addObject()
                .put("role", "user")
                .putArray("content");

        String prompt = pdfVerisi.getBelgeTuru() + " belgesinin ilk " + pdfVerisi.getSayfaSayisi() + " " + pdfVerisi.getBirimAdi() + " içeriğini incele. " +
                "Eser adını, eser türünü, yazarını, yayınevini, basım yılını, ISBN numarasını ve dilini çıkar. " +
                "Eser türü için Kitap, Makale, Rapor, Gazete Haberi, Blog Yazısı, Tweet, Şiir, Hikâye veya Diğer değerlerinden en uygununu kullan. " +
                "Tahmin yürütme; yalnızca belgede açıkça bulunan bilgileri kullan. " +
                "Bulamadığın alanlara Bilinmiyor yaz. guven_puani alanını 0 ile 1 arasında ver. " +
                "kanit alanında bilgilerin hangi sayfa, kapak veya metin bölümünden geldiğini kısa yaz.";

        if (pdfVerisi.isGorselPdf()) {
            System.out.println("PDF taranmış/görüntü tabanlı görünüyor.");
            System.out.println("İlk " + pdfVerisi.getSayfaSayisi() +
                    " sayfa PDF olarak OpenAI'ye gönderiliyor...");

            content.addObject()
                    .put("type", "input_file")
                    .put("filename", "ilk-sayfalar.pdf")
                    .put("file_data", "data:application/pdf;base64," + pdfVerisi.getBase64Pdf());

            content.addObject()
                    .put("type", "input_text")
                    .put("text", prompt);
        } else {
            System.out.println(pdfVerisi.getBelgeTuru() + " metin tabanlı.");
            System.out.println("İlk " + pdfVerisi.getSayfaSayisi() + " " + pdfVerisi.getBirimAdi() +
                    " metni OpenAI'ye gönderiliyor...");

            content.addObject()
                    .put("type", "input_text")
                    .put("text", prompt + "\n\nBelgeden çıkarılan metin:\n" + pdfVerisi.getMetin());
        }

        ObjectNode format = govde.putObject("text")
                .putObject("format");

        format.put("type", "json_schema");
        format.put("name", "eser_bilgisi");
        format.put("strict", true);

        ObjectNode schema = format.putObject("schema");
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode properties = schema.putObject("properties");
        properties.putObject("eser_adi").put("type", "string");
        properties.putObject("eser_turu").put("type", "string");
        properties.putObject("yazar").put("type", "string");
        properties.putObject("yayinevi").put("type", "string");
        properties.putObject("basim_yili").put("type", "string");
        properties.putObject("dil").put("type", "string");
        properties.putObject("isbn").put("type", "string");
        properties.putObject("guven_puani").put("type", "string");
        properties.putObject("kanit").put("type", "string");

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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/responses"))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = istekGonder(request);
        System.out.println("OpenAI HTTP durum kodu: " + response.statusCode());

        if (response.statusCode() == 429) {
            throw new KotaDolduException(
                    "OpenAI API kotası veya hız sınırı aşıldı: " + hataMesajiniCikar(response.body())
            );
        }

        if (geciciHataMi(response.statusCode())) {
            throw new GeciciKullanilamazException(
                    "OpenAI geçici olarak kullanılamıyor (HTTP " + response.statusCode() + "): " +
                            hataMesajiniCikar(response.body())
            );
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "OpenAI API hatası (HTTP " + response.statusCode() + "): " +
                            hataMesajiniCikar(response.body())
            );
        }

        JsonNode anaCevap = objectMapper.readTree(response.body());
        String metin = ciktiMetniniBul(anaCevap);

        if (metin == null || metin.isBlank()) {
            String durum = anaCevap.path("status").asText();
            if (durum == null || durum.isBlank()) {
                durum = "bilinmiyor";
            }

            throw new IllegalStateException(
                    "OpenAI boş veya beklenmeyen biçimde cevap döndürdü. Durum: " + durum
            );
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
            System.out.println("OpenAI isteği gönderiliyor. Deneme: " + deneme + "/" + MAKSIMUM_DENEME);
            sonCevap = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int kod = sonCevap.statusCode();

            if (kod == 429 && "insufficient_quota".equalsIgnoreCase(hataKodunuCikar(sonCevap.body()))) {
                return sonCevap;
            }

            if (!geciciHataMi(kod) && kod != 429) {
                return sonCevap;
            }

            if (deneme == MAKSIMUM_DENEME) {
                return sonCevap;
            }

            long kullanilacakBekleme = retryAfterMs(sonCevap, bekleme);
            System.out.println("OpenAI geçici olarak kullanılamıyor veya hız sınırında. " +
                    (kullanilacakBekleme / 1000) + " saniye sonra tekrar denenecek...");

            Thread.sleep(kullanilacakBekleme);
            bekleme *= 2;
        }

        return sonCevap;
    }

    private String ciktiMetniniBul(JsonNode anaCevap) {
        String ustDuzey = anaCevap.path("output_text").asText();
        if (ustDuzey != null && !ustDuzey.isBlank()) {
            return ustDuzey;
        }

        for (JsonNode output : anaCevap.path("output")) {
            if (!"message".equals(output.path("type").asText())) {
                continue;
            }

            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    String text = content.path("text").asText();
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
        }

        return "";
    }

    private long retryAfterMs(HttpResponse<String> response, long varsayilanMs) {
        try {
            String deger = response.headers().firstValue("retry-after").orElse("").trim();
            if (!deger.isBlank()) {
                double saniye = Double.parseDouble(deger);
                return Math.max(1_000L, (long) (saniye * 1_000L));
            }
        } catch (Exception ignored) {
        }

        return varsayilanMs;
    }

    private boolean geciciHataMi(int kod) {
        return kod == 408 || kod == 409 || kod == 500 || kod == 502 || kod == 503 || kod == 504;
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

    private String hataKodunuCikar(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.path("error").path("code").asText();
        } catch (Exception ignored) {
            return "";
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
