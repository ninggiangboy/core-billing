package dev.ngb.core_billing.platform.adapter.role.persistence;

import static dev.ngb.core_billing_jooq.Tables.ROLES;
import static dev.ngb.core_billing_jooq.Tables.ROLE_PERMISSIONS;

import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.PermissionScope;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.application.role.port.RoleRepository;
import dev.ngb.core_billing.platform.domain.role.Role;
import dev.ngb.core_billing.platform.domain.role.RoleId;
import dev.ngb.core_billing.sharedkernel.util.Causes;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing_jooq.tables.records.RolesRecord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;

public class JooqRoleRepository implements RoleRepository {

    private final DSLContext dsl;

    public JooqRoleRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(Role role) {
        try {
            dsl.insertInto(ROLES)
                    .set(ROLES.ID, role.id().value())
                    .set(ROLES.TENANT_ID, role.tenantId().value())
                    .set(ROLES.SCOPE, role.scope().name())
                    .set(ROLES.NAME, role.name())
                    .set(ROLES.IS_SYSTEM, role.isSystem())
                    .set(ROLES.CREATED_AT, role.createdAt())
                    .execute();
            insertPermissions(role);
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("a role with this name already exists");
            }
            throw e;
        }
    }

    @Override
    public void update(Role role) {
        dsl.update(ROLES)
                .set(ROLES.NAME, role.name())
                .where(ROLES.ID.eq(role.id().value()))
                .execute();
        dsl.deleteFrom(ROLE_PERMISSIONS).where(ROLE_PERMISSIONS.ROLE_ID.eq(role.id().value())).execute();
        insertPermissions(role);
    }

    @Override
    public Optional<Role> findById(RoleId id) {
        return dsl.selectFrom(ROLES)
                .where(ROLES.ID.eq(id.value()))
                .fetchOptional(row -> toDomain(row, permissions(List.of(row.getId()))));
    }

    @Override
    public Optional<Role> findByName(String name) {
        return dsl.selectFrom(ROLES)
                .where(ROLES.NAME.equalIgnoreCase(name))
                .fetchOptional(row -> toDomain(row, permissions(List.of(row.getId()))));
    }

    @Override
    public List<Role> findAllById(Collection<RoleId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<RolesRecord> rows = dsl.selectFrom(ROLES)
                .where(ROLES.ID.in(ids.stream().map(RoleId::value).toList()))
                .fetch();
        Map<UUID, Set<Permission>> permissions = permissions(rows.stream().map(RolesRecord::getId).toList());
        return rows.stream().map(row -> toDomain(row, permissions)).toList();
    }

    private void insertPermissions(Role role) {
        if (role.permissions().isEmpty()) {
            return;
        }
        var insert = dsl.insertInto(ROLE_PERMISSIONS, ROLE_PERMISSIONS.TENANT_ID, ROLE_PERMISSIONS.ROLE_ID, ROLE_PERMISSIONS.PERMISSION_CODE);
        for (Permission permission : role.permissions()) {
            insert = insert.values(role.tenantId().value(), role.id().value(), permission.code());
        }
        insert.execute();
    }

    private Map<UUID, Set<Permission>> permissions(List<UUID> roleIds) {
        Map<UUID, Set<Permission>> byRole = new HashMap<>();
        if (roleIds.isEmpty()) {
            return byRole;
        }
        dsl.select(ROLE_PERMISSIONS.ROLE_ID, ROLE_PERMISSIONS.PERMISSION_CODE)
                .from(ROLE_PERMISSIONS)
                .where(ROLE_PERMISSIONS.ROLE_ID.in(roleIds))
                .orderBy(ROLE_PERMISSIONS.PERMISSION_CODE)
                .forEach(row -> byRole.computeIfAbsent(row.value1(), key -> new java.util.HashSet<>())
                        .add(Permission.fromCode(row.value2()).orElseThrow()));
        return byRole;
    }

    private static Role toDomain(RolesRecord row, Map<UUID, Set<Permission>> permissions) {
        return Role.restore(
                new RoleId(row.getId()),
                new TenantId(row.getTenantId()),
                PermissionScope.valueOf(row.getScope()),
                row.getName(),
                row.getIsSystem(),
                row.getCreatedAt(),
                permissions.getOrDefault(row.getId(), Set.of()));
    }
}
