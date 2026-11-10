package io.corebilling.pricing.domain;

import java.util.Map;
import java.util.Objects;

/** Usage grouped by metric: the input every price rule works from. A metric that was not used reads as none. */
public final class UsageSummary {

    private final Map<String, Measure> byMetric;

    public UsageSummary(Map<String, Measure> byMetric) {
        this.byMetric = Map.copyOf(Objects.requireNonNull(byMetric, "byMetric"));
    }

    public static UsageSummary none() {
        return new UsageSummary(Map.of());
    }

    public Measure of(String metric) {
        return byMetric.getOrDefault(metric, Measure.NONE);
    }

    public Map<String, Measure> byMetric() {
        return byMetric;
    }
}
