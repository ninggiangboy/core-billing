package dev.ngb.sampletenant.billing.api.model;

import java.time.Instant;

/** A request for payment of debt a wallet already owes. */
public record Invoice(
        String id,
        String number,
        String customerId,
        String walletId,
        String status,
        String origin,
        Amount total,
        Instant periodStart,
        Instant periodEnd,
        String settlement,
        String voidReason,
        Instant createdAt,
        Instant issuedAt,
        Instant paidAt,
        Instant voidedAt) {}
