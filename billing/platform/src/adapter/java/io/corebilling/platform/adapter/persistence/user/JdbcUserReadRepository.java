package io.corebilling.platform.adapter.persistence.user;

import io.corebilling.platform.api.actor.Permission;
import io.corebilling.platform.application.user.query.UserReadRepository;
import io.corebilling.platform.application.user.query.UserView;
import io.corebilling.platform.domain.user.UserStatus;
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

/** Selects users, their role ids and their direct permissions straight into views. */
public class JdbcUserReadRepository implements UserReadRepository {

    private record Row(UUID id, String email, String displayName, String keycloakSubject, String status, Instant createdAt) {}

    private record AssignmentRow(UUID userId, UUID roleId) {}

    private record PermissionRow(UUID userId, String permissionCode) {}

    private final JdbcClient jdbc;

    public JdbcUserReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UserView> list() {
        List<Row> rows = jdbc.sql("select id, email, display_name, keycloak_subject, status, created_at from billing_users order by created_at, id").query(Row.class).list();
        return withDetails(rows);
    }

    @Override
    public Optional<UserView> findById(UUID id) {
        return jdbc.sql("select id, email, display_name, keycloak_subject, status, created_at from billing_users where id = ?")
                .param(id)
                .query(Row.class)
                .optional()
                .map(row -> withDetails(List.of(row)).get(0));
    }

    private List<UserView> withDetails(List<Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        UUID[] ids = rows.stream().map(Row::id).toArray(UUID[]::new);
        Map<UUID, Set<UUID>> roles = new HashMap<>();
        jdbc.sql("select user_id, role_id from role_assignments where user_id = any(?)")
                .param(ids)
                .query(AssignmentRow.class)
                .list()
                .forEach(row -> roles.computeIfAbsent(row.userId(), key -> new HashSet<>()).add(row.roleId()));
        Map<UUID, Set<Permission>> permissions = new HashMap<>();
        jdbc.sql("select user_id, permission_code from user_permissions where user_id = any(?) order by permission_code")
                .param(ids)
                .query(PermissionRow.class)
                .list()
                .forEach(row -> permissions.computeIfAbsent(row.userId(), key -> new HashSet<>())
                        .add(Permission.fromCode(row.permissionCode()).orElseThrow()));
        List<UserView> views = new ArrayList<>();
        for (Row row : rows) {
            views.add(new UserView(
                    row.id(),
                    row.email(),
                    row.displayName(),
                    row.keycloakSubject(),
                    UserStatus.valueOf(row.status()),
                    row.createdAt(),
                    roles.getOrDefault(row.id(), Set.of()),
                    permissions.getOrDefault(row.id(), Set.of())));
        }
        return views;
    }
}
