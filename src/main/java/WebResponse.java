import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP yanıt modeli.
 */
public record WebResponse(int status, String contentType, byte[] body, Map<String, String> headers) {
    public static WebResponse html(int status, String icerik) {
        return new WebResponse(status, "text/html; charset=UTF-8",
                icerik.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    public static WebResponse htmlOk(String icerik) {
        return html(200, icerik);
    }

    public static WebResponse json(int status, String icerik) {
        return new WebResponse(status, "application/json; charset=UTF-8",
                icerik.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    public static WebResponse jsonOk(String icerik) {
        return json(200, icerik);
    }

    public static WebResponse text(int status, String icerik) {
        return new WebResponse(status, "text/plain; charset=UTF-8",
                icerik.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    public static WebResponse binary(int status, String contentType, byte[] veri) {
        return new WebResponse(status, contentType, veri, Map.of());
    }

    public static WebResponse redirect(String konum) {
        return new WebResponse(302, "text/plain; charset=UTF-8", new byte[0],
                Map.of("Location", konum));
    }

    public static WebResponse notFound(String mesaj) {
        return html(404, "<h1>404</h1><p>" + WebGuvenlikService.htmlKacis(mesaj) + "</p>");
    }

    public static WebResponse forbidden(String mesaj) {
        return html(403, "<h1>403</h1><p>" + WebGuvenlikService.htmlKacis(mesaj) + "</p>");
    }

    public WebResponse withHeader(String ad, String deger) {
        Map<String, String> yeni = new LinkedHashMap<>(headers == null ? Map.of() : headers);
        yeni.put(ad, deger);
        return new WebResponse(status, contentType, body, yeni);
    }
}
