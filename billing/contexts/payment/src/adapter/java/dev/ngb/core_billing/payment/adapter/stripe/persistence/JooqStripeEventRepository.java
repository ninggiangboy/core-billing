package dev.ngb.core_billing.payment.adapter.stripe.persistence;

import static dev.ngb.core_billing_jooq.Tables.STRIPE_EVENTS;
import static dev.ngb.core_billing_jooq.Tables.STRIPE_EVENT_ATTEMPTS;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.util.Text;
import dev.ngb.core_billing.payment.application.stripe.port.StripeEventRepository;
import dev.ngb.core_billing.payment.domain.stripe.EventAttempt;
import dev.ngb.core_billing.payment.domain.stripe.EventStatus;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.payment.domain.stripe.StripeEvent;
import dev.ngb.core_billing.payment.domain.stripe.StripeEventId;
import dev.ngb.core_billing_jooq.tables.records.StripeEventsRecord;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

public class JooqStripeEventRepository implements StripeEventRepository {

    private static final int MAX_DETAIL = 1000;

    private final DSLContext dsl;

    public JooqStripeEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean insertIfAbsent(StripeEvent e) {
        // The inbox is first-writer-wins per Stripe account and event id.
        return dsl.insertInto(STRIPE_EVENTS)
                .set(STRIPE_EVENTS.ID, e.id().value())
                .set(STRIPE_EVENTS.TENANT_ID, e.tenantId().value())
                .set(STRIPE_EVENTS.CONFIGURATION_ID, e.configurationId().value())
                .set(STRIPE_EVENTS.STRIPE_ACCOUNT, e.stripeAccount())
                .set(STRIPE_EVENTS.EVENT_ID, e.eventId())
                .set(STRIPE_EVENTS.TYPE, e.type())
                .set(STRIPE_EVENTS.LIVEMODE, e.livemode())
                .set(STRIPE_EVENTS.PAYLOAD, e.payload())
                .set(STRIPE_EVENTS.STATUS, e.status().name())
                .set(STRIPE_EVENTS.ATTEMPTS, e.attempts())
                .set(STRIPE_EVENTS.NEXT_ATTEMPT_AT, e.nextAttemptAt())
                .set(STRIPE_EVENTS.LAST_ERROR, e.lastError())
                .set(STRIPE_EVENTS.RECEIVED_AT, e.receivedAt())
                .set(STRIPE_EVENTS.PROCESSED_AT, e.processedAt())
                .onConflict(STRIPE_EVENTS.STRIPE_ACCOUNT, STRIPE_EVENTS.EVENT_ID)
                .doNothing()
                .execute() == 1;
    }

    @Override
    public void update(StripeEvent e) {
        dsl.update(STRIPE_EVENTS)
                .set(STRIPE_EVENTS.CONFIGURATION_ID, e.configurationId().value())
                .set(STRIPE_EVENTS.STRIPE_ACCOUNT, e.stripeAccount())
                .set(STRIPE_EVENTS.EVENT_ID, e.eventId())
                .set(STRIPE_EVENTS.TYPE, e.type())
                .set(STRIPE_EVENTS.LIVEMODE, e.livemode())
                .set(STRIPE_EVENTS.PAYLOAD, e.payload())
                .set(STRIPE_EVENTS.STATUS, e.status().name())
                .set(STRIPE_EVENTS.ATTEMPTS, e.attempts())
                .set(STRIPE_EVENTS.NEXT_ATTEMPT_AT, e.nextAttemptAt())
                .set(STRIPE_EVENTS.LAST_ERROR, e.lastError())
                .set(STRIPE_EVENTS.RECEIVED_AT, e.receivedAt())
                .set(STRIPE_EVENTS.PROCESSED_AT, e.processedAt())
                // The attempt is over, so the claim goes with it: a short backoff must be able to come due again
                // before another worker's claim deadline would have run out.
                .set(STRIPE_EVENTS.CLAIMED_UNTIL, (Instant) null)
                .where(STRIPE_EVENTS.ID.eq(e.id().value()))
                .execute();
    }

    @Override
    public Optional<StripeEvent> findById(StripeEventId id) {
        return dsl.selectFrom(STRIPE_EVENTS)
                .where(STRIPE_EVENTS.ID.eq(id.value()))
                .fetchOptional(JooqStripeEventRepository::toDomain);
    }

    @Override
    public Optional<StripeEvent> lockById(StripeEventId id) {
        return dsl.selectFrom(STRIPE_EVENTS)
                .where(STRIPE_EVENTS.ID.eq(id.value()))
                .forUpdate()
                .fetchOptional(JooqStripeEventRepository::toDomain);
    }

    @Override
    public List<StripeEventId> claimDue(Instant now, Instant receivedBefore, Duration lease, int limit) {
        // Finding and claiming are one statement: skip locked makes a row another worker is holding invisible here,
        // and the deadline makes it invisible until the holder finishes or is given up on.
        return dsl.fetch("""
                        with due as (
                            select id from stripe_events
                            where ((status = 'FAILED' and next_attempt_at <= ?)
                                or (status = 'RECEIVED' and received_at <= ?))
                              and (claimed_until is null or claimed_until <= ?)
                            order by coalesce(next_attempt_at, received_at), id
                            limit ?
                            for update skip locked
                        )
                        update stripe_events s
                        set claimed_until = ?
                        from due where s.id = due.id
                        returning s.id
                        """,
                        Timestamp.from(now), Timestamp.from(receivedBefore), Timestamp.from(now), limit, Timestamp.from(now.plus(lease)))
                .getValues(STRIPE_EVENTS.ID)
                .stream()
                .map(StripeEventId::new)
                .toList();
    }

    @Override
    public void addAttempt(StripeEvent event, int number, String outcome, String detail, Instant at) {
        dsl.insertInto(STRIPE_EVENT_ATTEMPTS)
                .set(STRIPE_EVENT_ATTEMPTS.ID, UUID.randomUUID())
                .set(STRIPE_EVENT_ATTEMPTS.TENANT_ID, event.tenantId().value())
                .set(STRIPE_EVENT_ATTEMPTS.EVENT_ROW_ID, event.id().value())
                .set(STRIPE_EVENT_ATTEMPTS.ATTEMPT_NO, number)
                .set(STRIPE_EVENT_ATTEMPTS.OUTCOME, outcome)
                .set(STRIPE_EVENT_ATTEMPTS.DETAIL, detail == null ? null : Text.clip(detail, MAX_DETAIL))
                .set(STRIPE_EVENT_ATTEMPTS.ATTEMPTED_AT, at)
                .execute();
    }

    private static StripeEvent toDomain(StripeEventsRecord row) {
        return StripeEvent.restore(
                new StripeEventId(row.getId()),
                new TenantId(row.getTenantId()),
                new StripeConfigurationId(row.getConfigurationId()),
                row.getStripeAccount(),
                row.getEventId(),
                row.getType(),
                row.getLivemode(),
                row.getPayload(),
                EventStatus.valueOf(row.getStatus()),
                row.getAttempts(),
                row.getNextAttemptAt(),
                row.getLastError(),
                row.getReceivedAt(),
                row.getProcessedAt());
    }
}
