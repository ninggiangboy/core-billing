package dev.ngb.core_billing.webhook.adapter.persistence;

import static dev.ngb.core_billing_jooq.Tables.WEBHOOK_DELIVERIES;
import static dev.ngb.core_billing_jooq.Tables.WEBHOOK_DELIVERY_ATTEMPTS;
import static dev.ngb.core_billing_jooq.Tables.WEBHOOK_ENDPOINTS;

import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.Text;
import dev.ngb.core_billing.webhook.application.delivery.command.ClaimedDelivery;
import dev.ngb.core_billing.webhook.application.delivery.port.WebhookDeliveryRepository;
import dev.ngb.core_billing.webhook.domain.delivery.DeliveryAttempt;
import dev.ngb.core_billing.webhook.domain.delivery.DeliveryStatus;
import dev.ngb.core_billing.webhook.domain.delivery.WebhookDelivery;
import dev.ngb.core_billing.webhook.domain.delivery.WebhookDeliveryId;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpointId;
import dev.ngb.core_billing_jooq.tables.records.WebhookDeliveriesRecord;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public class JooqWebhookDeliveryRepository implements WebhookDeliveryRepository {

    private static final int MAX_ERROR = 500;
    private static final int MAX_EXCERPT = 1000;

    private final DSLContext dsl;

    public JooqWebhookDeliveryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean addIfAbsent(WebhookDelivery d) {
        // The partial unique index (endpoint, event) over originals makes a repeated event a no-op.
        return insert(d).onDuplicateKeyIgnore().execute() == 1;
    }

    @Override
    public void add(WebhookDelivery d) {
        insert(d).execute();
    }

    private org.jooq.InsertSetMoreStep<WebhookDeliveriesRecord> insert(WebhookDelivery d) {
        return dsl.insertInto(WEBHOOK_DELIVERIES)
                .set(WEBHOOK_DELIVERIES.ID, d.id().value())
                .set(WEBHOOK_DELIVERIES.TENANT_ID, d.tenantId().value())
                .set(WEBHOOK_DELIVERIES.ENDPOINT_ID, d.endpointId().value())
                .set(WEBHOOK_DELIVERIES.EVENT_ID, d.eventId())
                .set(WEBHOOK_DELIVERIES.EVENT_TYPE, d.eventType())
                .set(WEBHOOK_DELIVERIES.BODY, d.body())
                .set(WEBHOOK_DELIVERIES.STATUS, d.status().name())
                .set(WEBHOOK_DELIVERIES.ATTEMPTS, d.attempts())
                .set(WEBHOOK_DELIVERIES.NEXT_ATTEMPT_AT, d.nextAttemptAt())
                .set(WEBHOOK_DELIVERIES.RESEND_OF, d.resendOf() == null ? null : d.resendOf().value())
                .set(WEBHOOK_DELIVERIES.CREATED_AT, d.createdAt());
    }

    @Override
    public Optional<WebhookDelivery> findById(WebhookDeliveryId id) {
        return dsl.selectFrom(WEBHOOK_DELIVERIES)
                .where(WEBHOOK_DELIVERIES.ID.eq(id.value()))
                .fetchOptional(JooqWebhookDeliveryRepository::toDomain);
    }

    @Override
    public int killExhausted(Instant now, int maxAttempts) {
        return dsl.update(WEBHOOK_DELIVERIES)
                .set(WEBHOOK_DELIVERIES.STATUS, "DEAD")
                .set(WEBHOOK_DELIVERIES.COMPLETED_AT, now)
                .set(WEBHOOK_DELIVERIES.LAST_ERROR,
                        DSL.coalesce(WEBHOOK_DELIVERIES.LAST_ERROR, DSL.val("the worker was lost during the final attempt")))
                .where(WEBHOOK_DELIVERIES.STATUS.eq("IN_PROGRESS"))
                .and(WEBHOOK_DELIVERIES.NEXT_ATTEMPT_AT.le(now))
                .and(WEBHOOK_DELIVERIES.ATTEMPTS.ge(maxAttempts))
                .execute();
    }

    @Override
    public List<ClaimedDelivery> claimDue(Instant now, int limit, Duration lease) {
        // skip locked: a row another worker holds is invisible here, so two workers never take the same one.
        List<UUID> claimed = dsl.fetch("""
                        with due as (
                            select d.id from webhook_deliveries d
                            join webhook_endpoints e on e.tenant_id = d.tenant_id and e.id = d.endpoint_id
                            where d.status in ('PENDING', 'IN_PROGRESS') and d.next_attempt_at <= ? and e.status = 'ACTIVE'
                            order by d.next_attempt_at, d.id
                            limit ?
                            for update of d skip locked
                        )
                        update webhook_deliveries d
                        set status = 'IN_PROGRESS', attempts = d.attempts + 1, next_attempt_at = ?
                        from due where d.id = due.id
                        returning d.id
                        """, Timestamp.from(now), limit, Timestamp.from(now.plus(lease)))
                .getValues(WEBHOOK_DELIVERIES.ID);
        if (claimed.isEmpty()) {
            return List.of();
        }
        return dsl.select(WEBHOOK_DELIVERIES.asterisk(), WEBHOOK_ENDPOINTS.URL, WEBHOOK_ENDPOINTS.KEY_ID, WEBHOOK_ENDPOINTS.SECRET_CIPHERTEXT)
                .from(WEBHOOK_DELIVERIES)
                .join(WEBHOOK_ENDPOINTS)
                .on(WEBHOOK_ENDPOINTS.TENANT_ID.eq(WEBHOOK_DELIVERIES.TENANT_ID).and(WEBHOOK_ENDPOINTS.ID.eq(WEBHOOK_DELIVERIES.ENDPOINT_ID)))
                .where(WEBHOOK_DELIVERIES.ID.in(claimed))
                .orderBy(WEBHOOK_DELIVERIES.ID)
                .fetch(row -> new ClaimedDelivery(
                        toDomain(row.into(WEBHOOK_DELIVERIES)),
                        row.get(WEBHOOK_ENDPOINTS.URL),
                        row.get(WEBHOOK_ENDPOINTS.KEY_ID),
                        row.get(WEBHOOK_ENDPOINTS.SECRET_CIPHERTEXT)));
    }

    @Override
    public void recordAttempt(
            WebhookDeliveryId id, int claimedAttempts, DeliveryAttempt attempt, DeliveryStatus status, Instant nextAttemptAt, Instant completedAt) {
        dsl.insertInto(
                        WEBHOOK_DELIVERY_ATTEMPTS,
                        WEBHOOK_DELIVERY_ATTEMPTS.ID,
                        WEBHOOK_DELIVERY_ATTEMPTS.TENANT_ID,
                        WEBHOOK_DELIVERY_ATTEMPTS.DELIVERY_ID,
                        WEBHOOK_DELIVERY_ATTEMPTS.ATTEMPT_NO,
                        WEBHOOK_DELIVERY_ATTEMPTS.STATUS_CODE,
                        WEBHOOK_DELIVERY_ATTEMPTS.ERROR,
                        WEBHOOK_DELIVERY_ATTEMPTS.RESPONSE_EXCERPT,
                        WEBHOOK_DELIVERY_ATTEMPTS.LATENCY_MS,
                        WEBHOOK_DELIVERY_ATTEMPTS.ATTEMPTED_AT)
                .select(DSL.select(
                                DSL.field("gen_random_uuid()", UUID.class),
                                WEBHOOK_DELIVERIES.TENANT_ID,
                                WEBHOOK_DELIVERIES.ID,
                                DSL.val(attempt.attemptNo(), WEBHOOK_DELIVERY_ATTEMPTS.ATTEMPT_NO),
                                DSL.val(attempt.statusCode(), WEBHOOK_DELIVERY_ATTEMPTS.STATUS_CODE),
                                DSL.val(Text.clip(attempt.error(), MAX_ERROR), WEBHOOK_DELIVERY_ATTEMPTS.ERROR),
                                DSL.val(Text.clip(attempt.responseExcerpt(), MAX_EXCERPT), WEBHOOK_DELIVERY_ATTEMPTS.RESPONSE_EXCERPT),
                                DSL.val((int) Math.min(attempt.latencyMs(), Integer.MAX_VALUE), WEBHOOK_DELIVERY_ATTEMPTS.LATENCY_MS),
                                DSL.val(attempt.attemptedAt(), WEBHOOK_DELIVERY_ATTEMPTS.ATTEMPTED_AT))
                        .from(WEBHOOK_DELIVERIES)
                        .where(WEBHOOK_DELIVERIES.ID.eq(id.value())))
                .onConflict(WEBHOOK_DELIVERY_ATTEMPTS.DELIVERY_ID, WEBHOOK_DELIVERY_ATTEMPTS.ATTEMPT_NO)
                .doNothing()
                .execute();
        dsl.update(WEBHOOK_DELIVERIES)
                .set(WEBHOOK_DELIVERIES.STATUS, status.name())
                .set(WEBHOOK_DELIVERIES.NEXT_ATTEMPT_AT, nextAttemptAt)
                .set(WEBHOOK_DELIVERIES.LAST_STATUS_CODE, attempt.statusCode())
                .set(WEBHOOK_DELIVERIES.LAST_ERROR, Text.clip(attempt.error(), MAX_ERROR))
                .set(WEBHOOK_DELIVERIES.COMPLETED_AT, completedAt)
                .where(WEBHOOK_DELIVERIES.ID.eq(id.value()))
                .and(WEBHOOK_DELIVERIES.ATTEMPTS.eq(claimedAttempts))
                .and(WEBHOOK_DELIVERIES.STATUS.eq("IN_PROGRESS"))
                .execute();
    }

    private static WebhookDelivery toDomain(WebhookDeliveriesRecord row) {
        return new WebhookDelivery(
                new WebhookDeliveryId(row.getId()),
                new TenantId(row.getTenantId()),
                new WebhookEndpointId(row.getEndpointId()),
                row.getEventId(),
                row.getEventType(),
                row.getBody(),
                DeliveryStatus.valueOf(row.getStatus()),
                row.getAttempts(),
                row.getNextAttemptAt(),
                row.getResendOf() == null ? null : new WebhookDeliveryId(row.getResendOf()),
                row.getLastStatusCode(),
                row.getLastError(),
                row.getCreatedAt(),
                row.getCompletedAt());
    }
}
