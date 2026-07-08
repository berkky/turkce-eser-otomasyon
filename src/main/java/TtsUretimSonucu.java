import java.nio.file.Path;

public record TtsUretimSonucu(String saglayiciKimligi,
                              String saglayiciAdi,
                              String model,
                              String ses,
                              String ornekId,
                              String metinTuru,
                              Path sesDosyasi,
                              int karakter,
                              long dosyaBoyutu,
                              long sureMs) {
}
