/**
 * Alignment API hatası — kullanıcıya yalnızca güvenli mesaj taşır.
 */
public final class AlignmentApiException extends Exception {
    private final AlignmentHata hata;

    public AlignmentApiException(AlignmentHata hata) {
        super(hata.kullaniciMesaji());
        this.hata = hata;
    }

    public AlignmentHata hata() {
        return hata;
    }
}
