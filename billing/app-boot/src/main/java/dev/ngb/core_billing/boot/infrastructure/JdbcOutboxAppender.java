package dev.ngb.core_billing.boot.infrastructure;

import dev.ngb.core_billing.sharedkernel.DomainEvent;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/** Writes an event to the outbox table inside the caller's transaction; the relay delivers it later. */
public class JdbcOutboxAppender implements EventPublisher {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcOutboxAppender(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void publish(DomainEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("an event must be published inside a unit of work");
        }
        Timestamp occurredAt = Timestamp.from(event.occurredAt());
        jdbc.sql("""
                        insert into outbox (id, tenant_id, aggregate, type, payload, created_at, next_attempt_at)
                        values (?, ?, ?, ?, ?::jsonb, ?, ?)
                        """)
                .param(event.eventId())
                .param(event.tenantId().value())
                .param(event.aggregate())
                .param(event.type())
                .param(json.writeValueAsString(event.payload()))
                .param(occurredAt)
                .param(occurredAt)
                .update();
    }
}
