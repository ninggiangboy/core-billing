package dev.ngb.core_billing.pricing.adapter.persistence.metric;

import dev.ngb.core_billing.pricing.application.metric.query.MetricReadRepository;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Reads a metric's unit directly. */
public class JdbcMetricReadRepository implements MetricReadRepository {

    private final JdbcClient jdbc;

    public JdbcMetricReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> unitOf(String metric) {
        return jdbc.sql("select unit from pricing_metrics where code = ?").param(metric).query(String.class).optional();
    }
}
