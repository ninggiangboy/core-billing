package io.corebilling.pricing.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.corebilling.pricing.domain.metric.UsageSummary;
import io.corebilling.pricing.domain.plan.PriceLine;
import io.corebilling.pricing.domain.model.Prices;

/**
 * A base amount per billing cycle plus usage components, such as a cloud package: a monthly fee that includes
 * nothing, plus metered storage, plus per-call charges. Components are usage rules; each metric appears once.
 */
public record Hybrid(BigDecimal fixedPrice, List<PriceRule> components) implements PriceRule {

    public Hybrid {
        Prices.price(fixedPrice, "fixedPrice");
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("a hybrid price needs at least one usage component");
        }
        components = List.copyOf(components);
        Map<String, String> seen = new LinkedHashMap<>();
        for (PriceRule component : components) {
            if (component.model() == PricingModel.HYBRID || component.model() == PricingModel.SUBSCRIPTION) {
                throw new IllegalArgumentException("a hybrid component must be a usage, time, workload or event price");
            }
            for (String metric : component.metrics().keySet()) {
                if (seen.put(metric, component.metrics().get(metric)) != null) {
                    throw new IllegalArgumentException("metric '" + metric + "' is priced by more than one component");
                }
            }
        }
    }

    @Override
    public PricingModel model() {
        return PricingModel.HYBRID;
    }

    @Override
    public Map<String, String> metrics() {
        Map<String, String> all = new LinkedHashMap<>();
        components.forEach(c -> all.putAll(c.metrics()));
        return all;
    }

    @Override
    public List<PriceLine> price(UsageSummary usage) {
        List<PriceLine> lines = new ArrayList<>();
        lines.add(new PriceLine("base amount for one billing cycle", null, BigDecimal.ONE, fixedPrice));
        components.forEach(c -> lines.addAll(c.price(usage)));
        return lines;
    }
}
