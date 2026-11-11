package dev.ngb.core_billing.platform.adapter.role.persistence;

import static dev.ngb.core_billing_jooq.Tables.ROLES;
import static dev.ngb.core_billing_jooq.Tables.ROLE_PERMISSIONS;

import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.application.role.query.RoleReadRepository;
import dev.ngb.core_billing.platform.application.role.query.RoleView;
import dev.ngb.core_billing_jooq.tables.records.RolesRecord;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;

/** Selects roles and their permission codes straight into views. */
public class JooqRoleReadRepository implements RoleReadRepository {

    private final DSLContext dsl;

    public JooqRoleReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<RoleView> list() {
        return withPermissions(dsl.selectFrom(ROLES).orderBy(ROLES.CREATED_AT, ROLES.ID).fetch());
    }

    @Override
    public Optional<RoleView> findById(UUID id) {
        return dsl.selectFrom(ROLES)
                .where(ROLES.ID.eq(id))
                .fetchOptional()
                .map(row -> withPermissions(List.of(row)).getFirst());
    }

    private List<RoleView> withPermissions(List<RolesRecord> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Set<Permission>> permissions = new HashMap<>();
        dsl.select(ROLE_PERMISSIONS.ROLE_ID, ROLE_PERMISSIONS.PERMISSION_CODE)
                .from(ROLE_PERMISSIONS)
                .where(ROLE_PERMISSIONS.ROLE_ID.in(rows.stream().map(RolesRecord::getId).toList()))
                .orderBy(ROLE_PERMISSIONS.PERMISSION_CODE)
                .forEach(row -> permissions.computeIfAbsent(row.value1(), key -> new HashSet<>())
                        .add(Permission.fromCode(row.value2()).orElseThrow()));
        return rows.stream()
                .map(row -> new RoleView(
                        row.getId(), row.getName(), row.getIsSystem(), row.getCreatedAt(), permissions.getOrDefault(row.getId(), Set.of())))
                .toList();
    }
}
