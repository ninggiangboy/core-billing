package io.corebilling.webhook.adapter.persistence;

import io.corebilling.sharedkernel.TenantId;
import io.corebilling.webhook.application.delivery.command.ClaimedDelivery;
import io.corebilling.webhook.application.delivery.port.WebhookDeliveryRepository;
import io.corebilling.webhook.domain.delivery.DeliveryAttempt;
import io.corebilling.webhook.domain.delivery.DeliveryStatus;
import io.corebilling.webhook.domain.delivery.WebhookDelivery;
import io.corebilling.webhook.domain.delivery.WebhookDeliveryId;
import io.corebilling.webhook.domain.endpoint.WebhookEndpointId;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcWebhookDeliveryRepository implements WebhookDeliveryRepository {

    private static final int MAX_ERROR = 500;
    private static final int MAX_EXCERPT = 1000;

    private static final String COLUMNS = "d.id, d.tenant_id, d.endpoint_id, d.event_id, d.event_type, d.body, d.status, d.attempts, d.next_attempt_at, "
            + "d.resend_of, d.last_status_code, d.last_error, d.created_at, d.completed_at";

    private static final RowMapper<WebhookDelivery> MAPPER = (rs, n) -> {
        UUID resendOf = Rows.uuid(rs, "resend_of");
        return new WebhookDelivery(
                new WebhookDeliveryId(Rows.uuid(rs, "id")),
                new TenantId(Rows.uuid(rs, "tenant_id")),
                new WebhookEndpointId(Rows.uuid(rs, "endpoint_id")),
                Rows.uuid(rs, "event_id"),
                rs.getString("event_type"),
                rs.getString("body"),
                DeliveryStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                Rows.instant(rs, "next_attempt_at"),
                resendOf == null ? null : new WebhookDeliveryId(resendOf),
                Rows.nullableInt(rs, "last_status_code"),
                rs.getString("last_error"),
                Rows.instant(rs, "created_at"),
                Rows.instant(rs, "completed_at"));
    };

    private final JdbcClient jdbc;

    public JdbcWebhookDeliveryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean addIfAbsent(WebhookDelivery d) {
        // The partial unique index (endpoint, event) over originals makes a repeated event a no-op.
        return insert(d, "on conflict (endpoint_id, event_id) where resend_of is null do nothing") == 1;
    }

    @Override
    public void add(WebhookDelivery d) {
        insert(d, "");
    }

    private int insert(WebhookDelivery d, String conflict) {
        return jdbc.sql("""
                        insert into webhook_deliveries (id, tenant_id, endpoint_id, event_id, event_type, body, status, attempts, next_attempt_at,
                                                        resend_of, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """ + conflict)
                .params(d.id().value(), d.tenantId().value(), d.endpointId().value(), d.eventId(), d.eventType(), d.body(), d.status().name(),
                        d.attempts(), Timestamp.from(d.nextAttemptAt()), d.resendOf() == null ? null : d.resendOf().value(), Timestamp.from(d.createdAt()))
                .update();
    }

    @Override
    public Optional<WebhookDelivery> findById(WebhookDeliveryId id) {
        return jdbc.sql("select " + COLUMNS + " from webhook_deliveries d where d.id = ?").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public List<WebhookDelivery> list(DeliveryStatus status, WebhookEndpointId endpointId, WebhookDeliveryId before, int limit) {
        StringBuilder sql = new StringBuilder("select " + COLUMNS + " from webhook_deliveries d where true");
        List<Object> params = new ArrayList<>();
        if (status != null) {
            sql.append(" and d.status = ?");
            params.add(status.name());
        }
        if (endpointId != null) {
            sql.append(" and d.endpoint_id = ?");
            params.add(endpointId.value());
        }
        if (before != null) {
            sql.append(" and d.id < ?");
            params.add(before.value());
        }
        sql.append(" order by d.id desc limit ?");
        params.add(limit);
        return jdbc.sql(sql.toString()).params(params).query(MAPPER).list();
    }

    @Override
    public List<DeliveryAttempt> attempts(WebhookDeliveryId id) {
        return jdbc.sql("select attempt_no, status_code, error, response_excerpt, latency_ms, attempted_at from webhook_delivery_attempts "
                        + "where delivery_id = ? order by attempt_no")
                .param(id.value())
                .query((rs, n) -> new DeliveryAttempt(rs.getInt("attempt_no"), Rows.nullableInt(rs, "status_code"), rs.getString("error"),
                        rs.getString("response_excerpt"), rs.getLong("latency_ms"), Rows.instant(rs, "attempted_at")))
                .list();
    }

    @Override
    public int killExhausted(Instant now, int maxAttempts) {
        return jdbc.sql("""
                        update webhook_deliveries
                        set status = 'DEAD', completed_at = ?, last_error = coalesce(last_error, 'the worker was lost during the final attempt')
                        where status = 'IN_PROGRESS' and next_attempt_at <= ? and attempts >= ?
                        """)
                .params(Timestamp.from(now), Timestamp.from(now), maxAttempts)
                .update();
    }

    @Override
    public List<ClaimedDelivery> claimDue(Instant now, int limit, Duration lease) {
        // skip locked: a row another worker holds is invisible here, so two workers never take the same one.
        List<UUID> claimed = jdbc.sql("""
                        with due as (
                            select d.id from webhook_deliveries d
                            join webhook_endpoints e on e.tenant_id = d.tenant_id and e.id = d.endpoint_id
                            where d.status in ('PENDING', 'IN_PROGRESS') and d.next_attempt_at <= ? and e.status = 'ACTIVE'
                            order by d.next_attempt_at, d.id
                            limit ?
                            for update of d skip locked
                        )
                        update webhook_deliveries d
                        set status = 'IN_PROGRESS', attempts = d.attempts + 1, next_attempt_at = ?
                        from due where d.id = due.id
                        returning d.id
                        """)
                .params(Timestamp.from(now), limit, Timestamp.from(now.plus(lease)))
                .query(UUID.class)
                .list();
        if (claimed.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select " + COLUMNS + ", e.url as endpoint_url, e.key_id, e.secret_ciphertext from webhook_deliveries d "
                        + "join webhook_endpoints e on e.tenant_id = d.tenant_id and e.id = d.endpoint_id where d.id = any(?) order by d.id")
                .param(claimed.toArray(UUID[]::new))
                .query((rs, n) -> new ClaimedDelivery(MAPPER.mapRow(rs, n), rs.getString("endpoint_url"), rs.getString("key_id"),
                        rs.getString("secret_ciphertext")))
                .list();
    }

    @Override
    public void recordAttempt(
            WebhookDeliveryId id, int claimedAttempts, DeliveryAttempt attempt, DeliveryStatus status, Instant nextAttemptAt, Instant completedAt) {
        jdbc.sql("""
                        insert into webhook_delivery_attempts (id, tenant_id, delivery_id, attempt_no, status_code, error, response_excerpt, latency_ms, attempted_at)
                        select gen_random_uuid(), tenant_id, id, ?, ?, ?, ?, ?, ? from webhook_deliveries where id = ?
                        on conflict (delivery_id, attempt_no) do nothing
                        """)
                .params(attempt.attemptNo(), attempt.statusCode(), Rows.clip(attempt.error(), MAX_ERROR), Rows.clip(attempt.responseExcerpt(), MAX_EXCERPT),
                        (int) Math.min(attempt.latencyMs(), Integer.MAX_VALUE), Timestamp.from(attempt.attemptedAt()), id.value())
                .update();
        jdbc.sql("""
                        update webhook_deliveries
                        set status = ?, next_attempt_at = ?, last_status_code = ?, last_error = ?, completed_at = ?
                        where id = ? and attempts = ? and status = 'IN_PROGRESS'
                        """)
                .params(status.name(), Timestamp.from(nextAttemptAt), attempt.statusCode(), Rows.clip(attempt.error(), MAX_ERROR),
                        completedAt == null ? null : Timestamp.from(completedAt), id.value(), claimedAttempts)
                .update();
    }
}
