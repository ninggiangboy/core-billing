package dev.ngb.core_billing.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.platform.application.tenant.command.CreateTenantUseCase;
import dev.ngb.core_billing.platform.application.tenant.command.DeactivateTenantUseCase;
import dev.ngb.core_billing.platform.application.tenant.command.HostnameAlreadyMappedException;
import dev.ngb.core_billing.platform.application.tenant.command.TenantCodeAlreadyUsedException;
import dev.ngb.core_billing.platform.application.tenant.port.TenantDomainRepository;
import dev.ngb.core_billing.platform.application.tenant.port.TenantRepository;
import dev.ngb.core_billing.platform.domain.tenant.DomainKind;
import dev.ngb.core_billing.platform.domain.tenant.Hostname;
import dev.ngb.core_billing.platform.domain.tenant.Tenant;
import dev.ngb.core_billing.platform.domain.tenant.TenantCode;
import dev.ngb.core_billing.platform.domain.tenant.TenantDomain;
import dev.ngb.core_billing.platform.domain.tenant.TenantStatus;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Reload-and-compare for every repository write, because Spring Data JDBC drops a forgotten change silently. */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class PlatformRepositoryIntegrationTest {

    @Autowired UnitOfWork unitOfWork;
    @Autowired TenantRepository tenants;
    @Autowired TenantDomainRepository domains;
    @Autowired CreateTenantUseCase createTenant;
    @Autowired DeactivateTenantUseCase deactivate;
    @Autowired Clock clock;
    @Autowired IdGenerator ids;
    @Autowired OwnerDatabase owner;
    @Autowired ScopeTestSupport scopes;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    private String uniqueCode() {
        return "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @Test
    void createdTenantReloadsEqualAndGetsItsDefaultSubdomain() {
        String code = uniqueCode();
        Tenant created = scopes.onPlatform(() -> createTenant.execute(new CreateTenantUseCase.Command(code, "  Acme  ", "tester", "req")));

        Tenant reloaded = scopes.onPlatform(() -> unitOfWork.transactional(() -> tenants.findById(created.id()))).orElseThrow();
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(created);
        assertThat(reloaded.name()).isEqualTo("Acme");

        TenantDomain domain = scopes.onPlatform(() -> unitOfWork.transactional(
                        () -> domains.findByHostname(new Hostname(code + "." + baseDomain))))
                .orElseThrow();
        assertThat(domain.kind()).isEqualTo(DomainKind.TENANT_SUBDOMAIN);
        assertThat(domain.tenantId()).contains(created.id());
        assertThat(owner.count("select count(*) from audit_log where action = 'tenant.created' and subject = ?", "tenant/" + created.id()))
                .isEqualTo(1);
    }

    @Test
    void deactivationIsPersisted() {
        Tenant created = scopes.newTenant();
        scopes.onPlatform(() -> deactivate.execute(new DeactivateTenantUseCase.Command(created.id(), "tester", "req")));

        Tenant reloaded = scopes.onPlatform(() -> unitOfWork.transactional(() -> tenants.findById(created.id()))).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(TenantStatus.DEACTIVATED);
        assertThat(reloaded.name()).isEqualTo(created.name());
    }

    @Test
    void duplicateCodeAndHostnameAreRejectedByTheDatabaseConstraint() {
        Tenant created = scopes.newTenant();
        Tenant twin = Tenant.create(new dev.ngb.core_billing.sharedkernel.TenantId(ids.newId()), created.code(), "Twin", clock.now());
        assertThatThrownBy(() -> scopes.runOnPlatform(() -> unitOfWork.transactional(() -> tenants.add(twin))))
                .isInstanceOf(TenantCodeAlreadyUsedException.class);
        assertThatThrownBy(() -> scopes.onPlatform(() -> createTenant.execute(
                        new CreateTenantUseCase.Command(created.code().value(), "Again", "tester", "req"))))
                .isInstanceOf(TenantCodeAlreadyUsedException.class);

        TenantDomain clash = TenantDomain.forTenant(ids.newId(), created.id(), new Hostname(created.code() + "." + baseDomain),
                DomainKind.TENANT_SUBDOMAIN, clock.now());
        assertThatThrownBy(() -> scopes.runOnPlatform(() -> unitOfWork.transactional(() -> domains.add(clash))))
                .isInstanceOf(HostnameAlreadyMappedException.class);
    }

    @Test
    void domainsOfOneTenantAreListed() {
        Tenant created = scopes.newTenant();
        assertThat(scopes.onPlatform(() -> unitOfWork.transactional(() -> domains.findByTenant(created.id()))))
                .extracting(d -> d.hostname().value())
                .containsExactly(created.code() + "." + baseDomain);
        assertThat(new TenantCode(created.code().value())).isEqualTo(created.code());
    }
}
