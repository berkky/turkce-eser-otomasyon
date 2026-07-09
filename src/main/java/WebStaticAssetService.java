import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Statik web varlıkları ve izinli dosya sunumu.
 */
public final class WebStaticAssetService {
    private WebStaticAssetService() {
    }

    public static WebResponse classpathAsset(String yol) {
        String kaynak = "/web/" + yol;
        try (InputStream in = WebStaticAssetService.class.getResourceAsStream(kaynak)) {
            if (in == null) {
                return WebResponse.notFound("Varlık bulunamadı");
            }
            byte[] veri = in.readAllBytes();
            String tip = yol.endsWith(".css") ? "text/css; charset=UTF-8"
                    : yol.endsWith(".js") ? "application/javascript; charset=UTF-8"
                    : "application/octet-stream";
            return WebResponse.binary(200, tip, veri);
        } catch (Exception e) {
            return WebResponse.text(500, "Varlık okunamadı");
        }
    }

    public static WebResponse dosyaSun(Path kok, Path dosya) throws Exception {
        if (!WebGuvenlikService.guvenliAltDosya(kok, dosya) || !Files.isRegularFile(dosya)) {
            return WebResponse.notFound("Dosya bulunamadı");
        }
        if (!WebGuvenlikService.uzantiIzinli(dosya)) {
            return WebResponse.forbidden("Dosya türü izinli değil");
        }
        long boyut = Files.size(dosya);
        if (boyut == 0) {
            return WebResponse.notFound("Boş dosya");
        }
        byte[] veri = Files.readAllBytes(dosya);
        return WebResponse.binary(200, WebGuvenlikService.icerikTipi(dosya), veri);
    }
}
