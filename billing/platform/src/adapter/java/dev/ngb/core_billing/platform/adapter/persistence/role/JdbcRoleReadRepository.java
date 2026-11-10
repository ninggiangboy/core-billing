package dev.ngb.core_billing.platform.adapter.persistence.role;

import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.application.role.query.RoleReadRepository;
import dev.ngb.core_billing.platform.application.role.query.RoleView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Selects roles and their permission codes straight into views. */
public class JdbcRoleReadRepository implements RoleReadRepository {

    private record Row(UUID id, String name, boolean system, Instant createdAt) {}

    private record PermissionRow(UUID roleId, String permissionCode) {}

    private final JdbcClient jdbc;

    public JdbcRoleReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RoleView> list() {
        List<Row> rows = jdbc.sql("select id, name, is_system as system, created_at from roles order by created_at, id").query(Row.class).list();
        return withPermissions(rows);
    }

    @Override
    public Optional<RoleView> findById(UUID id) {
        return jdbc.sql("select id, name, is_system as system, created_at from roles where id = ?")
                .param(id)
                .query(Row.class)
                .optional()
                .map(row -> withPermissions(List.of(row)).get(0));
    }

    private List<RoleView> withPermissions(List<Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Set<Permission>> permissions = new HashMap<>();
        jdbc.sql("select role_id, permission_code from role_permissions where role_id = any(?) order by permission_code")
                .param(rows.stream().map(Row::id).toArray(UUID[]::new))
                .query(PermissionRow.class)
                .list()
                .forEach(row -> permissions.computeIfAbsent(row.roleId(), key -> new HashSet<>())
                        .add(Permission.fromCode(row.permissionCode()).orElseThrow()));
        List<RoleView> views = new ArrayList<>();
        for (Row row : rows) {
            views.add(new RoleView(row.id(), row.name(), row.system(), row.createdAt(), permissions.getOrDefault(row.id(), Set.of())));
        }
        return views;
    }
}
