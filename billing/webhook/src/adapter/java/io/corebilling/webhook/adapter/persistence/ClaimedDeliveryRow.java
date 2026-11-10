package io.corebilling.webhook.adapter.persistence;

import io.corebilling.webhook.application.delivery.command.ClaimedDelivery;
import io.corebilling.webhook.domain.delivery.WebhookDelivery;
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
