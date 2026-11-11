package dev.ngb.core_billing.webhook.adapter.persistence.delivery;

import static dev.ngb.core_billing_jooq.Tables.WEBHOOK_DELIVERIES;
import static dev.ngb.core_billing_jooq.Tables.WEBHOOK_DELIVERY_ATTEMPTS;
import static dev.ngb.core_billing_jooq.Tables.WEBHOOK_ENDPOINTS;
import static org.jooq.impl.DSL.noCondition;

import dev.ngb.core_billing.webhook.application.delivery.query.DeliveryAttemptView;
import dev.ngb.core_billing.webhook.application.delivery.query.DeliveryReadRepository;
import dev.ngb.core_billing.webhook.application.delivery.query.WebhookDeliveryView;
import dev.ngb.core_billing.webhook.domain.delivery.DeliveryStatus;
import dev.ngb.core_billing_jooq.tables.records.WebhookDeliveriesRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;

/** Selects deliveries and attempts straight into the views; the domain aggregate is never built. */
public class JooqWebhookDeliveryReadRepository implements DeliveryReadRepository {

    private final DSLContext dsl;

    public JooqWebhookDeliveryReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<WebhookDeliveryView> list(DeliveryStatus status, UUID endpointId, UUID before, int limit, UUID tenantSystemId) {
        Condition filter = (tenantSystemId == null ? noCondition() : WEBHOOK_ENDPOINTS.TENANT_SYSTEM_ID.eq(tenantSystemId))
                .and(status == null ? noCondition() : WEBHOOK_DELIVERIES.STATUS.eq(status.name()))
                .and(endpointId == null ? noCondition() : WEBHOOK_DELIVERIES.ENDPOINT_ID.eq(endpointId))
                .and(before == null ? noCondition() : WEBHOOK_DELIVERIES.ID.lt(before));
        return dsl.select(WEBHOOK_DELIVERIES.asterisk())
                .from(WEBHOOK_DELIVERIES)
                .join(WEBHOOK_ENDPOINTS)
                .on(WEBHOOK_ENDPOINTS.TENANT_ID.eq(WEBHOOK_DELIVERIES.TENANT_ID).and(WEBHOOK_ENDPOINTS.ID.eq(WEBHOOK_DELIVERIES.ENDPOINT_ID)))
                .where(filter)
                .orderBy(WEBHOOK_DELIVERIES.ID.desc())
                .limit(limit)
                .fetch(row -> toView(row.into(WEBHOOK_DELIVERIES)));
    }

    @Override
    public Optional<WebhookDeliveryView> findById(UUID id, UUID tenantSystemId) {
        return dsl.select(WEBHOOK_DELIVERIES.asterisk())
                .from(WEBHOOK_DELIVERIES)
                .join(WEBHOOK_ENDPOINTS)
                .on(WEBHOOK_ENDPOINTS.TENANT_ID.eq(WEBHOOK_DELIVERIES.TENANT_ID).and(WEBHOOK_ENDPOINTS.ID.eq(WEBHOOK_DELIVERIES.ENDPOINT_ID)))
                .where(WEBHOOK_DELIVERIES.ID.eq(id))
                .and(tenantSystemId == null ? noCondition() : WEBHOOK_ENDPOINTS.TENANT_SYSTEM_ID.eq(tenantSystemId))
                .fetchOptional(row -> toView(row.into(WEBHOOK_DELIVERIES)));
    }

    @Override
    public List<DeliveryAttemptView> attempts(UUID deliveryId) {
        return dsl.select(WEBHOOK_DELIVERY_ATTEMPTS.ATTEMPT_NO, WEBHOOK_DELIVERY_ATTEMPTS.STATUS_CODE, WEBHOOK_DELIVERY_ATTEMPTS.ERROR,
                        WEBHOOK_DELIVERY_ATTEMPTS.RESPONSE_EXCERPT, WEBHOOK_DELIVERY_ATTEMPTS.LATENCY_MS, WEBHOOK_DELIVERY_ATTEMPTS.ATTEMPTED_AT)
                .from(WEBHOOK_DELIVERY_ATTEMPTS)
                .where(WEBHOOK_DELIVERY_ATTEMPTS.DELIVERY_ID.eq(deliveryId))
                .orderBy(WEBHOOK_DELIVERY_ATTEMPTS.ATTEMPT_NO)
                .fetch(row -> new DeliveryAttemptView(
                        row.value1(), row.value2(), row.value3(), row.value4(), row.value5().longValue(), row.value6()));
    }

    private static WebhookDeliveryView toView(WebhookDeliveriesRecord row) {
        return new WebhookDeliveryView(
                row.getId(),
                row.getEndpointId(),
                row.getEventId(),
                row.getEventType(),
                row.getBody(),
                DeliveryStatus.valueOf(row.getStatus()),
                row.getAttempts(),
                row.getNextAttemptAt(),
                row.getResendOf(),
                row.getLastStatusCode(),
                row.getLastError(),
                row.getCreatedAt(),
                row.getCompletedAt());
    }
}
