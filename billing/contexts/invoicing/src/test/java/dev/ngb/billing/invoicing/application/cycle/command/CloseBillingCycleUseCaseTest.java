package dev.ngb.billing.invoicing.application.cycle.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.event.DomainEvent;
import dev.ngb.core.id.TenantId;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.invoicing.application.cycle.port.UnbilledLineRepository;
import dev.ngb.billing.invoicing.application.cycle.service.CycleSettings;
import dev.ngb.billing.invoicing.application.invoice.port.InvoiceRepository;
import dev.ngb.billing.invoicing.application.invoice.service.InvoiceIssuer;
import dev.ngb.billing.invoicing.domain.cycle.UnbilledLine;
import dev.ngb.billing.invoicing.domain.invoice.Invoice;
import dev.ngb.billing.invoicing.domain.invoice.InvoiceId;
import dev.ngb.billing.invoicing.domain.invoice.InvoiceItem;
import dev.ngb.billing.invoicing.domain.invoice.ItemSource;
import dev.ngb.billing.pricing.api.plan.BillingCycle;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.sharedkernel.id.WalletId;
import dev.ngb.billing.sharedkernel.money.Currency;
import dev.ngb.billing.sharedkernel.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** The cycle logic against in-memory ports: one period that throws must not hold back the periods behind it. */
class CloseBillingCycleUseCaseTest {

    private static final Currency USD = Currency.of("USD");
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-12-05T10:00:00Z");
    private static final Instant START = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-12-01T00:00:00Z");

    private final WalletId broken = new WalletId(UUID.randomUUID());
    private final WalletId healthy = new WalletId(UUID.randomUUID());
    private final List<Invoice> issued = new ArrayList<>();
    private final List<DomainEvent> published = new ArrayList<>();

    private final UnbilledLineRepository lines = new UnbilledLineRepository() {
        @Override
        public boolean add(UnbilledLine line) {
            return true;
        }

        @Override
        public Optional<UnbilledLine> lockByCharge(UUID chargeId) {
            return Optional.empty();
        }

        @Override
        public boolean periodInvoiced(WalletId walletId, Instant cycleStart) {
            return false;
        }

        @Override
        public List<ClosedPeriod> closedPeriods(Instant now, int limit) {
            return List.of(new ClosedPeriod(broken, START, END), new ClosedPeriod(healthy, START, END));
        }

        @Override
        public List<UnbilledLine> lockPeriod(WalletId walletId, Instant cycleStart) {
            if (walletId.equals(broken)) {
                throw new IllegalStateException("this period cannot be read");
            }
            InvoiceItem item = new InvoiceItem(UUID.randomUUID(), ItemSource.CHARGE, UUID.randomUUID(), null, null, null, "usage", Money.of(500, USD), START.plusSeconds(60));
            return List.of(new UnbilledLine(TENANT, new CustomerId(UUID.randomUUID()), walletId, item, START, END));
        }
    };

    private final InvoiceRepository invoices = new InvoiceRepository() {
        @Override
        public void add(Invoice invoice) {
            issued.add(invoice);
        }

        @Override
        public void update(Invoice invoice) {}

        @Override
        public Optional<Invoice> findById(InvoiceId id) {
            return Optional.empty();
        }

        @Override
        public Optional<Invoice> findByCharge(UUID chargeId) {
            return Optional.empty();
        }

        @Override
        public Optional<Invoice> lockById(InvoiceId id) {
            return Optional.empty();
        }

        @Override
        public long nextNumber(TenantId tenantId) {
            return issued.size() + 1L;
        }
    };

    private final UnitOfWork passThrough = new UnitOfWork() {
        @Override
        public <T> T transactional(Supplier<T> work) {
            return work.get();
        }
    };

    @Test
    void aPeriodThatThrowsIsCountedAndTheOthersAreStillInvoiced() {
        InvoiceIssuer issuer = new InvoiceIssuer(invoices, published::add, UUID::randomUUID, () -> NOW);
        CloseBillingCycleUseCase useCase = new CloseBillingCycleUseCase(passThrough, lines, issuer, new CycleSettings(BillingCycle.MONTHLY, 10), UUID::randomUUID, () -> NOW);

        CloseBillingCycleUseCase.Report report = useCase.runOnce();

        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.invoiced()).isEqualTo(1);
        assertThat(issued).singleElement().satisfies(invoice -> {
            assertThat(invoice.walletId()).isEqualTo(healthy);
            assertThat(invoice.number()).isEqualTo("INV-000001");
            assertThat(invoice.periodStart()).isEqualTo(START);
            assertThat(invoice.periodEnd()).isEqualTo(END);
        });
        assertThat(published).extracting(DomainEvent::type).containsExactly("invoice.created");
    }
}
