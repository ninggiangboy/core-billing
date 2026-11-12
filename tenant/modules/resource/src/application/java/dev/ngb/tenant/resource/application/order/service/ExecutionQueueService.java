package dev.ngb.tenant.resource.application.order.service;

import dev.ngb.tenant.resource.api.execution.ExecutionQueue;
import dev.ngb.tenant.resource.application.order.port.OrderQueueRepository;
import java.util.List;
import java.util.UUID;

/** Serves the queue to the execution worker, which reaches it only through the resource api. */
public class ExecutionQueueService implements ExecutionQueue {

    private final OrderQueueRepository queue;

    public ExecutionQueueService(OrderQueueRepository queue) {
        this.queue = queue;
    }

    @Override
    public List<Claimed> claim(int batchSize) {
        return queue.claim(batchSize);
    }

    @Override
    public void finish(UUID orderId) {
        queue.finish(orderId);
    }

    @Override
    public void requeue(UUID orderId) {
        queue.requeue(orderId);
    }
}
