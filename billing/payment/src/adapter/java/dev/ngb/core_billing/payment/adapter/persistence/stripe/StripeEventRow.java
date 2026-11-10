package dev.ngb.core_billing.payment.adapter.persistence.stripe;

import dev.ngb.core_billing.payment.domain.stripe.EventStatus;
import dev.ngb.core_billing.payment.domain.stripe.StoredEventId;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.payment.domain.stripe.StripeEvent;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("stripe_events")
record StripeEventRow(
        @Id UUID id,
        UUID tenantId,
        UUID configurationId,
        String stripeAccount,
        String eventId,
        String type,
        boolean livemode,
        String payload,
        String status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant receivedAt,
        Instant processedAt) {

    static StripeEventRow from(StripeEvent event) {
        return new StripeEventRow(
                event.id().value(),
                event.tenantId().value(),
                event.configurationId().value(),
                event.stripeAccount(),
                event.eventId(),
                event.type(),
                event.livemode(),
                event.payload(),
                event.status().name(),
                event.attempts(),
                event.nextAttemptAt(),
                event.lastError(),
                event.receivedAt(),
                event.processedAt());
    }

    StripeEvent toDomain() {
        return StripeEvent.restore(
                new StoredEventId(id),
                new TenantId(tenantId),
                new StripeConfigurationId(configurationId),
                stripeAccount,
                eventId,
                type,
                livemode,
                payload,
                EventStatus.valueOf(status),
                attempts,
                nextAttemptAt,
                lastError,
                receivedAt,
                processedAt);
    }
}
