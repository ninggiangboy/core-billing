package dev.ngb.core_billing.boot.infrastructure.messaging;

import static dev.ngb.core_billing_jooq.Tables.OUTBOX;

import dev.ngb.core_billing.sharedkernel.Backoff;
import dev.ngb.core_billing.sharedkernel.Causes;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventEnvelope;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing_jooq.tables.records.OutboxRecord;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Moves events from the transactional outbox to the in-process consumers, for the tenant whose scope is bound.
 *
 * <p>Each row is handled in its own transaction: it is claimed with {@code for update skip locked} (two relays
 * never take the same row), published as a Spring application event to every consumer, and marked delivered.
 * If a consumer throws, that transaction rolls back, so no half-done consumer work survives, and a second
 * transaction records the failure and schedules the retry with exponential backoff. After the attempt limit
 * the row becomes {@code DEAD} and is kept for inspection. Consumers deduplicate by event id (the inbox),
 * because a crash between a consumer's commit and the mark would otherwise deliver twice.
 */
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {};

    public record Result(int delivered, int failed, int dead) {}

    private record Row(UUID id, UUID tenantId, String aggregate, String type, String payload, Instant createdAt, int attempts) {}

    private enum Outcome { NONE, DELIVERED, RETRY, DEAD, CONTENDED }

    private record Attempt(Outcome outcome, UUID rowId) {
        static final Attempt NONE = new Attempt(Outcome.NONE, null);
    }

    private static final class DispatchFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient Row row;

        DispatchFailure(Row row, RuntimeException cause) {
            super(cause);
            this.row = row;
        }
    }

    private final UnitOfWork unitOfWork;
    private final DSLContext dsl;
    private final JsonMapper json;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final MeterRegistry meters;
    private final int batchSize;
    private final int maxAttempts;
    private final Backoff backoff;

    public OutboxRelay(
            UnitOfWork unitOfWork, DSLContext dsl, JsonMapper json, ApplicationEventPublisher publisher, Clock clock, MeterRegistry meters,
            int batchSize, int maxAttempts, Backoff backoff) {
        this.unitOfWork = unitOfWork;
        this.dsl = dsl;
        this.json = json;
        this.publisher = publisher;
        this.clock = clock;
        this.meters = meters;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    /** Relays up to one batch of due events. */
    public Result relayDue() {
        int delivered = 0;
        int failed = 0;
        int dead = 0;
        Set<UUID> contended = new HashSet<>();
        for (int i = 0; i < batchSize; i++) {
            Attempt attempt = relayNext(contended);
            if (attempt.outcome() == Outcome.NONE) {
                break;
            }
            switch (attempt.outcome()) {
                case CONTENDED -> {
                    // The row stays due for the next poll, but passing over it here keeps the rows behind it moving
                    // instead of letting one busy row block the rest of the batch.
                    contended.add(attempt.rowId());
                }
                case DELIVERED -> delivered++;
                case RETRY -> failed++;
                case DEAD -> dead++;
                default -> throw new IllegalStateException("unexpected outcome " + attempt.outcome());
            }
        }
        if (delivered > 0) {
            meters.counter("billing.outbox.delivered").increment(delivered);
        }
        if (dead > 0) {
            meters.counter("billing.outbox.dead").increment(dead);
        }
        return new Result(delivered, failed, dead);
    }

    private Attempt relayNext(Set<UUID> excluding) {
        Instant now = clock.now();
        try {
            return unitOfWork.transactional(() -> {
                Optional<Row> claimed = claim(now, excluding);
                if (claimed.isEmpty()) {
                    return Attempt.NONE;
                }
                Row row = claimed.get();
                try {
                    publisher.publishEvent(toEnvelope(row));
                } catch (RuntimeException failure) {
                    throw new DispatchFailure(row, failure);
                }
                dsl.update(OUTBOX)
                        .set(OUTBOX.STATUS, "DELIVERED")
                        .set(OUTBOX.LAST_ERROR, (String) null)
                        .where(OUTBOX.ID.eq(row.id()))
                        .execute();
                return new Attempt(Outcome.DELIVERED, row.id());
            });
        } catch (DispatchFailure failure) {
            Outcome outcome = unitOfWork.transactional(() -> recordFailure(failure.row, (RuntimeException) failure.getCause(), now));
            return new Attempt(outcome, failure.row.id());
        }
    }

    private Optional<Row> claim(Instant now, Set<UUID> excluding) {
        Condition due = OUTBOX.STATUS.eq("PENDING").and(OUTBOX.NEXT_ATTEMPT_AT.le(now));
        if (!excluding.isEmpty()) {
            due = due.and(OUTBOX.ID.notIn(excluding));
        }
        return dsl.selectFrom(OUTBOX)
                .where(due)
                .orderBy(OUTBOX.NEXT_ATTEMPT_AT, OUTBOX.ID)
                .limit(1)
                .forUpdate()
                .skipLocked()
                .fetchOptional(OutboxRelay::toRow);
    }

    private static Row toRow(OutboxRecord record) {
        return new Row(
                record.getId(),
                record.getTenantId(),
                record.getAggregate(),
                record.getType(),
                record.getPayload().data(),
                record.getCreatedAt(),
                record.getAttempts());
    }

    private EventEnvelope toEnvelope(Row row) {
        return new EventEnvelope(row.id(), new TenantId(row.tenantId()), row.type(), row.aggregate(), row.createdAt(), json.readValue(row.payload(), PAYLOAD));
    }

    private Outcome recordFailure(Row row, RuntimeException cause, Instant now) {
        if (Causes.isRetryableContention(cause)) {
            // The lock wait timed out, so no consumer committed and no attempt was spent: the row stays due and the
            // next poll tries it again.
            LOG.warn("outbox event {} ({}) hit lock contention, retrying on the next poll", row.id(), row.type());
            return Outcome.CONTENDED;
        }
        int failures = row.attempts() + 1;
        boolean dead = failures >= maxAttempts;
        Instant next = dead ? now : now.plus(backoff.delayAfter(failures, ThreadLocalRandom.current().nextDouble()));
        String reason = cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
        dsl.update(OUTBOX)
                .set(OUTBOX.ATTEMPTS, failures)
                .set(OUTBOX.STATUS, dead ? "DEAD" : "PENDING")
                .set(OUTBOX.NEXT_ATTEMPT_AT, next)
                .set(OUTBOX.LAST_ERROR, reason.length() > 500 ? reason.substring(0, 500) : reason)
                .where(OUTBOX.ID.eq(row.id()))
                .execute();
        if (dead) {
            LOG.error("outbox event {} ({}) is dead after {} attempts: {}", row.id(), row.type(), failures, reason);
            return Outcome.DEAD;
        }
        LOG.warn("outbox event {} ({}) failed attempt {}, retrying at {}: {}", row.id(), row.type(), failures, next, reason);
        return Outcome.RETRY;
    }
}
