package io.corebilling.platform.adapter.persistence;

import io.corebilling.platform.api.error.ConflictException;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import io.corebilling.platform.api.actor.Permission;
import io.corebilling.platform.api.actor.PermissionScope;
import io.corebilling.platform.application.role.port.RoleRepository;
import io.corebilling.platform.domain.role.Role;
import io.corebilling.platform.domain.role.RoleId;
import io.corebilling.sharedkernel.TenantId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.relational.core.query.Query;

public class JdbcRoleRepository implements RoleRepository {

    private final JdbcAggregateOperations jdbc;

    public JdbcRoleRepository(JdbcAggregateOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Role role) {
        try {
            jdbc.insert(toRow(role));
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("a role with this name already exists");
            }
            throw e;
        }
    }

    @Override
    public void update(Role role) {
        jdbc.update(toRow(role));
    }

    @Override
    public Optional<Role> findById(RoleId id) {
        return Optional.ofNullable(jdbc.findById(id.value(), RoleRow.class)).map(JdbcRoleRepository::toDomain);
    }

    @Override
    public Optional<Role> findByName(String name) {
        return jdbc.findOne(query(where("name").is(name).ignoreCase(true)), RoleRow.class).map(JdbcRoleRepository::toDomain);
    }

    @Override
    public List<Role> findAllById(Collection<RoleId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Role> found = new ArrayList<>();
        jdbc.findAllById(ids.stream().map(RoleId::value).toList(), RoleRow.class)
                .forEach(row -> found.add(toDomain(row)));
        return found;
    }

    @Override
    public List<Role> list() {
        return jdbc.findAll(Query.empty().sort(Sort.by("createdAt")), RoleRow.class).stream()
                .map(JdbcRoleRepository::toDomain)
                .toList();
    }

    private static RoleRow toRow(Role role) {
        return new RoleRow(
                role.id().value(),
                role.tenantId().value(),
                role.scope().name(),
                role.name(),
                role.isSystem(),
                role.createdAt(),
                role.permissions().stream()
                        .map(p -> new RoleRow.RolePermissionRow(role.tenantId().value(), p.code()))
                        .collect(Collectors.toSet()));
    }

    private static Role toDomain(RoleRow row) {
        return Role.restore(
                new RoleId(row.id()),
                new TenantId(row.tenantId()),
                PermissionScope.valueOf(row.scope()),
                row.name(),
                row.system(),
                row.createdAt(),
                row.permissions().stream()
                        .map(p -> Permission.fromCode(p.permissionCode()).orElseThrow())
                        .collect(Collectors.toSet()));
    }
}
