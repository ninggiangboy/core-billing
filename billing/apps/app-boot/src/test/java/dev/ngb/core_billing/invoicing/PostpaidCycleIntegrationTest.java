package dev.ngb.core_billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.infrastructure.messaging.OutboxRelay;
import dev.ngb.core_billing.invoicing.application.cycle.command.CloseBillingCycleUseCase;
import dev.ngb.core_billing.invoicing.application.cycle.command.CollectChargeUseCase;
import dev.ngb.core_billing.money.MoneyTestWorld;
import dev.ngb.core_billing.money.MoneyTestWorld.World;
import dev.ngb.core_billing.money.api.charge.ChargeCommand;
import dev.ngb.core_billing.money.api.charge.ChargeResult;
import dev.ngb.core_billing.money.api.charge.ChargeWallet;
import dev.ngb.core_billing.sharedkernel.event.EventEnvelope;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Postpaid aggregation: charges become unbilled lines through the outbox, and a closed period becomes one invoice. */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class PostpaidCycleIntegrationTest {

    @Autowired MoneyTestWorld worlds;
    @Autowired ChargeWallet charges;
    @Autowired OutboxRelay relay;
    @Autowired CloseBillingCycleUseCase closeCycle;
    @Autowired CollectChargeUseCase collect;
    @Autowired ScopeTestSupport scopes;
    @Autowired UnitOfWork unitOfWork;
    @Autowired OwnerDatabase owner;

    private ChargeResult charge(World world, long amount) {
        return scopes.inTenant(world.tenant().id(), () -> charges.charge(new ChargeCommand(world.wallet().id(), null, Money.of(amount, MoneyTestWorld.USD),
                null, "work " + amount, "charge-" + UUID.randomUUID())));
    }

    private void relayEvents(World world) {
        scopes.runInTenant(world.tenant().id(), relay::relayDue);
    }

    /** Pretends the lines were incurred in the previous period, which is how a period closes without waiting a month. */
    private void moveLinesToThePreviousMonth(World world) {
        owner.jdbc().update("update invoice_items set cycle_start = cycle_start - interval '1 month', cycle_end = cycle_end - interval '1 month' "
                + "where tenant_id = ? and invoice_id is null", world.tenant().id().value());
    }

    private CloseBillingCycleUseCase.Report close(World world) {
        return scopes.inTenant(world.tenant().id(), closeCycle::runOnce);
    }

    private int count(String sql, World world) {
        return owner.jdbc().queryForObject(sql, Integer.class, world.tenant().id().value());
    }

    @Test
    void severalChargesInAPeriodBecomeOneInvoiceWithCorrectTotals() {
        World world = worlds.postpaid(1_000_000);
        charge(world, 100);
        charge(world, 250);
        charge(world, 650);
        relayEvents(world);
        assertThat(count("select count(*) from invoice_items where tenant_id = ? and invoice_id is null", world)).isEqualTo(3);

        moveLinesToThePreviousMonth(world);
        CloseBillingCycleUseCase.Report report = close(world);

        assertThat(report.invoiced()).isEqualTo(1);
        assertThat(count("select count(*) from invoices where tenant_id = ?", world)).isEqualTo(1);
        Map<String, Object> invoice = owner.jdbc().queryForMap("select number, status, total, origin from invoices where tenant_id = ?", world.tenant().id().value());
        assertThat(invoice).containsEntry("number", "INV-000001").containsEntry("status", "OPEN").containsEntry("origin", "POSTPAID_CYCLE");
        assertThat(((Number) invoice.get("total")).longValue()).isEqualTo(1_000);
        assertThat(count("select count(*) from invoice_items where tenant_id = ? and invoice_id is not null", world)).isEqualTo(3);
        assertThat(count("select count(*) from outbox where tenant_id = ? and type = 'invoice.created'", world)).isEqualTo(1);
    }

    @Test
    void runningTheCycleJobTwiceProducesOneInvoice() {
        World world = worlds.postpaid(1_000_000);
        charge(world, 300);
        charge(world, 700);
        relayEvents(world);
        moveLinesToThePreviousMonth(world);

        assertThat(close(world).invoiced()).isEqualTo(1);
        assertThat(close(world).invoiced()).isZero();

        assertThat(count("select count(*) from invoices where tenant_id = ?", world)).isEqualTo(1);
        assertThat(count("select count(*) from outbox where tenant_id = ? and type = 'invoice.created'", world)).isEqualTo(1);
    }

    @Test
    void twoWorkersClosingTheSamePeriodAtOnceBillItOnce() throws Exception {
        World world = worlds.postpaid(1_000_000);
        charge(world, 300);
        charge(world, 700);
        relayEvents(world);
        moveLinesToThePreviousMonth(world);

        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> close(world).invoiced());
            var second = pool.submit(() -> close(world).invoiced());
            assertThat(first.get() + second.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("select count(*) from invoices where tenant_id = ?", world)).isEqualTo(1);
    }

    @Test
    void aPeriodThatHasNotEndedIsNotInvoicedYet() {
        World world = worlds.postpaid(1_000_000);
        charge(world, 300);
        relayEvents(world);

        assertThat(close(world).invoiced()).isZero();
        assertThat(count("select count(*) from invoices where tenant_id = ?", world)).isZero();
        assertThat(count("select count(*) from invoice_items where tenant_id = ? and invoice_id is null", world)).isEqualTo(1);
    }

    @Test
    void chargesOnAPrepaidWalletAreNeverCollected() {
        World world = worlds.prepaid(100_000);
        charge(world, 300);
        relayEvents(world);

        assertThat(count("select count(*) from invoice_items where tenant_id = ?", world)).isZero();
    }

    @Test
    void aRepeatedChargeEventAddsNothing() {
        World world = worlds.postpaid(1_000_000);
        ChargeResult charged = charge(world, 300);
        EventEnvelope event = envelope(world, charged, Instant.now());

        assertThat(scopes.inTenant(world.tenant().id(), () -> collect.handle(event))).isTrue();
        assertThat(scopes.inTenant(world.tenant().id(), () -> collect.handle(event))).isFalse();
        // The same charge under a new event id is still one line: the charge is what is unique.
        assertThat(scopes.inTenant(world.tenant().id(), () -> collect.handle(envelope(world, charged, Instant.now())))).isFalse();

        assertThat(count("select count(*) from invoice_items where tenant_id = ?", world)).isEqualTo(1);
    }

    @Test
    void aChargeEventWithAnUnreadablePayloadIsIgnoredSoItCannotBlockTheOtherConsumers() {
        World world = worlds.postpaid(1_000_000);
        EventEnvelope garbage = new EventEnvelope(UUID.randomUUID(), world.tenant().id(), "charge.succeeded", "charge/x", Instant.now(), Map.of("n", 1));

        assertThat(scopes.inTenant(world.tenant().id(), () -> collect.handle(garbage))).isFalse();
        assertThat(count("select count(*) from invoice_items where tenant_id = ?", world)).isZero();
    }

    @Test
    void aLateChargeForAnAlreadyInvoicedPeriodWaitsForTheOpenPeriod() {
        World world = worlds.postpaid(1_000_000);
        charge(world, 300);
        relayEvents(world);
        moveLinesToThePreviousMonth(world);
        close(world);
        Instant inTheBilledPeriod = Instant.now().atZone(ZoneOffset.UTC).minusMonths(1).toInstant();

        ChargeResult late = charge(world, 400);
        scopes.inTenant(world.tenant().id(), () -> collect.handle(envelope(world, late, inTheBilledPeriod)));

        List<Map<String, Object>> unbilled = owner.jdbc().queryForList("select cycle_start, cycle_end from invoice_items where tenant_id = ? and invoice_id is null",
                world.tenant().id().value());
        assertThat(unbilled).hasSize(1);
        Instant start = ((java.sql.Timestamp) unbilled.get(0).get("cycle_start")).toInstant();
        assertThat(start).isAfter(inTheBilledPeriod);
        assertThat(close(world).invoiced()).isZero();
    }

    private EventEnvelope envelope(World world, ChargeResult charged, Instant occurredAt) {
        return new EventEnvelope(UUID.randomUUID(), world.tenant().id(), "charge.succeeded", "charge/" + charged.id(), occurredAt, Map.of(
                "chargeId", charged.id().toString(), "walletId", world.wallet().id().toString(), "entryId", charged.ledgerEntryId().toString(),
                "amount", Map.of("minorUnits", charged.amount().minorUnits(), "currency", "USD"), "source", "STANDALONE", "reference", "manual"));
    }
}
