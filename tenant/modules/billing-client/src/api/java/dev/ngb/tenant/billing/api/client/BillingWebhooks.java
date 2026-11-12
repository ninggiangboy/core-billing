package dev.ngb.tenant.billing.api.client;

import java.time.Instant;
import java.util.List;

/** The webhook endpoint the bootstrap registers with the tenant's own credential. */
public interface BillingWebhooks {

    record CreateEndpointRequest(String url, String description, List<String> eventTypes) {}

    record Endpoint(
            String id,
            String url,
            String description,
            List<String> eventTypes,
            String status,
            String keyId,
            Instant createdAt,
            Instant updatedAt,
            Instant secretRotatedAt) {}

    record EndpointWithSecret(Endpoint endpoint, String signingSecret) {}

    List<Endpoint> endpoints();

    EndpointWithSecret createEndpoint(CreateEndpointRequest request);
}
