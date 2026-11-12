package dev.ngb.sampletenant.resource.adapter.order.persistence;

import static dev.ngb.sampletenant_jooq.Tables.EXECUTION_QUEUE;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.resource.api.execution.ExecutionQueue.Claimed;
import dev.ngb.sampletenant.resource.application.order.port.OrderQueueRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;

/** The queue this module owns; a claim is one statement that locks its rows with skip locked. */
public class JooqOrderQueueRepository implements OrderQueueRepository {

    private final DSLContext dsl;
    private final Clock clock;
    private final IdGenerator ids;

    public JooqOrderQueueRepository(DSLContext dsl, Clock clock, IdGenerator ids) {
        this.dsl = dsl;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public void enqueue(UUID orderId, Instant enqueuedAt) {
        dsl.insertInto(EXECUTION_QUEUE)
                .columns(
                        EXECUTION_QUEUE.ID,
                        EXECUTION_QUEUE.ORDER_ID,
                        EXECUTION_QUEUE.STATUS,
                        EXECUTION_QUEUE.ENQUEUED_AT,
                        EXECUTION_QUEUE.ATTEMPTS)
                .values(ids.newId(), orderId, "PENDING", enqueuedAt, 0)
                .execute();
    }

    @Override
    public List<Claimed> claim(int batchSize) {
        // skip locked: a row another worker is claiming is passed over instead of waited for.
        var next = dsl.select(EXECUTION_QUEUE.ID)
                .from(EXECUTION_QUEUE)
                .where(EXECUTION_QUEUE.STATUS.eq("PENDING"))
                .orderBy(EXECUTION_QUEUE.ENQUEUED_AT)
                .limit(batchSize)
                .forUpdate()
                .skipLocked();
        return dsl.update(EXECUTION_QUEUE)
                .set(EXECUTION_QUEUE.STATUS, "RUNNING")
                .set(EXECUTION_QUEUE.CLAIMED_AT, clock.now())
                .set(EXECUTION_QUEUE.ATTEMPTS, EXECUTION_QUEUE.ATTEMPTS.plus(1))
                .where(EXECUTION_QUEUE.ID.in(next))
                .returning(EXECUTION_QUEUE.ORDER_ID, EXECUTION_QUEUE.ATTEMPTS)
                .fetch(row -> new Claimed(row.getOrderId(), row.getAttempts()));
    }

    @Override
    public void finish(UUID orderId) {
        dsl.update(EXECUTION_QUEUE)
                .set(EXECUTION_QUEUE.STATUS, "DONE")
                .set(EXECUTION_QUEUE.FINISHED_AT, clock.now())
                .where(EXECUTION_QUEUE.ORDER_ID.eq(orderId))
                .execute();
    }

    @Override
    public void requeue(UUID orderId) {
        dsl.update(EXECUTION_QUEUE)
                .set(EXECUTION_QUEUE.STATUS, "PENDING")
                .setNull(EXECUTION_QUEUE.CLAIMED_AT)
                .where(EXECUTION_QUEUE.ORDER_ID.eq(orderId))
                .execute();
    }
}
