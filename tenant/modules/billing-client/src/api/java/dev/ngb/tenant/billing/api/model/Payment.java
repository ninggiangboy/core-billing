package dev.ngb.tenant.billing.api.model;

import java.time.Instant;

/** A wallet top-up or invoice payment and its provider state. */
public record Payment(
        String id,
        String walletId,
        String customerId,
        String status,
        Amount amount,
        Amount refunded,
        String checkoutUrl,
        String failureReason,
        String ledgerEntryId,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {}
