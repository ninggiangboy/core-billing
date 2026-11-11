package dev.ngb.core_billing.webhook.domain.delivery;

import dev.ngb.core_billing.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpointId;

/**
 * One event owed to one endpoint. {@code body} is the exact text that is signed and sent. {@code attempts}
 * counts attempts that were started, including one whose worker died.
 */
public record WebhookDelivery(
        WebhookDeliveryId id,
        TenantId tenantId,
        WebhookEndpointId endpointId,
        UUID eventId,
        String eventType,
        String body,
        DeliveryStatus status,
        int attempts,
        Instant nextAttemptAt,
        WebhookDeliveryId resendOf,
        Integer lastStatusCode,
        String lastError,
        Instant createdAt,
        Instant completedAt) {

    public WebhookDelivery {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(endpointId, "endpointId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** A new delivery, due immediately. */
    public static WebhookDelivery queue(
            WebhookDeliveryId id, TenantId tenantId, WebhookEndpointId endpointId, UUID eventId, String eventType, String body,
            WebhookDeliveryId resendOf, Instant now) {
        return new WebhookDelivery(id, tenantId, endpointId, eventId, eventType, body, DeliveryStatus.PENDING, 0, now, resendOf, null, null, now, null);
    }

    public boolean isFinished() {
        return status == DeliveryStatus.SUCCEEDED || status == DeliveryStatus.DEAD;
    }
}
