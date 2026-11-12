package dev.ngb.billing.webhook.application.endpoint.command;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core.security.SecretCipher;
import dev.ngb.billing.platform.api.audit.Audit;
import dev.ngb.billing.platform.api.audit.AuditEntry;
import dev.ngb.billing.platform.api.audit.AuditLog;
import dev.ngb.billing.platform.api.error.NotFoundException;
import dev.ngb.billing.platform.api.tenant.TenantContext;
import dev.ngb.billing.platform.api.tenant.TenantSystemId;
import dev.ngb.billing.webhook.application.endpoint.port.EventTypeCatalog;
import dev.ngb.billing.webhook.application.endpoint.port.SigningKeys;
import dev.ngb.billing.webhook.application.endpoint.port.WebhookEndpointRepository;
import dev.ngb.billing.webhook.application.endpoint.service.EndpointRules;
import dev.ngb.billing.webhook.application.endpoint.service.WebhookSettings;
import dev.ngb.billing.webhook.domain.endpoint.EndpointStatus;
import dev.ngb.billing.webhook.domain.endpoint.WebhookEndpoint;
import dev.ngb.billing.webhook.domain.endpoint.WebhookEndpointId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reads and changes the endpoints of the current tenant. */
public class ManageEndpointUseCase {

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

    public ManageEndpointUseCase(
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

    public WebhookEndpoint update(Audit audit, WebhookEndpointId id, Change change) {
        return update(audit, id, change, null);
    }

    public WebhookEndpoint update(Audit audit, WebhookEndpointId id, Change change, UUID caller) {
        String url = change.url() == null ? null : EndpointRules.url(change.url(), settings);
        var types = change.eventTypes() == null ? null : EndpointRules.eventTypes(change.eventTypes(), catalog);
        return unitOfWork.transactional(() -> {
            WebhookEndpoint endpoint = requireVisible(id, caller);
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
        return rotateSecret(audit, id, null);
    }

    public Rotated rotateSecret(Audit audit, WebhookEndpointId id, UUID caller) {
        return unitOfWork.transactional(() -> {
            WebhookEndpoint endpoint = requireVisible(id, caller);
            Instant now = clock.now();
            SigningKeys.Key key = keys.generate();
            endpoint.rotateSecret(key.keyId(), cipher.encrypt(key.secret(), id.toString()), now);
            endpoints.update(endpoint);
            record(audit, "webhook-endpoint.secret-rotated", id, now);
            return new Rotated(endpoint, key.secret());
        });
    }

    private WebhookEndpoint requireVisible(WebhookEndpointId id, UUID caller) {
        WebhookEndpoint endpoint = endpoints.findById(id).orElseThrow(() -> new NotFoundException("webhook endpoint not found"));
        if (caller != null && (endpoint.tenantSystemId() == null || !endpoint.tenantSystemId().equals(new TenantSystemId(caller)))) {
            throw new NotFoundException("webhook endpoint not found");
        }
        return endpoint;
    }

    private void record(Audit audit, String action, WebhookEndpointId id, Instant now) {
        TenantId tenantId = tenantContext.requireTenant();
        auditLog.record(AuditEntry.tenant(tenantId, audit.actor(), action, "webhook-endpoint/" + id, audit.requestId(), now));
    }
}
