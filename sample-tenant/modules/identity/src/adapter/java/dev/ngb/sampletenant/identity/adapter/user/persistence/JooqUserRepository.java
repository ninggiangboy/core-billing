package dev.ngb.sampletenant.identity.adapter.user.persistence;

import static dev.ngb.sampletenant_jooq.Tables.USERS;

import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.identity.application.user.port.UserRepository;
import dev.ngb.sampletenant.identity.domain.user.User;
import dev.ngb.sampletenant.identity.domain.user.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** The write side: upsert and the lookups login and registration decide with. */
public class JooqUserRepository implements UserRepository {

    private final DSLContext dsl;

    public JooqUserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return dsl.selectFrom(USERS).where(USERS.ID.eq(id)).fetchOptional().map(JooqUserRepository::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return dsl.selectFrom(USERS)
                .where(DSL.lower(USERS.EMAIL).eq(email.toLowerCase(java.util.Locale.ROOT)))
                .fetchOptional()
                .map(JooqUserRepository::toDomain);
    }

    @Override
    public boolean emailExists(String email) {
        return dsl.fetchExists(dsl.selectOne()
                .from(USERS)
                .where(DSL.lower(USERS.EMAIL).eq(email.toLowerCase(java.util.Locale.ROOT))));
    }

    @Override
    public boolean anyExists() {
        return dsl.fetchExists(dsl.selectOne().from(USERS));
    }

    @Override
    public void save(User user) {
        dsl.insertInto(USERS)
                .columns(USERS.ID, USERS.EMAIL, USERS.DISPLAY_NAME, USERS.PASSWORD_HASH, USERS.ROLE, USERS.STATUS, USERS.CREATED_AT)
                .values(
                        user.id(),
                        user.email(),
                        user.displayName(),
                        user.passwordHash(),
                        user.role().name(),
                        user.status().name(),
                        user.createdAt())
                .onConflict(USERS.ID)
                .doUpdate()
                .set(USERS.EMAIL, DSL.excluded(USERS.EMAIL))
                .set(USERS.DISPLAY_NAME, DSL.excluded(USERS.DISPLAY_NAME))
                .set(USERS.PASSWORD_HASH, DSL.excluded(USERS.PASSWORD_HASH))
                .set(USERS.ROLE, DSL.excluded(USERS.ROLE))
                .set(USERS.STATUS, DSL.excluded(USERS.STATUS))
                .execute();
    }

    private static User toDomain(dev.ngb.sampletenant_jooq.tables.records.UsersRecord row) {
        return new User(
                row.getId(),
                row.getEmail(),
                row.getDisplayName(),
                row.getPasswordHash(),
                Role.valueOf(row.getRole()),
                UserStatus.valueOf(row.getStatus()),
                row.getCreatedAt());
    }
}
