package dev.ngb.sampletenant.billing.api.model;

import java.util.List;

/** The outcome of charging a period's usage. */
public record Rating(
        String outcome,
        String id,
        String planId,
        Integer planVersion,
        Amount amount,
        String chargeId,
        String failureReason,
        Integer usageCount,
        java.time.Instant periodStart,
        java.time.Instant periodEnd,
        List<Line> lines,
        Boolean replayed) {}
