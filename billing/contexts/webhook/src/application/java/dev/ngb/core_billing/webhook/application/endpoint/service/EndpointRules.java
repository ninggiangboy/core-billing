package dev.ngb.core_billing.webhook.application.endpoint.service;

import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpoint;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookUrl;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import dev.ngb.core_billing.webhook.application.shared.port.EventTypeCatalog;
import dev.ngb.core_billing.webhook.application.shared.service.WebhookSettings;

/** Validation shared by creating and changing an endpoint. */
public final class EndpointRules {

    private EndpointRules() {}

    public static String url(String raw, WebhookSettings settings) {
        return WebhookUrl.parse(raw, settings.allowInsecureTargets());
    }

    public static Set<String> eventTypes(List<String> requested, EventTypeCatalog catalog) {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("subscribe to at least one event type");
        }
        Set<String> known = catalog.eventTypes();
        Set<String> result = new LinkedHashSet<>();
        for (String type : requested) {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("an event type must not be blank");
            }
            String trimmed = type.strip();
            if (!trimmed.equals(WebhookEndpoint.ALL_EVENTS) && !known.contains(trimmed)) {
                throw new IllegalArgumentException("unknown event type: " + trimmed);
            }
            result.add(trimmed);
        }
        return result;
    }
}
