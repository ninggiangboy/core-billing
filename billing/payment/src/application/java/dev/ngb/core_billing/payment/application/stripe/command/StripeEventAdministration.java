package dev.ngb.core_billing.payment.application.stripe.command;

import dev.ngb.core_billing.payment.application.incident.port.IncidentRepository;
import dev.ngb.core_billing.payment.application.shared.command.Audit;
import dev.ngb.core_billing.payment.application.stripe.port.StripeEventRepository;
import dev.ngb.core_billing.payment.domain.stripe.StoredEventId;
import dev.ngb.core_billing.payment.domain.stripe.StripeEvent;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.time.Instant;
import java.util.UUID;

/** The retry button for an event that gave up, and closing an incident. The history is read through queries. */
public class StripeEventAdministration {

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

    /** Puts a failed or dead event back in line and tries it now. */
    public void retry(Audit audit, StoredEventId id) {
        unitOfWork.transactional(() -> {
            StripeEvent event = events.lockById(id).orElseThrow(() -> new NotFoundException("stripe event not found"));
            Instant now = clock.now();
            event.reopen(now);
            events.update(event);
            auditLog.record(AuditEntry.tenant(tenantContext.requireTenant(), audit.actor(), "stripe-event.retried", "stripe-event/" + id,
                    audit.requestId(), now));
        });
        processor.attempt(id);
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
