package dev.ngb.billing.pricing.application.metric.port;

/** The units of the current tenant's metrics, fixed by the first plan that prices each one. */
public interface MetricRegistry {

    /**
     * Records that {@code metric} is measured in {@code unit}.
     *
     * @return true when the metric was new; false when it was already registered with the same unit
     * @throws dev.ngb.billing.platform.api.error.ConflictException when it is registered with a different unit
     */
    boolean register(String metric, String unit);
}
