import java.util.List;

/**
 * Demo değer önerisi ve önce/sonra karşılaştırması.
 */
public final class DemoDegerOnerisiService {
    public static final String DEGER_ONERISI =
            "Türkçe eserleri PDF/EPUB/web kaynaklarından alıp otomatik arşivleyen, "
                    + "kataloglayan ve yapay zekâ ile seslendirmeye hazırlayan yerel üretim sistemi.";

    private DemoDegerOnerisiService() {
    }

    public record OnceSonra(List<String> once, List<String> sonra) {
    }

    public record YapildiKaldi(List<String> yapildi, List<String> kaldi) {
    }

    public static OnceSonra onceSonra() {
        return new OnceSonra(
                List.of(
                        "Dağınık PDF/EPUB dosyaları",
                        "Elle künye çıkarma",
                        "Elle dosya adı düzenleme",
                        "Elle Excel kaydı",
                        "Uzun metni elle bölme",
                        "TTS sağlayıcı maliyet/kredi riski kontrolsüz"
                ),
                List.of(
                        "Otomatik kaynak alma (web, Archive.org, dosya)",
                        "Otomatik arşivleme ve SHA-256 tekrar kontrolü",
                        "Excel katalog ve metadata JSON",
                        "TTS parçalama ve kuyruk hazırlığı",
                        "Ses kalite paneli ve sağlayıcı karşılaştırması",
                        "Web MVP kontrol paneli",
                        "Güvenlik, idempotency ve tekrar üretim koruması"
                )
        );
    }

    public static YapildiKaldi yapildiKaldi() {
        return new YapildiKaldi(
                List.of(
                        "Kaynak alma ve arşivleme pipeline",
                        "Metadata çıkarma ve güvenlik/kurtarma",
                        "Metin bölme ve TTS parçalama",
                        "Piper / Google / ElevenLabs altyapısı",
                        "Önizleme, mock mod ve kredi kontrolü",
                        "Kalite paneli ve web MVP",
                        "Telaffuz sözlüğü ve maliyet planı (Adım 28)",
                        "Mock forced alignment ve okuma takibi (Adım 29)",
                        "Gerçek forced alignment API kapısı (Adım 30)",
                        "Onaylı tam eser üretim planı ve kuyruk kapısı (Adım 31)",
                        "GitHub kayıtlı açık kaynak proje"
                ),
                List.of(
                        "Canlı ElevenLabs tam eser TTS üretimi (manuel Adım 32+)",
                        "Telaffuz sözlüğü düzenleme UI (canlı)",
                        "Gerçek ElevenLabs forced alignment API",
                        "Bulut dağıtım ve çok kullanıcı desteği",
                        "Lisans/hak yönetim modülü"
                )
        );
    }

    public static List<String> risklerVeSonraki() {
        return List.of(
                "Büyük eserlerde TTS maliyeti yüksek — önizleme zorunlu",
                "Metadata KONTROL_GEREKIYOR kayıtları insan onayı bekler",
                "Telif/lisans her eser için ayrı değerlendirilmeli",
                "Sonraki adım: onaylı manuel tam eser TTS çalıştırma (Adım 32)"
        );
    }
}
