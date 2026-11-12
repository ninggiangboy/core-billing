package dev.ngb.billing.platform;

import dev.ngb.core.runtime.Clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.Application;
import dev.ngb.billing.platform.api.actor.Permission;
import dev.ngb.billing.platform.api.actor.PermissionScope;
import dev.ngb.billing.platform.api.error.ConflictException;
import dev.ngb.billing.platform.api.tenant.TenantSystemId;
import dev.ngb.billing.platform.application.credential.port.CredentialRepository;
import dev.ngb.billing.platform.application.role.port.RoleRepository;
import dev.ngb.billing.platform.application.tenantsystem.port.TenantSystemRepository;
import dev.ngb.billing.platform.application.user.port.UserRepository;
import dev.ngb.billing.platform.application.user.query.UserQueries;
import dev.ngb.billing.platform.application.user.query.UserView;
import dev.ngb.billing.platform.domain.credential.ApiCredential;
import dev.ngb.billing.platform.domain.credential.ApiCredentialId;
import dev.ngb.billing.platform.domain.credential.ApiKey;
import dev.ngb.billing.platform.domain.role.Role;
import dev.ngb.billing.platform.domain.role.RoleId;
import dev.ngb.billing.platform.domain.tenant.Tenant;
import dev.ngb.billing.platform.domain.tenantsystem.TenantSystem;
import dev.ngb.billing.platform.domain.user.BillingUser;
import dev.ngb.billing.platform.domain.user.BillingUserId;
import dev.ngb.billing.support.PostgresContainerConfig;
import dev.ngb.billing.support.ScopeTestSupport;
import java.security.SecureRandom;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Reload-and-compare for every identity repository write. */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class IdentityRepositoryIntegrationTest {

    @Autowired UnitOfWork unitOfWork;
    @Autowired UserRepository users;
    @Autowired UserQueries userQueries;
    @Autowired RoleRepository roles;
    @Autowired TenantSystemRepository systems;
    @Autowired CredentialRepository credentials;
    @Autowired Clock clock;
    @Autowired ScopeTestSupport scopes;

    private Role newRole(Tenant tenant, String name, Set<Permission> permissions) {
        return Role.create(new RoleId(UUID.randomUUID()), tenant.id(), PermissionScope.TENANT, name, false, permissions, clock.now());
    }

    @Test
    void rolesRoundTripAndUpdatesPersist() {
        Tenant tenant = scopes.newTenant();
        Role role = newRole(tenant, "Support", Set.of(Permission.USER_READ, Permission.ROLE_READ));
        scopes.runInTenant(tenant.id(), () -> unitOfWork.transactional(() -> roles.add(role)));

        Role reloaded = scopes.inTenant(tenant.id(), () -> unitOfWork.transactional(() -> roles.findById(role.id()))).orElseThrow();
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(role);

        role.replacePermissions(Set.of(Permission.USER_MANAGE));
        scopes.runInTenant(tenant.id(), () -> unitOfWork.transactional(() -> roles.update(role)));
        Role afterUpdate = scopes.inTenant(tenant.id(), () -> unitOfWork.transactional(() -> roles.findByName("SUPPORT"))).orElseThrow();
        assertThat(afterUpdate.permissions()).containsExactly(Permission.USER_MANAGE);
    }

    @Test
    void roleNamesAreUniquePerTenantIgnoringCase() {
        Tenant a = scopes.newTenant();
        Tenant b = scopes.newTenant();
        scopes.runInTenant(a.id(), () -> unitOfWork.transactional(() -> roles.add(newRole(a, "Support", Set.of()))));
        scopes.runInTenant(b.id(), () -> unitOfWork.transactional(() -> roles.add(newRole(b, "Support", Set.of()))));

        assertThatThrownBy(() -> scopes.runInTenant(a.id(), () -> unitOfWork.transactional(() -> roles.add(newRole(a, "SUPPORT", Set.of())))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void usersRoundTripWithRolesAndDirectPermissions() {
        Tenant tenant = scopes.newTenant();
        Role role = newRole(tenant, "Support", Set.of(Permission.USER_READ));
        BillingUser user = BillingUser.create(new BillingUserId(UUID.randomUUID()), tenant.id(), PermissionScope.TENANT,
                "Zoe@Example.com", "Zoe", "sub-" + UUID.randomUUID(), clock.now());
        user.assign(role.id());
        user.grant(Permission.ROLE_READ);
        scopes.runInTenant(tenant.id(), () -> unitOfWork.transactional(() -> {
            roles.add(role);
            users.add(user);
        }));

        BillingUser reloaded = scopes.inTenant(tenant.id(), () -> unitOfWork.transactional(() -> users.findById(user.id()))).orElseThrow();
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(user);

        user.unassign(role.id());
        user.revoke(Permission.ROLE_READ);
        user.grant(Permission.USER_MANAGE);
        user.disable();
        scopes.runInTenant(tenant.id(), () -> unitOfWork.transactional(() -> users.update(user)));
        BillingUser afterUpdate = scopes.inTenant(tenant.id(), () -> unitOfWork.transactional(() -> users.findByKeycloakSubject(user.keycloakSubject()))).orElseThrow();
        assertThat(afterUpdate).usingRecursiveComparison().isEqualTo(user);
        assertThat(afterUpdate.isActive()).isFalse();
        assertThat(afterUpdate.roleIds()).isEmpty();
    }

    @Test
    void emailIsUniquePerTenantAndUsersAreInvisibleAcrossTenants() {
        Tenant a = scopes.newTenant();
        Tenant b = scopes.newTenant();
        BillingUser inA = BillingUser.create(new BillingUserId(UUID.randomUUID()), a.id(), PermissionScope.TENANT, "same@example.com", "A", "sub-" + UUID.randomUUID(), clock.now());
        BillingUser inB = BillingUser.create(new BillingUserId(UUID.randomUUID()), b.id(), PermissionScope.TENANT, "same@example.com", "B", "sub-" + UUID.randomUUID(), clock.now());
        scopes.runInTenant(a.id(), () -> unitOfWork.transactional(() -> users.add(inA)));
        scopes.runInTenant(b.id(), () -> unitOfWork.transactional(() -> users.add(inB)));

        BillingUser duplicate = BillingUser.create(new BillingUserId(UUID.randomUUID()), a.id(), PermissionScope.TENANT, "same@example.com", "A2", "sub-" + UUID.randomUUID(), clock.now());
        assertThatThrownBy(() -> scopes.runInTenant(a.id(), () -> unitOfWork.transactional(() -> users.add(duplicate))))
                .isInstanceOf(ConflictException.class);

        assertThat(scopes.inTenant(b.id(), () -> unitOfWork.transactional(() -> users.findById(inA.id())))).isEmpty();
        assertThat(scopes.inTenant(a.id(), () -> userQueries.list())).extracting(UserView::id).containsExactly(inA.id().value());
    }

    @Test
    void aUserCannotReferenceARoleOfAnotherTenant() {
        Tenant a = scopes.newTenant();
        Tenant b = scopes.newTenant();
        Role roleOfB = newRole(b, "Support", Set.of());
        scopes.runInTenant(b.id(), () -> unitOfWork.transactional(() -> roles.add(roleOfB)));
        BillingUser user = BillingUser.create(new BillingUserId(UUID.randomUUID()), a.id(), PermissionScope.TENANT, "x@example.com", "X", "sub-" + UUID.randomUUID(), clock.now());
        user.assign(roleOfB.id());

        assertThatThrownBy(() -> scopes.runInTenant(a.id(), () -> unitOfWork.transactional(() -> users.add(user))))
                .hasMessageContaining("foreign key");
    }

    @Test
    void tenantSystemsAndCredentialsRoundTrip() {
        Tenant tenant = scopes.newTenant();
        TenantSystem system = TenantSystem.create(new TenantSystemId(UUID.randomUUID()), tenant.id(), "Sample", clock.now());
        ApiKey.Generated key = ApiKey.generate(new SecureRandom());
        ApiCredential credential = ApiCredential.issue(new ApiCredentialId(UUID.randomUUID()), tenant.id(), system.id(), key,
                Set.of(Permission.CUSTOMER_READ, Permission.CUSTOMER_WRITE), "ci", clock.now());
        scopes.runInTenant(tenant.id(), () -> unitOfWork.transactional(() -> {
            systems.add(system);
            credentials.add(credential);
        }));

        TenantSystem reloadedSystem = scopes.inTenant(tenant.id(), () -> unitOfWork.transactional(() -> systems.findById(system.id()))).orElseThrow();
        assertThat(reloadedSystem).usingRecursiveComparison().isEqualTo(system);
        ApiCredential reloaded = scopes.inTenant(tenant.id(), () -> unitOfWork.transactional(() -> credentials.findByKeyId(key.keyId()))).orElseThrow();
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(credential);
        assertThat(reloaded.matches(key.secret())).isTrue();

        credential.expireAt(clock.now().plusSeconds(3600));
        credential.revoke(clock.now());
        scopes.runInTenant(tenant.id(), () -> unitOfWork.transactional(() -> credentials.update(credential)));
        ApiCredential afterUpdate = scopes.inTenant(tenant.id(), () -> unitOfWork.transactional(() -> credentials.findById(credential.id()))).orElseThrow();
        assertThat(afterUpdate).usingRecursiveComparison().isEqualTo(credential);
    }

    @Test
    void aCredentialIsInvisibleToAnotherTenantAndBoundToItsOwnTenantSystem() {
        Tenant a = scopes.newTenant();
        Tenant b = scopes.newTenant();
        TenantSystem systemOfA = TenantSystem.create(new TenantSystemId(UUID.randomUUID()), a.id(), "Sample", clock.now());
        ApiKey.Generated key = ApiKey.generate(new SecureRandom());
        ApiCredential credential = ApiCredential.issue(new ApiCredentialId(UUID.randomUUID()), a.id(), systemOfA.id(), key, Set.of(), "x", clock.now());
        scopes.runInTenant(a.id(), () -> unitOfWork.transactional(() -> {
            systems.add(systemOfA);
            credentials.add(credential);
        }));

        assertThat(scopes.inTenant(b.id(), () -> unitOfWork.transactional(() -> credentials.findByKeyId(key.keyId())))).isEmpty();

        ApiCredential foreign = ApiCredential.issue(new ApiCredentialId(UUID.randomUUID()), b.id(), systemOfA.id(),
                ApiKey.generate(new SecureRandom()), Set.of(), "x", clock.now());
        assertThatThrownBy(() -> scopes.runInTenant(b.id(), () -> unitOfWork.transactional(() -> credentials.add(foreign))))
                .hasMessageContaining("foreign key");
    }
}
