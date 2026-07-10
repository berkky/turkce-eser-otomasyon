/**
 * CLI: Onaylı tam eser kuyruk kaydı (TTS başlatmaz).
 */
public final class TamEserKuyrugaAlApp {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        int eserId = TamEserPlanApp.parseEserId(args, 5);
        boolean onayli = false;
        for (String arg : args) {
            if (arg != null && ("-Onayli".equalsIgnoreCase(arg) || "--onayli".equalsIgnoreCase(arg))) {
                onayli = true;
            }
        }

        WebOrtam ortam = WebOrtam.varsayilan();
        TamEserUretimKuyrukService svc = new TamEserUretimKuyrukService(ortam);

        System.out.println("--- TAM ESER KUYRUĞA AL (Adım 31) ---");
        System.out.println("Eser ID: ESER-" + String.format("%05d", eserId));
        System.out.println("Onaylı: " + onayli);
        System.out.println("Not: Gerçek TTS üretimi başlatılmaz.");
        System.out.println();

        TamEserUretimKuyrukService.KuyrukSonucu sonuc = svc.kuyrugaAl(eserId, onayli);
        if (!sonuc.basarili()) {
            System.err.println("Kuyruk reddedildi: " + sonuc.mesaj());
            System.exit(1);
        }

        TamEserUretimKuyrukKaydi kayit = sonuc.kayit();
        System.out.println("Job ID: " + kayit.jobId());
        System.out.println("Durum: " + kayit.status());
        System.out.println("Output safe name: " + kayit.outputSafeName());
        System.out.println("Notlar: " + kayit.notlar());
        if (sonuc.buyukUyari()) {
            System.out.println();
            System.out.println("*** UYARI: Büyük eser — gerçek üretim bu adımda kapalı. Kuyruk BLOCKED/manuel bekliyor. ***");
        }
        System.out.println();
        System.out.println("Üretim başlatıldı mı: " + TamEserUretimKuyrukService.uretimBaslatildiMi());
        System.out.println("Dosya: ESER-" + String.format("%05d", eserId) + "-production-queue.json");
    }
}
