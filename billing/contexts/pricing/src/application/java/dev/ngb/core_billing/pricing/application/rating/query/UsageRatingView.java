package dev.ngb.core_billing.pricing.application.rating.query;

import dev.ngb.core_billing.pricing.domain.rating.RatingStatus;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A stored rating and the amount it accounted for, shaped for the GET handler. */
public record UsageRatingView(
        UUID id,
        CustomerId customerId,
        WalletId walletId,
        UUID planId,
        int planVersion,
        String setKey,
        int attempt,
        Instant periodStart,
        Instant periodEnd,
        int usageCount,
        Money amount,
        RatingStatus status,
        UUID chargeId,
        String failureReason,
        List<PriceLineView> breakdown,
        Instant createdAt) {}
