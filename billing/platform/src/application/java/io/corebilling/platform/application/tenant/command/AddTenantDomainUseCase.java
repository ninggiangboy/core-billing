package io.corebilling.platform.application.tenant.command;

import io.corebilling.platform.api.audit.AuditEntry;
import io.corebilling.platform.api.audit.AuditLog;
import io.corebilling.platform.domain.tenant.DomainKind;
import io.corebilling.platform.domain.tenant.Hostname;
import io.corebilling.platform.domain.tenant.TenantDomain;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.platform.application.tenant.port.TenantDomainRepository;
import io.corebilling.platform.application.tenant.port.TenantRepository;

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
