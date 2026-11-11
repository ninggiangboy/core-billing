package dev.ngb.core_billing.invoicing.adapter.persistence.invoice;

import dev.ngb.core_billing.invoicing.application.invoice.query.InvoiceItemView;
import dev.ngb.core_billing.invoicing.application.invoice.query.InvoicePaymentView;
import dev.ngb.core_billing.invoicing.application.invoice.query.InvoiceReadRepository;
import dev.ngb.core_billing.invoicing.application.invoice.query.InvoiceView;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceOrigin;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoicePayment;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceStatus;
import dev.ngb.core_billing.invoicing.domain.invoice.ItemSource;
import dev.ngb.core_billing.invoicing.domain.invoice.Settlement;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects invoices, lines and payment history straight into views; no aggregate is built. */
public class JdbcInvoiceReadRepository implements InvoiceReadRepository {

    private static final String COLUMNS = "id, number, customer_id, wallet_id, currency, status, origin, total, period_start, period_end, settlement, "
            + "void_reason, created_at, issued_at, paid_at, voided_at";

    private record InvoiceRowView(
            UUID id,
            String number,
            UUID customerId,
            UUID walletId,
            String currency,
            String status,
            String origin,
            long total,
            Instant periodStart,
            Instant periodEnd,
            String settlement,
            String voidReason,
            Instant createdAt,
            Instant issuedAt,
            Instant paidAt,
            Instant voidedAt) {

        InvoiceView toView() {
            return new InvoiceView(id, number, new CustomerId(customerId), new WalletId(walletId), InvoiceStatus.valueOf(status), InvoiceOrigin.valueOf(origin),
                    Money.of(total, Currency.of(currency.strip())), periodStart, periodEnd, settlement == null ? null : Settlement.valueOf(settlement),
                    voidReason, createdAt, issuedAt, paidAt, voidedAt);
        }
    }

    private record ItemRowView(
            UUID id,
            String source,
            UUID chargeId,
            UUID subscriptionId,
            Instant periodStart,
            Instant periodEnd,
            String description,
            String currency,
            long amount,
            Instant incurredAt) {

        InvoiceItemView toView() {
            return new InvoiceItemView(id, ItemSource.valueOf(source), chargeId, subscriptionId, periodStart, periodEnd, description,
                    Money.of(amount, Currency.of(currency.strip())), incurredAt);
        }
    }

    private record PaymentRowView(
            UUID paymentId, String currency, long amount, String status, String failureReason, UUID ledgerEntryId, Instant startedAt, Instant settledAt) {

        InvoicePaymentView toView() {
            return new InvoicePaymentView(paymentId, Money.of(amount, Currency.of(currency.strip())), InvoicePayment.Status.valueOf(status), failureReason,
                    ledgerEntryId, startedAt, settledAt);
        }
    }

    private final JdbcClient jdbc;

    public JdbcInvoiceReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<InvoiceView> list(UUID customerId, UUID walletId, InvoiceStatus status, UUID before, int limit, List<UUID> customerIds) {
        if (customerIds != null && customerIds.isEmpty()) {
            return List.of();
        }
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                        select %s
                        from invoices
                        where true
                        """.formatted(COLUMNS));
        if (customerId != null) {
            sql.append(" and customer_id = ?");
            params.add(customerId);
        }
        if (walletId != null) {
            sql.append(" and wallet_id = ?");
            params.add(walletId);
        }
        if (customerIds != null) {
            sql.append(" and customer_id = any(?)");
            params.add(customerIds.toArray(UUID[]::new));
        }
        if (status != null) {
            sql.append(" and status = ?");
            params.add(status.name());
        }
        if (before != null) {
            sql.append(" and id < ?");
            params.add(before);
        }
        sql.append(" order by id desc limit ?");
        params.add(limit);
        return jdbc.sql(sql.toString()).params(params).query(InvoiceRowView.class).list().stream().map(InvoiceRowView::toView).toList();
    }

    @Override
    public Optional<InvoiceView> findById(UUID id) {
        return jdbc.sql("""
                        select %s
                        from invoices
                        where id = ?
                        """.formatted(COLUMNS)).param(id).query(InvoiceRowView.class).optional().map(InvoiceRowView::toView);
    }

    @Override
    public List<InvoiceItemView> items(UUID invoiceId) {
        return jdbc.sql("""
                        select id, source, charge_id, subscription_id, period_start, period_end, description, currency, amount, incurred_at
                        from invoice_items
                        where invoice_id = ?
                        order by incurred_at, id
                        """)
                .param(invoiceId).query(ItemRowView.class).list().stream().map(ItemRowView::toView).toList();
    }

    @Override
    public List<InvoicePaymentView> payments(UUID invoiceId) {
        return jdbc.sql("""
                        select payment_id, currency, amount, status, failure_reason, ledger_entry_id, started_at, settled_at
                        from invoice_payments
                        where invoice_id = ?
                        order by started_at, payment_id
                        """)
                .param(invoiceId).query(PaymentRowView.class).list().stream().map(PaymentRowView::toView).toList();
    }
}
