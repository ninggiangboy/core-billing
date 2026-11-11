package dev.ngb.core_billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.infrastructure.outbox.OutboxRelay;
import dev.ngb.core_billing.invoicing.application.cycle.command.CloseBillingCycleUseCase;
import dev.ngb.core_billing.invoicing.application.subscription.command.CreateSubscriptionUseCase;
import dev.ngb.core_billing.money.MoneyTestWorld;
import dev.ngb.core_billing.money.MoneyTestWorld.World;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.pricing.PricingTestWorld;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.pricing.application.rating.command.ChargeFromUsageUseCase;
import dev.ngb.core_billing.pricing.domain.plan.PricingPlan;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * A Hybrid plan bills its base through the subscription and its usage through charges from usage, and both land on
 * the one invoice of the period: the base is charged once per cycle and never again with the usage.
 */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class HybridInvoiceIntegrationTest {

    private static final Audit AUDIT = new Audit("user/test", "req-1");
    private static final long BASE = 2_900;

    @Autowired MoneyTestWorld worlds;
    @Autowired PricingTestWorld pricing;
    @Autowired CreateSubscriptionUseCase create;
    @Autowired ChargeFromUsageUseCase chargeUsage;
    @Autowired OutboxRelay relay;
    @Autowired CloseBillingCycleUseCase closeCycle;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;

    @Test
    void theBaseAndTheUsageAreTwoLinesOfTheSameInvoice() {
        World world = worlds.postpaid(1_000_000);
        var tenant = world.tenant().id();
        String storage = pricing.metric();
        String calls = pricing.metric();
        PricingPlan plan = pricing.plan(tenant, "cloud", pricing.cloudPackage(storage, calls), BillingCycle.MONTHLY, null, null);

        var subscription = scopes.inTenant(tenant, () -> create.execute(new CreateSubscriptionUseCase.Command(world.wallet().id(), plan.id().value(),
                "sub-" + UUID.randomUUID(), AUDIT, null))).subscription();
        assertThat(subscription.periodsCharged()).isEqualTo(1);

        Instant now = Instant.now();
        pricing.record(tenant, world.customerId(), calls, "call", "1000", now.minusSeconds(30));
        var rated = scopes.inTenant(tenant, () -> chargeUsage.execute(new ChargeFromUsageUseCase.Command(world.customerId(), world.wallet().id(), plan.id(), null,
                now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)))));
        assertThat(rated.outcome()).isEqualTo(ChargeFromUsageUseCase.Outcome.CHARGED);
        assertThat(rated.rating().amount().minorUnits()).as("usage only: the base is not charged again").isEqualTo(50);
        assertThat(owner.jdbc().queryForObject("select balance from wallets where id = ?", Long.class, world.wallet().id().value())).isEqualTo(-(BASE + 50));

        scopes.runInTenant(tenant, relay::relayDue);
        owner.jdbc().update("update invoice_items set cycle_start = cycle_start - interval '1 month', cycle_end = cycle_end - interval '1 month' where tenant_id = ?",
                tenant.value());
        assertThat(scopes.inTenant(tenant, closeCycle::runOnce).invoiced()).isEqualTo(1);

        Map<String, Object> invoice = owner.jdbc().queryForMap("select id, total, status from invoices where tenant_id = ?", tenant.value());
        assertThat(((Number) invoice.get("total")).longValue()).isEqualTo(BASE + 50);
        List<Map<String, Object>> lines = owner.jdbc().queryForList("select source, amount, subscription_id, description from invoice_items where invoice_id = ? order by amount desc",
                invoice.get("id"));
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).containsEntry("source", "SUBSCRIPTION_PERIOD");
        assertThat(((Number) lines.get(0).get("amount")).longValue()).isEqualTo(BASE);
        assertThat(lines.get(0).get("subscription_id")).isEqualTo(subscription.id().value());
        assertThat(lines.get(1)).containsEntry("source", "CHARGE");
        assertThat(((Number) lines.get(1).get("amount")).longValue()).isEqualTo(50);
        assertThat((String) lines.get(1).get("description")).startsWith("Usage (plan:cloud-");
    }
}
