package dev.ngb.billing.usage.adapter.usage.persistence;

import static dev.ngb.billing_jooq.Tables.USAGE_EVENTS;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.sum;

import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.usage.api.lookup.UsageEventId;
import dev.ngb.billing.usage.application.usage.query.UsageEventReadRepository;
import dev.ngb.billing.usage.application.usage.query.UsageEventView;
import dev.ngb.billing.usage.application.usage.query.UsageRecordView;
import dev.ngb.billing.usage.application.usage.query.UsageTotalView;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.DSLContext;

/** Selects usage straight into the read models; it never builds the aggregate. */
public class JooqUsageEventReadRepository implements UsageEventReadRepository {

    private final DSLContext dsl;

    public JooqUsageEventReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<UsageEventView> page(CustomerId customerId, String metric, Instant from, Instant to, UsageEventId before, int limit) {
        Condition filter = USAGE_EVENTS.CUSTOMER_ID.eq(customerId.value())
                .and(metric == null ? noCondition() : USAGE_EVENTS.METRIC.eq(metric))
                .and(from == null ? noCondition() : USAGE_EVENTS.OCCURRED_AT.ge(from))
                .and(to == null ? noCondition() : USAGE_EVENTS.OCCURRED_AT.lt(to))
                .and(before == null ? noCondition() : USAGE_EVENTS.ID.lt(before.value()));
        return dsl.selectFrom(USAGE_EVENTS)
                .where(filter)
                .orderBy(USAGE_EVENTS.ID.desc())
                .limit(limit)
                .fetch(row -> new UsageEventView(
                        row.getId(),
                        row.getCustomerId(),
                        row.getMetric(),
                        row.getQuantity(),
                        row.getUnit(),
                        row.getOccurredAt(),
                        row.getRecordedAt(),
                        row.getIdempotencyKey(),
                        row.getTenantSystemId(),
                        row.getResourceRef(),
                        row.getSourceEventId()));
    }

    @Override
    public List<UsageRecordView> find(CustomerId customerId, Set<String> metrics, Instant from, Instant to, int limit) {
        Condition filter = USAGE_EVENTS.CUSTOMER_ID.eq(customerId.value())
                .and(USAGE_EVENTS.OCCURRED_AT.ge(from))
                .and(USAGE_EVENTS.OCCURRED_AT.lt(to))
                .and(metrics.isEmpty() ? noCondition() : USAGE_EVENTS.METRIC.in(metrics));
        return dsl.select(USAGE_EVENTS.ID, USAGE_EVENTS.CUSTOMER_ID, USAGE_EVENTS.METRIC, USAGE_EVENTS.UNIT, USAGE_EVENTS.QUANTITY,
                        USAGE_EVENTS.OCCURRED_AT)
                .from(USAGE_EVENTS)
                .where(filter)
                .orderBy(USAGE_EVENTS.OCCURRED_AT, USAGE_EVENTS.ID)
                .limit(limit)
                .fetch(row -> new UsageRecordView(row.value1(), row.value2(), row.value3(), row.value4(), row.value5(), row.value6()));
    }

    @Override
    public List<UsageTotalView> totals(CustomerId customerId, Set<String> metrics, Instant from, Instant to) {
        Condition filter = USAGE_EVENTS.CUSTOMER_ID.eq(customerId.value())
                .and(USAGE_EVENTS.OCCURRED_AT.ge(from))
                .and(USAGE_EVENTS.OCCURRED_AT.lt(to))
                .and(metrics.isEmpty() ? noCondition() : USAGE_EVENTS.METRIC.in(metrics));
        return dsl.select(USAGE_EVENTS.METRIC, USAGE_EVENTS.UNIT, sum(USAGE_EVENTS.QUANTITY), count())
                .from(USAGE_EVENTS)
                .where(filter)
                .groupBy(USAGE_EVENTS.METRIC, USAGE_EVENTS.UNIT)
                .orderBy(USAGE_EVENTS.METRIC)
                .fetch(row -> new UsageTotalView(row.value1(), row.value2(), row.value3(), row.value4().longValue()));
    }
}
