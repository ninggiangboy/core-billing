package dev.ngb.core_billing.invoicing.adapter.persistence.invoice;

import dev.ngb.core_billing.invoicing.application.invoice.port.InvoiceRepository;
import dev.ngb.core_billing.invoicing.domain.invoice.Invoice;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceId;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceItem;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoicePayment;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.sharedkernel.Causes;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * An invoice is written as its own row, its lines and its payment history. Lines and payments are upserted by
 * explicit SQL: a line can exist unbilled before its invoice does, and a payment's status moves forward on the
 * same row. The number comes from a counter row that is locked until the issuing transaction ends.
 */
public class JdbcInvoiceRepository implements InvoiceRepository {

    private static final String INVOICE_COLUMNS = "id, tenant_id, customer_id, wallet_id, currency, number, origin, status, total, period_start, "
            + "period_end, settlement, void_reason, created_at, issued_at, paid_at, voided_at";
    private static final String ITEM_COLUMNS = "id, tenant_id, invoice_id, customer_id, wallet_id, currency, source, charge_id, subscription_id, "
            + "description, amount, period_start, period_end, incurred_at";
    private static final String PAYMENT_COLUMNS = "payment_id, tenant_id, invoice_id, currency, amount, status, failure_reason, ledger_entry_id, "
            + "started_at, settled_at";

    private final JdbcAggregateOperations aggregates;
    private final JdbcClient jdbc;

    public JdbcInvoiceRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
    }

    @Override
    public void add(Invoice invoice) {
        try {
            aggregates.insert(InvoiceRow.from(invoice));
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
            attached = jdbc.sql("""
                            insert into invoice_items (id, tenant_id, invoice_id, customer_id, wallet_id, currency, source, charge_id, subscription_id,
                                                       description, amount, period_start, period_end, incurred_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            on conflict (id) do update set invoice_id = excluded.invoice_id where invoice_items.invoice_id is null
                            """)
                    .params(item.id(), invoice.tenantId().value(), invoice.id().value(), invoice.customerId().value(), invoice.walletId().value(),
                            invoice.currency().code(), item.source().name(), item.chargeId(), item.subscriptionId(), item.description(),
                            item.amount().minorUnits(), timestamp(item.periodStart()), timestamp(item.periodEnd()), timestamp(item.incurredAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("a charge on this invoice was already billed on another invoice");
        }
        if (attached != 1) {
            throw new ConflictException("an invoice line was already billed on another invoice");
        }
    }

    @Override
    public void update(Invoice invoice) {
        aggregates.update(InvoiceRow.from(invoice));
        invoice.payments().forEach(payment -> upsertPayment(invoice, payment));
    }

    @Override
    public Optional<Invoice> findById(InvoiceId id) {
        return Optional.ofNullable(aggregates.findById(id.value(), InvoiceRow.class)).map(this::load);
    }

    @Override
    public Optional<Invoice> findByCharge(UUID chargeId) {
        return jdbc.sql("select i.* from invoices i join invoice_items l on l.invoice_id = i.id and l.tenant_id = i.tenant_id where l.charge_id = ?")
                .param(chargeId).query(InvoiceRow.class).optional().map(this::load);
    }

    @Override
    public Optional<Invoice> lockById(InvoiceId id) {
        return jdbc.sql("select " + INVOICE_COLUMNS + " from invoices where id = ? for update").param(id.value()).query(InvoiceRow.class).optional()
                .map(this::load);
    }

    @Override
    public long nextNumber(TenantId tenantId) {
        return jdbc.sql("""
                        insert into invoice_counters (tenant_id, last_number) values (?, 1)
                        on conflict (tenant_id) do update set last_number = invoice_counters.last_number + 1
                        returning last_number
                        """)
                .param(tenantId.value())
                .query(Long.class)
                .single();
    }

    private Invoice load(InvoiceRow row) {
        List<InvoiceItem> items = jdbc.sql("select " + ITEM_COLUMNS + " from invoice_items where invoice_id = ? order by incurred_at, id")
                .param(row.id()).query(InvoiceItemRow.class).list().stream().map(InvoiceItemRow::toDomain).toList();
        List<InvoicePayment> payments = jdbc.sql("select " + PAYMENT_COLUMNS + " from invoice_payments where invoice_id = ? order by started_at, payment_id")
                .param(row.id()).query(InvoicePaymentRow.class).list().stream().map(InvoicePaymentRow::toDomain).toList();
        return row.toDomain(items, payments);
    }

    private void upsertPayment(Invoice invoice, InvoicePayment payment) {
        InvoicePaymentRow row = InvoicePaymentRow.from(invoice.tenantId().value(), invoice.id().value(), payment);
        jdbc.sql("""
                        insert into invoice_payments (payment_id, tenant_id, invoice_id, currency, amount, status, failure_reason, ledger_entry_id,
                                                      started_at, settled_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (payment_id) do update set status = excluded.status, failure_reason = excluded.failure_reason,
                                ledger_entry_id = excluded.ledger_entry_id, settled_at = excluded.settled_at
                        """)
                .params(row.paymentId(), row.tenantId(), row.invoiceId(), row.currency(), row.amount(), row.status(), row.failureReason(),
                        row.ledgerEntryId(), timestamp(row.startedAt()), timestamp(row.settledAt()))
                .update();
    }

    private static Timestamp timestamp(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
