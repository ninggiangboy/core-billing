package dev.ngb.core_billing.payment.adapter.persistence.stripe;

import dev.ngb.core_billing.payment.adapter.persistence.Rows;

import dev.ngb.core_billing.payment.application.stripe.port.StripeEventRepository;
import dev.ngb.core_billing.payment.domain.stripe.EventAttempt;
import dev.ngb.core_billing.payment.domain.stripe.EventStatus;
import dev.ngb.core_billing.payment.domain.stripe.StoredEventId;
import dev.ngb.core_billing.payment.domain.stripe.StripeEvent;
import dev.ngb.core_billing.sharedkernel.Text;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcStripeEventRepository implements StripeEventRepository {

    private static final int MAX_DETAIL = 1000;

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
                UUID.randomUUID(), event.tenantId().value(), event.id().value(), number, outcome, detail == null ? null : Text.clip(detail, MAX_DETAIL), at));
    }
}
