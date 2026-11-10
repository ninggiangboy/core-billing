package io.corebilling.payment.adapter.persistence;

import io.corebilling.payment.application.refund.port.RefundRepository;
import io.corebilling.payment.domain.payment.PaymentId;
import io.corebilling.payment.domain.refund.Refund;
import io.corebilling.payment.domain.refund.RefundId;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcRefundRepository implements RefundRepository {

    private static final String COLUMNS = "id, tenant_id, payment_id, currency, amount, status, reason, stripe_refund_id, reservation_id, ledger_entry_id, "
            + "failure_reason, actor, idempotency_key, request_hash, created_at, updated_at, completed_at";

    private final JdbcAggregateOperations aggregates;
    private final JdbcClient jdbc;

    public JdbcRefundRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
    }

    @Override
    public void add(Refund r) {
        aggregates.insert(RefundRow.from(r));
    }

    @Override
    public void update(Refund r) {
        aggregates.update(RefundRow.from(r));
    }

    @Override
    public Optional<Refund> findById(RefundId id) {
        return Optional.ofNullable(aggregates.findById(id.value(), RefundRow.class)).map(RefundRow::toDomain);
    }

    @Override
    public Optional<Refund> lockById(RefundId id) {
        return jdbc.sql("select " + COLUMNS + " from refunds where id = ? for update").param(id.value()).query(RefundRow.class).optional()
                .map(RefundRow::toDomain);
    }

    @Override
    public Optional<Refund> findByStripeRefundId(String stripeRefundId) {
        return jdbc.sql("select " + COLUMNS + " from refunds where stripe_refund_id = ?").param(stripeRefundId).query(RefundRow.class).optional()
                .map(RefundRow::toDomain);
    }

    @Override
    public Optional<Refund> findByIdempotencyKey(String key) {
        return jdbc.sql("select " + COLUMNS + " from refunds where idempotency_key = ?").param(key).query(RefundRow.class).optional()
                .map(RefundRow::toDomain);
    }

    @Override
    public long liveAmount(PaymentId paymentId) {
        return jdbc.sql("select coalesce(sum(amount), 0) from refunds where payment_id = ? and status in ('PENDING', 'SUCCEEDED')")
                .param(paymentId.value()).query(Long.class).single();
    }
}
