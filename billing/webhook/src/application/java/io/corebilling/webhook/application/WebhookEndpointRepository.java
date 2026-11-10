package io.corebilling.webhook.application;

import io.corebilling.webhook.domain.WebhookEndpoint;
import io.corebilling.webhook.domain.WebhookEndpointId;
import java.util.List;
import java.util.Optional;

/** Endpoints of the current tenant. */
public interface WebhookEndpointRepository {

    void add(WebhookEndpoint endpoint);

    void update(WebhookEndpoint endpoint);

    Optional<WebhookEndpoint> findById(WebhookEndpointId id);

    List<WebhookEndpoint> list();

    /** Active endpoints that want {@code eventType}, directly or through the all-events subscription. */
    List<WebhookEndpoint> findSubscribed(String eventType);
}
