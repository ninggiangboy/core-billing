package io.corebilling.webhook.application.endpoint.query;

import io.corebilling.webhook.domain.endpoint.EndpointStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An endpoint as the admin GET handlers show it, including the encrypted secret but never the plaintext. */
public record WebhookEndpointView(
        UUID id,
        String url,
        String description,
        String eventTypesCsv,
        EndpointStatus status,
        String keyId,
        String secretCiphertext,
        Instant createdAt,
        Instant updatedAt,
        Instant secretRotatedAt) {

    public List<String> eventTypes() {
        return eventTypesCsv == null || eventTypesCsv.isEmpty() ? List.of() : List.of(eventTypesCsv.split(","));
    }
}
