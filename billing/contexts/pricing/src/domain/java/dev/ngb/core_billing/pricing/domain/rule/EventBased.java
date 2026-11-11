package dev.ngb.core_billing.pricing.domain.rule;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import dev.ngb.core_billing.pricing.domain.metric.UsageSummary;

/** Price per discrete event (an API call, a message). The quantity counts events and must be a whole number. */
public record EventBased(String metric, String unit, BigDecimal unitPrice) implements PriceRule {

    public EventBased {
        Prices.metric(metric);
        Prices.unit(unit);
        Prices.price(unitPrice, "unitPrice");
    }

    @Override
    public PricingModel model() {
        return PricingModel.EVENT_BASED;
    }

    @Override
    public Map<String, String> metrics() {
        return Map.of(metric, unit);
    }

    @Override
    public List<PriceLine> price(UsageSummary usage) {
        BigDecimal count = usage.of(metric).quantity();
        if (!Prices.isWhole(count)) {
            throw new IllegalArgumentException("event-based pricing needs a whole number of %s, got %s".formatted(unit, Prices.plain(count)));
        }
        return List.of(new PriceLine(Prices.plain(count) + " " + unit + " x " + Prices.plain(unitPrice), metric, count, Prices.times(count, unitPrice)));
    }
}
