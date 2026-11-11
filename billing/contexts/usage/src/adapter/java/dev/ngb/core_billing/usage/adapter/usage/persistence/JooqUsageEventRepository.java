package dev.ngb.core_billing.usage.adapter.usage.persistence;

import static dev.ngb.core_billing_jooq.Tables.USAGE_EVENTS;

import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing.usage.api.lookup.UsageEventId;
import dev.ngb.core_billing.usage.application.usage.port.UsageEventRepository;
import dev.ngb.core_billing.usage.domain.usage.UsageEvent;
import dev.ngb.core_billing.usage.domain.usage.UsageTrace;
import dev.ngb.core_billing_jooq.tables.records.UsageEventsRecord;
import java.util.Optional;
import org.jooq.DSLContext;

/** Usage events are append-only; the only read here is the idempotency probe a write needs. */
public class JooqUsageEventRepository implements UsageEventRepository {

    private final DSLContext dsl;

    public JooqUsageEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean addIfAbsent(UsageEvent e) {
        // A concurrent insert of the same key makes this statement wait for that transaction, then do nothing.
        return dsl.insertInto(USAGE_EVENTS)
                .set(USAGE_EVENTS.ID, e.id().value())
                .set(USAGE_EVENTS.TENANT_ID, e.tenantId().value())
                .set(USAGE_EVENTS.CUSTOMER_ID, e.customerId().value())
                .set(USAGE_EVENTS.METRIC, e.metric())
                .set(USAGE_EVENTS.QUANTITY, e.quantity())
                .set(USAGE_EVENTS.UNIT, e.unit())
                .set(USAGE_EVENTS.OCCURRED_AT, e.occurredAt())
                .set(USAGE_EVENTS.RECORDED_AT, e.recordedAt())
                .set(USAGE_EVENTS.IDEMPOTENCY_KEY, e.idempotencyKey())
                .set(USAGE_EVENTS.REQUEST_HASH, e.requestHash())
                .set(USAGE_EVENTS.TENANT_SYSTEM_ID, e.trace().tenantSystemId() == null ? null : e.trace().tenantSystemId().value())
                .set(USAGE_EVENTS.RESOURCE_REF, e.trace().resourceRef())
                .set(USAGE_EVENTS.SOURCE_EVENT_ID, e.trace().sourceEventId())
                .onConflict(USAGE_EVENTS.TENANT_ID, USAGE_EVENTS.IDEMPOTENCY_KEY)
                .doNothing()
                .execute() == 1;
    }

    @Override
    public Optional<UsageEvent> findByIdempotencyKey(String idempotencyKey) {
        return dsl.selectFrom(USAGE_EVENTS)
                .where(USAGE_EVENTS.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(JooqUsageEventRepository::toDomain);
    }

    private static UsageEvent toDomain(UsageEventsRecord row) {
        return UsageEvent.restore(
                new UsageEventId(row.getId()),
                new TenantId(row.getTenantId()),
                new CustomerId(row.getCustomerId()),
                row.getMetric(),
                row.getQuantity(),
                row.getUnit(),
                row.getOccurredAt(),
                row.getRecordedAt(),
                row.getIdempotencyKey(),
                row.getRequestHash(),
                new UsageTrace(
                        row.getTenantSystemId() == null ? null : new TenantSystemId(row.getTenantSystemId()),
                        row.getResourceRef(),
                        row.getSourceEventId()));
    }
}
