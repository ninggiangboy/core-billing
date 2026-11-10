package dev.ngb.core_billing.webhook.application.endpoint.port;

import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpoint;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpointId;
import java.util.List;
import java.util.Optional;

/** Endpoints of the current tenant. */
public interface WebhookEndpointRepository {

    void add(WebhookEndpoint endpoint);

    void update(WebhookEndpoint endpoint);

    Optional<WebhookEndpoint> findById(WebhookEndpointId id);

    /** Active endpoints that want {@code eventType}, directly or through the all-events subscription. */
    List<WebhookEndpoint> findSubscribed(String eventType);
}
