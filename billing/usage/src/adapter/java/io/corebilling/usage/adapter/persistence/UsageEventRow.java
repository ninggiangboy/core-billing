package io.corebilling.usage.adapter.persistence;

import io.corebilling.platform.api.tenant.TenantSystemId;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.usage.api.usage.UsageEventId;
import io.corebilling.usage.domain.usage.UsageEvent;
import io.corebilling.usage.domain.usage.UsageTrace;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("usage_events")
record UsageEventRow(
        @Id UUID id,
        UUID tenantId,
        UUID customerId,
        String metric,
        BigDecimal quantity,
        String unit,
        Instant occurredAt,
        Instant recordedAt,
        String idempotencyKey,
        String requestHash,
        UUID tenantSystemId,
        String resourceRef,
        String sourceEventId) {

    static UsageEventRow from(UsageEvent event) {
        return new UsageEventRow(
                event.id().value(),
                event.tenantId().value(),
                event.customerId().value(),
                event.metric(),
                event.quantity(),
                event.unit(),
                event.occurredAt(),
                event.recordedAt(),
                event.idempotencyKey(),
                event.requestHash(),
                event.trace().tenantSystemId() == null ? null : event.trace().tenantSystemId().value(),
                event.trace().resourceRef(),
                event.trace().sourceEventId());
    }

    UsageEvent toDomain() {
        return UsageEvent.restore(
                new UsageEventId(id),
                new TenantId(tenantId),
                new CustomerId(customerId),
                metric,
                quantity,
                unit,
                occurredAt,
                recordedAt,
                idempotencyKey,
                requestHash,
                new UsageTrace(tenantSystemId == null ? null : new TenantSystemId(tenantSystemId), resourceRef, sourceEventId));
    }
}
