package dev.ngb.sampletenant.identity.adapter.persistence;

import dev.ngb.sampletenant.identity.application.port.SessionRepository;
import dev.ngb.sampletenant.identity.domain.UserSession;
import java.time.Instant;
import java.util.Optional;

public class JdbcSessionRepository implements SessionRepository {

    private final SessionJdbcRepository rows;

    public JdbcSessionRepository(SessionJdbcRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(UserSession session) {
        rows.save(new SessionRow(
                session.id(), session.userId(), session.tokenHash(), session.createdAt(), session.expiresAt()));
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
