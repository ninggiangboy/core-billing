package dev.ngb.core_billing.invoicing.domain.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.id.TenantId;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

    static final Instant NOW = Instant.parse("2026-01-31T10:00:00Z");

    static Subscription subscription(BillingCycle cycle) {
        return Subscription.create(new SubscriptionId(UUID.randomUUID()), new TenantId(UUID.randomUUID()), new CustomerId(UUID.randomUUID()),
                new WalletId(UUID.randomUUID()), Currency.of("USD"), UUID.randomUUID(), "pro", cycle, "key", "hash", NOW);
    }

    static Subscription paid(Subscription s, int periods) {
        return Subscription.restore(s.id(), s.tenantId(), s.customerId(), s.walletId(), s.currency(), s.planId(), s.planCode(), s.cycle(), s.anchor(), periods,
                SubscriptionStatus.ACTIVE, false, null, s.cycle().plus(s.anchor(), periods), 0, NOW, NOW, "key", "hash");
    }

    @Test
    void aNewSubscriptionIsActiveAndItsFirstPeriodIsDueAtOnce() {
        Subscription s = subscription(BillingCycle.MONTHLY);
        assertThat(s.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(s.periodsCharged()).isZero();
        assertThat(s.nextRenewalAt()).isEqualTo(NOW);
        assertThat(s.currentPeriodStart()).isNull();
        assertThat(s.currentPeriodEnd()).isNull();
    }

    @Test
    void periodsAreCountedFromTheAnchorSoMonthEndsDoNotDrift() {
        Subscription s = subscription(BillingCycle.MONTHLY);
        assertThat(s.periodStart(0)).isEqualTo(Instant.parse("2026-01-31T10:00:00Z"));
        assertThat(s.periodEnd(0)).isEqualTo(Instant.parse("2026-02-28T10:00:00Z"));
        assertThat(s.periodEnd(1)).isEqualTo(Instant.parse("2026-03-31T10:00:00Z"));
        assertThat(s.periodStart(2)).isEqualTo(s.periodEnd(1));
    }

    @Test
    void theCurrentPeriodIsTheLastOneThatWasPaidFor() {
        Subscription s = paid(subscription(BillingCycle.MONTHLY), 2);
        assertThat(s.currentPeriodStart()).isEqualTo(Instant.parse("2026-02-28T10:00:00Z"));
        assertThat(s.currentPeriodEnd()).isEqualTo(Instant.parse("2026-03-31T10:00:00Z"));
    }

    @Test
    void cancellingBeforeAnythingWasPaidEndsItAtOnce() {
        Subscription s = subscription(BillingCycle.MONTHLY);
        assertThat(s.requestCancel(NOW.plusSeconds(5))).isEqualTo(Subscription.Cancellation.CANCELED_NOW);
        assertThat(s.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(s.canceledAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(s.nextRenewalAt()).isNull();
    }

    @Test
    void cancellingAPaidSubscriptionKeepsThePaidPeriodAndIsRepeatable() {
        Subscription s = paid(subscription(BillingCycle.WEEKLY), 1);
        assertThat(s.requestCancel(NOW)).isEqualTo(Subscription.Cancellation.SCHEDULED);
        assertThat(s.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(s.cancelAtPeriodEnd()).isTrue();
        assertThat(s.requestCancel(NOW)).isEqualTo(Subscription.Cancellation.UNCHANGED);
    }

    @Test
    void aCanceledSubscriptionStaysCanceled() {
        Subscription s = subscription(BillingCycle.DAILY);
        s.cancelNow(NOW);
        s.cancelNow(NOW.plusSeconds(10));
        assertThat(s.canceledAt()).isEqualTo(NOW);
        assertThat(s.requestCancel(NOW)).isEqualTo(Subscription.Cancellation.UNCHANGED);
    }
}
