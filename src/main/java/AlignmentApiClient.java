/**
 * ElevenLabs Forced Alignment API istemcisi — varsayılan kapalı.
 * Gerçek çağrı yalnızca açık onay ve kredi ile yapılır.
 */
public final class AlignmentApiClient {
    private AlignmentApiClient() {
    }

    public static AlignmentResult uret(AlignmentStorageService.PreviewKaynaklari kaynak,
                                     String textHash,
                                     String audioHash,
                                     double audioDurationSeconds) {
        if (ElevenLabsFabrika.mockModAktif()) {
            throw new IllegalStateException("Mock mod aktif — gerçek alignment API çağrılamaz.");
        }
        var ozet = ElevenLabsFabrika.durumOzeti();
        if (!ozet.hazir() || ozet.kalanKredi() <= 0) {
            throw new IllegalStateException("ElevenLabs kredisi yok — gerçek alignment yapılamaz.");
        }
        // Adım 29: API yüzeyi hazır; canlı endpoint Adım 30'da bağlanacak.
        throw new IllegalStateException(
                "ElevenLabs forced alignment API bu sürümde henüz bağlı değil. -Mock kullanın.");
    }
}
