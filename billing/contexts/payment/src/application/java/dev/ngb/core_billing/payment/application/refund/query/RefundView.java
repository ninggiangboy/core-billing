package dev.ngb.core_billing.payment.application.refund.query;

import dev.ngb.core_billing.payment.domain.refund.RefundStatus;
import dev.ngb.core_billing.sharedkernel.money.Money;
import java.time.Instant;
import java.util.UUID;

/** A refund as the read API shows it. */
public record RefundView(
        UUID id,
        UUID paymentId,
        RefundStatus status,
        Money amount,
        String reason,
        String failureReason,
        UUID ledgerEntryId,
        Instant createdAt,
        Instant completedAt) {}
