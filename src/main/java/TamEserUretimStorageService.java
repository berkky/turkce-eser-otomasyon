import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Tam eser üretim plan/onay/kuyruk JSON depolama (.partial + rename).
 */
public final class TamEserUretimStorageService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path klasor;

    public TamEserUretimStorageService(Path kuyrukKok) {
        this.klasor = TamEserUretimGuvenlikService.uretimKlasoru(kuyrukKok);
    }

    public Path klasor() {
        return klasor;
    }

    public void planKaydet(TamEserUretimPlani plan) throws Exception {
        Path hedef = klasor.resolve(TamEserUretimGuvenlikService.guvenliDosyaAdi(plan.eserId(), "plan"));
        yaz(hedef, plan.json(JSON));
    }

    public TamEserUretimPlani planOku(int eserId) throws Exception {
        Path dosya = klasor.resolve(TamEserUretimGuvenlikService.guvenliDosyaAdi(eserId, "plan"));
        if (!Files.isRegularFile(dosya)) {
            return null;
        }
        ObjectNode n = (ObjectNode) JSON.readTree(Files.readString(dosya, StandardCharsets.UTF_8));
        List<TamEserUretimParcasi> parcalar = parcalariOku(n.path("parcalar"));
        return TamEserUretimPlani.fromJson(JSON, n, parcalar);
    }

    public boolean planVarMi(int eserId) throws Exception {
        return Files.isRegularFile(klasor.resolve(TamEserUretimGuvenlikService.guvenliDosyaAdi(eserId, "plan")));
    }

    public void onayKaydet(TamEserUretimOnayi onay) throws Exception {
        Path hedef = klasor.resolve(TamEserUretimGuvenlikService.guvenliDosyaAdi(onay.eserId(), "approval-draft"));
        yaz(hedef, onay.json(JSON));
    }

    public TamEserUretimOnayi onayOku(int eserId) throws Exception {
        Path dosya = klasor.resolve(TamEserUretimGuvenlikService.guvenliDosyaAdi(eserId, "approval-draft"));
        if (!Files.isRegularFile(dosya)) {
            return null;
        }
        ObjectNode n = (ObjectNode) JSON.readTree(Files.readString(dosya, StandardCharsets.UTF_8));
        return TamEserUretimOnayi.fromJson(JSON, n);
    }

    public void kuyrukKaydet(TamEserUretimKuyrukKaydi kayit) throws Exception {
        Path hedef = klasor.resolve(TamEserUretimGuvenlikService.guvenliDosyaAdi(kayit.eserId(), "queue"));
        yaz(hedef, kayit.json(JSON));
    }

    public TamEserUretimKuyrukKaydi kuyrukOku(int eserId) throws Exception {
        Path dosya = klasor.resolve(TamEserUretimGuvenlikService.guvenliDosyaAdi(eserId, "queue"));
        if (!Files.isRegularFile(dosya)) {
            return null;
        }
        ObjectNode n = (ObjectNode) JSON.readTree(Files.readString(dosya, StandardCharsets.UTF_8));
        return TamEserUretimKuyrukKaydi.fromJson(JSON, n);
    }

    public List<TamEserUretimKuyrukKaydi> tumKuyrukKayitlari() throws Exception {
        Files.createDirectories(klasor);
        List<TamEserUretimKuyrukKaydi> sonuc = new ArrayList<>();
        try (var s = Files.list(klasor)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                String ad = p.getFileName().toString();
                if (!ad.endsWith("-production-queue.json")) {
                    continue;
                }
                ObjectNode n = (ObjectNode) JSON.readTree(Files.readString(p, StandardCharsets.UTF_8));
                sonuc.add(TamEserUretimKuyrukKaydi.fromJson(JSON, n));
            }
        }
        return sonuc;
    }

    private static List<TamEserUretimParcasi> parcalariOku(JsonNode dizi) {
        List<TamEserUretimParcasi> parcalar = new ArrayList<>();
        if (dizi != null && dizi.isArray()) {
            for (JsonNode p : dizi) {
                parcalar.add(new TamEserUretimParcasi(
                        p.path("sira").asInt(),
                        p.path("safeName").asText(""),
                        p.path("karakterSayisi").asInt(),
                        p.path("tahminiSureSaniye").asInt()));
            }
        }
        return parcalar;
    }

    private void yaz(Path hedef, ObjectNode icerik) throws Exception {
        Files.createDirectories(klasor);
        TamEserUretimGuvenlikService.dosyaGuvenligi(klasor, hedef);
        Path partial = klasor.resolve(hedef.getFileName().toString() + ".partial");
        String json = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(icerik);
        Files.writeString(partial, json, StandardCharsets.UTF_8);
        Files.move(partial, hedef, StandardCopyOption.REPLACE_EXISTING);
    }
}
