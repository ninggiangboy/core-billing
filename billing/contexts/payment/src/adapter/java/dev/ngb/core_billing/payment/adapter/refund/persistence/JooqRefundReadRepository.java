package dev.ngb.core_billing.payment.adapter.refund.persistence;

import static dev.ngb.core_billing_jooq.Tables.REFUNDS;

import dev.ngb.core_billing.payment.application.refund.query.RefundReadRepository;
import dev.ngb.core_billing.payment.application.refund.query.RefundView;
import dev.ngb.core_billing.payment.domain.refund.RefundStatus;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing_jooq.tables.records.RefundsRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/** Selects refunds straight into views. */
public class JooqRefundReadRepository implements RefundReadRepository {

    private final DSLContext dsl;

    public JooqRefundReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<RefundView> byPayment(UUID paymentId) {
        return dsl.selectFrom(REFUNDS)
                .where(REFUNDS.PAYMENT_ID.eq(paymentId))
                .orderBy(REFUNDS.ID)
                .fetch(JooqRefundReadRepository::toView);
    }

    @Override
    public Optional<RefundView> findById(UUID id) {
        return dsl.selectFrom(REFUNDS)
                .where(REFUNDS.ID.eq(id))
                .fetchOptional(JooqRefundReadRepository::toView);
    }

    private static RefundView toView(RefundsRecord row) {
        return new RefundView(
                row.getId(),
                row.getPaymentId(),
                RefundStatus.valueOf(row.getStatus()),
                Money.of(row.getAmount(), Currency.of(row.getCurrency().strip())),
                row.getReason(),
                row.getFailureReason(),
                row.getLedgerEntryId(),
                row.getCreatedAt(),
                row.getCompletedAt());
    }
}
