package dev.ngb.core_billing.payment.adapter.persistence.refund;

import dev.ngb.core_billing.payment.application.refund.query.RefundReadRepository;
import dev.ngb.core_billing.payment.application.refund.query.RefundView;
import dev.ngb.core_billing.payment.domain.refund.RefundStatus;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects refunds straight into views. */
public class JdbcRefundReadRepository implements RefundReadRepository {

    private static final String COLUMNS = "id, payment_id, currency, amount, status, reason, failure_reason, ledger_entry_id, created_at, completed_at";

    private record Row(
            UUID id,
            UUID paymentId,
            String currency,
            long amount,
            String status,
            String reason,
            String failureReason,
            UUID ledgerEntryId,
            Instant createdAt,
            Instant completedAt) {

        RefundView toView() {
            return new RefundView(
                    id,
                    paymentId,
                    RefundStatus.valueOf(status),
                    Money.of(amount, Currency.of(currency.strip())),
                    reason,
                    failureReason,
                    ledgerEntryId,
                    createdAt,
                    completedAt);
        }
    }

    private final JdbcClient jdbc;

    public JdbcRefundReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RefundView> byPayment(UUID paymentId) {
        return jdbc.sql("select " + COLUMNS + " from refunds where payment_id = ? order by id")
                .param(paymentId)
                .query(Row.class)
                .list()
                .stream()
                .map(Row::toView)
                .toList();
    }

    @Override
    public Optional<RefundView> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from refunds where id = ?").param(id).query(Row.class).optional().map(Row::toView);
    }
}
