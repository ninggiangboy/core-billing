package io.corebilling.payment.application.stripe.command;

import io.corebilling.payment.domain.stripe.EventAttempt;
import io.corebilling.payment.domain.stripe.EventStatus;
import io.corebilling.payment.domain.incident.Incident;
import io.corebilling.payment.domain.stripe.StoredEventId;
import io.corebilling.payment.domain.stripe.StripeEvent;
import io.corebilling.platform.api.audit.AuditEntry;
import io.corebilling.platform.api.audit.AuditLog;
import io.corebilling.platform.api.error.NotFoundException;
import io.corebilling.platform.api.tenant.TenantContext;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.UnitOfWork;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.corebilling.payment.application.incident.port.IncidentRepository;
import io.corebilling.payment.application.shared.command.Audit;
import io.corebilling.payment.application.stripe.port.StripeEventRepository;

/** What an administrator sees of Stripe webhook processing, and the retry button for an event that gave up. */
public class StripeEventAdministration {

    public record Detail(StripeEvent event, List<EventAttempt> attempts) {}

    private final UnitOfWork unitOfWork;
    private final StripeEventRepository events;
    private final IncidentRepository incidents;
    private final StripeEventProcessor processor;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final Clock clock;

    public StripeEventAdministration(
            UnitOfWork unitOfWork, StripeEventRepository events, IncidentRepository incidents, StripeEventProcessor processor, AuditLog auditLog,
            TenantContext tenantContext, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.incidents = incidents;
        this.processor = processor;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.clock = clock;
    }

    public List<StripeEvent> list(EventStatus status, StoredEventId before, int limit) {
        return unitOfWork.transactional(() -> events.list(status, before, Math.max(1, Math.min(limit, 200))));
    }

    public Detail get(StoredEventId id) {
        return unitOfWork.transactional(() -> {
            StripeEvent event = events.findById(id).orElseThrow(() -> new NotFoundException("stripe event not found"));
            return new Detail(event, events.attempts(id));
        });
    }

    /** Puts a failed or dead event back in line and tries it now. */
    public Detail retry(Audit audit, StoredEventId id) {
        unitOfWork.transactional(() -> {
            StripeEvent event = events.lockById(id).orElseThrow(() -> new NotFoundException("stripe event not found"));
            Instant now = clock.now();
            event.reopen(now);
            events.update(event);
            auditLog.record(AuditEntry.tenant(tenantContext.requireTenant(), audit.actor(), "stripe-event.retried", "stripe-event/" + id,
                    audit.requestId(), now));
        });
        processor.attempt(id);
        return get(id);
    }

    public List<Incident> incidents(boolean openOnly, int limit) {
        return unitOfWork.transactional(() -> incidents.list(openOnly, Math.max(1, Math.min(limit, 200))));
    }

    public void resolveIncident(Audit audit, UUID id) {
        unitOfWork.transactional(() -> {
            Instant now = clock.now();
            if (!incidents.resolve(id, now)) {
                throw new NotFoundException("open incident not found");
            }
            auditLog.record(AuditEntry.tenant(tenantContext.requireTenant(), audit.actor(), "stripe-incident.resolved", "stripe-incident/" + id,
                    audit.requestId(), now));
        });
    }
}
