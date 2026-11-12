package dev.ngb.core_billing.platform.application.user.command;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.platform.api.actor.PermissionScope;
import dev.ngb.core_billing.platform.api.actor.RequestScope;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.application.auth.port.IdentityProviderPort;
import dev.ngb.core_billing.platform.application.role.port.RoleRepository;
import dev.ngb.core_billing.platform.application.tenant.exception.TenantNotFoundException;
import dev.ngb.core_billing.platform.application.tenant.port.TenantRepository;
import dev.ngb.core_billing.platform.application.user.port.UserRepository;
import dev.ngb.core_billing.platform.domain.role.Role;
import dev.ngb.core_billing.platform.domain.role.RoleId;
import dev.ngb.core_billing.platform.domain.user.BillingUser;
import dev.ngb.core_billing.platform.domain.user.BillingUserId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Billing Users of the current scope: Platform Users on the Platform, Tenant Users inside a Tenant. */
public class ManageUserUseCase {

    public record CreateUser(String email, String displayName, String temporaryPassword, Set<RoleId> roleIds) {
        public CreateUser {
            roleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        }

        @Override
        public String toString() {
            return "CreateUser[email=" + email + "]";
        }
    }

    private record Prepared(TenantId tenantId, PermissionScope scope, String tenantCode) {}

    private final UnitOfWork unitOfWork;
    private final UserRepository users;
    private final RoleRepository roles;
    private final TenantRepository tenants;
    private final IdentityProviderPort identityProvider;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public ManageUserUseCase(
            UnitOfWork unitOfWork,
            UserRepository users,
            RoleRepository roles,
            TenantRepository tenants,
            IdentityProviderPort identityProvider,
            AuditLog auditLog,
            TenantContext tenantContext,
            IdGenerator ids,
            Clock clock) {
        this.unitOfWork = unitOfWork;
        this.users = users;
        this.roles = roles;
        this.tenants = tenants;
        this.identityProvider = identityProvider;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Creates the Keycloak identity first and the Billing user second, and deletes the identity again if
     * storing the user fails, so no half-created user is left behind. No database transaction is held
     * open while Keycloak is called.
     */
    public BillingUser create(Audit caller, CreateUser command) {
        String email = command.email() == null ? "" : command.email().strip().toLowerCase(Locale.ROOT);
        Prepared prepared = unitOfWork.transactional(() -> prepare(email, command.roleIds()));

        String subject = identityProvider.createIdentity(new IdentityProviderPort.NewIdentity(
                prepared.tenantCode() + ":" + email, email, command.displayName(), prepared.tenantCode(),
                command.temporaryPassword()));
        try {
            return unitOfWork.transactional(() -> {
                BillingUser user = BillingUser.create(
                        new BillingUserId(ids.newId()), prepared.tenantId(), prepared.scope(), email, command.displayName(),
                        subject, clock.now());
                command.roleIds().forEach(user::assign);
                users.add(user);
                auditLog.record(AuditEntry.tenant(
                        prepared.tenantId(), caller.actor(), "user.created", "user/" + user.id(), caller.requestId(), clock.now()));
                return user;
            });
        } catch (RuntimeException failure) {
            try {
                identityProvider.deleteIdentity(subject);
            } catch (RuntimeException compensationFailure) {
                failure.addSuppressed(compensationFailure);
            }
            throw failure;
        }
    }


    public BillingUser assignRole(Audit caller, BillingUserId userId, RoleId roleId) {
        return unitOfWork.transactional(() -> {
            BillingUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("user not found"));
            Role role = roles.findById(roleId).orElseThrow(() -> new NotFoundException("role not found"));
            requireSameScope(user, role);
            user.assign(role.id());
            users.update(user);
            audit(caller, user, "user.role-assigned", "user/" + user.id() + "/role/" + role.id());
            return user;
        });
    }

    public BillingUser unassignRole(Audit caller, BillingUserId userId, RoleId roleId) {
        return unitOfWork.transactional(() -> {
            BillingUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("user not found"));
            user.unassign(roleId);
            users.update(user);
            audit(caller, user, "user.role-removed", "user/" + user.id() + "/role/" + roleId);
            return user;
        });
    }

    private Prepared prepare(String email, Set<RoleId> roleIds) {
        RequestScope current = tenantContext.current();
        TenantId tenantId = current.dataTenantId();
        PermissionScope scope = current instanceof RequestScope.Platform ? PermissionScope.PLATFORM : PermissionScope.TENANT;
        String tenantCode = tenants.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()))
                .code()
                .value();
        if (users.findByEmail(email).isPresent()) {
            throw new ConflictException("a user with this email already exists");
        }
        List<Role> found = roles.findAllById(roleIds);
        if (found.size() != roleIds.size()) {
            throw new NotFoundException("role not found");
        }
        found.forEach(role -> {
            if (role.scope() != scope) {
                throw new IllegalArgumentException("role " + role.name() + " belongs to another scope");
            }
        });
        return new Prepared(tenantId, scope, tenantCode);
    }

    private static void requireSameScope(BillingUser user, Role role) {
        if (user.scope() != role.scope()) {
            throw new IllegalArgumentException("role " + role.name() + " belongs to another scope");
        }
    }

    private void audit(Audit caller, BillingUser user, String action, String subject) {
        auditLog.record(AuditEntry.tenant(user.tenantId(), caller.actor(), action, subject, caller.requestId(), clock.now()));
    }
}
