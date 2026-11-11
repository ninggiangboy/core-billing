package dev.ngb.core_billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.infrastructure.messaging.OutboxRelay;
import dev.ngb.core_billing.invoicing.application.cycle.command.CloseBillingCycleUseCase;
import dev.ngb.core_billing.invoicing.application.invoice.command.InvoiceChargeUseCase;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceStatus;
import dev.ngb.core_billing.invoicing.domain.invoice.Settlement;
import dev.ngb.core_billing.money.MoneyTestWorld;
import dev.ngb.core_billing.money.MoneyTestWorld.World;
import dev.ngb.core_billing.money.api.charge.ChargeCommand;
import dev.ngb.core_billing.money.api.charge.ChargeResult;
import dev.ngb.core_billing.money.api.charge.ChargeStatus;
import dev.ngb.core_billing.money.api.charge.ChargeWallet;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** An invoice for one standalone charge, on request. */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class InvoiceChargeIntegrationTest {

    private static final Audit AUDIT = new Audit("user/test", "req-1");

    @Autowired MoneyTestWorld worlds;
    @Autowired ChargeWallet charges;
    @Autowired InvoiceChargeUseCase invoiceCharge;
    @Autowired OutboxRelay relay;
    @Autowired CloseBillingCycleUseCase closeCycle;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;

    private ChargeResult charge(World world, long amount) {
        return scopes.inTenant(world.tenant().id(), () -> charges.charge(new ChargeCommand(world.wallet().id(), null, Money.of(amount, MoneyTestWorld.USD),
                null, "one-off work", "charge-" + UUID.randomUUID())));
    }

    private InvoiceChargeUseCase.Result invoice(World world, ChargeResult charged) {
        return scopes.inTenant(world.tenant().id(), () -> invoiceCharge.execute(new InvoiceChargeUseCase.Command(charged.id(), AUDIT, null)));
    }

    private long balance(World world) {
        return owner.jdbc().queryForObject("select balance from wallets where id = ?", Long.class, world.wallet().id().value());
    }

    @Test
    void aPrepaidChargeGetsAPaidInvoiceBecauseTheWalletAlreadyPaidIt() {
        World world = worlds.prepaid(10_000);
        ChargeResult charged = charge(world, 1_200);

        InvoiceChargeUseCase.Result result = invoice(world, charged);

        assertThat(result.replayed()).isFalse();
        assertThat(result.invoice().status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(result.invoice().settlement()).isEqualTo(Settlement.WALLET);
        assertThat(result.invoice().number()).isEqualTo("INV-000001");
        assertThat(result.invoice().total()).isEqualTo(Money.of(1_200, MoneyTestWorld.USD));
        assertThat(result.invoice().items()).singleElement().satisfies(item -> {
            assertThat(item.chargeId()).isEqualTo(charged.id().value());
            assertThat(item.description()).isEqualTo("one-off work");
        });
        assertThat(balance(world)).isEqualTo(10_000 - 1_200);
        assertThat(owner.count("select count(*) from outbox where tenant_id = ? and type = 'invoice.created'", world.tenant().id().value())).isEqualTo(1);
        assertThat(owner.count("select count(*) from outbox where tenant_id = ? and type = 'invoice.paid'", world.tenant().id().value())).isEqualTo(1);
    }

    @Test
    void aPostpaidChargeGetsAnOpenInvoiceForTheDebtItCreated() {
        World world = worlds.postpaid(100_000);
        ChargeResult charged = charge(world, 2_000);

        InvoiceChargeUseCase.Result result = invoice(world, charged);

        assertThat(result.invoice().status()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(result.invoice().settlement()).isNull();
        assertThat(balance(world)).isEqualTo(-2_000);
        assertThat(owner.count("select count(*) from outbox where tenant_id = ? and type = 'invoice.paid'", world.tenant().id().value())).isZero();
    }

    @Test
    void askingAgainReturnsTheSameInvoice() {
        World world = worlds.postpaid(100_000);
        ChargeResult charged = charge(world, 2_000);

        InvoiceChargeUseCase.Result first = invoice(world, charged);
        InvoiceChargeUseCase.Result again = invoice(world, charged);

        assertThat(again.replayed()).isTrue();
        assertThat(again.invoice().id()).isEqualTo(first.invoice().id());
        assertThat(owner.count("select count(*) from invoices where tenant_id = ?", world.tenant().id().value())).isEqualTo(1);
    }

    @Test
    void aChargeWaitingForItsCycleMovesOntoTheInvoiceAndIsNotBilledTwice() {
        World world = worlds.postpaid(100_000);
        ChargeResult charged = charge(world, 2_000);
        scopes.runInTenant(world.tenant().id(), relay::relayDue);
        assertThat(owner.count("select count(*) from invoice_items where tenant_id = ? and invoice_id is null", world.tenant().id().value())).isEqualTo(1);

        InvoiceChargeUseCase.Result result = invoice(world, charged);
        assertThat(result.invoice().items()).hasSize(1);
        owner.jdbc().update("update invoice_items set cycle_start = cycle_start - interval '1 month', cycle_end = cycle_end - interval '1 month' "
                + "where tenant_id = ? and invoice_id is null", world.tenant().id().value());
        var report = scopes.inTenant(world.tenant().id(), closeCycle::runOnce);

        assertThat(report.invoiced()).isZero();
        assertThat(owner.count("select count(*) from invoices where tenant_id = ?", world.tenant().id().value())).isEqualTo(1);
        assertThat(owner.count("select count(*) from invoice_items where tenant_id = ?", world.tenant().id().value())).isEqualTo(1);
    }

    @Test
    void aChargeAlreadyOnACycleInvoiceReturnsThatInvoice() {
        World world = worlds.postpaid(100_000);
        ChargeResult charged = charge(world, 2_000);
        scopes.runInTenant(world.tenant().id(), relay::relayDue);
        owner.jdbc().update("update invoice_items set cycle_start = cycle_start - interval '1 month', cycle_end = cycle_end - interval '1 month' "
                + "where tenant_id = ?", world.tenant().id().value());
        scopes.inTenant(world.tenant().id(), closeCycle::runOnce);

        InvoiceChargeUseCase.Result result = invoice(world, charged);

        assertThat(result.replayed()).isTrue();
        assertThat(result.invoice().origin().name()).isEqualTo("POSTPAID_CYCLE");
    }

    @Test
    void aFailedChargeCannotBeInvoiced() {
        World world = worlds.prepaid(100);
        ChargeResult failed = charge(world, 5_000);
        assertThat(failed.status()).isEqualTo(ChargeStatus.FAILED);

        assertThatThrownBy(() -> invoice(world, failed)).isInstanceOf(ConflictException.class).hasMessageContaining("failed");
        assertThat(owner.count("select count(*) from invoices where tenant_id = ?", world.tenant().id().value())).isZero();
    }

    @Test
    void anotherTenantsChargeIsNotFound() {
        World world = worlds.postpaid(100_000);
        World stranger = worlds.postpaid(100_000);
        ChargeResult charged = charge(world, 2_000);

        assertThatThrownBy(() -> scopes.inTenant(stranger.tenant().id(), () -> invoiceCharge.execute(new InvoiceChargeUseCase.Command(charged.id(), AUDIT, null))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void twoRequestsAtOnceCreateOneInvoice() throws Exception {
        World world = worlds.postpaid(100_000);
        ChargeResult charged = charge(world, 2_000);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> invoice(world, charged).invoice().id());
            var second = pool.submit(() -> invoice(world, charged).invoice().id());
            assertThat(first.get()).isEqualTo(second.get());
        } finally {
            pool.shutdownNow();
        }
        assertThat(owner.count("select count(*) from invoices where tenant_id = ?", world.tenant().id().value())).isEqualTo(1);
    }
}
