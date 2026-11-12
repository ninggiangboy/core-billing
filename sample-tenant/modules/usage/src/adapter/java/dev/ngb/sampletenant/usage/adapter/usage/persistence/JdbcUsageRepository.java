package dev.ngb.sampletenant.usage.adapter.usage.persistence;

import dev.ngb.sampletenant.usage.application.usage.port.UsageRepository;
import dev.ngb.sampletenant.usage.domain.usage.UsageRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcUsageRepository implements UsageRepository {

    private final UsageJdbcRepository rows;
    private final JdbcTemplate jdbc;

    public JdbcUsageRepository(UsageJdbcRepository rows, JdbcTemplate jdbc) {
        this.rows = rows;
        this.jdbc = jdbc;
    }

    @Override
    public void save(UsageRecord record) {
        jdbc.update(
                """
                insert into usage_records
                    (id, order_id, billing_customer_id, metric, quantity, unit, billing_event_id,
                     idempotency_key, occurred_at, recorded_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (order_id, metric) do nothing
                """,
                record.id(),
                record.orderId(),
                record.billingCustomerId(),
                record.metric(),
                record.quantity(),
                record.unit(),
                record.billingEventId(),
                record.idempotencyKey(),
                java.sql.Timestamp.from(record.occurredAt()),
                java.sql.Timestamp.from(record.recordedAt()));
    }

    @Override
    public Optional<UsageRecord> findByOrderIdAndMetric(UUID orderId, String metric) {
        return rows.findByOrderIdAndMetric(orderId, metric).map(JdbcUsageRepository::toDomain);
    }

    @Override
    public List<UsageRecord> findByBillingCustomerId(String billingCustomerId) {
        return rows.findByBillingCustomerIdOrderByOccurredAtDesc(billingCustomerId).stream()
                .map(JdbcUsageRepository::toDomain)
                .toList();
    }

    private static UsageRecord toDomain(UsageRow row) {
        return new UsageRecord(
                row.id(),
                row.orderId(),
                row.billingCustomerId(),
                row.metric(),
                row.quantity(),
                row.unit(),
                row.billingEventId(),
                row.idempotencyKey(),
                row.occurredAt(),
                row.recordedAt());
    }
}
