package dev.ngb.core_billing.payment.adapter.refund.persistence;

import static dev.ngb.core_billing_jooq.Tables.REFUNDS;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.util.Causes;
import dev.ngb.core_billing.payment.application.refund.port.RefundRepository;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import dev.ngb.core_billing.payment.domain.refund.Refund;
import dev.ngb.core_billing.payment.domain.refund.RefundId;
import dev.ngb.core_billing.payment.domain.refund.RefundStatus;
import dev.ngb.core_billing.platform.api.error.IdempotencyConflictException;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing_jooq.Keys;
import dev.ngb.core_billing_jooq.tables.records.RefundsRecord;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public class JooqRefundRepository implements RefundRepository {

    private static final String KEY_CONSTRAINT = Keys.REFUNDS_TENANT_ID_IDEMPOTENCY_KEY_KEY.getName();

    private final DSLContext dsl;

    public JooqRefundRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(Refund r) {
        try {
            var insert = dsl.insertInto(REFUNDS);
            apply(insert, r).execute();
        } catch (RuntimeException e) {
            if (Causes.violates(e, KEY_CONSTRAINT)) {
                throw new IdempotencyConflictException("refund", r.idempotencyKey());
            }
            throw e;
        }
    }

    @Override
    public void update(Refund r) {
        dsl.update(REFUNDS)
                .set(REFUNDS.PAYMENT_ID, r.paymentId().value())
                .set(REFUNDS.CURRENCY, r.amount().currency().code())
                .set(REFUNDS.AMOUNT, r.amount().minorUnits())
                .set(REFUNDS.STATUS, r.status().name())
                .set(REFUNDS.REASON, r.reason())
                .set(REFUNDS.STRIPE_REFUND_ID, r.stripeRefundId())
                .set(REFUNDS.RESERVATION_ID, r.reservationId())
                .set(REFUNDS.LEDGER_ENTRY_ID, r.ledgerEntryId())
                .set(REFUNDS.FAILURE_REASON, r.failureReason())
                .set(REFUNDS.ACTOR, r.actor())
                .set(REFUNDS.IDEMPOTENCY_KEY, r.idempotencyKey())
                .set(REFUNDS.REQUEST_HASH, r.requestHash())
                .set(REFUNDS.UPDATED_AT, r.updatedAt())
                .set(REFUNDS.COMPLETED_AT, r.completedAt())
                .where(REFUNDS.ID.eq(r.id().value()))
                .execute();
    }

    @Override
    public Optional<Refund> findById(RefundId id) {
        return dsl.selectFrom(REFUNDS)
                .where(REFUNDS.ID.eq(id.value()))
                .fetchOptional(JooqRefundRepository::toDomain);
    }

    @Override
    public Optional<Refund> lockById(RefundId id) {
        return dsl.selectFrom(REFUNDS)
                .where(REFUNDS.ID.eq(id.value()))
                .forUpdate()
                .fetchOptional(JooqRefundRepository::toDomain);
    }

    @Override
    public Optional<Refund> findByStripeRefundId(String stripeRefundId) {
        return dsl.selectFrom(REFUNDS)
                .where(REFUNDS.STRIPE_REFUND_ID.eq(stripeRefundId))
                .fetchOptional(JooqRefundRepository::toDomain);
    }

    @Override
    public Optional<Refund> findByIdempotencyKey(String key) {
        return dsl.selectFrom(REFUNDS)
                .where(REFUNDS.IDEMPOTENCY_KEY.eq(key))
                .fetchOptional(JooqRefundRepository::toDomain);
    }

    @Override
    public long liveAmount(PaymentId paymentId) {
        Long sum = dsl.select(DSL.coalesce(DSL.sum(REFUNDS.AMOUNT), DSL.val(0L)))
                .from(REFUNDS)
                .where(REFUNDS.PAYMENT_ID.eq(paymentId.value()))
                .and(REFUNDS.STATUS.in("PENDING", "SUCCEEDED"))
                .fetchOne(0, Long.class);
        return sum == null ? 0L : sum;
    }

    private static org.jooq.InsertSetMoreStep<RefundsRecord> apply(org.jooq.InsertSetStep<RefundsRecord> insert, Refund r) {
        return insert
                .set(REFUNDS.ID, r.id().value())
                .set(REFUNDS.TENANT_ID, r.tenantId().value())
                .set(REFUNDS.PAYMENT_ID, r.paymentId().value())
                .set(REFUNDS.CURRENCY, r.amount().currency().code())
                .set(REFUNDS.AMOUNT, r.amount().minorUnits())
                .set(REFUNDS.STATUS, r.status().name())
                .set(REFUNDS.REASON, r.reason())
                .set(REFUNDS.STRIPE_REFUND_ID, r.stripeRefundId())
                .set(REFUNDS.RESERVATION_ID, r.reservationId())
                .set(REFUNDS.LEDGER_ENTRY_ID, r.ledgerEntryId())
                .set(REFUNDS.FAILURE_REASON, r.failureReason())
                .set(REFUNDS.ACTOR, r.actor())
                .set(REFUNDS.IDEMPOTENCY_KEY, r.idempotencyKey())
                .set(REFUNDS.REQUEST_HASH, r.requestHash())
                .set(REFUNDS.CREATED_AT, r.createdAt())
                .set(REFUNDS.UPDATED_AT, r.updatedAt())
                .set(REFUNDS.COMPLETED_AT, r.completedAt());
    }

    private static Refund toDomain(RefundsRecord row) {
        return Refund.restore(
                new RefundId(row.getId()),
                new TenantId(row.getTenantId()),
                new PaymentId(row.getPaymentId()),
                Money.of(row.getAmount(), Currency.of(row.getCurrency().strip())),
                RefundStatus.valueOf(row.getStatus()),
                row.getReason(),
                row.getStripeRefundId(),
                row.getReservationId(),
                row.getLedgerEntryId(),
                row.getFailureReason(),
                row.getActor(),
                row.getIdempotencyKey(),
                row.getRequestHash(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCompletedAt());
    }
}
