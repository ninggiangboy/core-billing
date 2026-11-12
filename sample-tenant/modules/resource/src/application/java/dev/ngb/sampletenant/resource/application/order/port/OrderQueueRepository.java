package dev.ngb.sampletenant.resource.application.order.port;

import dev.ngb.sampletenant.resource.api.execution.ExecutionQueue.Claimed;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistence boundary for the execution queue this module owns. */
public interface OrderQueueRepository {

    void enqueue(UUID orderId, Instant enqueuedAt);

    List<Claimed> claim(int batchSize);

    void finish(UUID orderId);

    void requeue(UUID orderId);
}
