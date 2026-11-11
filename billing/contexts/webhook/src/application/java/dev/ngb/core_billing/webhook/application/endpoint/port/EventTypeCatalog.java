package dev.ngb.core_billing.webhook.application.endpoint.port;

import java.util.Set;

/** The event types that exist, so a subscription to a misspelled type is rejected instead of silently never firing. */
public interface EventTypeCatalog {

    Set<String> eventTypes();
}
