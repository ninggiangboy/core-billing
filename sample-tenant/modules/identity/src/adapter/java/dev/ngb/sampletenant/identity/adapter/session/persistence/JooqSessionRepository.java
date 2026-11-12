package dev.ngb.sampletenant.identity.adapter.session.persistence;

import static dev.ngb.sampletenant_jooq.Tables.USER_SESSIONS;

import dev.ngb.sampletenant.identity.application.session.port.SessionRepository;
import dev.ngb.sampletenant.identity.domain.session.UserSession;
import java.time.Instant;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** The write side: one insert, one indexed lookup and the two deletes. */
public class JooqSessionRepository implements SessionRepository {

    private final DSLContext dsl;

    public JooqSessionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(UserSession session) {
        dsl.insertInto(USER_SESSIONS)
                .columns(
                        USER_SESSIONS.ID,
                        USER_SESSIONS.USER_ID,
                        USER_SESSIONS.TOKEN_HASH,
                        USER_SESSIONS.CREATED_AT,
                        USER_SESSIONS.EXPIRES_AT)
                .values(
                        session.id(),
                        session.userId(),
                        session.tokenHash(),
                        session.createdAt(),
                        session.expiresAt())
                .onConflict(USER_SESSIONS.ID)
                .doUpdate()
                .set(USER_SESSIONS.EXPIRES_AT, DSL.excluded(USER_SESSIONS.EXPIRES_AT))
                .execute();
    }

    @Override
    public Optional<UserSession> findByTokenHash(String tokenHash) {
        return dsl.selectFrom(USER_SESSIONS)
                .where(USER_SESSIONS.TOKEN_HASH.eq(tokenHash))
                .fetchOptional()
                .map(row -> new UserSession(
                        row.getId(),
                        row.getUserId(),
                        row.getTokenHash(),
                        row.getCreatedAt(),
                        row.getExpiresAt()));
    }

    @Override
    public void deleteByTokenHash(String tokenHash) {
        dsl.deleteFrom(USER_SESSIONS).where(USER_SESSIONS.TOKEN_HASH.eq(tokenHash)).execute();
    }

    @Override
    public void deleteExpired(Instant now) {
        dsl.deleteFrom(USER_SESSIONS).where(USER_SESSIONS.EXPIRES_AT.le(now)).execute();
    }
}
