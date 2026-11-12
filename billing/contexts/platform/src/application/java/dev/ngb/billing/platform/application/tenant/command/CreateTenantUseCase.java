package dev.ngb.billing.platform.application.tenant.command;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.platform.api.audit.AuditEntry;
import dev.ngb.billing.platform.api.audit.AuditLog;
import dev.ngb.billing.platform.application.tenant.exception.TenantCodeAlreadyUsedException;
import dev.ngb.billing.platform.application.tenant.port.TenantDomainRepository;
import dev.ngb.billing.platform.application.tenant.port.TenantRepository;
import dev.ngb.billing.platform.domain.tenant.DomainKind;
import dev.ngb.billing.platform.domain.tenant.Hostname;
import dev.ngb.billing.platform.domain.tenant.Tenant;
import dev.ngb.billing.platform.domain.tenant.TenantCode;
import dev.ngb.billing.platform.domain.tenant.TenantDomain;
import java.time.Instant;

/** Creates a Tenant and its default subdomain in one transaction. Runs at Platform scope. */
public class CreateTenantUseCase {

    public record Command(String code, String name, String actor, String requestId) {}

    private final UnitOfWork unitOfWork;
    private final TenantRepository tenants;
    private final TenantDomainRepository domains;
    private final AuditLog auditLog;
    private final IdGenerator ids;
    private final Clock clock;
    private final String baseDomain;

    public CreateTenantUseCase(
            UnitOfWork unitOfWork,
            TenantRepository tenants,
            TenantDomainRepository domains,
            AuditLog auditLog,
            IdGenerator ids,
            Clock clock,
            String baseDomain) {
        this.unitOfWork = unitOfWork;
        this.tenants = tenants;
        this.domains = domains;
        this.auditLog = auditLog;
        this.ids = ids;
        this.clock = clock;
        this.baseDomain = baseDomain;
    }

    public Tenant execute(Command command) {
        return unitOfWork.transactional(() -> {
            TenantCode code = TenantCode.forNewTenant(command.code());
            if (tenants.findByCode(code).isPresent()) {
                throw new TenantCodeAlreadyUsedException(code.value());
            }
            Instant now = clock.now();
            Tenant tenant = Tenant.create(new TenantId(ids.newId()), code, command.name(), now);
            tenants.add(tenant);
            domains.add(TenantDomain.forTenant(
                    ids.newId(),
                    tenant.id(),
                    new Hostname(code.value() + "." + baseDomain),
                    DomainKind.TENANT_SUBDOMAIN,
                    now));
            auditLog.record(AuditEntry.platform(
                    command.actor(), "tenant.created", "tenant/" + tenant.id(), command.requestId(), now));
            return tenant;
        });
    }
}
