package dev.ngb.sampletenant.identity.adapter.persistence;

import dev.ngb.sampletenant.identity.application.port.SessionRepository;
import dev.ngb.sampletenant.identity.domain.UserSession;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcSessionRepository implements SessionRepository {

    private final SessionJdbcRepository rows;
    private final JdbcTemplate jdbc;

    public JdbcSessionRepository(SessionJdbcRepository rows, JdbcTemplate jdbc) {
        this.rows = rows;
        this.jdbc = jdbc;
    }

    @Override
    public void save(UserSession session) {
        jdbc.update(
                """
                insert into user_sessions (id, user_id, token_hash, created_at, expires_at)
                values (?, ?, ?, ?, ?)
                on conflict (id) do update
                    set expires_at = excluded.expires_at
                """,
                session.id(),
                session.userId(),
                session.tokenHash(),
                Timestamp.from(session.createdAt()),
                Timestamp.from(session.expiresAt()));
    }

    @Override
    public Optional<UserSession> findByTokenHash(String tokenHash) {
        return rows.findByTokenHash(tokenHash).map(JdbcSessionRepository::toDomain);
    }

    @Override
    public void deleteByTokenHash(String tokenHash) {
        rows.deleteByTokenHash(tokenHash);
    }

    @Override
    public void deleteExpired(Instant now) {
        rows.findAll().forEach(session -> {
            if (!now.isBefore(session.expiresAt())) {
                rows.deleteById(session.id());
            }
        });
    }

    private static UserSession toDomain(SessionRow row) {
        return new UserSession(row.id(), row.userId(), row.tokenHash(), row.createdAt(), row.expiresAt());
    }
}
