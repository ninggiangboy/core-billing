package dev.ngb.core_billing.webhook.application.delivery.command;

import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.webhook.application.delivery.port.WebhookDeliveryRepository;
import dev.ngb.core_billing.webhook.application.endpoint.port.WebhookEndpointRepository;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.webhook.domain.delivery.WebhookDelivery;
import dev.ngb.core_billing.webhook.domain.delivery.WebhookDeliveryId;
import java.time.Instant;
import java.util.UUID;

/** Changes delivery state: resending a stored event. The history is read through DeliveryQueries. */
public class ManageDeliveryUseCase {

    private final UnitOfWork unitOfWork;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookEndpointRepository endpoints;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public ManageDeliveryUseCase(
            UnitOfWork unitOfWork, WebhookDeliveryRepository deliveries, WebhookEndpointRepository endpoints, AuditLog auditLog,
            TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.deliveries = deliveries;
        this.endpoints = endpoints;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Queues a new delivery of the same stored event and body to the same endpoint, with a fresh attempt budget.
     * The original keeps its history. Only a delivery that has finished can be resent.
     */
    public WebhookDelivery resend(Audit audit, WebhookDeliveryId id) {
        return resend(audit, id, null);
    }

    public WebhookDelivery resend(Audit audit, WebhookDeliveryId id, UUID caller) {
        return unitOfWork.transactional(() -> {
            WebhookDelivery original = deliveries.findById(id).orElseThrow(() -> new NotFoundException("webhook delivery not found"));
            var endpoint = endpoints.findById(original.endpointId()).orElseThrow(() -> new NotFoundException("webhook endpoint not found"));
            if (caller != null && (endpoint.tenantSystemId() == null || !endpoint.tenantSystemId().equals(new TenantSystemId(caller)))) {
                throw new NotFoundException("webhook delivery not found");
            }
            if (!original.isFinished()) {
                throw new ConflictException("this delivery is still being attempted");
            }
            Instant now = clock.now();
            WebhookDeliveryId root = original.resendOf() != null ? original.resendOf() : original.id();
            WebhookDelivery copy = WebhookDelivery.queue(
                    new WebhookDeliveryId(ids.newId()), original.tenantId(), original.endpointId(), original.eventId(), original.eventType(),
                    original.body(), root, now);
            deliveries.add(copy);
            auditLog.record(AuditEntry.tenant(tenantContext.requireTenant(), audit.actor(), "webhook-delivery.resent", "webhook-delivery/" + id,
                    audit.requestId(), now));
            return copy;
        });
    }
}
