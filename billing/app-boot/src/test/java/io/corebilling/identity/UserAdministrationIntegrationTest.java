package io.corebilling.identity;

import io.corebilling.platform.api.ConflictException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.corebilling.Application;
import io.corebilling.platform.api.Actor;
import io.corebilling.platform.api.Permission;
import io.corebilling.platform.application.Authentication;
import io.corebilling.platform.application.Caller;
import io.corebilling.platform.application.ProvisionTenantUseCase;
import io.corebilling.platform.application.RoleAdministration;
import io.corebilling.platform.application.SeedDefaultRolesUseCase;
import io.corebilling.platform.application.TenantMismatchException;
import io.corebilling.platform.application.UserAdministration;
import io.corebilling.platform.domain.BillingUser;
import io.corebilling.platform.domain.Role;
import io.corebilling.platform.domain.Tenant;
import io.corebilling.support.FakeIdentityConfig;
import io.corebilling.support.OwnerDatabase;
import io.corebilling.support.PostgresContainerConfig;
import io.corebilling.support.RecordingIdentityProvider;
import io.corebilling.support.ScopeTestSupport;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = Application.class)
@Import({PostgresContainerConfig.class, FakeIdentityConfig.class})
class UserAdministrationIntegrationTest {

    private static final Caller CALLER = new Caller("user/admin", "req-1");

    @Autowired UserAdministration userAdmin;
    @Autowired RoleAdministration roleAdmin;
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

        assertThat(scopes.inTenant(tenant.id(), () -> roleAdmin.list())).extracting(Role::name)
                .containsExactlyInAnyOrder(SeedDefaultRolesUseCase.TENANT_ADMIN, SeedDefaultRolesUseCase.TENANT_VIEWER);
        var identity = identityProvider.created.stream().filter(i -> i.email().equals(adminEmail)).findFirst().orElseThrow();
        assertThat(identity.username()).isEqualTo(code + ":" + adminEmail);
        assertThat(identity.tenantCode()).isEqualTo(code);

        BillingUser admin = scopes.inTenant(tenant.id(), () -> userAdmin.list()).getFirst();
        Actor actor = scopes.inTenant(tenant.id(), () -> authentication.authenticateUser(admin.keycloakSubject(), code)).orElseThrow();
        assertThat(actor.permissions()).contains(Permission.USER_MANAGE, Permission.CREDENTIAL_MANAGE, Permission.CUSTOMER_WRITE);
        assertThat(actor.permissions()).noneMatch(p -> !p.validIn(io.corebilling.platform.api.PermissionScope.TENANT));
        assertThat(owner.count("select count(*) from audit_log where tenant_id = ? and action = 'user.created' and actor = 'user/root'",
                        tenant.id().value()))
                .isEqualTo(1);
    }

    @Test
    void thePlatformHasItsOwnDefaultAdminRole() {
        assertThat(scopes.onPlatform(() -> roleAdmin.list())).extracting(Role::name).contains(SeedDefaultRolesUseCase.PLATFORM_ADMIN);
        Role platformAdmin = scopes.onPlatform(() -> roleAdmin.list()).stream()
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
        BillingUser first = scopes.inTenant(tenant.id(), () -> userAdmin.list()).getFirst();
        identityProvider.returnSubjectOnce(first.keycloakSubject());

        assertThatThrownBy(() -> scopes.runInTenant(tenant.id(),
                        () -> userAdmin.create(CALLER, new UserAdministration.CreateUser(email(), "Second", null, Set.of()))))
                .isInstanceOf(RuntimeException.class);

        assertThat(identityProvider.deleted).contains(first.keycloakSubject());
        assertThat(scopes.inTenant(tenant.id(), () -> userAdmin.list())).hasSize(1);
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
        Role platformAdmin = scopes.onPlatform(() -> roleAdmin.list()).stream()
                .filter(r -> r.name().equals(SeedDefaultRolesUseCase.PLATFORM_ADMIN)).findFirst().orElseThrow();
        assertThatThrownBy(() -> scopes.runInTenant(tenant.id(),
                        () -> userAdmin.create(CALLER, new UserAdministration.CreateUser(email(), "X", null, Set.of(platformAdmin.id())))))
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
