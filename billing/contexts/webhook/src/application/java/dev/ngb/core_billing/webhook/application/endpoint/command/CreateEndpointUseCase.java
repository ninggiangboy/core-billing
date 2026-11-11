package dev.ngb.core_billing.webhook.application.endpoint.command;

import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpoint;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpointId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import dev.ngb.core_billing.webhook.application.endpoint.port.WebhookEndpointRepository;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.webhook.application.endpoint.port.EventTypeCatalog;
import dev.ngb.core_billing.sharedkernel.SecretCipher;
import dev.ngb.core_billing.webhook.application.endpoint.port.SigningKeys;
import dev.ngb.core_billing.webhook.application.endpoint.service.WebhookSettings;
import dev.ngb.core_billing.webhook.application.endpoint.service.EndpointRules;

/** Registers a destination and generates its signing secret, which is shown to the caller exactly once. */
public class CreateEndpointUseCase {

    public record Command(String url, String description, List<String> eventTypes, Audit audit, UUID caller) {
        public Command(String url, String description, List<String> eventTypes, Audit audit) {
            this(url, description, eventTypes, audit, null);
        }
    }

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
                    id, tenantId, command.caller() == null ? null : new TenantSystemId(command.caller()), url, command.description(), types,
                    key.keyId(), cipher.encrypt(key.secret(), id.toString()), now);
            endpoints.add(endpoint);
            auditLog.record(AuditEntry.tenant(tenantId, command.audit().actor(), "webhook-endpoint.created", "webhook-endpoint/" + id,
                    command.audit().requestId(), now));
            return new Result(endpoint, key.secret());
        });
    }
}
