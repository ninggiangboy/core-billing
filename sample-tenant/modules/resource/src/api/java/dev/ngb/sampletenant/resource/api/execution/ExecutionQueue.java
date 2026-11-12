package dev.ngb.sampletenant.resource.api.execution;

import java.util.List;
import java.util.UUID;

/** The queue operations the execution worker needs. The resource module owns the queue table. */
public interface ExecutionQueue {

    record Claimed(UUID orderId, int attempts) {}

    List<Claimed> claim(int batchSize);

    void finish(UUID orderId);

    void requeue(UUID orderId);
}
