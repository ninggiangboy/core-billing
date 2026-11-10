package io.corebilling.pricing.application.rating.query;

import io.corebilling.pricing.domain.rating.RatingStatus;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.WalletId;
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
