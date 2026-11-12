package dev.ngb.tenant.billing.api.model;

import java.time.Instant;

/** A wallet in one currency. Available is the balance minus held reservations. */
public record Wallet(
        String id,
        String customerId,
        String currency,
        String mode,
        Amount balance,
        Amount held,
        Amount available,
        Amount creditLimit,
        Amount outstanding,
        Long lastSequence,
        Instant createdAt) {}
