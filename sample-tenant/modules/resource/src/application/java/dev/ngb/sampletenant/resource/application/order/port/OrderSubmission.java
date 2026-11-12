package dev.ngb.sampletenant.resource.application.order.port;

import dev.ngb.sampletenant.resource.domain.order.Order;

/** Saves the order and its queue row in one local transaction. */
public interface OrderSubmission {

    void submit(Order order);
}
