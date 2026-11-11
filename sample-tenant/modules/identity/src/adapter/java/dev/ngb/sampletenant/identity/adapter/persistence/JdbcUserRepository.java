package dev.ngb.sampletenant.identity.adapter.persistence;

import dev.ngb.sampletenant.identity.api.Role;
import dev.ngb.sampletenant.identity.application.port.UserRepository;
import dev.ngb.sampletenant.identity.domain.User;
import dev.ngb.sampletenant.identity.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcUserRepository implements UserRepository {

    private final UserJdbcRepository rows;

    public JdbcUserRepository(UserJdbcRepository rows) {
        this.rows = rows;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return rows.findById(id).map(JdbcUserRepository::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return rows.findByEmail(email).map(JdbcUserRepository::toDomain);
    }

    @Override
    public List<User> findAll() {
        return rows.findAllByOrderByCreatedAtAsc().stream().map(JdbcUserRepository::toDomain).toList();
    }

    @Override
    public boolean emailExists(String email) {
        return rows.existsByEmail(email);
    }

    @Override
    public void save(User user) {
        rows.save(new UserRow(
                user.id(),
                user.email(),
                user.displayName(),
                user.passwordHash(),
                user.role().name(),
                user.status().name(),
                user.createdAt()));
    }

    private static User toDomain(UserRow row) {
        return new User(
                row.id(),
                row.email(),
                row.displayName(),
                row.passwordHash(),
                Role.valueOf(row.role()),
                UserStatus.valueOf(row.status()),
                row.createdAt());
    }
}
