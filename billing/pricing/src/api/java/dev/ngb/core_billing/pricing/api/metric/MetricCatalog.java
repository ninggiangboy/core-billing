package dev.ngb.core_billing.pricing.api.metric;

import java.util.Optional;

/**
 * Which unit a metric is measured in, as fixed by the pricing plans of the current tenant. Usage is validated
 * against it. It answers within the current tenant scope.
 */
public interface MetricCatalog {

    Optional<String> unitOf(String metric);
}
