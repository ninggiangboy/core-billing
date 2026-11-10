package io.corebilling.pricing.domain;

import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The record that a set of usage events was priced under a specific plan version and charged (or came to nothing).
 * It keeps the breakdown, so the amount can be explained later without recomputing it under newer terms.
 */
public record UsageRating(
        RatingId id,
        TenantId tenantId,
        CustomerId customerId,
        WalletId walletId,
        PlanId planId,
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
        List<PriceLine> breakdown,
        Instant createdAt) {

    public UsageRating {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(walletId, "walletId");
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(setKey, "setKey");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(status, "status");
        breakdown = List.copyOf(breakdown);
        Objects.requireNonNull(createdAt, "createdAt");
        if ((status == RatingStatus.NO_CHARGE) != (chargeId == null)) {
            throw new IllegalArgumentException("only a rating that came to nothing has no charge");
        }
        if ((status == RatingStatus.FAILED) != (failureReason != null)) {
            throw new IllegalArgumentException("only a failed rating carries a failure reason");
        }
    }

    /** True when the usage it covers is now accounted for and must not be rated again. */
    public boolean consumesUsage() {
        return status != RatingStatus.FAILED;
    }
}
