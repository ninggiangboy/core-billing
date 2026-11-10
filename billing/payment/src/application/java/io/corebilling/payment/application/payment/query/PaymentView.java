package io.corebilling.payment.application.payment.query;

import io.corebilling.payment.domain.payment.PaymentStatus;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.WalletId;
import java.time.Instant;
import java.util.UUID;

/** A top-up payment as the read API shows it. */
public record PaymentView(
        UUID id,
        WalletId walletId,
        CustomerId customerId,
        PaymentStatus status,
        Money amount,
        Money refunded,
        String checkoutSessionId,
        String paymentIntentId,
        String checkoutUrl,
        String failureReason,
        UUID ledgerEntryId,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {}
