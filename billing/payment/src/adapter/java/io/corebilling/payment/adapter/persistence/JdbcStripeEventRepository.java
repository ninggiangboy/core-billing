package io.corebilling.payment.adapter.persistence;

import io.corebilling.payment.application.StripeEventRepository;
import io.corebilling.payment.domain.EventAttempt;
import io.corebilling.payment.domain.EventStatus;
import io.corebilling.payment.domain.StoredEventId;
import io.corebilling.payment.domain.StripeConfigurationId;
import io.corebilling.payment.domain.StripeEvent;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcStripeEventRepository implements StripeEventRepository {

    private static final String COLUMNS = "id, tenant_id, configuration_id, stripe_account, event_id, type, livemode, payload, status, attempts, "
            + "next_attempt_at, last_error, received_at, processed_at";

    private static final RowMapper<StripeEvent> MAPPER = (rs, n) -> StripeEvent.restore(
            new StoredEventId(Rows.uuid(rs, "id")),
            new TenantId(Rows.uuid(rs, "tenant_id")),
            new StripeConfigurationId(Rows.uuid(rs, "configuration_id")),
            rs.getString("stripe_account"),
            rs.getString("event_id"),
            rs.getString("type"),
            rs.getBoolean("livemode"),
            rs.getString("payload"),
            EventStatus.valueOf(rs.getString("status")),
            rs.getInt("attempts"),
            Rows.instant(rs, "next_attempt_at"),
            rs.getString("last_error"),
            Rows.instant(rs, "received_at"),
            Rows.instant(rs, "processed_at"));

    private final JdbcClient jdbc;

    public JdbcStripeEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(StripeEvent e) {
        return jdbc.sql("insert into stripe_events (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict (stripe_account, event_id) do nothing")
                .params(e.id().value(), e.tenantId().value(), e.configurationId().value(), e.stripeAccount(), e.eventId(), e.type(), e.livemode(),
                        e.payload(), e.status().name(), e.attempts(), Rows.timestamp(e.nextAttemptAt()), e.lastError(), Rows.timestamp(e.receivedAt()),
                        Rows.timestamp(e.processedAt()))
                .update() == 1;
    }

    @Override
    public void update(StripeEvent e) {
        int updated = jdbc.sql("update stripe_events set status = ?, attempts = ?, next_attempt_at = ?, last_error = ?, processed_at = ? where id = ?")
                .params(e.status().name(), e.attempts(), Rows.timestamp(e.nextAttemptAt()), e.lastError(), Rows.timestamp(e.processedAt()), e.id().value())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("stripe event " + e.id() + " was not updated");
        }
    }

    @Override
    public Optional<StripeEvent> findById(StoredEventId id) {
        return jdbc.sql("select " + COLUMNS + " from stripe_events where id = ?").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public Optional<StripeEvent> lockById(StoredEventId id) {
        return jdbc.sql("select " + COLUMNS + " from stripe_events where id = ? for update").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public List<StoredEventId> due(Instant now, Instant receivedBefore, int limit) {
        return jdbc.sql("""
                        select id from stripe_events
                        where (status = 'FAILED' and next_attempt_at <= ?) or (status = 'RECEIVED' and received_at <= ?)
                        order by coalesce(next_attempt_at, received_at), id
                        limit ?
                        """)
                .params(Rows.timestamp(now), Rows.timestamp(receivedBefore), limit)
                .query((rs, n) -> new StoredEventId(Rows.uuid(rs, "id")))
                .list();
    }

    @Override
    public List<StripeEvent> list(EventStatus status, StoredEventId before, int limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("select " + COLUMNS + " from stripe_events where true");
        if (status != null) {
            sql.append(" and status = ?");
            params.add(status.name());
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
    public void addAttempt(StripeEvent event, int number, String outcome, String detail, Instant at) {
        jdbc.sql("insert into stripe_event_attempts (id, tenant_id, event_row_id, attempt_no, outcome, detail, attempted_at) values (?, ?, ?, ?, ?, ?, ?)")
                .params(UUID.randomUUID(), event.tenantId().value(), event.id().value(), number, outcome, detail == null ? null : clip(detail),
                        Rows.timestamp(at))
                .update();
    }

    @Override
    public List<EventAttempt> attempts(StoredEventId id) {
        return jdbc.sql("select attempt_no, outcome, detail, attempted_at from stripe_event_attempts where event_row_id = ? order by attempt_no")
                .param(id.value())
                .query((rs, n) -> new EventAttempt(rs.getInt("attempt_no"), rs.getString("outcome"), rs.getString("detail"), Rows.instant(rs, "attempted_at")))
                .list();
    }

    private static String clip(String text) {
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
