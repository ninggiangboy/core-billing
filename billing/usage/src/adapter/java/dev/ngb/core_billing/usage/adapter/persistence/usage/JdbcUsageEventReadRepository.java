package dev.ngb.core_billing.usage.adapter.persistence.usage;

import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.usage.api.usage.UsageEventId;
import dev.ngb.core_billing.usage.application.query.UsageEventReadRepository;
import dev.ngb.core_billing.usage.application.query.UsageEventView;
import dev.ngb.core_billing.usage.application.query.UsageRecordView;
import dev.ngb.core_billing.usage.application.query.UsageTotalView;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects usage straight into the read models; it never builds the aggregate. */
public class JdbcUsageEventReadRepository implements UsageEventReadRepository {

    private static final String HISTORY_COLUMNS = "id, customer_id, metric, quantity, unit, occurred_at, recorded_at, idempotency_key, "
            + "tenant_system_id, resource_ref, source_event_id";

    private final JdbcClient jdbc;

    public JdbcUsageEventReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UsageEventView> page(CustomerId customerId, String metric, Instant from, Instant to, UsageEventId before, int limit) {
        StringBuilder sql = new StringBuilder("""
                        select %s
                        from usage_events
                        where customer_id = ?
                        """.formatted(HISTORY_COLUMNS));
        List<Object> params = new ArrayList<>();
        params.add(customerId.value());
        if (metric != null) {
            sql.append(" and metric = ?");
            params.add(metric);
        }
        if (from != null) {
            sql.append(" and occurred_at >= ?");
            params.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" and occurred_at < ?");
            params.add(Timestamp.from(to));
        }
        if (before != null) {
            sql.append(" and id < ?");
            params.add(before.value());
        }
        sql.append(" order by id desc limit ?");
        params.add(limit);
        return jdbc.sql(sql.toString()).params(params).query(UsageEventView.class).list();
    }

    @Override
    public List<UsageRecordView> find(CustomerId customerId, Set<String> metrics, Instant from, Instant to, int limit) {
        StringBuilder sql = new StringBuilder("""
                        select id, customer_id, metric, unit, quantity, occurred_at
                        from usage_events
                        where customer_id = ? and occurred_at >= ? and occurred_at < ?
                        """);
        List<Object> params = new ArrayList<>(List.of(customerId.value(), Timestamp.from(from), Timestamp.from(to)));
        if (!metrics.isEmpty()) {
            sql.append(" and metric = any(?)");
            params.add(metrics.toArray(String[]::new));
        }
        sql.append(" order by occurred_at, id limit ?");
        params.add(limit);
        return jdbc.sql(sql.toString()).params(params).query(UsageRecordView.class).list();
    }

    @Override
    public List<UsageTotalView> totals(CustomerId customerId, Set<String> metrics, Instant from, Instant to) {
        StringBuilder sql = new StringBuilder("""
                        select metric, unit, sum(quantity) as quantity, count(*) as events
                        from usage_events
                        where customer_id = ? and occurred_at >= ? and occurred_at < ?
                        """);
        List<Object> params = new ArrayList<>(List.of(customerId.value(), Timestamp.from(from), Timestamp.from(to)));
        if (!metrics.isEmpty()) {
            sql.append(" and metric = any(?)");
            params.add(metrics.toArray(String[]::new));
        }
        sql.append(" group by metric, unit order by metric");
        return jdbc.sql(sql.toString()).params(params).query(UsageTotalView.class).list();
    }
}
