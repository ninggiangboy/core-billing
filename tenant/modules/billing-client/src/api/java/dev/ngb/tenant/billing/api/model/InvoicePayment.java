package dev.ngb.tenant.billing.api.model;

import java.time.Instant;

/** A payment started for an invoice. */
public record InvoicePayment(
        String paymentId,
        Amount amount,
        String status,
        String failureReason,
        String ledgerEntryId,
        Instant startedAt,
        Instant settledAt) {}
