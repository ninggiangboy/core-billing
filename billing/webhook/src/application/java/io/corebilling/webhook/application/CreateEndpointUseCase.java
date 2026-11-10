package io.corebilling.webhook.application;

import io.corebilling.platform.api.AuditEntry;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.webhook.domain.WebhookEndpoint;
import io.corebilling.webhook.domain.WebhookEndpointId;
import java.time.Instant;
import java.util.List;

/** Registers a destination and generates its signing secret, which is shown to the caller exactly once. */
public class CreateEndpointUseCase {

    public record Command(String url, String description, List<String> eventTypes, Audit audit) {}

    /** {@code signingSecret} is the only time the plaintext exists outside the delivery worker. */
    public record Result(WebhookEndpoint endpoint, String signingSecret) {}

    private final UnitOfWork unitOfWork;
    private final WebhookEndpointRepository endpoints;
    private final SecretCipher cipher;
    private final SigningKeys keys;
    private final EventTypeCatalog catalog;
    private final WebhookSettings settings;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateEndpointUseCase(
            UnitOfWork unitOfWork, WebhookEndpointRepository endpoints, SecretCipher cipher, SigningKeys keys, EventTypeCatalog catalog,
            WebhookSettings settings, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.endpoints = endpoints;
        this.cipher = cipher;
        this.keys = keys;
        this.catalog = catalog;
        this.settings = settings;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public Result execute(Command command) {
        String url = EndpointRules.url(command.url(), settings);
        var types = EndpointRules.eventTypes(command.eventTypes(), catalog);
        return unitOfWork.transactional(() -> {
            TenantId tenantId = tenantContext.requireTenant();
            Instant now = clock.now();
            WebhookEndpointId id = new WebhookEndpointId(ids.newId());
            SigningKeys.Key key = keys.generate();
            WebhookEndpoint endpoint = WebhookEndpoint.create(
                    id, tenantId, url, command.description(), types, key.keyId(), cipher.encrypt(key.secret(), id.toString()), now);
            endpoints.add(endpoint);
            auditLog.record(AuditEntry.tenant(tenantId, command.audit().actor(), "webhook-endpoint.created", "webhook-endpoint/" + id,
                    command.audit().requestId(), now));
            return new Result(endpoint, key.secret());
        });
    }
}
