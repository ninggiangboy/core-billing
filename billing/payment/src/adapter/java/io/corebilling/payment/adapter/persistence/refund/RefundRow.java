package io.corebilling.payment.adapter.persistence.refund;

import io.corebilling.payment.domain.payment.PaymentId;
import io.corebilling.payment.domain.refund.Refund;
import io.corebilling.payment.domain.refund.RefundId;
import io.corebilling.payment.domain.refund.RefundStatus;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("refunds")
record RefundRow(
        @Id UUID id,
        UUID tenantId,
        UUID paymentId,
        String currency,
        long amount,
        String status,
        String reason,
        String stripeRefundId,
        UUID reservationId,
        UUID ledgerEntryId,
        String failureReason,
        String actor,
        String idempotencyKey,
        String requestHash,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    static RefundRow from(Refund refund) {
        return new RefundRow(
                refund.id().value(),
                refund.tenantId().value(),
                refund.paymentId().value(),
                refund.amount().currency().code(),
                refund.amount().minorUnits(),
                refund.status().name(),
                refund.reason(),
                refund.stripeRefundId(),
                refund.reservationId(),
                refund.ledgerEntryId(),
                refund.failureReason(),
                refund.actor(),
                refund.idempotencyKey(),
                refund.requestHash(),
                refund.createdAt(),
                refund.updatedAt(),
                refund.completedAt());
    }

    Refund toDomain() {
        return Refund.restore(
                new RefundId(id),
                new TenantId(tenantId),
                new PaymentId(paymentId),
                Money.of(amount, Currency.of(currency.strip())),
                RefundStatus.valueOf(status),
                reason,
                stripeRefundId,
                reservationId,
                ledgerEntryId,
                failureReason,
                actor,
                idempotencyKey,
                requestHash,
                createdAt,
                updatedAt,
                completedAt);
    }
}
