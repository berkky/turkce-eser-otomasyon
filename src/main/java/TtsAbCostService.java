import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

public final class TtsAbCostService {
    public enum Basis { CHARACTER_USD, TOKEN_USD_ESTIMATE, CREDIT_PER_CHARACTER, LOCAL_FREE }

    public record PriceProfile(String providerCode, Basis basis, BigDecimal unitPrice,
                               long unitsPerPrice, boolean estimate) { }

    public record Estimate(String providerCode, Basis basis, long inputUnits,
                           BigDecimal estimatedUsd, BigDecimal estimatedCredits, boolean estimate) { }

    private static final Map<String, PriceProfile> PROFILES = Map.of(
            "xai", new PriceProfile("xai", Basis.CHARACTER_USD, new BigDecimal("15"), 1_000_000, false),
            "google", new PriceProfile("google", Basis.CHARACTER_USD, new BigDecimal("30"), 1_000_000, false),
            "elevenlabs", new PriceProfile("elevenlabs", Basis.CHARACTER_USD, new BigDecimal("100"), 1_000_000, true),
            "cartesia", new PriceProfile("cartesia", Basis.CREDIT_PER_CHARACTER, BigDecimal.ONE, 1, true),
            "openai", new PriceProfile("openai", Basis.TOKEN_USD_ESTIMATE,
                    new BigDecimal("0"), 1_000_000, true),
            "piper", new PriceProfile("piper", Basis.LOCAL_FREE, BigDecimal.ZERO, 1, false)
    );

    public PriceProfile profile(String providerCode) {
        String key = providerCode == null ? "" : providerCode.toLowerCase(Locale.ROOT);
        return PROFILES.getOrDefault(key,
                new PriceProfile(key, Basis.TOKEN_USD_ESTIMATE, BigDecimal.ZERO, 1_000_000, true));
    }

    public Estimate estimate(String providerCode, String text) {
        PriceProfile profile = profile(providerCode);
        int characters = text == null ? 0 : text.length();
        return switch (profile.basis()) {
            case CHARACTER_USD -> new Estimate(profile.providerCode(), profile.basis(), characters,
                    profile.unitPrice().multiply(BigDecimal.valueOf(characters))
                            .divide(BigDecimal.valueOf(profile.unitsPerPrice()), 8, RoundingMode.HALF_UP),
                    BigDecimal.ZERO, profile.estimate());
            case CREDIT_PER_CHARACTER -> new Estimate(profile.providerCode(), profile.basis(), characters,
                    BigDecimal.ZERO, profile.unitPrice().multiply(BigDecimal.valueOf(characters)),
                    true);
            case TOKEN_USD_ESTIMATE -> {
                long estimatedTokens = Math.max(1L, Math.round(characters / 4.0));
                yield new Estimate(profile.providerCode(), profile.basis(), estimatedTokens,
                        profile.unitPrice().multiply(BigDecimal.valueOf(estimatedTokens))
                                .divide(BigDecimal.valueOf(profile.unitsPerPrice()), 8, RoundingMode.HALF_UP),
                        BigDecimal.ZERO, true);
            }
            case LOCAL_FREE -> new Estimate(profile.providerCode(), profile.basis(), characters,
                    BigDecimal.ZERO, BigDecimal.ZERO, false);
        };
    }

    public void requireWithinBudget(Estimate estimate, BigDecimal alreadyReserved, BigDecimal budget) {
        BigDecimal total = (alreadyReserved == null ? BigDecimal.ZERO : alreadyReserved)
                .add(estimate.estimatedUsd());
        if (budget == null || total.compareTo(budget) > 0) {
            throw new IllegalStateException("BUDGET_EXCEEDED: tahmini maliyet deney bütçesini aşıyor.");
        }
    }
}
