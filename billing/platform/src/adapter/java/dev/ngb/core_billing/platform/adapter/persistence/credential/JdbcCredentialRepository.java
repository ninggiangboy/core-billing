package dev.ngb.core_billing.platform.adapter.persistence.credential;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.platform.application.credential.port.CredentialRepository;
import dev.ngb.core_billing.platform.domain.credential.ApiCredential;
import dev.ngb.core_billing.platform.domain.credential.CredentialId;
import dev.ngb.core_billing.platform.domain.credential.CredentialStatus;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;

public class JdbcCredentialRepository implements CredentialRepository {

    private final JdbcAggregateOperations jdbc;

    public JdbcCredentialRepository(JdbcAggregateOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(ApiCredential credential) {
        jdbc.insert(toRow(credential));
    }

    @Override
    public void update(ApiCredential credential) {
        jdbc.update(toRow(credential));
    }

    @Override
    public Optional<ApiCredential> findById(CredentialId id) {
        return Optional.ofNullable(jdbc.findById(id.value(), CredentialRow.class)).map(JdbcCredentialRepository::toDomain);
    }

    @Override
    public Optional<ApiCredential> findByKeyId(String keyId) {
        return jdbc.findOne(query(where("keyId").is(keyId)), CredentialRow.class).map(JdbcCredentialRepository::toDomain);
    }

    @Override
    public List<ApiCredential> findByTenantSystem(TenantSystemId tenantSystemId) {
        return jdbc.findAll(query(where("tenantSystemId").is(tenantSystemId.value())).sort(Sort.by("createdAt")), CredentialRow.class)
                .stream()
                .map(JdbcCredentialRepository::toDomain)
                .toList();
    }

    private static CredentialRow toRow(ApiCredential c) {
        return new CredentialRow(
                c.id().value(),
                c.tenantId().value(),
                c.tenantSystemId().value(),
                c.keyId(),
                c.secretHash(),
                c.scopes().stream().map(Permission::code).sorted().toArray(String[]::new),
                c.label(),
                c.status().name(),
                c.createdAt(),
                c.expiresAt(),
                c.revokedAt());
    }

    private static ApiCredential toDomain(CredentialRow row) {
        Set<Permission> scopes = Arrays.stream(row.scopes())
                .map(code -> Permission.fromCode(code).orElseThrow())
                .collect(Collectors.toSet());
        return ApiCredential.restore(
                new CredentialId(row.id()),
                new TenantId(row.tenantId()),
                new TenantSystemId(row.tenantSystemId()),
                row.keyId(),
                row.secretHash(),
                scopes,
                row.label(),
                CredentialStatus.valueOf(row.status()),
                row.createdAt(),
                row.expiresAt(),
                row.revokedAt());
    }
}
