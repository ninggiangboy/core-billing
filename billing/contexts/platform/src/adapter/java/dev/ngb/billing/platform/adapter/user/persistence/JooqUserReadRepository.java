package dev.ngb.billing.platform.adapter.user.persistence;

import static dev.ngb.billing_jooq.Tables.BILLING_USERS;
import static dev.ngb.billing_jooq.Tables.ROLE_ASSIGNMENTS;
import static dev.ngb.billing_jooq.Tables.USER_PERMISSIONS;

import dev.ngb.billing.platform.api.actor.Permission;
import dev.ngb.billing.platform.application.user.query.UserReadRepository;
import dev.ngb.billing.platform.application.user.query.UserView;
import dev.ngb.billing.platform.domain.user.UserStatus;
import dev.ngb.billing_jooq.tables.records.BillingUsersRecord;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;

/** Selects users, their role ids and their direct permissions straight into views. */
public class JooqUserReadRepository implements UserReadRepository {

    private final DSLContext dsl;

    public JooqUserReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<UserView> list() {
        return withDetails(dsl.selectFrom(BILLING_USERS).orderBy(BILLING_USERS.CREATED_AT, BILLING_USERS.ID).fetch());
    }

    @Override
    public Optional<UserView> findById(UUID id) {
        return dsl.selectFrom(BILLING_USERS)
                .where(BILLING_USERS.ID.eq(id))
                .fetchOptional()
                .map(row -> withDetails(List.of(row)).getFirst());
    }

    private List<UserView> withDetails(List<BillingUsersRecord> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(BillingUsersRecord::getId).toList();
        Map<UUID, Set<UUID>> roles = new HashMap<>();
        dsl.select(ROLE_ASSIGNMENTS.USER_ID, ROLE_ASSIGNMENTS.ROLE_ID)
                .from(ROLE_ASSIGNMENTS)
                .where(ROLE_ASSIGNMENTS.USER_ID.in(ids))
                .forEach(row -> roles.computeIfAbsent(row.value1(), key -> new HashSet<>()).add(row.value2()));
        Map<UUID, Set<Permission>> permissions = new HashMap<>();
        dsl.select(USER_PERMISSIONS.USER_ID, USER_PERMISSIONS.PERMISSION_CODE)
                .from(USER_PERMISSIONS)
                .where(USER_PERMISSIONS.USER_ID.in(ids))
                .orderBy(USER_PERMISSIONS.PERMISSION_CODE)
                .forEach(row -> permissions.computeIfAbsent(row.value1(), key -> new HashSet<>())
                        .add(Permission.fromCode(row.value2()).orElseThrow()));
        return rows.stream()
                .map(row -> new UserView(
                        row.getId(),
                        row.getEmail(),
                        row.getDisplayName(),
                        row.getKeycloakSubject(),
                        UserStatus.valueOf(row.getStatus()),
                        row.getCreatedAt(),
                        roles.getOrDefault(row.getId(), Set.of()),
                        permissions.getOrDefault(row.getId(), Set.of())))
                .toList();
    }
}
