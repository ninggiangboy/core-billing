package io.corebilling.payment.adapter.persistence.payment;

import io.corebilling.payment.domain.payment.PaymentAttempt;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("payment_attempts")
record PaymentAttemptRow(
        @Id UUID id,
        UUID tenantId,
        UUID paymentId,
        String stripeEventId,
        String failureCode,
        String failureMessage,
        Instant occurredAt) {

    PaymentAttempt toDomain() {
        return new PaymentAttempt(stripeEventId, failureCode, failureMessage, occurredAt);
    }
}
