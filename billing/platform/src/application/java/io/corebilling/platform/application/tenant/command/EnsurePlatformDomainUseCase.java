package io.corebilling.platform.application.tenant.command;

import io.corebilling.platform.domain.tenant.Hostname;
import io.corebilling.platform.domain.tenant.TenantDomain;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.platform.application.tenant.port.TenantDomainRepository;

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
