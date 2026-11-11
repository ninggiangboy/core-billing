package dev.ngb.core_billing.invoicing.application.subscription.query;

import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionStatus;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.UUID;

/**
 * A subscription as the read API shows it. The current period is the last one that was paid for (null before the first
 * charge), and {@code nextRenewalAt} is null once it is canceled or Billing has stopped retrying.
 */
public record SubscriptionView(
        UUID id,
        CustomerId customerId,
        WalletId walletId,
        UUID planId,
        String planCode,
        BillingCycle cycle,
        SubscriptionStatus status,
        Instant anchor,
        int periodsCharged,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Instant canceledAt,
        Instant nextRenewalAt,
        int renewalAttempts,
        Instant createdAt) {}
