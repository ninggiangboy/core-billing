package dev.ngb.core_billing.webhook.adapter.persistence;

import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.webhook.domain.endpoint.EndpointStatus;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpoint;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpointId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("webhook_endpoints")
record WebhookEndpointRow(
        @Id UUID id,
        UUID tenantId,
        UUID tenantSystemId,
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
                endpoint.tenantSystemId() == null ? null : endpoint.tenantSystemId().value(),
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
                tenantSystemId == null ? null : new TenantSystemId(tenantSystemId),
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
