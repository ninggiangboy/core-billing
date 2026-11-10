package dev.ngb.core_billing.pricing.adapter.persistence;

import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.pricing.application.metric.port.MetricRegistry;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcMetricRegistry implements MetricRegistry {

    private final JdbcClient jdbc;

    public JdbcMetricRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean register(String metric, String unit) {
        int inserted = jdbc.sql("insert into pricing_metrics (tenant_id, code, unit, created_at) values (app_tenant_id(), ?, ?, now()) on conflict do nothing")
                .params(metric, unit)
                .update();
        if (inserted == 1) {
            return true;
        }
        String existing = currentUnit(metric).orElseThrow();
        if (!existing.equals(unit)) {
            throw new ConflictException("metric '" + metric + "' is already measured in '" + existing + "', not '" + unit + "'");
        }
        return false;
    }

    private Optional<String> currentUnit(String metric) {
        return jdbc.sql("select unit from pricing_metrics where code = ?").param(metric).query(String.class).optional();
    }
}
