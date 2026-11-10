package io.corebilling.payment.adapter.persistence;

import io.corebilling.payment.application.stripe.port.StripeEventRepository;
import io.corebilling.payment.domain.stripe.EventAttempt;
import io.corebilling.payment.domain.stripe.EventStatus;
import io.corebilling.payment.domain.stripe.StoredEventId;
import io.corebilling.payment.domain.stripe.StripeEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcStripeEventRepository implements StripeEventRepository {

    private static final String COLUMNS = "id, tenant_id, configuration_id, stripe_account, event_id, type, livemode, payload, status, attempts, "
            + "next_attempt_at, last_error, received_at, processed_at";

    private final JdbcAggregateOperations aggregates;
    private final JdbcClient jdbc;

    public JdbcStripeEventRepository(JdbcAggregateOperations aggregates, JdbcClient jdbc) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(StripeEvent e) {
        // The inbox is first-writer-wins per Stripe account and event id.
        StripeEventRow row = StripeEventRow.from(e);
        return jdbc.sql("insert into stripe_events (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict (stripe_account, event_id) do nothing")
                .params(row.id(), row.tenantId(), row.configurationId(), row.stripeAccount(), row.eventId(), row.type(), row.livemode(), row.payload(),
                        row.status(), row.attempts(), Rows.timestamp(row.nextAttemptAt()), row.lastError(), Rows.timestamp(row.receivedAt()),
                        Rows.timestamp(row.processedAt()))
                .update() == 1;
    }

    @Override
    public void update(StripeEvent e) {
        aggregates.update(StripeEventRow.from(e));
    }

    @Override
    public Optional<StripeEvent> findById(StoredEventId id) {
        return Optional.ofNullable(aggregates.findById(id.value(), StripeEventRow.class)).map(StripeEventRow::toDomain);
    }

    @Override
    public Optional<StripeEvent> lockById(StoredEventId id) {
        return jdbc.sql("select " + COLUMNS + " from stripe_events where id = ? for update").param(id.value()).query(StripeEventRow.class)
                .optional()
                .map(StripeEventRow::toDomain);
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
                .query(UUID.class)
                .list()
                .stream()
                .map(StoredEventId::new)
                .toList();
    }

    @Override
    public void addAttempt(StripeEvent event, int number, String outcome, String detail, Instant at) {
        aggregates.insert(new EventAttemptRow(
                UUID.randomUUID(), event.tenantId().value(), event.id().value(), number, outcome, detail == null ? null : clip(detail), at));
    }

    private static String clip(String text) {
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
