package dev.ngb.core_billing.platform.application.tenant.command;

import dev.ngb.core_billing.platform.api.actor.RequestScope;
import dev.ngb.core_billing.platform.domain.tenant.Hostname;
import dev.ngb.core_billing.platform.domain.tenant.Tenant;
import dev.ngb.core_billing.platform.domain.tenant.TenantDomain;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.util.Optional;
import dev.ngb.core_billing.platform.application.tenant.port.TenantDomainRepository;
import dev.ngb.core_billing.platform.application.tenant.port.TenantRepository;

/**
 * Turns a Host header into a {@link RequestScope}. Anything unknown, malformed or belonging to a
 * deactivated Tenant resolves to nothing, so callers can answer with one generic error.
 */
public class ResolveHostUseCase {

    private final UnitOfWork unitOfWork;
    private final TenantDomainRepository domains;
    private final TenantRepository tenants;

    public ResolveHostUseCase(UnitOfWork unitOfWork, TenantDomainRepository domains, TenantRepository tenants) {
        this.unitOfWork = unitOfWork;
        this.domains = domains;
        this.tenants = tenants;
    }

    public Optional<RequestScope> execute(String hostHeader) {
        Hostname hostname;
        try {
            hostname = Hostname.fromHostHeader(hostHeader);
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        return unitOfWork.transactional(() -> domains.findByHostname(hostname).flatMap(this::scopeOf));
    }

    private Optional<RequestScope> scopeOf(TenantDomain domain) {
        return domain.tenantId()
                .map(tenantId -> tenants.findById(tenantId)
                        .filter(Tenant::isActive)
                        .map(tenant -> RequestScope.tenant(tenant.id())))
                .orElseGet(() -> Optional.of(RequestScope.platform()));
    }
}
