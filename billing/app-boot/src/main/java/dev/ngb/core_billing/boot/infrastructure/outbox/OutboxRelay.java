package dev.ngb.core_billing.boot.infrastructure.outbox;

import dev.ngb.core_billing.sharedkernel.Backoff;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventEnvelope;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
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

    private enum Outcome { NONE, DELIVERED, RETRY, DEAD }

    private static final class DispatchFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient Row row;

        DispatchFailure(Row row, RuntimeException cause) {
            super(cause);
            this.row = row;
        }
    }

    private final UnitOfWork unitOfWork;
    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final MeterRegistry meters;
    private final int batchSize;
    private final int maxAttempts;
    private final Backoff backoff;

    public OutboxRelay(
            UnitOfWork unitOfWork, JdbcClient jdbc, JsonMapper json, ApplicationEventPublisher publisher, Clock clock, MeterRegistry meters,
            int batchSize, int maxAttempts, Backoff backoff) {
        this.unitOfWork = unitOfWork;
        this.jdbc = jdbc;
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
            if (outcome == Outcome.NONE) {
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
                jdbc.sql("update outbox set status = 'DELIVERED', last_error = null where id = ?").param(row.id()).update();
                return Outcome.DELIVERED;
            });
        } catch (DispatchFailure failure) {
            return unitOfWork.transactional(() -> recordFailure(failure.row, (RuntimeException) failure.getCause(), now));
        }
    }

    private Optional<Row> claim(Instant now) {
        return jdbc.sql("""
                        select id, tenant_id, aggregate, type, payload::text as payload, created_at, attempts
                        from outbox
                        where status = 'PENDING' and next_attempt_at <= ?
                        order by next_attempt_at, id
                        limit 1
                        for update skip locked
                        """)
                .param(Timestamp.from(now))
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("aggregate"),
                        rs.getString("type"), rs.getString("payload"), rs.getTimestamp("created_at").toInstant(), rs.getInt("attempts")))
                .optional();
    }

    private EventEnvelope toEnvelope(Row row) {
        return new EventEnvelope(row.id(), new TenantId(row.tenantId()), row.type(), row.aggregate(), row.createdAt(), json.readValue(row.payload(), PAYLOAD));
    }

    private Outcome recordFailure(Row row, RuntimeException cause, Instant now) {
        int failures = row.attempts() + 1;
        boolean dead = failures >= maxAttempts;
        Instant next = dead ? now : now.plus(backoff.delayAfter(failures, ThreadLocalRandom.current().nextDouble()));
        String reason = cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
        jdbc.sql("update outbox set attempts = ?, status = ?, next_attempt_at = ?, last_error = ? where id = ?")
                .params(failures, dead ? "DEAD" : "PENDING", Timestamp.from(next), reason.length() > 500 ? reason.substring(0, 500) : reason, row.id())
                .update();
        if (dead) {
            LOG.error("outbox event {} ({}) is dead after {} attempts: {}", row.id(), row.type(), failures, reason);
            return Outcome.DEAD;
        }
        LOG.warn("outbox event {} ({}) failed attempt {}, retrying at {}: {}", row.id(), row.type(), failures, next, reason);
        return Outcome.RETRY;
    }
}
