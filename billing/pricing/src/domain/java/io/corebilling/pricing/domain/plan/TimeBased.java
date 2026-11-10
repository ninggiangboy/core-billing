package io.corebilling.pricing.domain.plan;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import io.corebilling.pricing.domain.metric.UsageSummary;

/**
 * Price per unit of time, for a resource that runs for a while (a compute instance). The quantity is a duration
 * in {@code unit}. With an {@code increment}, the duration is first rounded up to a whole number of increments
 * (for example, billed per started minute). {@code increment} is zero for no rounding.
 */
public record TimeBased(String metric, String unit, BigDecimal unitPrice, BigDecimal increment) implements PriceRule {

    public TimeBased {
        Prices.metric(metric);
        Prices.unit(unit);
        Prices.price(unitPrice, "unitPrice");
        Prices.price(increment, "increment");
    }

    @Override
    public PricingModel model() {
        return PricingModel.TIME_BASED;
    }

    @Override
    public Map<String, String> metrics() {
        return Map.of(metric, unit);
    }

    @Override
    public List<PriceLine> price(UsageSummary usage) {
        BigDecimal used = usage.of(metric).quantity();
        BigDecimal billed = increment.signum() == 0 || used.signum() == 0 ? used : Prices.ceilToMultiple(used, increment);
        String note = billed.compareTo(used) == 0 ? "" : " (" + Prices.plain(used) + " rounded up to " + Prices.plain(increment) + " " + unit + " steps)";
        return List.of(new PriceLine(Prices.plain(billed) + " " + unit + " x " + Prices.plain(unitPrice) + note, metric, billed, Prices.times(billed, unitPrice)));
    }
}
