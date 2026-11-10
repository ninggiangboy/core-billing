package io.corebilling.webhook.application.endpoint.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections of the current tenant's endpoints. */
public interface EndpointReadRepository {

    List<WebhookEndpointView> list();

    Optional<WebhookEndpointView> findById(UUID id);
}
