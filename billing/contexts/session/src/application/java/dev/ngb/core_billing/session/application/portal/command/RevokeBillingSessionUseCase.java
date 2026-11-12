package dev.ngb.core_billing.session.application.portal.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.session.application.portal.port.BillingSessionRepository;
import dev.ngb.core_billing.session.domain.portal.BillingSession;
import dev.ngb.core_billing.session.domain.portal.BillingSessionId;
import java.time.Instant;
import java.util.UUID;

/** Revokes a session immediately. A Tenant System may revoke only the sessions it issued; a Tenant User may revoke any. */
public class RevokeBillingSessionUseCase {

    private final UnitOfWork unitOfWork;
    private final BillingSessionRepository sessions;
    private final AuditLog auditLog;
    private final Clock clock;

    public RevokeBillingSessionUseCase(UnitOfWork unitOfWork, BillingSessionRepository sessions, AuditLog auditLog, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.sessions = sessions;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    public BillingSession execute(BillingSessionId id, UUID tenantSystemId, Audit audit) {
        return unitOfWork.transactional(() -> {
            BillingSession session = sessions.findById(id).orElseThrow(() -> new NotFoundException("billing session not found"));
            if (tenantSystemId != null && !session.tenantSystemId().value().equals(tenantSystemId)) {
                throw new NotFoundException("billing session not found");
            }
            Instant now = clock.now();
            session.revoke(now);
            sessions.update(session);
            auditLog.record(AuditEntry.tenant(session.tenantId(), audit.actor(), "session.revoked",
                    "billing-session/" + session.id(), audit.requestId(), now));
            return session;
        });
    }
}
