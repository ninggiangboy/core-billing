package dev.ngb.core_billing.pricing.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import dev.ngb.core_billing.pricing.domain.metric.UsageSummary;
import dev.ngb.core_billing.pricing.domain.plan.PriceLine;
import dev.ngb.core_billing.pricing.domain.model.Prices;

/** A fixed amount for one billing cycle, whatever is used. */
public record Subscription(BigDecimal fixedPrice) implements PriceRule {

    public Subscription {
        Prices.price(fixedPrice, "fixedPrice");
    }

    @Override
    public PricingModel model() {
        return PricingModel.SUBSCRIPTION;
    }

    @Override
    public Map<String, String> metrics() {
        return Map.of();
    }

    @Override
    public List<PriceLine> price(UsageSummary usage) {
        return List.of(new PriceLine("subscription for one billing cycle", null, BigDecimal.ONE, fixedPrice));
    }
}
