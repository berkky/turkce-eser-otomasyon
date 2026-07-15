import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class TtsAbApp {
    private TtsAbApp() { }

    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        List<String> arguments = Arrays.asList(args);
        WebOrtam environment = WebOrtam.varsayilan();
        TtsAbService service = new TtsAbService(environment);
        if (arguments.contains("--status") || arguments.isEmpty()) {
            System.out.println("A/B çıktı kökü: " + service.root());
            for (TtsAbCandidate candidate : service.registry()) {
                System.out.printf("%-12s %-24s %-28s %s%n", candidate.providerCode(),
                        candidate.modelName(), candidate.voiceId(), candidate.configurationStatus());
            }
            System.out.println("Canlı mod varsayılan kapalıdır; hiçbir API çağrısı yapılmadı.");
            return;
        }

        long seed = longArg(arguments, "--seed", System.currentTimeMillis());
        if (arguments.contains("--mock")) {
            TtsAbSourceText source = arguments.contains("--local-source")
                    ? service.selectSourceCandidate() : service.fixtureSource(true);
            Path output = service.createMockExperiment(source, seed);
            Path publicPackage = service.createPublicPackage(output);
            System.out.println("MOCK A/B paketi hazır: " + output);
            System.out.println("PUBLIC kör test paketi hazır: " + publicPackage);
            if (source.sourceType() == TtsAbSourceType.FIXTURE) {
                System.out.println("UYARI: Fixture kaynak ve sentetik mock sesler kalite kanıtı değildir.");
            }
            return;
        }

        if (!arguments.contains("--live")) {
            throw new IllegalArgumentException("--status, --mock veya --live seçilmelidir.");
        }
        if (!arguments.contains("--gercek-api-onayli")) {
            throw new IllegalStateException("Canlı çağrı için --gercek-api-onayli zorunludur.");
        }
        String voice = stringArg(arguments, "--voice", "");
        if (!XaiTtsSaglayici.CONFIGURED_VOICE_CANDIDATES.contains(voice.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("--voice yalnızca lumen, ursa veya sal olabilir.");
        }
        TtsAbSourceText preview = service.selectSourceCandidate();
        System.out.println("\n--- ONAYLANACAK TEST METNİ ---\n" + preview.text());
        System.out.println("\nKarakter: " + preview.sourceCharacterCount()
                + " | Kelime: " + preview.sourceWordCount()
                + " | SHA-256: " + preview.sourceTextHash());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            System.out.print("Metni sabitlemek için METNI ONAYLIYORUM yaz: ");
            if (!"METNI ONAYLIYORUM".equals(reader.readLine())) {
                System.out.println("Metin onaylanmadı; canlı çağrı yapılmadı.");
                return;
            }
            System.out.print("Ücretli tek xAI çağrısı için UCRETLI XAI CAGRISINI ONAYLIYORUM yaz: ");
            if (!"UCRETLI XAI CAGRISINI ONAYLIYORUM".equals(reader.readLine())) {
                System.out.println("Ücretli çağrı onaylanmadı.");
                return;
            }
        }
        TtsAbSourceText approved = new TtsAbSourceText(preview.eserId(), preview.sourceType(), preview.text(),
                preview.sourceTextHash(), preview.sourceCharacterCount(), preview.sourceWordCount(),
                preview.sourceLicenseNote(), true);
        TtsAbCandidate selected = new TtsAbCandidate("xai", "xai-tts", voice, "tr", "", List.of(), "CONFIGURED");
        TtsAbExperiment experiment = service.newExperiment(approved, TtsAbExperimentMode.RAW_BASELINE,
                List.of(selected), seed, new BigDecimal(stringArg(arguments, "--budget-usd", "1.00")), false);
        Path output = service.runSelectedXai(experiment, true);
        System.out.println("Canlı tek-aday deney çıktısı: " + output);
    }

    private static String stringArg(List<String> args, String name, String defaultValue) {
        int index = args.indexOf(name);
        return index >= 0 && index + 1 < args.size() ? args.get(index + 1) : defaultValue;
    }

    private static long longArg(List<String> args, String name, long defaultValue) {
        try { return Long.parseLong(stringArg(args, name, Long.toString(defaultValue))); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(name + " sayı olmalıdır."); }
    }
}
