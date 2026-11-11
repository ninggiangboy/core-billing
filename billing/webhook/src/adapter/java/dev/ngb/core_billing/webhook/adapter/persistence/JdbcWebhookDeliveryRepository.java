package dev.ngb.core_billing.webhook.adapter.persistence;

import dev.ngb.core_billing.sharedkernel.Text;
import dev.ngb.core_billing.webhook.application.delivery.command.ClaimedDelivery;
import dev.ngb.core_billing.webhook.application.delivery.port.WebhookDeliveryRepository;
import dev.ngb.core_billing.webhook.domain.delivery.DeliveryAttempt;
import dev.ngb.core_billing.webhook.domain.delivery.DeliveryStatus;
import dev.ngb.core_billing.webhook.domain.delivery.WebhookDelivery;
import dev.ngb.core_billing.webhook.domain.delivery.WebhookDeliveryId;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcWebhookDeliveryRepository implements WebhookDeliveryRepository {

    private static final int MAX_ERROR = 500;
    private static final int MAX_EXCERPT = 1000;

    private static final String COLUMNS = "d.id, d.tenant_id, d.endpoint_id, d.event_id, d.event_type, d.body, d.status, d.attempts, d.next_attempt_at, "
            + "d.resend_of, d.last_status_code, d.last_error, d.created_at, d.completed_at";

    private final JdbcAggregateOperations aggregates;
    private final JdbcClient jdbc;

    public JdbcWebhookDeliveryRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
    }

    @Override
    public boolean addIfAbsent(WebhookDelivery d) {
        // The partial unique index (endpoint, event) over originals makes a repeated event a no-op.
        return insert(d, "on conflict (endpoint_id, event_id) where resend_of is null do nothing") == 1;
    }

    @Override
    public void add(WebhookDelivery d) {
        aggregates.insert(WebhookDeliveryRow.from(d));
    }

    private int insert(WebhookDelivery d, String conflict) {
        WebhookDeliveryRow row = WebhookDeliveryRow.from(d);
        return jdbc.sql("""
                        insert into webhook_deliveries (id, tenant_id, endpoint_id, event_id, event_type, body, status, attempts, next_attempt_at,
                                                        resend_of, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """ + conflict)
                .params(row.id(), row.tenantId(), row.endpointId(), row.eventId(), row.eventType(), row.body(), row.status(),
                        row.attempts(), Timestamp.from(row.nextAttemptAt()), row.resendOf(), Timestamp.from(row.createdAt()))
                .update();
    }

    @Override
    public Optional<WebhookDelivery> findById(WebhookDeliveryId id) {
        return Optional.ofNullable(aggregates.findById(id.value(), WebhookDeliveryRow.class)).map(WebhookDeliveryRow::toDomain);
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
                .query(ClaimedDeliveryRow.class)
                .list()
                .stream()
                .map(ClaimedDeliveryRow::toClaimed)
                .toList();
    }

    @Override
    public void recordAttempt(
            WebhookDeliveryId id, int claimedAttempts, DeliveryAttempt attempt, DeliveryStatus status, Instant nextAttemptAt, Instant completedAt) {
        jdbc.sql("""
                        insert into webhook_delivery_attempts (id, tenant_id, delivery_id, attempt_no, status_code, error, response_excerpt, latency_ms, attempted_at)
                        select gen_random_uuid(), tenant_id, id, ?, ?, ?, ?, ?, ? from webhook_deliveries where id = ?
                        on conflict (delivery_id, attempt_no) do nothing
                        """)
                .params(attempt.attemptNo(), attempt.statusCode(), Text.clip(attempt.error(), MAX_ERROR), Text.clip(attempt.responseExcerpt(), MAX_EXCERPT),
                        (int) Math.min(attempt.latencyMs(), Integer.MAX_VALUE), Timestamp.from(attempt.attemptedAt()), id.value())
                .update();
        jdbc.sql("""
                        update webhook_deliveries
                        set status = ?, next_attempt_at = ?, last_status_code = ?, last_error = ?, completed_at = ?
                        where id = ? and attempts = ? and status = 'IN_PROGRESS'
                        """)
                .params(status.name(), Timestamp.from(nextAttemptAt), attempt.statusCode(), Text.clip(attempt.error(), MAX_ERROR),
                        completedAt == null ? null : Timestamp.from(completedAt), id.value(), claimedAttempts)
                .update();
    }
}
