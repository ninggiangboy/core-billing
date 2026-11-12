package dev.ngb.billing.webhook.application.delivery.port;

import dev.ngb.core.event.DomainEvent;

/** Renders an event as the JSON text that is delivered: id, type, tenant, time and the event's own data. */
public interface EventBodyWriter {

    String write(DomainEvent event);
}
