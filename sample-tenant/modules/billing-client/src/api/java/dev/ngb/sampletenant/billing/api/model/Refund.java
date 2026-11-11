package dev.ngb.sampletenant.billing.api.model;

import java.time.Instant;

/** A refund against a payment. */
public record Refund(
        String id,
        String paymentId,
        String status,
        Amount amount,
        String reason,
        String failureReason,
        String ledgerEntryId,
        Instant createdAt,
        Instant completedAt) {}
