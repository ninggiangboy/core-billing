package dev.ngb.billing.boot.infrastructure.messaging;

import static dev.ngb.billing_jooq.Tables.EVENT_INBOX;

import dev.ngb.core.event.DomainEvent;
import dev.ngb.core.event.EventInbox;
import org.jooq.DSLContext;

/** The primary key (tenant, consumer, event) turns a repeated event into a no-op insert. */
public class JooqEventInbox implements EventInbox {

    private final DSLContext dsl;

    public JooqEventInbox(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean firstTime(String consumer, DomainEvent event) {
        return dsl.insertInto(EVENT_INBOX)
                .set(EVENT_INBOX.TENANT_ID, event.tenantId().value())
                .set(EVENT_INBOX.CONSUMER, consumer)
                .set(EVENT_INBOX.EVENT_ID, event.eventId())
                .onConflict(EVENT_INBOX.TENANT_ID, EVENT_INBOX.CONSUMER, EVENT_INBOX.EVENT_ID)
                .doNothing()
                .execute() == 1;
    }
}
