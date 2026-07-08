import java.util.regex.Pattern;

/**
 * ElevenLabs önizleme ve üretim öncesi basit Türkçe konuşma metni temizliği.
 */
public final class TurkishSpeechTextNormalizer {
    private static final Pattern COKLU_BOSLUK = Pattern.compile("\\s+");
    private static final Pattern KONTROL_KARAKTER = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]+");
    private static final Pattern SAYFA_NUMARASI = Pattern.compile(
            "(?m)^\\s*(?:sayfa|s\\.?|pg\\.?)\\s*\\d+\\s*[-–—]?\\s*\\d*\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern OCR_SATIRI = Pattern.compile(
            "(?m)^\\s*(?:\\d{1,4}\\s+){2,}\\S.*$|(?m)^\\s*[-_=]{3,}\\s*$"
    );
    private static final int MAKS_PARAGRAF_KARAKTER = 1_200;

    private TurkishSpeechTextNormalizer() {
    }

    public static String normalize(String metin) {
        if (metin == null || metin.isBlank()) {
            return "";
        }

        String temiz = metin.replace('\r', '\n');
        temiz = KONTROL_KARAKTER.matcher(temiz).replaceAll("");
        temiz = SAYFA_NUMARASI.matcher(temiz).replaceAll("");
        temiz = OCR_SATIRI.matcher(temiz).replaceAll("");
        temiz = baslikTekrarlariniAzalt(temiz);
        temiz = uzunParagraflariBol(temiz);
        temiz = COKLU_BOSLUK.matcher(temiz).replaceAll(" ");
        return temiz.trim();
    }

    private static String baslikTekrarlariniAzalt(String metin) {
        String[] satirlar = metin.split("\\n");
        StringBuilder sonuc = new StringBuilder();
        String onceki = null;
        for (String satir : satirlar) {
            String kisa = satir.trim();
            if (kisa.isBlank()) {
                if (!sonuc.isEmpty() && sonuc.charAt(sonuc.length() - 1) != '\n') {
                    sonuc.append('\n');
                }
                onceki = null;
                continue;
            }
            if (onceki != null && onceki.equalsIgnoreCase(kisa)) {
                continue;
            }
            if (sonuc.length() > 0 && sonuc.charAt(sonuc.length() - 1) != '\n') {
                sonuc.append('\n');
            }
            sonuc.append(kisa);
            onceki = kisa;
        }
        String birlestir = sonuc.toString();
        TekrarYardimci tekrar = new TekrarYardimci();
        if (tekrar.matchesBasit(birlestir)) {
            return tekrar.tekSatir(birlestir);
        }
        return birlestir;
    }

    private static String uzunParagraflariBol(String metin) {
        String[] paragraflar = metin.split("\\n{2,}");
        StringBuilder sonuc = new StringBuilder();
        for (String paragraf : paragraflar) {
            String kisa = paragraf.trim();
            if (kisa.isEmpty()) {
                continue;
            }
            if (kisa.length() <= MAKS_PARAGRAF_KARAKTER) {
                sonuc.append(kisa).append("\n\n");
                continue;
            }
            int baslangic = 0;
            while (baslangic < kisa.length()) {
                int bitis = Math.min(kisa.length(), baslangic + MAKS_PARAGRAF_KARAKTER);
                if (bitis < kisa.length()) {
                    int nokta = kisa.lastIndexOf('.', bitis);
                    int soru = kisa.lastIndexOf('?', bitis);
                    int unlem = kisa.lastIndexOf('!', bitis);
                    int cumleSonu = Math.max(nokta, Math.max(soru, unlem));
                    if (cumleSonu > baslangic + 200) {
                        bitis = cumleSonu + 1;
                    }
                }
                sonuc.append(kisa, baslangic, bitis);
                sonuc.append("\n\n");
                baslangic = bitis;
            }
        }
        return sonuc.toString().trim();
    }

    private static final class TekrarYardimci {
        boolean matchesBasit(String metin) {
            String tek = metin.replaceAll("\\s+", " ").trim();
            if (tek.length() < 6 || tek.length() % 2 != 0) {
                return false;
            }
            int yarim = tek.length() / 2;
            return tek.substring(0, yarim).equalsIgnoreCase(tek.substring(yarim));
        }

        String tekSatir(String metin) {
            String tek = metin.replaceAll("\\s+", " ").trim();
            return tek.substring(0, tek.length() / 2).trim();
        }
    }
}
