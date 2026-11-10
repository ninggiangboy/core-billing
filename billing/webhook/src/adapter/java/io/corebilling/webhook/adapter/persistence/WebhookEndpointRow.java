package io.corebilling.webhook.adapter.persistence;

import io.corebilling.sharedkernel.TenantId;
import io.corebilling.webhook.domain.endpoint.EndpointStatus;
import io.corebilling.webhook.domain.endpoint.WebhookEndpoint;
import io.corebilling.webhook.domain.endpoint.WebhookEndpointId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("webhook_endpoints")
record WebhookEndpointRow(
        @Id UUID id,
        UUID tenantId,
        String url,
        String description,
        String eventTypesCsv,
        String status,
        String keyId,
        String secretCiphertext,
        Instant createdAt,
        Instant updatedAt,
        Instant secretRotatedAt) {

    static WebhookEndpointRow from(WebhookEndpoint endpoint) {
        return new WebhookEndpointRow(
                endpoint.id().value(),
                endpoint.tenantId().value(),
                endpoint.url(),
                endpoint.description(),
                String.join(",", endpoint.eventTypes()),
                endpoint.status().name(),
                endpoint.keyId(),
                endpoint.secretCiphertext(),
                endpoint.createdAt(),
                endpoint.updatedAt(),
                endpoint.secretRotatedAt());
    }

    WebhookEndpoint toDomain() {
        return WebhookEndpoint.restore(
                new WebhookEndpointId(id),
                new TenantId(tenantId),
                url,
                description,
                eventTypesCsv == null || eventTypesCsv.isEmpty() ? List.of() : List.of(eventTypesCsv.split(",")),
                EndpointStatus.valueOf(status),
                keyId,
                secretCiphertext,
                createdAt,
                updatedAt,
                secretRotatedAt);
    }
}
