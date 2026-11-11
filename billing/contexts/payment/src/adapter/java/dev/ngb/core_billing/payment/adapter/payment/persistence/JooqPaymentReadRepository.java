package dev.ngb.core_billing.payment.adapter.payment.persistence;

import static dev.ngb.core_billing_jooq.Tables.PAYMENTS;
import static dev.ngb.core_billing_jooq.Tables.PAYMENT_ATTEMPTS;
import static org.jooq.impl.DSL.noCondition;

import dev.ngb.core_billing.payment.application.payment.query.PaymentAttemptView;
import dev.ngb.core_billing.payment.application.payment.query.PaymentReadRepository;
import dev.ngb.core_billing.payment.application.payment.query.PaymentView;
import dev.ngb.core_billing.payment.domain.payment.PaymentStatus;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import dev.ngb.core_billing_jooq.tables.records.PaymentsRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;

/** Selects payments and their failed attempts straight into views. */
public class JooqPaymentReadRepository implements PaymentReadRepository {

    private final DSLContext dsl;

    public JooqPaymentReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<PaymentView> list(WalletId walletId, PaymentStatus status, UUID before, int limit, List<UUID> customerIds) {
        if (customerIds != null && customerIds.isEmpty()) {
            return List.of();
        }
        Condition filter = (walletId == null ? noCondition() : PAYMENTS.WALLET_ID.eq(walletId.value()))
                .and(customerIds == null ? noCondition() : PAYMENTS.CUSTOMER_ID.in(customerIds))
                .and(status == null ? noCondition() : PAYMENTS.STATUS.eq(status.name()))
                .and(before == null ? noCondition() : PAYMENTS.ID.lt(before));
        return dsl.selectFrom(PAYMENTS)
                .where(filter)
                .orderBy(PAYMENTS.ID.desc())
                .limit(limit)
                .fetch(JooqPaymentReadRepository::toView);
    }

    @Override
    public Optional<PaymentView> findById(UUID id) {
        return dsl.selectFrom(PAYMENTS)
                .where(PAYMENTS.ID.eq(id))
                .fetchOptional(JooqPaymentReadRepository::toView);
    }

    @Override
    public List<PaymentAttemptView> attempts(UUID paymentId) {
        return dsl.select(PAYMENT_ATTEMPTS.FAILURE_CODE, PAYMENT_ATTEMPTS.FAILURE_MESSAGE, PAYMENT_ATTEMPTS.OCCURRED_AT)
                .from(PAYMENT_ATTEMPTS)
                .where(PAYMENT_ATTEMPTS.PAYMENT_ID.eq(paymentId))
                .orderBy(PAYMENT_ATTEMPTS.OCCURRED_AT, PAYMENT_ATTEMPTS.ID)
                .fetch(row -> new PaymentAttemptView(row.value1(), row.value2(), row.value3()));
    }

    private static PaymentView toView(PaymentsRecord row) {
        Currency money = Currency.of(row.getCurrency().strip());
        return new PaymentView(
                row.getId(),
                new WalletId(row.getWalletId()),
                new CustomerId(row.getCustomerId()),
                PaymentStatus.valueOf(row.getStatus()),
                Money.of(row.getAmount(), money),
                Money.of(row.getRefundedAmount(), money),
                row.getCheckoutSessionId(),
                row.getPaymentIntentId(),
                row.getCheckoutUrl(),
                row.getFailureReason(),
                row.getLedgerEntryId(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCompletedAt());
    }
}
