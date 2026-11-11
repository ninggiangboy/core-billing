package dev.ngb.sampletenant.billing.api.model;

import java.util.List;

/** A pure estimate that writes nothing. */
public record Estimate(
        String planId,
        String planCode,
        int planVersion,
        Amount amount,
        String exactAmount,
        String adjustment,
        List<Line> lines) {}
