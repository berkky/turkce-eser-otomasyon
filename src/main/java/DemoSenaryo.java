/**
 * Demo senaryosu — ESER-00005 ve ESER-00006 rolleri.
 */
public record DemoSenaryo(
        int eserId,
        String eserAdi,
        String demoRolu,
        String kaynakTipi,
        String onemAciklamasi,
        boolean buyukEser
) {
    public static DemoSenaryo kasagi() {
        return new DemoSenaryo(
                SesKaliteOlcutleri.KASAGI_ESER_ID,
                "Kaşağı - Vikikaynak",
                "Kısa eser — ElevenLabs önizleme ve kalite karşılaştırması için ideal örnek",
                "Vikikaynak web sayfası",
                "Hızlı ElevenLabs önizleme döngüsünü patrona göstermek için kullanılır. Düşük maliyetli test senaryosu.",
                false
        );
    }

    public static DemoSenaryo astronomi() {
        return new DemoSenaryo(
                SesKaliteOlcutleri.ASTRONOMI_ESER_ID,
                "Astronomi Alfa Yayınları",
                "Büyük eser — Archive.org kaynağı, metadata güvenliği ve TTS parçalama testi",
                "Archive.org PDF",
                "Büyük eser koruması, maliyet riski ve metadata KONTROL_GEREKIYOR akışını göstermek için kullanılır.",
                true
        );
    }
}
