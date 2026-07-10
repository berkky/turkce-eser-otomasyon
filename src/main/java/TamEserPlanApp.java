/**
 * CLI: Tam eser üretim planı (TTS başlatmaz).
 */
public final class TamEserPlanApp {
    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        int eserId = parseEserId(args, 5);

        WebOrtam ortam = WebOrtam.varsayilan();
        TamEserUretimPlanService svc = new TamEserUretimPlanService(ortam);

        System.out.println("--- TAM ESER ÜRETİM PLANI (Adım 31) ---");
        System.out.println("Eser ID: ESER-" + String.format("%05d", eserId));
        System.out.println("Not: Gerçek TTS üretimi yapılmaz. Yalnızca plan JSON oluşturulur.");
        System.out.println();

        TamEserUretimPlani plan = svc.planUret(eserId);
        System.out.println("Başlık: " + plan.baslik());
        System.out.println("Karakter: " + plan.toplamKarakter());
        System.out.println("TTS parça: " + plan.ttsParcaSayisi());
        System.out.println("Tahmini dakika: " + plan.tahminiDakika());
        System.out.println("Sağlayıcı: " + plan.secilenSaglayici() + " / " + plan.secilenModel());
        System.out.println("Kalan kredi: " + plan.kalanKredi());
        System.out.println("Tahmini kredi: " + plan.tahminiKrediIhtiyaci());
        System.out.println("Kredi yeterli: " + plan.krediYeterliMi());
        System.out.println("Büyük eser: " + plan.buyukEserMi());
        System.out.println("Risk: " + plan.riskSeviyesi());
        System.out.println("Önerilen: " + plan.onerilenAksiyon());
        System.out.println("Tam üretim varsayılan kapalı: " + plan.tamUretimVarsayilanKapaliMi());
        System.out.println();
        System.out.println("Plan dosyası: ESER-" + String.format("%05d", eserId) + "-production-plan.json");
        System.out.println("Uyarı: Onay olmadan üretim başlatılmaz.");
    }

    static int parseEserId(String[] args, int varsayilan) {
        for (String arg : args) {
            if (arg == null || arg.startsWith("-")) {
                continue;
            }
            try {
                return Integer.parseInt(arg.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
            }
        }
        return varsayilan;
    }
}
