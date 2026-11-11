package dev.ngb.sampletenant.billing.api.model;

import java.time.Instant;

/** One recorded change in a subscription's schedule. */
public record SubscriptionEvent(
        String id,
        String type,
        Integer periodNumber,
        Instant periodStart,
        Instant periodEnd,
        Amount amount,
        String chargeId,
        String detail,
        Instant occurredAt) {}
