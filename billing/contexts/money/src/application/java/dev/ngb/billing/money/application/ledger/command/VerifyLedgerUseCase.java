package dev.ngb.billing.money.application.ledger.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.money.application.ledger.port.LedgerVerifier;
import dev.ngb.billing.money.application.messaging.service.MoneyActors;
import dev.ngb.billing.platform.api.audit.AuditEntry;
import dev.ngb.billing.platform.api.audit.AuditLog;
import dev.ngb.billing.platform.api.tenant.TenantContext;
import java.util.List;

/**
 * Recomputes each wallet's balance and sequence from its ledger entries and compares them with the stored
 * projection, in the current tenant. It reports and audits every disagreement and never rewrites the projection:
 * deciding which side is right is a human's job.
 */
public class VerifyLedgerUseCase {

    private final UnitOfWork unitOfWork;
    private final LedgerVerifier verifier;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final Clock clock;

    public VerifyLedgerUseCase(UnitOfWork unitOfWork, LedgerVerifier verifier, AuditLog auditLog, TenantContext tenantContext, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.verifier = verifier;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.clock = clock;
    }

    public List<LedgerVerifier.Mismatch> execute() {
        return unitOfWork.transactional(() -> {
            List<LedgerVerifier.Mismatch> mismatches = verifier.findMismatches();
            for (LedgerVerifier.Mismatch mismatch : mismatches) {
                auditLog.record(AuditEntry.tenant(tenantContext.requireTenant(), MoneyActors.LEDGER_VERIFY, "ledger.mismatch", "wallet/" + mismatch.walletId(), null, clock.now()));
            }
            return mismatches;
        });
    }
}
