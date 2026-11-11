package dev.ngb.core_billing.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.infrastructure.outbox.OutboxRelay;
import dev.ngb.core_billing.invoicing.application.cycle.command.CloseBillingCycleUseCase;
import dev.ngb.core_billing.invoicing.application.subscription.command.CreateSubscriptionUseCase;
import dev.ngb.core_billing.invoicing.application.subscription.command.RenewSubscriptionUseCase;
import dev.ngb.core_billing.money.MoneyTestWorld;
import dev.ngb.core_billing.money.MoneyTestWorld.World;
import dev.ngb.core_billing.money.api.charge.ChargeCommand;
import dev.ngb.core_billing.money.api.charge.ChargeWallet;
import dev.ngb.core_billing.money.api.reservation.ReserveBudget;
import dev.ngb.core_billing.money.api.reservation.ReserveBudgetCommand;
import dev.ngb.core_billing.money.application.reservation.command.ExpireReservationsUseCase;
import dev.ngb.core_billing.payment.PaymentTestWorld;
import dev.ngb.core_billing.payment.PaymentTestWorld.PayWorld;
import dev.ngb.core_billing.payment.application.payment.command.CreateTopUpUseCase;
import dev.ngb.core_billing.payment.application.stripe.command.ReceiveStripeWebhookUseCase;
import dev.ngb.core_billing.payment.domain.payment.Payment;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.pricing.PricingTestWorld;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import dev.ngb.core_billing.support.FakePaymentProviderConfig;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import dev.ngb.core_billing.support.StripeEvents;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Every money flow that touches one wallet at once, on real PostgreSQL. The assertion is the database's own
 * deadlock counter: a consistent lock order and short coordination transactions must leave it at zero.
 */
@SpringBootTest(classes = Application.class)
@Import({PostgresContainerConfig.class, FakePaymentProviderConfig.class})
class DeadlockStressIntegrationTest {

    private static final Audit AUDIT = new Audit("user/test", "req-stress");
    private static final Currency USD = MoneyTestWorld.USD;

    @Autowired MoneyTestWorld worlds;
    @Autowired PricingTestWorld pricing;
    @Autowired PaymentTestWorld payments;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;
    @Autowired ChargeWallet chargeWallet;
    @Autowired ReserveBudget reserveBudget;
    @Autowired CreateSubscriptionUseCase createSubscription;
    @Autowired RenewSubscriptionUseCase renew;
    @Autowired ExpireReservationsUseCase expireReservations;
    @Autowired CloseBillingCycleUseCase closeCycle;
    @Autowired CreateTopUpUseCase topUp;
    @Autowired ReceiveStripeWebhookUseCase stripeWebhooks;
    @Autowired OutboxRelay relay;

    @Test
    void chargeRenewalExpiryCycleCloseAndSettlementOnOneWalletNeverDeadlock() throws Exception {
        World world = worlds.postpaid(50_000_000);
        WalletId wallet = world.wallet().id();
        PayWorld pay = payments.directFor(world, "acct_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));

        var plan = pricing.plan(world.tenant().id(), "stress", pricing.pro(), BillingCycle.MONTHLY, null, null);
        var subscription = scopes.inTenant(world.tenant().id(), () -> createSubscription.execute(
                new CreateSubscriptionUseCase.Command(wallet, plan.id().value(), "sub-" + UUID.randomUUID(), AUDIT, null))).subscription();
        owner.jdbc().update("update subscriptions set next_renewal_at = now() - interval '1 second' where id = ?", subscription.id().value());

        var held = scopes.inTenant(world.tenant().id(), () -> reserveBudget.reserve(
                new ReserveBudgetCommand(wallet, Money.of(1_000, USD), Duration.ofMinutes(5), "vm", "res-" + UUID.randomUUID())));
        owner.jdbc().update(
                "update reservations set expires_at = now() - interval '1 second', created_at = now() - interval '1 minute' where id = ?",
                held.id().value());

        scopes.inTenant(world.tenant().id(), () -> chargeWallet.charge(
                new ChargeCommand(wallet, null, Money.of(700, USD), null, "work", "stress-" + UUID.randomUUID())));
        scopes.runInTenant(world.tenant().id(), relay::relayDue);
        owner.jdbc().update(
                "update invoice_items set cycle_start = cycle_start - interval '1 month', cycle_end = cycle_end - interval '1 month'"
                        + " where tenant_id = ? and invoice_id is null",
                world.tenant().id().value());

        owner.jdbc().execute("select pg_stat_reset()");

        List<String> settlementBodies = new ArrayList<>();
        List<String> signatures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long amount = 10_000 + i;
            Payment payment = scopes.inTenant(world.tenant().id(), () -> topUp.execute(new CreateTopUpUseCase.Command(
                    wallet, Money.of(amount, USD), "topup-" + UUID.randomUUID(), AUDIT))).payment();
            String body = StripeEvents.paidCheckout(
                    StripeEvents.newEventId(), null, payment.checkoutSessionId(),
                    "pi_" + UUID.randomUUID().toString().replace("-", ""), payment.id().toString(), payment.amount().minorUnits());
            settlementBodies.add(body);
            signatures.add(StripeEvents.sign(pay.webhookSecret(), body));
        }

        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            tasks.add(() -> {
                for (int i = 0; i < 30; i++) {
                    scopes.inTenant(world.tenant().id(), () -> chargeWallet.charge(new ChargeCommand(
                            wallet, null, Money.of(100, USD), null, "stress", "stress-charge-" + UUID.randomUUID())));
                }
                return null;
            });
            tasks.add(() -> {
                for (int i = 0; i < 10; i++) {
                    scopes.inTenant(world.tenant().id(), renew::runDue);
                }
                return null;
            });
            tasks.add(() -> {
                for (int i = 0; i < 20; i++) {
                    scopes.inTenant(world.tenant().id(), () -> expireReservations.execute(10));
                }
                return null;
            });
            tasks.add(() -> {
                for (int i = 0; i < 10; i++) {
                    scopes.inTenant(world.tenant().id(), closeCycle::runOnce);
                }
                return null;
            });
            for (int i = 0; i < settlementBodies.size(); i++) {
                String body = settlementBodies.get(i);
                String signature = signatures.get(i);
                tasks.add(() -> {
                    stripeWebhooks.receiveDirect(pay.configuration().id(), body, signature);
                    return null;
                });
            }

            List<Future<Void>> futures = tasks.stream().map(pool::submit).toList();
            for (Future<Void> future : futures) {
                future.get(180, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(deadlocks()).as("pg_stat_database.deadlocks after the stress").isZero();
        assertThat(owner.count("select count(*) from invoices where tenant_id = ?", world.tenant().id().value()))
                .as("the cycle closed once").isEqualTo(1);
    }

    private long deadlocks() {
        Long value = owner.jdbc().queryForObject(
                "select deadlocks from pg_stat_database where datname = current_database()", Long.class);
        return value == null ? 0 : value;
    }
}
