package dev.ngb.core_billing.platform.adapter.user.persistence;

import static dev.ngb.core_billing_jooq.Tables.BILLING_USERS;
import static dev.ngb.core_billing_jooq.Tables.ROLE_ASSIGNMENTS;
import static dev.ngb.core_billing_jooq.Tables.USER_PERMISSIONS;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.util.Causes;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.PermissionScope;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.application.user.port.UserRepository;
import dev.ngb.core_billing.platform.domain.role.RoleId;
import dev.ngb.core_billing.platform.domain.user.BillingUser;
import dev.ngb.core_billing.platform.domain.user.BillingUserId;
import dev.ngb.core_billing.platform.domain.user.UserStatus;
import dev.ngb.core_billing_jooq.tables.records.BillingUsersRecord;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;

public class JooqUserRepository implements UserRepository {

    private final DSLContext dsl;

    public JooqUserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(BillingUser user) {
        try {
            dsl.insertInto(BILLING_USERS)
                    .set(BILLING_USERS.ID, user.id().value())
                    .set(BILLING_USERS.TENANT_ID, user.tenantId().value())
                    .set(BILLING_USERS.SCOPE, user.scope().name())
                    .set(BILLING_USERS.EMAIL, user.email())
                    .set(BILLING_USERS.DISPLAY_NAME, user.displayName())
                    .set(BILLING_USERS.KEYCLOAK_SUBJECT, user.keycloakSubject())
                    .set(BILLING_USERS.STATUS, user.status().name())
                    .set(BILLING_USERS.CREATED_AT, user.createdAt())
                    .execute();
            insertDetails(user);
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("a user with this email already exists");
            }
            throw e;
        }
    }

    @Override
    public void update(BillingUser user) {
        dsl.update(BILLING_USERS)
                .set(BILLING_USERS.EMAIL, user.email())
                .set(BILLING_USERS.DISPLAY_NAME, user.displayName())
                .set(BILLING_USERS.KEYCLOAK_SUBJECT, user.keycloakSubject())
                .set(BILLING_USERS.STATUS, user.status().name())
                .where(BILLING_USERS.ID.eq(user.id().value()))
                .execute();
        dsl.deleteFrom(ROLE_ASSIGNMENTS).where(ROLE_ASSIGNMENTS.USER_ID.eq(user.id().value())).execute();
        dsl.deleteFrom(USER_PERMISSIONS).where(USER_PERMISSIONS.USER_ID.eq(user.id().value())).execute();
        insertDetails(user);
    }

    @Override
    public Optional<BillingUser> findById(BillingUserId id) {
        return dsl.selectFrom(BILLING_USERS)
                .where(BILLING_USERS.ID.eq(id.value()))
                .fetchOptional(row -> toDomain(row, roleIds(row.getId()), permissions(row.getId())));
    }

    @Override
    public Optional<BillingUser> findByKeycloakSubject(String subject) {
        return dsl.selectFrom(BILLING_USERS)
                .where(BILLING_USERS.KEYCLOAK_SUBJECT.eq(subject))
                .fetchOptional(row -> toDomain(row, roleIds(row.getId()), permissions(row.getId())));
    }

    @Override
    public Optional<BillingUser> findByEmail(String email) {
        return dsl.selectFrom(BILLING_USERS)
                .where(BILLING_USERS.EMAIL.eq(email))
                .fetchOptional(row -> toDomain(row, roleIds(row.getId()), permissions(row.getId())));
    }

    private void insertDetails(BillingUser user) {
        if (!user.roleIds().isEmpty()) {
            var assignments = dsl.insertInto(ROLE_ASSIGNMENTS, ROLE_ASSIGNMENTS.TENANT_ID, ROLE_ASSIGNMENTS.USER_ID, ROLE_ASSIGNMENTS.ROLE_ID);
            for (RoleId roleId : user.roleIds()) {
                assignments = assignments.values(user.tenantId().value(), user.id().value(), roleId.value());
            }
            assignments.execute();
        }
        if (!user.directPermissions().isEmpty()) {
            var permissions = dsl.insertInto(
                    USER_PERMISSIONS, USER_PERMISSIONS.TENANT_ID, USER_PERMISSIONS.USER_ID, USER_PERMISSIONS.PERMISSION_CODE);
            for (Permission permission : user.directPermissions()) {
                permissions = permissions.values(user.tenantId().value(), user.id().value(), permission.code());
            }
            permissions.execute();
        }
    }

    private Set<RoleId> roleIds(java.util.UUID userId) {
        return dsl.select(ROLE_ASSIGNMENTS.ROLE_ID)
                .from(ROLE_ASSIGNMENTS)
                .where(ROLE_ASSIGNMENTS.USER_ID.eq(userId))
                .fetch(ROLE_ASSIGNMENTS.ROLE_ID)
                .stream()
                .map(RoleId::new)
                .collect(Collectors.toSet());
    }

    private Set<Permission> permissions(java.util.UUID userId) {
        return dsl.select(USER_PERMISSIONS.PERMISSION_CODE)
                .from(USER_PERMISSIONS)
                .where(USER_PERMISSIONS.USER_ID.eq(userId))
                .orderBy(USER_PERMISSIONS.PERMISSION_CODE)
                .fetch(USER_PERMISSIONS.PERMISSION_CODE)
                .stream()
                .map(code -> Permission.fromCode(code).orElseThrow())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static BillingUser toDomain(BillingUsersRecord row, Set<RoleId> roleIds, Set<Permission> permissions) {
        return BillingUser.restore(
                new BillingUserId(row.getId()),
                new TenantId(row.getTenantId()),
                PermissionScope.valueOf(row.getScope()),
                row.getEmail(),
                row.getDisplayName(),
                row.getKeycloakSubject(),
                UserStatus.valueOf(row.getStatus()),
                row.getCreatedAt(),
                roleIds,
                permissions);
    }
}
