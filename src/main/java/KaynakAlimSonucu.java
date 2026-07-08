import java.nio.file.Path;

public record KaynakAlimSonucu(
        boolean basarili,
        boolean tekrar,
        int eserId,
        String eserAdi,
        KaynakAlimTuru tur,
        Path kaynakDosyasi,
        Path metinEserKlasoru,
        Path ttsKlasoru,
        int bolumSayisi,
        int ttsParcaSayisi,
        int toplamKarakter,
        String mesaj
) {
    public static KaynakAlimSonucu tekrar(int eserId, String eserAdi, KaynakAlimTuru tur,
                                           Path kaynak, Path metinKlasoru, String mesaj) {
        return new KaynakAlimSonucu(true, true, eserId, eserAdi, tur, kaynak, metinKlasoru,
                metinKlasoru == null ? null : metinKlasoru.resolve("tts-parcalari"), 0, 0, 0, mesaj);
    }
}
