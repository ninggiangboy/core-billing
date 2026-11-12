package dev.ngb.billing.webhook.adapter.endpoint.persistence;

import static dev.ngb.billing_jooq.Tables.WEBHOOK_ENDPOINTS;
import static org.jooq.impl.DSL.condition;

import dev.ngb.core.id.TenantId;
import dev.ngb.billing.platform.api.tenant.TenantSystemId;
import dev.ngb.billing.webhook.application.endpoint.port.WebhookEndpointRepository;
import dev.ngb.billing.webhook.domain.endpoint.EndpointStatus;
import dev.ngb.billing.webhook.domain.endpoint.WebhookEndpoint;
import dev.ngb.billing.webhook.domain.endpoint.WebhookEndpointId;
import dev.ngb.billing_jooq.tables.records.WebhookEndpointsRecord;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;

public class JooqWebhookEndpointRepository implements WebhookEndpointRepository {

    private final DSLContext dsl;

    public JooqWebhookEndpointRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(WebhookEndpoint e) {
        dsl.insertInto(WEBHOOK_ENDPOINTS)
                .set(WEBHOOK_ENDPOINTS.ID, e.id().value())
                .set(WEBHOOK_ENDPOINTS.TENANT_ID, e.tenantId().value())
                .set(WEBHOOK_ENDPOINTS.TENANT_SYSTEM_ID, e.tenantSystemId() == null ? null : e.tenantSystemId().value())
                .set(WEBHOOK_ENDPOINTS.URL, e.url())
                .set(WEBHOOK_ENDPOINTS.DESCRIPTION, e.description())
                .set(WEBHOOK_ENDPOINTS.EVENT_TYPES, e.eventTypes().toArray(String[]::new))
                .set(WEBHOOK_ENDPOINTS.STATUS, e.status().name())
                .set(WEBHOOK_ENDPOINTS.KEY_ID, e.keyId())
                .set(WEBHOOK_ENDPOINTS.SECRET_CIPHERTEXT, e.secretCiphertext())
                .set(WEBHOOK_ENDPOINTS.CREATED_AT, e.createdAt())
                .set(WEBHOOK_ENDPOINTS.UPDATED_AT, e.updatedAt())
                .set(WEBHOOK_ENDPOINTS.SECRET_ROTATED_AT, e.secretRotatedAt())
                .execute();
    }

    @Override
    public void update(WebhookEndpoint e) {
        int updated = dsl.update(WEBHOOK_ENDPOINTS)
                .set(WEBHOOK_ENDPOINTS.URL, e.url())
                .set(WEBHOOK_ENDPOINTS.DESCRIPTION, e.description())
                .set(WEBHOOK_ENDPOINTS.EVENT_TYPES, e.eventTypes().toArray(String[]::new))
                .set(WEBHOOK_ENDPOINTS.STATUS, e.status().name())
                .set(WEBHOOK_ENDPOINTS.KEY_ID, e.keyId())
                .set(WEBHOOK_ENDPOINTS.SECRET_CIPHERTEXT, e.secretCiphertext())
                .set(WEBHOOK_ENDPOINTS.UPDATED_AT, e.updatedAt())
                .set(WEBHOOK_ENDPOINTS.SECRET_ROTATED_AT, e.secretRotatedAt())
                .where(WEBHOOK_ENDPOINTS.ID.eq(e.id().value()))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("webhook endpoint " + e.id() + " was not updated");
        }
    }

    @Override
    public Optional<WebhookEndpoint> findById(WebhookEndpointId id) {
        return dsl.selectFrom(WEBHOOK_ENDPOINTS)
                .where(WEBHOOK_ENDPOINTS.ID.eq(id.value()))
                .fetchOptional(JooqWebhookEndpointRepository::toDomain);
    }

    @Override
    public List<WebhookEndpoint> findSubscribed(String eventType) {
        return dsl.selectFrom(WEBHOOK_ENDPOINTS)
                .where(WEBHOOK_ENDPOINTS.STATUS.eq("ACTIVE"))
                .and(condition("{0} = any({1})", eventType, WEBHOOK_ENDPOINTS.EVENT_TYPES)
                        .or(condition("'*' = any({0})", WEBHOOK_ENDPOINTS.EVENT_TYPES)))
                .orderBy(WEBHOOK_ENDPOINTS.ID)
                .fetch(JooqWebhookEndpointRepository::toDomain);
    }

    private static WebhookEndpoint toDomain(WebhookEndpointsRecord row) {
        return WebhookEndpoint.restore(
                new WebhookEndpointId(row.getId()),
                new TenantId(row.getTenantId()),
                row.getTenantSystemId() == null ? null : new TenantSystemId(row.getTenantSystemId()),
                row.getUrl(),
                row.getDescription(),
                eventTypes(row.getEventTypes()),
                EndpointStatus.valueOf(row.getStatus()),
                row.getKeyId(),
                row.getSecretCiphertext(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getSecretRotatedAt());
    }

    private static List<String> eventTypes(String[] types) {
        return types == null || types.length == 0 ? List.of() : Arrays.asList(types);
    }
}
