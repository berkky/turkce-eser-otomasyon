/**
 * CLI: ElevenLabs durum özeti (API anahtarı değeri asla yazdırılmaz).
 */
public final class ElevenLabsDurumApp {
    public static void main(String[] args) {
        Utf8Konsol.etkinlestir();
        System.out.println("========================================");
        System.out.println("ELEVENLABS TTS DURUMU");
        System.out.println("========================================");
        System.out.println();
        System.out.println(ElevenLabsFabrika.durumOzeti().konsolOzeti());
        System.out.println();
        System.out.println(ElevenLabsModelPolitikasi.politikaOzeti());
    }
}
