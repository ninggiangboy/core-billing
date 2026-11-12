package dev.ngb.tenant.usage.adapter.usage.persistence;

import static dev.ngb.tenant_jooq.Tables.USAGE_RECORDS;

import dev.ngb.tenant.usage.application.usage.port.UsageRepository;
import dev.ngb.tenant.usage.domain.usage.UsageRecord;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/** The write side: an idempotent insert and the lookup a retry decides with. */
public class JooqUsageRepository implements UsageRepository {

    private final DSLContext dsl;

    public JooqUsageRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(UsageRecord record) {
        dsl.insertInto(USAGE_RECORDS)
                .columns(
                        USAGE_RECORDS.ID,
                        USAGE_RECORDS.ORDER_ID,
                        USAGE_RECORDS.BILLING_CUSTOMER_ID,
                        USAGE_RECORDS.METRIC,
                        USAGE_RECORDS.QUANTITY,
                        USAGE_RECORDS.UNIT,
                        USAGE_RECORDS.BILLING_EVENT_ID,
                        USAGE_RECORDS.IDEMPOTENCY_KEY,
                        USAGE_RECORDS.OCCURRED_AT,
                        USAGE_RECORDS.RECORDED_AT)
                .values(
                        record.id(),
                        record.orderId(),
                        record.billingCustomerId(),
                        record.metric(),
                        record.quantity(),
                        record.unit(),
                        record.billingEventId(),
                        record.idempotencyKey(),
                        record.occurredAt(),
                        record.recordedAt())
                .onConflict(USAGE_RECORDS.ORDER_ID, USAGE_RECORDS.METRIC)
                .doNothing()
                .execute();
    }

    @Override
    public Optional<UsageRecord> findByOrderIdAndMetric(UUID orderId, String metric) {
        return dsl.selectFrom(USAGE_RECORDS)
                .where(USAGE_RECORDS.ORDER_ID.eq(orderId).and(USAGE_RECORDS.METRIC.eq(metric)))
                .fetchOptional()
                .map(row -> new UsageRecord(
                        row.getId(),
                        row.getOrderId(),
                        row.getBillingCustomerId(),
                        row.getMetric(),
                        row.getQuantity(),
                        row.getUnit(),
                        row.getBillingEventId(),
                        row.getIdempotencyKey(),
                        row.getOccurredAt(),
                        row.getRecordedAt()));
    }
}
