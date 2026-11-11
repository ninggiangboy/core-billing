package dev.ngb.core_billing.identity;

import dev.ngb.core_billing.platform.api.error.ConflictException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.platform.api.actor.Actor;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.application.auth.Authentication;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.application.tenant.command.ProvisionTenantUseCase;
import dev.ngb.core_billing.platform.application.role.command.RoleAdministration;
import dev.ngb.core_billing.platform.application.role.query.RoleQueries;
import dev.ngb.core_billing.platform.application.role.query.RoleView;
import dev.ngb.core_billing.platform.application.role.command.SeedDefaultRolesUseCase;
import dev.ngb.core_billing.platform.application.tenant.command.TenantMismatchException;
import dev.ngb.core_billing.platform.application.user.command.UserAdministration;
import dev.ngb.core_billing.platform.application.user.query.UserQueries;
import dev.ngb.core_billing.platform.application.user.query.UserView;
import dev.ngb.core_billing.platform.domain.user.BillingUser;
import dev.ngb.core_billing.platform.domain.role.Role;
import dev.ngb.core_billing.platform.domain.role.RoleId;
import dev.ngb.core_billing.platform.domain.tenant.Tenant;
import dev.ngb.core_billing.support.FakeIdentityConfig;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.RecordingIdentityProvider;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import dev.ngb.core_billing.platform.api.actor.PermissionScope;

@SpringBootTest(classes = Application.class)
@Import({PostgresContainerConfig.class, FakeIdentityConfig.class})
class UserAdministrationIntegrationTest {

    private static final Audit CALLER = new Audit("user/admin", "req-1");

    @Autowired UserAdministration userAdmin;
    @Autowired UserQueries userQueries;
    @Autowired RoleAdministration roleAdmin;
    @Autowired RoleQueries roleQueries;
    @Autowired Authentication authentication;
    @Autowired ProvisionTenantUseCase provision;
    @Autowired RecordingIdentityProvider identityProvider;
    @Autowired OwnerDatabase owner;
    @Autowired ScopeTestSupport scopes;

    private String email() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String tenantCode() {
        return "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @Test
    void provisioningATenantSeedsDefaultRolesAndTheFirstAdministrator() {
        String code = tenantCode();
        String adminEmail = email();
        Tenant tenant = scopes.onPlatform(() -> provision.execute(new ProvisionTenantUseCase.Command(
                code, "Acme", adminEmail, "Alice", "Temp-pass-1", "user/root", "req-1")));

        assertThat(scopes.inTenant(tenant.id(), () -> roleQueries.list())).extracting(RoleView::name)
                .containsExactlyInAnyOrder(SeedDefaultRolesUseCase.TENANT_ADMIN, SeedDefaultRolesUseCase.TENANT_VIEWER);
        var identity = identityProvider.created.stream().filter(i -> i.email().equals(adminEmail)).findFirst().orElseThrow();
        assertThat(identity.username()).isEqualTo(code + ":" + adminEmail);
        assertThat(identity.tenantCode()).isEqualTo(code);

        UserView admin = scopes.inTenant(tenant.id(), () -> userQueries.list()).getFirst();
        Actor actor = scopes.inTenant(tenant.id(), () -> authentication.authenticateUser(admin.keycloakSubject(), code)).orElseThrow();
        assertThat(actor.permissions()).contains(Permission.USER_MANAGE, Permission.CREDENTIAL_MANAGE, Permission.CUSTOMER_WRITE);
        assertThat(actor.permissions()).noneMatch(p -> !p.validIn(dev.ngb.core_billing.platform.api.actor.PermissionScope.TENANT));
        assertThat(owner.count("select count(*) from audit_log where tenant_id = ? and action = 'user.created' and actor = 'user/root'",
                        tenant.id().value()))
                .isEqualTo(1);
    }

    @Test
    void thePlatformHasItsOwnDefaultAdminRole() {
        assertThat(scopes.onPlatform(() -> roleQueries.list())).extracting(RoleView::name).contains(SeedDefaultRolesUseCase.PLATFORM_ADMIN);
        RoleView platformAdmin = scopes.onPlatform(() -> roleQueries.list()).stream()
                .filter(r -> r.name().equals(SeedDefaultRolesUseCase.PLATFORM_ADMIN)).findFirst().orElseThrow();
        assertThat(platformAdmin.permissions()).contains(Permission.TENANT_CREATE, Permission.USER_MANAGE)
                .doesNotContain(Permission.CREDENTIAL_MANAGE, Permission.CUSTOMER_READ);
    }

    @Test
    void aTokenFromAnotherTenantIsAMismatchNotASilentMiss() {
        Tenant a = scopes.newTenant();
        Tenant b = scopes.newTenant();
        assertThatThrownBy(() -> scopes.inTenant(b.id(), () -> authentication.authenticateUser("any-subject", a.code().value())))
                .isInstanceOf(TenantMismatchException.class);
        assertThatThrownBy(() -> scopes.inTenant(b.id(), () -> authentication.authenticateUser("any-subject", null)))
                .isInstanceOf(TenantMismatchException.class);
    }

    @Test
    void anUnknownOrDisabledSubjectAuthenticatesAsNobody() {
        Tenant tenant = scopes.newTenant();
        Optional<Actor> unknown = scopes.inTenant(tenant.id(), () -> authentication.authenticateUser("nobody", tenant.code().value()));
        assertThat(unknown).isEmpty();
    }

    @Test
    void theSameEmailInTwoTenantsMakesTwoUsersAndTwoIdentities() {
        Tenant a = scopes.newTenant();
        Tenant b = scopes.newTenant();
        String shared = email();

        BillingUser inA = scopes.inTenant(a.id(), () -> userAdmin.create(CALLER, new UserAdministration.CreateUser(shared, "Sam", null, Set.of())));
        BillingUser inB = scopes.inTenant(b.id(), () -> userAdmin.create(CALLER, new UserAdministration.CreateUser(shared, "Sam", null, Set.of())));

        assertThat(inA.keycloakSubject()).isNotEqualTo(inB.keycloakSubject());
        assertThat(identityProvider.created.stream().filter(i -> i.email().equals(shared)).map(i -> i.username()))
                .containsExactlyInAnyOrder(a.code() + ":" + shared, b.code() + ":" + shared);
    }

    @Test
    void aStorageFailureDeletesTheIdentityItJustCreated() {
        Tenant tenant = scopes.newTenant();
        String subject = "reused-subject-" + UUID.randomUUID();
        identityProvider.returnSubjectOnce(subject);
        scopes.runInTenant(tenant.id(), () -> userAdmin.create(CALLER, new UserAdministration.CreateUser(email(), "First", null, Set.of())));
        // The first user took a fresh subject; make the second call collide on a stored subject.
        UserView first = scopes.inTenant(tenant.id(), () -> userQueries.list()).getFirst();
        identityProvider.returnSubjectOnce(first.keycloakSubject());

        assertThatThrownBy(() -> scopes.runInTenant(tenant.id(),
                        () -> userAdmin.create(CALLER, new UserAdministration.CreateUser(email(), "Second", null, Set.of()))))
                .isInstanceOf(RuntimeException.class);

        assertThat(identityProvider.deleted).contains(first.keycloakSubject());
        assertThat(scopes.inTenant(tenant.id(), () -> userQueries.list())).hasSize(1);
    }

    @Test
    void aDuplicateEmailIsRejectedBeforeAnyIdentityIsCreated() {
        Tenant tenant = scopes.newTenant();
        String email = email();
        scopes.runInTenant(tenant.id(), () -> userAdmin.create(CALLER, new UserAdministration.CreateUser(email, "One", null, Set.of())));
        int createdBefore = identityProvider.created.size();

        assertThatThrownBy(() -> scopes.runInTenant(tenant.id(),
                        () -> userAdmin.create(CALLER, new UserAdministration.CreateUser(email.toUpperCase(), "Two", null, Set.of()))))
                .isInstanceOf(ConflictException.class);
        assertThat(identityProvider.created).hasSize(createdBefore);
    }

    @Test
    void rolesFromAnotherScopeAreRefused() {
        Tenant tenant = scopes.newTenant();
        RoleView platformAdmin = scopes.onPlatform(() -> roleQueries.list()).stream()
                .filter(r -> r.name().equals(SeedDefaultRolesUseCase.PLATFORM_ADMIN)).findFirst().orElseThrow();
        assertThatThrownBy(() -> scopes.runInTenant(tenant.id(),
                        () -> userAdmin.create(CALLER, new UserAdministration.CreateUser(email(), "X", null, Set.of(new RoleId(platformAdmin.id()))))))
                .as("a platform role is invisible inside a tenant")
                .hasMessageContaining("role not found");
    }

    @Test
    void assigningRolesChangesEffectivePermissions() {
        Tenant tenant = scopes.newTenant();
        Role support = scopes.inTenant(tenant.id(), () -> roleAdmin.create(CALLER, "Support", Set.of(Permission.USER_READ)));
        BillingUser user = scopes.inTenant(tenant.id(), () -> userAdmin.create(CALLER, new UserAdministration.CreateUser(email(), "Una", null, Set.of())));

        Actor before = scopes.inTenant(tenant.id(), () -> authentication.authenticateUser(user.keycloakSubject(), tenant.code().value())).orElseThrow();
        assertThat(before.permissions()).isEmpty();

        scopes.runInTenant(tenant.id(), () -> userAdmin.assignRole(CALLER, user.id(), support.id()));
        Actor after = scopes.inTenant(tenant.id(), () -> authentication.authenticateUser(user.keycloakSubject(), tenant.code().value())).orElseThrow();
        assertThat(after.permissions()).containsExactly(Permission.USER_READ);

        scopes.runInTenant(tenant.id(), () -> userAdmin.unassignRole(CALLER, user.id(), support.id()));
        Actor removed = scopes.inTenant(tenant.id(), () -> authentication.authenticateUser(user.keycloakSubject(), tenant.code().value())).orElseThrow();
        assertThat(removed.permissions()).isEmpty();
    }
}
