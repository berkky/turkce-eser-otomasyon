import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adım 25 ses kalite paneli doğrulamaları — gerçek API gerektirmez.
 */
public final class Adim25Dogrulama {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();

        utf8BaslikTesti();
        Path kok = Files.createTempDirectory("adim25-panel-");
        Path sesArsiv = kok.resolve("ses-arsivi");
        Path metinArsiv = kok.resolve("metin-arsivi");
        Path panel = kok.resolve("panel");

        try {
            Files.createDirectories(metinArsiv.resolve("ESER-00005 - Kasagi - Vikikaynak").resolve("tts-parcalari"));
            Files.createDirectories(metinArsiv.resolve("ESER-00006 - Astronomi Alfa Yayinlari").resolve("tts-parcalari"));
            Files.writeString(
                    metinArsiv.resolve("ESER-00006 - Astronomi Alfa Yayinlari").resolve("tts-parcalari").resolve("001-001.txt"),
                    "Astronomi demo parça metni.",
                    StandardCharsets.UTF_8
            );

            SesKalitePanelDemoService.demoVerisiOlustur(sesArsiv);

            SesKalitePanelService.PanelSonucu ilk =
                    SesKalitePanelService.uret(sesArsiv, metinArsiv, panel, kok);

            if (!Files.isRegularFile(ilk.html()) || !Files.isRegularFile(ilk.json()) || !Files.isRegularFile(ilk.md())) {
                hata("Panel dosyaları oluşturulmadı");
            }

            ObjectNode deger = (ObjectNode) new ObjectMapper().readTree(
                    Files.readString(ilk.degerlendirmeler(), StandardCharsets.UTF_8));
            ArrayNode dizi = (ArrayNode) deger.path("degerlendirmeler");
            if (dizi.isEmpty()) {
                hata("Değerlendirme şablonu boş");
            }
            ObjectNode ilkKayit = (ObjectNode) dizi.get(0);
            ilkKayit.put("humanScoreOverall", 4.5);
            ilkKayit.put("reviewerNote", "Test puanı — korunmalı");
            ilkKayit.put("updatedAt", "2026-07-09T00:00:00Z");
            Files.writeString(ilk.degerlendirmeler(),
                    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(deger),
                    StandardCharsets.UTF_8);

            SesKalitePanelService.PanelSonucu ikinci =
                    SesKalitePanelService.uret(sesArsiv, metinArsiv, panel, kok);
            JsonNode ikinciDeger = new ObjectMapper().readTree(
                    Files.readString(ikinci.degerlendirmeler(), StandardCharsets.UTF_8));
            double puan = ikinciDeger.path("degerlendirmeler").get(0).path("humanScoreOverall").asDouble(0);
            if (puan != 4.5) {
                hata("İnsan puanı ikinci çalışmada silindi");
            }

            boolean astronomiBuyuk = ikinci.rapor().onizlemeler().stream()
                    .anyMatch(o -> o.eserId() == 6 && o.buyukEser());
            if (!astronomiBuyuk) {
                hata("ESER-00006 büyük eser olarak işaretlenmedi");
            }

            boolean sifirByteGecersiz = ikinci.rapor().onizlemeler().stream()
                    .anyMatch(o -> o.eserId() == 6 && SesKaliteOlcutleri.DURUM_GECERSIZ.equals(o.status()));
            if (!sifirByteGecersiz) {
                hata("0 byte mp3 GECERSIZ işaretlenmedi");
            }

            boolean kasagiVar = ikinci.rapor().onizlemeler().stream().anyMatch(o -> o.eserId() == 5);
            boolean astronomiVar = ikinci.rapor().onizlemeler().stream().anyMatch(o -> o.eserId() == 6);
            if (!kasagiVar || !astronomiVar) {
                hata("ESER-00005 veya ESER-00006 panelde görünmüyor");
            }

            if (!Files.readString(ilk.html(), StandardCharsets.UTF_8).contains("Ses Önizleme Kalite Paneli")) {
                hata("index.html içeriği eksik");
            }

            System.out.println("OK: Panel dosyaları üretildi");
            System.out.println("OK: İnsan puanı korundu");
            System.out.println("OK: 0 byte mp3 GECERSIZ");
            System.out.println("OK: ESER-00006 büyük eser koruması");
            System.out.println("OK: ESER-00005 ve ESER-00006 panelde");
            System.out.println("ADIM 25 DOĞRULAMA: BAŞARILI");
        } finally {
            silRecursif(kok);
        }
    }

    private static void utf8BaslikTesti() {
        String baslik = "GERİYE DÖNÜK GÜVENLİK DOĞRULAMA";
        if (!baslik.contains("İ") || !baslik.contains("Ö") || !baslik.contains("Ü")) {
            hata("UTF-8 başlık testi başarısız");
        }
        System.out.println("OK: UTF-8 Türkçe başlık — " + baslik);
    }

    private static void silRecursif(Path kok) throws Exception {
        if (!Files.exists(kok)) {
            return;
        }
        try (var s = Files.walk(kok)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private static void hata(String mesaj) {
        throw new IllegalStateException("ADIM 25 TEST HATASI: " + mesaj);
    }
}
