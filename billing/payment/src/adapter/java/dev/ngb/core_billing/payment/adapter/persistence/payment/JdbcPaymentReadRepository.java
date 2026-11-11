package dev.ngb.core_billing.payment.adapter.persistence.payment;

import dev.ngb.core_billing.payment.application.payment.query.PaymentAttemptView;
import dev.ngb.core_billing.payment.application.payment.query.PaymentReadRepository;
import dev.ngb.core_billing.payment.application.payment.query.PaymentView;
import dev.ngb.core_billing.payment.domain.payment.PaymentStatus;
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
    public List<PaymentView> list(WalletId walletId, PaymentStatus status, UUID before, int limit, List<UUID> customerIds) {
        if (customerIds != null && customerIds.isEmpty()) {
            return List.of();
        }
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                        select %s
                        from payments
                        where true
                        """.formatted(COLUMNS));
        if (walletId != null) {
            sql.append(" and wallet_id = ?");
            params.add(walletId.value());
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
        return jdbc.sql(sql.toString()).params(params).query(Row.class).list().stream().map(Row::toView).toList();
    }

    @Override
    public Optional<PaymentView> findById(UUID id) {
        return jdbc.sql("""
                        select %s
                        from payments
                        where id = ?
                        """.formatted(COLUMNS)).param(id).query(Row.class).optional().map(Row::toView);
    }

    @Override
    public List<PaymentAttemptView> attempts(UUID paymentId) {
        return jdbc.sql("""
                        select failure_code, failure_message, occurred_at
                        from payment_attempts
                        where payment_id = ?
                        order by occurred_at, id
                        """)
                .param(paymentId)
                .query(PaymentAttemptView.class)
                .list();
    }
}
