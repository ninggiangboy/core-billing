package dev.ngb.sampletenant.billing.api.model;

import java.time.Instant;

/** One invoice line, backed by a succeeded charge. */
public record InvoiceItem(
        String id,
        String source,
        String chargeId,
        String subscriptionId,
        Instant periodStart,
        Instant periodEnd,
        String description,
        Amount amount,
        Instant incurredAt) {}
