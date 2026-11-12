package dev.ngb.billing.platform.application.tenant.command;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.platform.api.audit.AuditEntry;
import dev.ngb.billing.platform.api.audit.AuditLog;
import dev.ngb.billing.platform.application.tenant.exception.TenantNotFoundException;
import dev.ngb.billing.platform.application.tenant.port.TenantDomainRepository;
import dev.ngb.billing.platform.application.tenant.port.TenantRepository;
import dev.ngb.billing.platform.domain.tenant.DomainKind;
import dev.ngb.billing.platform.domain.tenant.Hostname;
import dev.ngb.billing.platform.domain.tenant.TenantDomain;

/** Maps an additional hostname to a Tenant. Runs at Platform scope. */
public class AddTenantDomainUseCase {

    public record Command(TenantId tenantId, String hostname, String actor, String requestId) {}

    private final UnitOfWork unitOfWork;
    private final TenantRepository tenants;
    private final TenantDomainRepository domains;
    private final AuditLog auditLog;
    private final IdGenerator ids;
    private final Clock clock;

    public AddTenantDomainUseCase(
            UnitOfWork unitOfWork,
            TenantRepository tenants,
            TenantDomainRepository domains,
            AuditLog auditLog,
            IdGenerator ids,
            Clock clock) {
        this.unitOfWork = unitOfWork;
        this.tenants = tenants;
        this.domains = domains;
        this.auditLog = auditLog;
        this.ids = ids;
        this.clock = clock;
    }

    public TenantDomain execute(Command command) {
        return unitOfWork.transactional(() -> {
            tenants.findById(command.tenantId())
                    .orElseThrow(() -> new TenantNotFoundException(command.tenantId().toString()));
            Hostname hostname = Hostname.fromHostHeader(command.hostname());
            TenantDomain domain = TenantDomain.forTenant(
                    ids.newId(), command.tenantId(), hostname, DomainKind.TENANT_SUBDOMAIN, clock.now());
            domains.add(domain);
            auditLog.record(AuditEntry.platform(
                    command.actor(),
                    "tenant.domain-added",
                    "tenant/" + command.tenantId() + "/domain/" + hostname,
                    command.requestId(),
                    clock.now()));
            return domain;
        });
    }
}
