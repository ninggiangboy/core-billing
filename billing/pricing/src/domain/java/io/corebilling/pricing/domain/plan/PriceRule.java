package io.corebilling.pricing.domain.plan;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import io.corebilling.pricing.domain.metric.UsageSummary;

/**
 * A pricing strategy: how a quantity of usage becomes an amount. Adding a pricing model is adding one
 * implementation of this interface; nothing outside the pricing context is touched, because money only ever
 * sees the final amount in minor units.
 *
 * <p>Implementations work in {@link BigDecimal} and return exact amounts in minor units. Rounding to whole minor
 * units happens once, on the total, in {@link PlanVersion#quote}.
 */
public interface PriceRule {

    PricingModel model();

    /** The metrics this rule reads, each with the unit it is measured in. Empty for a fixed price. */
    Map<String, String> metrics();

    /** The priced lines for {@code usage}; their amounts add up to the exact price. */
    List<PriceLine> price(UsageSummary usage);
}
