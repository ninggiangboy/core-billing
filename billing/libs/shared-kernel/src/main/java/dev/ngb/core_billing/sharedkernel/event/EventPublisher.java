package dev.ngb.core_billing.sharedkernel.event;

/** Port that records an event in the caller's transaction. It never delivers synchronously. */
public interface EventPublisher {

    void publish(DomainEvent event);
}
