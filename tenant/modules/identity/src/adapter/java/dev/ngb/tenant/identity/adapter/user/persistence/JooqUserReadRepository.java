package dev.ngb.tenant.identity.adapter.user.persistence;

import static dev.ngb.tenant_jooq.Tables.USERS;

import dev.ngb.tenant.identity.api.security.Role;
import dev.ngb.tenant.identity.application.user.query.UserReadRepository;
import dev.ngb.tenant.identity.application.user.query.UserView;
import dev.ngb.tenant.identity.domain.user.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/** The read side: selects straight into the view the user screens render. */
public class JooqUserReadRepository implements UserReadRepository {

    private final DSLContext dsl;

    public JooqUserReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<UserView> list() {
        return dsl.select(USERS.ID, USERS.EMAIL, USERS.DISPLAY_NAME, USERS.ROLE, USERS.STATUS, USERS.CREATED_AT)
                .from(USERS)
                .orderBy(USERS.CREATED_AT.asc())
                .fetch(row -> new UserView(
                        row.get(USERS.ID),
                        row.get(USERS.EMAIL),
                        row.get(USERS.DISPLAY_NAME),
                        Role.valueOf(row.get(USERS.ROLE)),
                        UserStatus.valueOf(row.get(USERS.STATUS)),
                        row.get(USERS.CREATED_AT)));
    }

    @Override
    public Optional<UserView> byId(UUID id) {
        return dsl.select(USERS.ID, USERS.EMAIL, USERS.DISPLAY_NAME, USERS.ROLE, USERS.STATUS, USERS.CREATED_AT)
                .from(USERS)
                .where(USERS.ID.eq(id))
                .fetchOptional(row -> new UserView(
                        row.get(USERS.ID),
                        row.get(USERS.EMAIL),
                        row.get(USERS.DISPLAY_NAME),
                        Role.valueOf(row.get(USERS.ROLE)),
                        UserStatus.valueOf(row.get(USERS.STATUS)),
                        row.get(USERS.CREATED_AT)));
    }
}
