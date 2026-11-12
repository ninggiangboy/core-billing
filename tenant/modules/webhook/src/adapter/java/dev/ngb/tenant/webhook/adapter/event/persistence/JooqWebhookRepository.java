package dev.ngb.tenant.webhook.adapter.event.persistence;

import static dev.ngb.tenant_jooq.Tables.WEBHOOK_EVENTS;

import dev.ngb.tenant.webhook.application.event.port.WebhookRepository;
import dev.ngb.tenant.webhook.domain.event.WebhookEvent;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/** Dedupes on Billing's event id with a unique index, so a retry inserts nothing. */
public class JooqWebhookRepository implements WebhookRepository {

    private final DSLContext dsl;

    public JooqWebhookRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean saveIfAbsent(WebhookEvent event) {
        int inserted = dsl.insertInto(WEBHOOK_EVENTS)
                .columns(
                        WEBHOOK_EVENTS.ID,
                        WEBHOOK_EVENTS.BILLING_EVENT_ID,
                        WEBHOOK_EVENTS.TYPE,
                        WEBHOOK_EVENTS.TENANT_ID,
                        WEBHOOK_EVENTS.OCCURRED_AT,
                        WEBHOOK_EVENTS.PAYLOAD,
                        WEBHOOK_EVENTS.RECEIVED_AT,
                        WEBHOOK_EVENTS.STATUS,
                        WEBHOOK_EVENTS.DECISION,
                        WEBHOOK_EVENTS.DETAIL)
                .values(
                        event.id(),
                        event.billingEventId(),
                        event.type(),
                        event.tenantId(),
                        event.occurredAt(),
                        event.payload(),
                        event.receivedAt(),
                        event.status(),
                        event.decision(),
                        event.detail())
                .onConflict(WEBHOOK_EVENTS.BILLING_EVENT_ID)
                .doNothing()
                .execute();
        return inserted > 0;
    }

    @Override
    public Optional<WebhookEvent> findByBillingEventId(String billingEventId) {
        return dsl.selectFrom(WEBHOOK_EVENTS)
                .where(WEBHOOK_EVENTS.BILLING_EVENT_ID.eq(billingEventId))
                .fetchOptional()
                .map(row -> WebhookEvent.restore(
                        row.getId(),
                        row.getBillingEventId(),
                        row.getType(),
                        row.getTenantId(),
                        row.getOccurredAt(),
                        row.getPayload(),
                        row.getReceivedAt(),
                        row.getStatus(),
                        row.getDecision(),
                        row.getDetail()));
    }

    @Override
    public void save(WebhookEvent event) {
        dsl.update(WEBHOOK_EVENTS)
                .set(WEBHOOK_EVENTS.STATUS, event.status())
                .set(WEBHOOK_EVENTS.DECISION, event.decision())
                .set(WEBHOOK_EVENTS.DETAIL, event.detail())
                .where(WEBHOOK_EVENTS.ID.eq(event.id()))
                .execute();
    }
}
