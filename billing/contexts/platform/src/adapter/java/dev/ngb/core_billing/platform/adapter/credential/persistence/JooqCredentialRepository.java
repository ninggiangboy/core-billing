package dev.ngb.core_billing.platform.adapter.credential.persistence;

import static dev.ngb.core_billing_jooq.Tables.API_CREDENTIALS;

import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.platform.application.credential.port.CredentialRepository;
import dev.ngb.core_billing.platform.domain.credential.ApiCredential;
import dev.ngb.core_billing.platform.domain.credential.ApiCredentialId;
import dev.ngb.core_billing.platform.domain.credential.CredentialStatus;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing_jooq.tables.records.ApiCredentialsRecord;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.DSLContext;

public class JooqCredentialRepository implements CredentialRepository {

    private final DSLContext dsl;

    public JooqCredentialRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(ApiCredential credential) {
        dsl.insertInto(API_CREDENTIALS)
                .set(API_CREDENTIALS.ID, credential.id().value())
                .set(API_CREDENTIALS.TENANT_ID, credential.tenantId().value())
                .set(API_CREDENTIALS.TENANT_SYSTEM_ID, credential.tenantSystemId().value())
                .set(API_CREDENTIALS.KEY_ID, credential.keyId())
                .set(API_CREDENTIALS.SECRET_HASH, credential.secretHash())
                .set(API_CREDENTIALS.SCOPES, scopes(credential))
                .set(API_CREDENTIALS.LABEL, credential.label())
                .set(API_CREDENTIALS.STATUS, credential.status().name())
                .set(API_CREDENTIALS.CREATED_AT, credential.createdAt())
                .set(API_CREDENTIALS.EXPIRES_AT, credential.expiresAt())
                .set(API_CREDENTIALS.REVOKED_AT, credential.revokedAt())
                .execute();
    }

    @Override
    public void update(ApiCredential credential) {
        dsl.update(API_CREDENTIALS)
                .set(API_CREDENTIALS.KEY_ID, credential.keyId())
                .set(API_CREDENTIALS.SECRET_HASH, credential.secretHash())
                .set(API_CREDENTIALS.SCOPES, scopes(credential))
                .set(API_CREDENTIALS.LABEL, credential.label())
                .set(API_CREDENTIALS.STATUS, credential.status().name())
                .set(API_CREDENTIALS.EXPIRES_AT, credential.expiresAt())
                .set(API_CREDENTIALS.REVOKED_AT, credential.revokedAt())
                .where(API_CREDENTIALS.ID.eq(credential.id().value()))
                .execute();
    }

    @Override
    public Optional<ApiCredential> findById(ApiCredentialId id) {
        return dsl.selectFrom(API_CREDENTIALS)
                .where(API_CREDENTIALS.ID.eq(id.value()))
                .fetchOptional(JooqCredentialRepository::toDomain);
    }

    @Override
    public Optional<ApiCredential> findByKeyId(String keyId) {
        return dsl.selectFrom(API_CREDENTIALS)
                .where(API_CREDENTIALS.KEY_ID.eq(keyId))
                .fetchOptional(JooqCredentialRepository::toDomain);
    }

    @Override
    public List<ApiCredential> findByTenantSystem(TenantSystemId tenantSystemId) {
        return dsl.selectFrom(API_CREDENTIALS)
                .where(API_CREDENTIALS.TENANT_SYSTEM_ID.eq(tenantSystemId.value()))
                .orderBy(API_CREDENTIALS.CREATED_AT)
                .fetch(JooqCredentialRepository::toDomain);
    }

    private static String[] scopes(ApiCredential credential) {
        return credential.scopes().stream().map(Permission::code).sorted().toArray(String[]::new);
    }

    private static ApiCredential toDomain(ApiCredentialsRecord row) {
        Set<Permission> scopes = Arrays.stream(row.getScopes())
                .map(code -> Permission.fromCode(code).orElseThrow())
                .collect(Collectors.toSet());
        return ApiCredential.restore(
                new ApiCredentialId(row.getId()),
                new TenantId(row.getTenantId()),
                new TenantSystemId(row.getTenantSystemId()),
                row.getKeyId(),
                row.getSecretHash(),
                scopes,
                row.getLabel(),
                CredentialStatus.valueOf(row.getStatus()),
                row.getCreatedAt(),
                row.getExpiresAt(),
                row.getRevokedAt());
    }
}
