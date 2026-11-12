package dev.ngb.billing.payment.application.payment.query;

import dev.ngb.billing.payment.domain.payment.PaymentStatus;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing.sharedkernel.id.WalletId;
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
