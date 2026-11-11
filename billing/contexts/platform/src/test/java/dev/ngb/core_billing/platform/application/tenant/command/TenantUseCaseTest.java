package dev.ngb.core_billing.platform.application.tenant.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.actor.RequestScope;
import dev.ngb.core_billing.platform.application.tenant.exception.HostnameAlreadyMappedException;
import dev.ngb.core_billing.platform.application.tenant.exception.TenantCodeAlreadyUsedException;
import dev.ngb.core_billing.platform.application.tenant.port.TenantDomainRepository;
import dev.ngb.core_billing.platform.application.tenant.exception.TenantNotFoundException;
import dev.ngb.core_billing.platform.application.tenant.port.TenantRepository;
import dev.ngb.core_billing.platform.domain.tenant.Hostname;
import dev.ngb.core_billing.platform.domain.tenant.Tenant;
import dev.ngb.core_billing.platform.domain.tenant.TenantCode;
import dev.ngb.core_billing.platform.domain.tenant.TenantDomain;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.runtime.IdGenerator;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Use cases run with in-memory ports and no Spring context. */
class TenantUseCaseTest {

    private final Map<TenantId, Tenant> tenantStore = new HashMap<>();
    private final Map<Hostname, TenantDomain> domainStore = new HashMap<>();
    private final List<AuditEntry> audit = new ArrayList<>();
    private int transactions;

    private final UnitOfWork unitOfWork = new UnitOfWork() {
        @Override
        public <T> T transactional(Supplier<T> work) {
            transactions++;
            return work.get();
        }
    };
    private final TenantRepository tenants = new TenantRepository() {
        @Override
        public void add(Tenant tenant) {
            tenantStore.put(tenant.id(), tenant);
        }

        @Override
        public void update(Tenant tenant) {
            tenantStore.put(tenant.id(), tenant);
        }

        @Override
        public Optional<Tenant> findById(TenantId id) {
            return Optional.ofNullable(tenantStore.get(id));
        }

        @Override
        public Optional<Tenant> findByCode(TenantCode code) {
            return tenantStore.values().stream().filter(t -> t.code().equals(code)).findFirst();
        }
    };
    private final TenantDomainRepository domains = new TenantDomainRepository() {
        @Override
        public void add(TenantDomain domain) {
            if (domainStore.containsKey(domain.hostname())) {
                throw new HostnameAlreadyMappedException(domain.hostname().value());
            }
            domainStore.put(domain.hostname(), domain);
        }

        @Override
        public Optional<TenantDomain> findByHostname(Hostname hostname) {
            return Optional.ofNullable(domainStore.get(hostname));
        }

        @Override
        public List<TenantDomain> findByTenant(TenantId tenantId) {
            return domainStore.values().stream().filter(d -> d.tenantId().equals(Optional.of(tenantId))).toList();
        }
    };
    private final AuditLog auditLog = audit::add;
    private final IdGenerator ids = UUID::randomUUID;
    private final Clock clock = () -> Instant.parse("2026-11-10T00:00:00Z");

    private final CreateTenantUseCase create =
            new CreateTenantUseCase(unitOfWork, tenants, domains, auditLog, ids, clock, "billing.test");
    private final DeactivateTenantUseCase deactivate = new DeactivateTenantUseCase(unitOfWork, tenants, auditLog, clock);
    private final ResolveHostUseCase resolve = new ResolveHostUseCase(unitOfWork, domains, tenants);

    @Test
    void creatingATenantMapsItsSubdomainAndAudits() {
        Tenant tenant = create.execute(new CreateTenantUseCase.Command("acme", "Acme", "admin", "req-1"));

        assertThat(domainStore).containsKey(new Hostname("acme.billing.test"));
        assertThat(audit).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo("tenant.created");
            assertThat(entry.tenantId()).isEqualTo(RequestScope.PLATFORM_TENANT_ID);
            assertThat(entry.target()).isEqualTo("tenant/" + tenant.id());
        });
        assertThat(transactions).isEqualTo(1);
    }

    @Test
    void aTakenCodeIsRejected() {
        create.execute(new CreateTenantUseCase.Command("acme", "Acme", "admin", "req-1"));
        assertThatThrownBy(() -> create.execute(new CreateTenantUseCase.Command("acme", "Other", "admin", "req-2")))
                .isInstanceOf(TenantCodeAlreadyUsedException.class);
    }

    @Test
    void resolvesPlatformTenantAndNothingElse() {
        Tenant tenant = create.execute(new CreateTenantUseCase.Command("acme", "Acme", "admin", "req-1"));
        domains.add(TenantDomain.platform(UUID.randomUUID(), new Hostname("platform.billing.test"), clock.now()));

        assertThat(resolve.execute("platform.billing.test:8080")).contains(RequestScope.platform());
        assertThat(resolve.execute("ACME.billing.test")).contains(RequestScope.tenant(tenant.id()));
        assertThat(resolve.execute("other.billing.test")).isEmpty();
        assertThat(resolve.execute("not a host")).isEmpty();
    }

    @Test
    void aDeactivatedTenantNoLongerResolves() {
        Tenant tenant = create.execute(new CreateTenantUseCase.Command("acme", "Acme", "admin", "req-1"));
        deactivate.execute(new DeactivateTenantUseCase.Command(tenant.id(), "admin", "req-2"));

        assertThat(resolve.execute("acme.billing.test")).isEmpty();
        assertThat(audit).extracting(AuditEntry::action).containsExactly("tenant.created", "tenant.deactivated");
    }

    @Test
    void deactivatingAnUnknownTenantFails() {
        assertThatThrownBy(() -> deactivate.execute(new DeactivateTenantUseCase.Command(new TenantId(UUID.randomUUID()), "a", "r")))
                .isInstanceOf(TenantNotFoundException.class);
    }
}
