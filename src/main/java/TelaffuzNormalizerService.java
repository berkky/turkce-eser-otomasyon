import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aktif telaffuz notlarını TTS metnine uygular (METIN_NORMALIZE modu).
 */
public final class TelaffuzNormalizerService {
    private TelaffuzNormalizerService() {
    }

    public record Sonuc(String metin, List<String> uygulananNotlar, List<String> uyarilar) {
    }

    public static Sonuc uygula(String temelMetin, List<TelaffuzNotu> notlar) {
        if (temelMetin == null || temelMetin.isBlank()) {
            return new Sonuc("", List.of(), List.of());
        }
        String sonuc = temelMetin;
        List<String> uygulanan = new ArrayList<>();
        List<String> uyarilar = new ArrayList<>();
        Set<String> uygulananId = new LinkedHashSet<>();

        List<TelaffuzNotu> aktif = notlar.stream()
                .filter(TelaffuzNotu::aktifMetinNormalize)
                .filter(n -> n.ifade() != null && !n.ifade().isBlank())
                .filter(n -> n.onerilenOkunus() != null && !n.onerilenOkunus().isBlank())
                .sorted((a, b) -> Integer.compare(b.ifade().length(), a.ifade().length()))
                .toList();

        for (TelaffuzNotu not : aktif) {
            if (uygulananId.contains(not.id())) {
                continue;
            }
            String yeni = kelimeSinirliDegistir(sonuc, not.ifade().trim(), not.onerilenOkunus().trim());
            if (!yeni.equals(sonuc)) {
                sonuc = yeni;
                uygulanan.add(not.id() + ":" + not.ifade());
                uygulananId.add(not.id());
            }
        }

        if (sonuc.length() > temelMetin.length() * 2) {
            uyarilar.add("Telaffuz uygulaması metni aşırı uzattı; kontrol önerilir.");
        }
        return new Sonuc(sonuc.trim(), uygulanan, uyarilar);
    }

    private static String kelimeSinirliDegistir(String metin, String ifade, String okunus) {
        String escaped = Pattern.quote(ifade);
        Pattern p = Pattern.compile("(?i)(?<![\\p{L}\\p{N}])" + escaped + "(?![\\p{L}\\p{N}])");
        Matcher m = p.matcher(metin);
        StringBuffer sb = new StringBuffer();
        boolean degisti = false;
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(okunus));
            degisti = true;
        }
        if (!degisti) {
            return metin;
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String revisionHash(List<TelaffuzNotu> notlar) {
        StringBuilder sb = new StringBuilder();
        notlar.stream()
                .filter(TelaffuzNotu::aktifMetinNormalize)
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .forEach(n -> sb.append(n.id()).append('|')
                        .append(n.ifade().toLowerCase(Locale.ROOT)).append('|')
                        .append(n.onerilenOkunus()).append(';'));
        return Integer.toHexString(sb.toString().hashCode());
    }
}
