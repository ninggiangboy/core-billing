package dev.ngb.core_billing.pricing.adapter.persistence.plan;

import dev.ngb.core_billing.pricing.domain.rule.EventBased;
import dev.ngb.core_billing.pricing.domain.rule.Hybrid;
import dev.ngb.core_billing.pricing.domain.rule.PriceRule;
import dev.ngb.core_billing.pricing.domain.rule.PricingModel;
import dev.ngb.core_billing.pricing.domain.rule.FixedPrice;
import dev.ngb.core_billing.pricing.domain.rule.Tier;
import dev.ngb.core_billing.pricing.domain.rule.TierMode;
import dev.ngb.core_billing.pricing.domain.rule.TimeBased;
import dev.ngb.core_billing.pricing.domain.rule.UsageBased;
import dev.ngb.core_billing.pricing.domain.rule.WorkloadBased;
import java.math.BigDecimal;
import java.util.List;

/**
 * The JSON form of a price rule, used on the API and in the database. Every number is a decimal string (a JSON
 * number is accepted too) in minor units of the plan's currency, so a fraction of a cent survives exactly.
 * Which fields apply depends on the model:
 *
 * <ul>
 *   <li>TIME_BASED: metric, unit, unitPrice, increment (optional)
 *   <li>USAGE_BASED: metric, unit, mode (GRADUATED by default), tiers; or unitPrice alone for a flat rate
 *   <li>WORKLOAD_BASED: metric, unit, unitPrice, executionFee (optional)
 *   <li>EVENT_BASED: metric, unit, unitPrice
 *   <li>SUBSCRIPTION: fixedPrice
 *   <li>HYBRID: fixedPrice and components (each with its own model)
 * </ul>
 */
public record RuleDto(
        String model, String metric, String unit, String unitPrice, String increment, String mode, List<TierDto> tiers, String executionFee,
        String fixedPrice, List<RuleDto> components) {

    public record TierDto(String upTo, String unitPrice) {}

    public static RuleDto of(PriceRule rule) {
        String model = rule.model().name();
        return switch (rule) {
            case TimeBased t -> new RuleDto(model, t.metric(), t.unit(), plain(t.unitPrice()), plain(t.increment()), null, null, null, null, null);
            case UsageBased u -> new RuleDto(model, u.metric(), u.unit(), null, null, u.mode().name(),
                    u.tiers().stream().map(t -> new TierDto(t.upTo() == null ? null : plain(t.upTo()), plain(t.unitPrice()))).toList(), null, null, null);
            case WorkloadBased w -> new RuleDto(model, w.metric(), w.unit(), plain(w.unitPrice()), null, null, null, plain(w.executionFee()), null, null);
            case EventBased e -> new RuleDto(model, e.metric(), e.unit(), plain(e.unitPrice()), null, null, null, null, null, null);
            case FixedPrice s -> new RuleDto(model, null, null, null, null, null, null, null, plain(s.fixedPrice()), null);
            case Hybrid h -> new RuleDto(model, null, null, null, null, null, null, null, plain(h.fixedPrice()), h.components().stream().map(RuleDto::of).toList());
            default -> throw new IllegalStateException("no JSON form for " + rule.getClass().getSimpleName());
        };
    }

    /** @param model the plan's model; a component states its own */
    public PriceRule toRule(PricingModel model) {
        PricingModel effective = this.model == null ? model : PricingModel.valueOf(this.model);
        if (effective != model && model != null) {
            throw new IllegalArgumentException("the rule is %s but the plan is %s".formatted(effective, model));
        }
        return switch (effective) {
            case TIME_BASED -> new TimeBased(metric, unit, decimal(unitPrice, "unitPrice"), increment == null ? BigDecimal.ZERO : decimal(increment, "increment"));
            case USAGE_BASED -> new UsageBased(metric, unit, mode == null ? TierMode.GRADUATED : TierMode.valueOf(mode), tiersOrFlat());
            case WORKLOAD_BASED -> new WorkloadBased(metric, unit, executionFee == null ? BigDecimal.ZERO : decimal(executionFee, "executionFee"),
                    decimal(unitPrice, "unitPrice"));
            case EVENT_BASED -> new EventBased(metric, unit, decimal(unitPrice, "unitPrice"));
            case SUBSCRIPTION -> new FixedPrice(decimal(fixedPrice, "fixedPrice"));
            case HYBRID -> {
                if (components == null) {
                    throw new IllegalArgumentException("components is required for a HYBRID rule");
                }
                yield new Hybrid(decimal(fixedPrice, "fixedPrice"), components.stream().map(c -> c.toRule(c.model == null ? null : PricingModel.valueOf(c.model))).toList());
            }
        };
    }

    private List<Tier> tiersOrFlat() {
        if (tiers != null) {
            return tiers.stream().map(t -> new Tier(t.upTo() == null ? null : decimal(t.upTo(), "upTo"), decimal(t.unitPrice(), "tier unitPrice"))).toList();
        }
        return List.of(new Tier(null, decimal(unitPrice, "unitPrice")));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    static BigDecimal decimal(String text, String name) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        try {
            return new BigDecimal(text.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " is not a number");
        }
    }
}
