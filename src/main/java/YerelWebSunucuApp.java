import java.awt.Desktop;
import java.net.URI;

/**
 * Yerel web MVP kontrol paneli giriş noktası.
 */
public final class YerelWebSunucuApp {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        int port = WebOrtam.PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException ignored) {
            }
        }

        WebOrtam ortam = WebOrtam.varsayilan();
        YerelWebSunucu sunucu = new YerelWebSunucu(ortam);

        try {
            sunucu.baslat(port);
        } catch (java.net.BindException e) {
            System.err.println("Port " + port + " kullanımda. Başka bir port deneyin veya mevcut sunucuyu kapatın.");
            System.exit(2);
        }

        String url = "http://" + WebOrtam.HOST + ":" + sunucu.port() + "/";
        System.out.println("========================================");
        System.out.println("YEREL WEB MVP KONTROL PANELİ");
        System.out.println("========================================");
        System.out.println("URL: " + url);
        System.out.println("Yalnızca localhost — dış erişim kapalı");
        System.out.println("API anahtarları gösterilmez");
        System.out.println("Tam eser üretimi bu panelden başlatılamaz");
        System.out.println("Durdurmak için Ctrl+C");
        System.out.println();

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {
            System.out.println("Tarayıcı otomatik açılamadı; URL'yi elle açın.");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(sunucu::durdur));
        Thread.currentThread().join();
    }
}
