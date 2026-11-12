package dev.ngb.sampletenant.identity.adapter.user.persistence;

import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.identity.application.user.port.UserRepository;
import dev.ngb.sampletenant.identity.domain.user.User;
import dev.ngb.sampletenant.identity.domain.user.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcUserRepository implements UserRepository {

    private final UserJdbcRepository rows;
    private final JdbcTemplate jdbc;

    public JdbcUserRepository(UserJdbcRepository rows, JdbcTemplate jdbc) {
        this.rows = rows;
        this.jdbc = jdbc;
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
        jdbc.update(
                """
                insert into users (id, email, display_name, password_hash, role, status, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update
                    set email = excluded.email,
                        display_name = excluded.display_name,
                        password_hash = excluded.password_hash,
                        role = excluded.role,
                        status = excluded.status
                """,
                user.id(),
                user.email(),
                user.displayName(),
                user.passwordHash(),
                user.role().name(),
                user.status().name(),
                java.sql.Timestamp.from(user.createdAt()));
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
