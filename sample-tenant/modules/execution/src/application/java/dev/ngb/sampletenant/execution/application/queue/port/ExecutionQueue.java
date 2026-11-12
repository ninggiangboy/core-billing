package dev.ngb.sampletenant.execution.application.queue.port;

import java.util.List;
import java.util.UUID;

/** The database-backed queue. A claim uses {@code for update skip locked}, so workers never collide. */
public interface ExecutionQueue {

    record Claimed(UUID orderId, int attempts) {}

    List<Claimed> claim(int batchSize);

    void finish(UUID orderId);

    void requeue(UUID orderId);
}
