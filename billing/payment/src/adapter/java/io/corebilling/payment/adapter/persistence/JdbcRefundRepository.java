package io.corebilling.payment.adapter.persistence;

import io.corebilling.payment.application.refund.port.RefundRepository;
import io.corebilling.payment.domain.payment.PaymentId;
import io.corebilling.payment.domain.refund.Refund;
import io.corebilling.payment.domain.refund.RefundId;
import io.corebilling.payment.domain.refund.RefundStatus;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcRefundRepository implements RefundRepository {

    private static final String COLUMNS = "id, tenant_id, payment_id, currency, amount, status, reason, stripe_refund_id, reservation_id, ledger_entry_id, "
            + "failure_reason, actor, idempotency_key, request_hash, created_at, updated_at, completed_at";

    private static final RowMapper<Refund> MAPPER = (rs, n) -> Refund.restore(
            new RefundId(Rows.uuid(rs, "id")),
            new TenantId(Rows.uuid(rs, "tenant_id")),
            new PaymentId(Rows.uuid(rs, "payment_id")),
            Money.of(rs.getLong("amount"), Currency.of(rs.getString("currency").strip())),
            RefundStatus.valueOf(rs.getString("status")),
            rs.getString("reason"),
            rs.getString("stripe_refund_id"),
            Rows.uuid(rs, "reservation_id"),
            Rows.uuid(rs, "ledger_entry_id"),
            rs.getString("failure_reason"),
            rs.getString("actor"),
            rs.getString("idempotency_key"),
            rs.getString("request_hash"),
            Rows.instant(rs, "created_at"),
            Rows.instant(rs, "updated_at"),
            Rows.instant(rs, "completed_at"));

    private final JdbcClient jdbc;

    public JdbcRefundRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Refund r) {
        jdbc.sql("insert into refunds (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(r.id().value(), r.tenantId().value(), r.paymentId().value(), r.amount().currency().code(), r.amount().minorUnits(),
                        r.status().name(), r.reason(), r.stripeRefundId(), r.reservationId(), r.ledgerEntryId(), r.failureReason(), r.actor(),
                        r.idempotencyKey(), r.requestHash(), Timestamp.from(r.createdAt()), Timestamp.from(r.updatedAt()), Rows.timestamp(r.completedAt()))
                .update();
    }

    @Override
    public void update(Refund r) {
        int updated = jdbc.sql("""
                        update refunds
                        set status = ?, stripe_refund_id = ?, ledger_entry_id = ?, failure_reason = ?, updated_at = ?, completed_at = ?
                        where id = ?
                        """)
                .params(r.status().name(), r.stripeRefundId(), r.ledgerEntryId(), r.failureReason(), Timestamp.from(r.updatedAt()),
                        Rows.timestamp(r.completedAt()), r.id().value())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("refund " + r.id() + " was not updated");
        }
    }

    @Override
    public Optional<Refund> findById(RefundId id) {
        return jdbc.sql("select " + COLUMNS + " from refunds where id = ?").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public Optional<Refund> lockById(RefundId id) {
        return jdbc.sql("select " + COLUMNS + " from refunds where id = ? for update").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public Optional<Refund> findByStripeRefundId(String stripeRefundId) {
        return jdbc.sql("select " + COLUMNS + " from refunds where stripe_refund_id = ?").param(stripeRefundId).query(MAPPER).optional();
    }

    @Override
    public Optional<Refund> findByIdempotencyKey(String key) {
        return jdbc.sql("select " + COLUMNS + " from refunds where idempotency_key = ?").param(key).query(MAPPER).optional();
    }

    @Override
    public List<Refund> listByPayment(PaymentId paymentId) {
        return jdbc.sql("select " + COLUMNS + " from refunds where payment_id = ? order by id").param(paymentId.value()).query(MAPPER).list();
    }

    @Override
    public long liveAmount(PaymentId paymentId) {
        return jdbc.sql("select coalesce(sum(amount), 0) from refunds where payment_id = ? and status in ('PENDING', 'SUCCEEDED')")
                .param(paymentId.value()).query(Long.class).single();
    }
}
