package dev.ngb.core_billing.boot.infrastructure;

import static dev.ngb.core_billing_jooq.Tables.EVENT_INBOX;

import dev.ngb.core_billing.sharedkernel.DomainEvent;
import dev.ngb.core_billing.sharedkernel.EventInbox;
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
