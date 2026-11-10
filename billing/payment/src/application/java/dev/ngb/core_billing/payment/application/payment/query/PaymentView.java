package dev.ngb.core_billing.payment.application.payment.query;

import dev.ngb.core_billing.payment.domain.payment.PaymentStatus;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
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
