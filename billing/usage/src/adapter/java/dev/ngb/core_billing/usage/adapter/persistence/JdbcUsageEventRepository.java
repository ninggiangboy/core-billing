package dev.ngb.core_billing.usage.adapter.persistence;

import dev.ngb.core_billing.usage.application.port.UsageEventRepository;
import dev.ngb.core_billing.usage.domain.usage.UsageEvent;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Usage events are append-only; the only read here is the idempotency probe a write needs. */
public class JdbcUsageEventRepository implements UsageEventRepository {

    private static final String COLUMNS = "id, tenant_id, customer_id, metric, quantity, unit, occurred_at, recorded_at, idempotency_key, request_hash, "
            + "tenant_system_id, resource_ref, source_event_id";

    private final JdbcClient jdbc;

    public JdbcUsageEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean addIfAbsent(UsageEvent e) {
        // A concurrent insert of the same key makes this statement wait for that transaction, then do nothing.
        UsageEventRow row = UsageEventRow.from(e);
        return jdbc.sql("insert into usage_events (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict (tenant_id, idempotency_key) do nothing")
                .params(row.id(), row.tenantId(), row.customerId(), row.metric(), row.quantity(), row.unit(), Timestamp.from(row.occurredAt()),
                        Timestamp.from(row.recordedAt()), row.idempotencyKey(), row.requestHash(), row.tenantSystemId(), row.resourceRef(),
                        row.sourceEventId())
                .update() == 1;
    }

    @Override
    public Optional<UsageEvent> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql("select " + COLUMNS + " from usage_events where idempotency_key = ?")
                .param(idempotencyKey)
                .query(UsageEventRow.class)
                .optional()
                .map(UsageEventRow::toDomain);
    }
}
