/**
 * CLI: Tam eser onay taslağı (TTS başlatmaz).
 */
public final class TamEserOnayTaslagiApp {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        int eserId = TamEserPlanApp.parseEserId(args, 5);

        WebOrtam ortam = WebOrtam.varsayilan();
        TamEserUretimOnayService svc = new TamEserUretimOnayService(ortam);

        System.out.println("--- TAM ESER ONAY TASLAĞI (Adım 31) ---");
        System.out.println("Eser ID: ESER-" + String.format("%05d", eserId));
        System.out.println();

        try {
            TamEserUretimOnayi onay = svc.taslakOlustur(eserId);
            System.out.println("Onay ID: " + onay.onayId());
            System.out.println("Durum: " + onay.onayDurumu());
            System.out.println("Tahmini kredi: " + onay.tahminiKredi());
            System.out.println("Parça: " + onay.parcaSayisi());
            System.out.println();
            System.out.println(onay.onayMetni());
            System.out.println();
            System.out.println("Dosya: ESER-" + String.format("%05d", eserId) + "-production-approval-draft.json");
        } catch (IllegalStateException e) {
            System.err.println("Onay taslağı oluşturulamadı: " + e.getMessage());
            System.exit(1);
        }
    }
}
