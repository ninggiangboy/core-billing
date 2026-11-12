package dev.ngb.billing.webhook.application.endpoint.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections of the current tenant's endpoints. */
public interface EndpointReadRepository {

    /** A null system means the Tenant User's view: every endpoint of the tenant. */
    List<WebhookEndpointView> list(UUID tenantSystemId);

    Optional<WebhookEndpointView> findById(UUID id, UUID tenantSystemId);
}
