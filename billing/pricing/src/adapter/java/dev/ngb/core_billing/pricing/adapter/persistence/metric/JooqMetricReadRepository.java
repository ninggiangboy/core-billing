package dev.ngb.core_billing.pricing.adapter.persistence.metric;

import static dev.ngb.core_billing_jooq.Tables.PRICING_METRICS;

import dev.ngb.core_billing.pricing.application.metric.query.MetricReadRepository;
import java.util.Optional;
import org.jooq.DSLContext;

/** Reads a metric's unit directly. */
public class JooqMetricReadRepository implements MetricReadRepository {

    private final DSLContext dsl;

    public JooqMetricReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<String> unitOf(String metric) {
        return dsl.select(PRICING_METRICS.UNIT)
                .from(PRICING_METRICS)
                .where(PRICING_METRICS.CODE.eq(metric))
                .fetchOptional(PRICING_METRICS.UNIT);
    }
}
