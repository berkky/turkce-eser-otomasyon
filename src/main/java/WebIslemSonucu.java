/**
 * Güvenli web işlem sonucu.
 */
public record WebIslemSonucu(boolean basarili, String mesaj, String yonlendirme) {
    public static WebIslemSonucu basarili(String mesaj, String yonlendirme) {
        return new WebIslemSonucu(true, mesaj, yonlendirme);
    }

    public static WebIslemSonucu hatali(String mesaj) {
        return new WebIslemSonucu(false, mesaj, "/islemler");
    }
}
