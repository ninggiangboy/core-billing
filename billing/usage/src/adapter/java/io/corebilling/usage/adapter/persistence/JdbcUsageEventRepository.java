package io.corebilling.usage.adapter.persistence;

import io.corebilling.platform.api.TenantSystemId;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.usage.api.UsageEventId;
import io.corebilling.usage.api.UsageRecord;
import io.corebilling.usage.api.UsageTotal;
import io.corebilling.usage.application.UsageEventRepository;
import io.corebilling.usage.domain.UsageEvent;
import io.corebilling.usage.domain.UsageTrace;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcUsageEventRepository implements UsageEventRepository {

    private static final String COLUMNS = "id, tenant_id, customer_id, metric, quantity, unit, occurred_at, recorded_at, idempotency_key, request_hash, "
            + "tenant_system_id, resource_ref, source_event_id";

    private static final RowMapper<UsageEvent> MAPPER = (rs, n) -> {
        UUID system = rs.getObject("tenant_system_id", UUID.class);
        return UsageEvent.restore(
                new UsageEventId(rs.getObject("id", UUID.class)),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                new CustomerId(rs.getObject("customer_id", UUID.class)),
                rs.getString("metric"),
                UsageEvent.normalize(rs.getBigDecimal("quantity")),
                rs.getString("unit"),
                instant(rs, "occurred_at"),
                instant(rs, "recorded_at"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                new UsageTrace(system == null ? null : new TenantSystemId(system), rs.getString("resource_ref"), rs.getString("source_event_id")));
    };

    private final JdbcClient jdbc;

    public JdbcUsageEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    @Override
    public boolean addIfAbsent(UsageEvent e) {
        // A concurrent insert of the same key makes this statement wait for that transaction, then do nothing.
        return jdbc.sql("insert into usage_events (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict (tenant_id, idempotency_key) do nothing")
                .params(e.id().value(), e.tenantId().value(), e.customerId().value(), e.metric(), e.quantity(), e.unit(), Timestamp.from(e.occurredAt()),
                        Timestamp.from(e.recordedAt()), e.idempotencyKey(), e.requestHash(),
                        e.trace().tenantSystemId() == null ? null : e.trace().tenantSystemId().value(), e.trace().resourceRef(), e.trace().sourceEventId())
                .update() == 1;
    }

    @Override
    public Optional<UsageEvent> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql("select " + COLUMNS + " from usage_events where idempotency_key = ?").param(idempotencyKey).query(MAPPER).optional();
    }

    @Override
    public List<UsageEvent> page(CustomerId customerId, String metric, Instant from, Instant to, UsageEventId before, int limit) {
        StringBuilder sql = new StringBuilder("select " + COLUMNS + " from usage_events where customer_id = ?");
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
        return jdbc.sql(sql.toString()).params(params).query(MAPPER).list();
    }

    @Override
    public List<UsageRecord> find(CustomerId customerId, Set<String> metrics, Instant from, Instant to, int limit) {
        StringBuilder sql = new StringBuilder("select id, customer_id, metric, unit, quantity, occurred_at from usage_events "
                + "where customer_id = ? and occurred_at >= ? and occurred_at < ?");
        List<Object> params = new ArrayList<>(List.of(customerId.value(), Timestamp.from(from), Timestamp.from(to)));
        if (!metrics.isEmpty()) {
            sql.append(" and metric = any(?)");
            params.add(metrics.toArray(String[]::new));
        }
        sql.append(" order by occurred_at, id limit ?");
        params.add(limit);
        return jdbc.sql(sql.toString()).params(params)
                .query((rs, n) -> new UsageRecord(new UsageEventId(rs.getObject("id", UUID.class)), new CustomerId(rs.getObject("customer_id", UUID.class)),
                        rs.getString("metric"), rs.getString("unit"), rs.getBigDecimal("quantity").stripTrailingZeros(), instant(rs, "occurred_at")))
                .list();
    }

    @Override
    public List<UsageTotal> totals(CustomerId customerId, Set<String> metrics, Instant from, Instant to) {
        StringBuilder sql = new StringBuilder("select metric, unit, sum(quantity) as quantity, count(*) as events from usage_events "
                + "where customer_id = ? and occurred_at >= ? and occurred_at < ?");
        List<Object> params = new ArrayList<>(List.of(customerId.value(), Timestamp.from(from), Timestamp.from(to)));
        if (!metrics.isEmpty()) {
            sql.append(" and metric = any(?)");
            params.add(metrics.toArray(String[]::new));
        }
        sql.append(" group by metric, unit order by metric");
        return jdbc.sql(sql.toString()).params(params)
                .query((rs, n) -> new UsageTotal(rs.getString("metric"), rs.getString("unit"), rs.getBigDecimal("quantity").stripTrailingZeros(), rs.getLong("events")))
                .list();
    }
}
