package dev.ngb.core_billing.webhook.application.delivery.port;

import dev.ngb.core_billing.sharedkernel.DomainEvent;

/** Renders an event as the JSON text that is delivered: id, type, tenant, time and the event's own data. */
public interface EventBodyWriter {

    String write(DomainEvent event);
}
