import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Ham PCM 16-bit little-endian sesi standart WAV kabına yazar. */
public final class WavYazici {
    private WavYazici() {
    }

    public static void pcm16MonoYaz(Path hedef, byte[] pcm, int orneklemeHizi) throws IOException {
        if (pcm == null || pcm.length == 0) {
            throw new IOException("Gemini boş PCM ses döndürdü.");
        }
        Files.createDirectories(hedef.toAbsolutePath().getParent());
        int kanal = 1;
        int bit = 16;
        int byteHizi = orneklemeHizi * kanal * bit / 8;
        int blok = kanal * bit / 8;

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(44 + pcm.length);
             DataOutputStream out = new DataOutputStream(bos)) {
            ascii(out, "RIFF");
            littleInt(out, 36 + pcm.length);
            ascii(out, "WAVE");
            ascii(out, "fmt ");
            littleInt(out, 16);
            littleShort(out, (short) 1);
            littleShort(out, (short) kanal);
            littleInt(out, orneklemeHizi);
            littleInt(out, byteHizi);
            littleShort(out, (short) blok);
            littleShort(out, (short) bit);
            ascii(out, "data");
            littleInt(out, pcm.length);
            out.write(pcm);
            Files.write(hedef, bos.toByteArray());
        }
    }

    private static void ascii(DataOutputStream out, String s) throws IOException {
        out.writeBytes(s);
    }

    private static void littleInt(DataOutputStream out, int v) throws IOException {
        out.writeByte(v & 0xff);
        out.writeByte((v >>> 8) & 0xff);
        out.writeByte((v >>> 16) & 0xff);
        out.writeByte((v >>> 24) & 0xff);
    }

    private static void littleShort(DataOutputStream out, short v) throws IOException {
        out.writeByte(v & 0xff);
        out.writeByte((v >>> 8) & 0xff);
    }
}
