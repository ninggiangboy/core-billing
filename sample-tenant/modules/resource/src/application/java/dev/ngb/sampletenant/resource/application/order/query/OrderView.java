package dev.ngb.sampletenant.resource.application.order.query;

import java.time.Instant;
import java.util.UUID;

/** One ordered resource as the order screens render it. */
public record OrderView(
        UUID id,
        UUID customerId,
        String reference,
        UUID planId,
        String billingPlanId,
        String status,
        String walletId,
        String reservationId,
        long estimatedMinorUnits,
        String currency,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {}
