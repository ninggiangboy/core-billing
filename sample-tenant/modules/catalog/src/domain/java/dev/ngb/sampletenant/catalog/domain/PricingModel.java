package dev.ngb.sampletenant.catalog.domain;

/** The pricing models Billing supports; this system names the one each resource type uses. */
public enum PricingModel {
    TIME_BASED,
    USAGE_BASED,
    WORKLOAD_BASED,
    EVENT_BASED,
    SUBSCRIPTION,
    HYBRID
}
