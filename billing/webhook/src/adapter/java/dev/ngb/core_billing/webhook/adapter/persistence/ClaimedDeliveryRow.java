package dev.ngb.core_billing.webhook.adapter.persistence;

import dev.ngb.core_billing.webhook.application.delivery.command.ClaimedDelivery;
import dev.ngb.core_billing.webhook.domain.delivery.WebhookDelivery;
import java.time.Instant;
import java.util.UUID;

/** A delivery joined with the endpoint credentials it must be signed with, read by the claiming statement. */
record ClaimedDeliveryRow(
        UUID id,
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
        Instant completedAt,
        String endpointUrl,
        String keyId,
        String secretCiphertext) {

    ClaimedDelivery toClaimed() {
        WebhookDelivery delivery = new WebhookDeliveryRow(
                        id, tenantId, endpointId, eventId, eventType, body, status, attempts, nextAttemptAt, resendOf, lastStatusCode, lastError, createdAt,
                        completedAt)
                .toDomain();
        return new ClaimedDelivery(delivery, endpointUrl, keyId, secretCiphertext);
    }
}
