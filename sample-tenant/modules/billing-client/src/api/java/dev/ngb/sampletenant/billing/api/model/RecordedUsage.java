package dev.ngb.sampletenant.billing.api.model;

/** The recorded event and whether this call replayed an earlier one. */
public record RecordedUsage(UsageEvent event, boolean replayed) {}
