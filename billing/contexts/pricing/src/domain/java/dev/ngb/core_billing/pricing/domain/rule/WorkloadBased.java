package dev.ngb.core_billing.pricing.domain.rule;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import dev.ngb.core_billing.pricing.domain.metric.Measure;
import dev.ngb.core_billing.pricing.domain.metric.UsageSummary;

/**
 * Price for background workloads: a start fee for every execution plus a rate for the work it consumed. The
 * quantity is the work done (CPU-seconds, GPU-minutes) and each usage event is one execution, so a job that
 * is cheap to run still pays for being scheduled.
 */
public record WorkloadBased(String metric, String unit, BigDecimal executionFee, BigDecimal unitPrice) implements PriceRule {

    public WorkloadBased {
        Prices.metric(metric);
        Prices.unit(unit);
        Prices.price(executionFee, "executionFee");
        Prices.price(unitPrice, "unitPrice");
    }

    @Override
    public PricingModel model() {
        return PricingModel.WORKLOAD_BASED;
    }

    @Override
    public Map<String, String> metrics() {
        return Map.of(metric, unit);
    }

    @Override
    public List<PriceLine> price(UsageSummary usage) {
        Measure measure = usage.of(metric);
        BigDecimal executions = BigDecimal.valueOf(measure.events());
        return List.of(
                new PriceLine(measure.events() + " executions x " + Prices.plain(executionFee), metric, executions, Prices.times(executions, executionFee)),
                new PriceLine(Prices.plain(measure.quantity()) + " " + unit + " x " + Prices.plain(unitPrice), metric, measure.quantity(),
                        Prices.times(measure.quantity(), unitPrice)));
    }
}
