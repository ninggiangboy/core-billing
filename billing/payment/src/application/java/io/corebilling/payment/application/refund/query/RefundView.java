package io.corebilling.payment.application.refund.query;

import io.corebilling.payment.domain.refund.RefundStatus;
import io.corebilling.sharedkernel.Money;
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
