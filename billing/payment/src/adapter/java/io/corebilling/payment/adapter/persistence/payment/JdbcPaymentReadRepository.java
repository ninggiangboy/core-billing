package io.corebilling.payment.adapter.persistence.payment;

import io.corebilling.payment.application.payment.query.PaymentAttemptView;
import io.corebilling.payment.application.payment.query.PaymentReadRepository;
import io.corebilling.payment.application.payment.query.PaymentView;
import io.corebilling.payment.domain.payment.PaymentStatus;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.WalletId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects payments and their failed attempts straight into views. */
public class JdbcPaymentReadRepository implements PaymentReadRepository {

    private static final String COLUMNS = "id, wallet_id, customer_id, currency, amount, refunded_amount, status, checkout_session_id, payment_intent_id, "
            + "checkout_url, failure_reason, ledger_entry_id, created_at, updated_at, completed_at";

    private record Row(
            UUID id,
            UUID walletId,
            UUID customerId,
            String currency,
            long amount,
            long refundedAmount,
            String status,
            String checkoutSessionId,
            String paymentIntentId,
            String checkoutUrl,
            String failureReason,
            UUID ledgerEntryId,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt) {

        PaymentView toView() {
            Currency money = Currency.of(currency.strip());
            return new PaymentView(
                    id,
                    new WalletId(walletId),
                    new CustomerId(customerId),
                    PaymentStatus.valueOf(status),
                    Money.of(amount, money),
                    Money.of(refundedAmount, money),
                    checkoutSessionId,
                    paymentIntentId,
                    checkoutUrl,
                    failureReason,
                    ledgerEntryId,
                    createdAt,
                    updatedAt,
                    completedAt);
        }
    }

    private final JdbcClient jdbc;

    public JdbcPaymentReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PaymentView> list(WalletId walletId, PaymentStatus status, UUID before, int limit) {
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
            params.add(before);
        }
        sql.append(" order by id desc limit ?");
        params.add(limit);
        return jdbc.sql(sql.toString()).params(params).query(Row.class).list().stream().map(Row::toView).toList();
    }

    @Override
    public Optional<PaymentView> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from payments where id = ?").param(id).query(Row.class).optional().map(Row::toView);
    }

    @Override
    public List<PaymentAttemptView> attempts(UUID paymentId) {
        return jdbc.sql("select failure_code, failure_message, occurred_at from payment_attempts where payment_id = ? order by occurred_at, id")
                .param(paymentId)
                .query(PaymentAttemptView.class)
                .list();
    }
}
