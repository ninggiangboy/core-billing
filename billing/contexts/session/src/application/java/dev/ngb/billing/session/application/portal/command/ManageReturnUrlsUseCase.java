package dev.ngb.billing.session.application.portal.command;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.platform.api.audit.Audit;
import dev.ngb.billing.platform.api.audit.AuditEntry;
import dev.ngb.billing.platform.api.audit.AuditLog;
import dev.ngb.billing.platform.api.tenant.TenantContext;
import dev.ngb.billing.session.application.portal.port.ReturnUrlRepository;
import dev.ngb.billing.session.application.portal.service.ReturnUrlPolicy;
import java.util.LinkedHashSet;
import java.util.Set;

/** Reads and replaces the Tenant's allowlist of return URLs a Billing Session may point back to. */
public class ManageReturnUrlsUseCase {

    private final UnitOfWork unitOfWork;
    private final ReturnUrlRepository returnUrls;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final Clock clock;

    public ManageReturnUrlsUseCase(UnitOfWork unitOfWork, ReturnUrlRepository returnUrls, AuditLog auditLog, TenantContext tenantContext, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.returnUrls = returnUrls;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.clock = clock;
    }

    public Set<String> list() {
        return unitOfWork.transactional(() -> Set.copyOf(returnUrls.allowed()));
    }

    public Set<String> replace(Set<String> urls, Audit audit) {
        LinkedHashSet<String> canonical = new LinkedHashSet<>();
        for (String url : urls == null ? Set.<String>of() : urls) {
            canonical.add(ReturnUrlPolicy.requireValidEntry(url));
        }
        return unitOfWork.transactional(() -> {
            TenantId tenantId = tenantContext.requireTenant();
            returnUrls.replace(tenantId, canonical);
            auditLog.record(AuditEntry.tenant(tenantId, audit.actor(), "portal.return-urls-updated", "portal/return-urls",
                    audit.requestId(), clock.now()));
            return Set.copyOf(canonical);
        });
    }
}
