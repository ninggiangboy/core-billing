package dev.ngb.sampletenant.resource.adapter.order.persistence;

import static dev.ngb.sampletenant_jooq.Tables.EXECUTION_QUEUE;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.resource.api.execution.ExecutionQueue.Claimed;
import dev.ngb.sampletenant.resource.application.order.port.OrderQueueRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;

/** The queue this module owns; a claim is one statement with for update skip locked. */
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
        return dsl.fetch(
                        """
                        update execution_queue
                           set status = 'RUNNING', claimed_at = ?, attempts = attempts + 1
                         where id in (
                               select id from execution_queue
                                where status = 'PENDING'
                                order by enqueued_at
                                for update skip locked
                                limit ?)
                        returning order_id, attempts
                        """,
                        Timestamp.from(clock.now()),
                        batchSize)
                .map(row -> new Claimed(row.get("order_id", UUID.class), row.get("attempts", Integer.class)));
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
