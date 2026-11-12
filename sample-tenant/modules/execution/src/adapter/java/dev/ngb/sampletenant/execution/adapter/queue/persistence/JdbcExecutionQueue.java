package dev.ngb.sampletenant.execution.adapter.queue.persistence;

import dev.ngb.core.runtime.Clock;
import dev.ngb.sampletenant.execution.application.queue.port.ExecutionQueue;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Claims pending rows in one atomic statement; no two workers can take the same order. */
public class JdbcExecutionQueue implements ExecutionQueue {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcExecutionQueue(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public List<Claimed> claim(int batchSize) {
        return jdbc.query(
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
                (rs, row) -> new Claimed(rs.getObject("order_id", UUID.class), rs.getInt("attempts")),
                Timestamp.from(clock.now()),
                batchSize);
    }

    @Override
    public void finish(UUID orderId) {
        jdbc.update(
                "update execution_queue set status = 'DONE', finished_at = ? where order_id = ?",
                Timestamp.from(clock.now()),
                orderId);
    }

    @Override
    public void requeue(UUID orderId) {
        jdbc.update(
                "update execution_queue set status = 'PENDING', claimed_at = null where order_id = ?", orderId);
    }
}
