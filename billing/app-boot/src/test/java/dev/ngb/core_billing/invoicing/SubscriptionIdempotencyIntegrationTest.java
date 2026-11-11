package dev.ngb.core_billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.invoicing.application.subscription.command.CreateSubscriptionUseCase;
import dev.ngb.core_billing.invoicing.application.subscription.command.RenewSubscriptionUseCase;
import dev.ngb.core_billing.invoicing.domain.subscription.Subscription;
import dev.ngb.core_billing.money.MoneyTestWorld;
import dev.ngb.core_billing.money.MoneyTestWorld.World;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.pricing.PricingTestWorld;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * A period is charged once however the renewal is repeated: a second run, two workers at once, a failure half way
 * through, or a bad row written behind the application's back.
 */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class SubscriptionIdempotencyIntegrationTest {

    private static final Audit AUDIT = new Audit("user/test", "req-1");
    private static final long PRICE = 2_900;

    @Autowired MoneyTestWorld worlds;
    @Autowired PricingTestWorld pricing;
    @Autowired CreateSubscriptionUseCase create;
    @Autowired RenewSubscriptionUseCase renew;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;

    private Subscription subscribe(World world) {
        UUID plan = pricing.plan(world.tenant().id(), "pro", pricing.pro(), BillingCycle.MONTHLY, null, null).id().value();
        return scopes.inTenant(world.tenant().id(), () -> create.execute(new CreateSubscriptionUseCase.Command(world.wallet().id(), plan,
                "sub-" + UUID.randomUUID(), AUDIT, null))).subscription();
    }

    private void aMonthPasses(Subscription subscription) {
        owner.jdbc().update("update subscriptions set anchor = anchor - interval '1 month', next_renewal_at = next_renewal_at - interval '1 month' where id = ?",
                subscription.id().value());
    }

    private long balance(World world) {
        return owner.jdbc().queryForObject("select balance from wallets where id = ?", Long.class, world.wallet().id().value());
    }

    private long succeededCharges(Subscription subscription, int period) {
        return owner.count("select count(*) from charges where status = 'SUCCEEDED' and reference = ?", "subscription:" + subscription.id() + ":" + period);
    }

    @Test
    void renewingTheSamePeriodRepeatedlyChargesItOnce() {
        World world = worlds.prepaid(100_000);
        Subscription subscription = subscribe(world);
        aMonthPasses(subscription);

        RenewSubscriptionUseCase.Outcome first = scopes.inTenant(world.tenant().id(), () -> renew.renewIfDue(subscription.id()));
        RenewSubscriptionUseCase.Outcome second = scopes.inTenant(world.tenant().id(), () -> renew.renewIfDue(subscription.id()));
        scopes.inTenant(world.tenant().id(), renew::runDue);

        assertThat(first).isEqualTo(RenewSubscriptionUseCase.Outcome.RENEWED);
        assertThat(second).isEqualTo(RenewSubscriptionUseCase.Outcome.NOT_DUE);
        assertThat(succeededCharges(subscription, 1)).isEqualTo(1);
        assertThat(balance(world)).isEqualTo(100_000 - 2 * PRICE);
    }

    @Test
    void concurrentRenewalsOfOneSubscriptionChargeItOnce() throws Exception {
        World world = worlds.prepaid(100_000);
        Subscription subscription = subscribe(world);
        aMonthPasses(subscription);

        Callable<RenewSubscriptionUseCase.Outcome> attempt = () -> scopes.inTenant(world.tenant().id(), () -> renew.renewIfDue(subscription.id()));
        var pool = Executors.newFixedThreadPool(4);
        try {
            var results = new java.util.ArrayList<java.util.concurrent.Future<RenewSubscriptionUseCase.Outcome>>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(attempt));
            }
            long renewed = 0;
            for (var result : results) {
                if (result.get() == RenewSubscriptionUseCase.Outcome.RENEWED) {
                    renewed++;
                }
            }
            assertThat(renewed).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(succeededCharges(subscription, 1)).isEqualTo(1);
        assertThat(balance(world)).isEqualTo(100_000 - 2 * PRICE);
    }

    @Test
    void theDatabaseRefusesASecondRenewedRecordForOnePeriod() {
        World world = worlds.prepaid(100_000);
        Subscription subscription = subscribe(world);

        assertThatThrownBy(() -> owner.jdbc().update("insert into subscription_events (id, tenant_id, subscription_id, type, period_number, occurred_at) "
                + "values (?, ?, ?, 'RENEWED', 0, now())", UUID.randomUUID(), world.tenant().id().value(), subscription.id().value()))
                .hasMessageContaining("subscription_events_renewed_once_idx");
    }

    @Test
    void aFailureAfterTheChargeRollsTheChargeBackAndALaterRunChargesThePeriodOnce() {
        World world = worlds.prepaid(100_000);
        Subscription subscription = subscribe(world);
        aMonthPasses(subscription);
        // A stray record for period 1 makes the history insert fail after the wallet was already charged in the same transaction.
        UUID stray = UUID.randomUUID();
        owner.jdbc().update("insert into subscription_events (id, tenant_id, subscription_id, type, period_number, occurred_at) values (?, ?, ?, 'RENEWED', 1, now())",
                stray, world.tenant().id().value(), subscription.id().value());

        RenewSubscriptionUseCase.Report broken = scopes.inTenant(world.tenant().id(), renew::runDue);

        assertThat(broken.failed()).isEqualTo(1);
        assertThat(succeededCharges(subscription, 1)).as("the charge went back with the failed transaction").isZero();
        assertThat(balance(world)).isEqualTo(100_000 - PRICE);
        assertThat(owner.jdbc().queryForObject("select periods_charged from subscriptions where id = ?", Integer.class, subscription.id().value())).isEqualTo(1);

        owner.jdbc().update("delete from subscription_events where id = ?", stray);
        owner.jdbc().update("update subscriptions set next_renewal_at = now() - interval '1 second' where id = ?", subscription.id().value());
        RenewSubscriptionUseCase.Report fixed = scopes.inTenant(world.tenant().id(), renew::runDue);

        assertThat(fixed.renewed()).isEqualTo(1);
        assertThat(succeededCharges(subscription, 1)).isEqualTo(1);
        assertThat(balance(world)).isEqualTo(100_000 - 2 * PRICE);
        assertThat(owner.count("select count(*) from charges where reference = ?", "subscription:" + subscription.id() + ":1")).as("one successful charge, no failed one")
                .isEqualTo(1);
    }

    @Test
    void everyPeriodHasItsOwnChargeAndNoPeriodIsChargedTwiceAcrossSeveralMonths() {
        World world = worlds.prepaid(100_000);
        Subscription subscription = subscribe(world);
        for (int month = 1; month <= 3; month++) {
            aMonthPasses(subscription);
            scopes.inTenant(world.tenant().id(), renew::runDue);
            scopes.inTenant(world.tenant().id(), renew::runDue);
        }

        for (int period = 0; period <= 3; period++) {
            assertThat(succeededCharges(subscription, period)).as("period " + period).isEqualTo(1);
        }
        assertThat(balance(world)).isEqualTo(100_000 - 4 * PRICE);
    }
}
