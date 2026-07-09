import com.sun.net.httpserver.HttpExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Localhost kısıtı, path traversal ve içerik güvenliği.
 */
public final class WebGuvenlikService {
    private static final Set<String> IZINLI_UZANTILAR = Set.of(
            "html", "css", "js", "json", "csv", "md", "txt", "mp3", "m4b", "wav", "pdf"
    );
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|xi-api-key|sk-[a-z0-9]{10,}|ELEVENLABS_API_KEY\\s*[=:]\\s*\\S+)"
    );

    private WebGuvenlikService() {
    }

    public static boolean localhostMu(HttpExchange exchange) {
        InetSocketAddress remote = exchange.getRemoteAddress();
        if (remote == null) {
            return true;
        }
        InetAddress addr = remote.getAddress();
        if (addr == null) {
            return "127.0.0.1".equals(remote.getHostString()) || "::1".equals(remote.getHostString());
        }
        return addr.isLoopbackAddress();
    }

    public static void localhostZorunlu(HttpExchange exchange) {
        if (!localhostMu(exchange)) {
            throw new GuvenlikIstisnasi("Yalnızca localhost erişimine izin verilir.");
        }
    }

    public static boolean guvenliAltDosya(Path kok, Path hedef) {
        if (kok == null || hedef == null) {
            return false;
        }
        Path normKok = kok.toAbsolutePath().normalize();
        Path normHedef = hedef.toAbsolutePath().normalize();
        return normHedef.startsWith(normKok);
    }

    public static boolean uzantiIzinli(Path dosya) {
        String ad = dosya.getFileName().toString();
        int nokta = ad.lastIndexOf('.');
        if (nokta < 0) {
            return false;
        }
        return IZINLI_UZANTILAR.contains(ad.substring(nokta + 1).toLowerCase(Locale.ROOT));
    }

    public static String htmlKacis(String metin) {
        if (metin == null) {
            return "";
        }
        return metin.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static String yolMaskele(Path yol) {
        if (yol == null) {
            return "";
        }
        String mutlak = yol.toAbsolutePath().normalize().toString().replace('\\', '/');
        int idx = mutlak.toLowerCase(Locale.ROOT).indexOf("/eser-");
        if (idx >= 0) {
            return "…" + mutlak.substring(idx);
        }
        Path dosya = yol.getFileName();
        return dosya == null ? "…" : "…/" + dosya;
    }

    public static String envDurumu(String ad) {
        String deger = System.getenv(ad);
        return deger == null || deger.isBlank() ? "YOK" : "TANIMLI";
    }

    public static boolean apiKeySizintisiVar(String metin) {
        if (metin == null || metin.isBlank()) {
            return false;
        }
        if (API_KEY_PATTERN.matcher(metin).find()) {
            return true;
        }
        for (String env : new String[]{"ELEVENLABS_API_KEY", "OPENAI_API_KEY", "GEMINI_API_KEY", "AZURE_SPEECH_KEY"}) {
            String deger = System.getenv(env);
            if (deger != null && !deger.isBlank() && metin.contains(deger)) {
                return true;
            }
        }
        return false;
    }

    public static String guvenliJson(String json) {
        if (apiKeySizintisiVar(json)) {
            throw new GuvenlikIstisnasi("JSON çıktısında hassas veri tespit edildi.");
        }
        return json;
    }

    public static String icerikTipi(Path dosya) {
        String ad = dosya.getFileName().toString().toLowerCase(Locale.ROOT);
        if (ad.endsWith(".mp3")) return "audio/mpeg";
        if (ad.endsWith(".m4b")) return "audio/mp4";
        if (ad.endsWith(".wav")) return "audio/wav";
        if (ad.endsWith(".pdf")) return "application/pdf";
        if (ad.endsWith(".css")) return "text/css; charset=UTF-8";
        if (ad.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (ad.endsWith(".json")) return "application/json; charset=UTF-8";
        if (ad.endsWith(".html")) return "text/html; charset=UTF-8";
        return "text/plain; charset=UTF-8";
    }

    public static final class GuvenlikIstisnasi extends RuntimeException {
        public GuvenlikIstisnasi(String mesaj) {
            super(mesaj);
        }
    }
}
