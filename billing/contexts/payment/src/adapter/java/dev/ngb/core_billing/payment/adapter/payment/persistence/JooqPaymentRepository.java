package dev.ngb.core_billing.payment.adapter.payment.persistence;

import static dev.ngb.core_billing_jooq.Tables.PAYMENTS;
import static dev.ngb.core_billing_jooq.Tables.PAYMENT_ATTEMPTS;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.util.Text;
import dev.ngb.core_billing.payment.application.payment.port.PaymentRepository;
import dev.ngb.core_billing.payment.domain.payment.Payment;
import dev.ngb.core_billing.payment.domain.payment.PaymentAttempt;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.payment.domain.payment.PaymentStatus;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing_jooq.tables.records.PaymentsRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public class JooqPaymentRepository implements PaymentRepository {

    private static final int MAX_FAILURE = 500;

    private final DSLContext dsl;

    public JooqPaymentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean addIfAbsent(Payment p) {
        var insert = dsl.insertInto(PAYMENTS);
        return apply(insert, p)
                .onConflict(PAYMENTS.TENANT_ID, PAYMENTS.IDEMPOTENCY_KEY)
                .doNothing()
                .execute() == 1;
    }

    @Override
    public void update(Payment p) {
        dsl.update(PAYMENTS)
                .set(PAYMENTS.CONFIGURATION_ID, p.configurationId().value())
                .set(PAYMENTS.WALLET_ID, p.walletId().value())
                .set(PAYMENTS.CUSTOMER_ID, p.customerId().value())
                .set(PAYMENTS.CURRENCY, p.amount().currency().code())
                .set(PAYMENTS.AMOUNT, p.amount().minorUnits())
                .set(PAYMENTS.REFUNDED_AMOUNT, p.refunded().minorUnits())
                .set(PAYMENTS.PURPOSE, purpose(p))
                .set(PAYMENTS.STATUS, p.status().name())
                .set(PAYMENTS.CHECKOUT_SESSION_ID, p.checkoutSessionId())
                .set(PAYMENTS.PAYMENT_INTENT_ID, p.paymentIntentId())
                .set(PAYMENTS.CHECKOUT_URL, p.checkoutUrl())
                .set(PAYMENTS.FAILURE_REASON, p.failureReason())
                .set(PAYMENTS.LEDGER_ENTRY_ID, p.ledgerEntryId())
                .set(PAYMENTS.INVOICE_ID, p.invoiceId())
                .set(PAYMENTS.IDEMPOTENCY_KEY, p.idempotencyKey())
                .set(PAYMENTS.REQUEST_HASH, p.requestHash())
                .set(PAYMENTS.UPDATED_AT, p.updatedAt())
                .set(PAYMENTS.COMPLETED_AT, p.completedAt())
                .where(PAYMENTS.ID.eq(p.id().value()))
                .execute();
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return dsl.selectFrom(PAYMENTS)
                .where(PAYMENTS.ID.eq(id.value()))
                .fetchOptional(JooqPaymentRepository::toDomain);
    }

    @Override
    public Optional<Payment> lockById(PaymentId id) {
        return dsl.selectFrom(PAYMENTS)
                .where(PAYMENTS.ID.eq(id.value()))
                .forUpdate()
                .fetchOptional(JooqPaymentRepository::toDomain);
    }

    @Override
    public Optional<Payment> lockByCheckoutSession(String sessionId) {
        return dsl.selectFrom(PAYMENTS)
                .where(PAYMENTS.CHECKOUT_SESSION_ID.eq(sessionId))
                .forUpdate()
                .fetchOptional(JooqPaymentRepository::toDomain);
    }

    @Override
    public Optional<Payment> lockByPaymentIntent(String intentId) {
        return dsl.selectFrom(PAYMENTS)
                .where(PAYMENTS.PAYMENT_INTENT_ID.eq(intentId))
                .forUpdate()
                .fetchOptional(JooqPaymentRepository::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String key) {
        return dsl.selectFrom(PAYMENTS)
                .where(PAYMENTS.IDEMPOTENCY_KEY.eq(key))
                .fetchOptional(JooqPaymentRepository::toDomain);
    }

    @Override
    public void addAttempt(PaymentId id, PaymentAttempt attempt) {
        dsl.insertInto(
                        PAYMENT_ATTEMPTS,
                        PAYMENT_ATTEMPTS.ID,
                        PAYMENT_ATTEMPTS.TENANT_ID,
                        PAYMENT_ATTEMPTS.PAYMENT_ID,
                        PAYMENT_ATTEMPTS.STRIPE_EVENT_ID,
                        PAYMENT_ATTEMPTS.FAILURE_CODE,
                        PAYMENT_ATTEMPTS.FAILURE_MESSAGE,
                        PAYMENT_ATTEMPTS.OCCURRED_AT)
                .select(DSL.select(
                                DSL.val(UUID.randomUUID(), PAYMENT_ATTEMPTS.ID),
                                PAYMENTS.TENANT_ID,
                                PAYMENTS.ID,
                                DSL.val(attempt.stripeEventId(), PAYMENT_ATTEMPTS.STRIPE_EVENT_ID),
                                DSL.val(attempt.failureCode(), PAYMENT_ATTEMPTS.FAILURE_CODE),
                                DSL.val(Text.clip(attempt.failureMessage(), MAX_FAILURE), PAYMENT_ATTEMPTS.FAILURE_MESSAGE),
                                DSL.val(attempt.occurredAt(), PAYMENT_ATTEMPTS.OCCURRED_AT))
                        .from(PAYMENTS)
                        .where(PAYMENTS.ID.eq(id.value())))
                .onConflict(PAYMENT_ATTEMPTS.PAYMENT_ID, PAYMENT_ATTEMPTS.STRIPE_EVENT_ID)
                .doNothing()
                .execute();
    }

    @Override
    public List<PaymentAttempt> attempts(PaymentId id) {
        return dsl.selectFrom(PAYMENT_ATTEMPTS)
                .where(PAYMENT_ATTEMPTS.PAYMENT_ID.eq(id.value()))
                .orderBy(PAYMENT_ATTEMPTS.OCCURRED_AT, PAYMENT_ATTEMPTS.ID)
                .fetch(row -> new PaymentAttempt(row.getStripeEventId(), row.getFailureCode(), row.getFailureMessage(), row.getOccurredAt()));
    }

    private static org.jooq.InsertSetMoreStep<PaymentsRecord> apply(org.jooq.InsertSetStep<PaymentsRecord> insert, Payment p) {
        return insert
                .set(PAYMENTS.ID, p.id().value())
                .set(PAYMENTS.TENANT_ID, p.tenantId().value())
                .set(PAYMENTS.CONFIGURATION_ID, p.configurationId().value())
                .set(PAYMENTS.WALLET_ID, p.walletId().value())
                .set(PAYMENTS.CUSTOMER_ID, p.customerId().value())
                .set(PAYMENTS.CURRENCY, p.amount().currency().code())
                .set(PAYMENTS.AMOUNT, p.amount().minorUnits())
                .set(PAYMENTS.REFUNDED_AMOUNT, p.refunded().minorUnits())
                .set(PAYMENTS.PURPOSE, purpose(p))
                .set(PAYMENTS.STATUS, p.status().name())
                .set(PAYMENTS.CHECKOUT_SESSION_ID, p.checkoutSessionId())
                .set(PAYMENTS.PAYMENT_INTENT_ID, p.paymentIntentId())
                .set(PAYMENTS.CHECKOUT_URL, p.checkoutUrl())
                .set(PAYMENTS.FAILURE_REASON, p.failureReason())
                .set(PAYMENTS.LEDGER_ENTRY_ID, p.ledgerEntryId())
                .set(PAYMENTS.INVOICE_ID, p.invoiceId())
                .set(PAYMENTS.IDEMPOTENCY_KEY, p.idempotencyKey())
                .set(PAYMENTS.REQUEST_HASH, p.requestHash())
                .set(PAYMENTS.CREATED_AT, p.createdAt())
                .set(PAYMENTS.UPDATED_AT, p.updatedAt())
                .set(PAYMENTS.COMPLETED_AT, p.completedAt());
    }

    private static String purpose(Payment p) {
        return p.isForInvoice() ? "INVOICE" : "TOP_UP";
    }

    private static Payment toDomain(PaymentsRecord row) {
        Currency money = Currency.of(row.getCurrency().strip());
        return Payment.restore(
                new PaymentId(row.getId()),
                new TenantId(row.getTenantId()),
                new StripeConfigurationId(row.getConfigurationId()),
                new WalletId(row.getWalletId()),
                new CustomerId(row.getCustomerId()),
                Money.of(row.getAmount(), money),
                Money.of(row.getRefundedAmount(), money),
                PaymentStatus.valueOf(row.getStatus()),
                row.getCheckoutSessionId(),
                row.getPaymentIntentId(),
                row.getCheckoutUrl(),
                row.getFailureReason(),
                row.getLedgerEntryId(),
                row.getInvoiceId(),
                row.getIdempotencyKey(),
                row.getRequestHash(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCompletedAt());
    }
}
