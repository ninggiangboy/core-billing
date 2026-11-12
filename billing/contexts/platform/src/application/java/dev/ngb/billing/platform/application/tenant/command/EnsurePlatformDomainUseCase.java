package dev.ngb.billing.platform.application.tenant.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.platform.application.tenant.exception.HostnameAlreadyMappedException;
import dev.ngb.billing.platform.application.tenant.port.TenantDomainRepository;
import dev.ngb.billing.platform.domain.tenant.Hostname;
import dev.ngb.billing.platform.domain.tenant.TenantDomain;

/** Makes sure the Platform Administration hostname is mapped. Safe to run on every start. */
public class EnsurePlatformDomainUseCase {

    private final UnitOfWork unitOfWork;
    private final TenantDomainRepository domains;
    private final IdGenerator ids;
    private final Clock clock;

    public EnsurePlatformDomainUseCase(
            UnitOfWork unitOfWork, TenantDomainRepository domains, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.domains = domains;
        this.ids = ids;
        this.clock = clock;
    }

    public void execute(String hostname) {
        unitOfWork.transactional(() -> {
            Hostname host = new Hostname(hostname);
            if (domains.findByHostname(host).isEmpty()) {
                try {
                    domains.add(TenantDomain.platform(ids.newId(), host, clock.now()));
                } catch (HostnameAlreadyMappedException concurrentStart) {
                    // Another instance mapped it first; the mapping exists, which is all we need.
                }
            }
        });
    }
}
