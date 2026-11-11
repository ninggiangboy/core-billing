package dev.ngb.core_billing.invoicing.adapter.persistence.subscription;

import dev.ngb.core_billing.invoicing.domain.subscription.Subscription;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionId;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionStatus;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("subscriptions")
record SubscriptionRow(
        @Id UUID id,
        UUID tenantId,
        UUID customerId,
        UUID walletId,
        String currency,
        UUID planId,
        String planCode,
        String cycle,
        Instant anchor,
        int periodsCharged,
        String status,
        boolean cancelAtPeriodEnd,
        Instant canceledAt,
        Instant nextRenewalAt,
        int renewalAttempts,
        Instant createdAt,
        Instant updatedAt,
        String idempotencyKey,
        String requestHash) {

    static SubscriptionRow from(Subscription s) {
        return new SubscriptionRow(s.id().value(), s.tenantId().value(), s.customerId().value(), s.walletId().value(), s.currency().code(), s.planId(),
                s.planCode(), s.cycle().name(), s.anchor(), s.periodsCharged(), s.status().name(), s.cancelAtPeriodEnd(), s.canceledAt(), s.nextRenewalAt(),
                s.renewalAttempts(), s.createdAt(), s.updatedAt(), s.idempotencyKey(), s.requestHash());
    }

    Subscription toDomain() {
        return Subscription.restore(new SubscriptionId(id), new TenantId(tenantId), new CustomerId(customerId), new WalletId(walletId),
                Currency.of(currency.strip()), planId, planCode, BillingCycle.valueOf(cycle), anchor, periodsCharged, SubscriptionStatus.valueOf(status),
                cancelAtPeriodEnd, canceledAt, nextRenewalAt, renewalAttempts, createdAt, updatedAt, idempotencyKey, requestHash);
    }
}
