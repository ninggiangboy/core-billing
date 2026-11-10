package io.corebilling.boot.infrastructure;

import io.corebilling.sharedkernel.DomainEvent;
import io.corebilling.sharedkernel.EventInbox;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The primary key (tenant, consumer, event) turns a repeated event into a no-op insert. */
public class JdbcEventInbox implements EventInbox {

    private final JdbcClient jdbc;

    public JdbcEventInbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean firstTime(String consumer, DomainEvent event) {
        return jdbc.sql("insert into event_inbox (tenant_id, consumer, event_id) values (?, ?, ?) on conflict do nothing")
                .params(event.tenantId().value(), consumer, event.eventId())
                .update() == 1;
    }
}
