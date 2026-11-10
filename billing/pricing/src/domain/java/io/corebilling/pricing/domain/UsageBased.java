package io.corebilling.pricing.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Price by the quantity consumed (storage, bandwidth). One tier is a flat rate; several tiers give a volume
 * discount. {@code GRADUATED} prices each band of the quantity at its own tier's rate; {@code VOLUME} prices the
 * whole quantity at the rate of the tier it falls in. A first tier priced at zero is an included allowance, which
 * is how a usage threshold is expressed.
 */
public record UsageBased(String metric, String unit, TierMode mode, List<Tier> tiers) implements PriceRule {

    public UsageBased {
        Prices.metric(metric);
        Prices.unit(unit);
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("at least one tier is required");
        }
        tiers = List.copyOf(tiers);
        BigDecimal previous = BigDecimal.ZERO;
        for (int i = 0; i < tiers.size(); i++) {
            BigDecimal upTo = tiers.get(i).upTo();
            boolean last = i == tiers.size() - 1;
            if (upTo == null && !last) {
                throw new IllegalArgumentException("only the last tier may be unbounded");
            }
            if (upTo != null && upTo.compareTo(previous) <= 0) {
                throw new IllegalArgumentException("tier limits must increase");
            }
            if (upTo == null) {
                continue;
            }
            previous = upTo;
        }
        if (tiers.get(tiers.size() - 1).upTo() != null) {
            throw new IllegalArgumentException("the last tier must be unbounded (no upTo)");
        }
    }

    @Override
    public PricingModel model() {
        return PricingModel.USAGE_BASED;
    }

    @Override
    public Map<String, String> metrics() {
        return Map.of(metric, unit);
    }

    @Override
    public List<PriceLine> price(UsageSummary usage) {
        BigDecimal used = usage.of(metric).quantity();
        return mode == TierMode.VOLUME ? volume(used) : graduated(used);
    }

    private List<PriceLine> volume(BigDecimal used) {
        for (Tier tier : tiers) {
            if (tier.upTo() == null || used.compareTo(tier.upTo()) <= 0) {
                return List.of(new PriceLine(Prices.plain(used) + " " + unit + " x " + Prices.plain(tier.unitPrice()) + " (volume tier)", metric, used,
                        Prices.times(used, tier.unitPrice())));
            }
        }
        throw new IllegalStateException("the last tier is unbounded, so a tier always matches");
    }

    private List<PriceLine> graduated(BigDecimal used) {
        List<PriceLine> lines = new ArrayList<>();
        BigDecimal lower = BigDecimal.ZERO;
        for (int i = 0; i < tiers.size() && used.compareTo(lower) > 0; i++) {
            Tier tier = tiers.get(i);
            BigDecimal upper = tier.upTo() == null ? used : used.min(tier.upTo());
            BigDecimal inTier = upper.subtract(lower);
            lines.add(new PriceLine(Prices.plain(inTier) + " " + unit + " x " + Prices.plain(tier.unitPrice()) + " (tier " + (i + 1) + ")", metric, inTier,
                    Prices.times(inTier, tier.unitPrice())));
            lower = upper;
        }
        if (lines.isEmpty()) {
            lines.add(new PriceLine("no " + unit + " used", metric, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        return lines;
    }
}
