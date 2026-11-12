package dev.ngb.billing.pricing.adapter.metric.persistence;

import static dev.ngb.billing_jooq.Tables.PRICING_METRICS;

import dev.ngb.billing.platform.api.error.ConflictException;
import dev.ngb.billing.pricing.application.metric.port.MetricRegistry;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public class JooqMetricRegistry implements MetricRegistry {

    private final DSLContext dsl;

    public JooqMetricRegistry(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean register(String metric, String unit) {
        int inserted = dsl.insertInto(PRICING_METRICS)
                .set(PRICING_METRICS.TENANT_ID, DSL.field("app_tenant_id()", java.util.UUID.class))
                .set(PRICING_METRICS.CODE, metric)
                .set(PRICING_METRICS.UNIT, unit)
                .set(PRICING_METRICS.CREATED_AT, DSL.field("now()", java.time.Instant.class))
                .onDuplicateKeyIgnore()
                .execute();
        if (inserted == 1) {
            return true;
        }
        String existing = currentUnit(metric).orElseThrow();
        if (!existing.equals(unit)) {
            throw new ConflictException("metric '%s' is already measured in '%s', not '%s'".formatted(metric, existing, unit));
        }
        return false;
    }

    private Optional<String> currentUnit(String metric) {
        return dsl.select(PRICING_METRICS.UNIT)
                .from(PRICING_METRICS)
                .where(PRICING_METRICS.CODE.eq(metric))
                .fetchOptional(PRICING_METRICS.UNIT);
    }
}
