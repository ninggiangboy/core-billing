package dev.ngb.core_billing.boot.infrastructure;

import static dev.ngb.core_billing_jooq.Tables.OUTBOX;

import dev.ngb.core_billing.sharedkernel.DomainEvent;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/** Writes an event to the outbox table inside the caller's transaction; the relay delivers it later. */
public class JooqOutboxAppender implements EventPublisher {

    private final DSLContext dsl;
    private final JsonMapper json;

    public JooqOutboxAppender(DSLContext dsl, JsonMapper json) {
        this.dsl = dsl;
        this.json = json;
    }

    @Override
    public void publish(DomainEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("an event must be published inside a unit of work");
        }
        dsl.insertInto(OUTBOX)
                .set(OUTBOX.ID, event.eventId())
                .set(OUTBOX.TENANT_ID, event.tenantId().value())
                .set(OUTBOX.AGGREGATE, event.aggregate())
                .set(OUTBOX.TYPE, event.type())
                .set(OUTBOX.PAYLOAD, JSONB.valueOf(json.writeValueAsString(event.payload())))
                .set(OUTBOX.CREATED_AT, event.occurredAt())
                .set(OUTBOX.NEXT_ATTEMPT_AT, event.occurredAt())
                .execute();
    }
}
