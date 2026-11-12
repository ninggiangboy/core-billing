package dev.ngb.billing.webhook.application.endpoint.query;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.platform.api.error.NotFoundException;
import java.util.List;
import java.util.UUID;

/** Reads the current tenant's endpoints for the admin GET handlers. */
public class EndpointQueries {

    private final UnitOfWork unitOfWork;
    private final EndpointReadRepository endpoints;

    public EndpointQueries(UnitOfWork unitOfWork, EndpointReadRepository endpoints) {
        this.unitOfWork = unitOfWork;
        this.endpoints = endpoints;
    }

    public List<WebhookEndpointView> list() {
        return list(null);
    }

    public List<WebhookEndpointView> list(UUID caller) {
        return unitOfWork.transactional(() -> endpoints.list(caller));
    }

    public WebhookEndpointView get(UUID id) {
        return get(id, null);
    }

    public WebhookEndpointView get(UUID id, UUID caller) {
        return unitOfWork.transactional(
                () -> endpoints.findById(id, caller).orElseThrow(() -> new NotFoundException("webhook endpoint not found")));
    }
}
