import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TtsAbAudioService {
    private static final Duration TIMEOUT = Duration.ofMinutes(10);
    private static final Pattern LUFS = Pattern.compile("(?m)^\\s*I:\\s*(-?[0-9.]+)\\s*LUFS");
    private static final Pattern PEAK = Pattern.compile("(?m)^\\s*Peak:\\s*(-?[0-9.]+)\\s*dBFS");
    private final Path project;
    private final FfmpegClient ffmpeg;
    private final ObjectMapper json = new ObjectMapper();

    public TtsAbAudioService(Path project) {
        this.project = project.toAbsolutePath().normalize();
        this.ffmpeg = new FfmpegClient(project);
    }

    public boolean available() {
        return ffmpeg.kontrolEt().hazir() && commandWorks(ffmpeg.ffprobeKomutu());
    }

    public TtsAbAudioMetrics probe(Path audio) throws Exception {
        if (!Files.isRegularFile(audio) || Files.size(audio) == 0) {
            throw new IOException("Ses dosyası yok veya 0 byte: " + audio);
        }
        CommandResult result = run(List.of(
                ffmpeg.ffprobeKomutu(), "-v", "error",
                "-show_entries", "format=duration,size,bit_rate:stream=codec_name,sample_rate,channels,bit_rate",
                "-select_streams", "a:0", "-of", "json", audio.toAbsolutePath().toString()), Duration.ofSeconds(60));
        if (result.exitCode() != 0) {
            throw new IOException("FFprobe sesi doğrulayamadı: " + trim(result.output(), 900));
        }
        JsonNode root = json.readTree(result.output());
        JsonNode stream = root.path("streams").path(0);
        JsonNode format = root.path("format");
        if (stream.isMissingNode() || stream.path("codec_name").asText().isBlank()) {
            throw new IOException("Oynatılabilir ses akışı bulunamadı.");
        }
        long durationMs = Math.round(parseDouble(format.path("duration").asText("0")) * 1000.0);
        if (durationMs <= 0) throw new IOException("Ses süresi geçersiz.");
        Loudness loudness = loudness(audio);
        return new TtsAbAudioMetrics(
                durationMs,
                Files.size(audio),
                stream.path("codec_name").asText(),
                stream.path("sample_rate").asInt(),
                stream.path("channels").asInt(),
                firstLong(stream.path("bit_rate").asText(), format.path("bit_rate").asText()),
                loudness.lufs(),
                loudness.truePeak(),
                XaiTtsSaglayici.sha256(audio));
    }

    public TtsAbAudioMetrics normalize(Path source, Path target) throws Exception {
        probe(source);
        Path script = project.resolve("tts-ab-normalize.ps1");
        if (!Files.isRegularFile(script)) throw new IOException("Normalizasyon scripti bulunamadı: " + script);
        Files.createDirectories(target.toAbsolutePath().getParent());
        CommandResult result = run(List.of(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", script.toString(),
                "-InputPath", source.toAbsolutePath().toString(),
                "-OutputPath", target.toAbsolutePath().toString()), TIMEOUT);
        if (result.exitCode() != 0) {
            Files.deleteIfExists(target);
            throw new IOException("A/B normalizasyonu başarısız: " + trim(result.output(), 1_500));
        }
        TtsAbAudioMetrics metrics = probe(target);
        if (metrics.channels() != 1 || metrics.sampleRate() != 44_100
                || !"mp3".equalsIgnoreCase(metrics.codec())) {
            Files.deleteIfExists(target);
            throw new IOException("Normalize ses hedef formatta değil.");
        }
        if (metrics.truePeakDbtp() != null && metrics.truePeakDbtp() > -0.9) {
            Files.deleteIfExists(target);
            throw new IOException("Normalize ses true peak sınırını aşıyor: " + metrics.truePeakDbtp());
        }
        return metrics;
    }

    private Loudness loudness(Path audio) {
        try {
            String nullSink = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                    ? "NUL" : "/dev/null";
            CommandResult result = run(List.of(ffmpeg.ffmpegKomutu(), "-hide_banner", "-nostats",
                    "-i", audio.toAbsolutePath().toString(), "-af", "ebur128=peak=true",
                    "-f", "null", nullSink), Duration.ofMinutes(3));
            Matcher lufs = LUFS.matcher(result.output());
            Matcher peak = PEAK.matcher(result.output());
            Double lufsValue = null;
            Double peakValue = null;
            while (lufs.find()) lufsValue = parseDouble(lufs.group(1));
            while (peak.find()) peakValue = parseDouble(peak.group(1));
            return new Loudness(lufsValue, peakValue);
        } catch (Exception ignored) {
            return new Loudness(null, null);
        }
    }

    private boolean commandWorks(String command) {
        try {
            return run(List.of(command, "-version"), Duration.ofSeconds(30)).exitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static CommandResult run(List<String> command, Duration timeout) throws Exception {
        Path output = Files.createTempFile("tts-ab-command-", ".log");
        try {
            ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
            builder.redirectErrorStream(true);
            builder.redirectOutput(output.toFile());
            Process process = builder.start();
            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Komut zaman aşımına uğradı.");
            }
            return new CommandResult(process.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static long firstLong(String first, String second) {
        try { return Long.parseLong(first); } catch (Exception ignored) { }
        try { return Long.parseLong(second); } catch (Exception ignored) { return 0L; }
    }

    private static double parseDouble(String value) {
        try { return Double.parseDouble(value.replace(',', '.')); }
        catch (Exception e) { return 0.0; }
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }

    private record Loudness(Double lufs, Double truePeak) { }
    private record CommandResult(int exitCode, String output) { }
}
