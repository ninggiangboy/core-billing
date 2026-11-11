package dev.ngb.sampletenant.usage.adapter.persistence;

import dev.ngb.sampletenant.usage.application.port.UsageRepository;
import dev.ngb.sampletenant.usage.domain.UsageRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcUsageRepository implements UsageRepository {

    private final UsageJdbcRepository rows;

    public JdbcUsageRepository(UsageJdbcRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(UsageRecord record) {
        rows.save(new UsageRow(
                record.id(),
                record.orderId(),
                record.billingCustomerId(),
                record.metric(),
                record.quantity(),
                record.unit(),
                record.billingEventId(),
                record.idempotencyKey(),
                record.occurredAt(),
                record.recordedAt()));
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
