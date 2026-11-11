package dev.ngb.sampletenant.resource.application.port;

import dev.ngb.sampletenant.resource.domain.Order;

/** Saves the order and its queue row in one local transaction. */
public interface OrderSubmission {

    void submit(Order order);
}
