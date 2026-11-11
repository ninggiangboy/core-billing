package dev.ngb.core_billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The worker's short lock budget must not turn a wallet that is busy into a failed renewal: the period stays due,
 * the retry budget is untouched and the tenant hears nothing.
 */
@SpringBootTest(
        classes = Application.class,
        properties = "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '300ms'")
@Import(PostgresContainerConfig.class)
class RenewalLockContentionIntegrationTest {

    private static final Audit AUDIT = new Audit("user/test", "req-lock");

    @Autowired MoneyTestWorld worlds;
    @Autowired PricingTestWorld pricing;
    @Autowired CreateSubscriptionUseCase create;
    @Autowired RenewSubscriptionUseCase renew;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;

    @Test
    void aWalletLockWaitLeavesThePeriodDueForTheNextTick() throws SQLException {
        World world = worlds.prepaid(50_000);
        UUID plan = pricing.plan(world.tenant().id(), "pro", pricing.pro(), BillingCycle.MONTHLY, null, null).id().value();
        Subscription subscription = scopes.inTenant(world.tenant().id(), () -> create.execute(
                new CreateSubscriptionUseCase.Command(world.wallet().id(), plan, "sub-" + UUID.randomUUID(), AUDIT, null))).subscription();
        owner.jdbc().update("update subscriptions set next_renewal_at = now() - interval '1 second' where id = ?", subscription.id().value());

        try (Connection held = owner.jdbc().getDataSource().getConnection()) {
            held.setAutoCommit(false);
            try (Statement lock = held.createStatement()) {
                lock.execute("select id from wallets where id = '%s' for update".formatted(world.wallet().id().value()));
            }
            try {
                assertThat(scopes.inTenant(world.tenant().id(), renew::runDue))
                        .as("a lock wait that times out is not a failed attempt")
                        .isEqualTo(new RenewSubscriptionUseCase.Report(0, 0, 0));
            } finally {
                held.rollback();
            }
        }

        assertThat(owner.jdbc().queryForMap(
                "select status, renewal_attempts, periods_charged, next_renewal_at < now() as still_due from subscriptions where id = ?",
                subscription.id().value()))
                .as("the period is untouched and still due")
                .containsEntry("status", "ACTIVE")
                .containsEntry("renewal_attempts", 0)
                .containsEntry("periods_charged", 1)
                .containsEntry("still_due", true);
        assertThat(owner.count("select count(*) from subscription_events where subscription_id = ? and type = 'PAYMENT_FAILED'", subscription.id().value()))
                .as("the tenant is not told a payment failed")
                .isZero();
        assertThat(owner.count("select count(*) from outbox where tenant_id = ? and type = 'subscription.payment_failed'", world.tenant().id().value()))
                .isZero();

        assertThat(scopes.inTenant(world.tenant().id(), renew::runDue).renewed())
                .as("the next tick charges the period once the wallet is free")
                .isEqualTo(1);
    }
}
