package dev.ngb.billing.usage.application.usage.port;

import java.util.Optional;

/**
 * Which unit a metric is measured in. Pricing owns the answer (the plan that prices a metric fixes its unit), and
 * the wiring layer connects the two, so usage does not depend on pricing.
 */
public interface MetricUnits {

    Optional<String> unitOf(String metric);
}
