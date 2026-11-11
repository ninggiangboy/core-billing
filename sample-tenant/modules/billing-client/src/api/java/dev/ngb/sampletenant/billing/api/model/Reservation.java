package dev.ngb.sampletenant.billing.api.model;

import java.time.Instant;

/** A budget hold. It posts nothing to the Ledger until it is captured or charged. */
public record Reservation(
        String id,
        String walletId,
        String status,
        Amount amount,
        Amount captured,
        Instant expiresAt,
        String reference,
        Instant createdAt,
        Instant closedAt,
        Boolean replayed) {}
