package dev.ngb.core_billing.pricing.application.metric.query;

import java.util.Optional;

/** The unit a metric is measured in. */
public interface MetricReadRepository {

    Optional<String> unitOf(String metric);
}
