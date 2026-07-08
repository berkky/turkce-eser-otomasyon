import java.nio.file.Path;

public interface TtsSaglayici {
    String kimlik();
    String ad();
    String model();
    String ses();
    Hazirlik hazirlik();
    TtsUretimSonucu uret(TtsUretimIstegi istek, Path ciktiKlasoru) throws Exception;

    record Hazirlik(boolean hazir, String mesaj) {
        public static Hazirlik hazir(String mesaj) {
            return new Hazirlik(true, mesaj);
        }

        public static Hazirlik degil(String mesaj) {
            return new Hazirlik(false, mesaj);
        }
    }
}
