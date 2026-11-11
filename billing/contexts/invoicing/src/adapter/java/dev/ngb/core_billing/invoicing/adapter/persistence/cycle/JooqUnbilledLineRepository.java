package dev.ngb.core_billing.invoicing.adapter.persistence.cycle;

import static dev.ngb.core_billing_jooq.Tables.INVOICES;
import static dev.ngb.core_billing_jooq.Tables.INVOICE_ITEMS;

import dev.ngb.core_billing.invoicing.application.cycle.port.UnbilledLineRepository;
import dev.ngb.core_billing.invoicing.domain.cycle.UnbilledLine;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceItem;
import dev.ngb.core_billing.invoicing.domain.invoice.ItemSource;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import dev.ngb.core_billing_jooq.tables.records.InvoiceItemsRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/** Unbilled lines are the invoice-line rows that have no invoice yet; the unique charge makes collection idempotent. */
public class JooqUnbilledLineRepository implements UnbilledLineRepository {

    private final DSLContext dsl;

    public JooqUnbilledLineRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean add(UnbilledLine line) {
        return dsl.insertInto(INVOICE_ITEMS)
                .set(INVOICE_ITEMS.ID, line.item().id())
                .set(INVOICE_ITEMS.TENANT_ID, line.tenantId().value())
                .set(INVOICE_ITEMS.INVOICE_ID, (UUID) null)
                .set(INVOICE_ITEMS.CUSTOMER_ID, line.customerId().value())
                .set(INVOICE_ITEMS.WALLET_ID, line.walletId().value())
                .set(INVOICE_ITEMS.CURRENCY, line.currency().code())
                .set(INVOICE_ITEMS.SOURCE, line.item().source().name())
                .set(INVOICE_ITEMS.CHARGE_ID, line.item().chargeId())
                .set(INVOICE_ITEMS.SUBSCRIPTION_ID, line.item().subscriptionId())
                .set(INVOICE_ITEMS.DESCRIPTION, line.item().description())
                .set(INVOICE_ITEMS.AMOUNT, line.item().amount().minorUnits())
                .set(INVOICE_ITEMS.PERIOD_START, line.item().periodStart())
                .set(INVOICE_ITEMS.PERIOD_END, line.item().periodEnd())
                .set(INVOICE_ITEMS.CYCLE_START, line.cycleStart())
                .set(INVOICE_ITEMS.CYCLE_END, line.cycleEnd())
                .set(INVOICE_ITEMS.INCURRED_AT, line.item().incurredAt())
                .onConflict(INVOICE_ITEMS.TENANT_ID, INVOICE_ITEMS.CHARGE_ID)
                .doNothing()
                .execute() == 1;
    }

    @Override
    public Optional<UnbilledLine> lockByCharge(UUID chargeId) {
        return dsl.selectFrom(INVOICE_ITEMS)
                .where(INVOICE_ITEMS.CHARGE_ID.eq(chargeId))
                .and(INVOICE_ITEMS.INVOICE_ID.isNull())
                .forUpdate()
                .fetchOptional(JooqUnbilledLineRepository::toDomain);
    }

    @Override
    public boolean periodInvoiced(WalletId walletId, Instant cycleStart) {
        return dsl.fetchExists(INVOICES,
                INVOICES.WALLET_ID.eq(walletId.value())
                        .and(INVOICES.ORIGIN.eq("POSTPAID_CYCLE"))
                        .and(INVOICES.PERIOD_START.eq(cycleStart)));
    }

    @Override
    public List<ClosedPeriod> closedPeriods(Instant now, int limit) {
        return dsl.select(INVOICE_ITEMS.WALLET_ID, INVOICE_ITEMS.CYCLE_START, INVOICE_ITEMS.CYCLE_END)
                .from(INVOICE_ITEMS)
                .where(INVOICE_ITEMS.INVOICE_ID.isNull())
                .and(INVOICE_ITEMS.CYCLE_END.le(now))
                .groupBy(INVOICE_ITEMS.WALLET_ID, INVOICE_ITEMS.CYCLE_START, INVOICE_ITEMS.CYCLE_END)
                .orderBy(INVOICE_ITEMS.CYCLE_END, INVOICE_ITEMS.WALLET_ID)
                .limit(limit)
                .fetch(row -> new ClosedPeriod(new WalletId(row.value1()), row.value2(), row.value3()));
    }

    @Override
    public List<UnbilledLine> lockPeriod(WalletId walletId, Instant cycleStart) {
        return dsl.selectFrom(INVOICE_ITEMS)
                .where(INVOICE_ITEMS.WALLET_ID.eq(walletId.value()))
                .and(INVOICE_ITEMS.CYCLE_START.eq(cycleStart))
                .and(INVOICE_ITEMS.INVOICE_ID.isNull())
                .orderBy(INVOICE_ITEMS.INCURRED_AT, INVOICE_ITEMS.ID)
                .forUpdate()
                .fetch(JooqUnbilledLineRepository::toDomain);
    }

    private static UnbilledLine toDomain(InvoiceItemsRecord row) {
        InvoiceItem item = new InvoiceItem(
                row.getId(),
                ItemSource.valueOf(row.getSource()),
                row.getChargeId(),
                row.getSubscriptionId(),
                row.getPeriodStart(),
                row.getPeriodEnd(),
                row.getDescription(),
                Money.of(row.getAmount(), Currency.of(row.getCurrency().strip())),
                row.getIncurredAt());
        return new UnbilledLine(
                new TenantId(row.getTenantId()), new CustomerId(row.getCustomerId()), new WalletId(row.getWalletId()), item,
                row.getCycleStart(), row.getCycleEnd());
    }
}
