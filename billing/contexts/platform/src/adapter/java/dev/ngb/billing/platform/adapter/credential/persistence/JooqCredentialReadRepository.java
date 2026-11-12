package dev.ngb.billing.platform.adapter.credential.persistence;

import static dev.ngb.billing_jooq.Tables.API_CREDENTIALS;

import dev.ngb.billing.platform.api.actor.Permission;
import dev.ngb.billing.platform.application.credential.query.CredentialReadRepository;
import dev.ngb.billing.platform.application.credential.query.CredentialView;
import dev.ngb.billing.platform.domain.credential.CredentialStatus;
import dev.ngb.billing_jooq.tables.records.ApiCredentialsRecord;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.DSLContext;

/** Selects credential metadata straight into views; the secret hash is never selected. */
public class JooqCredentialReadRepository implements CredentialReadRepository {

    private final DSLContext dsl;

    public JooqCredentialReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<CredentialView> findByTenantSystem(UUID tenantSystemId) {
        return dsl.select(API_CREDENTIALS.ID, API_CREDENTIALS.TENANT_SYSTEM_ID, API_CREDENTIALS.KEY_ID, API_CREDENTIALS.SCOPES, API_CREDENTIALS.LABEL,
                        API_CREDENTIALS.STATUS, API_CREDENTIALS.CREATED_AT, API_CREDENTIALS.EXPIRES_AT, API_CREDENTIALS.REVOKED_AT)
                .from(API_CREDENTIALS)
                .where(API_CREDENTIALS.TENANT_SYSTEM_ID.eq(tenantSystemId))
                .orderBy(API_CREDENTIALS.CREATED_AT, API_CREDENTIALS.ID)
                .fetch(row -> new CredentialView(
                        row.value1(),
                        row.value2(),
                        row.value3(),
                        row.value5(),
                        scopes(row.value4()),
                        CredentialStatus.valueOf(row.value6()),
                        row.value7(),
                        row.value8(),
                        row.value9()));
    }

    private static Set<Permission> scopes(String[] codes) {
        return codes == null
                ? Set.of()
                : Arrays.stream(codes).map(code -> Permission.fromCode(code).orElseThrow()).collect(Collectors.toSet());
    }
}
