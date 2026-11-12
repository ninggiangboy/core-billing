package dev.ngb.billing.invoicing.adapter.invoice.persistence;

import static dev.ngb.billing_jooq.Tables.INVOICES;
import static dev.ngb.billing_jooq.Tables.INVOICE_COUNTERS;
import static dev.ngb.billing_jooq.Tables.INVOICE_ITEMS;
import static dev.ngb.billing_jooq.Tables.INVOICE_PAYMENTS;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.util.Causes;
import dev.ngb.billing.invoicing.application.invoice.port.InvoiceRepository;
import dev.ngb.billing.invoicing.domain.invoice.Invoice;
import dev.ngb.billing.invoicing.domain.invoice.InvoiceId;
import dev.ngb.billing.invoicing.domain.invoice.InvoiceItem;
import dev.ngb.billing.invoicing.domain.invoice.InvoiceOrigin;
import dev.ngb.billing.invoicing.domain.invoice.InvoicePayment;
import dev.ngb.billing.invoicing.domain.invoice.InvoiceStatus;
import dev.ngb.billing.invoicing.domain.invoice.ItemSource;
import dev.ngb.billing.invoicing.domain.invoice.Settlement;
import dev.ngb.billing.platform.api.error.ConflictException;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.sharedkernel.id.WalletId;
import dev.ngb.billing.sharedkernel.money.Currency;
import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing_jooq.tables.records.InvoicesRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;

/**
 * An invoice is written as its own row, its lines and its payment history. Lines and payments are upserted by
 * explicit SQL: a line can exist unbilled before its invoice does, and a payment's status moves forward on the
 * same row. The number comes from a counter row that is locked until the issuing transaction ends.
 */
public class JooqInvoiceRepository implements InvoiceRepository {

    private final DSLContext dsl;

    public JooqInvoiceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(Invoice invoice) {
        try {
            dsl.insertInto(INVOICES)
                    .set(INVOICES.ID, invoice.id().value())
                    .set(INVOICES.TENANT_ID, invoice.tenantId().value())
                    .set(INVOICES.CUSTOMER_ID, invoice.customerId().value())
                    .set(INVOICES.WALLET_ID, invoice.walletId().value())
                    .set(INVOICES.CURRENCY, invoice.currency().code())
                    .set(INVOICES.NUMBER, invoice.number())
                    .set(INVOICES.ORIGIN, invoice.origin().name())
                    .set(INVOICES.STATUS, invoice.status().name())
                    .set(INVOICES.TOTAL, invoice.total().minorUnits())
                    .set(INVOICES.PERIOD_START, invoice.periodStart())
                    .set(INVOICES.PERIOD_END, invoice.periodEnd())
                    .set(INVOICES.SETTLEMENT, invoice.settlement() == null ? null : invoice.settlement().name())
                    .set(INVOICES.VOID_REASON, invoice.voidReason())
                    .set(INVOICES.CREATED_AT, invoice.createdAt())
                    .set(INVOICES.ISSUED_AT, invoice.issuedAt())
                    .set(INVOICES.PAID_AT, invoice.paidAt())
                    .set(INVOICES.VOIDED_AT, invoice.voidedAt())
                    .execute();
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("an invoice for this wallet and period already exists");
            }
            throw e;
        }
        for (InvoiceItem item : invoice.items()) {
            attach(invoice, item);
        }
        invoice.payments().forEach(payment -> upsertPayment(invoice, payment));
    }

    /** A line is attached once: an unbilled line becomes this invoice's, and a charge already billed on another line is reported as a conflict. */
    private void attach(Invoice invoice, InvoiceItem item) {
        int attached;
        try {
            attached = dsl.insertInto(INVOICE_ITEMS)
                    .set(INVOICE_ITEMS.ID, item.id())
                    .set(INVOICE_ITEMS.TENANT_ID, invoice.tenantId().value())
                    .set(INVOICE_ITEMS.INVOICE_ID, invoice.id().value())
                    .set(INVOICE_ITEMS.CUSTOMER_ID, invoice.customerId().value())
                    .set(INVOICE_ITEMS.WALLET_ID, invoice.walletId().value())
                    .set(INVOICE_ITEMS.CURRENCY, invoice.currency().code())
                    .set(INVOICE_ITEMS.SOURCE, item.source().name())
                    .set(INVOICE_ITEMS.CHARGE_ID, item.chargeId())
                    .set(INVOICE_ITEMS.SUBSCRIPTION_ID, item.subscriptionId())
                    .set(INVOICE_ITEMS.DESCRIPTION, item.description())
                    .set(INVOICE_ITEMS.AMOUNT, item.amount().minorUnits())
                    .set(INVOICE_ITEMS.PERIOD_START, item.periodStart())
                    .set(INVOICE_ITEMS.PERIOD_END, item.periodEnd())
                    .set(INVOICE_ITEMS.INCURRED_AT, item.incurredAt())
                    .onConflict(INVOICE_ITEMS.ID)
                    .doUpdate()
                    .set(INVOICE_ITEMS.INVOICE_ID, invoice.id().value())
                    .where(INVOICE_ITEMS.INVOICE_ID.isNull())
                    .execute();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("a charge on this invoice was already billed on another invoice");
        }
        if (attached != 1) {
            throw new ConflictException("an invoice line was already billed on another invoice");
        }
    }

    @Override
    public void update(Invoice invoice) {
        dsl.update(INVOICES)
                .set(INVOICES.CUSTOMER_ID, invoice.customerId().value())
                .set(INVOICES.WALLET_ID, invoice.walletId().value())
                .set(INVOICES.CURRENCY, invoice.currency().code())
                .set(INVOICES.NUMBER, invoice.number())
                .set(INVOICES.ORIGIN, invoice.origin().name())
                .set(INVOICES.STATUS, invoice.status().name())
                .set(INVOICES.TOTAL, invoice.total().minorUnits())
                .set(INVOICES.PERIOD_START, invoice.periodStart())
                .set(INVOICES.PERIOD_END, invoice.periodEnd())
                .set(INVOICES.SETTLEMENT, invoice.settlement() == null ? null : invoice.settlement().name())
                .set(INVOICES.VOID_REASON, invoice.voidReason())
                .set(INVOICES.ISSUED_AT, invoice.issuedAt())
                .set(INVOICES.PAID_AT, invoice.paidAt())
                .set(INVOICES.VOIDED_AT, invoice.voidedAt())
                .where(INVOICES.ID.eq(invoice.id().value()))
                .execute();
        invoice.payments().forEach(payment -> upsertPayment(invoice, payment));
    }

    @Override
    public Optional<Invoice> findById(InvoiceId id) {
        return dsl.selectFrom(INVOICES)
                .where(INVOICES.ID.eq(id.value()))
                .fetchOptional(this::load);
    }

    @Override
    public Optional<Invoice> findByCharge(UUID chargeId) {
        return dsl.select(INVOICES.fields())
                .from(INVOICES)
                .join(INVOICE_ITEMS)
                .on(INVOICE_ITEMS.INVOICE_ID.eq(INVOICES.ID).and(INVOICE_ITEMS.TENANT_ID.eq(INVOICES.TENANT_ID)))
                .where(INVOICE_ITEMS.CHARGE_ID.eq(chargeId))
                .fetchOptional(row -> load(row.into(INVOICES)));
    }

    @Override
    public Optional<Invoice> lockById(InvoiceId id) {
        return dsl.selectFrom(INVOICES)
                .where(INVOICES.ID.eq(id.value()))
                .forUpdate()
                .fetchOptional(this::load);
    }

    @Override
    public long nextNumber(TenantId tenantId) {
        return dsl.insertInto(INVOICE_COUNTERS)
                .set(INVOICE_COUNTERS.TENANT_ID, tenantId.value())
                .set(INVOICE_COUNTERS.LAST_NUMBER, 1L)
                .onConflict(INVOICE_COUNTERS.TENANT_ID)
                .doUpdate()
                .set(INVOICE_COUNTERS.LAST_NUMBER, INVOICE_COUNTERS.LAST_NUMBER.plus(1))
                .returningResult(INVOICE_COUNTERS.LAST_NUMBER)
                .fetchOne()
                .value1();
    }

    private Invoice load(InvoicesRecord row) {
        List<InvoiceItem> items = dsl.selectFrom(INVOICE_ITEMS)
                .where(INVOICE_ITEMS.INVOICE_ID.eq(row.getId()))
                .orderBy(INVOICE_ITEMS.INCURRED_AT, INVOICE_ITEMS.ID)
                .fetch(item -> toItem(item));
        List<InvoicePayment> payments = dsl.selectFrom(INVOICE_PAYMENTS)
                .where(INVOICE_PAYMENTS.INVOICE_ID.eq(row.getId()))
                .orderBy(INVOICE_PAYMENTS.STARTED_AT, INVOICE_PAYMENTS.PAYMENT_ID)
                .fetch(payment -> new InvoicePayment(
                        payment.getPaymentId(),
                        Money.of(payment.getAmount(), Currency.of(payment.getCurrency().strip())),
                        InvoicePayment.Status.valueOf(payment.getStatus()),
                        payment.getFailureReason(),
                        payment.getLedgerEntryId(),
                        payment.getStartedAt(),
                        payment.getSettledAt()));
        return toDomain(row, items, payments);
    }

    private static InvoiceItem toItem(dev.ngb.billing_jooq.tables.records.InvoiceItemsRecord row) {
        return new InvoiceItem(
                row.getId(),
                ItemSource.valueOf(row.getSource()),
                row.getChargeId(),
                row.getSubscriptionId(),
                row.getPeriodStart(),
                row.getPeriodEnd(),
                row.getDescription(),
                Money.of(row.getAmount(), Currency.of(row.getCurrency().strip())),
                row.getIncurredAt());
    }

    private static Invoice toDomain(InvoicesRecord row, List<InvoiceItem> items, List<InvoicePayment> payments) {
        return Invoice.restore(
                new InvoiceId(row.getId()),
                new TenantId(row.getTenantId()),
                new CustomerId(row.getCustomerId()),
                new WalletId(row.getWalletId()),
                Currency.of(row.getCurrency().strip()),
                InvoiceOrigin.valueOf(row.getOrigin()),
                items,
                row.getPeriodStart(),
                row.getPeriodEnd(),
                row.getNumber(),
                InvoiceStatus.valueOf(row.getStatus()),
                row.getSettlement() == null ? null : Settlement.valueOf(row.getSettlement()),
                row.getVoidReason(),
                row.getCreatedAt(),
                row.getIssuedAt(),
                row.getPaidAt(),
                row.getVoidedAt(),
                payments);
    }

    private void upsertPayment(Invoice invoice, InvoicePayment payment) {
        dsl.insertInto(INVOICE_PAYMENTS)
                .set(INVOICE_PAYMENTS.PAYMENT_ID, payment.paymentId())
                .set(INVOICE_PAYMENTS.TENANT_ID, invoice.tenantId().value())
                .set(INVOICE_PAYMENTS.INVOICE_ID, invoice.id().value())
                .set(INVOICE_PAYMENTS.CURRENCY, payment.amount().currency().code())
                .set(INVOICE_PAYMENTS.AMOUNT, payment.amount().minorUnits())
                .set(INVOICE_PAYMENTS.STATUS, payment.status().name())
                .set(INVOICE_PAYMENTS.FAILURE_REASON, payment.failureReason())
                .set(INVOICE_PAYMENTS.LEDGER_ENTRY_ID, payment.ledgerEntryId())
                .set(INVOICE_PAYMENTS.STARTED_AT, payment.startedAt())
                .set(INVOICE_PAYMENTS.SETTLED_AT, payment.settledAt())
                .onConflict(INVOICE_PAYMENTS.PAYMENT_ID)
                .doUpdate()
                .set(INVOICE_PAYMENTS.STATUS, payment.status().name())
                .set(INVOICE_PAYMENTS.FAILURE_REASON, payment.failureReason())
                .set(INVOICE_PAYMENTS.LEDGER_ENTRY_ID, payment.ledgerEntryId())
                .set(INVOICE_PAYMENTS.SETTLED_AT, payment.settledAt())
                .execute();
    }
}
