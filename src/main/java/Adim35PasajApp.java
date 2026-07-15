import java.nio.file.Path;

public final class Adim35PasajApp {
    private Adim35PasajApp() {
    }

    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        KasagiPasajService service = new KasagiPasajService(WebOrtam.varsayilan());
        String command = args.length == 0 ? "generate" : args[0].trim().toLowerCase(java.util.Locale.ROOT);
        if ("status".equals(command)) {
            PassageSelectionManifest latest = service.latestSelection();
            if (latest == null) {
                System.out.println("Henüz pasaj seçimi yok.");
                return;
            }
            print(latest, service.root().resolve(latest.selectionId()));
            return;
        }
        if ("rights".equals(command)) {
            PassageSelectionManifest latest = service.latestSelection();
            String selectionId = args.length > 1 ? args[1] : latest == null ? null : latest.selectionId();
            if (selectionId == null) throw new IllegalStateException("Pasaj seçimi bulunamadı.");
            Path rights = service.prepareRights(selectionId);
            System.out.println("Hak/atıf kaydı hazır: " + rights.getFileName());
            System.out.println("Pasaj seçimi/onayı: YOK");
            System.out.println("Canlı API çağrısı ve ses üretimi: YOK");
            return;
        }
        if ("upgrade-approval-request".equals(command)) {
            PassageSelectionManifest latest = service.latestSelection();
            String selectionId = args.length > 1 ? args[1] : latest == null ? null : latest.selectionId();
            if (selectionId == null) throw new IllegalStateException("Pasaj seçimi bulunamadı.");
            boolean changed = service.upgradeApprovalRequestSchema(selectionId);
            System.out.println("approval-request şema yükseltmesi: "
                    + (changed ? "GÜNCELLENDİ" : "ZATEN_GÜNCEL (idempotent)"));
            System.out.println("Selection ID: " + selectionId);
            System.out.println("schemaVersion: " + KasagiPasajService.APPROVAL_REQUEST_SCHEMA_VERSION);
            System.out.println("Pasaj seçimi/onayı: YOK");
            System.out.println("approved-passage / canlı profil / ses: YOK");
            return;
        }
        if (!"generate".equals(command)) {
            throw new IllegalArgumentException(
                    "Komutlar: generate, status, rights [selectionId], upgrade-approval-request [selectionId]");
        }
        Path output = service.createSelection();
        PassageSelectionManifest manifest = service.readManifest(output.getFileName().toString());
        print(manifest, output);
        System.out.println("Canlı API çağrısı: YOK");
        System.out.println("Ses üretimi: YOK");
    }

    private static void print(PassageSelectionManifest manifest, Path output) {
        System.out.println("--- ADIM 35 KAŞAĞI PASAJ ADAYLARI ---");
        System.out.println("Selection ID: " + manifest.selectionId());
        System.out.println("Kaynak: " + manifest.source().sourceFileSafeName());
        System.out.println("Karakter: " + manifest.source().sourceCharacterCount());
        System.out.println("TTS parça: " + manifest.source().ttsPartCount());
        System.out.println("Lisans: " + manifest.source().sourceLicenseStatus());
        for (PassageCandidate candidate : manifest.candidates()) {
            System.out.printf(java.util.Locale.ROOT,
                    "%s | %d karakter | %d kelime | %d saniye | skor %.2f | %s%n",
                    candidate.candidateId(), candidate.normalizedCharacterCount(), candidate.wordCount(),
                    candidate.estimatedSpeechSeconds(), candidate.scores().totalScore(),
                    candidate.normalizedTextHash());
            System.out.println("  Öneri: " + KasagiPasajService.recommendationType(candidate.candidateId())
                    + " (otomatik seçim değildir)");
        }
        System.out.println("Klasör: " + output.getFileName());
    }
}
