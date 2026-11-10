package io.corebilling.webhook.application.endpoint.command;

import io.corebilling.webhook.domain.endpoint.WebhookEndpoint;
import io.corebilling.webhook.domain.endpoint.WebhookUrl;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import io.corebilling.webhook.application.shared.port.EventTypeCatalog;
import io.corebilling.webhook.application.shared.port.WebhookSettings;

/** Validation shared by creating and changing an endpoint. */
final class EndpointRules {

    private EndpointRules() {}

    static String url(String raw, WebhookSettings settings) {
        return WebhookUrl.parse(raw, settings.allowInsecureTargets());
    }

    static Set<String> eventTypes(List<String> requested, EventTypeCatalog catalog) {
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
