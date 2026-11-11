package dev.ngb.core_billing.webhook.adapter.endpoint.persistence;

import static dev.ngb.core_billing_jooq.Tables.WEBHOOK_ENDPOINTS;

import dev.ngb.core_billing.webhook.application.endpoint.query.EndpointReadRepository;
import dev.ngb.core_billing.webhook.application.endpoint.query.WebhookEndpointView;
import dev.ngb.core_billing.webhook.domain.endpoint.EndpointStatus;
import dev.ngb.core_billing_jooq.tables.records.WebhookEndpointsRecord;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/** Selects endpoints straight into the view; the domain aggregate is never built. */
public class JooqWebhookEndpointReadRepository implements EndpointReadRepository {

    private final DSLContext dsl;

    public JooqWebhookEndpointReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<WebhookEndpointView> list(UUID tenantSystemId) {
        return dsl.selectFrom(WEBHOOK_ENDPOINTS)
                .where(tenantSystemId == null ? org.jooq.impl.DSL.noCondition() : WEBHOOK_ENDPOINTS.TENANT_SYSTEM_ID.eq(tenantSystemId))
                .orderBy(WEBHOOK_ENDPOINTS.CREATED_AT, WEBHOOK_ENDPOINTS.ID)
                .fetch(JooqWebhookEndpointReadRepository::toView);
    }

    @Override
    public Optional<WebhookEndpointView> findById(UUID id, UUID tenantSystemId) {
        return dsl.selectFrom(WEBHOOK_ENDPOINTS)
                .where(WEBHOOK_ENDPOINTS.ID.eq(id))
                .and(tenantSystemId == null ? org.jooq.impl.DSL.noCondition() : WEBHOOK_ENDPOINTS.TENANT_SYSTEM_ID.eq(tenantSystemId))
                .fetchOptional(JooqWebhookEndpointReadRepository::toView);
    }

    private static WebhookEndpointView toView(WebhookEndpointsRecord row) {
        String[] eventTypes = row.getEventTypes();
        return new WebhookEndpointView(
                row.getId(),
                row.getUrl(),
                row.getDescription(),
                eventTypes == null || eventTypes.length == 0 ? null : String.join(",", Arrays.asList(eventTypes)),
                EndpointStatus.valueOf(row.getStatus()),
                row.getKeyId(),
                row.getSecretCiphertext(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getSecretRotatedAt());
    }
}
