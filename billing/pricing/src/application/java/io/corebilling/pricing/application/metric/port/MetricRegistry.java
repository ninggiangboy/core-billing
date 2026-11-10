package io.corebilling.pricing.application.metric.port;

import java.util.Optional;

/** The units of the current tenant's metrics, fixed by the first plan that prices each one. */
public interface MetricRegistry {

    /**
     * Records that {@code metric} is measured in {@code unit}.
     *
     * @return true when the metric was new; false when it was already registered with the same unit
     * @throws io.corebilling.platform.api.error.ConflictException when it is registered with a different unit
     */
    boolean register(String metric, String unit);

    Optional<String> unitOf(String metric);
}
