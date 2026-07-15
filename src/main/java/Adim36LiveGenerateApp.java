import java.math.BigDecimal;
import java.util.Locale;

/** Adım 36 canlı üretim / onay CLI — kullanıcı confirmation olmadan ağ çağrısı yapmaz. */
public final class Adim36LiveGenerateApp {
    private Adim36LiveGenerateApp() {
    }

    public static void main(String[] args) throws Exception {
        Utf8Konsol.etkinlestir();
        Args parsed = Args.parse(args);
        KasagiLivePreviewService service = new KasagiLivePreviewService(WebOrtam.varsayilan());
        LiveProvider provider = LiveProvider.valueOf(parsed.provider.toLowerCase(Locale.ROOT));

        if (parsed.approveOnly) {
            ApprovedPassage approved = service.resolveApprovedPassage();
            if (!approved.selectionId().equals(parsed.selectionId)) {
                throw new IllegalArgumentException("APPROVED_PASSAGE_INVALID");
            }
            LiveGenerationApproval approval = service.createLiveApproval(
                    parsed.selectionId, approved.approvedTextHash(), provider, parsed.voice,
                    parsed.maxBudgetUsd, parsed.maxRequestCount, parsed.confirmation, "LOCAL_USER", false);
            System.out.println("--- ADIM 36 LIVE APPROVE ---");
            System.out.println("liveApprovalId=" + approval.liveApprovalId());
            System.out.println("provider=" + provider.name());
            System.out.println("status=" + approval.status());
            System.out.println("voice=" + parsed.voice);
            System.out.println("maxBudgetUsd=" + parsed.maxBudgetUsd);
            System.out.println("expiresAt=" + approval.expiresAt());
            System.out.println("networkCalls=false");
            return;
        }

        if (!parsed.live) {
            System.out.println("DRY_RUN_ONLY");
            return;
        }
        LiveGenerateResult result = service.generate(provider, parsed.selectionId, parsed.voice,
                parsed.maxBudgetUsd, parsed.confirmation, true, true,
                null, null, null);
        System.out.println("--- ADIM 36 LIVE GENERATE ---");
        System.out.println("provider=" + result.provider());
        System.out.println("calledNetwork=" + result.calledNetwork());
        System.out.println("cacheHit=" + result.cacheHit());
        System.out.println("requestCount=" + result.requestCount());
        System.out.println("output=" + (result.outputPath() == null ? "" : result.outputPath().safeName()));
        System.out.println("rawPathSafe=" + result.rawPathSafe());
        System.out.println("requestHash=" + result.requestHash());
        System.out.println("rawHash=" + result.rawHash());
        System.out.println("normalizedPathSafe=" + result.normalizedPathSafe());
        System.out.println("normalizedHash=" + result.normalizedHash());
        System.out.println("durationSeconds=" + result.durationSeconds());
        System.out.println("codec=" + result.codec());
        System.out.println("sampleRate=" + result.sampleRate());
        System.out.println("channels=" + result.channels());
        System.out.println("bitrate=" + result.bitrate());
        System.out.println("integratedLufs=" + result.integratedLufs());
        System.out.println("truePeakDbtp=" + result.truePeakDbtp());
        System.out.println("estimatedCostUsd=" + result.estimatedCostUsd());
        System.out.println("actualCostUsd=" + result.actualCostUsd());
        System.out.println("approvalConsumed=" + result.approvalConsumed());
        System.out.println("attemptId=" + result.attemptId());
        System.out.println("approvalState=" + result.approvalState());
        System.out.println("status=" + result.status());
    }

    private record Args(String provider, String selectionId, String voice, BigDecimal maxBudgetUsd,
                        String confirmation, boolean live, boolean approveOnly, int maxRequestCount) {
        static Args parse(String[] args) {
            String provider = null;
            String selectionId = null;
            String voice = null;
            BigDecimal budget = null;
            String confirmation = null;
            boolean live = false;
            boolean approveOnly = false;
            int maxRequestCount = 1;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-Provider" -> provider = args[++i];
                    case "-SelectionId" -> selectionId = args[++i];
                    case "-Voice" -> voice = args[++i];
                    case "-MaxBudgetUsd" -> budget = new BigDecimal(args[++i]);
                    case "-Confirmation" -> confirmation = args[++i];
                    case "-RetryConfirmation" -> {
                        ++i; // ignore value; automatic retry unsupported
                        throw new IllegalArgumentException("AUTOMATIC_RETRY_NOT_SUPPORTED");
                    }
                    case "-MaxRequestCount" -> maxRequestCount = Integer.parseInt(args[++i]);
                    case "-Live" -> live = true;
                    case "-NoRetry" -> { /* varsayılan: retry yok */ }
                    case "-AllowRetry" -> throw new IllegalArgumentException("AUTOMATIC_RETRY_NOT_SUPPORTED");
                    case "-ApproveOnly" -> approveOnly = true;
                    default -> throw new IllegalArgumentException("Bilinmeyen argüman: " + args[i]);
                }
            }
            if (provider == null || selectionId == null || voice == null || budget == null) {
                throw new IllegalArgumentException(
                        "Zorunlu: -Provider -SelectionId -Voice -MaxBudgetUsd [-Confirmation] [-Live|-ApproveOnly]");
            }
            return new Args(provider, selectionId, voice, budget, confirmation, live, approveOnly, maxRequestCount);
        }
    }
}
