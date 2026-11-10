package io.corebilling.payment.adapter.persistence;

import io.corebilling.payment.application.payment.port.PaymentRepository;
import io.corebilling.payment.domain.payment.Payment;
import io.corebilling.payment.domain.payment.PaymentAttempt;
import io.corebilling.payment.domain.payment.PaymentId;
import io.corebilling.payment.domain.payment.PaymentStatus;
import io.corebilling.payment.domain.stripe.StripeConfigurationId;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcPaymentRepository implements PaymentRepository {

    private static final String COLUMNS = "id, tenant_id, configuration_id, wallet_id, customer_id, currency, amount, refunded_amount, status, "
            + "checkout_session_id, payment_intent_id, checkout_url, failure_reason, ledger_entry_id, idempotency_key, request_hash, created_at, "
            + "updated_at, completed_at";

    private static final RowMapper<Payment> MAPPER = (rs, n) -> {
        Currency currency = Currency.of(rs.getString("currency").strip());
        return Payment.restore(
                new PaymentId(Rows.uuid(rs, "id")),
                new TenantId(Rows.uuid(rs, "tenant_id")),
                new StripeConfigurationId(Rows.uuid(rs, "configuration_id")),
                new WalletId(Rows.uuid(rs, "wallet_id")),
                new CustomerId(Rows.uuid(rs, "customer_id")),
                Money.of(rs.getLong("amount"), currency),
                Money.of(rs.getLong("refunded_amount"), currency),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getString("checkout_session_id"),
                rs.getString("payment_intent_id"),
                rs.getString("checkout_url"),
                rs.getString("failure_reason"),
                Rows.uuid(rs, "ledger_entry_id"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                Rows.instant(rs, "created_at"),
                Rows.instant(rs, "updated_at"),
                Rows.instant(rs, "completed_at"));
    };

    private final JdbcClient jdbc;

    public JdbcPaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Payment p) {
        jdbc.sql("insert into payments (" + COLUMNS + ", purpose) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'TOP_UP')")
                .params(p.id().value(), p.tenantId().value(), p.configurationId().value(), p.walletId().value(), p.customerId().value(),
                        p.amount().currency().code(), p.amount().minorUnits(), p.refunded().minorUnits(), p.status().name(), p.checkoutSessionId(),
                        p.paymentIntentId(), p.checkoutUrl(), p.failureReason(), p.ledgerEntryId(), p.idempotencyKey(), p.requestHash(),
                        Timestamp.from(p.createdAt()), Timestamp.from(p.updatedAt()), Rows.timestamp(p.completedAt()))
                .update();
    }

    @Override
    public void update(Payment p) {
        int updated = jdbc.sql("""
                        update payments
                        set refunded_amount = ?, status = ?, checkout_session_id = ?, payment_intent_id = ?, checkout_url = ?, failure_reason = ?,
                            ledger_entry_id = ?, updated_at = ?, completed_at = ?
                        where id = ?
                        """)
                .params(p.refunded().minorUnits(), p.status().name(), p.checkoutSessionId(), p.paymentIntentId(), p.checkoutUrl(), p.failureReason(),
                        p.ledgerEntryId(), Timestamp.from(p.updatedAt()), Rows.timestamp(p.completedAt()), p.id().value())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("payment " + p.id() + " was not updated");
        }
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return jdbc.sql("select " + COLUMNS + " from payments where id = ?").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public Optional<Payment> lockById(PaymentId id) {
        return jdbc.sql("select " + COLUMNS + " from payments where id = ? for update").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public Optional<Payment> lockByCheckoutSession(String sessionId) {
        return jdbc.sql("select " + COLUMNS + " from payments where checkout_session_id = ? for update").param(sessionId).query(MAPPER).optional();
    }

    @Override
    public Optional<Payment> lockByPaymentIntent(String intentId) {
        return jdbc.sql("select " + COLUMNS + " from payments where payment_intent_id = ? for update").param(intentId).query(MAPPER).optional();
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String key) {
        return jdbc.sql("select " + COLUMNS + " from payments where idempotency_key = ?").param(key).query(MAPPER).optional();
    }

    @Override
    public List<Payment> list(WalletId walletId, PaymentStatus status, PaymentId before, int limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("select " + COLUMNS + " from payments where true");
        if (walletId != null) {
            sql.append(" and wallet_id = ?");
            params.add(walletId.value());
        }
        if (status != null) {
            sql.append(" and status = ?");
            params.add(status.name());
        }
        if (before != null) {
            sql.append(" and id < ?");
            params.add(before.value());
        }
        sql.append(" order by id desc limit ?");
        params.add(limit);
        return jdbc.sql(sql.toString()).params(params).query(MAPPER).list();
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
        return jdbc.sql("select stripe_event_id, failure_code, failure_message, occurred_at from payment_attempts where payment_id = ? order by occurred_at, id")
                .param(id.value())
                .query((rs, n) -> new PaymentAttempt(rs.getString("stripe_event_id"), rs.getString("failure_code"), rs.getString("failure_message"),
                        Rows.instant(rs, "occurred_at")))
                .list();
    }

    private static String clip(String text) {
        return text == null || text.length() <= 500 ? text : text.substring(0, 500);
    }
}
