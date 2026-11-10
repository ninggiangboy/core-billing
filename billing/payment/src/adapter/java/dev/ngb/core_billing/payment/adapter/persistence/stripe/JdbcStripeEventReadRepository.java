package dev.ngb.core_billing.payment.adapter.persistence.stripe;

import dev.ngb.core_billing.payment.application.stripe.query.EventAttemptView;
import dev.ngb.core_billing.payment.application.stripe.query.StripeEventReadRepository;
import dev.ngb.core_billing.payment.application.stripe.query.StripeEventView;
import dev.ngb.core_billing.payment.domain.stripe.EventStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects Stripe events and their attempt history straight into views. */
public class JdbcStripeEventReadRepository implements StripeEventReadRepository {

    private static final String COLUMNS = "id, event_id, type, status, attempts, next_attempt_at, last_error, received_at, processed_at";

    private record Row(
            UUID id,
            String eventId,
            String type,
            String status,
            int attempts,
            Instant nextAttemptAt,
            String lastError,
            Instant receivedAt,
            Instant processedAt) {

        StripeEventView toView() {
            return new StripeEventView(
                    id, eventId, type, EventStatus.valueOf(status), attempts, nextAttemptAt, lastError, receivedAt, processedAt);
        }
    }

    private final JdbcClient jdbc;

    public JdbcStripeEventReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StripeEventView> list(EventStatus status, UUID before, int limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("select " + COLUMNS + " from stripe_events where true");
        if (status != null) {
            sql.append(" and status = ?");
            params.add(status.name());
        }
        if (before != null) {
            sql.append(" and id < ?");
            params.add(before);
        }
        sql.append(" order by id desc limit ?");
        params.add(limit);
        return jdbc.sql(sql.toString()).params(params).query(Row.class).list().stream().map(Row::toView).toList();
    }

    @Override
    public Optional<StripeEventView> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from stripe_events where id = ?").param(id).query(Row.class).optional().map(Row::toView);
    }

    @Override
    public List<EventAttemptView> attempts(UUID eventId) {
        return jdbc.sql("select attempt_no as attempt, outcome, detail, attempted_at from stripe_event_attempts where event_row_id = ? order by attempt_no")
                .param(eventId)
                .query(EventAttemptView.class)
                .list();
    }
}
