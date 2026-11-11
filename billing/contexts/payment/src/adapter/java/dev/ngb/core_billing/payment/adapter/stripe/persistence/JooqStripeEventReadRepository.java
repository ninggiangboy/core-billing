package dev.ngb.core_billing.payment.adapter.stripe.persistence;

import static dev.ngb.core_billing_jooq.Tables.STRIPE_EVENTS;
import static dev.ngb.core_billing_jooq.Tables.STRIPE_EVENT_ATTEMPTS;
import static org.jooq.impl.DSL.noCondition;

import dev.ngb.core_billing.payment.application.stripe.query.EventAttemptView;
import dev.ngb.core_billing.payment.application.stripe.query.StripeEventReadRepository;
import dev.ngb.core_billing.payment.application.stripe.query.StripeEventView;
import dev.ngb.core_billing.payment.domain.stripe.EventStatus;
import dev.ngb.core_billing_jooq.tables.records.StripeEventsRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;

/** Selects Stripe events and their attempt history straight into views. */
public class JooqStripeEventReadRepository implements StripeEventReadRepository {

    private final DSLContext dsl;

    public JooqStripeEventReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<StripeEventView> list(EventStatus status, UUID before, int limit) {
        Condition filter = (status == null ? noCondition() : STRIPE_EVENTS.STATUS.eq(status.name()))
                .and(before == null ? noCondition() : STRIPE_EVENTS.ID.lt(before));
        return dsl.selectFrom(STRIPE_EVENTS)
                .where(filter)
                .orderBy(STRIPE_EVENTS.ID.desc())
                .limit(limit)
                .fetch(JooqStripeEventReadRepository::toView);
    }

    @Override
    public Optional<StripeEventView> findById(UUID id) {
        return dsl.selectFrom(STRIPE_EVENTS)
                .where(STRIPE_EVENTS.ID.eq(id))
                .fetchOptional(JooqStripeEventReadRepository::toView);
    }

    @Override
    public List<EventAttemptView> attempts(UUID eventId) {
        return dsl.select(STRIPE_EVENT_ATTEMPTS.ATTEMPT_NO, STRIPE_EVENT_ATTEMPTS.OUTCOME, STRIPE_EVENT_ATTEMPTS.DETAIL,
                        STRIPE_EVENT_ATTEMPTS.ATTEMPTED_AT)
                .from(STRIPE_EVENT_ATTEMPTS)
                .where(STRIPE_EVENT_ATTEMPTS.EVENT_ROW_ID.eq(eventId))
                .orderBy(STRIPE_EVENT_ATTEMPTS.ATTEMPT_NO)
                .fetch(row -> new EventAttemptView(row.value1(), row.value2(), row.value3(), row.value4()));
    }

    private static StripeEventView toView(StripeEventsRecord row) {
        return new StripeEventView(
                row.getId(),
                row.getEventId(),
                row.getType(),
                EventStatus.valueOf(row.getStatus()),
                row.getAttempts(),
                row.getNextAttemptAt(),
                row.getLastError(),
                row.getReceivedAt(),
                row.getProcessedAt());
    }
}
