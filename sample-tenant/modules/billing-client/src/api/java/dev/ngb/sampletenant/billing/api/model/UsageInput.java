package dev.ngb.sampletenant.billing.api.model;

/** An expected quantity for a pure cost estimate. */
public record UsageInput(String metric, String quantity, Long events) {}
