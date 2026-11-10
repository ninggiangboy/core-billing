package io.corebilling.webhook.application.endpoint.command;

import io.corebilling.platform.api.audit.AuditEntry;
import io.corebilling.platform.api.audit.AuditLog;
import io.corebilling.platform.api.error.NotFoundException;
import io.corebilling.platform.api.tenant.TenantContext;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.webhook.domain.endpoint.EndpointStatus;
import io.corebilling.webhook.domain.endpoint.WebhookEndpoint;
import io.corebilling.webhook.domain.endpoint.WebhookEndpointId;
import java.time.Instant;
import java.util.List;
import io.corebilling.webhook.application.endpoint.port.WebhookEndpointRepository;
import io.corebilling.webhook.application.shared.command.Audit;
import io.corebilling.webhook.application.shared.port.EventTypeCatalog;
import io.corebilling.webhook.application.shared.port.SecretCipher;
import io.corebilling.webhook.application.shared.port.SigningKeys;
import io.corebilling.webhook.application.shared.port.WebhookSettings;

/** Reads and changes the endpoints of the current tenant. */
public class EndpointAdministration {

    /** A change request: a null field is left as it is. */
    public record Change(String url, String description, List<String> eventTypes, EndpointStatus status) {}

    public record Rotated(WebhookEndpoint endpoint, String signingSecret) {}

    private final UnitOfWork unitOfWork;
    private final WebhookEndpointRepository endpoints;
    private final SecretCipher cipher;
    private final SigningKeys keys;
    private final EventTypeCatalog catalog;
    private final WebhookSettings settings;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final Clock clock;

    public EndpointAdministration(
            UnitOfWork unitOfWork, WebhookEndpointRepository endpoints, SecretCipher cipher, SigningKeys keys, EventTypeCatalog catalog,
            WebhookSettings settings, AuditLog auditLog, TenantContext tenantContext, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.endpoints = endpoints;
        this.cipher = cipher;
        this.keys = keys;
        this.catalog = catalog;
        this.settings = settings;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.clock = clock;
    }

    public WebhookEndpoint get(WebhookEndpointId id) {
        return unitOfWork.transactional(() -> endpoints.findById(id).orElseThrow(() -> new NotFoundException("webhook endpoint not found")));
    }

    public List<WebhookEndpoint> list() {
        return unitOfWork.transactional(endpoints::list);
    }

    public WebhookEndpoint update(Audit audit, WebhookEndpointId id, Change change) {
        String url = change.url() == null ? null : EndpointRules.url(change.url(), settings);
        var types = change.eventTypes() == null ? null : EndpointRules.eventTypes(change.eventTypes(), catalog);
        return unitOfWork.transactional(() -> {
            WebhookEndpoint endpoint = endpoints.findById(id).orElseThrow(() -> new NotFoundException("webhook endpoint not found"));
            Instant now = clock.now();
            endpoint.reconfigure(
                    url == null ? endpoint.url() : url,
                    change.description() == null ? endpoint.description() : change.description(),
                    types == null ? endpoint.eventTypes() : types,
                    now);
            if (change.status() != null) {
                endpoint.changeStatus(change.status(), now);
            }
            endpoints.update(endpoint);
            record(audit, "webhook-endpoint.updated", id, now);
            return endpoint;
        });
    }

    /** Replaces the signing secret at once. Deliveries still waiting are signed with the new one. */
    public Rotated rotateSecret(Audit audit, WebhookEndpointId id) {
        return unitOfWork.transactional(() -> {
            WebhookEndpoint endpoint = endpoints.findById(id).orElseThrow(() -> new NotFoundException("webhook endpoint not found"));
            Instant now = clock.now();
            SigningKeys.Key key = keys.generate();
            endpoint.rotateSecret(key.keyId(), cipher.encrypt(key.secret(), id.toString()), now);
            endpoints.update(endpoint);
            record(audit, "webhook-endpoint.secret-rotated", id, now);
            return new Rotated(endpoint, key.secret());
        });
    }

    private void record(Audit audit, String action, WebhookEndpointId id, Instant now) {
        TenantId tenantId = tenantContext.requireTenant();
        auditLog.record(AuditEntry.tenant(tenantId, audit.actor(), action, "webhook-endpoint/" + id, audit.requestId(), now));
    }
}
