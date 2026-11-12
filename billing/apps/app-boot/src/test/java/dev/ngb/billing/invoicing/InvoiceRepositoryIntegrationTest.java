package dev.ngb.billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.Application;
import dev.ngb.billing.invoicing.application.invoice.port.InvoiceRepository;
import dev.ngb.billing.invoicing.application.invoice.service.InvoiceIssuer;
import dev.ngb.billing.invoicing.domain.invoice.Invoice;
import dev.ngb.billing.invoicing.domain.invoice.InvoiceId;
import dev.ngb.billing.invoicing.domain.invoice.InvoiceItem;
import dev.ngb.billing.invoicing.domain.invoice.InvoiceOrigin;
import dev.ngb.billing.invoicing.domain.invoice.InvoiceStatus;
import dev.ngb.billing.invoicing.domain.invoice.ItemSource;
import dev.ngb.billing.money.MoneyTestWorld;
import dev.ngb.billing.money.MoneyTestWorld.World;
import dev.ngb.billing.money.api.charge.ChargeCommand;
import dev.ngb.billing.money.api.charge.ChargeResult;
import dev.ngb.billing.money.api.charge.ChargeWallet;
import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing.support.PostgresContainerConfig;
import dev.ngb.billing.support.ScopeTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Reload-and-compare for every invoice write, the number sequence, and the one-line-one-invoice rule. */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class InvoiceRepositoryIntegrationTest {

    @Autowired UnitOfWork unitOfWork;
    @Autowired InvoiceRepository invoices;
    @Autowired InvoiceIssuer issuer;
    @Autowired MoneyTestWorld worlds;
    @Autowired ChargeWallet charges;
    @Autowired ScopeTestSupport scopes;
    @Autowired Clock clock;

    private ChargeResult charge(World world, long amount) {
        return scopes.inTenant(world.tenant().id(), () -> charges.charge(new ChargeCommand(world.wallet().id(), null, Money.of(amount, MoneyTestWorld.USD), null,
                "test", "charge-" + UUID.randomUUID())));
    }

    private Invoice draftFor(World world, ChargeResult... results) {
        List<InvoiceItem> items = java.util.Arrays.stream(results).map(r -> new InvoiceItem(UUID.randomUUID(), ItemSource.CHARGE, r.id().value(), null, null,
                null, "charge " + r.id(), r.amount(), clock.now())).toList();
        return Invoice.draft(new InvoiceId(UUID.randomUUID()), world.tenant().id(), world.customerId(), world.wallet().id(), MoneyTestWorld.USD,
                InvoiceOrigin.CHARGE, items, null, null, clock.now());
    }

    private Invoice issue(World world, Invoice draft, boolean settledByWallet) {
        return scopes.inTenant(world.tenant().id(), () -> unitOfWork.transactional(() -> issuer.issue(draft, settledByWallet)));
    }

    private Invoice reload(World world, Invoice invoice) {
        return scopes.inTenant(world.tenant().id(), () -> unitOfWork.transactional(() -> invoices.findById(invoice.id()))).orElseThrow();
    }

    @Test
    void anIssuedInvoiceRoundTripsWithItsLines() {
        World world = worlds.postpaid(100_000);
        Invoice issued = issue(world, draftFor(world, charge(world, 300), charge(world, 700)), false);

        Invoice reloaded = reload(world, issued);

        assertThat(reloaded).usingRecursiveComparison().isEqualTo(issued);
        assertThat(reloaded.status()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(reloaded.total()).isEqualTo(Money.of(1_000, MoneyTestWorld.USD));
        assertThat(reloaded.items()).hasSize(2);
    }

    @Test
    void statusChangesPersist() {
        World world = worlds.postpaid(100_000);
        Invoice invoice = issue(world, draftFor(world, charge(world, 500)), false);

        invoice.markUncollectible();
        scopes.runInTenant(world.tenant().id(), () -> unitOfWork.transactional(() -> invoices.update(invoice)));
        assertThat(reload(world, invoice).status()).isEqualTo(InvoiceStatus.UNCOLLECTIBLE);

        invoice.voidInvoice("written off by mistake", clock.now());
        scopes.runInTenant(world.tenant().id(), () -> unitOfWork.transactional(() -> invoices.update(invoice)));
        Invoice voided = reload(world, invoice);
        assertThat(voided.status()).isEqualTo(InvoiceStatus.VOID);
        assertThat(voided.voidReason()).isEqualTo("written off by mistake");
        assertThat(voided.voidedAt()).isNotNull();
    }

    @Test
    void numbersAreGapFreePerTenantAndARolledBackInvoiceGivesItsNumberBack() {
        World a = worlds.postpaid(100_000);
        World b = worlds.postpaid(100_000);
        Invoice first = issue(a, draftFor(a, charge(a, 100)), false);
        Invoice other = issue(b, draftFor(b, charge(b, 100)), false);

        ChargeResult doomed = charge(a, 100);
        assertThatThrownBy(() -> scopes.runInTenant(a.tenant().id(), () -> unitOfWork.transactional(() -> {
            issuer.issue(draftFor(a, doomed), false);
            throw new IllegalStateException("rolled back");
        }))).isInstanceOf(IllegalStateException.class);
        Invoice second = issue(a, draftFor(a, doomed), false);

        assertThat(first.number()).isEqualTo("INV-000001");
        assertThat(other.number()).isEqualTo("INV-000001");
        assertThat(second.number()).isEqualTo("INV-000002");
    }

    @Test
    void aWalletSettledInvoiceIsStoredAsPaid() {
        World world = worlds.postpaid(100_000);
        Invoice issued = issue(world, draftFor(world, charge(world, 250)), true);
        Invoice reloaded = reload(world, issued);
        assertThat(reloaded.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(reloaded.settlement()).isEqualTo(dev.ngb.billing.invoicing.domain.invoice.Settlement.WALLET);
        assertThat(reloaded.paidAt()).isNotNull();
    }

    @Test
    void aChargeCanBeBilledOnOneLineOnly() {
        World world = worlds.postpaid(100_000);
        ChargeResult charged = charge(world, 400);
        issue(world, draftFor(world, charged), false);

        assertThatThrownBy(() -> issue(world, draftFor(world, charged), false)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void anotherTenantCannotSeeTheInvoice() {
        World world = worlds.postpaid(100_000);
        World stranger = worlds.postpaid(100_000);
        Invoice issued = issue(world, draftFor(world, charge(world, 100)), false);

        assertThat(scopes.inTenant(stranger.tenant().id(), () -> unitOfWork.transactional(() -> invoices.findById(issued.id())))).isEmpty();
    }
}
