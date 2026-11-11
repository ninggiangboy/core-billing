package dev.ngb.core_billing.boot.infrastructure.outbox;

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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
        for (int i = 0; i < batchSize; i++) {
            Outcome outcome = relayNext();
            if (outcome == Outcome.NONE || outcome == Outcome.CONTENDED) {
                // A contended row stays due, so another pass in this tick would only find the same row again.
                break;
            }
            switch (outcome) {
                case DELIVERED -> delivered++;
                case RETRY -> failed++;
                case DEAD -> dead++;
                default -> throw new IllegalStateException("unexpected outcome " + outcome);
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

    private Outcome relayNext() {
        Instant now = clock.now();
        try {
            return unitOfWork.transactional(() -> {
                Optional<Row> claimed = claim(now);
                if (claimed.isEmpty()) {
                    return Outcome.NONE;
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
                return Outcome.DELIVERED;
            });
        } catch (DispatchFailure failure) {
            return unitOfWork.transactional(() -> recordFailure(failure.row, (RuntimeException) failure.getCause(), now));
        }
    }

    private Optional<Row> claim(Instant now) {
        return dsl.selectFrom(OUTBOX)
                .where(OUTBOX.STATUS.eq("PENDING"))
                .and(OUTBOX.NEXT_ATTEMPT_AT.le(now))
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
