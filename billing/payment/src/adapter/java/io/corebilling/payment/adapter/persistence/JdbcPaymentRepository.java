package io.corebilling.payment.adapter.persistence;

import io.corebilling.payment.application.payment.port.PaymentRepository;
import io.corebilling.payment.domain.payment.Payment;
import io.corebilling.payment.domain.payment.PaymentAttempt;
import io.corebilling.payment.domain.payment.PaymentId;
import io.corebilling.payment.domain.payment.PaymentStatus;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcPaymentRepository implements PaymentRepository {

    private static final String COLUMNS = "id, tenant_id, configuration_id, wallet_id, customer_id, currency, amount, refunded_amount, status, "
            + "checkout_session_id, payment_intent_id, checkout_url, failure_reason, ledger_entry_id, idempotency_key, request_hash, created_at, "
            + "updated_at, completed_at, purpose";

    private final JdbcAggregateOperations aggregates;
    private final JdbcClient jdbc;

    public JdbcPaymentRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
    }

    @Override
    public void add(Payment p) {
        aggregates.insert(PaymentRow.from(p));
    }

    @Override
    public void update(Payment p) {
        aggregates.update(PaymentRow.from(p));
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return Optional.ofNullable(aggregates.findById(id.value(), PaymentRow.class)).map(PaymentRow::toDomain);
    }

    @Override
    public Optional<Payment> lockById(PaymentId id) {
        return jdbc.sql("select " + COLUMNS + " from payments where id = ? for update").param(id.value()).query(PaymentRow.class).optional()
                .map(PaymentRow::toDomain);
    }

    @Override
    public Optional<Payment> lockByCheckoutSession(String sessionId) {
        return jdbc.sql("select " + COLUMNS + " from payments where checkout_session_id = ? for update").param(sessionId).query(PaymentRow.class)
                .optional()
                .map(PaymentRow::toDomain);
    }

    @Override
    public Optional<Payment> lockByPaymentIntent(String intentId) {
        return jdbc.sql("select " + COLUMNS + " from payments where payment_intent_id = ? for update").param(intentId).query(PaymentRow.class)
                .optional()
                .map(PaymentRow::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String key) {
        return jdbc.sql("select " + COLUMNS + " from payments where idempotency_key = ?").param(key).query(PaymentRow.class).optional()
                .map(PaymentRow::toDomain);
    }

    @Override
    public void addAttempt(PaymentId id, PaymentAttempt attempt) {
        jdbc.sql("""
                        insert into payment_attempts (id, tenant_id, payment_id, stripe_event_id, failure_code, failure_message, occurred_at)
                        select ?, tenant_id, id, ?, ?, ?, ? from payments where id = ?
                        on conflict (payment_id, stripe_event_id) do nothing
                        """)
                .params(UUID.randomUUID(), attempt.stripeEventId(), attempt.failureCode(), clip(attempt.failureMessage()), Timestamp.from(attempt.occurredAt()),
                        id.value())
                .update();
    }

    @Override
    public List<PaymentAttempt> attempts(PaymentId id) {
        return jdbc.sql("select id, tenant_id, payment_id, stripe_event_id, failure_code, failure_message, occurred_at from payment_attempts "
                        + "where payment_id = ? order by occurred_at, id")
                .param(id.value())
                .query(PaymentAttemptRow.class)
                .list()
                .stream()
                .map(PaymentAttemptRow::toDomain)
                .toList();
    }

    private static String clip(String text) {
        return text == null || text.length() <= 500 ? text : text.substring(0, 500);
    }
}
