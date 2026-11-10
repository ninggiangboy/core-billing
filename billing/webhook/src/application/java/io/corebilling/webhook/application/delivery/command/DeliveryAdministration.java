package io.corebilling.webhook.application.delivery.command;

import io.corebilling.platform.api.AuditEntry;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.webhook.domain.delivery.DeliveryAttempt;
import io.corebilling.webhook.domain.delivery.DeliveryStatus;
import io.corebilling.webhook.domain.delivery.WebhookDelivery;
import io.corebilling.webhook.domain.delivery.WebhookDeliveryId;
import io.corebilling.webhook.domain.endpoint.WebhookEndpointId;
import java.time.Instant;
import java.util.List;
import io.corebilling.webhook.application.delivery.port.WebhookDeliveryRepository;
import io.corebilling.webhook.application.endpoint.port.WebhookEndpointRepository;
import io.corebilling.webhook.application.shared.command.Audit;

/** Delivery history of the current tenant, and resending a stored event. */
public class DeliveryAdministration {

    public static final int MAX_PAGE = 200;

    public record Detail(WebhookDelivery delivery, List<DeliveryAttempt> attempts) {}

    private final UnitOfWork unitOfWork;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookEndpointRepository endpoints;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public DeliveryAdministration(
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

    /** Newest first. {@code status}, {@code endpointId} and {@code before} (the last id of the previous page) are optional filters. */
    public List<WebhookDelivery> list(DeliveryStatus status, WebhookEndpointId endpointId, WebhookDeliveryId before, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        return unitOfWork.transactional(() -> deliveries.list(status, endpointId, before, Math.min(limit, MAX_PAGE)));
    }

    public Detail get(WebhookDeliveryId id) {
        return unitOfWork.transactional(() -> {
            WebhookDelivery delivery = deliveries.findById(id).orElseThrow(() -> new NotFoundException("webhook delivery not found"));
            return new Detail(delivery, deliveries.attempts(id));
        });
    }

    /**
     * Queues a new delivery of the same stored event and body to the same endpoint, with a fresh attempt budget.
     * The original keeps its history. Only a delivery that has finished can be resent.
     */
    public WebhookDelivery resend(Audit audit, WebhookDeliveryId id) {
        return unitOfWork.transactional(() -> {
            WebhookDelivery original = deliveries.findById(id).orElseThrow(() -> new NotFoundException("webhook delivery not found"));
            if (!original.isFinished()) {
                throw new ConflictException("this delivery is still being attempted");
            }
            endpoints.findById(original.endpointId()).orElseThrow(() -> new NotFoundException("webhook endpoint not found"));
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
