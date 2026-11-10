package io.corebilling.payment.adapter.persistence;

import io.corebilling.payment.domain.payment.Payment;
import io.corebilling.payment.domain.payment.PaymentId;
import io.corebilling.payment.domain.payment.PaymentStatus;
import io.corebilling.payment.domain.stripe.StripeConfigurationId;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("payments")
record PaymentRow(
        @Id UUID id,
        UUID tenantId,
        UUID configurationId,
        UUID walletId,
        UUID customerId,
        String currency,
        long amount,
        long refundedAmount,
        String status,
        String checkoutSessionId,
        String paymentIntentId,
        String checkoutUrl,
        String failureReason,
        UUID ledgerEntryId,
        String idempotencyKey,
        String requestHash,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        String purpose) {

    static PaymentRow from(Payment payment) {
        return new PaymentRow(
                payment.id().value(),
                payment.tenantId().value(),
                payment.configurationId().value(),
                payment.walletId().value(),
                payment.customerId().value(),
                payment.amount().currency().code(),
                payment.amount().minorUnits(),
                payment.refunded().minorUnits(),
                payment.status().name(),
                payment.checkoutSessionId(),
                payment.paymentIntentId(),
                payment.checkoutUrl(),
                payment.failureReason(),
                payment.ledgerEntryId(),
                payment.idempotencyKey(),
                payment.requestHash(),
                payment.createdAt(),
                payment.updatedAt(),
                payment.completedAt(),
                "TOP_UP");
    }

    Payment toDomain() {
        Currency money = Currency.of(currency.strip());
        return Payment.restore(
                new PaymentId(id),
                new TenantId(tenantId),
                new StripeConfigurationId(configurationId),
                new WalletId(walletId),
                new CustomerId(customerId),
                Money.of(amount, money),
                Money.of(refundedAmount, money),
                PaymentStatus.valueOf(status),
                checkoutSessionId,
                paymentIntentId,
                checkoutUrl,
                failureReason,
                ledgerEntryId,
                idempotencyKey,
                requestHash,
                createdAt,
                updatedAt,
                completedAt);
    }
}
