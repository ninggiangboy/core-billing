package io.corebilling.webhook.adapter.persistence;

import io.corebilling.sharedkernel.TenantId;
import io.corebilling.webhook.domain.delivery.DeliveryStatus;
import io.corebilling.webhook.domain.delivery.WebhookDelivery;
import io.corebilling.webhook.domain.delivery.WebhookDeliveryId;
import io.corebilling.webhook.domain.endpoint.WebhookEndpointId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("webhook_deliveries")
record WebhookDeliveryRow(
        @Id UUID id,
        UUID tenantId,
        UUID endpointId,
        UUID eventId,
        String eventType,
        String body,
        String status,
        int attempts,
        Instant nextAttemptAt,
        UUID resendOf,
        Integer lastStatusCode,
        String lastError,
        Instant createdAt,
        Instant completedAt) {

    static WebhookDeliveryRow from(WebhookDelivery delivery) {
        return new WebhookDeliveryRow(
                delivery.id().value(),
                delivery.tenantId().value(),
                delivery.endpointId().value(),
                delivery.eventId(),
                delivery.eventType(),
                delivery.body(),
                delivery.status().name(),
                delivery.attempts(),
                delivery.nextAttemptAt(),
                delivery.resendOf() == null ? null : delivery.resendOf().value(),
                delivery.lastStatusCode(),
                delivery.lastError(),
                delivery.createdAt(),
                delivery.completedAt());
    }

    WebhookDelivery toDomain() {
        return new WebhookDelivery(
                new WebhookDeliveryId(id),
                new TenantId(tenantId),
                new WebhookEndpointId(endpointId),
                eventId,
                eventType,
                body,
                DeliveryStatus.valueOf(status),
                attempts,
                nextAttemptAt,
                resendOf == null ? null : new WebhookDeliveryId(resendOf),
                lastStatusCode,
                lastError,
                createdAt,
                completedAt);
    }
}
