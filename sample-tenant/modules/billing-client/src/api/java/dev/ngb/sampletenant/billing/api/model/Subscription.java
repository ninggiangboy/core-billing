package dev.ngb.sampletenant.billing.api.model;

import java.time.Instant;

/** A recurring subscription: the plan, wallet and cycle are fixed when it is created. */
public record Subscription(
        String id,
        String customerId,
        String walletId,
        String planId,
        String planCode,
        String cycle,
        String status,
        Instant anchor,
        Integer periodsCharged,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Boolean cancelAtPeriodEnd,
        Instant canceledAt,
        Instant nextRenewalAt,
        Integer renewalAttempts,
        Instant createdAt) {}
