package dev.ngb.core_billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.infrastructure.outbox.OutboxRelay;
import dev.ngb.core_billing.invoicing.application.cycle.command.CloseBillingCycleUseCase;
import dev.ngb.core_billing.invoicing.application.subscription.command.CancelSubscriptionUseCase;
import dev.ngb.core_billing.invoicing.application.subscription.command.CreateSubscriptionUseCase;
import dev.ngb.core_billing.invoicing.application.subscription.command.RenewSubscriptionUseCase;
import dev.ngb.core_billing.invoicing.domain.subscription.Subscription;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionId;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionStatus;
import dev.ngb.core_billing.money.MoneyTestWorld;
import dev.ngb.core_billing.money.MoneyTestWorld.World;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.pricing.PricingTestWorld;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The renewal job: due periods are charged once, the schedule moves forward, and a refused charge is retried later. */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class SubscriptionRenewalIntegrationTest {

    private static final Audit AUDIT = new Audit("user/test", "req-1");
    private static final long PRICE = 2_900;

    @Autowired MoneyTestWorld worlds;
    @Autowired PricingTestWorld pricing;
    @Autowired CreateSubscriptionUseCase create;
    @Autowired CancelSubscriptionUseCase cancel;
    @Autowired RenewSubscriptionUseCase renew;
    @Autowired OutboxRelay relay;
    @Autowired CloseBillingCycleUseCase closeCycle;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;

    private Subscription subscribe(World world) {
        UUID plan = pricing.plan(world.tenant().id(), "pro", pricing.pro(), BillingCycle.MONTHLY, null, null).id().value();
        return scopes.inTenant(world.tenant().id(), () -> create.execute(new CreateSubscriptionUseCase.Command(world.wallet().id(), plan,
                "sub-" + UUID.randomUUID(), AUDIT, null))).subscription();
    }

    private RenewSubscriptionUseCase.Report runDue(World world) {
        return scopes.inTenant(world.tenant().id(), renew::runDue);
    }

    /** Moves the subscription's whole timeline one month into the past, so its next period is due now. */
    private void aMonthPasses(Subscription subscription) {
        owner.jdbc().update("update subscriptions set anchor = anchor - interval '1 month', next_renewal_at = next_renewal_at - interval '1 month' where id = ?",
                subscription.id().value());
    }

    private void due(Subscription subscription) {
        owner.jdbc().update("update subscriptions set next_renewal_at = now() - interval '1 second' where id = ?", subscription.id().value());
    }

    private long balance(World world) {
        return owner.jdbc().queryForObject("select balance from wallets where id = ?", Long.class, world.wallet().id().value());
    }

    private int events(Subscription subscription, String type) {
        return (int) owner.count("select count(*) from subscription_events where subscription_id = ? and type = ?", subscription.id().value(), type);
    }

    private Map<String, Object> row(Subscription subscription) {
        return owner.jdbc().queryForMap("select status, periods_charged, renewal_attempts from subscriptions where id = ?", subscription.id().value());
    }

    @Test
    void thePeriodThatComesDueIsChargedOnceAndAnnounced() {
        World world = worlds.prepaid(100_000);
        Subscription subscription = subscribe(world);
        assertThat(balance(world)).isEqualTo(100_000 - PRICE);

        aMonthPasses(subscription);
        RenewSubscriptionUseCase.Report report = runDue(world);

        assertThat(report.renewed()).isEqualTo(1);
        assertThat(balance(world)).isEqualTo(100_000 - 2 * PRICE);
        assertThat(row(subscription)).containsEntry("status", "ACTIVE").containsEntry("periods_charged", 2).containsEntry("renewal_attempts", 0);
        assertThat(events(subscription, "RENEWED")).isEqualTo(2);
        assertThat(owner.count("select count(*) from outbox where tenant_id = ? and type = 'subscription.renewed'", world.tenant().id().value())).isEqualTo(2);
        assertThat(owner.count("select count(*) from charges where tenant_id = ? and reference like ?", world.tenant().id().value(),
                "subscription:" + subscription.id() + ":%")).isEqualTo(2);
    }

    @Test
    void runningTheJobAgainForTheSamePeriodChargesNothing() {
        World world = worlds.prepaid(100_000);
        Subscription subscription = subscribe(world);
        aMonthPasses(subscription);
        runDue(world);

        RenewSubscriptionUseCase.Report again = runDue(world);

        assertThat(again.renewed()).isZero();
        assertThat(balance(world)).isEqualTo(100_000 - 2 * PRICE);
        assertThat(events(subscription, "RENEWED")).isEqualTo(2);
    }

    @Test
    void aPeriodNotYetDueIsLeftAlone() {
        World world = worlds.prepaid(100_000);
        Subscription subscription = subscribe(world);

        assertThat(runDue(world).renewed()).isZero();
        assertThat(row(subscription)).containsEntry("periods_charged", 1);
    }

    @Test
    void aCanceledSubscriptionEndsWhereItsPaidPeriodEndsAndChargesNothingMore() {
        World world = worlds.prepaid(100_000);
        Subscription subscription = subscribe(world);
        Subscription scheduled = scopes.inTenant(world.tenant().id(), () -> cancel.execute(new CancelSubscriptionUseCase.Command(subscription.id(), AUDIT, null)));
        assertThat(scheduled.cancelAtPeriodEnd()).isTrue();
        assertThat(scheduled.status()).isEqualTo(SubscriptionStatus.ACTIVE);

        aMonthPasses(subscription);
        RenewSubscriptionUseCase.Report report = runDue(world);

        assertThat(report.ended()).isEqualTo(1);
        assertThat(report.renewed()).isZero();
        assertThat(row(subscription)).containsEntry("status", "CANCELED").containsEntry("periods_charged", 1);
        assertThat(balance(world)).isEqualTo(100_000 - PRICE);
        assertThat(events(subscription, "CANCEL_REQUESTED")).isEqualTo(1);
        assertThat(events(subscription, "CANCELED")).isEqualTo(1);
    }

    @Test
    void aRefusedChargeIsRetriedLaterAndSucceedsOnceTheWalletCanPay() {
        World world = worlds.prepaid(0);
        Subscription subscription = subscribe(world);
        assertThat(row(subscription)).containsEntry("status", "PAST_DUE").containsEntry("periods_charged", 0).containsEntry("renewal_attempts", 1);
        assertThat(events(subscription, "RENEWED")).isZero();
        assertThat(runDue(world).renewed()).as("not due again yet").isZero();

        worlds.credit(world.tenant(), world.wallet(), 10_000);
        due(subscription);
        RenewSubscriptionUseCase.Report report = runDue(world);

        assertThat(report.renewed()).isEqualTo(1);
        assertThat(row(subscription)).containsEntry("status", "ACTIVE").containsEntry("periods_charged", 1).containsEntry("renewal_attempts", 0);
        assertThat(balance(world)).isEqualTo(10_000 - PRICE);
    }

    @Test
    void aPostpaidRenewalIsDebtThatAppearsAsASubscriptionLineOnThePeriodInvoice() {
        World world = worlds.postpaid(100_000);
        Subscription subscription = subscribe(world);
        scopes.runInTenant(world.tenant().id(), relay::relayDue);
        owner.jdbc().update("update invoice_items set cycle_start = cycle_start - interval '1 month', cycle_end = cycle_end - interval '1 month' where tenant_id = ?",
                world.tenant().id().value());

        assertThat(scopes.inTenant(world.tenant().id(), closeCycle::runOnce).invoiced()).isEqualTo(1);

        Map<String, Object> line = owner.jdbc().queryForMap("select source, subscription_id, amount, period_start, period_end, description from invoice_items "
                + "where tenant_id = ?", world.tenant().id().value());
        assertThat(line).containsEntry("source", "SUBSCRIPTION_PERIOD");
        assertThat(line.get("subscription_id")).isEqualTo(subscription.id().value());
        assertThat(((Number) line.get("amount")).longValue()).isEqualTo(PRICE);
        assertThat(line.get("period_start")).isNotNull();
        assertThat((String) line.get("description")).startsWith("Subscription pro-");
        assertThat(balance(world)).isEqualTo(-PRICE);
    }

    @Test
    void twoWorkersRenewingAtOnceChargeThePeriodOnce() throws Exception {
        World world = worlds.prepaid(100_000);
        Subscription subscription = subscribe(world);
        aMonthPasses(subscription);

        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> runDue(world).renewed());
            var second = pool.submit(() -> runDue(world).renewed());
            assertThat(first.get() + second.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(balance(world)).isEqualTo(100_000 - 2 * PRICE);
        assertThat(new SubscriptionId(subscription.id().value())).isEqualTo(subscription.id());
    }
}
