import java.util.List;

/**
 * Demo güvenlik uyarıları — simülasyon ile gerçek işlem ayrımı.
 */
public final class DemoGuvenlikService {
    public static final String GITHUB_URL = "https://github.com/berkky/turkce-eser-otomasyon";

    private DemoGuvenlikService() {
    }

    public static List<String> uyarilar() {
        return List.of(
                "Bu ekranda gerçek TTS üretimi başlatılmaz.",
                "ElevenLabs kredisi açılmadan premium ses üretimi yapılmaz.",
                "Büyük eserlerde önce önizleme ve maliyet onayı gerekir.",
                "Bu panel localhost üzerinde çalışır — dış erişim kapalıdır.",
                "Demo modu mevcut arşiv verisini salt okunur gösterir; dosya taşımaz veya silmez.",
                "Telif ve lisans kontrolü her eser için ayrı değerlendirilmelidir; sistem otomatik hak onayı vermez."
        );
    }

    public static String simulasyonNotu() {
        return "SUNUM MODU — Gösterilen akış gerçek arşiv verisini okur; riskli üretim işlemleri simüle edilmez.";
    }
}
