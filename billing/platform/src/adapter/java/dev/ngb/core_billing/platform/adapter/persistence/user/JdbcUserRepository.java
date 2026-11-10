package dev.ngb.core_billing.platform.adapter.persistence.user;
import dev.ngb.core_billing.platform.adapter.persistence.Causes;


import dev.ngb.core_billing.platform.api.error.ConflictException;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.PermissionScope;
import dev.ngb.core_billing.platform.application.user.port.UserRepository;
import dev.ngb.core_billing.platform.domain.user.BillingUser;
import dev.ngb.core_billing.platform.domain.role.RoleId;
import dev.ngb.core_billing.platform.domain.user.UserId;
import dev.ngb.core_billing.platform.domain.user.UserStatus;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;

public class JdbcUserRepository implements UserRepository {

    private final JdbcAggregateOperations jdbc;

    public JdbcUserRepository(JdbcAggregateOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(BillingUser user) {
        try {
            jdbc.insert(toRow(user));
        } catch (RuntimeException e) {
            if (Causes.contains(e, DuplicateKeyException.class)) {
                throw new ConflictException("a user with this email already exists");
            }
            throw e;
        }
    }

    @Override
    public void update(BillingUser user) {
        jdbc.update(toRow(user));
    }

    @Override
    public Optional<BillingUser> findById(UserId id) {
        return Optional.ofNullable(jdbc.findById(id.value(), UserRow.class)).map(JdbcUserRepository::toDomain);
    }

    @Override
    public Optional<BillingUser> findByKeycloakSubject(String subject) {
        return jdbc.findOne(query(where("keycloakSubject").is(subject)), UserRow.class).map(JdbcUserRepository::toDomain);
    }

    @Override
    public Optional<BillingUser> findByEmail(String email) {
        return jdbc.findOne(query(where("email").is(email)), UserRow.class).map(JdbcUserRepository::toDomain);
    }

    private static UserRow toRow(BillingUser user) {
        return new UserRow(
                user.id().value(),
                user.tenantId().value(),
                user.scope().name(),
                user.email(),
                user.displayName(),
                user.keycloakSubject(),
                user.status().name(),
                user.createdAt(),
                user.roleIds().stream()
                        .map(role -> new UserRow.RoleAssignmentRow(user.tenantId().value(), role.value()))
                        .collect(Collectors.toSet()),
                user.directPermissions().stream()
                        .map(p -> new UserRow.UserPermissionRow(user.tenantId().value(), p.code()))
                        .collect(Collectors.toSet()));
    }

    private static BillingUser toDomain(UserRow row) {
        return BillingUser.restore(
                new UserId(row.id()),
                new TenantId(row.tenantId()),
                PermissionScope.valueOf(row.scope()),
                row.email(),
                row.displayName(),
                row.keycloakSubject(),
                UserStatus.valueOf(row.status()),
                row.createdAt(),
                row.roleAssignments().stream().map(a -> new RoleId(a.roleId())).collect(Collectors.toSet()),
                row.permissions().stream()
                        .map(p -> Permission.fromCode(p.permissionCode()).orElseThrow())
                        .collect(Collectors.toSet()));
    }
}
