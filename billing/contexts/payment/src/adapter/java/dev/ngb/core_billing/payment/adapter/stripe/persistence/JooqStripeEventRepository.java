package dev.ngb.core_billing.payment.adapter.stripe.persistence;

import static dev.ngb.core_billing_jooq.Tables.STRIPE_EVENTS;
import static dev.ngb.core_billing_jooq.Tables.STRIPE_EVENT_ATTEMPTS;

import dev.ngb.core_billing.payment.application.stripe.port.StripeEventRepository;
import dev.ngb.core_billing.payment.domain.stripe.EventAttempt;
import dev.ngb.core_billing.payment.domain.stripe.EventStatus;
import dev.ngb.core_billing.payment.domain.stripe.StripeEventId;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.payment.domain.stripe.StripeEvent;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.Text;
import dev.ngb.core_billing_jooq.tables.records.StripeEventsRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

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
    public List<StripeEventId> due(Instant now, Instant receivedBefore, int limit) {
        return dsl.select(STRIPE_EVENTS.ID)
                .from(STRIPE_EVENTS)
                .where(STRIPE_EVENTS.STATUS.eq("FAILED").and(STRIPE_EVENTS.NEXT_ATTEMPT_AT.le(now))
                        .or(STRIPE_EVENTS.STATUS.eq("RECEIVED").and(STRIPE_EVENTS.RECEIVED_AT.le(receivedBefore))))
                .orderBy(DSL.coalesce(STRIPE_EVENTS.NEXT_ATTEMPT_AT, STRIPE_EVENTS.RECEIVED_AT), STRIPE_EVENTS.ID)
                .limit(limit)
                .fetch(STRIPE_EVENTS.ID)
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
